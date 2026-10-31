package com.naleul.naleul.domain.webBilling.dto.response;

/** 토스페이 인증 주소 — 웹은 checkoutUri 로 이동해요 (PC 는 QR, 모바일은 토스 앱) */
public record TossPayPrepareResponse(String checkoutUri, String orderName, long amount, boolean testMode) {
}
