package com.naleul.naleul.domain.webBilling.client;

import lombok.Getter;

/**
 * 토스 API 실패.
 * @param retryable 응답을 못 받은 경우(네트워크 · 시간 초과) — 결제가 됐는지 모르므로 같은 주문번호(멱등키)로 다시 시도해야 함
 */
@Getter
public class TossPaymentsException extends RuntimeException {
    private final String code;
    private final boolean retryable;

    public TossPaymentsException(String code, String message, boolean retryable) {
        super(message);
        this.code = code;
        this.retryable = retryable;
    }
}
