package com.naleul.naleul.domain.user.service;

import com.naleul.naleul.domain.autobiography.service.AutobiographyService;
import com.naleul.naleul.domain.generalCategory.service.GeneralCategoryService;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.goalCategory.service.GoalCategoryService;
import com.naleul.naleul.domain.user.dto.request.AppleLoginRequest;
import com.naleul.naleul.domain.user.dto.LoginResponse;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.enums.CharacterType;
import com.naleul.naleul.domain.user.enums.UserRole;
import com.naleul.naleul.domain.user.repository.UserRepository;
import com.naleul.naleul.domain.userColor.service.UserColorService;
import com.naleul.naleul.global.apple.AppleTokenVerifier;
import com.naleul.naleul.global.jwt.JwtProvider;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppleAuthService {

    private final AppleTokenVerifier appleTokenVerifier;
    private final UserRepository userRepository;
    private final RefreshSessionService refreshSessionService;
    private final JwtProvider jwtProvider;
    private final UserColorService userColorService;
    private final AutobiographyService autobiographyService;

    @Transactional
    public LoginResponse appleLogin(AppleLoginRequest request) {
        // 1. Apple identityToken 검증
        Claims claims = appleTokenVerifier.verify(request.getIdentityToken());

        // 2. Apple 고유 사용자 ID (sub) — 앱마다 유저마다 고유
        String appleId = claims.getSubject();

        // 3. 이메일 (최초 로그인 시에만 옴, 이후 null 가능)
        String email = claims.get("email", String.class);

        // 4. 기존 회원 조회 or 신규 가입
        User user = userRepository.findByAppleId(appleId)
                .orElseGet(() -> registerNewAppleUser(appleId, email, request.getFullName()));

        // 5. JWT 발급 (카카오와 동일 패턴)
        String accessToken = jwtProvider.generateAccessToken(user.getUserId(), user.getUserRole());
        String refreshToken = refreshSessionService.issue(user.getUserId()); // 기기(로그인)마다 따로

        return new LoginResponse(
                accessToken,
                refreshToken,
                user.getUserId(),
                user.getUserName(),          // 빠졌던 부분 추가
                user.getUserEmail(),
                user.getUserRole(),
                user.isOnboardingCompleted(),
                user.getPatternType(),
                user.adminViewPlanOrNull()
        );
    }

    private User registerNewAppleUser(String appleId, String email, String fullName) {
        String name = (fullName != null && !fullName.isBlank()) ? fullName : "Apple User";

        User newUser = userRepository.save(
                User.builder()
                        .appleId(appleId)
                        .userName(name)
                        .userEmail(email)           // 이메일 숨기기 선택 시 null 가능
                        .userRole(UserRole.FREE)    // 카카오와 동일하게 FREE
                        .characterType(CharacterType.FEMALE)
                        .build()
        );

        // 카카오 신규 가입과 동일하게 기본 데이터 생성
        userColorService.createDefaultColors(newUser);
        autobiographyService.createDefaultQuestions(newUser);

        return newUser;
    }

}