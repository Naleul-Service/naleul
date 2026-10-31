package com.naleul.naleul.domain.goalCreation.dto.request;

import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 확정 요청 (명세 7-8).
 * @param draftId                     지금 화면에 띄운 초안 id (최신 초안인지 확인용)
 * @param plan                        사용자가 고친 초안 전체
 * @param planningStyleSelectedByUser 초안 화면에서 성향 토글을 직접 바꿨으면 true → 프로필에 USER_SET으로 저장
 * @param colorId                     사용자가 고른 목표 색상 칩(UserColor id). 없으면 서버가 안 쓰는 색 자동 배정
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoalConfirmRequest {

    @NotNull(message = "초안 정보가 필요해요.")
    private Long draftId;

    @NotNull(message = "계획 내용이 필요해요.")
    private GoalPlanDto plan;

    private boolean planningStyleSelectedByUser;

    private Long colorId;
}
