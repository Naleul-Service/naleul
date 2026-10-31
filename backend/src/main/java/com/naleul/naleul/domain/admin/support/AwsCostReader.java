package com.naleul.naleul.domain.admin.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.costexplorer.CostExplorerClient;
import software.amazon.awssdk.services.costexplorer.model.DateInterval;
import software.amazon.awssdk.services.costexplorer.model.GetCostAndUsageRequest;
import software.amazon.awssdk.services.costexplorer.model.GetCostAndUsageResponse;
import software.amazon.awssdk.services.costexplorer.model.Granularity;
import software.amazon.awssdk.services.costexplorer.model.Group;
import software.amazon.awssdk.services.costexplorer.model.GroupDefinition;
import software.amazon.awssdk.services.costexplorer.model.GroupDefinitionType;
import software.amazon.awssdk.services.costexplorer.model.ResultByTime;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 운영 대시보드 — AWS 서버 비용을 Cost Explorer 에서 자동으로 불러온다 (2026-10).
 *
 *  - 지난달(다 끝난 달) 서비스별 금액 → "서버 비용" 월 고정비로 (yml server-costs 를 손으로 채우지 않아도 됨)
 *  - 이번 달 1일 ~ 어제까지 금액 → 안내 문구로
 *  - Cost Explorer API 는 호출마다 0.01달러라서 6시간 동안 결과를 재사용한다 (대시보드를 자주 열어도 하루 최대 몇 번)
 *  - 켜는 법: admin.dashboard.aws-cost.enabled=true (ADMIN_AWS_COST_ENABLED=true)
 *    + IAM 사용자(또는 EC2 역할)에 ce:GetCostAndUsage 권한 + Billing 콘솔에서 Cost Explorer 켜기
 *  - 실패하면 빈 값 → 대시보드는 yml server-costs 로 그대로 동작
 */
@Slf4j
@Component
public class AwsCostReader {

    /** 금액은 USD. 서비스 이름은 AWS 표기 그대로 ("Amazon Elastic Compute Cloud - Compute") */
    public record AwsCost(LocalDate lastMonth, Map<String, BigDecimal> lastMonthByService, BigDecimal lastMonthTotal,
                          BigDecimal monthToDate, LocalDate monthToDateUntil, Instant fetchedAt) {
    }

    private static final Duration CACHE = Duration.ofHours(6);
    /** 1센트도 안 되는 서비스는 묶어서 "기타" */
    private static final BigDecimal MIN_SERVICE_USD = new BigDecimal("0.01");

    private final boolean enabled;
    private final ObjectProvider<AwsCredentialsProvider> credentials;
    private volatile AwsCost cached;
    private volatile Instant failedAt;

    public AwsCostReader(@Value("${admin.dashboard.aws-cost.enabled:false}") boolean enabled,
                         ObjectProvider<AwsCredentialsProvider> credentials) {
        this.enabled = enabled;
        this.credentials = credentials;
    }

    public boolean enabled() {
        return enabled;
    }

    /** 켜져 있고 불러오기에 성공했으면 값 (6시간 캐시). 실패 직후 30분은 다시 부르지 않는다 */
    public synchronized Optional<AwsCost> read() {
        if (!enabled) return Optional.empty();
        Instant now = Instant.now();
        if (cached != null && cached.fetchedAt().plus(CACHE).isAfter(now)) return Optional.of(cached);
        if (failedAt != null && failedAt.plus(Duration.ofMinutes(30)).isAfter(now)) return Optional.ofNullable(cached);
        try {
            cached = fetch(LocalDate.now(ZoneOffset.UTC));
            failedAt = null;
        } catch (RuntimeException e) {
            failedAt = now;
            log.warn("[AwsCost] Cost Explorer 불러오기 실패 — yml server-costs 로 계속 (권한 ce:GetCostAndUsage 확인)", e);
        }
        return Optional.ofNullable(cached);
    }

    /** Cost Explorer 는 UTC 날짜 기준 · 끝 날짜는 포함하지 않는다 */
    AwsCost fetch(LocalDate todayUtc) {
        LocalDate thisMonth = todayUtc.withDayOfMonth(1);
        LocalDate lastMonth = thisMonth.minusMonths(1);
        AwsCredentialsProvider provider = credentials.getIfAvailable(DefaultCredentialsProvider::create);
        try (CostExplorerClient ce = CostExplorerClient.builder()
                .region(Region.US_EAST_1)   // Cost Explorer 는 us-east-1 하나뿐
                .credentialsProvider(provider)
                .build()) {
            Map<String, BigDecimal> byService = byService(ce.getCostAndUsage(request(lastMonth, thisMonth, true)));
            BigDecimal total = byService.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal mtd = BigDecimal.ZERO;
            if (todayUtc.isAfter(thisMonth)) {   // 1일이면 아직 이번 달 데이터가 없다
                GetCostAndUsageResponse r = ce.getCostAndUsage(request(thisMonth, todayUtc, false));
                for (ResultByTime t : r.resultsByTime()) mtd = mtd.add(amount(t.total().get("UnblendedCost")));
            }
            log.info("[AwsCost] 불러옴 lastMonth={} totalUsd={} monthToDateUsd={}", lastMonth, total, mtd);
            return new AwsCost(lastMonth, byService, total, mtd, todayUtc.minusDays(1), Instant.now());
        }
    }

    private static GetCostAndUsageRequest request(LocalDate start, LocalDate end, boolean groupByService) {
        GetCostAndUsageRequest.Builder b = GetCostAndUsageRequest.builder()
                .timePeriod(DateInterval.builder().start(start.toString()).end(end.toString()).build())
                .granularity(Granularity.MONTHLY)
                .metrics("UnblendedCost");
        if (groupByService) {
            b.groupBy(GroupDefinition.builder().type(GroupDefinitionType.DIMENSION).key("SERVICE").build());
        }
        return b.build();
    }

    /** 금액 큰 순서 · 1센트 미만은 "기타"로 */
    private static Map<String, BigDecimal> byService(GetCostAndUsageResponse r) {
        Map<String, BigDecimal> sums = new LinkedHashMap<>();
        for (ResultByTime t : r.resultsByTime()) {
            for (Group g : t.groups()) {
                String name = g.keys().isEmpty() ? "기타" : g.keys().get(0);
                sums.merge(name, amount(g.metrics().get("UnblendedCost")), BigDecimal::add);
            }
        }
        List<Map.Entry<String, BigDecimal>> sorted = new ArrayList<>(sums.entrySet());
        sorted.sort(Map.Entry.<String, BigDecimal>comparingByValue(Comparator.reverseOrder()));
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        BigDecimal etc = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> e : sorted) {
            if (e.getValue().compareTo(MIN_SERVICE_USD) < 0) etc = etc.add(e.getValue());
            else out.put(e.getKey(), e.getValue());
        }
        if (etc.signum() > 0) out.merge("기타", etc, BigDecimal::add);
        return out;
    }

    private static BigDecimal amount(software.amazon.awssdk.services.costexplorer.model.MetricValue v) {
        if (v == null || v.amount() == null) return BigDecimal.ZERO;
        try {
            return new BigDecimal(v.amount());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
