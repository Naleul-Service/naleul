package com.naleul.naleul.domain.user.entity;

import com.naleul.naleul.domain.user.enums.*;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor
public class User {

    private static final int EXP_PER_LEVEL = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    /** V69: 가입 시각 (Aha-moment · 운영 대시보드). 그 전 가입자는 null */
    @org.hibernate.annotations.CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private java.time.LocalDateTime createdAt;

    @Column(unique = true)
    private String appleId;

    @Column(nullable = false)
    private String userName;

    @Column(nullable = false, unique = true)
    private String userEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole userRole;

    @Column(nullable = false)
    private int level = 1;

    @Column(nullable = false)
    private int experience = 0;

    @Column(nullable = false)
    private int totalExperience = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(10) DEFAULT 'FEMALE'")
    private CharacterType characterType = CharacterType.FEMALE;

    @Column(name = "is_premium", nullable = false)
    private boolean isPremium = false;

    @Column(name = "premium_expires_at")
    private LocalDateTime premiumExpiresAt;

    /** 운영진이 지금 보고 있는 플랜 (V66). null = PRO. 운영진이 아니면 쓰지 않는다 */
    @Enumerated(EnumType.STRING)
    @Column(name = "admin_view_plan", length = 10)
    private AdminViewPlan adminViewPlan;

    // ===== ✅ 신규 추가: 약관 동의 / 온보딩 =====
    @Column(name = "service_terms_agreed", nullable = false)
    private boolean serviceTermsAgreed = false;

    @Column(name = "privacy_agreed", nullable = false)
    private boolean privacyAgreed = false;

    @Column(name = "marketing_agreed", nullable = false)
    private boolean marketingAgreed = false;

    @Column(name = "terms_agreed_at")
    private LocalDateTime termsAgreedAt;

    @Column(name = "onboarding_completed", nullable = false)
    private boolean onboardingCompleted = false;

    // ===== ✅ 신규 추가: AI 패턴 분석용 유형 =====
    @Enumerated(EnumType.STRING)
    @Column(name = "pattern_type", nullable = true, columnDefinition = "VARCHAR(30)")
    private UserPatternType patternType;

    // ===== ✅ 신규 추가: AI 목표 생성 — 실행 성향 =====
    @Enumerated(EnumType.STRING)
    @Column(name = "planning_style", length = 20)
    private PlanningStyle planningStyle;

    @Enumerated(EnumType.STRING)
    @Column(name = "planning_style_source", length = 20)
    private PlanningStyleSource planningStyleSource;

    // User.java에 추가
    public void agreeToTerms(boolean service, boolean privacy, boolean marketing, LocalDateTime agreedAt) {
        this.serviceTermsAgreed = service;
        this.privacyAgreed = privacy;
        this.marketingAgreed = marketing;
        this.termsAgreedAt = agreedAt;
    }

    public void completeOnboarding() {
        this.onboardingCompleted = true;
    }

    @Builder
    public User(Long userId, String appleId, String userName, String userEmail,
                UserRole userRole, CharacterType characterType) {
        this.userId = userId;
        this.appleId = appleId;
        this.userName = userName;
        this.userEmail = userEmail;
        this.userRole = userRole;
        this.characterType = characterType;
        this.level = 1;
        this.experience = 0;
        this.totalExperience = 0;
        this.isPremium = false;
    }

    public boolean gainExperience(int exp) {
        this.experience += exp;
        this.totalExperience += exp;

        if (this.experience >= EXP_PER_LEVEL) {
            this.experience -= EXP_PER_LEVEL;
            this.level++;
            return true;
        }
        return false;
    }

    /** 운영진 — 운영 대시보드를 보고, Free · Pro 화면을 바꿔 가며 확인할 수 있다 */
    public boolean isAdmin() {
        return this.userRole == UserRole.ADMIN;
    }

    /** 운영진이 보고 있는 플랜 (기본 PRO). 운영진이 아니면 null */
    public AdminViewPlan adminViewPlanOrNull() {
        if (!isAdmin()) return null;
        return this.adminViewPlan == null ? AdminViewPlan.PRO : this.adminViewPlan;
    }

    public void changeAdminViewPlan(AdminViewPlan plan) {
        if (!isAdmin() || plan == null) return;
        this.adminViewPlan = plan;
    }

    /** admin.dashboard.admin-user-ids 에 있는 사용자를 운영진으로 (AdminRoleSync) */
    public void promoteToAdmin() {
        this.userRole = UserRole.ADMIN;
    }

    /**
     * 지금 Pro 혜택을 받는지. 운영진은 결제와 상관없이 고른 플랜(기본 Pro)을 따른다 —
     * Free 로 바꾸면 하루 횟수 제한 · Pro 전용 기능 잠금이 일반 Free 사용자와 똑같이 걸린다.
     */
    public boolean isCurrentlyPremium() {
        if (isAdmin()) {
            return adminViewPlanOrNull() == AdminViewPlan.PRO;
        }
        return hasPaidPremium();
    }

    /**
     * 실제로 결제한 Pro 기간 안인지 (운영진의 플랜 바꿔 보기와 상관없이).
     * 결제 · 연장 · 환불처럼 돈과 관련된 판단은 반드시 이걸 쓴다 — isCurrentlyPremium 은 기능 잠금 · 횟수 제한용.
     */
    public boolean hasPaidPremium() {
        if (!this.isPremium) {
            return false;
        }
        if (this.premiumExpiresAt == null) {
            return true;
        }
        return this.premiumExpiresAt.isAfter(java.time.LocalDateTime.now());
    }

    public void changeCharacter(CharacterType characterType) {
        this.characterType = characterType;
    }

    public void grantPremium(LocalDateTime expiresAt) {
        this.isPremium = true;
        this.premiumExpiresAt = expiresAt;
        if (!isAdmin()) this.userRole = UserRole.PRO;   // 운영진이 결제해도 운영진 권한은 그대로
    }

    /**
     * Pro 기간 연장 — 이미 더 늦게까지(또는 기한 없이) Pro 면 줄이지 않는다.
     * 웹 구독(토스)과 iOS 구독(Apple)이 같은 premiumExpiresAt 을 쓰므로, 한쪽 결제가 다른 쪽 기간을 덮어써서
     * 이미 돈을 낸 기간이 사라지지 않게 grantPremium 대신 이걸 쓴다.
     * @param until null 이면 기한 없음
     */
    public void extendPremium(LocalDateTime until) {
        boolean active = hasPaidPremium();   // 운영진이 Pro 로 보고 있어도 실제 결제 기간으로 판단
        if (active && this.premiumExpiresAt == null) {
            grantPremium(null);   // 이미 기한 없음 → 그대로
            return;
        }
        if (until == null) {
            grantPremium(null);
            return;
        }
        grantPremium(active && this.premiumExpiresAt.isAfter(until) ? this.premiumExpiresAt : until);
    }

    public void revokePremium() {
        this.isPremium = false;
        this.premiumExpiresAt = null;
        if (!isAdmin()) this.userRole = UserRole.FREE;
    }

    // ===== ✅ 신규 추가 =====

    /**
     * 유저가 AI 패턴 분석 유형을 선택/변경할 때 호출.
     */
    public void updatePatternType(UserPatternType patternType) {
        this.patternType = patternType;
    }

    /**
     * 실행 성향 저장 (명세 3-3).
     * 사용자가 직접 고른 값(USER_SET)은 AI 추정값(AI_INFERRED)으로 덮어쓰지 않는다.
     */
    public void updatePlanningStyle(PlanningStyle style, PlanningStyleSource source) {
        if (style == null || source == null) {
            return;
        }
        if (this.planningStyleSource == PlanningStyleSource.USER_SET
                && source == PlanningStyleSource.AI_INFERRED) {
            return;
        }
        this.planningStyle = style;
        this.planningStyleSource = source;
    }

    public void updateUserName(String userName) {
        this.userName = userName;
    }

    public void downgradeToFree() {
        if (this.userRole == UserRole.PRO) {
            this.userRole = UserRole.FREE;
        }
    }
}