package com.naleul.naleul.domain.user.dto;

import com.naleul.naleul.domain.user.enums.AdminViewPlan;
import com.naleul.naleul.domain.user.enums.UserPatternType;
import com.naleul.naleul.domain.user.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LoginResponse {

    private String accessToken;
    private String refreshToken;
    private Long userId;
    private String userName;
    private String userEmail;
    private UserRole userRole;
    private boolean onboardingCompleted;
    private UserPatternType patternType;
    /** 운영진이 보고 있는 플랜 FREE | PRO (운영진이 아니면 null) — 웹은 이걸로 광고 · 배지를 맞춘다 */
    private AdminViewPlan adminViewPlan;

    /** 예전 호출부 호환 (운영진 플랜 없이) */
    public LoginResponse(String accessToken, String refreshToken, Long userId, String userName, String userEmail,
                         UserRole userRole, boolean onboardingCompleted, UserPatternType patternType) {
        this(accessToken, refreshToken, userId, userName, userEmail, userRole, onboardingCompleted, patternType, null);
    }
}