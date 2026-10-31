package com.naleul.naleul.domain.goalCreation.personal;

import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto.PlanFit;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto.PlanTask;
import com.naleul.naleul.domain.goalCreation.dto.plan.Weekday;
import com.naleul.naleul.domain.pattern.calculator.HeatmapStats.TimeWindow;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.naleul.naleul.domain.goalCreation.personal.PersonalText.*;

/**
 * 초안 화면의 "나에게 맞춘 점".
 * AI 가 쓴 말이 아니라, 만들어진 계획을 서버가 직접 확인해서 쓴다 — 계획과 다른 말을 하지 않게.
 * (예: 골든 타임에 실제로 넣은 루틴이 있을 때만 "그 시간에 넣었어요")
 */
public final class PlanFitBuilder {

    private PlanFitBuilder() {
    }

    public static PlanFit build(GoalPlanDto plan, PersonalPlanContext ctx) {
        CapacityProfile p = ctx.profile();
        List<PlanTask> routines = plan.tasks().stream().filter(PlanTask::isRoutine).toList();
        int planWeekly = weeklyMinutes(routines);
        int cap = ctx.weeklyCapMinutes();
        List<String> notes = new ArrayList<>();

        // 1) 양 — 가장 중요한 근거
        String amount = planWeekly <= cap
                ? "이 목표 루틴은 주 " + minutes(planWeekly) + "로, " + ctx.intensity().label() + " 기준 상한(주 " + minutes(cap) + ") 안에 맞췄어요."
                : "권장 상한은 주 " + minutes(cap) + "인데 이 계획은 주 " + minutes(planWeekly) + "예요. 조금 줄이면 끝까지 가기 쉬워요.";
        if (p.hasHistory()) {
            notes.add("최근 4주 실행률 " + p.executionRate() + "%, 일주일에 " + minutes(p.weeklyDoneMinutes())
                    + " 정도 끝내고 있어요. " + amount);
        } else {
            notes.add("아직 실행 기록이 적어 가볍게 시작하도록 잡았어요. " + amount
                    + " 기록이 쌓이면 주간 점검에서 조정을 제안해 드려요.");
        }

        // 2) 이미 하고 있는 루틴만큼 여유를 남겼는지
        if (p.activeRoutineWeeklyMinutes() >= 360) {
            notes.add("이미 진행 중인 루틴이 " + p.activeRoutineCount() + "개(주 " + minutes(p.activeRoutineWeeklyMinutes())
                    + ") 있어서 그만큼 여유를 남겼어요.");
        }

        // 3) 잘 되는 시간에 실제로 넣었는지
        if (p.golden() != null) {
            long inGolden = routines.stream().filter(t -> inWindow(t, p.golden())).count();
            if (inGolden > 0) {
                notes.add(window(p.golden()) + "에 가장 잘 실행해서(" + p.golden().rate() + "%) 루틴 "
                        + inGolden + "개를 이 시간에 넣었어요.");
            }
        }

        // 4) 약한 요일을 실제로 비웠는지
        if (p.weakDay() != null && p.weakDayRate() != null && !routines.isEmpty()) {
            Weekday weak = Weekday.from(p.weakDay());
            boolean avoided = routines.stream()
                    .noneMatch(t -> t.routineDays() != null && t.routineDays().contains(weak));
            if (avoided) {
                notes.add(day(p.weakDay()) + "요일은 실행률이 낮아서(" + p.weakDayRate() + "%) 루틴을 비웠어요.");
            }
        }

        // 5) 같은 카테고리 달성자
        if (ctx.achievers() != null) {
            AchieverSummary a = ctx.achievers();
            String line = a.categoryLabel() + " 목표를 달성한 " + a.achievedCount() + "명은 보통 "
                    + Math.max(1, a.medianDays() / 7) + "주 동안, 주 " + minutes(a.medianWeeklyRoutineMinutes()) + " 정도 루틴을 했어요.";
            if (plan.goal() != null && plan.goal().startDate() != null && plan.goal().endDate() != null) {
                long days = ChronoUnit.DAYS.between(plan.goal().startDate(), plan.goal().endDate());
                if (days < a.medianDays() / 2) line += " 이 계획은 기간이 꽤 짧은 편이에요.";
            }
            notes.add(line);
        }

        return new PlanFit(ctx.intensity().name(), cap, planWeekly, p.hasHistory(), List.copyOf(notes));
    }

    /** 루틴 주간 합계(분) — 검증기 V6-1 과 같은 계산 */
    public static int weeklyMinutes(List<PlanTask> routines) {
        return routines.stream()
                .filter(t -> t.durationMinutes() != null && t.routineDays() != null)
                .mapToInt(t -> t.durationMinutes() * (int) t.routineDays().stream().filter(Objects::nonNull).distinct().count())
                .sum();
    }

    /** 루틴 시작 시각이 그 시간대 안이고, 요일 중 하나라도 그 묶음(평일/주말)에 들면 */
    static boolean inWindow(PlanTask t, TimeWindow w) {
        if (t.preferredStartTime() == null || t.routineDays() == null) return false;
        int h = t.preferredStartTime().getHour();
        if (h < w.startHour() || h >= w.endHour()) return false;
        return t.routineDays().stream().filter(Objects::nonNull).anyMatch(d -> w.dayGroup().contains(d.toDayOfWeek()));
    }
}
