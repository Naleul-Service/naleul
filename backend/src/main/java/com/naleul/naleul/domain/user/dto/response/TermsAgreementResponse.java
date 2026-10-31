package com.naleul.naleul.domain.user.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TermsAgreementResponse {
    private Long userId;
    private boolean serviceTermsAgreed;
    private boolean privacyAgreed;
    private boolean marketingAgreed;
    private boolean onboardingCompleted;
}