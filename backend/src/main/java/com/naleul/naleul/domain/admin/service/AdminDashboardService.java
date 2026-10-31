package com.naleul.naleul.domain.admin.service;

import com.naleul.naleul.domain.admin.config.AdminDashboardProperties;
import com.naleul.naleul.domain.admin.config.AdminDashboardProperties.ModelPrice;
import com.naleul.naleul.domain.admin.dto.AdminDashboardResponse;
import com.naleul.naleul.domain.admin.dto.AdminDashboardResponse.*;
import com.naleul.naleul.domain.admin.repository.AdminDashboardQueryRepository;
import com.naleul.naleul.domain.admin.repository.AdminDashboardQueryRepository.*;
import com.naleul.naleul.domain.admin.support.AiCostCalculator;
import com.naleul.naleul.domain.admin.support.AiCostCalculator.Tokens;
import com.naleul.naleul.domain.aiUsage.enums.AiFeature;
import com.naleul.naleul.domain.goalCategory.kind.GoalType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.function.Function;

/**
 * 운영 대시보드 — 돈(매출 · AI 비용 · 서버 비용)과 서비스 지표(목표 달성률 · Task/루틴 실천률 · 사용자).
 *
 * 기간: 오늘(한국)을 포함한 최근 days 일.
 * 계산 규칙은 앱 안의 자동 판정(GoalOutcomeCalculator)과 같은 기준을 쓴다 → 화면마다 숫자가 다르게 나오지 않게.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardService {

    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    static final int MAX_DAYS = 3650;
    static final int WEEK_BUCKET_AFTER_DAYS = 90;
    static final double DAYS_PER_MONTH = 30.44;
    static final int ON_TRACK_RATE = 80;   // GoalOutcomeCalculator.infer 의 "달성" 기준

    private final AdminDashboardQueryRepository query;
    private final AdminDashboardProperties props;
    private final AiQualityCalculator aiQualityCalculator;

    /** AWS 비용 자동 불러오기 (2026-10). 켜지 않았으면 yml server-costs 만 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.naleul.naleul.domain.admin.support.AwsCostReader awsCostReader;

    public AdminDashboardResponse get(int requestedDays) {
        int days = Math.max(1, Math.min(MAX_DAYS, requestedDays));
        LocalDate today = LocalDate.now(KST);
        LocalDate from = today.minusDays(days - 1L);
        LocalDateTime fromAt = serverTime(from);
        LocalDateTime toAt = serverTime(today.plusDays(1));
        LocalDateTime now = LocalDateTime.now();
        int offset = kstOffsetMinutes();
        boolean weekly = days > WEEK_BUCKET_AFTER_DAYS;
        List<String> notes = new ArrayList<>();

        // ── 사용자 ──
        long total = query.totalUsers();
        long pro = query.proUsers(now);
        long activeInPeriod = query.usersCompletedTaskSince(fromAt, toAt);
        Users users = new Users(total, pro, percent(pro, total),
                query.usersCompletedTaskSince(serverTime(today.minusDays(6)), toAt),
                query.usersCompletedTaskSince(serverTime(today.minusDays(29)), toAt),
                activeInPeriod, query.usersWithActiveGoal());

        // ── 돈 ──
        AiCost aiCost = aiCost(fromAt, toAt, notes);
        Revenue revenue = revenue(fromAt, toAt, now, notes);
        ServerCost serverCost = serverCost(days, notes);
        long totalCost = aiCost.krw() + serverCost.periodKrw();
        Profit profit = new Profit(revenue.netKrw(), totalCost, revenue.netKrw() - totalCost,
                activeInPeriod == 0 ? null : Math.round((double) aiCost.krw() / activeInPeriod),
                activeInPeriod == 0 ? null : Math.round((double) totalCost / activeInPeriod));

        // ── 목표 · Task · 루틴 ──
        Goals goals = goals(fromAt, toAt, today);
        List<ExecutionDayRow> taskDays = query.executionByDay(from, today, false);
        List<ExecutionDayRow> routineDays = query.executionByDay(from, today, true);
        Execution tasks = execution(taskDays, query.executionUsers(from, today, false), query.openTasks(today));
        Execution routines = execution(routineDays, query.executionUsers(from, today, true), query.activeRoutines());
        Practice practice = practice(from, today, fromAt, toAt, offset);

        FunnelRow f = query.goalSessions(fromAt, toAt);
        AiGoalFunnel funnel = new AiGoalFunnel(f.sessions(), query.goalDrafts(fromAt, toAt),
                f.confirmed(), f.abandoned(), percent(f.confirmed(), f.sessions()));

        AiQuality aiQuality = aiQualityCalculator.calculate(from, today, fromAt, toAt,
                from.atStartOfDay(), today.plusDays(1).atStartOfDay());

        List<DailyPoint> series = series(from, today, weekly, taskDays, routineDays,
                query.aiUsageByDay(fromAt, toAt, offset), query.dailyActiveUsers(fromAt, toAt, offset));
        if (weekly) notes.add("기간이 " + WEEK_BUCKET_AFTER_DAYS + "일을 넘어 그래프는 주 단위(월요일 시작)로 묶었어요. 주간 활동 사용자는 일별 값의 합이 아니라 최댓값이에요.");

        return new AdminDashboardResponse(
                new AdminDashboardResponse.Period(from, today, days, weekly ? "WEEK" : "DAY", LocalDateTime.now(KST)),
                new Finance(revenue, aiCost, serverCost, profit),
                users, goals, tasks, routines, practice, funnel, aiQuality, series, notes);
    }

    // ───────────────────── AI 비용 ─────────────────────

    private AiCost aiCost(LocalDateTime fromAt, LocalDateTime toAt, List<String> notes) {
        List<ModelPrice> prices = props.modelPricesOrDefault();
        BigDecimal usdKrw = props.usdKrw();
        List<AiUsageRow> rows = query.aiUsageByFeatureAndModel(fromAt, toAt);

        Map<String, long[]> featureTokens = new LinkedHashMap<>();   // calls, failed, in, out
        Map<String, BigDecimal> featureUsd = new LinkedHashMap<>();
        Map<String, long[]> modelTokens = new TreeMap<>();
        Map<String, BigDecimal> modelUsd = new TreeMap<>();
        Set<String> unpriced = new TreeSet<>();
        long calls = 0, failed = 0, in = 0, out = 0, cw = 0, cr = 0;
        BigDecimal usd = BigDecimal.ZERO;

        for (AiUsageRow r : rows) {
            BigDecimal cost = BigDecimal.ZERO;
            long tokens = r.input() + r.output() + r.cacheWrite() + r.cacheRead();
            if (tokens > 0) {
                Optional<ModelPrice> price = AiCostCalculator.priceOf(prices, r.model());
                if (price.isPresent()) {
                    cost = AiCostCalculator.usd(price.get(), new Tokens(r.input(), r.output(), r.cacheWrite(), r.cacheRead()));
                } else {
                    unpriced.add(String.valueOf(r.model()));
                }
            }
            calls += r.calls(); failed += r.failed(); in += r.input(); out += r.output();
            cw += r.cacheWrite(); cr += r.cacheRead(); usd = usd.add(cost);

            add(featureTokens, r.feature(), r.calls(), r.failed(), r.input(), r.output());
            featureUsd.merge(r.feature(), cost, BigDecimal::add);
            if (r.model() != null) {
                add(modelTokens, r.model(), r.calls(), r.failed(), r.input(), r.output());
                modelUsd.merge(r.model(), cost, BigDecimal::add);
            }
        }

        List<AiFeatureRow> byFeature = featureTokens.entrySet().stream().map(e -> {
            long[] v = e.getValue();
            BigDecimal u = featureUsd.get(e.getKey());
            long success = v[0] - v[1];
            return new AiFeatureRow(e.getKey(), featureLabel(e.getKey()), v[0], v[1], v[2], v[3],
                    scale(u), krw(u, usdKrw),
                    success == 0 ? null : u.divide(BigDecimal.valueOf(success), 4, RoundingMode.HALF_UP));
        }).sorted(Comparator.comparing(AiFeatureRow::usd).reversed()).toList();

        List<AiModelRow> byModel = modelTokens.entrySet().stream().map(e -> {
            long[] v = e.getValue();
            BigDecimal u = modelUsd.get(e.getKey());
            return new AiModelRow(e.getKey(), v[0], v[2], v[3], scale(u), krw(u, usdKrw));
        }).sorted(Comparator.comparing(AiModelRow::usd).reversed()).toList();

        LocalDateTime since = query.aiTrackingSince();
        if (since == null) {
            notes.add("AI 토큰 기록(ai_call_log)이 아직 없어요. 이번 배포 이후의 호출부터 비용이 계산돼요.");
        }
        if (!unpriced.isEmpty()) {
            notes.add("단가를 모르는 모델이 있어 AI 비용에서 빠졌어요: " + String.join(", ", unpriced)
                    + " → application.yml admin.dashboard.model-prices 에 추가해 주세요.");
        }
        return new AiCost(scale(usd), krw(usd, usdKrw), usdKrw, calls, failed, in, out, cw, cr,
                byFeature, byModel, List.copyOf(unpriced), since);
    }

    private static void add(Map<String, long[]> map, String key, long calls, long failed, long in, long out) {
        long[] v = map.computeIfAbsent(key, k -> new long[4]);
        v[0] += calls; v[1] += failed; v[2] += in; v[3] += out;
    }

    private static String featureLabel(String key) {
        try {
            return AiFeature.valueOf(key).label();
        } catch (IllegalArgumentException e) {
            return key;
        }
    }

    // ───────────────────── 매출 ─────────────────────

    private Revenue revenue(LocalDateTime fromAt, LocalDateTime toAt, LocalDateTime now, List<String> notes) {
        boolean sandbox = props.includeSandbox();
        Set<String> unknownCurrency = new TreeSet<>();
        long gross = 0, refund = 0;
        int purchases = 0, renewals = 0, refunds = 0;
        Map<String, long[]> productPeriod = new TreeMap<>();   // payments, grossKrw

        for (RevenueRow r : query.revenue(fromAt, toAt, sandbox)) {
            Long krw = toKrw(r.amount(), r.currency());
            if (krw == null) {
                unknownCurrency.add(r.currency());
                krw = 0L;
            }
            switch (r.eventType()) {
                case "PURCHASE", "RENEWAL" -> {
                    gross += krw;
                    if (r.eventType().equals("PURCHASE")) purchases += (int) r.count();
                    else renewals += (int) r.count();
                    long[] p = productPeriod.computeIfAbsent(r.productId(), k -> new long[2]);
                    p[0] += r.count();
                    p[1] += krw;
                }
                case "REFUND" -> {
                    refund += krw;
                    refunds += (int) r.count();
                }
                default -> {
                }
            }
        }
        long fee = Math.round(Math.max(0, gross - refund) * props.appleFeeRate().doubleValue());
        long net = gross - refund - fee;

        // ── MRR 추정: 활성 구독 수 × 월 환산 가격 ──
        Map<String, Long> priceKrw = new HashMap<>();
        props.productPricesOrEmpty().forEach(p -> priceKrw.put(p.productId(), p.priceKrw()));
        for (LatestPriceRow p : query.latestPrices(sandbox)) {     // 실제 결제 금액이 설정값보다 우선
            Long krw = toKrw(p.amount(), p.currency());
            if (krw != null) priceKrw.put(p.productId(), krw);
        }

        Map<String, ProductRow> products = new TreeMap<>();
        long mrr = 0;
        int activeSubs = 0;
        Set<String> unpricedProducts = new TreeSet<>();
        for (ActiveSubscriptionRow s : query.activeSubscriptions(now, sandbox)) {
            activeSubs += (int) s.count();
            Long monthly = monthlyPrice(priceKrw.get(s.productId()), s.avgPeriodDays());
            if (monthly == null) unpricedProducts.add(s.productId());
            else mrr += monthly * s.count();
            long[] p = productPeriod.getOrDefault(s.productId(), new long[2]);
            products.put(s.productId(), new ProductRow(s.productId(), (int) p[0], p[1], (int) s.count(), monthly));
        }
        productPeriod.forEach((id, p) -> products.putIfAbsent(id,
                new ProductRow(id, (int) p[0], p[1], 0, monthlyPrice(priceKrw.get(id), null))));

        LocalDateTime since = query.revenueTrackingSince();
        if (since == null) {
            notes.add("결제 금액 기록(iap_revenue_event)이 아직 없어요. 이번 배포 이후 결제부터 매출이 쌓여요. MRR 은 활성 구독 수 × 상품 가격으로 추정해요.");
        }
        if (!unpricedProducts.isEmpty()) {
            notes.add("가격을 몰라 MRR 에서 빠진 상품: " + String.join(", ", unpricedProducts)
                    + " → admin.dashboard.product-prices 에 원 가격을 넣어 주세요.");
        }
        if (!unknownCurrency.isEmpty()) {
            notes.add("원화로 바꿀 수 없는 통화가 있어 매출에서 빠졌어요: " + String.join(", ", unknownCurrency));
        }
        if (sandbox) notes.add("include-sandbox=true: 테스트(Sandbox) 결제도 매출에 포함돼 있어요.");

        return new Revenue(gross, refund, fee, net, purchases, renewals, refunds, activeSubs, mrr,
                props.appleFeeRate(), List.copyOf(products.values()), since);
    }

    /** 결제 금액 → 월 환산. 구독 주기를 모르거나 한 달 남짓이면 그대로, 1년이면 ÷12 */
    static Long monthlyPrice(Long priceKrw, Double periodDays) {
        if (priceKrw == null) return null;
        if (periodDays == null || periodDays < 40) return priceKrw;
        return Math.round(priceKrw * DAYS_PER_MONTH / periodDays);
    }

    private Long toKrw(BigDecimal amount, String currency) {
        if (amount == null) return 0L;
        if (currency == null || currency.equalsIgnoreCase("KRW")) return amount.setScale(0, RoundingMode.HALF_UP).longValue();
        if (currency.equalsIgnoreCase("USD")) return krw(amount, props.usdKrw());
        return null;
    }

    // ───────────────────── 서버 비용 ─────────────────────

    private ServerCost serverCost(int days, List<String> notes) {
        List<ServerCostItem> items = new ArrayList<>();
        // AWS 는 Cost Explorer 에서 자동으로 (2026-10) — 지난달 서비스별 금액을 월 비용으로 쓴다
        Optional<com.naleul.naleul.domain.admin.support.AwsCostReader.AwsCost> aws = awsCostReader != null ? awsCostReader.read() : Optional.empty();
        aws.ifPresent(a -> {
            a.lastMonthByService().forEach((service, usd) -> {
                long monthly = krw(usd, props.usdKrw());
                items.add(new ServerCostItem("AWS · " + shortAwsName(service), monthly,
                        Math.round(monthly * days / DAYS_PER_MONTH)));
            });
            notes.add(String.format("AWS 비용은 Cost Explorer 에서 자동으로 불러와요 — %d월 합계 ₩%,d · 이번 달 %s까지 ₩%,d (6시간마다 갱신, 1달러 = %s원).",
                    a.lastMonth().getMonthValue(), krw(a.lastMonthTotal(), props.usdKrw()),
                    a.monthToDateUntil().getMonthValue() + "/" + a.monthToDateUntil().getDayOfMonth(),
                    krw(a.monthToDate(), props.usdKrw()), props.usdKrw().toPlainString()));
        });
        if (awsCostReader != null && awsCostReader.enabled() && aws.isEmpty()) {
            notes.add("AWS 비용을 불러오지 못했어요 → IAM 에 ce:GetCostAndUsage 권한이 있는지, Billing 에서 Cost Explorer 가 켜져 있는지 확인해 주세요.");
        }
        // AWS 밖의 고정비 (도메인 · Apple Developer · Vercel 등) 는 yml server-costs 그대로
        //   AWS 자동 불러오기를 켰으면 yml 의 AWS 항목(EC2 · RDS · S3 …)은 빼야 두 번 세지 않아요
        props.serverCostsOrEmpty().stream()
                .map(c -> new ServerCostItem(c.name(), c.monthlyKrw(), Math.round(c.monthlyKrw() * days / DAYS_PER_MONTH)))
                .forEach(items::add);
        if (items.isEmpty()) {
            notes.add("서버 비용이 설정되지 않았어요 → ADMIN_AWS_COST_ENABLED=true 로 AWS 비용을 자동으로 불러오거나, application.yml admin.dashboard.server-costs 에 월 비용을 넣어 주세요.");
        }
        return new ServerCost(items.stream().mapToLong(ServerCostItem::monthlyKrw).sum(),
                items.stream().mapToLong(ServerCostItem::periodKrw).sum(), items);
    }

    /** "Amazon Elastic Compute Cloud - Compute" → "EC2" 처럼 짧게 (모르는 이름은 그대로) */
    static String shortAwsName(String service) {
        if (service == null) return "기타";
        if (service.startsWith("Amazon Elastic Compute Cloud")) return "EC2";
        if (service.startsWith("EC2 - Other")) return "EC2 기타(EBS · 데이터 전송)";
        if (service.startsWith("Amazon Relational Database Service")) return "RDS";
        if (service.startsWith("Amazon Simple Storage Service")) return "S3";
        if (service.startsWith("Amazon Virtual Private Cloud")) return "VPC(공인 IP)";
        if (service.startsWith("Amazon Route 53")) return "Route 53";
        if (service.startsWith("Amazon CloudFront")) return "CloudFront";
        if (service.startsWith("AmazonCloudWatch") || service.startsWith("Amazon CloudWatch")) return "CloudWatch";
        if (service.equals("Tax")) return "세금(VAT)";
        return service.replace("Amazon ", "").replace("AWS ", "");
    }

    // ───────────────────── 목표 ─────────────────────

    private Goals goals(LocalDateTime fromAt, LocalDateTime toAt, LocalDate today) {
        List<OutcomeRow> outcomes = query.outcomes(fromAt, toAt);
        int achieved = sum(outcomes, "ACHIEVED"), partial = sum(outcomes, "PARTIAL"), not = sum(outcomes, "NOT_ACHIEVED");
        int ended = achieved + partial + not;

        List<GoalExecutionRow> exec = query.inProgressGoalExecution(today);
        List<Integer> rates = exec.stream().filter(r -> r.due() > 0)
                .map(r -> (int) Math.round(100.0 * r.done() / r.due())).toList();
        int[] histogram = new int[5];
        rates.forEach(r -> histogram[Math.min(4, r / 20)]++);
        Integer avg = rates.isEmpty() ? null : (int) Math.round(rates.stream().mapToInt(Integer::intValue).average().orElse(0));
        long onTrack = rates.stream().filter(r -> r >= ON_TRACK_RATE).count();

        List<GoalGroupRow> byType = group(outcomes, r -> r.goalType() == null ? "UNKNOWN" : r.goalType(), AdminDashboardService::goalTypeLabel);
        List<GoalGroupRow> bySource = group(outcomes, r -> r.aiGenerated() ? "AI" : "MANUAL",
                k -> k.equals("AI") ? "AI 로 만든 목표" : "직접 만든 목표");

        return new Goals(ended, achieved, partial, not, percent(achieved, ended), percent(achieved + partial, ended),
                (int) query.inProgressGoals(), avg, percent(onTrack, rates.size()),
                Arrays.stream(histogram).boxed().toList(), byType, bySource,
                byPath(query.outcomesByPath(fromAt, toAt)), inProgressByPath(exec));
    }

    // ───────────────────── 핵심 지표 × AI 정확도 (2026-10-25) ─────────────────────

    /** 만든 방법 · AI 초안 경로 — 화면에 보이는 순서 */
    static final List<String> PATH_ORDER = List.of("AS_IS", "MANUAL", "QUICK_FIX", "REGEN", "AI_LEGACY", "MANUAL_GOAL");

    static String pathLabel(String key) {
        return switch (key) {
            case "AS_IS" -> "AI · 그대로 확정";
            case "MANUAL" -> "AI · 직접 고쳐 확정";
            case "QUICK_FIX" -> "AI · 말로 고쳐 확정";
            case "REGEN" -> "AI · 다시 생성 뒤 확정";
            case "AI_LEGACY" -> "AI · 경로 기록 없음 (V68 이전)";
            case "AI" -> "AI 로 만든 목표 전체";
            case "MANUAL_GOAL" -> "직접 만든 목표";
            default -> key;
        };
    }

    static List<PathGroupRow> byPath(List<PathOutcomeRow> rows) {
        List<PathGroupRow> out = new ArrayList<>();
        for (String key : PATH_ORDER) {
            List<PathOutcomeRow> g = rows.stream().filter(r -> key.equals(r.key())).toList();
            int n = (int) g.stream().mapToLong(PathOutcomeRow::count).sum();
            if (n == 0) continue;
            int a = (int) g.stream().filter(r -> "ACHIEVED".equals(r.result())).mapToLong(PathOutcomeRow::count).sum();
            int p = (int) g.stream().filter(r -> "PARTIAL".equals(r.result())).mapToLong(PathOutcomeRow::count).sum();
            int user = (int) g.stream().filter(r -> "USER".equals(r.source())).mapToLong(PathOutcomeRow::count).sum();
            out.add(new PathGroupRow(key, pathLabel(key), n, a, p, percent(a, n), percent(a + p, n),
                    wavg(g, PathOutcomeRow::avgRoutineRate), wavg(g, PathOutcomeRow::avgTaskRate),
                    wavg(g, PathOutcomeRow::keptRate), user));
        }
        return out;
    }

    private static Integer wavg(List<PathOutcomeRow> rows, Function<PathOutcomeRow, Double> avgOf) {
        double sum = 0;
        long n = 0;
        for (PathOutcomeRow r : rows) {
            Double v = avgOf.apply(r);
            if (v == null) continue;
            sum += v * r.count();
            n += r.count();
        }
        return n == 0 ? null : (int) Math.round(sum / n);
    }

    /** 진행 중 목표 실천률을 AI 전체 + 경로별 + 직접으로 (해야 했던 게 없는 목표는 뺀다) */
    static List<InProgressGroup> inProgressByPath(List<GoalExecutionRow> exec) {
        List<InProgressGroup> out = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        keys.add("AI");
        keys.addAll(PATH_ORDER);
        for (String key : keys) {
            List<Integer> rates = exec.stream().filter(r -> r.due() > 0)
                    .filter(r -> key.equals("AI") ? !"MANUAL_GOAL".equals(r.key()) : key.equals(r.key()))
                    .map(r -> (int) Math.round(100.0 * r.done() / r.due())).toList();
            if (rates.isEmpty()) continue;
            long onTrack = rates.stream().filter(r -> r >= ON_TRACK_RATE).count();
            out.add(new InProgressGroup(key, pathLabel(key), rates.size(),
                    (int) Math.round(rates.stream().mapToInt(Integer::intValue).average().orElse(0)),
                    percent(onTrack, rates.size())));
        }
        return out;
    }

    static final List<String> ORIGIN_ORDER = List.of("AI_KEPT", "AI_EDITED", "AI_ADDED", "AI_OTHER", "MANUAL", "NO_GOAL");

    static String originLabel(String key) {
        return switch (key) {
            case "AI_KEPT" -> "AI 초안 그대로 둔 것";
            case "AI_EDITED" -> "AI 초안을 고친 것";
            case "AI_ADDED" -> "초안 화면에서 사용자가 더한 것";
            case "AI_OTHER" -> "AI 목표 · 확정 뒤 추가 (또는 V68 이전)";
            case "MANUAL" -> "직접 만든 목표";
            case "NO_GOAL" -> "목표 없는 Task (임시 목표 포함)";
            default -> key;
        };
    }

    private Practice practice(LocalDate from, LocalDate today, LocalDateTime fromAt, LocalDateTime toAt, int off) {
        long td = 0, tc = 0, rd = 0, rc = 0;
        for (ActiveDayRow r : query.executionOnActiveDays(from, today, fromAt, toAt, off)) {
            if (r.routine()) { rd += r.due(); rc += r.done(); } else { td += r.due(); tc += r.done(); }
        }
        return new Practice(new ActiveDay(td, tc, percent(tc, td), rd, rc, percent(rc, rd)),
                origins(query.executionByOrigin(from, today)), query.visitTrackingSince());
    }

    static List<AdminDashboardResponse.OriginRow> origins(List<AdminDashboardQueryRepository.OriginRow> rows) {
        List<AdminDashboardResponse.OriginRow> out = new ArrayList<>();
        for (String key : ORIGIN_ORDER) {
            long rd = 0, rc = 0, td = 0, tc = 0, users = 0;
            for (AdminDashboardQueryRepository.OriginRow r : rows) {
                if (!key.equals(r.origin())) continue;
                if (r.routine()) { rd += r.due(); rc += r.done(); } else { td += r.due(); tc += r.done(); }
                users = Math.max(users, r.users());   // 루틴 · 일반 중 큰 쪽 (겹치는 사람은 한 번)
            }
            if (rd + td == 0) continue;
            out.add(new AdminDashboardResponse.OriginRow(key, originLabel(key), rd, rc, percent(rc, rd), td, tc,
                    percent(tc, td), users));
        }
        return out;
    }

    private static int sum(List<OutcomeRow> rows, String result) {
        return (int) rows.stream().filter(r -> r.result().equals(result)).mapToLong(OutcomeRow::count).sum();
    }

    private static List<GoalGroupRow> group(List<OutcomeRow> rows, Function<OutcomeRow, String> keyOf,
                                            Function<String, String> labelOf) {
        Map<String, List<OutcomeRow>> grouped = new TreeMap<>();
        rows.forEach(r -> grouped.computeIfAbsent(keyOf.apply(r), k -> new ArrayList<>()).add(r));
        return grouped.entrySet().stream().map(e -> {
            List<OutcomeRow> g = e.getValue();
            int a = sum(g, "ACHIEVED"), p = sum(g, "PARTIAL");
            int n = (int) g.stream().mapToLong(OutcomeRow::count).sum();
            return new GoalGroupRow(e.getKey(), labelOf.apply(e.getKey()), n, a, p, percent(a, n),
                    weightedAvg(g, OutcomeRow::avgRoutineRate), weightedAvg(g, OutcomeRow::avgTaskRate));
        }).sorted(Comparator.comparingInt(GoalGroupRow::ended).reversed()).toList();
    }

    /** 그룹별 평균을 개수로 가중 평균 (평균값이 없는 행은 뺀다) */
    private static Integer weightedAvg(List<OutcomeRow> rows, Function<OutcomeRow, Double> avgOf) {
        double sum = 0;
        long n = 0;
        for (OutcomeRow r : rows) {
            Double v = avgOf.apply(r);
            if (v == null) continue;
            sum += v * r.count();
            n += r.count();
        }
        return n == 0 ? null : (int) Math.round(sum / n);
    }

    private static String goalTypeLabel(String key) {
        try {
            return GoalType.valueOf(key).getLabel();
        } catch (IllegalArgumentException e) {
            return "분류 없음";
        }
    }

    // ───────────────────── Task · 루틴 ─────────────────────

    private static Execution execution(List<ExecutionDayRow> days, long users, long activeItems) {
        long due = days.stream().mapToLong(ExecutionDayRow::due).sum();
        long done = days.stream().mapToLong(ExecutionDayRow::done).sum();
        long skipped = days.stream().mapToLong(ExecutionDayRow::skipped).sum();
        return new Execution(due, done, skipped, percent(done, due), users, activeItems);
    }

    // ───────────────────── 그래프 ─────────────────────

    private List<DailyPoint> series(LocalDate from, LocalDate to, boolean weekly,
                                    List<ExecutionDayRow> taskDays, List<ExecutionDayRow> routineDays,
                                    List<AiDailyRow> aiDays, List<DailyActiveRow> activeDays) {
        Function<LocalDate, LocalDate> bucketOf = d -> weekly ? d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) : d;
        Map<LocalDate, long[]> counts = new TreeMap<>();      // taskDue, taskDone, routineDue, routineDone, active
        Map<LocalDate, BigDecimal> usd = new HashMap<>();
        for (LocalDate d = bucketOf.apply(from); !d.isAfter(to); d = d.plus(1, weekly ? ChronoUnit.WEEKS : ChronoUnit.DAYS)) {
            counts.put(d, new long[5]);
        }
        taskDays.forEach(r -> bump(counts, bucketOf.apply(r.date()), 0, r.due(), r.done()));
        routineDays.forEach(r -> bump(counts, bucketOf.apply(r.date()), 2, r.due(), r.done()));
        activeDays.forEach(r -> {
            long[] v = counts.get(bucketOf.apply(r.date()));
            if (v != null) v[4] = Math.max(v[4], r.users());
        });
        List<ModelPrice> prices = props.modelPricesOrDefault();
        aiDays.forEach(r -> AiCostCalculator.priceOf(prices, r.model()).ifPresent(p ->
                usd.merge(bucketOf.apply(r.date()),
                        AiCostCalculator.usd(p, new Tokens(r.input(), r.output(), r.cacheWrite(), r.cacheRead())),
                        BigDecimal::add)));

        return counts.entrySet().stream().map(e -> {
            long[] v = e.getValue();
            return new DailyPoint(e.getKey(), v[0], v[1], percent(v[1], v[0]), v[2], v[3], percent(v[3], v[2]),
                    scale(usd.getOrDefault(e.getKey(), BigDecimal.ZERO)), v[4]);
        }).toList();
    }

    private static void bump(Map<LocalDate, long[]> counts, LocalDate key, int idx, long due, long done) {
        long[] v = counts.get(key);
        if (v == null) return;
        v[idx] += due;
        v[idx + 1] += done;
    }

    // ───────────────────── 공통 ─────────────────────

    static Integer percent(long part, long whole) {
        return whole <= 0 ? null : (int) Math.round(100.0 * part / whole);
    }

    private static BigDecimal scale(BigDecimal usd) {
        return usd.setScale(4, RoundingMode.HALF_UP);
    }

    private static long krw(BigDecimal usd, BigDecimal usdKrw) {
        return usd.multiply(usdKrw).setScale(0, RoundingMode.HALF_UP).longValue();
    }

    /** 한국 날짜 0시 → DB 에 저장된 서버 시간대 기준 시각 (AiUsageService.startOfUserToday 와 같은 변환) */
    private static LocalDateTime serverTime(LocalDate kstDate) {
        return kstDate.atStartOfDay(KST).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }

    /** 서버 시각 + 이 값(분) = 한국 시각 */
    private static int kstOffsetMinutes() {
        Instant now = Instant.now();
        return (KST.getRules().getOffset(now).getTotalSeconds()
                - ZoneId.systemDefault().getRules().getOffset(now).getTotalSeconds()) / 60;
    }
}
