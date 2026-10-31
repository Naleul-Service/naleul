package com.naleul.naleul.domain.playBilling.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Play 결제에 붙이는 사용자 표시 (obfuscatedAccountId) — 앱 · 서버가 같은 방법으로 만든다.
 * 결제한 나를 계정과 영수증을 보낸 나를 계정이 같은지 확인해서, 남의 구매 토큰으로 Pro 를 받지 못하게 한다.
 * 개인정보를 Google 에 넘기지 않게 user_id 를 그대로 쓰지 않고 해시 (64자 = Play 한도).
 *
 * Android: SHA-256("naleul-play:" + userId) 소문자 hex
 */
public final class PlayAccountId {

    private PlayAccountId() {
    }

    public static String of(Long userId) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(("naleul-play:" + userId).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
