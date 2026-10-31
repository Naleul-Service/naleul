package com.naleul.naleul.domain.admin.repository;

import com.naleul.naleul.domain.aiUsage.limit.UserPlanSql;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 운영 대시보드 "AI 관심도" 집계 (V70). 기능별 시도 · 완료 · 중단을 Free / Pro 로 나눈다.
 *
 * 플랜: 행에 남긴 시도 당시 플랜(user_plan, V70) → 없으면(예전 행) 지금 플랜.
 * 시간: goal_creation_session · ai_suggestion_log · ai_limit_hit · actual_activity 는 서버 시간(fromAt/toAt),
 *       pattern_suggestion_action · task.completed_at 은 한국 시간(kstFrom/kstTo), user_visit_day 는 한국 날짜.
 * 운영진(ADMIN) 계정은 뺀다.
 */
@Repository
@RequiredArgsConstructor
public class AiInterestQueryRepository {

    private static final String NOW_PLAN = UserPlanSql.planOf("u");
    private static final String NOT_ADMIN = "u.user_role <> 'ADMIN'";

    private final NamedParameterJdbcTemplate jdbc;

    public record Range(LocalDateTime fromAt, LocalDateTime toAt, LocalDateTime kstFrom, LocalDateTime kstTo,
                        LocalDate kstFromDay, LocalDate kstToDay) {
        MapSqlParameterSource params() {
            return new MapSqlParameterSource("fromAt", Timestamp.valueOf(fromAt)).addValue("toAt", Timestamp.valueOf(toAt))
                    .addValue("kstFrom", Timestamp.valueOf(kstFrom)).addValue("kstTo", Timestamp.valueOf(kstTo))
                    .addValue("fromDay", Date.valueOf(kstFromDay)).addValue("toDay", Date.valueOf(kstToDay));
        }
    }

    // ───────────── AI 목표 만들기 ─────────────

    /**
     * @param reviewEntered 정리 화면까지 왔는지 (V67 이후 세션만 기록)
     * @param timedOut      예전 행에서 7일 방치로 정리된 것으로 보이는지 (abandon_reason 이 없을 때만 씀)
     */
    public record SessionRow(long userId, String plan, String status, String abandonReason, boolean reviewEntered,
                             boolean timedOut, int drafts, int readyDrafts) {
    }

    public List<SessionRow> goalSessions(Range r) {
        return jdbc.query("""
                SELECT s.user_id, COALESCE(s.user_plan, %s) plan, s.status, s.abandon_reason,
                       (s.review_entered_at IS NOT NULL OR s.reviewed_at IS NOT NULL) review_entered,
                       (s.updated_at >= s.expires_at) timed_out,
                       (SELECT COUNT(*) FROM goal_draft d WHERE d.goal_creation_session_id = s.goal_creation_session_id) drafts,
                       (SELECT COUNT(*) FROM goal_draft d WHERE d.goal_creation_session_id = s.goal_creation_session_id
                          AND d.status = 'READY') ready_drafts
                FROM goal_creation_session s JOIN users u ON u.user_id = s.user_id
                WHERE s.created_at >= :fromAt AND s.created_at < :toAt AND %s
                """.formatted(NOW_PLAN, NOT_ADMIN), r.params(),
                (rs, i) -> new SessionRow(rs.getLong("user_id"), rs.getString("plan"), rs.getString("status"),
                        rs.getString("abandon_reason"), rs.getBoolean("review_entered"), rs.getBoolean("timed_out"),
                        rs.getInt("drafts"), rs.getInt("ready_drafts")));
    }

    // ───────────── 말로 고치기 · AI 일정 조정 · 브레인 덤프 ─────────────

    public record SuggestionRow(long userId, String plan, String feature, String status, boolean aiUsed, int validCount,
                                LocalDateTime createdAt) {
    }

    public List<SuggestionRow> suggestions(Range r) {
        return jdbc.query("""
                SELECT l.user_id, COALESCE(l.user_plan, %s) plan, l.feature, l.status, l.ai_used, l.valid_count, l.created_at
                FROM ai_suggestion_log l JOIN users u ON u.user_id = l.user_id
                WHERE l.created_at >= :fromAt AND l.created_at < :toAt AND %s
                """.formatted(NOW_PLAN, NOT_ADMIN), r.params(),
                (rs, i) -> new SuggestionRow(rs.getLong("user_id"), rs.getString("plan"), rs.getString("feature"),
                        rs.getString("status"), rs.getBoolean("ai_used"), rs.getInt("valid_count"),
                        ldt(rs.getObject("created_at"))));
    }

    // ───────────── 한도에 막힘 ─────────────

    public record LimitRow(long userId, String plan, String feature, String reason) {
    }

    public List<LimitRow> limitHits(Range r) {
        return jdbc.query("""
                SELECT h.user_id, COALESCE(h.user_plan, %s) plan, h.feature, h.reason
                FROM ai_limit_hit h JOIN users u ON u.user_id = h.user_id
                WHERE h.created_at >= :fromAt AND h.created_at < :toAt AND %s
                """.formatted(NOW_PLAN, NOT_ADMIN), r.params(),
                (rs, i) -> new LimitRow(rs.getLong("user_id"), rs.getString("plan"), rs.getString("feature"),
                        rs.getString("reason")));
    }

    /** 한도 기록을 시작한 시각 (V70 배포) — null 이면 아직 한 건도 없음 */
    public LocalDateTime limitTrackingSince() {
        List<Object> r = jdbc.queryForList("SELECT MIN(created_at) FROM ai_limit_hit", new MapSqlParameterSource(), Object.class);
        return r.isEmpty() ? null : ldt(r.get(0));
    }

    // ───────────── 나의 패턴 제안 (루틴 시간 · 요일) ─────────────

    public record PatternRow(long userId, String plan, String action) {
    }

    /** 플랜은 지금 플랜 (이 테이블엔 당시 플랜이 없다) */
    public List<PatternRow> patternActions(Range r) {
        return jdbc.query("""
                SELECT p.user_id, %s plan, p.action
                FROM pattern_suggestion_action p JOIN users u ON u.user_id = p.user_id
                WHERE p.created_at >= :kstFrom AND p.created_at < :kstTo
                  AND p.suggestion_type IN ('ROUTINE_TIME', 'ROUTINE_DAY') AND %s
                """.formatted(NOW_PLAN, NOT_ADMIN), r.params(),
                (rs, i) -> new PatternRow(rs.getLong("user_id"), rs.getString("plan"), rs.getString("action")));
    }

    // ───────────── 분모: 기간 안에 활동한 사용자 ─────────────

    /**
     * 기간 안에 한 번이라도 활동한 사용자 → 지금 플랜.
     * 활동 = 오늘 화면 방문(user_visit_day) ∪ Task 완료 ∪ 실제 한 일 추가 ∪ AI 기능 시도.
     */
    public Map<Long, String> activeUsers(Range r) {
        Map<Long, String> m = new HashMap<>();
        jdbc.query("""
                SELECT u.user_id, %s plan FROM users u
                WHERE %s AND u.user_id IN (
                    SELECT v.user_id FROM user_visit_day v WHERE v.visit_date BETWEEN :fromDay AND :toDay
                    UNION SELECT t.user_id FROM task t
                          WHERE t.task_status = 'COMPLETED' AND t.completed_at >= :kstFrom AND t.completed_at < :kstTo
                    UNION SELECT a.user_id FROM actual_activity a WHERE a.created_at >= :fromAt AND a.created_at < :toAt
                    UNION SELECT s.user_id FROM goal_creation_session s WHERE s.created_at >= :fromAt AND s.created_at < :toAt
                    UNION SELECT l.user_id FROM ai_suggestion_log l WHERE l.created_at >= :fromAt AND l.created_at < :toAt
                    UNION SELECT h.user_id FROM ai_limit_hit h WHERE h.created_at >= :fromAt AND h.created_at < :toAt
                    UNION SELECT p.user_id FROM pattern_suggestion_action p
                          WHERE p.created_at >= :kstFrom AND p.created_at < :kstTo
                )
                """.formatted(NOW_PLAN, NOT_ADMIN), r.params(),
                (RowCallbackHandler) rs -> m.put(rs.getLong("user_id"), rs.getString("plan")));
        return m;
    }

    private static LocalDateTime ldt(Object o) {
        if (o == null) return null;
        if (o instanceof LocalDateTime l) return l;
        if (o instanceof Timestamp t) return t.toLocalDateTime();
        return null;
    }
}
