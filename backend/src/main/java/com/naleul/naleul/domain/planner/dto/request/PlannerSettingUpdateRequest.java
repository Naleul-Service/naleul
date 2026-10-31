package com.naleul.naleul.domain.planner.dto.request;

/** null 인 항목은 그대로 둔다. 예: { "autoCarryEnabled": false } */
public record PlannerSettingUpdateRequest(
        Boolean autoGenerateEnabled,
        Boolean nightlyPushEnabled,
        /** 못 한 일을 다음 날 아침 자동 이월 (2026-10) */
        Boolean autoCarryEnabled
) {
    /** 예전 모양 (자동 이월 없음) */
    public PlannerSettingUpdateRequest(Boolean autoGenerateEnabled, Boolean nightlyPushEnabled) {
        this(autoGenerateEnabled, nightlyPushEnabled, null);
    }
}
