package com.naleul.naleul.domain.webBilling.dto.response;

/** 토스 카드 등록 창(requestBillingAuth)을 여는 데 필요한 값 */
public record WebBillingPrepareResponse(String customerKey, String orderName, long amount, String customerName,
                                        boolean testMode) {
}
