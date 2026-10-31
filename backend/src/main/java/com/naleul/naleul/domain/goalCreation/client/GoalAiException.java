package com.naleul.naleul.domain.goalCreation.client;

import lombok.Getter;

/**
 * AI 호출 실패. 서비스 계층에서 잡아서
 *  - 인터뷰(동기): 1회 재시도 후 ErrorCode.AI_UNAVAILABLE (502)
 *  - 초안 생성(비동기): GoalDraft.markFailed(reason.name())
 * 으로 바꾼다.
 */
@Getter
public class GoalAiException extends RuntimeException {

    public enum Reason {
        AI_TIMEOUT,        // 응답 시간 초과
        AI_UNAVAILABLE,    // 4xx/5xx, 네트워크 오류
        AI_INVALID_OUTPUT  // 도구 호출 없음, JSON 구조 불일치
    }

    private final Reason reason;

    public GoalAiException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public GoalAiException(Reason reason, String message) {
        this(reason, message, null);
    }

    /** 재시도해볼 만한 실패인지 (출력 형식 오류는 같은 입력으로 다시 해도 비슷할 가능성이 높지만 1회는 허용) */
    public boolean isRetryable() {
        return reason == Reason.AI_TIMEOUT || reason == Reason.AI_UNAVAILABLE;
    }
}
