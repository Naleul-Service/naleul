package com.naleul.naleul.domain.admin.support;

import com.naleul.naleul.domain.admin.config.AdminDashboardProperties;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.enums.UserRole;
import com.naleul.naleul.domain.user.service.UserFinder;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 운영 대시보드는 전체 사용자의 매출 · 비용을 보여주므로 운영진만 볼 수 있어야 한다.
 *
 * 왜 화면에서 버튼을 숨기는 것만으로는 안 되나:
 *   API 주소(/api/v1/admin/dashboard)만 알면 로그인한 누구나 직접 호출할 수 있기 때문.
 *   그래서 서버에서 "이 사람이 운영진인가"를 반드시 한 번 더 확인한다.
 *
 * 운영진 = admin.dashboard.admin-user-ids 에 있는 id 이거나 user_role = ADMIN.
 * (Pro 결제를 하면 user_role 이 PRO 로 바뀌므로, id 목록이 더 확실하다)
 */
@Component
@RequiredArgsConstructor
public class AdminGuard {

    private final AdminDashboardProperties properties;
    private final UserFinder userFinder;

    public void check(Long userId) {
        if (userId == null) throw new CustomException(ErrorCode.ADMIN_ONLY);
        if (properties.adminUserIds() != null && properties.adminUserIds().contains(userId)) return;
        User user = userFinder.findUser(userId);
        if (user.getUserRole() != UserRole.ADMIN) throw new CustomException(ErrorCode.ADMIN_ONLY);
    }
}
