-- ============================================================
-- V54: 로그인 유지 — 기기(로그인)마다 refresh token 한 줄
--
-- 문제: `refresh-token` 테이블은 사용자당 1줄(user_id UNIQUE)이라
--   - 웹에서 로그인하면 iOS 의 refresh token 이 덮여서 iOS 가 다음 재발급 때 로그아웃되고
--   - 웹에서 API 여러 개가 동시에 재발급을 시도하면 첫 번째만 성공하고 나머지는 "토큰 불일치" → 로그아웃
-- 해결: 로그인할 때마다 세션 한 줄을 새로 만든다. 재발급은 그 줄만 회전(rotate)하고,
--   직전 토큰도 2분 동안은 받아 준다(동시 재발급 경쟁 대비). 쓸 때마다 만료를 60일 뒤로 미룬다(sliding).
-- 기존 `refresh-token` 테이블은 지우지 않고 데이터만 옮긴다 (지금 로그인한 사람이 풀리지 않게).
-- ============================================================

CREATE TABLE refresh_session (
    refresh_session_id   BIGINT       NOT NULL AUTO_INCREMENT,
    user_id              BIGINT       NOT NULL,
    token_hash           CHAR(64)     NOT NULL COMMENT '현재 refresh token 의 SHA-256',
    token                VARCHAR(512) NOT NULL COMMENT '현재 refresh token (직전 토큰으로 온 동시 요청에 같은 값을 돌려주기 위해)',
    previous_token_hash  CHAR(64)     NULL     COMMENT '바로 전 토큰의 SHA-256 (회전 직후 2분만 인정)',
    rotated_at           DATETIME(6)  NULL,
    expires_at           DATETIME(6)  NOT NULL,
    created_at           DATETIME(6)  NOT NULL,
    last_used_at         DATETIME(6)  NOT NULL,
    PRIMARY KEY (refresh_session_id),
    UNIQUE KEY uk_refresh_session_token (token_hash),
    KEY idx_refresh_session_prev (previous_token_hash),
    KEY idx_refresh_session_user (user_id, last_used_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 기존 토큰 옮기기 (아직 만료 안 된 것만)
INSERT IGNORE INTO refresh_session (user_id, token_hash, token, expires_at, created_at, last_used_at)
SELECT rt.user_id, SHA2(rt.token, 256), rt.token, rt.expires_at, NOW(6), NOW(6)
FROM `refresh-token` rt
WHERE rt.expires_at > NOW();
