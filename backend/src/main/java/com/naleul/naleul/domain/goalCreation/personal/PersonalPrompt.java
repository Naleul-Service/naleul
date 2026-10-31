package com.naleul.naleul.domain.goalCreation.personal;

import static com.naleul.naleul.domain.goalCreation.personal.PersonalText.*;

/**
 * 계획 AI 에 넘기는 개인 맞춤 블록.
 * 숫자는 서버가 계산한 값 그대로 넣고, 어떻게 쓸지는 plan-system.txt 의 [개인 맞춤 원칙]이 정한다.
 */
public final class PersonalPrompt {

    private PersonalPrompt() {
    }

    public static String forPlan(PersonalPlanContext ctx) {
        StringBuilder sb = new StringBuilder();
        CapacityProfile p = ctx.profile();

        sb.append("[이 사용자의 최근 4주 실행 기록]\n");
        if (p.hasHistory()) {
            sb.append("- 실행률 ").append(p.executionRate()).append("% (계획한 일 중 실제로 한 비율)\n");
            sb.append("- 일주일에 평균 ").append(minutes(p.weeklyDoneMinutes())).append(" 어치 할 일을 끝냄\n");
        } else {
            sb.append("- 아직 실행 기록이 거의 없어요. [사용자가 인터뷰에서 한 말]의 현재 수준(지금 얼마나 하고 있는지)을 출발점으로 삼아.\n");
        }
        if (p.activeRoutineCount() > 0) {
            sb.append("- 이미 진행 중인 루틴 ").append(p.activeRoutineCount()).append("개, 주 ")
                    .append(minutes(p.activeRoutineWeeklyMinutes())).append("\n");
        }
        if (p.golden() != null) {
            sb.append("- 가장 잘 지키는 시간: ").append(window(p.golden()))
                    .append(" (실행률 ").append(p.golden().rate()).append("%)\n");
        }
        if (p.weak() != null) {
            sb.append("- 자주 놓치는 시간: ").append(window(p.weak()))
                    .append(" (실행률 ").append(p.weak().rate()).append("%)\n");
        }
        if (p.weakDay() != null && p.weakDayRate() != null) {
            sb.append("- 실행률이 낮은 요일: ").append(day(p.weakDay())).append("요일 (")
                    .append(p.weakDayRate()).append("%, 요일 평균 ").append(p.averageDayRate()).append("%)\n");
        }
        sb.append('\n');

        sb.append("[계획 강도]\n").append(ctx.intensity().label()).append(" — ")
                .append(ctx.intensity() == PlanIntensity.STEADY
                        ? "지금 실제로 하는 양 근처에서 시작해 천천히 늘려. 첫 2주 루틴 합계는 아래 상한의 60% 이내."
                        : "조금 더 빠르게 늘려도 돼. 첫 주부터 아래 상한의 80%까지 써도 돼.")
                .append("\n\n");

        if (ctx.achievers() != null) {
            AchieverSummary a = ctx.achievers();
            sb.append("[같은 카테고리(").append(a.categoryLabel()).append(") 목표를 달성한 사용자 ")
                    .append(a.achievedCount()).append("명의 익명 집계]\n");
            sb.append("- 목표 기간 중앙값 ").append(a.medianDays() / 7).append("주\n");
            sb.append("- 루틴 주간 합계 중앙값 ").append(minutes(a.medianWeeklyRoutineMinutes()))
                    .append(", 루틴 ").append(a.medianRoutineCount()).append("개\n");
            sb.append("- 끝난 목표 중 달성 비율 ").append(a.achievedRatePercent()).append("%\n\n");
        }
        return sb.toString();
    }

    /** 인터뷰 AI 용 한 줄 — 기록이 없을 때만 "현재 수준"을 꼭 묻게 한다 */
    public static String forInterview(CapacityProfile p) {
        if (p != null && p.hasHistory()) {
            return "이 사용자는 최근 4주 실행률 " + p.executionRate() + "%, 일주일에 "
                    + minutes(p.weeklyDoneMinutes()) + " 정도 할 일을 끝내고 있어. 현재 수준은 따로 묻지 않아도 돼.";
        }
        return "이 사용자는 아직 실행 기록이 없어. practicePreference 를 물을 때 지금 이 일을 일주일에 얼마나 하고 있는지(현재 수준)도 한 문장으로 함께 물어.";
    }
}
