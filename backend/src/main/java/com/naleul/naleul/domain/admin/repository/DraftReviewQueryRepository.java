package com.naleul.naleul.domain.admin.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 운영 대시보드 "AI 초안 정확도" 집계 (V68 goal_draft_review · _item · _field · goal_draft).
 * 기간은 확정 시각(goal_draft_review.confirmed_at) 기준. 초안 화면 진입(퍼널 · 다시 생성률)은 첫 초안 시각 기준.
 * window(14 · 30)는 첫 행동부터 센 기간 — 컬럼 이름 앞자리(d14 · d30)로만 쓰고 값은 서비스가 14/30 으로 고정한다.
 */
@Repository
@RequiredArgsConstructor
public class DraftReviewQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * @param goalType      목표 카테고리 1단계 (HEALTH …), null 이면 전체
     * @param intensity     STEADY · CHALLENGE, null 이면 전체
     * @param planningStyle PLANNER · SPONTANEOUS, null 이면 전체
     * @param promptVersion 계획 프롬프트 버전, null 이면 전체
     * @param window        14 · 30
     */
    public record Filter(LocalDateTime fromAt, LocalDateTime toAt, String goalType, String intensity,
                         String planningStyle, String promptVersion, int window) {

        String w() {
            return window == 30 ? "d30" : "d14";
        }

        /** goal_draft_review r · goal_creation_session s 기준 조건 */
        String where() {
            StringBuilder sb = new StringBuilder(" AND r.confirmed_at >= :fromAt AND r.confirmed_at < :toAt");
            if (goalType != null) sb.append(" AND s.goal_type = :goalType");
            if (intensity != null) sb.append(" AND r.intensity = :intensity");
            if (planningStyle != null) sb.append(" AND r.planning_style = :planningStyle");
            if (promptVersion != null) sb.append(" AND r.prompt_version = :promptVersion");
            return sb.toString();
        }

        MapSqlParameterSource params() {
            return new MapSqlParameterSource("fromAt", Timestamp.valueOf(fromAt))
                    .addValue("toAt", Timestamp.valueOf(toAt))
                    .addValue("goalType", goalType)
                    .addValue("intensity", intensity)
                    .addValue("planningStyle", planningStyle)
                    .addValue("promptVersion", promptVersion);
        }
    }

    private static final String FROM_REVIEW = """
            FROM goal_draft_review r
            JOIN goal_creation_session s ON s.goal_creation_session_id = r.goal_creation_session_id
            """;

    // ───────────── 세션 ─────────────

    public record SessionStat(long sessions, long keptFields, long aiFields, long notUsed, long notUsedBase,
                              long baseEditCount) {
    }

    public SessionStat sessions(Filter f, LocalDateTime notUsedCut) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) sessions,
                       COALESCE(SUM(r.kept_fields), 0) kept, COALESCE(SUM(r.ai_fields), 0) ai,
                       COALESCE(SUM(r.confirmed_at <= :cut AND (r.first_action_at IS NULL
                           OR r.first_action_at > DATE_ADD(r.confirmed_at, INTERVAL 14 DAY))), 0) not_used,
                       COALESCE(SUM(r.confirmed_at <= :cut), 0) not_used_base,
                       COALESCE(SUM(r.base_edit_count), 0) base_edits
                """ + FROM_REVIEW + " WHERE 1 = 1" + f.where(),
                f.params().addValue("cut", Timestamp.valueOf(notUsedCut)),
                (rs, i) -> new SessionStat(rs.getLong("sessions"), rs.getLong("kept"), rs.getLong("ai"),
                        rs.getLong("not_used"), rs.getLong("not_used_base"), rs.getLong("base_edits")));
    }

    public record PathStat(String path, long sessions, long planned, long done) {
    }

    public List<PathStat> paths(Filter f) {
        String w = f.w();
        return jdbc.query("""
                SELECT r.path, COUNT(*) sessions,
                       COALESCE(SUM(r.%1$s_routine_planned), 0) planned, COALESCE(SUM(r.%1$s_routine_done), 0) done
                """.formatted(w) + FROM_REVIEW + " WHERE 1 = 1" + f.where() + " GROUP BY r.path",
                f.params(), (rs, i) -> new PathStat(rs.getString("path"), rs.getLong("sessions"),
                        rs.getLong("planned"), rs.getLong("done")));
    }

    public record Retention(String key, long evaluated, long kept) {
    }

    /** 확정 뒤 유지 (루틴 · 일회성 Task) — groupBy: r.path 또는 i.item_kind */
    public List<Retention> retention(Filter f, String groupBy) {
        String w = f.w();
        return jdbc.query("""
                SELECT %2$s k, COALESCE(SUM(i.%1$s_result IS NOT NULL), 0) evaluated,
                       COALESCE(SUM(i.%1$s_result = 'KEPT'), 0) kept
                FROM goal_draft_review_item i
                JOIN goal_draft_review r ON r.goal_draft_review_id = i.goal_draft_review_id
                JOIN goal_creation_session s ON s.goal_creation_session_id = r.goal_creation_session_id
                WHERE i.item_kind IN ('ROUTINE', 'ONE_TIME')
                """.formatted(w, groupBy) + f.where() + " GROUP BY " + groupBy,
                f.params(), (rs, i) -> new Retention(rs.getString("k"), rs.getLong("evaluated"), rs.getLong("kept")));
    }

    public record QuickFixStat(long sessions, long once, long twice, long threePlus) {
    }

    public QuickFixStat quickFix(Filter f) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(r.quick_fix_count > 0), 0) qf, COALESCE(SUM(r.quick_fix_count = 1), 0) once,
                       COALESCE(SUM(r.quick_fix_count = 2), 0) twice, COALESCE(SUM(r.quick_fix_count >= 3), 0) three
                """ + FROM_REVIEW + " WHERE 1 = 1" + f.where(),
                f.params(), (rs, i) -> new QuickFixStat(rs.getLong("qf"), rs.getLong("once"), rs.getLong("twice"),
                        rs.getLong("three")));
    }

    // ───────────── 초안 화면 진입 (첫 초안 시각 기준, 카테고리 필터만) ─────────────

    /**
     * @param qfFinished 말로 고치기를 쓰고 확정 · 그만둠으로 끝난 세션
     * @param qfSucceeded 그중 말로 고친 뒤 다시 생성 없이 확정
     * @param qfThenRegen 말로 고치다가 다시 생성으로 넘어간 세션
     */
    public record Entered(long entered, long confirmed, long exited, long pending, long regenSessions,
                          long qfFinished, long qfSucceeded, long qfThenRegen) {
    }

    public Entered entered(Filter f) {
        String type = f.goalType() != null ? " AND x.goal_type = :goalType" : "";
        return jdbc.queryForObject("""
                SELECT COUNT(*) entered,
                       COALESCE(SUM(x.status = 'CONFIRMED'), 0) confirmed,
                       COALESCE(SUM(x.status = 'ABANDONED'), 0) exited,
                       COALESCE(SUM(x.status NOT IN ('CONFIRMED', 'ABANDONED')), 0) pending,
                       COALESCE(SUM(x.regen > 0), 0) regen_sessions,
                       COALESCE(SUM(x.qf > 0 AND x.status IN ('CONFIRMED', 'ABANDONED')), 0) qf_finished,
                       COALESCE(SUM(x.qf > 0 AND x.status = 'CONFIRMED'
                           AND (x.last_regen IS NULL OR x.last_regen < x.first_qf)), 0) qf_succeeded,
                       COALESCE(SUM(x.qf > 0 AND x.last_regen > x.first_qf), 0) qf_then_regen
                FROM (SELECT d.goal_creation_session_id sid, s.status, s.goal_type,
                             MIN(d.created_at) first_at, COUNT(d.source) with_source,
                             SUM(d.source = 'REGEN') regen, SUM(d.source = 'QUICK_FIX') qf,
                             MAX(CASE WHEN d.source = 'REGEN' THEN d.version END) last_regen,
                             MIN(CASE WHEN d.source = 'QUICK_FIX' THEN d.version END) first_qf
                      FROM goal_draft d
                      JOIN goal_creation_session s ON s.goal_creation_session_id = d.goal_creation_session_id
                      GROUP BY d.goal_creation_session_id, s.status, s.goal_type
                      HAVING with_source > 0 AND first_at >= :fromAt AND first_at < :toAt) x
                WHERE 1 = 1
                """ + type, f.params(), (rs, i) -> new Entered(rs.getLong("entered"), rs.getLong("confirmed"),
                rs.getLong("exited"), rs.getLong("pending"), rs.getLong("regen_sessions"), rs.getLong("qf_finished"),
                rs.getLong("qf_succeeded"), rs.getLong("qf_then_regen")));
    }

    // ───────────── 수정 정도별 실천률 ─────────────

    public record BucketStat(String bucket, long sessions, long planned, long done, long ended, long achieved) {
    }

    public List<BucketStat> buckets(Filter f) {
        String w = f.w();
        return jdbc.query("""
                SELECT CASE WHEN r.ai_fields = 0 OR r.kept_fields * 100 >= r.ai_fields * 90 THEN 'NEAR'
                            WHEN r.kept_fields * 100 >= r.ai_fields * 70 THEN 'SOME' ELSE 'MANY' END b,
                       COUNT(*) sessions,
                       COALESCE(SUM(r.%1$s_routine_planned), 0) planned, COALESCE(SUM(r.%1$s_routine_done), 0) done,
                       COALESCE(SUM(o.goal_outcome_id IS NOT NULL), 0) ended,
                       COALESCE(SUM(o.result = 'ACHIEVED'), 0) achieved
                """.formatted(w) + FROM_REVIEW + """
                LEFT JOIN goal_outcome o ON o.goal_category_id = r.goal_category_id
                WHERE 1 = 1
                """ + f.where() + " GROUP BY b",
                f.params(), (rs, i) -> new BucketStat(rs.getString("b"), rs.getLong("sessions"), rs.getLong("planned"),
                        rs.getLong("done"), rs.getLong("ended"), rs.getLong("achieved")));
    }

    // ───────────── 항목 · 칸 ─────────────

    public record ItemStat(String kind, long aiItems, long edited, long deleted, long added, long kept,
                           long keptPlanned, long keptDone) {
    }

    public List<ItemStat> items(Filter f) {
        String w = f.w();
        return jdbc.query("""
                SELECT i.item_kind kind,
                       COALESCE(SUM(i.result <> 'ADDED'), 0) ai_items, COALESCE(SUM(i.result = 'EDITED'), 0) edited,
                       COALESCE(SUM(i.result = 'DELETED'), 0) deleted, COALESCE(SUM(i.result = 'ADDED'), 0) added,
                       COALESCE(SUM(i.result = 'KEPT'), 0) kept,
                       COALESCE(SUM(CASE WHEN i.result = 'KEPT' THEN i.%1$s_planned END), 0) kept_planned,
                       COALESCE(SUM(CASE WHEN i.result = 'KEPT' THEN i.%1$s_done END), 0) kept_done
                FROM goal_draft_review_item i
                JOIN goal_draft_review r ON r.goal_draft_review_id = i.goal_draft_review_id
                JOIN goal_creation_session s ON s.goal_creation_session_id = r.goal_creation_session_id
                WHERE 1 = 1
                """.formatted(w) + f.where() + " GROUP BY i.item_kind",
                f.params(), (rs, i) -> new ItemStat(rs.getString("kind"), rs.getLong("ai_items"), rs.getLong("edited"),
                        rs.getLong("deleted"), rs.getLong("added"), rs.getLong("kept"), rs.getLong("kept_planned"),
                        rs.getLong("kept_done")));
    }

    /** 칸별: AI 가 만든 칸(그대로 · 고침 · 삭제) 중 확정 전 고친 수, 확정 뒤 고친 수 (kind 가 null 이면 전체) */
    public record FieldStat(String kind, String field, long base, long edited, long afterBase, long afterEdited) {
    }

    public List<FieldStat> fields(Filter f, String kind) {
        String w = f.w();
        return jdbc.query("""
                SELECT fd.item_kind kind, fd.field,
                       COALESCE(SUM(fd.result IN ('KEPT', 'EDITED', 'DELETED')), 0) base,
                       COALESCE(SUM(fd.result = 'EDITED'), 0) edited,
                       COALESCE(SUM(fd.%1$s_result IS NOT NULL), 0) after_base,
                       COALESCE(SUM(fd.%1$s_result = 'EDITED'), 0) after_edited
                FROM goal_draft_review_field fd
                JOIN goal_draft_review r ON r.goal_draft_review_id = fd.goal_draft_review_id
                JOIN goal_creation_session s ON s.goal_creation_session_id = r.goal_creation_session_id
                WHERE fd.stage = 'CONFIRM' AND fd.field <> 'ITEM'
                """.formatted(w) + (kind != null ? " AND fd.item_kind = :kind" : "") + f.where()
                        + " GROUP BY fd.item_kind, fd.field",
                f.params().addValue("kind", kind), (rs, i) -> new FieldStat(rs.getString("kind"), rs.getString("field"),
                        rs.getLong("base"), rs.getLong("edited"), rs.getLong("after_base"), rs.getLong("after_edited")));
    }

    public record DirectionStat(String kind, String field, String direction, long count, BigDecimal avgDelta,
                                long planned, long done) {
    }

    public List<DirectionStat> directions(Filter f) {
        String w = f.w();
        return jdbc.query("""
                SELECT fd.item_kind kind, fd.field, fd.direction, COUNT(*) n, AVG(fd.delta) avg_delta,
                       COALESCE(SUM(i.%1$s_planned), 0) planned, COALESCE(SUM(i.%1$s_done), 0) done
                FROM goal_draft_review_field fd
                JOIN goal_draft_review_item i ON i.goal_draft_review_item_id = fd.goal_draft_review_item_id
                JOIN goal_draft_review r ON r.goal_draft_review_id = fd.goal_draft_review_id
                JOIN goal_creation_session s ON s.goal_creation_session_id = r.goal_creation_session_id
                WHERE fd.stage = 'CONFIRM' AND fd.result = 'EDITED' AND fd.direction IS NOT NULL
                """.formatted(w) + f.where() + " GROUP BY fd.item_kind, fd.field, fd.direction",
                f.params(), (rs, i) -> new DirectionStat(rs.getString("kind"), rs.getString("field"),
                        rs.getString("direction"), rs.getLong("n"), rs.getBigDecimal("avg_delta"),
                        rs.getLong("planned"), rs.getLong("done")));
    }

    public record WeeklyStat(String intensity, long sessions, long draftMinutes, long confirmedMinutes) {
    }

    public List<WeeklyStat> weekly(Filter f) {
        return jdbc.query("""
                SELECT COALESCE(r.intensity, 'STEADY') it, COUNT(*) n,
                       COALESCE(SUM(r.draft_weekly_minutes), 0) dw, COALESCE(SUM(r.confirmed_weekly_minutes), 0) cw
                """ + FROM_REVIEW + " WHERE r.draft_weekly_minutes > 0" + f.where() + " GROUP BY it",
                f.params(), (rs, i) -> new WeeklyStat(rs.getString("it"), rs.getLong("n"), rs.getLong("dw"), rs.getLong("cw")));
    }

    public List<String> promptVersions() {
        return jdbc.queryForList("""
                SELECT DISTINCT prompt_version FROM goal_draft_review
                WHERE prompt_version IS NOT NULL ORDER BY prompt_version DESC LIMIT 20
                """, new MapSqlParameterSource(), String.class);
    }

    // ───────────── 항목 상세 사례 ─────────────

    public record CaseRow(LocalDateTime at, String stage, String result, String label, String field, String path,
                          String request, String aiValue, String finalValue, String direction, Integer delta) {
    }

    /**
     * @param results BEFORE: EDITED · DELETED · ADDED 중, AFTER = 확정 뒤 고침 · 삭제
     *                (삭제된 항목은 칸마다 줄이 있어서 이름 칸 한 줄만 보여 준다)
     */
    private String caseWhere(Filter f, List<String> results, boolean after) {
        String w = f.w();
        StringBuilder sb = new StringBuilder(" WHERE fd.stage = 'CONFIRM' AND fd.item_kind = :kind").append(f.where()).append(" AND (1 = 0");
        if (!results.isEmpty()) {
            sb.append(" OR (fd.result IN (:results) AND (fd.result <> 'DELETED' OR fd.field = 'TITLE'))");
        }
        if (after) {
            sb.append(" OR (fd.%1$s_result = 'EDITED' OR (fd.%1$s_result = 'DELETED' AND fd.field = 'TITLE'))".formatted(w));
        }
        return sb.append(")").toString();
    }

    private static final String CASE_FROM = """
            FROM goal_draft_review_field fd
            JOIN goal_draft_review r ON r.goal_draft_review_id = fd.goal_draft_review_id
            JOIN goal_creation_session s ON s.goal_creation_session_id = r.goal_creation_session_id
            LEFT JOIN goal_draft_review_item i ON i.goal_draft_review_item_id = fd.goal_draft_review_item_id
            """;

    public List<CaseRow> cases(Filter f, String kind, List<String> results, boolean after, int limit, int offset) {
        String w = f.w();
        MapSqlParameterSource p = f.params().addValue("kind", kind).addValue("results", results.isEmpty() ? List.of("-") : results)
                .addValue("limit", limit).addValue("offset", offset);
        return jdbc.query("""
                SELECT r.confirmed_at at, fd.result, fd.%1$s_result after_result, i.label, fd.field, r.path,
                       (SELECT GROUP_CONCAT(d.user_feedback ORDER BY d.version SEPARATOR ' → ')
                        FROM goal_draft d
                        WHERE d.goal_creation_session_id = r.goal_creation_session_id
                          AND d.source IN ('QUICK_FIX', 'REGEN') AND d.user_feedback IS NOT NULL) req,
                       fd.ai_value, fd.final_value, fd.direction, fd.delta
                """.formatted(w) + CASE_FROM + caseWhere(f, results, after)
                        + " ORDER BY r.confirmed_at DESC, fd.goal_draft_review_field_id LIMIT :limit OFFSET :offset",
                p, (rs, i) -> {
                    String before = rs.getString("result");
                    String afterResult = rs.getString("after_result");
                    boolean isBefore = results.contains(before) && !"KEPT".equals(before);
                    String stage = isBefore ? "BEFORE_CONFIRM" : "AFTER_CONFIRM";
                    Timestamp at = rs.getTimestamp("at");
                    Object dv = rs.getObject("delta");
                    Integer delta = dv == null ? null : ((Number) dv).intValue();
                    return new CaseRow(at != null ? at.toLocalDateTime() : null, stage, isBefore ? before : afterResult,
                            rs.getString("label"), rs.getString("field"), rs.getString("path"), rs.getString("req"),
                            rs.getString("ai_value"), rs.getString("final_value"), rs.getString("direction"), delta);
                });
    }

    public long caseCount(Filter f, String kind, List<String> results, boolean after) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) " + CASE_FROM + caseWhere(f, results, after),
                f.params().addValue("kind", kind).addValue("results", results.isEmpty() ? List.of("-") : results), Long.class);
        return n == null ? 0 : n;
    }
}
