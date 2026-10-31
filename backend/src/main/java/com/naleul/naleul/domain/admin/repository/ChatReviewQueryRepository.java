package com.naleul.naleul.domain.admin.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 운영 대시보드 "AI 채팅 정리 정확도" 집계 (V67 goal_review_field · goal_creation_session · goal_creation_message).
 * 기간은 첫 "계획 만들기"를 누른 시각(goal_review_field.created_at) 기준.
 */
@Repository
@RequiredArgsConstructor
public class ChatReviewQueryRepository {

    /** 누락률을 재는 항목 (이유 · 실행 성향 제외) */
    static final String MISS_FIELDS = "('GOAL_STATEMENT','METRIC','DEADLINE','APPROACH','PRACTICE_PREFERENCE')";

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * @param goalType      목표 카테고리 1단계 (HEALTH …), null 이면 전체
     * @param promptVersion 인터뷰 프롬프트 버전, null 이면 전체
     * @param answerSource  CHIP · TYPED, null 이면 전체 (그 방식으로 답한 항목만)
     */
    public record Filter(LocalDateTime fromAt, LocalDateTime toAt, String goalType, String promptVersion,
                         String answerSource) {

        String where(String fieldAlias, String sessionAlias, boolean withSource) {
            StringBuilder sb = new StringBuilder();
            if (goalType != null) sb.append(" AND ").append(sessionAlias).append(".goal_type = :goalType");
            if (promptVersion != null) sb.append(" AND ").append(sessionAlias).append(".prompt_version = :promptVersion");
            if (withSource && answerSource != null && fieldAlias != null) {
                sb.append(" AND ").append(fieldAlias).append(".answer_source = :answerSource");
            }
            return sb.toString();
        }

        MapSqlParameterSource params() {
            return new MapSqlParameterSource("fromAt", Timestamp.valueOf(fromAt))
                    .addValue("toAt", Timestamp.valueOf(toAt))
                    .addValue("goalType", goalType)
                    .addValue("promptVersion", promptVersion)
                    .addValue("answerSource", answerSource);
        }
    }

    public record FieldStat(String field, long aiFilled, long edited, long added, long emptyBase,
                            long chip, long typed) {
    }

    public List<FieldStat> fieldStats(Filter f) {
        return jdbc.query("""
                SELECT f.field,
                       COALESCE(SUM(f.ai_filled), 0)                    ai_filled,
                       COALESCE(SUM(f.result = 'EDITED'), 0)            edited,
                       COALESCE(SUM(f.result = 'ADDED'), 0)             added,
                       COALESCE(SUM(NOT f.ai_filled), 0)                empty_base,
                       COALESCE(SUM(f.answer_source = 'CHIP'), 0)       chip,
                       COALESCE(SUM(f.answer_source = 'TYPED'), 0)      typed
                FROM goal_review_field f
                JOIN goal_creation_session s ON s.goal_creation_session_id = f.goal_creation_session_id
                WHERE f.created_at >= :fromAt AND f.created_at < :toAt
                """ + f.where("f", "s", true) + " GROUP BY f.field", f.params(),
                (rs, i) -> new FieldStat(rs.getString("field"), rs.getLong("ai_filled"), rs.getLong("edited"),
                        rs.getLong("added"), rs.getLong("empty_base"), rs.getLong("chip"), rs.getLong("typed")));
    }

    public record SessionStat(long sessions, long editedSessions, long addedSessions, long untouched, long editedItems) {
    }

    public SessionStat sessionStats(Filter f) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) sessions,
                       COALESCE(SUM(x.e > 0), 0)             edited_sessions,
                       COALESCE(SUM(x.a > 0), 0)             added_sessions,
                       COALESCE(SUM(x.e = 0 AND x.a = 0), 0) untouched,
                       COALESCE(SUM(x.e), 0)                 edited_items
                FROM (
                    SELECT f.goal_creation_session_id,
                           SUM(f.result = 'EDITED') e,
                           SUM(f.result = 'ADDED' AND f.field IN """ + MISS_FIELDS + """
                ) a
                    FROM goal_review_field f
                    JOIN goal_creation_session s ON s.goal_creation_session_id = f.goal_creation_session_id
                    WHERE f.created_at >= :fromAt AND f.created_at < :toAt
                """ + f.where("f", "s", true) + """
                    GROUP BY f.goal_creation_session_id
                ) x
                """, f.params(),
                (rs, i) -> new SessionStat(rs.getLong("sessions"), rs.getLong("edited_sessions"),
                        rs.getLong("added_sessions"), rs.getLong("untouched"), rs.getLong("edited_items")));
    }

    public record ExitStat(long entered, long reviewed, long exited, long pending) {
    }

    /** 정리 화면 진입(review_entered_at) 기준 — 답 방식 필터는 해당 없음 */
    public ExitStat exit(Filter f) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) entered,
                       COALESCE(SUM(s.reviewed_at IS NOT NULL), 0)                                   reviewed,
                       COALESCE(SUM(s.reviewed_at IS NULL AND s.status = 'ABANDONED'), 0)            exited,
                       COALESCE(SUM(s.reviewed_at IS NULL AND s.status = 'READY_TO_GENERATE'), 0)    pending
                FROM goal_creation_session s
                WHERE s.review_entered_at >= :fromAt AND s.review_entered_at < :toAt
                """ + f.where(null, "s", false), f.params(),
                (rs, i) -> new ExitStat(rs.getLong("entered"), rs.getLong("reviewed"),
                        rs.getLong("exited"), rs.getLong("pending")));
    }

    public List<String> promptVersions() {
        return jdbc.queryForList("""
                SELECT prompt_version FROM goal_creation_session
                WHERE prompt_version IS NOT NULL
                GROUP BY prompt_version ORDER BY prompt_version DESC LIMIT 20
                """, new MapSqlParameterSource(), String.class);
    }

    public record ItemRow(LocalDateTime at, String result, String question, String chipsJson, String answer,
                          String answerSource, String aiValue, String finalValue) {
    }

    /** @param results EDITED · ADDED 중 보여줄 것 */
    public List<ItemRow> items(Filter f, String field, List<String> results, int limit, int offset) {
        MapSqlParameterSource p = f.params().addValue("field", field).addValue("results", results)
                .addValue("limit", limit).addValue("offset", offset);
        return jdbc.query("""
                SELECT f.created_at, f.result, f.ai_value, f.final_value, f.answer_source,
                       q.content question, q.quick_replies chips, a.content answer
                FROM goal_review_field f
                JOIN goal_creation_session s ON s.goal_creation_session_id = f.goal_creation_session_id
                LEFT JOIN goal_creation_message q ON q.goal_creation_message_id = f.question_message_id
                LEFT JOIN goal_creation_message a ON a.goal_creation_message_id = f.answer_message_id
                WHERE f.created_at >= :fromAt AND f.created_at < :toAt
                  AND f.field = :field AND f.result IN (:results)
                """ + f.where("f", "s", true) + """
                 ORDER BY f.created_at DESC, f.goal_review_field_id DESC
                LIMIT :limit OFFSET :offset
                """, p,
                (rs, i) -> new ItemRow(rs.getTimestamp("created_at").toLocalDateTime(), rs.getString("result"),
                        rs.getString("question"), rs.getString("chips"), rs.getString("answer"),
                        rs.getString("answer_source"), rs.getString("ai_value"), rs.getString("final_value")));
    }

    public long itemCount(Filter f, String field, List<String> results) {
        Long n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM goal_review_field f
                JOIN goal_creation_session s ON s.goal_creation_session_id = f.goal_creation_session_id
                WHERE f.created_at >= :fromAt AND f.created_at < :toAt
                  AND f.field = :field AND f.result IN (:results)
                """ + f.where("f", "s", true),
                f.params().addValue("field", field).addValue("results", results), Long.class);
        return n == null ? 0 : n;
    }
}
