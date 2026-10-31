package com.naleul.naleul.domain.webBilling.dto.request;

import com.naleul.naleul.domain.webBilling.enums.WebPlan;
import jakarta.validation.constraints.NotNull;

public record WebBillingPrepareRequest(@NotNull WebPlan plan) {
}
