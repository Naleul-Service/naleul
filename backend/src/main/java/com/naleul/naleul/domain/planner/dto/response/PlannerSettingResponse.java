package com.naleul.naleul.domain.planner.dto.response;

import com.naleul.naleul.domain.planner.entity.PlannerSetting;

public record PlannerSettingResponse(
        boolean autoGenerateEnabled,
        boolean nightlyPushEnabled,
        int taskGapMinutes,
        /** 못 한 일을 다음 날 아침 자동 이월 (2026-10) */
        boolean autoCarryEnabled
) {
    public static PlannerSettingResponse from(PlannerSetting s) {
        return new PlannerSettingResponse(s.isAutoGenerateEnabled(), s.isNightlyPushEnabled(), s.getTaskGapMinutes(),
                s.isAutoCarryEnabled());
    }
}
