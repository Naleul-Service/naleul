package com.naleul.naleul.domain.user.service;

import com.naleul.naleul.domain.user.dto.request.TermsAgreementRequest;
import com.naleul.naleul.domain.user.dto.response.TermsAgreementResponse;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.repository.UserRepository;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserTermsService {

    private final UserRepository userRepository;
    private final UserFinder userFinder;

    @Transactional
    public TermsAgreementResponse agreeToTerms(Long userId, TermsAgreementRequest request) {
        User user = userFinder.findUser(userId);
        // 필수 약관 미동의 시 진행 불가
        if (!request.isServiceTermsAgreed() || !request.isPrivacyAgreed()) {
            throw new CustomException(ErrorCode.REQUIRED_TERMS_NOT_AGREED);
        }

        user.agreeToTerms(
                request.isServiceTermsAgreed(),
                request.isPrivacyAgreed(),
                request.isMarketingAgreed(),
                LocalDateTime.now()
        );

        return TermsAgreementResponse.builder()
                .userId(user.getUserId())
                .serviceTermsAgreed(user.isServiceTermsAgreed())
                .privacyAgreed(user.isPrivacyAgreed())
                .marketingAgreed(user.isMarketingAgreed())
                .onboardingCompleted(user.isOnboardingCompleted())
                .build();
    }

    @Transactional
    public void completeOnboarding(Long userId) {
        User user = userFinder.findUser(userId);
        user.completeOnboarding();
    }
}