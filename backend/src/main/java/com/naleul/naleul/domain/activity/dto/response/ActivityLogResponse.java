package com.naleul.naleul.domain.activity.dto.response;

import java.util.List;

/**
 * 빠른 기록 결과.
 * @param activities 저장된 기록 (요청 순서)
 * @param tempGoals  이번에 새로 만든 임시 목표
 */
public record ActivityLogResponse(List<ActualActivityResponse> activities, List<CreatedTempGoal> tempGoals) {

    public record CreatedTempGoal(String tempKey, Long goalCategoryId, String name, String emoji, String colorCode) {
    }
}
