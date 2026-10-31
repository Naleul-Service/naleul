package com.naleul.naleul.domain.user.dto.response;

import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.enums.UserPatternType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserResponse {
    private Long id;
    private String email;
    private String nickname;
    private String userRole;       // ✅ 추가 ("ADMIN" | "FREE" | "PRO")
    private boolean isPremium;
    private UserPatternType patternType;
    /** 운영진인지 — 운영 대시보드 · 플랜 바꿔 보기 메뉴를 보여줄지 */
    private boolean admin;
    /** 운영진이 보고 있는 플랜 "FREE" | "PRO" (운영진이 아니면 null) */
    private String adminViewPlan;

    public static UserResponse from(User user) {
        return UserResponse.builder()
                .id(user.getUserId())
                .email(user.getUserEmail())
                .nickname(user.getUserName())
                .userRole(user.getUserRole().name())   // Enum이라면 .name()
                .isPremium(user.isPremium())
                .patternType(user.getPatternType())
                .admin(user.isAdmin())
                .adminViewPlan(user.adminViewPlanOrNull() != null ? user.adminViewPlanOrNull().name() : null)
                .build();
    }

}
