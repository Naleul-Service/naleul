package com.naleul.naleul.domain.goalCreation.dto.response;

import com.naleul.naleul.domain.goalCreation.entity.GoalDraft;
import com.naleul.naleul.domain.goalCreation.enums.GoalDraftStatus;

/** 이어하기용 최신 초안 정보 (내용 없이 id·버전·상태만) */
public record GoalDraftBriefResponse(Long draftId, int version, GoalDraftStatus status) {

    public static GoalDraftBriefResponse from(GoalDraft draft) {
        return new GoalDraftBriefResponse(draft.getGoalDraftId(), draft.getVersion(), draft.getStatus());
    }
}
