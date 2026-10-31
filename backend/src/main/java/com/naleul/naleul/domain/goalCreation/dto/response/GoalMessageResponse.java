package com.naleul.naleul.domain.goalCreation.dto.response;

import com.naleul.naleul.domain.goalCreation.dto.slot.SlotKey;
import com.naleul.naleul.domain.goalCreation.entity.GoalCreationMessage;
import com.naleul.naleul.domain.goalCreation.enums.MessageRole;

import java.util.List;

/**
 * 대화 한 줄.
 * skippable: ASSISTANT 질문이 건너뛸 수 있는 슬롯인지 (iOS "건너뛰기" 버튼 노출 여부)
 * skip: USER 메시지가 건너뛰기였는지 (이어하기 화면에서 "건너뛰었어요" 표시용)
 */
public record GoalMessageResponse(
        Long messageId,
        MessageRole role,
        String content,
        String targetSlot,
        List<String> quickReplies,
        boolean skippable,
        boolean skip
) {
    public static GoalMessageResponse from(GoalCreationMessage m, List<String> quickReplies) {
        SlotKey key = SlotKey.fromKey(m.getTargetSlot());
        // 마지막 "더 알려줄 정보"(extraNote) 질문도 건너뛸 수 있어요 (2026-10)
        boolean skippable = m.getRole() == MessageRole.ASSISTANT
                && ((key != null && key.isSkippable()) || "extraNote".equals(m.getTargetSlot()));
        return new GoalMessageResponse(m.getGoalCreationMessageId(), m.getRole(), m.getContent(),
                m.getTargetSlot(), quickReplies, skippable, m.isSkip());
    }
}
