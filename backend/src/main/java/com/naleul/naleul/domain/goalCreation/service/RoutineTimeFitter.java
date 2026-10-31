package com.naleul.naleul.domain.goalCreation.service;

import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto.PlanTask;
import com.naleul.naleul.domain.goalCreation.dto.plan.Weekday;
import com.naleul.naleul.domain.goalCreation.dto.slot.TimeRange;
import com.naleul.naleul.domain.lifePattern.service.FixedBlockResolver;
import com.naleul.naleul.domain.lifePattern.support.DayFixedBlocks;
import com.naleul.naleul.domain.lifePattern.support.FixedBlock;
import com.naleul.naleul.domain.planner.entity.PlannerSetting;
import com.naleul.naleul.domain.planner.support.DateTimeRange;
import com.naleul.naleul.domain.routine.dto.response.RoutineResponse;
import com.naleul.naleul.domain.routine.enums.RoutineStatus;
import com.naleul.naleul.domain.routine.reader.RoutineReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * AI 초안 루틴의 시작 시각을 실제 하루에 맞춘다 (2026-10, TimeTable 자동 배치 규칙과 같은 기준).
 *
 * AI 는 인터뷰 답(선호 시간대 · 불가능한 시간)만 보고 시각을 정해서, 점심 · 저녁 같은 고정 시간이나
 * 이미 하고 있는 다른 목표의 루틴과 겹치는 시각을 고를 때가 있다. 겹친 채로 확정되면 그 시각에 잠겨서
 * 자동 배치도 옮기지 못한다.
 *
 *  1. 루틴 요일마다(다음 7일 중 그 요일) 고정 시간 · 하루 범위(기상~취침) · 다른 루틴(앞뒤 쉬는 시간 20분)을 본다
 *  2. AI 가 고른 시각이 모든 요일에 들어가면 그대로 둔다
 *  3. 아니면 모든 요일에 들어가는 시각 중 AI 시각과 가장 가까운 곳으로 옮긴다
 *     — 사용자가 말한 선호 시간대 안을 먼저, 같은 목표의 다른 루틴과 이어지는 시각을 먼저 (같은 목표는 묶어서)
 *  4. 한 번에 할 연속된 시간이 어느 요일에도 없으면(4시간짜리인데 점심이 끼는 등) 시각을 비운다
 *     → 날마다 자동 배치가 빈 시간에 넣고, 필요하면 점심 앞뒤로 나눠 넣는다
 *  옮기거나 비우면 루틴 reason 에 한 문장을 덧붙여 초안 검토 화면에서 보이게 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoutineTimeFitter {

    static final int STEP_MINUTES = 10;
    /** 루틴 사이 쉬는 시간 — 자동 배치 기본값과 같게 */
    static final int GAP_MINUTES = PlannerSetting.DEFAULT_GAP_MINUTES;
    /** 같은 목표 루틴과 이 안으로 이어지면 "묶였다"고 본다 */
    static final int GROUP_NEAR_MINUTES = 30;

    private final FixedBlockResolver fixedBlockResolver;
    private final RoutineReader routineReader;

    /** 사용자 하루 정보가 없거나 실패하면 계획을 그대로 돌려준다 (초안 생성은 막지 않음) */
    public GoalPlanDto fit(Long userId, GoalPlanDto plan, LocalDate today, List<TimeRange> preferred) {
        if (userId == null || plan == null || plan.tasks() == null) return plan;
        try {
            Map<DayOfWeek, Day> days = days(userId, today);
            addExistingRoutines(userId, today, days);
            return fit(plan, days, preferred);
        } catch (RuntimeException e) {
            log.warn("[RoutineTimeFitter] 시각 맞추기 실패 → AI 시각 그대로 userId={}", userId, e);
            return plan;
        }
    }

    // ── 계산 (DB 없이, 테스트 대상) ─────────────────────────

    /** 요일 하나의 하루: 기준 날짜 · 하루 범위 · 고정 시간 · 다른 루틴(쉬는 시간 포함) · 이번 초안 루틴 */
    static final class Day {
        final LocalDate date;
        final DateTimeRange range;
        final List<DateTimeRange> fixed = new ArrayList<>();
        final List<DateTimeRange> routines = new ArrayList<>();
        final List<DateTimeRange> planned = new ArrayList<>();

        Day(LocalDate date, DateTimeRange range) {
            this.date = date;
            this.range = range;
        }

        boolean fits(LocalDateTime s, LocalDateTime e) {
            if (s.isBefore(range.start()) || e.isAfter(range.end())) return false;
            DateTimeRange r = new DateTimeRange(s, e);
            for (DateTimeRange f : fixed) if (r.overlaps(f)) return false;
            DateTimeRange padded = new DateTimeRange(s.minusMinutes(GAP_MINUTES), e.plusMinutes(GAP_MINUTES));
            for (DateTimeRange o : routines) if (padded.overlaps(o)) return false;
            for (DateTimeRange o : planned) if (padded.overlaps(o)) return false;
            return true;
        }

        boolean nearPlanned(LocalDateTime s, LocalDateTime e) {
            for (DateTimeRange o : planned) {
                long before = ChronoUnit.MINUTES.between(o.end(), s);
                long after = ChronoUnit.MINUTES.between(e, o.start());
                if ((before >= 0 && before <= GROUP_NEAR_MINUTES) || (after >= 0 && after <= GROUP_NEAR_MINUTES)) return true;
            }
            return false;
        }
    }

    static GoalPlanDto fit(GoalPlanDto plan, Map<DayOfWeek, Day> days, List<TimeRange> preferred) {
        List<PlanTask> out = new ArrayList<>();
        boolean changed = false;
        for (PlanTask t : plan.tasks()) {
            if (!t.isRoutine() || t.preferredStartTime() == null || t.durationMinutes() == null
                    || t.durationMinutes() <= 0 || t.routineDays() == null || t.routineDays().isEmpty()) {
                out.add(t);
                continue;
            }
            List<Day> its = t.routineDays().stream().filter(Objects::nonNull).distinct()
                    .map(w -> days.get(w.toDayOfWeek())).filter(Objects::nonNull).toList();
            if (its.isEmpty()) {
                out.add(t);
                continue;
            }
            int minutes = t.durationMinutes();
            LocalTime pref = t.preferredStartTime().withSecond(0).withNano(0);
            LocalTime chosen = fitsAll(its, pref, minutes) ? pref : nearest(its, pref, minutes, preferred);
            if (chosen != null) {
                for (Day d : its) d.planned.add(slot(d, chosen, minutes));
            }
            if (Objects.equals(chosen, pref)) {
                out.add(t);
                continue;
            }
            changed = true;
            String note = chosen != null
                    ? "점심 같은 고정 시간이나 다른 루틴과 겹치지 않게 " + hm(chosen) + "로 맞췄어요."
                    : "하루에 " + minutes + "분을 한 번에 할 빈 시간이 없어, 날마다 빈 시간에 맞춰 넣을게요 (필요하면 점심 앞뒤로 나눠서).";
            out.add(new PlanTask(t.tempId(), t.type(), t.subGoalTempId(), t.milestoneTempId(), t.title(), t.emoji(),
                    t.durationMinutes(), t.routineDays(), chosen, t.startDate(), t.endDate(), t.scheduledDate(),
                    t.dueDate(), t.description(), t.reason() == null || t.reason().isBlank() ? note : t.reason().strip() + " " + note));
        }
        if (!changed) return plan;
        return new GoalPlanDto(plan.goal(), plan.subGoals(), plan.milestones(), out, plan.warnings(), plan.fit());
    }

    static boolean fitsAll(List<Day> days, LocalTime start, int minutes) {
        for (Day d : days) {
            DateTimeRange r = slot(d, start, minutes);
            if (!d.fits(r.start(), r.end())) return false;
        }
        return true;
    }

    /**
     * 모든 요일에 들어가는 시각 중 고르기. 점수가 낮을수록 좋다:
     *  AI 시각과의 거리(분) + 선호 시간대 밖이면 +180 − 같은 목표 루틴과 이어지면 60. 같으면 이른 시각
     */
    static LocalTime nearest(List<Day> days, LocalTime pref, int minutes, List<TimeRange> preferred) {
        LocalTime best = null;
        long bestCost = Long.MAX_VALUE;
        for (int m = 0; m < 24 * 60; m += STEP_MINUTES) {
            LocalTime t = LocalTime.of(m / 60, m % 60);
            if (!fitsAll(days, t, minutes)) continue;
            long cost = Math.abs(m - (pref.getHour() * 60L + pref.getMinute()));
            if (preferred != null && !preferred.isEmpty() && preferred.stream().noneMatch(r -> r.contains(t))) cost += 180;
            boolean grouped = days.stream().anyMatch(d -> {
                DateTimeRange r = slot(d, t, minutes);
                return d.nearPlanned(r.start(), r.end());
            });
            if (grouped) cost -= 60;
            if (cost < bestCost) {
                bestCost = cost;
                best = t;
            }
        }
        return best;
    }

    private static DateTimeRange slot(Day d, LocalTime start, int minutes) {
        LocalDateTime s = d.date.atTime(start);
        // 기상 전 새벽 시각(00~04시대)은 하루 범위 기준으로 다음 날 새벽으로 본다 (밤샘 공부 등)
        if (s.isBefore(d.range.start()) && d.date.plusDays(1).atTime(start).isBefore(d.range.end())) {
            s = d.date.plusDays(1).atTime(start);
        }
        return new DateTimeRange(s, s.plusMinutes(minutes));
    }

    private static String hm(LocalTime t) {
        return String.format("%02d:%02d", t.getHour(), t.getMinute());
    }

    // ── DB 읽기 ──────────────────────────────────────────

    private Map<DayOfWeek, Day> days(Long userId, LocalDate today) {
        Map<LocalDate, DayFixedBlocks> blocks = fixedBlockResolver.resolve(userId, today, today.plusDays(6));
        Map<DayOfWeek, Day> days = new EnumMap<>(DayOfWeek.class);
        for (LocalDate d = today; d.isBefore(today.plusDays(7)); d = d.plusDays(1)) {
            DayFixedBlocks f = blocks.get(d);
            if (f == null || f.dayRange() == null) continue;
            Day day = new Day(d, f.dayRange());
            for (FixedBlock b : f.busyBlocks()) day.fixed.add(b.range());
            days.put(d.getDayOfWeek(), day);
        }
        return days;
    }

    /** 이미 진행 중인 다른 루틴 (시간이 정해진 것만) */
    private void addExistingRoutines(Long userId, LocalDate today, Map<DayOfWeek, Day> days) {
        for (RoutineResponse r : routineReader.getAllRoutinesForDisplay(userId)) {
            boolean live = (r.getRoutineStatus() == RoutineStatus.NOT_STARTED || r.getRoutineStatus() == RoutineStatus.IN_PROGRESS)
                    && (r.getRepeatEndDate() == null || !r.getRepeatEndDate().isBefore(today));
            if (!live || r.getRepeatDays() == null) continue;
            for (RoutineResponse.RepeatDayInfo info : r.getRepeatDays()) {
                Day d = days.get(info.getDayOfWeek());
                LocalTime s = info.getStartTime() != null ? info.getStartTime() : r.getRepeatStartTime();
                LocalTime e = info.getEndTime() != null ? info.getEndTime() : r.getRepeatEndTime();
                if (d == null || s == null || e == null) continue;
                LocalDateTime start = d.date.atTime(s);
                LocalDateTime end = e.isAfter(s) ? d.date.atTime(e) : d.date.plusDays(1).atTime(e);
                d.routines.add(new DateTimeRange(start, end));
            }
        }
    }

    /** Weekday ↔ DayOfWeek (테스트 편의) */
    static DayOfWeek dow(Weekday w) {
        return w.toDayOfWeek();
    }
}
