package com.naleul.naleul.domain.playBilling.client;

import lombok.Getter;

/**
 * Google Play Developer API 실패.
 * @param notFound 토큰이 없거나 이 앱의 구매가 아님 (404 · 410) — 다시 시도해도 같음
 */
@Getter
public class PlayBillingException extends RuntimeException {
    private final int status;

    public PlayBillingException(int status, String message) {
        super(message);
        this.status = status;
    }

    public boolean notFound() {
        return status == 404 || status == 410;
    }
}
