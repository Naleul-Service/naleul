package com.naleul.naleul.domain.admin.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 운영 대시보드 집계 쿼리 (전체 사용자 대상, 읽기 전용).
 *
 * 왜 JPA(엔티티) 대신 SQL 인가:
 *   전체 사용자의 Task 를 엔티티로 다 불러오면 수십만 개 객체를 메모리에 올리게 된다.
 *   여기서는 개수 · 합계만 필요하니 DB 에서 GROUP BY 로 세어 숫자만 받는 것이 훨씬 가볍다.
 *
 * 시간 기준
 *   - DATE 컬럼(scheduled_date, routine_date …)은 한국 날짜 그대로 비교한다.
 *   - DATETIME 컬럼(created_at, completed_at …)은 서버 시간대로 저장돼 있어서,
 *     서비스가 "한국 0시"를 서버 시간대로 바꿔 넘긴다 (fromAt / toAt).
 *     날짜별로 묶을 때는 offsetMinutes(한국 − 서버)만큼 더해서 한국 날짜로 바꾼다.
 */
@Repository
@RequiredArgsConstructor
public class AdminDashboardQueryRepository {

    /** Task 의 "하기로 한 날" — GoalOutcomeRecorder.plannedDate 와 같은 우선순위 (루틴은 routine_date) */
    private static final String PLANNED_DATE =
            "COALESCE(t.routine_date, t.scheduled_date, DATE(t.planned_start_at), t.due_date)";

    /** 달성률 집계에서 빼는 목표: 삭제 · Brain dump 임시 목표 · 기록형(끝이 없는) 목표 */
    private static final String REAL_GOAL =
            "g.goal_category_status <> 'DELETED' AND g.is_temporary = FALSE AND g.goal_mode = 'ACHIEVEMENT'";

    /**
     * 목표를 만든 방법 · AI 초안 경로 (V68 goal_draft_review.path, 별칭 r).
     * MANUAL_GOAL 직접 만든 목표 · AI_LEGACY 경로 기록이 없는 AI 목표(V68 이전) · AS_IS · MANUAL · QUICK_FIX · REGEN.
     * %s = "AI 로 만든 목표인가" 컬럼.
     */
    private static final String PATH_KEY =
            "(CASE WHEN %s = FALSE THEN 'MANUAL_GOAL' WHEN r.path IS NULL THEN 'AI_LEGACY' ELSE r.path END)";

    private final NamedParameterJdbcTemplate jdbc;

    // ───────────── AI ─────────────

    public record AiUsageRow(String feature, String model, long calls, long failed,
                             long input, long output, long cacheWrite, long cacheRead) {
    }

    public List<AiUsageRow> aiUsageByFeatureAndModel(LocalDateTime fromAt, LocalDateTime toAt) {
        return jdbc.query("""
                SELECT feature, model, COUNT(*) calls, SUM(success = FALSE) failed,
                       SUM(input_tokens) input, SUM(output_tokens) output,
                       SUM(cache_creation_input_tokens) cw, SUM(cache_read_input_tokens) cr
                FROM ai_call_log
                WHERE created_at >= :fromAt AND created_at < :toAt
                GROUP BY feature, model
                """, range(fromAt, toAt),
                (rs, i) -> new AiUsageRow(rs.getString("feature"), rs.getString("model"),
                        rs.getLong("calls"), rs.getLong("failed"), rs.getLong("input"), rs.getLong("output"),
                        rs.getLong("cw"), rs.getLong("cr")));
    }

    public record AiDailyRow(LocalDate date, String model, long input, long output, long cacheWrite, long cacheRead) {
    }

    public List<AiDailyRow> aiUsageByDay(LocalDateTime fromAt, LocalDateTime toAt, int offsetMinutes) {
        return jdbc.query("""
                SELECT DATE(DATE_ADD(created_at, INTERVAL :offset MINUTE)) d, model,
                       SUM(input_tokens) input, SUM(output_tokens) output,
                       SUM(cache_creation_input_tokens) cw, SUM(cache_read_input_tokens) cr
                FROM ai_call_log
                WHERE created_at >= :fromAt AND created_at < :toAt AND success = TRUE
                GROUP BY d, model
                """, range(fromAt, toAt).addValue("offset", offsetMinutes),
                (rs, i) -> new AiDailyRow(rs.getDate("d").toLocalDate(), rs.getString("model"),
                        rs.getLong("input"), rs.getLong("output"), rs.getLong("cw"), rs.getLong("cr")));
    }

    public LocalDateTime aiTrackingSince() {
        return minDateTime("SELECT MIN(created_at) FROM ai_call_log");
    }

    // ───────────── 매출 ─────────────

    public record RevenueRow(String eventType, String productId, String currency, long count, BigDecimal amount) {
    }

    public List<RevenueRow> revenue(LocalDateTime fromAt, LocalDateTime toAt, boolean includeSandbox) {
        return jdbc.query("""
                SELECT event_type, product_id, currency, COUNT(*) cnt, COALESCE(SUM(price_amount), 0) amount
                FROM iap_revenue_event
                WHERE event_at >= :fromAt AND event_at < :toAt
                  AND (:sandbox = TRUE OR LOWER(environment) = 'production')
                GROUP BY event_type, product_id, currency
                """, range(fromAt, toAt).addValue("sandbox", includeSandbox),
                (rs, i) -> new RevenueRow(rs.getString("event_type"), rs.getString("product_id"),
                        rs.getString("currency"), rs.getLong("cnt"), rs.getBigDecimal("amount")));
    }

    public record LatestPriceRow(String productId, String currency, BigDecimal amount) {
    }

    /** 상품별 가장 최근 유료 결제 금액 — MRR 추정에 쓰는 "현재 가격" */
    public List<LatestPriceRow> latestPrices(boolean includeSandbox) {
        return jdbc.query("""
                SELECT e.product_id, e.currency, e.price_amount
                FROM iap_revenue_event e
                JOIN (SELECT product_id, MAX(iap_revenue_event_id) id
                      FROM iap_revenue_event
                      WHERE event_type IN ('PURCHASE', 'RENEWAL') AND price_amount > 0
                        AND (:sandbox = TRUE OR LOWER(environment) = 'production')
                      GROUP BY product_id) last ON last.id = e.iap_revenue_event_id
                """, new MapSqlParameterSource("sandbox", includeSandbox),
                (rs, i) -> new LatestPriceRow(rs.getString("product_id"), rs.getString("currency"),
                        rs.getBigDecimal("price_amount")));
    }

    public LocalDateTime revenueTrackingSince() {
        return minDateTime("SELECT MIN(created_at) FROM iap_revenue_event");
    }

    public record ActiveSubscriptionRow(String productId, long count, Double avgPeriodDays) {
    }

    /** 지금 유효한 구독 (활성 · 결제 유예 기간) 과 평균 구독 주기(일) — 월 구독 ≈ 30, 연 구독 ≈ 365 */
    public List<ActiveSubscriptionRow> activeSubscriptions(LocalDateTime now, boolean includeSandbox) {
        return jdbc.query("""
                SELECT product_id, COUNT(*) cnt, AVG(DATEDIFF(expires_date, purchase_date)) period_days
                FROM purchase_transaction
                WHERE status IN ('ACTIVE', 'GRACE_PERIOD')
                  AND (expires_date IS NULL OR expires_date > :now)
                  AND (:sandbox = TRUE OR LOWER(environment) = 'production')
                GROUP BY product_id
                """, new MapSqlParameterSource("now", Timestamp.valueOf(now)).addValue("sandbox", includeSandbox),
                (rs, i) -> {
                    double d = rs.getDouble("period_days");
                    return new ActiveSubscriptionRow(rs.getString("product_id"), rs.getLong("cnt"),
                            rs.wasNull() ? null : d);
                });
    }

    // ───────────── 사용자 ─────────────

    public long totalUsers() {
        return count("SELECT COUNT(*) FROM users", new MapSqlParameterSource());
    }

    public long proUsers(LocalDateTime now) {
        return count("""
                SELECT COUNT(*) FROM users
                WHERE is_premium = TRUE AND (premium_expires_at IS NULL OR premium_expires_at > :now)
                """, new MapSqlParameterSource("now", Timestamp.valueOf(now)));
    }

    /** 이 시각 이후 Task(루틴 포함)를 1개 이상 완료한 사용자 수 — "활동 사용자"의 기준 */
    public long usersCompletedTaskSince(LocalDateTime fromAt, LocalDateTime toAt) {
        return count("""
                SELECT COUNT(DISTINCT user_id) FROM task
                WHERE task_status = 'COMPLETED' AND completed_at >= :fromAt AND completed_at < :toAt
                """, range(fromAt, toAt));
    }

    public long usersWithActiveGoal() {
        return count("SELECT COUNT(DISTINCT g.user_id) FROM goal_category g WHERE g.goal_category_status = 'IN_PROGRESS' AND "
                + REAL_GOAL, new MapSqlParameterSource());
    }

    public record DailyActiveRow(LocalDate date, long users) {
    }

    public List<DailyActiveRow> dailyActiveUsers(LocalDateTime fromAt, LocalDateTime toAt, int offsetMinutes) {
        return jdbc.query("""
                SELECT DATE(DATE_ADD(completed_at, INTERVAL :offset MINUTE)) d, COUNT(DISTINCT user_id) users
                FROM task
                WHERE task_status = 'COMPLETED' AND completed_at >= :fromAt AND completed_at < :toAt
                GROUP BY d
                """, range(fromAt, toAt).addValue("offset", offsetMinutes),
                (rs, i) -> new DailyActiveRow(rs.getDate("d").toLocalDate(), rs.getLong("users")));
    }

    // ───────────── 목표 ─────────────

    public record OutcomeRow(String goalType, boolean aiGenerated, String result, long count,
                             Double avgRoutineRate, Double avgTaskRate) {
    }

    /** 기간 중 끝난 목표의 결과 (goal_outcome.created_at = 결과가 정해진 시각) */
    public List<OutcomeRow> outcomes(LocalDateTime fromAt, LocalDateTime toAt) {
        return jdbc.query("""
                SELECT o.goal_type, o.ai_generated, o.result, COUNT(*) cnt,
                       AVG(o.routine_rate) avg_routine, AVG(o.task_rate) avg_task
                FROM goal_outcome o
                WHERE o.created_at >= :fromAt AND o.created_at < :toAt
                GROUP BY o.goal_type, o.ai_generated, o.result
                """, range(fromAt, toAt),
                (rs, i) -> {
                    double r = rs.getDouble("avg_routine");
                    Double routine = rs.wasNull() ? null : r;
                    double t = rs.getDouble("avg_task");
                    Double task = rs.wasNull() ? null : t;
                    return new OutcomeRow(rs.getString("goal_type"), rs.getBoolean("ai_generated"),
                            rs.getString("result"), rs.getLong("cnt"), routine, task);
                });
    }

    public long inProgressGoals() {
        return count("SELECT COUNT(*) FROM goal_category g WHERE g.goal_category_status = 'IN_PROGRESS' AND " + REAL_GOAL,
                new MapSqlParameterSource());
    }

    /** @param key 만든 방법 · AI 초안 경로 (PATH_KEY 와 같은 값) */
    public record GoalExecutionRow(long goalId, String key, long due, long done) {
    }

    /**
     * 진행 중 목표별 "오늘까지 해야 했던 Task·루틴" 중 완료한 개수 (목표 시작부터 누적).
     * 미리 끝낸 Task(하기로 한 날이 미래)도 완료로 센다 — GoalOutcomeCalculator 와 같은 기준.
     */
    public List<GoalExecutionRow> inProgressGoalExecution(LocalDate today) {
        return jdbc.query("""
                SELECT g.goal_category_id gid, %3$s k,
                       COUNT(*) due,
                       SUM(t.task_status = 'COMPLETED') done
                FROM task t
                JOIN goal_category g ON g.goal_category_id = t.goal_category_id
                LEFT JOIN goal_draft_review r ON r.goal_category_id = g.goal_category_id
                WHERE g.goal_category_status = 'IN_PROGRESS' AND %1$s
                  AND t.task_status <> 'DELETED'
                  AND (t.task_status = 'COMPLETED' OR %2$s <= :today)
                GROUP BY g.goal_category_id, k
                """.formatted(REAL_GOAL, PLANNED_DATE, PATH_KEY.formatted("g.is_ai_generated")),
                new MapSqlParameterSource("today", today),
                (rs, i) -> new GoalExecutionRow(rs.getLong("gid"), rs.getString("k"), rs.getLong("due"), rs.getLong("done")));
    }

    // ───────────── Task · 루틴 ─────────────

    public record ExecutionDayRow(LocalDate date, long due, long done, long skipped) {
    }

    /**
     * 하기로 한 날이 [from, to] 인 Task 를 날짜별로 센다 (삭제 제외).
     * @param routine true: 루틴이 만든 Task / false: 일반 Task
     */
    public List<ExecutionDayRow> executionByDay(LocalDate from, LocalDate to, boolean routine) {
        return jdbc.query("""
                SELECT %1$s d, COUNT(*) due,
                       SUM(t.task_status = 'COMPLETED') done,
                       SUM(t.task_status = 'SKIPPED') skipped
                FROM task t
                WHERE t.routine_id IS %2$s NULL
                  AND t.task_status <> 'DELETED'
                  AND %1$s BETWEEN :from AND :to
                GROUP BY d
                """.formatted(PLANNED_DATE, routine ? "NOT" : ""),
                new MapSqlParameterSource("from", from).addValue("to", to),
                (rs, i) -> new ExecutionDayRow(rs.getDate("d").toLocalDate(),
                        rs.getLong("due"), rs.getLong("done"), rs.getLong("skipped")));
    }

    public long executionUsers(LocalDate from, LocalDate to, boolean routine) {
        return count("""
                SELECT COUNT(DISTINCT t.user_id) FROM task t
                WHERE t.routine_id IS %1$s NULL AND t.task_status <> 'DELETED'
                  AND %2$s BETWEEN :from AND :to
                """.formatted(routine ? "NOT" : "", PLANNED_DATE),
                new MapSqlParameterSource("from", from).addValue("to", to));
    }

    public long activeRoutines() {
        return count("SELECT COUNT(*) FROM routine WHERE routine_status = 'IN_PROGRESS'", new MapSqlParameterSource());
    }

    public long openTasks(LocalDate today) {
        return count("""
                SELECT COUNT(*) FROM task t
                WHERE t.routine_id IS NULL AND t.task_status = 'TODO' AND %s >= :today
                """.formatted(PLANNED_DATE), new MapSqlParameterSource("today", today));
    }

    // ───────────── AI 목표 만들기 ─────────────

    public record FunnelRow(long sessions, long confirmed, long abandoned) {
    }

    public FunnelRow goalSessions(LocalDateTime fromAt, LocalDateTime toAt) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) sessions,
                       COALESCE(SUM(status = 'CONFIRMED'), 0) confirmed,
                       COALESCE(SUM(status = 'ABANDONED'), 0) abandoned
                FROM goal_creation_session
                WHERE created_at >= :fromAt AND created_at < :toAt
                """, range(fromAt, toAt),
                (rs, i) -> new FunnelRow(rs.getLong("sessions"), rs.getLong("confirmed"), rs.getLong("abandoned")));
    }

    public long goalDrafts(LocalDateTime fromAt, LocalDateTime toAt) {
        return count("SELECT COUNT(*) FROM goal_draft WHERE created_at >= :fromAt AND created_at < :toAt",
                range(fromAt, toAt));
    }

    // ───────────── 핵심 지표 × AI 정확도 (2026-10-25) ─────────────

    /**
     * 기간 중 끝난 목표를 만든 방법 · AI 초안 경로별로 (V68).
     * @param keptRate 초안 보존율 평균 % (경로 기록이 있는 AI 목표만)
     */
    public record PathOutcomeRow(String key, String result, String source, long count,
                                 Double avgRoutineRate, Double avgTaskRate, Double keptRate) {
    }

    public List<PathOutcomeRow> outcomesByPath(LocalDateTime fromAt, LocalDateTime toAt) {
        return jdbc.query("""
                SELECT %s k, o.result, o.source, COUNT(*) cnt,
                       AVG(o.routine_rate) avg_routine, AVG(o.task_rate) avg_task,
                       AVG(CASE WHEN r.ai_fields > 0 THEN 100.0 * r.kept_fields / r.ai_fields END) kept
                FROM goal_outcome o
                LEFT JOIN goal_draft_review r ON r.goal_category_id = o.goal_category_id
                WHERE o.created_at >= :fromAt AND o.created_at < :toAt
                GROUP BY k, o.result, o.source
                """.formatted(PATH_KEY.formatted("o.ai_generated")), range(fromAt, toAt),
                (rs, i) -> new PathOutcomeRow(rs.getString("k"), rs.getString("result"), rs.getString("source"),
                        rs.getLong("cnt"), dbl(rs, "avg_routine"), dbl(rs, "avg_task"), dbl(rs, "kept")));
    }

    /**
     * 하기로 한 날이 [from, to] 인 Task · 루틴 회차를 "어디서 온 것인가"로 나눈다 (삭제 제외).
     * AI_KEPT · AI_EDITED · AI_ADDED: AI 초안 화면에서 그대로 둔 것 · 고친 것 · 사용자가 더한 것 (V68 항목 결과)
     * AI_OTHER: AI 목표지만 초안 항목이 아님 (확정 뒤 추가 · V68 이전) · MANUAL: 직접 만든 목표 · NO_GOAL: 목표 없음 · 임시 목표
     */
    public record OriginRow(String origin, boolean routine, long due, long done, long users) {
    }

    public List<OriginRow> executionByOrigin(LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT CASE WHEN ri.result IS NOT NULL THEN CONCAT('AI_', ri.result)
                            WHEN ti.result IS NOT NULL THEN CONCAT('AI_', ti.result)
                            WHEN g.goal_category_id IS NULL OR g.is_temporary = TRUE THEN 'NO_GOAL'
                            WHEN g.is_ai_generated = TRUE THEN 'AI_OTHER'
                            ELSE 'MANUAL' END origin,
                       (t.routine_id IS NOT NULL) is_routine,
                       COUNT(*) due, COALESCE(SUM(t.task_status = 'COMPLETED'), 0) done,
                       COUNT(DISTINCT t.user_id) users
                FROM task t
                LEFT JOIN goal_category g ON g.goal_category_id = t.goal_category_id
                LEFT JOIN goal_draft_review_item ri ON ri.item_kind = 'ROUTINE' AND ri.entity_id = t.routine_id
                LEFT JOIN goal_draft_review_item ti ON ti.item_kind = 'ONE_TIME' AND ti.entity_id = t.task_id
                WHERE t.task_status <> 'DELETED' AND %1$s BETWEEN :from AND :to
                GROUP BY origin, is_routine
                """.formatted(PLANNED_DATE), new MapSqlParameterSource("from", from).addValue("to", to),
                (rs, i) -> new OriginRow(rs.getString("origin"), rs.getBoolean("is_routine"), rs.getLong("due"),
                        rs.getLong("done"), rs.getLong("users")));
    }

    public record ActiveDayRow(boolean routine, long due, long done) {
    }

    /**
     * 실천률 B — 그날 앱을 쓴 사용자의, 그날 해야 했던 Task · 루틴 회차만 센다 (이탈한 사람의 미완료를 뺀 "제안 품질").
     * 쓴 날 = 오늘 화면 방문(user_visit_day, V69) ∪ Task 를 완료한 날 ∪ 실제 한 일을 추가한 날.
     * task.completed_at 은 한국 시간, actual_activity.created_at 은 서버 시간(:off 로 한국 날짜).
     */
    public List<ActiveDayRow> executionOnActiveDays(LocalDate from, LocalDate to, LocalDateTime fromAt,
                                                    LocalDateTime toAt, int off) {
        return jdbc.query("""
                SELECT (t.routine_id IS NOT NULL) is_routine, COUNT(*) due,
                       COALESCE(SUM(t.task_status = 'COMPLETED'), 0) done
                FROM task t
                JOIN (SELECT v.user_id uid, v.visit_date d FROM user_visit_day v
                      WHERE v.visit_date BETWEEN :from AND :to
                      UNION
                      SELECT c.user_id, DATE(c.completed_at) FROM task c
                      WHERE c.task_status = 'COMPLETED' AND c.completed_at >= :kstFrom AND c.completed_at < :kstTo
                      UNION
                      SELECT x.user_id, DATE(DATE_ADD(x.created_at, INTERVAL :off MINUTE)) FROM actual_activity x
                      WHERE x.created_at >= :fromAt AND x.created_at < :toAt) ad
                  ON ad.uid = t.user_id AND ad.d = %1$s
                WHERE t.task_status <> 'DELETED' AND %1$s BETWEEN :from AND :to
                GROUP BY is_routine
                """.formatted(PLANNED_DATE),
                range(fromAt, toAt).addValue("from", from).addValue("to", to).addValue("off", off)
                        .addValue("kstFrom", Timestamp.valueOf(from.atStartOfDay()))
                        .addValue("kstTo", Timestamp.valueOf(to.plusDays(1).atStartOfDay())),
                (rs, i) -> new ActiveDayRow(rs.getBoolean("is_routine"), rs.getLong("due"), rs.getLong("done")));
    }

    /** 화면 방문 기록을 시작한 날 (V69) */
    public LocalDate visitTrackingSince() {
        java.sql.Date d = jdbc.queryForObject("SELECT MIN(visit_date) FROM user_visit_day", new MapSqlParameterSource(),
                java.sql.Date.class);
        return d == null ? null : d.toLocalDate();
    }

    private static Double dbl(java.sql.ResultSet rs, String col) throws java.sql.SQLException {
        double v = rs.getDouble(col);
        return rs.wasNull() ? null : v;
    }

    // ───────────── 공통 ─────────────

    private static MapSqlParameterSource range(LocalDateTime fromAt, LocalDateTime toAt) {
        return new MapSqlParameterSource("fromAt", Timestamp.valueOf(fromAt)).addValue("toAt", Timestamp.valueOf(toAt));
    }

    private long count(String sql, MapSqlParameterSource params) {
        Long v = jdbc.queryForObject(sql, params, Long.class);
        return v == null ? 0 : v;
    }

    private LocalDateTime minDateTime(String sql) {
        Timestamp ts = jdbc.queryForObject(sql, new MapSqlParameterSource(), Timestamp.class);
        return ts == null ? null : ts.toLocalDateTime();
    }
}
