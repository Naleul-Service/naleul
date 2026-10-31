package com.naleul.naleul.domain.webBilling.exception;

import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;

/** 카드 등록 · 결제 실패 — 토스가 준 이유(예: "잔액이 부족합니다")를 그대로 사용자에게 보여준다 */
public class WebBillingPaymentException extends CustomException {
    private final String userMessage;

    public WebBillingPaymentException(String userMessage) {
        super(ErrorCode.WEB_BILLING_PAYMENT_FAILED);
        this.userMessage = userMessage;
    }

    public String getUserMessage() {
        return userMessage;
    }
}
