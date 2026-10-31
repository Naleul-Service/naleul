package com.naleul.naleul.domain.goalCategory.dto.request;

import com.naleul.naleul.domain.goalCategory.kind.GoalSubType;
import com.naleul.naleul.domain.goalCategory.kind.GoalType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 업무형(기록형) 목표 만들기 (V39). 이름만 있으면 된다.
 * 예) { "name": "나를 업무", "emoji": "💼", "goalType": "WORK", "goalSubType": "COMPANY_WORK", "description": "백엔드 담당" }
 * - colorId 를 비우면 아직 안 쓰는 색을 자동으로 고른다.
 * - goalType·goalSubType·goalKindLabel 은 선택 (업무형은 WORK · COMPANY_WORK / SIDE_PROJECT)
 * - description 은 선택 — "내가 맡은 일" / "무엇을 만드는지" 한 줄. motive 칸에 저장한다
 * - V53: 업무형은 목표 전체 마감이 없다 (마감은 주제마다 — work_topic.due_date)
 */
public record RecordGoalCreateRequest(
        @NotBlank(message = "목표 이름을 입력해 주세요.")
        @Size(max = 40, message = "목표 이름은 40자 이하로 입력해 주세요.")
        String name,

        @Size(max = 16)
        String emoji,

        Long colorId,

        GoalType goalType,
        GoalSubType goalSubType,
        String goalKindLabel,

        @Size(max = 100, message = "한 줄 설명은 100자 이하로 입력해 주세요.")
        String description
) {
}
