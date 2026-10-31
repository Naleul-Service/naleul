package com.naleul.naleul.domain.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * GET /api/v1/admin/dashboard 응답.
 *
 * 금액은 전부 원(KRW) 정수, AI 비용만 달러(usd)도 함께 준다.
 * 비율(%)은 분모가 0이면 null — 화면에서 "-" 로 보여준다 (0% 와 "데이터 없음"은 다른 뜻이라서).
 */
public record AdminDashboardResponse(
        Period period,
        Finance finance,
        Users users,
        Goals goals,
        Execution tasks,
        Execution routines,
        Practice practice,
        AiGoalFunnel aiGoalFunnel,
        AiQuality aiQuality,
        List<DailyPoint> series,
        List<String> notes
) {

    /** @param bucket DAY · WEEK — 기간이 90일을 넘으면 그래프를 주 단위로 묶는다 */
    public record Period(LocalDate from, LocalDate to, int days, String bucket, LocalDateTime generatedAt) {
    }

    // ───────────── 돈 ─────────────

    public record Finance(Revenue revenue, AiCost aiCost, ServerCost serverCost, Profit profit) {
    }

    /**
     * @param grossKrw        결제 금액 합계 (Apple 수수료 떼기 전)
     * @param refundKrw       환불 금액 합계
     * @param appleFeeKrw     Apple 수수료 (환불 뺀 금액 × 수수료율)
     * @param netKrw          실제로 들어오는 돈 = gross − refund − fee
     * @param estimatedMrrKrw 지금 활성 구독 기준 월 반복 매출 추정 (연간 구독은 ÷12)
     * @param trackingSince   결제 금액 기록을 시작한 시각 (그 전 결제는 금액을 모른다)
     */
    public record Revenue(long grossKrw, long refundKrw, long appleFeeKrw, long netKrw,
                          int purchaseCount, int renewalCount, int refundCount,
                          int activeSubscriptions, long estimatedMrrKrw, BigDecimal appleFeeRate,
                          List<ProductRow> products, LocalDateTime trackingSince) {
    }

    /** @param monthlyPriceKrw null 이면 가격을 몰라서 MRR 에서 빠졌다 */
    public record ProductRow(String productId, int periodPayments, long periodGrossKrw,
                             int activeSubscriptions, Long monthlyPriceKrw) {
    }

    /**
     * @param unpricedModels 단가표에 없어 비용 계산에서 빠진 모델 (yml model-prices 에 추가 필요)
     * @param trackingSince  토큰 기록을 시작한 시각
     */
    public record AiCost(BigDecimal usd, long krw, BigDecimal usdKrw, long calls, long failedCalls,
                         long inputTokens, long outputTokens, long cacheWriteTokens, long cacheReadTokens,
                         List<AiFeatureRow> byFeature, List<AiModelRow> byModel,
                         List<String> unpricedModels, LocalDateTime trackingSince) {
    }

    public record AiFeatureRow(String feature, String label, long calls, long failedCalls,
                               long inputTokens, long outputTokens, BigDecimal usd, long krw,
                               BigDecimal avgUsdPerCall) {
    }

    public record AiModelRow(String model, long calls, long inputTokens, long outputTokens, BigDecimal usd, long krw) {
    }

    /** @param periodKrw 월 고정비를 기간 일수만큼 나눠 계산 (월 × 일수 / 30.44) */
    public record ServerCost(long monthlyKrw, long periodKrw, List<ServerCostItem> items) {
    }

    public record ServerCostItem(String name, long monthlyKrw, long periodKrw) {
    }

    /**
     * @param profitKrw              netRevenue − AI − 서버
     * @param aiCostPerActiveUserKrw 기간 중 활동한 사용자 1명당 AI 비용
     * @param costPerActiveUserKrw   기간 중 활동한 사용자 1명당 전체 비용
     */
    public record Profit(long netRevenueKrw, long totalCostKrw, long profitKrw,
                         Long aiCostPerActiveUserKrw, Long costPerActiveUserKrw) {
    }

    // ───────────── 사용자 ─────────────

    /**
     * @param activeInPeriod 기간 중 Task 를 1개 이상 완료한 사용자 수
     * @param withActiveGoal 진행 중인 목표가 있는 사용자 수
     */
    public record Users(long total, long pro, Integer proRatePercent,
                        long active7d, long active30d, long activeInPeriod, long withActiveGoal) {
    }

    // ───────────── 핵심 지표: 목표 ─────────────

    /**
     * @param ended               기간 중 끝난 목표 수 (goal_outcome 기준)
     * @param achievementRate     달성 / 끝난 목표 (%) ← 핵심 지표
     * @param successRate         (달성 + 부분 달성) / 끝난 목표 (%)
     * @param inProgress          지금 진행 중인 목표 수 (임시 · 기록형 목표 제외)
     * @param avgExecutionRate    진행 중 목표들의 평균 실천률 — 오늘까지 해야 했던 Task·루틴 중 완료 비율의 목표별 평균
     * @param onTrackRate         실천률 80% 이상(자동 판정에서 "달성" 기준)인 진행 중 목표 비율
     * @param executionHistogram  실천률 구간별 목표 수 (0–19, 20–39, 40–59, 60–79, 80–100)
     */
    public record Goals(int ended, int achieved, int partial, int notAchieved,
                        Integer achievementRate, Integer successRate,
                        int inProgress, Integer avgExecutionRate, Integer onTrackRate,
                        List<Integer> executionHistogram,
                        List<GoalGroupRow> byType, List<GoalGroupRow> bySource,
                        List<PathGroupRow> byPath, List<InProgressGroup> inProgressByPath) {
    }

    /**
     * 끝난 목표를 만든 방법 · AI 초안 경로별로 (V68, 2026-10-25).
     * key: AS_IS 그대로 확정 · MANUAL 직접 고쳐 확정 · QUICK_FIX 말로 고쳐 확정 · REGEN 다시 생성 뒤 확정 ·
     *      AI_LEGACY 경로 기록 없는 AI 목표(V68 이전) · MANUAL_GOAL 직접 만든 목표
     * @param avgKeptRate 초안 보존율 평균 (경로 기록이 있는 AI 목표만)
     * @param userPicked  사용자가 직접 결과를 고른 목표 수 (나머지는 자동 판정)
     */
    public record PathGroupRow(String key, String label, int ended, int achieved, int partial,
                               Integer achievementRate, Integer successRate,
                               Integer avgRoutineRate, Integer avgTaskRate, Integer avgKeptRate, int userPicked) {
    }

    /** 진행 중 목표의 실천률을 만든 방법 · AI 초안 경로별로 (key 는 PathGroupRow 와 같고, AI 는 AI 목표 전체) */
    public record InProgressGroup(String key, String label, int goals, Integer avgExecutionRate, Integer onTrackRate) {
    }

    /** @param key goalType(HEALTH …) 또는 AI · MANUAL */
    public record GoalGroupRow(String key, String label, int ended, int achieved, int partial,
                               Integer achievementRate, Integer avgRoutineRate, Integer avgTaskRate) {
    }

    // ───────────── Task · 루틴 실천률 ─────────────

    /**
     * 기간 안에서 "오늘까지 해야 했던" 것 중 완료한 비율.
     * @param due       해야 했던 개수 (삭제 제외)
     * @param skipped   건너뛴 개수 (루틴만 의미 있음, 완료로 치지 않는다)
     * @param users     해야 할 것이 있었던 사용자 수
     */
    public record Execution(long due, long completed, long skipped, Integer rate, long users, long activeItems) {
    }

    /**
     * Sub 핵심 지표 · Task 실천률을 "잘 실천하고 있는지" 쪼개 본다 (2026-10-25).
     * @param activeDay        실천률 B — 그날 앱을 쓴 사용자의 그날 Task · 루틴만 (이탈한 사람의 미완료 제외)
     * @param origins          Task · 루틴이 어디서 왔는지별 실천률 (AI 초안 그대로 · 고침 · 사용자가 더함 · 그 밖 AI 목표 · 직접 목표 · 목표 없음)
     * @param visitTrackingSince 화면 방문 기록 시작일 (그 전 날은 Task 완료 · 실제 한 일로만 "쓴 날"을 셈)
     */
    public record Practice(ActiveDay activeDay, List<OriginRow> origins, LocalDate visitTrackingSince) {
    }

    public record ActiveDay(long taskDue, long taskDone, Integer taskRate, long routineDue, long routineDone,
                            Integer routineRate) {
    }

    public record OriginRow(String key, String label, long routineDue, long routineDone, Integer routineRate,
                            long taskDue, long taskDone, Integer taskRate, long users) {
    }

    // ───────────── AI 목표 만들기 전환 ─────────────

    /**
     * @param sessions  AI 목표 대화를 시작한 횟수
     * @param drafts    계획 초안을 만든 횟수 (다시 만들기 포함)
     * @param confirmed 목표로 확정된 대화 수
     * @param conversionRate confirmed / sessions (%)
     */
    public record AiGoalFunnel(long sessions, long drafts, long confirmed, long abandoned, Integer conversionRate) {
    }

    // ───────────── AI 품질 (채택 · 수정 · 결과) ─────────────

    /**
     * AI 기능마다 같은 세 단계로 잰다.
     *  ① 채택 — AI 가 낸 결과를 받아들였나   ② 수정 — 받아들이기 전에 얼마나 고쳤나   ③ 결과 — 받아들인 뒤 실천됐나
     * ①이 높은데 ③이 낮으면 "받아들였지만 현실적이지 않은 계획" (가장 위험한 경우).
     */
    public record AiQuality(List<AiQualityRow> features, OutcomeCheck outcomeCheck) {
    }

    /**
     * @param key        GOAL_PLAN · TIMETABLE · GOAL_EDIT · SCHEDULE_EDIT · BRAIN_DUMP · PATTERN
     * @param adoptRate  ① 채택 (%) — adoptBase 가 분모
     * @param editRate   ② 수정 (%) — 높을수록 많이 고쳤다. 해당 없으면 null
     * @param resultRate ③ 결과 (%)
     * @param beforeRate ③과 비교할 기준 (적용 전 같은 기간 실천률 등). 없으면 null
     * @param extraLabel 기능별 보조 지표 이름 (엉뚱한 제안 비율 · 배치 실패율 등)
     */
    public record AiQualityRow(String key, String label,
                               String adoptLabel, Integer adoptRate, long adoptBase,
                               String editLabel, Integer editRate, long editBase,
                               String resultLabel, Integer resultRate, long resultBase, Integer beforeRate,
                               String extraLabel, Integer extraRate, long extraBase) {
    }

    /**
     * 자동 판정 신뢰도 — 기간 중 끝난 목표 기준.
     * @param autoJudged    기한이 지나 자동 판정돼 결과 확인 모달을 띄운 목표
     * @param responseRate  모달에 답한 비율 = answered / autoJudged
     * @param compared      직접 고른 결과와 자동 판정을 비교할 수 있는 목표 (모달 · 마무리 · 목표 상세)
     * @param matchRate     자동 판정이 직접 고른 결과와 같은 비율 ← 목표 달성률을 믿어도 되는지
     * @param metric80      수치 80~99% 라 자동 "달성" 처리된 목표 수
     */
    public record OutcomeCheck(long autoJudged, long answered, long dismissed, long pending, Integer responseRate,
                               long compared, long matched, Integer matchRate, long metric80,
                               List<OutcomeReasonRow> byReason) {
    }

    /** @param reason METRIC · ROUTINE_RATE · TASK_RATE · NO_RECORD */
    public record OutcomeReasonRow(String reason, String label, long compared, long matched, Integer matchRate) {
    }

    // ───────────── 그래프 ─────────────

    /** date: 그 날(또는 그 주 월요일). rate 는 분모 0 이면 null */
    public record DailyPoint(LocalDate date, long taskDue, long taskDone, Integer taskRate,
                             long routineDue, long routineDone, Integer routineRate,
                             BigDecimal aiUsd, long activeUsers) {
    }
}
