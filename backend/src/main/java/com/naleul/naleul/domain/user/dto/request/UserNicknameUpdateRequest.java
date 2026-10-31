// domain/user/dto/request/UserNicknameUpdateRequest.java
package com.naleul.naleul.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UserNicknameUpdateRequest(

        @NotBlank(message = "닉네임을 입력해주세요.")
        @Pattern(
                regexp = "^[가-힣a-zA-Z0-9]{2,10}$",
                message = "특수문자 제외 2~10자 이내로 입력해주세요."
        )
        String nickname
) {
}