package com.naleul.naleul.domain.goalCreation.service;

import com.naleul.naleul.domain.goalCreation.entity.GoalCreationSession;
import com.naleul.naleul.domain.goalCreation.repository.GoalCreationSessionRepository;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 소유권까지 확인된 세션 조회 (CategoryFinder와 같은 역할).
 * 남의 세션이면 403이 아니라 404 — "그런 세션이 있다"는 사실 자체를 숨기기 위함 (명세 7 공통).
 */
@Component
@RequiredArgsConstructor
public class GoalCreationSessionFinder {

    private final GoalCreationSessionRepository sessionRepository;

    public GoalCreationSession findOwned(Long sessionId, Long userId) {
        return sessionRepository.findByGoalCreationSessionIdAndUser_UserId(sessionId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.GOAL_SESSION_NOT_FOUND));
    }
}
