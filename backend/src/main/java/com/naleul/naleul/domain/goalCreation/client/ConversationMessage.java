package com.naleul.naleul.domain.goalCreation.client;

import com.naleul.naleul.domain.goalCreation.enums.MessageRole;

/** AI에게 넘기는 대화 기록 한 줄. skip=true면 사용자가 건너뛴 것 */
public record ConversationMessage(MessageRole role, String content, String targetSlot, boolean skip) {
}
