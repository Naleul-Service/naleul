package com.naleul.naleul.domain.goalCreation.enums;

/**
 * 인터뷰 답을 어떻게 했는지 (V67, 운영 대시보드 "AI 채팅 정리 정확도").
 * CHIP: AI 가 띄운 답변 칩을 눌렀다 / TYPED: 직접 썼다 / SKIP: 건너뛰었다.
 * 앱이 보내지 않으면(예전 버전) 서버가 "직전 질문의 칩과 글자가 똑같은지"로 추정한다.
 */
public enum AnswerSource {
    CHIP, TYPED, SKIP
}
