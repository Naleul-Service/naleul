package com.naleul.naleul.domain.user.dto.request;

import lombok.Getter;

@Getter
public class TermsAgreementRequest {
    private boolean serviceTermsAgreed;
    private boolean privacyAgreed;
    private boolean marketingAgreed;
}