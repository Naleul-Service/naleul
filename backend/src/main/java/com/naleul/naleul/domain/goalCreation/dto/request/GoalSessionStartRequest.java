package com.naleul.naleul.domain.goalCreation.dto.request;

import com.naleul.naleul.domain.goalCategory.kind.GoalSubType;
import com.naleul.naleul.domain.goalCategory.kind.GoalType;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/** 둘 다 선택. 테스트에서 값을 넣을 수 있도록 @Builder + @AllArgsConstructor */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoalSessionStartRequest {

    @Size(max = 500, message = "첫 메시지는 500자 이하로 입력해주세요.")
    private String initialMessage;

    /** 임시 목표 "구체화하기"로 진입한 경우 */
    private Long sourceGoalId;

    /**
     * 목표 카테고리 (명세 6.A.1.2). 예전 앱 호환을 위해 선택 — 없으면 AI 가 목표 문장으로 분류한다.
     * 예) { "goalType": "HEALTH", "goalSubType": "DIET" }
     *     { "goalType": "HEALTH", "goalSubType": "HEALTH_OTHER", "goalKindLabel": "필라테스" }
     *     { "goalType": "ETC", "goalKindLabel": "악기 연습" }
     */
    private GoalType goalType;
    private GoalSubType goalSubType;

    @Size(max = 10, message = "기타 카테고리 이름은 10자 이하로 입력해주세요.")
    private String goalKindLabel;

    /**
     * 이 목표를 위해 꼭 하고 싶은 일 (선택, V42). 예) 다이어트 → ["헬스", "식단 기록"]
     * 계획 AI 가 반드시 루틴(또는 할 일)으로 넣는다. 최대 5개 · 각 30자 (넘치면 서버가 자른다)
     */
    @Size(max = 10, message = "꼭 할 일은 5개까지 적을 수 있어요.")
    private List<String> mustDoItems;
}
