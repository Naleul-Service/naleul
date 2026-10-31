package com.naleul.naleul.domain.webBilling.dto.request;

import com.naleul.naleul.domain.webBilling.enums.WebPlan;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 토스페이 정기구독 시작
 * @param returnOrigin 인증 후 돌아올 웹 origin (예: http://localhost:3000). 서버 허용 목록에 있을 때만 써요
 */
public record TossPayPrepareRequest(@NotNull WebPlan plan, @NotBlank @Size(max = 200) String returnOrigin) {
}
