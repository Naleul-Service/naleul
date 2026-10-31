package com.naleul.naleul.domain.user.service;

import com.naleul.naleul.domain.user.dto.LoginResponse;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.global.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * /api/v1/auth/token-reissue — refresh token 으로 access + 새 refresh token 발급.
 * 실제 확인·회전은 RefreshSessionService (기기별 세션).
 * 실패하면 500 이 아니라 401 (클라이언트가 "다시 로그인"으로 처리할 수 있게).
 */
@Service
@RequiredArgsConstructor
public class TokenReissueService {

    private final RefreshSessionService refreshSessionService;
    private final UserFinder userFinder;
    private final JwtProvider jwtProvider;

    @Transactional
    public LoginResponse reissue(String refreshToken) {
        RefreshSessionService.Rotated r = refreshSessionService.rotate(refreshToken);
        User user = userFinder.findUser(r.userId());
        String newAccessToken = jwtProvider.generateAccessToken(user.getUserId(), user.getUserRole());
        return new LoginResponse(
                newAccessToken,
                r.refreshToken(),
                user.getUserId(),
                user.getUserName(),
                user.getUserEmail(),
                user.getUserRole(),
                user.isOnboardingCompleted(),
                user.getPatternType(),
                user.adminViewPlanOrNull()
        );
    }
}
