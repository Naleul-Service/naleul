package com.naleul.naleul.global.apple;

// global/apple/AppleTokenVerifier.java

import com.fasterxml.jackson.databind.ObjectMapper;
import com.naleul.naleul.global.apple.dto.response.ApplePublicKeyResponse;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

// global/apple/AppleTokenVerifier.java
@Slf4j
@Component
@RequiredArgsConstructor
public class AppleTokenVerifier {

    private static final String APPLE_PUBLIC_KEY_URL = "https://appleid.apple.com/auth/keys";

    private static final String APPLE_ISSUER = "https://appleid.apple.com";

    /**
     * 우리 서비스용으로 발급된 토큰인지 확인할 aud(audience) 목록.
     *  - iOS 앱 로그인: 앱 Bundle ID (naleul.naleul)
     *  - 웹 로그인: Apple Developer 에서 만든 Services ID (예: naleul.naleul.web)
     * 같은 팀 · 같은 Primary App ID 로 묶여 있으면 두 경로의 sub(사용자 ID)가 같아서 같은 계정으로 로그인된다.
     */
    @Value("${apple.login.audiences:naleul.naleul}")
    private String[] allowedAudiences;

    private final WebClient.Builder webClientBuilder;  // RestTemplate → WebClient
    private final ObjectMapper objectMapper;

    public Claims verify(String identityToken) {
        try {
            String kid = extractKidFromHeader(identityToken);
            ApplePublicKeyResponse publicKeys = fetchApplePublicKeys();  // WebClient로 호출

            ApplePublicKeyResponse.ApplePublicKey matchingKey = publicKeys.getKeys()
                    .stream()
                    .filter(key -> key.getKid().equals(kid))
                    .findFirst()
                    .orElseThrow(() -> new CustomException(ErrorCode.APPLE_TOKEN_INVALID));

            PublicKey publicKey = buildPublicKey(matchingKey);

            // 서명 · 만료(exp) · 발급자(iss) 검증
            Claims claims = Jwts.parser()
                    .verifyWith((RSAPublicKey) publicKey)
                    .requireIssuer(APPLE_ISSUER)
                    .build()
                    .parseSignedClaims(identityToken)
                    .getPayload();

            // 다른 앱 · 다른 서비스용으로 발급된 Apple 토큰은 거절
            Set<String> audience = claims.getAudience();
            boolean ours = audience != null && Arrays.stream(allowedAudiences)
                    .map(String::trim)
                    .anyMatch(audience::contains);
            if (!ours) {
                log.warn("Apple token audience 불일치: aud={}, allowed={}", audience, Arrays.toString(allowedAudiences));
                throw new CustomException(ErrorCode.APPLE_TOKEN_INVALID);
            }
            return claims;

        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Apple token 검증 실패: {}", e.getMessage());
            throw new CustomException(ErrorCode.APPLE_TOKEN_INVALID);
        }
    }

    private ApplePublicKeyResponse fetchApplePublicKeys() {
        return webClientBuilder.build()
                .get()
                .uri(APPLE_PUBLIC_KEY_URL)
                .retrieve()
                .bodyToMono(ApplePublicKeyResponse.class)
                .block();  // 동기 처리
    }

    // extractKidFromHeader, buildPublicKey는 동일
    private String extractKidFromHeader(String identityToken) throws Exception {
        String headerPart = identityToken.split("\\.")[0];
        byte[] headerBytes = Base64.getUrlDecoder().decode(headerPart);
        Map<String, String> header = objectMapper.readValue(headerBytes, Map.class);
        return header.get("kid");
    }

    private PublicKey buildPublicKey(ApplePublicKeyResponse.ApplePublicKey key) throws Exception {
        byte[] nBytes = Base64.getUrlDecoder().decode(key.getN());
        byte[] eBytes = Base64.getUrlDecoder().decode(key.getE());
        RSAPublicKeySpec spec = new RSAPublicKeySpec(
                new BigInteger(1, nBytes),
                new BigInteger(1, eBytes)
        );
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }
}