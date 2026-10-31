package com.naleul.naleul.domain.admin.support;

import com.naleul.naleul.domain.admin.config.AdminDashboardProperties.ModelPrice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 토큰 수 → 달러 (DB · 스프링 없이 순수 계산 → 단위 테스트 대상).
 *
 * Anthropic 과금 규칙
 *  - 일반 입력 토큰   : 입력 단가
 *  - 캐시 저장(5분)   : 입력 단가 × 1.25
 *  - 캐시 읽기        : 입력 단가 × 0.1
 *  - 출력 토큰        : 출력 단가
 *  (input_tokens 에는 캐시 토큰이 포함되지 않는다 — 세 값은 따로 더한다)
 */
public final class AiCostCalculator {

    static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);
    static final BigDecimal CACHE_WRITE_MULTIPLIER = new BigDecimal("1.25");
    static final BigDecimal CACHE_READ_MULTIPLIER = new BigDecimal("0.1");

    private AiCostCalculator() {
    }

    public record Tokens(long input, long output, long cacheWrite, long cacheRead) {
    }

    /** 모델 id 와 가장 길게 맞는 prefix 의 단가 (claude-sonnet-4-5 가 claude-sonnet-4 보다 우선) */
    public static Optional<ModelPrice> priceOf(List<ModelPrice> prices, String model) {
        if (model == null) return Optional.empty();
        return prices.stream()
                .filter(p -> p.prefix() != null && model.startsWith(p.prefix()))
                .max(Comparator.comparingInt(p -> p.prefix().length()));
    }

    public static BigDecimal usd(ModelPrice price, Tokens t) {
        BigDecimal in = price.inputUsdPerMtok();
        BigDecimal out = price.outputUsdPerMtok();
        BigDecimal total = in.multiply(BigDecimal.valueOf(t.input()))
                .add(in.multiply(CACHE_WRITE_MULTIPLIER).multiply(BigDecimal.valueOf(t.cacheWrite())))
                .add(in.multiply(CACHE_READ_MULTIPLIER).multiply(BigDecimal.valueOf(t.cacheRead())))
                .add(out.multiply(BigDecimal.valueOf(t.output())));
        return total.divide(MILLION, 6, RoundingMode.HALF_UP);
    }
}
