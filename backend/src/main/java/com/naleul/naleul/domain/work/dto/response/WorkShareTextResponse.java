package com.naleul.naleul.domain.work.dto.response;

/** 공유용 텍스트 (V52) — 앱은 이 text 를 공유 시트에 그대로 넘긴다 */
public record WorkShareTextResponse(String scope, String text) {
}
