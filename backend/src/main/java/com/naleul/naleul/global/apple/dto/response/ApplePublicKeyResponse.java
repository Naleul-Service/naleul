package com.naleul.naleul.global.apple.dto.response;

// global/apple/dto/ApplePublicKeyResponse.java

import lombok.Getter;
import java.util.List;

// Apple의 https://appleid.apple.com/auth/keys 응답 구조
@Getter
public class ApplePublicKeyResponse {
    private List<ApplePublicKey> keys;

    @Getter
    public static class ApplePublicKey {
        private String kty;  // 키 타입 (RSA)
        private String kid;  // 키 ID
        private String use;  // 용도 (sig = 서명)
        private String alg;  // 알고리즘 (RS256)
        private String n;    // RSA modulus (Base64)
        private String e;    // RSA exponent (Base64)
    }
}