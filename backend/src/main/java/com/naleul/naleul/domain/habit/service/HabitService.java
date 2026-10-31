package com.naleul.naleul.domain.habit.service;

import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.habit.dto.request.HabitCreateRequest;
import com.naleul.naleul.domain.habit.dto.request.HabitOrderRequest;
import com.naleul.naleul.domain.habit.dto.request.HabitUpdateRequest;
import com.naleul.naleul.domain.habit.dto.response.HabitHeatmapResponse;
import com.naleul.naleul.domain.habit.dto.response.HabitHomeResponse;
import com.naleul.naleul.domain.habit.dto.response.HabitResponse;
import com.naleul.naleul.domain.habit.entity.Habit;
import com.naleul.naleul.domain.habit.entity.HabitCheck;
import com.naleul.naleul.domain.habit.entity.HabitPause;
import com.naleul.naleul.domain.habit.enums.HabitDayState;
import com.naleul.naleul.domain.habit.repository.HabitCheckRepository;
import com.naleul.naleul.domain.habit.repository.HabitPauseRepository;
import com.naleul.naleul.domain.habit.repository.HabitRepository;
import com.naleul.naleul.domain.habit.support.HabitCalendar;
import com.naleul.naleul.domain.habit.support.HabitCalendar.HabitView;
import com.naleul.naleul.domain.habit.support.HabitCalendar.Period;
import com.naleul.naleul.domain.planner.support.PlannerClock;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.service.UserFinder;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 기본 생활 습관 (V52) — 추가 · 수정 · 쉬기 · 다시 시작 · 삭제 · 체크 · 목록 · 잔디맵.
 * 습관은 사용자의 기본 생활 습관 그릇(HabitGoalProvisioner) 아래에 담긴다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class HabitService {

    /** 한 사람이 가질 수 있는 습관 수 (플랜과 상관없이 — 체크 목록이 너무 길면 오히려 안 본다) */
    static final int MAX_HABITS = 30;
    /** 잔디맵 기본 기간: 오늘까지 26주 */
    static final int DEFAULT_HEATMAP_DAYS = 182;
    static final int MAX_HEATMAP_DAYS = 400;
    static final int RATE_DAYS = 30;

    private final HabitRepository habitRepository;
    private final HabitCheckRepository checkRepository;
    private final HabitPauseRepository pauseRepository;
    private final HabitGoalProvisioner provisioner;
    private final UserFinder userFinder;
    private final PlannerClock clock;

    // ── 조회 ──────────────────────────────────────────────

    /** 습관 화면: date(기본 오늘) 기준으로 해야 하는지 · 했는지 + 연속 기록 · 30일 실천률 · 최근 7일 */
    public HabitHomeResponse home(Long userId, LocalDate date) {
        User user = userFinder.findUser(userId);
        HabitGoalProvisioner.HabitHome home = provisioner.ensure(user);
        LocalDate today = clock.today();
        LocalDate target = date == null || date.isAfter(today) ? today : date;

        List<Habit> habits = habitRepository.findAllOfUser(userId);
        // 연속 기록(오늘부터 400일) + 기준 날짜의 최근 7일이 모두 들어가게
        LocalDate loadFrom = earliest(target.minusDays(6), today.minusDays(HabitCalendar.STREAK_LOOKBACK_DAYS));
        Map<Long, HabitView> views = views(habits, loadFrom, today);

        List<HabitResponse> rows = habits.stream().map(h -> toResponse(h, views.get(h.getHabitId()), target, today)).toList();
        int scheduled = (int) rows.stream().filter(HabitResponse::scheduled).count();
        int done = (int) rows.stream().filter(r -> r.scheduled() && r.checked()).count();
        return new HabitHomeResponse(home.goalCategoryId(), home.name(), home.emoji(),
                target, today, scheduled, done, rows);
    }

    /** 잔디맵. 기간을 안 주면 오늘까지 26주. 최대 400일 */
    @Transactional(readOnly = true)
    public HabitHeatmapResponse heatmap(Long userId, LocalDate from, LocalDate to) {
        userFinder.validateUserExists(userId);
        LocalDate today = clock.today();
        LocalDate end = to == null || to.isAfter(today) ? today : to;
        LocalDate start = from == null ? end.minusDays(DEFAULT_HEATMAP_DAYS - 1L) : from;
        if (start.isAfter(end) || ChronoUnit.DAYS.between(start, end) >= MAX_HEATMAP_DAYS) {
            throw new CustomException(ErrorCode.HABIT_HEATMAP_INVALID_PERIOD);
        }

        List<Habit> habits = habitRepository.findAllOfUser(userId);
        LocalDate loadFrom = earliest(start, today.minusDays(HabitCalendar.STREAK_LOOKBACK_DAYS));
        Map<Long, HabitView> views = views(habits, loadFrom, today);

        List<HabitHeatmapResponse.Day> days = new ArrayList<>();
        List<List<HabitDayState>> cellsByHabit = new ArrayList<>();
        habits.forEach(h -> cellsByHabit.add(new ArrayList<>()));
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            int done = 0;
            int scheduled = 0;
            for (int i = 0; i < habits.size(); i++) {
                HabitView v = views.get(habits.get(i).getHabitId());
                HabitDayState s = HabitCalendar.stateOf(v, d, today);
                cellsByHabit.get(i).add(s);
                if (s == HabitDayState.DONE) done++;
                // 분모 = 그날 해야 했던 습관 (반복 요일이 아닌 날 더 한 건 분자에만). 오늘 아직 안 한 것도 넣는다
                if (v.scheduled(d)) scheduled++;
            }
            days.add(new HabitHeatmapResponse.Day(d, done, scheduled, HabitCalendar.level(done, scheduled)));
        }

        List<HabitHeatmapResponse.HabitRow> rows = new ArrayList<>();
        for (int i = 0; i < habits.size(); i++) {
            Habit h = habits.get(i);
            HabitView v = views.get(h.getHabitId());
            List<HabitDayState> cells = cellsByHabit.get(i);
            int doneDays = (int) cells.stream().filter(s -> s == HabitDayState.DONE).count();
            rows.add(new HabitHeatmapResponse.HabitRow(h.getHabitId(), h.getHabitName(), h.getEmoji(), h.getHabitStatus(),
                    HabitCalendar.streak(v, today), HabitCalendar.rate(v, start, end, today),
                    doneDays, cells));
        }
        return new HabitHeatmapResponse(start, end, today, days, rows);
    }

    /**
     * 캘린더용: [from, to] 에서 시간대를 정한 습관이 "해야 하는 날"의 블록 (V56).
     * 쓰기 없음 (provisioner 를 부르지 않는다) — TimeTable 조회 · 자동 배치에서 쓴다.
     */
    @Transactional(readOnly = true)
    public List<HabitSlot> timedSlots(Long userId, LocalDate from, LocalDate to) {
        List<Habit> timed = habitRepository.findAllOfUser(userId).stream()
                .filter(h -> h.isActive() && h.hasTimeRange())
                .toList();
        if (timed.isEmpty()) return List.of();
        Map<Long, HabitView> views = views(timed, from, to);
        List<HabitSlot> slots = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            for (Habit h : timed) {
                HabitView v = views.get(h.getHabitId());
                if (!v.scheduled(d)) continue;
                slots.add(new HabitSlot(h.getHabitId(), h.getHabitName(), h.getEmoji(), d,
                        d.atTime(h.getStartTime()), d.atTime(h.getEndTime()), v.checked(d)));
            }
        }
        return slots;
    }

    /** 습관 시간 블록 하나 */
    public record HabitSlot(Long habitId, String name, String emoji, LocalDate date,
                            java.time.LocalDateTime start, java.time.LocalDateTime end, boolean checked) {
    }

    // ── 추가 · 수정 ───────────────────────────────────────

    public HabitResponse create(Long userId, HabitCreateRequest request) {
        User user = userFinder.findUser(userId);
        GoalCategory home = provisioner.reference(provisioner.ensure(user));
        if (habitRepository.countOfUser(userId) >= MAX_HABITS) {
            throw new CustomException(ErrorCode.HABIT_LIMIT_EXCEEDED);
        }
        if (!Habit.isValidTimeRange(request.startTime(), request.endTime())) {
            throw new CustomException(ErrorCode.HABIT_INVALID_TIME_RANGE);
        }
        int order = habitRepository.findMaxSortOrder(userId) + 1;
        Habit habit = Habit.create(user, home, request.name(), request.emoji(),
                request.repeatDays(), request.remindTime(), order, clock.today());
        habit.changeTimeRange(request.startTime(), request.endTime());
        habit = habitRepository.save(habit);
        return single(habit);
    }

    public HabitResponse update(Long userId, Long habitId, HabitUpdateRequest request) {
        Habit habit = find(userId, habitId);
        habit.update(request.name(), request.emoji(), request.repeatDays(), request.remindTime(),
                Boolean.TRUE.equals(request.clearRemindTime()));
        if (Boolean.TRUE.equals(request.clearTimeRange())) {
            habit.changeTimeRange(null, null);
        } else if (request.startTime() != null || request.endTime() != null) {
            if (!Habit.isValidTimeRange(request.startTime(), request.endTime())) {
                throw new CustomException(ErrorCode.HABIT_INVALID_TIME_RANGE);
            }
            habit.changeTimeRange(request.startTime(), request.endTime());
        }
        return single(habit);
    }

    /** 쉬기 — 쉬는 동안은 해야 하는 날로 세지 않는다. 이미 쉬는 중이면 그대로 */
    public HabitResponse pause(Long userId, Long habitId) {
        Habit habit = find(userId, habitId);
        if (habit.isActive()) {
            habit.pause();
            pauseRepository.save(HabitPause.start(habit, clock.today()));
        }
        return single(habit);
    }

    /** 다시 시작 — 오늘부터 다시 해야 하는 날 */
    public HabitResponse resume(Long userId, Long habitId) {
        Habit habit = find(userId, habitId);
        if (habit.isPaused()) {
            habit.resume();
            pauseRepository.findOpen(habitId).ifPresent(p -> {
                p.close(clock.today());
                if (p.isEmpty()) pauseRepository.delete(p);   // 같은 날 쉬었다 다시 시작
            });
        }
        return single(habit);
    }

    /** 지우기 (체크 기록은 남긴다) */
    public void delete(Long userId, Long habitId) {
        find(userId, habitId).delete();
    }

    /** 순서 바꾸기 — 보낸 순서대로 0, 1, 2… 빠진 습관은 그 뒤에 원래 순서대로 */
    public HabitHomeResponse reorder(Long userId, HabitOrderRequest request) {
        List<Habit> habits = habitRepository.findAllOfUser(userId);
        Map<Long, Habit> byId = new HashMap<>();
        habits.forEach(h -> byId.put(h.getHabitId(), h));
        Set<Long> ordered = new LinkedHashSet<>();
        for (Long id : request.habitIds()) {
            if (byId.containsKey(id)) ordered.add(id);
        }
        habits.forEach(h -> ordered.add(h.getHabitId()));
        int i = 0;
        for (Long id : ordered) byId.get(id).changeSortOrder(i++);
        return home(userId, null);
    }

    // ── 체크 ──────────────────────────────────────────────

    /**
     * 했어요 — 같은 날 두 번 눌러도 1행. 미래 날짜는 안 됨. 만든 날보다 이전이면 시작일을 당긴다.
     * 버튼을 빠르게 두 번 눌러 요청이 동시에 와도 500 이 나지 않게 INSERT IGNORE 로 넣는다 (uk_habit_check_day).
     */
    public HabitResponse check(Long userId, Long habitId, LocalDate date) {
        Habit habit = find(userId, habitId);
        LocalDate day = date == null ? clock.today() : date;
        if (day.isAfter(clock.today())) throw new CustomException(ErrorCode.HABIT_CHECK_FUTURE_DATE);
        habit.extendStartTo(day);
        checkRepository.insertIgnore(habitId, userId, day, clock.now());
        return single(habit, day);
    }

    /** 체크 풀기 */
    public HabitResponse uncheck(Long userId, Long habitId, LocalDate date) {
        Habit habit = find(userId, habitId);
        LocalDate day = date == null ? clock.today() : date;
        checkRepository.deleteOne(habitId, day);
        return single(habit, day);
    }

    // ── 내부 ──────────────────────────────────────────────

    private Habit find(Long userId, Long habitId) {
        return habitRepository.findOwned(habitId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.HABIT_NOT_FOUND));
    }

    private HabitResponse single(Habit habit) {
        return single(habit, clock.today());
    }

    private HabitResponse single(Habit habit, LocalDate date) {
        LocalDate today = clock.today();
        checkRepository.flush();
        LocalDate loadFrom = earliest(date.minusDays(6), today.minusDays(HabitCalendar.STREAK_LOOKBACK_DAYS));
        HabitView v = views(List.of(habit), loadFrom, today)
                .get(habit.getHabitId());
        return toResponse(habit, v, date, today);
    }

    /** 습관들의 체크 · 쉰 기간을 한 번에 읽어 계산용 묶음으로 */
    Map<Long, HabitView> views(Collection<Habit> habits, LocalDate from, LocalDate to) {
        Map<Long, HabitView> result = new HashMap<>();
        if (habits.isEmpty()) return result;
        List<Long> ids = habits.stream().map(Habit::getHabitId).toList();
        Map<Long, Set<LocalDate>> checks = new HashMap<>();
        for (HabitCheck c : checkRepository.findBetween(ids, from, to)) {
            checks.computeIfAbsent(c.getHabit().getHabitId(), k -> new HashSet<>()).add(c.getCheckDate());
        }
        Map<Long, List<Period>> pauses = new HashMap<>();
        for (HabitPause p : pauseRepository.findAllOf(ids)) {
            if (p.isEmpty()) continue;
            pauses.computeIfAbsent(p.getHabit().getHabitId(), k -> new ArrayList<>())
                    .add(new Period(p.getStartDate(), p.getEndDate()));
        }
        for (Habit h : habits) {
            result.put(h.getHabitId(), new HabitView(h.getStartDate(), h.repeatDaySet(),
                    checks.getOrDefault(h.getHabitId(), Set.of()), pauses.getOrDefault(h.getHabitId(), List.of())));
        }
        return result;
    }

    static HabitResponse toResponse(Habit h, HabitView v, LocalDate date, LocalDate today) {
        List<HabitResponse.DayCell> recent = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = date.minusDays(i);
            recent.add(new HabitResponse.DayCell(d, HabitCalendar.stateOf(v, d, today)));
        }
        return new HabitResponse(
                h.getHabitId(), h.getHabitName(), h.getEmoji(), h.repeatDayList(), h.isDaily(), h.getRemindTime(),
                h.getHabitStatus(), h.getStartDate(), h.getSortOrder(),
                v.scheduled(date), v.checked(date), HabitCalendar.stateOf(v, date, today),
                HabitCalendar.streak(v, today), HabitCalendar.rate(v, today.minusDays(RATE_DAYS - 1L), today),
                recent, h.getStartTime(), h.getEndTime());
    }

    private static LocalDate earliest(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }
}
