package com.naleul.naleul.domain.user.service;

import com.naleul.naleul.domain.user.entity.RefreshSession;
import com.naleul.naleul.domain.user.repository.RefreshSessionRepository;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import com.naleul.naleul.global.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;

/**
 * 로그인 유지 (refresh token 세션).
 *
 * 예전: 사용자당 refresh token 1개 → 웹 로그인이 iOS 토큰을 덮어쓰고, 동시 재발급 중 하나만 성공해서 로그아웃됐다.
 * 지금:
 *  - 로그인할 때마다 세션 한 줄 (기기별로 따로 회전)
 *  - 재발급 = 그 세션의 토큰만 새로 바꾸고 만료를 60일 뒤로 (sliding) → 두 달 안에 한 번만 열면 계속 로그인
 *  - 방금 회전된 "직전 토큰"으로 2분 안에 다시 오면(동시 요청) 같은 새 토큰을 돌려준다
 *  - 사용자당 최근 10개 세션만 남긴다
 */
@Service
@RequiredArgsConstructor
public class RefreshSessionService {

    static final int MAX_SESSIONS_PER_USER = 10;
    static final long REUSE_GRACE_SECONDS = 120;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final RefreshSessionRepository repository;
    private final JwtProvider jwtProvider;

    /** 로그인: 새 세션을 만들고 refresh token 을 돌려준다 */
    @Transactional
    public String issue(Long userId) {
        LocalDateTime now = now();
        String token = jwtProvider.generateRefreshToken(userId);
        repository.deleteExpired(userId, now);
        repository.save(RefreshSession.start(userId, token, hash(token), now, expiresFrom(now)));
        prune(userId);
        return token;
    }

    /**
     * 재발급: 받은 refresh token 을 확인하고 회전한다.
     * @return 사용자 ID 와 새 refresh token
     */
    @Transactional
    public Rotated rotate(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank() || !jwtProvider.isValid(refreshToken)) {
            throw new CustomException(ErrorCode.INVALID_TOKEN); // 서명이 틀리거나 JWT 자체가 만료
        }
        LocalDateTime now = now();
        String h = hash(refreshToken);

        RefreshSession current = repository.findByTokenHashForUpdate(h).orElse(null);
        if (current != null) {
            if (current.getExpiresAt().isBefore(now)) {
                repository.delete(current);
                throw new CustomException(ErrorCode.TOKEN_EXPIRED);
            }
            String next = jwtProvider.generateRefreshToken(current.getUserId());
            current.rotate(next, hash(next), now, expiresFrom(now));
            return new Rotated(current.getUserId(), next);
        }

        // 방금 다른 요청이 같은 토큰으로 회전시켰다 → 그 결과를 같이 쓴다
        RefreshSession rotated = repository.findByPreviousTokenHash(h).orElse(null);
        if (rotated != null && rotated.getRotatedAt() != null
                && rotated.getRotatedAt().plusSeconds(REUSE_GRACE_SECONDS).isAfter(now)) {
            rotated.touch(now);
            return new Rotated(rotated.getUserId(), rotated.getToken());
        }
        throw new CustomException(ErrorCode.INVALID_TOKEN);
    }

    /** 로그아웃: 이 기기의 세션만 지운다 (없으면 조용히 넘어감) */
    @Transactional
    public void revoke(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) return;
        repository.findByTokenHash(hash(refreshToken)).ifPresent(repository::delete);
    }

    /** 탈퇴: 모든 기기 로그아웃 */
    @Transactional
    public void revokeAll(Long userId) {
        repository.deleteAllByUserId(userId);
    }

    private void prune(Long userId) {
        List<RefreshSession> all = repository.findAllByUserIdRecentFirst(userId);
        if (all.size() > MAX_SESSIONS_PER_USER) {
            repository.deleteAll(all.subList(MAX_SESSIONS_PER_USER, all.size()));
        }
    }

    private static LocalDateTime expiresFrom(LocalDateTime now) {
        return now.plusDays(JwtProvider.REFRESH_TOKEN_DAYS);
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(KST);
    }

    static String hash(String token) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record Rotated(Long userId, String refreshToken) {}
}
