package com.naleul.naleul.global.jwt;

import com.naleul.naleul.domain.user.enums.UserRole;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtProvider {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration}")
    private long expiration;

    private static final long ACCESS_TOKEN_EXPIRE = 1000L * 60 * 60;        // 1시간
    /** 쓸 때마다 다시 60일 (RefreshSessionService 가 회전하면서 연장) → 두 달 안에 한 번만 열면 계속 로그인 */
    public static final int REFRESH_TOKEN_DAYS = 60;
    private static final long REFRESH_TOKEN_EXPIRE = 1000L * 60 * 60 * 24 * REFRESH_TOKEN_DAYS;

    public String generateAccessToken(Long userId, UserRole role) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role.name())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRE))
                .signWith(getSigningKey())
                .compact();
    }

    /** 둘러보기(로그인 전) 토큰: 30일, 읽기만 가능 (JwtAuthenticationFilter 가 GET 외에는 막는다) */
    private static final long GUEST_TOKEN_EXPIRE = 1000L * 60 * 60 * 24 * 30;
    public static final String GUEST_CLAIM = "guest";

    public String generateGuestAccessToken(Long guestUserId) {
        return Jwts.builder()
                .subject(String.valueOf(guestUserId))
                .claim("role", UserRole.FREE.name())
                .claim(GUEST_CLAIM, true)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + GUEST_TOKEN_EXPIRE))
                .signWith(getSigningKey())
                .compact();
    }

    public boolean isGuest(String token) {
        Boolean g = getClaims(token).get(GUEST_CLAIM, Boolean.class);
        return Boolean.TRUE.equals(g);
    }

    public String generateRefreshToken(Long userId) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                // 같은 초에 두 번 발급해도 토큰이 겹치지 않게 (iat 는 초 단위라 웹·앱 동시 로그인 시 같은 값이 나왔음)
                .id(java.util.UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + REFRESH_TOKEN_EXPIRE))
                .signWith(getSigningKey())
                .compact();
    }

    public Long getUserId(String token) {
        return Long.parseLong(getClaims(token).getSubject());
    }

    public String getRole(String token) {
        return getClaims(token).get("role", String.class);
    }

    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            throw new CustomException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException e) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    // JwtProvider.java에 추가

    // 토큰이 정상인지 (유효 + 만료 안됨)
    public boolean isValid(String token) {
        try {
            getClaims(token); // 내부적으로 파싱
            return true;
        } catch (ExpiredJwtException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    // 토큰이 만료됐는지
    public boolean isExpired(String token) {
        try {
            getClaims(token);
            return false;
        } catch (ExpiredJwtException e) {
            return true; // 만료됨
        } catch (Exception e) {
            return false; // 만료가 아닌 다른 문제
        }
    }

    // 만료된 토큰에서도 userId 추출 (갱신할 때 누구 토큰인지 알아야 함)
    public Long extractUserIdIgnoreExpiration(String token) {
        try {
            return getUserId(token);
        } catch (ExpiredJwtException e) {
            // 만료 예외에서도 Claims는 꺼낼 수 있음
            return Long.parseLong(e.getClaims().getSubject());
        }
    }
}