package com.naleul.naleul.domain.activity.service;

import com.naleul.naleul.domain.activity.dto.response.RecordPatternResponse;
import com.naleul.naleul.domain.activity.dto.response.RecordPatternResponse.*;
import com.naleul.naleul.domain.category.service.CategoryFinder;
import com.naleul.naleul.domain.planner.support.PlannerClock;
import com.naleul.naleul.domain.routine.entity.Routine;
import com.naleul.naleul.domain.routine.repository.RoutineRepository;
import com.naleul.naleul.domain.task.repository.TaskRepository;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.service.UserFinder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 기록형 목표 상세의 "이 목표에 대한 나의 패턴".
 *
 * 숫자는 모두 이 목표의 완료한 Task(일반 + 루틴)에서 바로 계산한다 (캐시·AI 없음).
 * 직접 남긴 "한 일"(actual_activity)은 상세 화면 타임라인에서 따로 보여주고 여기에는 섞지 않는다.
 * 유형 이름("저녁에 몰입하는 평일형")이나 분석 문장은 만들지 않고 숫자만 내려준다.
 * 계산(compute)은 DB 없이 돌아가는 순수 함수라 테스트에서 날짜를 고정해 검증한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecordPatternService {

    static final int WINDOW_DAYS = 28;      // 최근 4주 / 그 전 4주
    static final int HEATMAP_WEEKS = 16;
    static final int TREND_WEEKS = 12;
    /** "요즘 늘었어요"로 꼽으려면 최근 4주에 그 전보다 최소 이만큼 더 해야 한다 */
    static final long RISING_MIN_MINUTES = 60;
    static final int RISING_MIN_COUNT = 2;

    static final List<String> BANDS = List.of("DAWN", "MORNING", "AFTERNOON", "EVENING");

    private final RoutineRepository routineRepository;
    private final TaskRepository taskRepository;
    private final UserFinder userFinder;
    private final CategoryFinder categoryFinder;
    private final PlannerClock clock;

    public RecordPatternResponse get(Long userId, Long goalCategoryId) {
        User user = userFinder.findUser(userId);
        categoryFinder.findGoalCategory(goalCategoryId, user);   // 내 목표인지 확인 (아니면 404)

        List<Entry> entries = taskRepository.findCompletedOfGoal(userId, goalCategoryId).stream()
                .map(t -> Entry.of(t.getActualStartAt(), t.getActualEndAt(), t.getPlannedStartAt(), t.getPlannedEndAt(),
                        t.getPlannedDurationMinutes(), t.getRoutineDate(), t.getScheduledDate(), t.getCompletedAt()))
                .filter(Objects::nonNull)
                .toList();

        List<Routine> routines = routineRepository.findAllByGoalCategoryId(goalCategoryId);
        List<RoutineInfo> infos = routines.stream()
                .map(r -> new RoutineInfo(r.getRoutineId(), r.getRoutineName(), r.getEmoji(), defaultMinutes(r)))
                .toList();
        List<RoutineDone> dones = routines.isEmpty() ? List.of()
                : taskRepository.findCompletedRoutineTasks(infos.stream().map(RoutineInfo::id).toList()).stream()
                .map(t -> new RoutineDone(t.getRoutineId(), t.getRoutineDate(),
                        minutesOf(t.getActualStartAt(), t.getActualEndAt(), t.getPlannedDurationMinutes())))
                .toList();

        return compute(entries, infos, dones, clock.today());
    }

    /**
     * 완료한 Task 한 건.
     * date    : 그 Task 를 한 날
     * startAt : 한 시각 (실제 → 계획). 모르면 null → "언제" 표에서 뺀다
     * minutes : 걸린 시간 (실제 → 계획). 모르면 null → 개수에만 들어간다
     */
    record Entry(LocalDate date, LocalDateTime startAt, Long minutes) {

        static Entry of(LocalDateTime actualStart, LocalDateTime actualEnd,
                        LocalDateTime plannedStart, LocalDateTime plannedEnd, Long plannedMinutes,
                        LocalDate routineDate, LocalDate scheduledDate, LocalDateTime completedAt) {
            LocalDateTime start = actualStart != null ? actualStart : plannedStart;
            LocalDate date = start != null ? start.toLocalDate()
                    : routineDate != null ? routineDate
                    : scheduledDate != null ? scheduledDate
                    : completedAt != null ? completedAt.toLocalDate() : null;
            if (date == null) return null;   // 날짜를 전혀 알 수 없는 Task 는 뺀다

            Long minutes = minutesOf(actualStart, actualEnd, plannedMinutes);
            if (minutes == null && plannedStart != null && plannedEnd != null && plannedEnd.isAfter(plannedStart)) {
                minutes = Duration.between(plannedStart, plannedEnd).toMinutes();
            }
            return new Entry(date, start, minutes);
        }

        long minutesOrZero() {
            return minutes != null ? minutes : 0;
        }

        /** 시각을 모르면 null */
        String band() {
            if (startAt == null) return null;
            int h = startAt.getHour();
            return h < 6 ? "DAWN" : h < 12 ? "MORNING" : h < 18 ? "AFTERNOON" : "EVENING";
        }
    }

    /** 루틴 (defaultMinutes: 완료 Task 에 시간이 없을 때 쓰는 루틴 기본 시간, 없으면 null) */
    record RoutineInfo(Long id, String name, String emoji, Integer defaultMinutes) {
    }

    /** 완료한 루틴 Task 한 건 (minutes 가 null 이면 시간 정보 없음 → 루틴 기본 시간) */
    record RoutineDone(Long routineId, LocalDate date, Long minutes) {
    }

    /** 실제 시각 → 계획 시간 순. 둘 다 없으면 null */
    static Long minutesOf(LocalDateTime actualStart, LocalDateTime actualEnd, Long plannedMinutes) {
        if (actualStart != null && actualEnd != null && actualEnd.isAfter(actualStart)) {
            return Duration.between(actualStart, actualEnd).toMinutes();
        }
        return plannedMinutes != null && plannedMinutes > 0 ? plannedMinutes : null;
    }

    /** 루틴 기본 시간: 소요 시간 → 반복 시작·끝 시각 순 */
    static Integer defaultMinutes(Routine r) {
        if (r.getDurationMinutes() != null && r.getDurationMinutes() > 0) return r.getDurationMinutes();
        LocalTime s = r.getRepeatStartTime();
        LocalTime e = r.getRepeatEndTime();
        if (s != null && e != null) {
            long m = Duration.between(s, e).toMinutes();
            if (m <= 0) m += 24 * 60;   // 자정을 넘기는 루틴 (23:00 ~ 01:00)
            return (int) m;
        }
        return null;
    }

    // ═════════════════════════════ 계산 ═════════════════════════════

    static RecordPatternResponse compute(List<Entry> all, List<RoutineInfo> routines, List<RoutineDone> dones,
                                         LocalDate today) {
        // 미래 날짜 기록은 통계에서 뺀다 (입력 실수 방어)
        List<Entry> entries = all.stream().filter(e -> !e.date().isAfter(today))
                .sorted(Comparator.comparing(Entry::date)).toList();

        LocalDate recentStart = today.minusDays(WINDOW_DAYS - 1);
        LocalDate prevStart = recentStart.minusDays(WINDOW_DAYS);
        LocalDate prevEnd = recentStart.minusDays(1);

        Summary summary = new Summary(
                entries.size(), sumMinutes(entries), distinctDays(entries),
                entries.isEmpty() ? null : entries.get(0).date(),
                period(between(entries, recentStart, today), recentStart, today),
                period(between(entries, prevStart, prevEnd), prevStart, prevEnd),
                weekStreak(entries, today), !between(entries, weekStart(today), today).isEmpty());

        return new RecordPatternResponse(summary, rhythm(entries, today), when(entries),
                routines(routines, dones, today, recentStart, prevStart, prevEnd));
    }

    // ───────── 리듬 ─────────

    static Rhythm rhythm(List<Entry> entries, LocalDate today) {
        LocalDate thisWeek = weekStart(today);
        LocalDate heatmapStart = thisWeek.minusWeeks(HEATMAP_WEEKS - 1);

        Map<LocalDate, List<Entry>> byDay = between(entries, heatmapStart, today).stream()
                .collect(Collectors.groupingBy(Entry::date, TreeMap::new, Collectors.toList()));
        List<DayStat> days = byDay.entrySet().stream()
                .map(e -> new DayStat(e.getKey(), e.getValue().size(), sumMinutes(e.getValue())))
                .toList();

        LocalDate trendStart = thisWeek.minusWeeks(TREND_WEEKS - 1);
        List<Entry> before = entries.stream().filter(e -> e.date().isBefore(trendStart)).toList();
        int cumCount = before.size();
        long cumMinutes = sumMinutes(before);
        List<WeekStat> weeks = new ArrayList<>();
        for (LocalDate w = trendStart; !w.isAfter(thisWeek); w = w.plusWeeks(1)) {
            List<Entry> in = between(entries, w, w.plusDays(6));
            cumCount += in.size();
            cumMinutes += sumMinutes(in);
            weeks.add(new WeekStat(w, in.size(), sumMinutes(in), cumCount, cumMinutes));
        }
        return new Rhythm(heatmapStart, days, weeks);
    }

    /** 기록이 있는 주가 연속 몇 주인지. 이번 주가 비어 있으면 아직 끊긴 게 아니므로 지난주부터 센다 */
    static int weekStreak(List<Entry> entries, LocalDate today) {
        Set<LocalDate> weeks = entries.stream().map(e -> weekStart(e.date())).collect(Collectors.toSet());
        LocalDate w = weekStart(today);
        if (!weeks.contains(w)) w = w.minusWeeks(1);
        int streak = 0;
        while (weeks.contains(w)) {
            streak++;
            w = w.minusWeeks(1);
        }
        return streak;
    }

    // ───────── 언제 ─────────

    static When when(List<Entry> entries) {
        List<Entry> timed = entries.stream().filter(e -> e.band() != null).toList();
        List<WhenCell> cells = new ArrayList<>();
        DayOfWeek peakDay = null;
        String peakBand = null;
        int peak = 0;
        for (DayOfWeek d : DayOfWeek.values()) {
            for (String b : BANDS) {
                List<Entry> in = timed.stream()
                        .filter(e -> e.startAt().getDayOfWeek() == d && e.band().equals(b)).toList();
                cells.add(new WhenCell(d, b, in.size(), sumMinutes(in)));
                if (in.size() > peak) {
                    peak = in.size();
                    peakDay = d;
                    peakBand = b;
                }
            }
        }
        List<BandStat> bands = BANDS.stream().map(b -> {
            List<Entry> in = timed.stream().filter(e -> e.band().equals(b)).toList();
            return new BandStat(b, in.size(), sumMinutes(in));
        }).toList();
        return new When(cells, bands, peakDay, peakBand, entries.size() - timed.size());
    }

    // ───────── 루틴 ─────────

    static Routines routines(List<RoutineInfo> routines, List<RoutineDone> dones, LocalDate today,
                             LocalDate recentStart, LocalDate prevStart, LocalDate prevEnd) {
        Map<Long, List<RoutineDone>> byRoutine = dones.stream()
                .filter(d -> !d.date().isAfter(today))
                .collect(Collectors.groupingBy(RoutineDone::routineId));

        List<RoutineStat> items = routines.stream().map(r -> {
            List<RoutineDone> all = byRoutine.getOrDefault(r.id(), List.of());
            List<RoutineDone> recent = all.stream().filter(d -> !d.date().isBefore(recentStart)).toList();
            List<RoutineDone> previous = all.stream()
                    .filter(d -> !d.date().isBefore(prevStart) && !d.date().isAfter(prevEnd)).toList();
            return new RoutineStat(r.id(), r.name(), r.emoji(),
                    routineMinutes(all, r), all.size(),
                    routineMinutes(recent, r), recent.size(),
                    routineMinutes(previous, r), previous.size());
        }).sorted(Comparator.comparingLong(RoutineStat::minutes).reversed()
                .thenComparing(Comparator.comparingInt(RoutineStat::completedCount).reversed())).toList();

        Long rising = items.stream()
                .filter(s -> s.recentCount() > s.previousCount()
                        && (s.recentMinutes() - s.previousMinutes() >= RISING_MIN_MINUTES
                        || s.recentCount() - s.previousCount() >= RISING_MIN_COUNT))
                .max(Comparator.comparingInt((RoutineStat s) -> s.recentCount() - s.previousCount())
                        .thenComparingLong(s -> s.recentMinutes() - s.previousMinutes()))
                .map(RoutineStat::routineId).orElse(null);
        return new Routines(items, rising);
    }

    static long routineMinutes(List<RoutineDone> dones, RoutineInfo r) {
        long fallback = r.defaultMinutes() != null ? r.defaultMinutes() : 0;
        return dones.stream().mapToLong(d -> d.minutes() != null ? d.minutes() : fallback).sum();
    }

    // ───────── 작은 도구 ─────────

    static LocalDate weekStart(LocalDate d) {
        return d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    static List<Entry> between(List<Entry> entries, LocalDate from, LocalDate to) {
        return entries.stream().filter(e -> !e.date().isBefore(from) && !e.date().isAfter(to)).toList();
    }

    static long sumMinutes(List<Entry> entries) {
        return entries.stream().mapToLong(Entry::minutesOrZero).sum();
    }

    static int distinctDays(List<Entry> entries) {
        return (int) entries.stream().map(Entry::date).distinct().count();
    }

    static PeriodStat period(List<Entry> in, LocalDate start, LocalDate end) {
        return new PeriodStat(start, end, in.size(), sumMinutes(in), distinctDays(in));
    }
}
