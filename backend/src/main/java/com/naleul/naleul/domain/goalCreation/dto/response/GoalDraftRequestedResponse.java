package com.naleul.naleul.domain.goalCreation.dto.response;

import com.naleul.naleul.domain.goalCreation.enums.GoalDraftStatus;

/** 202 응답. 클라이언트는 pollAfterMs 뒤부터 GET drafts/{draftId}로 폴링 */
public record GoalDraftRequestedResponse(Long draftId, int version, GoalDraftStatus status, long pollAfterMs) {
}
