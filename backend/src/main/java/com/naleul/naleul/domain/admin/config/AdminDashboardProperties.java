package com.naleul.naleul.domain.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;
import java.util.List;

/**
 * application.yml 의 admin.dashboard.* — 운영 대시보드 계산에 쓰는 값.
 * yml 에 없으면 기본값으로 동작한다 (운영 yml 을 당장 안 고쳐도 기동에 문제 없음).
 *
 * 금액은 DB 에서 알 수 없는 값(AI 모델 단가 · 서버 월 비용 · 환율)만 여기에 둔다.
 */
@ConfigurationProperties(prefix = "admin.dashboard")
public record AdminDashboardProperties(
        /* 대시보드를 볼 수 있는 사용자 id (users.user_role = ADMIN 인 사용자도 볼 수 있다). 예) ADMIN_USER_IDS=1,2 */
        @DefaultValue({"1", "2"}) List<Long> adminUserIds,
        /* 1달러 = 몇 원 */
        @DefaultValue("1400") BigDecimal usdKrw,
        /* Apple 수수료율. 연 매출 100만 달러 이하 Small Business Program 이면 0.15, 아니면 0.30 */
        @DefaultValue("0.15") BigDecimal appleFeeRate,
        /* Sandbox(테스트 결제)도 매출에 넣을지 — 개발 서버에서 화면 확인할 때만 true */
        @DefaultValue("false") boolean includeSandbox,
        /* AI 모델 단가 (달러 / 100만 토큰). 비우면 기본 단가표를 쓴다 */
        List<ModelPrice> modelPrices,
        /* 서버 · 인프라 월 고정비 (원) */
        List<ServerCost> serverCosts,
        /* 상품 가격 (원) — 아직 결제 기록(iap_revenue_event)이 없는 상품의 MRR 추정에만 쓴다 */
        List<ProductPrice> productPrices,
        /* 서버 기동 때 admin-user-ids 를 user_role = ADMIN 으로 맞출지 (AdminRoleSync, 기본 false) */
        @DefaultValue("false") boolean syncRoleOnStartup
) {

    /**
     * @param prefix 모델 id 앞부분. 응답 모델 id(claude-haiku-4-5-20251001)가 이것으로 시작하면 이 단가를 쓴다
     */
    public record ModelPrice(String prefix, BigDecimal inputUsdPerMtok, BigDecimal outputUsdPerMtok) {
    }

    public record ServerCost(String name, long monthlyKrw) {
    }

    public record ProductPrice(String productId, long priceKrw) {
    }

    /** 2026-10 기준 Anthropic 공식 단가. 단가가 바뀌면 yml 의 model-prices 로 덮어쓴다 */
    public static final List<ModelPrice> DEFAULT_MODEL_PRICES = List.of(
            new ModelPrice("claude-haiku-4-5", new BigDecimal("1"), new BigDecimal("5")),
            new ModelPrice("claude-sonnet-5-5", new BigDecimal("2"), new BigDecimal("10")),
            new ModelPrice("claude-sonnet-4-5", new BigDecimal("3"), new BigDecimal("15")),
            new ModelPrice("claude-sonnet-4", new BigDecimal("3"), new BigDecimal("15")),
            new ModelPrice("fake", BigDecimal.ZERO, BigDecimal.ZERO)
    );

    public List<ModelPrice> modelPricesOrDefault() {
        return modelPrices == null || modelPrices.isEmpty() ? DEFAULT_MODEL_PRICES : modelPrices;
    }

    public List<ServerCost> serverCostsOrEmpty() {
        return serverCosts == null ? List.of() : serverCosts;
    }

    public List<ProductPrice> productPricesOrEmpty() {
        return productPrices == null ? List.of() : productPrices;
    }
}
