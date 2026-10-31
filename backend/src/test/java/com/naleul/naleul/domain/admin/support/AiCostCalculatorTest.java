package com.naleul.naleul.domain.admin.support;

import com.naleul.naleul.domain.admin.config.AdminDashboardProperties;
import com.naleul.naleul.domain.admin.config.AdminDashboardProperties.ModelPrice;
import com.naleul.naleul.domain.admin.support.AiCostCalculator.Tokens;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 토큰 → 달러 계산 */
class AiCostCalculatorTest {

    private static final List<ModelPrice> PRICES = AdminDashboardProperties.DEFAULT_MODEL_PRICES;

    @Test
    void 날짜가_붙은_모델_id_도_prefix_로_단가를_찾는다() {
        assertThat(AiCostCalculator.priceOf(PRICES, "claude-haiku-4-5-20251001"))
                .get().extracting(ModelPrice::prefix).isEqualTo("claude-haiku-4-5");
    }

    @Test
    void 더_길게_맞는_prefix_가_우선이다() {
        assertThat(AiCostCalculator.priceOf(PRICES, "claude-sonnet-4-5-20250929"))
                .get().extracting(ModelPrice::prefix).isEqualTo("claude-sonnet-4-5");
    }

    @Test
    void 모르는_모델은_빈값() {
        assertThat(AiCostCalculator.priceOf(PRICES, "gpt-4o")).isEmpty();
        assertThat(AiCostCalculator.priceOf(PRICES, null)).isEmpty();
    }

    @Test
    void 입력_출력_캐시를_각각_단가대로_더한다() {
        ModelPrice haiku = new ModelPrice("claude-haiku-4-5", new BigDecimal("1"), new BigDecimal("5"));
        // 입력 100만 = $1, 출력 100만 = $5, 캐시 저장 100만 = $1.25, 캐시 읽기 100만 = $0.1
        BigDecimal usd = AiCostCalculator.usd(haiku, new Tokens(1_000_000, 1_000_000, 1_000_000, 1_000_000));
        assertThat(usd).isEqualByComparingTo("7.35");
    }

    @Test
    void 일반적인_한번_호출() {
        ModelPrice sonnet = new ModelPrice("claude-sonnet-5-5", new BigDecimal("2"), new BigDecimal("10"));
        // 입력 6,000 × $2/M = 0.012, 출력 3,000 × $10/M = 0.03
        assertThat(AiCostCalculator.usd(sonnet, new Tokens(6_000, 3_000, 0, 0))).isEqualByComparingTo("0.042");
    }
}
