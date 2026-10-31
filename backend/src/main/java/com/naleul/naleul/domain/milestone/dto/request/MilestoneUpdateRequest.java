package com.naleul.naleul.domain.milestone.dto.request;

import com.naleul.naleul.domain.milestone.enums.MilestoneStatus;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 마일스톤 수정 — null 인 필드는 그대로 둔다.
 * description: 빈 문자열이면 지움 / clearTargetValue: true 면 목표 수치를 지움
 * status: ACHIEVED(달성) · PENDING(달성 취소). MISSED 는 서버가 기한이 지나면 정한다.
 * targetValue: 보내면 "직접 정한 값"이 되어 자동 계산에서 빠진다 (V40)
 * autoTargetValue: true 면 직접 정한 값을 버리고 자동 계산으로 되돌린다
 */
public record MilestoneUpdateRequest(
        @Size(min = 1, max = 40, message = "마일스톤 이름은 1~40자로 입력해주세요.")
        String title,

        @Size(max = 300, message = "설명은 300자 이하로 입력해주세요.")
        String description,

        LocalDate dueDate,
        BigDecimal targetValue,
        Boolean clearTargetValue,
        MilestoneStatus status,
        Boolean autoTargetValue
) {
}
