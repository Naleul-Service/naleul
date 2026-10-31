package com.naleul.naleul.domain.user.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AppleLoginRequest {
    private String identityToken;  // iOS에서 Apple SDK가 발급한 JWT
    private String fullName;       // 최초 로그인 시에만 옴, 이후 null
}