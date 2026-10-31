package com.naleul.naleul.domain.user.service;

import com.naleul.naleul.domain.autobiography.service.AutobiographyService;
import com.naleul.naleul.domain.habit.service.HabitGoalProvisioner;
import com.naleul.naleul.domain.lifePattern.service.LifePatternService;
import com.naleul.naleul.domain.notification.service.NotificationSettingsService;
import com.naleul.naleul.domain.planner.service.PlannerSettingService;
import com.naleul.naleul.domain.user.dto.LoginResponse;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.enums.CharacterType;
import com.naleul.naleul.domain.user.enums.UserRole;
import com.naleul.naleul.domain.user.repository.UserRepository;
import com.naleul.naleul.domain.userColor.service.UserColorService;
import com.naleul.naleul.global.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 없이 둘러보기.
 *
 * 모든 방문자가 같은 "둘러보기" 계정 하나를 읽기 전용으로 쓴다.
 *  - 그 계정엔 데이터가 없어서 모든 화면이 빈 상태(= "추가해 보세요" 안내)로 보인다
 *  - 토큰에 guest=true 가 있어서 GET 외의 요청(저장 · 수정 · 삭제 · AI)은 필터가 401 GUEST_LOGIN_REQUIRED 로 막는다
 *    → 방문자가 남의 데이터를 만들거나, AI 비용을 쓰거나, 계정이 무한히 늘어나는 일이 없다
 */
@Service
@RequiredArgsConstructor
public class GuestAccountService {

    static final String GUEST_EMAIL = "guest@naleul.invalid";

    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final UserColorService userColorService;
    private final AutobiographyService autobiographyService;

    private final LifePatternService lifePatternService;
    private final HabitGoalProvisioner habitGoalProvisioner;
    private final PlannerSettingService plannerSettingService;
    private final NotificationSettingsService notificationSettingsService;

    /**
     * 서버가 뜰 때 한 번: 둘러보기 계정과, 화면을 열 때 처음 만들어지는 기본 행(고정 시간 · 습관 그릇 · 설정)을 미리 만든다.
     * 여러 방문자가 동시에 처음 열어도 같은 행을 두 번 만들다 충돌(500)하지 않게.
     */
    @Transactional
    public User ensureGuest() {
        return userRepository.findFirstByUserEmailOrderByUserIdAsc(GUEST_EMAIL).orElseGet(this::create);
    }

    /** 트랜잭션 없이 각 서비스가 자기 트랜잭션으로 만든다 (계정은 ensureGuest 에서 먼저 커밋) */
    public void provisionDefaults(User guest) {
        Long id = guest.getUserId();
        lifePatternService.ensureInitialized(id);
        habitGoalProvisioner.ensure(guest);
        plannerSettingService.getOrCreate(id);
        notificationSettingsService.get(id);
    }

    @Transactional
    public LoginResponse issue() {
        User guest = userRepository.findFirstByUserEmailOrderByUserIdAsc(GUEST_EMAIL).orElseGet(this::create);
        return new LoginResponse(
                jwtProvider.generateGuestAccessToken(guest.getUserId()),
                null,
                guest.getUserId(),
                guest.getUserName(),
                null,
                guest.getUserRole(),
                true,
                guest.getPatternType()
        );
    }

    private User create() {
        User u = userRepository.save(User.builder()
                .userName("둘러보기")
                .userEmail(GUEST_EMAIL)
                .userRole(UserRole.FREE)
                .characterType(CharacterType.FEMALE)
                .build());
        u.completeOnboarding();
        userColorService.createDefaultColors(u);
        autobiographyService.createDefaultQuestions(u);
        return u;
    }
}
