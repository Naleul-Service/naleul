package com.naleul.naleul.domain.goalCreation.dto.request;

import com.naleul.naleul.domain.goalCreation.personal.PlanIntensity;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto;
import com.naleul.naleul.domain.user.enums.PlanningStyle;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 초안 생성 / 다시 생성 요청 (모두 선택).
 * planningStyle: 사용자가 초안 화면에서 성향 토글을 직접 바꾼 경우에만 보냄
 * basePlan: "말로 고치기" — 사용자가 화면에서 직접 고친 지금 초안. 보내면 서버에 저장된 직전 초안 대신
 *           이 계획을 [이전 초안]으로 AI 에 넘겨, feedback 에 해당하는 부분만 바꾸고 나머지(사용자 수정 포함)는 유지한다.
 * intensity: 계획 강도 STEADY(안정형) / CHALLENGE(도전형) (V47). 안 보내면 직전 초안의 강도, 첫 초안이면 안정형
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoalDraftRequest {

    @Size(max = 200, message = "요청사항은 200자 이하로 입력해주세요.")
    private String feedback;

    private PlanningStyle planningStyle;

    private GoalPlanDto basePlan;

    private PlanIntensity intensity;
}
