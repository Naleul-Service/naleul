package com.naleul.naleul.domain.user.dto.request;

import com.naleul.naleul.domain.user.enums.UserWithdrawalReason;
import jakarta.validation.constraints.NotNull;

public record UserWithdrawalRequest(
        @NotNull(message = "탈퇴 사유를 선택해주세요.")
        UserWithdrawalReason reason,

        String detail   // reason == OTHER 일 때만 필수, 서비스 단에서 검증
) {}