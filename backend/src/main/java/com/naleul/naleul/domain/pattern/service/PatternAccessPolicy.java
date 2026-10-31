package com.naleul.naleul.domain.pattern.service;

import com.naleul.naleul.domain.pattern.enums.PatternPeriod;
import org.springframework.stereotype.Component;

/**
 * 나의 패턴 Free/Pro 확인 지점 (명세 4 · 13장 #5 — 아직 결정 전이라 모두 허용).
 * 정해지면 여기서 UserRole 을 보고 CustomException(ErrorCode.PREMIUM_REQUIRED 등)을 던진다.
 */
@Component
public class PatternAccessPolicy {

    public void check(Long userId, PatternPeriod period) {
        // 지금은 모두 공개
    }
}
