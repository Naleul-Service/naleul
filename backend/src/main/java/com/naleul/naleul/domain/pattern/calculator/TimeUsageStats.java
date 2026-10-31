package com.naleul.naleul.domain.pattern.calculator;

import java.util.List;

/** 어디에 시간을 쓰고 있나요? (명세 4 · 2-5) */
public record TimeUsageStats(double weeklyTotalHours, List<Goal> goals) {

    /**
     * @param planRatio    실제 / 계획 (%), 계획 시간이 없으면 null
     * @param newTemporary 기간 안에 만들어진 임시 목표
     */
    public record Goal(Long goalCategoryId, String name, String emoji, String colorCode,
                       double actualHoursPerWeek, double plannedHoursPerWeek, Integer planRatio,
                       boolean temporary, boolean newTemporary) {
    }
}
