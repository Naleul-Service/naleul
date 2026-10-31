package com.naleul.naleul.domain.admin.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 운영 대시보드 "AI 품질" 집계 — AI 기능마다 ① 채택 ② 수정 ③ 결과 (전체 사용자, 읽기 전용).
 *
 * 시간 기준은 AdminDashboardQueryRepository 와 같다:
 *  - created_at(서버 시간대) 컬럼은 fromAt / toAt
 *  - 한국 시간으로 저장한 컬럼(task_schedule_log · pattern_suggestion_action)은 kstFrom / kstTo
 *  - DATE 컬럼(routine_date …)은 한국 날짜 그대로
 */
@Repository
@RequiredArgsConstructor
public class AiQualityQueryRepository {

    /** AdminDashboardQueryRepository.PLANNED_DATE 와 같은 "하기로 한 날" */
    private static final String PLANNED_DATE =
            "COALESCE(t.routine_date, t.scheduled_date, DATE(t.planned_start_at), t.due_date)";
    /** 적용 · 확정 뒤 결과를 보는 기간 (일) */
    public static final int RESULT_WINDOW_DAYS = 14;
    /** 패턴 제안은 루틴 하나의 요일 · 시간을 바꾸는 거라 길게 본다 (일) */
    public static final int PATTERN_WINDOW_DAYS = 28;

    private final NamedParameterJdbcTemplate jdbc;

    // ───────────── AI 목표 생성 ─────────────

    public record GoalPlanRow(long confirmed, long firstDraft, long draftTasks, long editedTasks,
                              long draftMinutes, long confirmedMinutes, long aiEnded, long aiAchieved) {
    }

    public GoalPlanRow goalPlan(LocalDateTime fromAt, LocalDateTime toAt) {
        MapSqlParameterSource p = range(fromAt, toAt);
        long[] confirm = jdbc.queryForObject("""
                SELECT COUNT(*) confirmed, COALESCE(SUM(d.maxv = 1), 0) first_draft
                FROM goal_creation_session s
                JOIN (SELECT goal_creation_session_id sid, MAX(version) maxv FROM goal_draft GROUP BY goal_creation_session_id) d
                  ON d.sid = s.goal_creation_session_id
                WHERE s.status = 'CONFIRMED' AND s.updated_at >= :fromAt AND s.updated_at < :toAt
                """, p, (rs, i) -> new long[]{rs.getLong("confirmed"), rs.getLong("first_draft")});
        long[] fit = jdbc.queryForObject("""
                SELECT COALESCE(SUM(draft_task_count), 0) dt, COALESCE(SUM(edited_task_count), 0) et,
                       COALESCE(SUM(draft_weekly_minutes), 0) dm, COALESCE(SUM(confirmed_weekly_minutes), 0) cm
                FROM goal_plan_fit WHERE created_at >= :fromAt AND created_at < :toAt
                """, p, (rs, i) -> new long[]{rs.getLong("dt"), rs.getLong("et"), rs.getLong("dm"), rs.getLong("cm")});
        long[] outcome = jdbc.queryForObject("""
                SELECT COUNT(*) ended, COALESCE(SUM(result = 'ACHIEVED'), 0) achieved
                FROM goal_outcome WHERE ai_generated = TRUE AND created_at >= :fromAt AND created_at < :toAt
                """, p, (rs, i) -> new long[]{rs.getLong("ended"), rs.getLong("achieved")});
        return new GoalPlanRow(confirm[0], confirm[1], fit[0], fit[1], fit[2], fit[3], outcome[0], outcome[1]);
    }

    /**
     * V68 AI 초안 정확도 (goal_draft_review, 확정 시각 기준) — AI 품질 "AI 목표 생성" 줄 요약.
     * 자세한 숫자는 대시보드 AI 초안 탭 (DraftReviewService) 과 같은 정의.
     *
     * @param noRegen        다시 생성 없이 확정한 세션 (성향 · 강도 변경은 조건 변경이라 다시 생성으로 안 셈)
     * @param keptFields     AI 값이 그대로 남은 칸 (자동 조정 제외) · aiFields = AI 가 만든 칸
     * @param routinePlanned 첫 행동부터 14일 동안 해야 했던 루틴 (14일이 지난 세션만) · routineDone = 체크한 루틴
     * @param weeklySessions 루틴이 있는 세션 (루틴 시간 변화의 n)
     */
    public record DraftReviewRow(long sessions, long noRegen, long keptFields, long aiFields,
                                 long routinePlanned, long routineDone,
                                 long weeklySessions, long draftMinutes, long confirmedMinutes) {
    }

    public DraftReviewRow draftReview(LocalDateTime fromAt, LocalDateTime toAt) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) sessions,
                       COALESCE(SUM(path <> 'REGEN'), 0) no_regen,
                       COALESCE(SUM(kept_fields), 0) kept, COALESCE(SUM(ai_fields), 0) ai,
                       COALESCE(SUM(d14_routine_planned), 0) planned, COALESCE(SUM(d14_routine_done), 0) done,
                       COALESCE(SUM(draft_weekly_minutes > 0), 0) weekly_sessions,
                       COALESCE(SUM(CASE WHEN draft_weekly_minutes > 0 THEN draft_weekly_minutes END), 0) dm,
                       COALESCE(SUM(CASE WHEN draft_weekly_minutes > 0 THEN confirmed_weekly_minutes END), 0) cm
                FROM goal_draft_review WHERE confirmed_at >= :fromAt AND confirmed_at < :toAt
                """, range(fromAt, toAt), (rs, i) -> new DraftReviewRow(rs.getLong("sessions"), rs.getLong("no_regen"),
                rs.getLong("kept"), rs.getLong("ai"), rs.getLong("planned"), rs.getLong("done"),
                rs.getLong("weekly_sessions"), rs.getLong("dm"), rs.getLong("cm")));
    }

    // ───────────── 자동 배치 (TimeTable) ─────────────

    /**
     * @param placed   엔진이 배치한 Task (배치된 날이 기간 안 · 어제까지)
     * @param kept     그 뒤 사용자가 옮기거나 시간 미정으로 돌리지 않은 Task
     * @param keptDone 그대로 두고 완료한 Task
     */
    public record PlacementRow(long placed, long kept, long keptDone, long planPlaced, long planUnplaced) {
    }

    public PlacementRow placement(LocalDate from, LocalDate yesterday) {
        MapSqlParameterSource p = new MapSqlParameterSource("from", from).addValue("to", yesterday);
        long[] r = jdbc.queryForObject("""
                SELECT COUNT(*) placed,
                       COALESCE(SUM(x.moved = 0), 0) kept,
                       COALESCE(SUM(x.moved = 0 AND x.done = 1), 0) kept_done
                FROM (SELECT p.task_id,
                             MAX(CASE WHEN m.task_schedule_log_id IS NULL THEN 0 ELSE 1 END) moved,
                             MAX(CASE WHEN t.task_status = 'COMPLETED' THEN 1 ELSE 0 END) done
                      FROM task_schedule_log p
                      JOIN task t ON t.task_id = p.task_id
                      LEFT JOIN task_schedule_log m
                        ON m.task_id = p.task_id AND m.action IN ('USER_MOVE', 'USER_UNSCHEDULE')
                       AND m.created_at > p.created_at
                      WHERE p.action = 'ENGINE_PLACE' AND p.to_date BETWEEN :from AND :to
                        AND t.task_status <> 'DELETED'
                      GROUP BY p.task_id) x
                """, p, (rs, i) -> new long[]{rs.getLong("placed"), rs.getLong("kept"), rs.getLong("kept_done")});
        long[] plan = jdbc.queryForObject("""
                SELECT COALESCE(SUM(placed_count), 0) placed, COALESCE(SUM(unplaced_count), 0) unplaced
                FROM daily_plan WHERE plan_date BETWEEN :from AND :to AND status <> 'FAILED'
                """, p, (rs, i) -> new long[]{rs.getLong("placed"), rs.getLong("unplaced")});
        return new PlacementRow(r[0], r[1], r[2], plan[0], plan[1]);
    }

    // ───────────── 말로 고치기 · AI 일정 조정 · 브레인 덤프 (ai_suggestion_log) ─────────────

    /**
     * @param withValid      검증을 통과한 제안이 1개 이상 있던 미리보기 수 (채택률 분모)
     * @param appliedValid   적용한 미리보기의 보여 준 변경 수 합
     * @param appliedItems   적용한 변경 · 확정한 항목 수 합
     * @param appliedUnedited 브레인 덤프: 하나도 안 고치고 확정한 수
     */
    public record SuggestionRow(String feature, long total, long withValid, long applied,
                                long suggested, long valid, long appliedValid, long appliedItems,
                                long appliedUnedited, long edited, long goalChanged) {
    }

    public List<SuggestionRow> suggestions(LocalDateTime fromAt, LocalDateTime toAt) {
        return jdbc.query("""
                SELECT feature, COUNT(*) total,
                       COALESCE(SUM(valid_count > 0), 0) with_valid,
                       COALESCE(SUM(status = 'APPLIED'), 0) applied,
                       COALESCE(SUM(suggested_count), 0) suggested,
                       COALESCE(SUM(valid_count), 0) valid,
                       COALESCE(SUM(CASE WHEN status = 'APPLIED' THEN valid_count ELSE 0 END), 0) applied_valid,
                       COALESCE(SUM(CASE WHEN status = 'APPLIED' THEN applied_count ELSE 0 END), 0) applied_items,
                       COALESCE(SUM(status = 'APPLIED' AND edited_count = 0), 0) applied_unedited,
                       COALESCE(SUM(CASE WHEN status = 'APPLIED' THEN COALESCE(edited_count, 0) ELSE 0 END), 0) edited,
                       COALESCE(SUM(CASE WHEN status = 'APPLIED' THEN COALESCE(goal_changed_count, 0) ELSE 0 END), 0) goal_changed
                FROM ai_suggestion_log
                WHERE ai_used = TRUE AND created_at >= :fromAt AND created_at < :toAt
                GROUP BY feature
                """, range(fromAt, toAt),
                (rs, i) -> new SuggestionRow(rs.getString("feature"), rs.getLong("total"), rs.getLong("with_valid"),
                        rs.getLong("applied"), rs.getLong("suggested"), rs.getLong("valid"),
                        rs.getLong("applied_valid"), rs.getLong("applied_items"), rs.getLong("applied_unedited"),
                        rs.getLong("edited"), rs.getLong("goal_changed")));
    }

    public record DoneRow(String feature, long due, long done) {
    }

    /** 적용된 제안이 만든 · 옮긴 Task 중 완료한 것 (하기로 한 날이 지났거나 이미 완료한 것만) */
    public List<DoneRow> suggestionTaskResults(LocalDateTime fromAt, LocalDateTime toAt, LocalDate today) {
        return jdbc.query("""
                SELECT l.feature, COUNT(*) due, COALESCE(SUM(t.task_status = 'COMPLETED'), 0) done
                FROM ai_suggestion_target x
                JOIN ai_suggestion_log l ON l.ai_suggestion_log_id = x.ai_suggestion_log_id
                JOIN task t ON t.task_id = x.target_id
                WHERE x.target_type = 'TASK' AND l.status = 'APPLIED' AND l.ai_used = TRUE
                  AND l.created_at >= :fromAt AND l.created_at < :toAt
                  AND t.task_status <> 'DELETED'
                  AND (t.task_status = 'COMPLETED' OR %s < :today)
                GROUP BY l.feature
                """.formatted(PLANNED_DATE), range(fromAt, toAt).addValue("today", today),
                (rs, i) -> new DoneRow(rs.getString("feature"), rs.getLong("due"), rs.getLong("done")));
    }

    /** AI 일정 조정으로 옮긴 루틴의, 적용 뒤 RESULT_WINDOW_DAYS 일 동안의 루틴 Task (어제까지) */
    public DoneRow movedRoutineResults(LocalDateTime fromAt, LocalDateTime toAt, LocalDate today) {
        return jdbc.queryForObject("""
                SELECT 'SCHEDULE_EDIT' feature, COUNT(*) due, COALESCE(SUM(t.task_status = 'COMPLETED'), 0) done
                FROM ai_suggestion_target x
                JOIN ai_suggestion_log l ON l.ai_suggestion_log_id = x.ai_suggestion_log_id
                JOIN task t ON t.routine_id = x.target_id
                WHERE x.target_type = 'ROUTINE' AND l.feature = 'SCHEDULE_EDIT' AND l.status = 'APPLIED'
                  AND l.created_at >= :fromAt AND l.created_at < :toAt
                  AND t.task_status <> 'DELETED'
                  AND t.routine_date >= DATE(l.applied_at)
                  AND t.routine_date < LEAST(:today, DATE_ADD(DATE(l.applied_at), INTERVAL %d DAY))
                """.formatted(RESULT_WINDOW_DAYS), range(fromAt, toAt).addValue("today", today),
                (rs, i) -> new DoneRow(rs.getString("feature"), rs.getLong("due"), rs.getLong("done")));
    }

    /** 전 · 후 실천률 (같은 길이의 기간) */
    public record BeforeAfterRow(long beforeDue, long beforeDone, long afterDue, long afterDone) {
    }

    /** 말로 고치기: 고친 목표의 적용 전 · 후 RESULT_WINDOW_DAYS 일 실천률 */
    public BeforeAfterRow goalEditBeforeAfter(LocalDateTime fromAt, LocalDateTime toAt, LocalDate today) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(a.after_win), 0) after_due, COALESCE(SUM(a.after_win AND a.done), 0) after_done,
                       COALESCE(SUM(a.before_win), 0) before_due, COALESCE(SUM(a.before_win AND a.done), 0) before_done
                FROM (SELECT (%1$s >= DATE(l.applied_at)
                                AND %1$s < LEAST(:today, DATE_ADD(DATE(l.applied_at), INTERVAL %2$d DAY))) after_win,
                             (%1$s >= DATE_SUB(DATE(l.applied_at), INTERVAL %2$d DAY)
                                AND %1$s < DATE(l.applied_at)) before_win,
                             (t.task_status = 'COMPLETED') done
                      FROM ai_suggestion_log l
                      JOIN task t ON t.goal_category_id = l.target_goal_id
                      WHERE l.feature = 'GOAL_EDIT' AND l.status = 'APPLIED'
                        AND l.created_at >= :fromAt AND l.created_at < :toAt
                        AND t.task_status <> 'DELETED') a
                """.formatted(PLANNED_DATE, RESULT_WINDOW_DAYS), range(fromAt, toAt).addValue("today", today),
                (rs, i) -> new BeforeAfterRow(rs.getLong("before_due"), rs.getLong("before_done"),
                        rs.getLong("after_due"), rs.getLong("after_done")));
    }

    // ───────────── 나의 패턴 제안 ─────────────

    public record PatternRow(long applied, long dismissed) {
    }

    public PatternRow patternActions(LocalDateTime kstFrom, LocalDateTime kstTo) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(action = 'APPLIED'), 0) applied, COALESCE(SUM(action = 'DISMISSED'), 0) dismissed
                FROM pattern_suggestion_action
                WHERE created_at >= :fromAt AND created_at < :toAt
                """, range(kstFrom, kstTo),
                (rs, i) -> new PatternRow(rs.getLong("applied"), rs.getLong("dismissed")));
    }

    /** 반영한 루틴의 반영 전 · 후 PATTERN_WINDOW_DAYS 일 실천률 (어제까지) */
    public BeforeAfterRow patternBeforeAfter(LocalDateTime kstFrom, LocalDateTime kstTo, LocalDate today) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(a.after_win), 0) after_due, COALESCE(SUM(a.after_win AND a.done), 0) after_done,
                       COALESCE(SUM(a.before_win), 0) before_due, COALESCE(SUM(a.before_win AND a.done), 0) before_done
                FROM (SELECT (t.routine_date >= DATE(s.created_at)
                                AND t.routine_date < LEAST(:today, DATE_ADD(DATE(s.created_at), INTERVAL %1$d DAY))) after_win,
                             (t.routine_date >= DATE_SUB(DATE(s.created_at), INTERVAL %1$d DAY)
                                AND t.routine_date < DATE(s.created_at)) before_win,
                             (t.task_status = 'COMPLETED') done
                      FROM pattern_suggestion_action s
                      JOIN task t ON t.routine_id = s.routine_id
                      WHERE s.action = 'APPLIED' AND s.routine_id IS NOT NULL
                        AND s.created_at >= :fromAt AND s.created_at < :toAt
                        AND t.task_status <> 'DELETED') a
                """.formatted(PATTERN_WINDOW_DAYS), range(kstFrom, kstTo).addValue("today", today),
                (rs, i) -> new BeforeAfterRow(rs.getLong("before_due"), rs.getLong("before_done"),
                        rs.getLong("after_due"), rs.getLong("after_done")));
    }

    // ───────────── 자동 판정 신뢰도 ─────────────

    /**
     * 판정 근거별.
     * @param compared   사용자가 직접 고른 결과가 있고 자동 판정도 남아 있는 목표 (일치율 분모)
     * @param metric80   수치 80~99% 라 자동으로 "달성" 처리된 목표 (기준을 100 → 80 으로 내린 효과)
     */
    public record OutcomeCheckRow(String reason, long autoJudged, long answered, long dismissed, long pending,
                                  long compared, long matched, long metric80) {
    }

    public List<OutcomeCheckRow> outcomeChecks(LocalDateTime fromAt, LocalDateTime toAt) {
        return jdbc.query("""
                SELECT COALESCE(auto_reason, 'UNKNOWN') reason,
                       COALESCE(SUM(prompt_status IS NOT NULL), 0) auto_judged,
                       COALESCE(SUM(prompt_status = 'ANSWERED'), 0) answered,
                       COALESCE(SUM(prompt_status = 'DISMISSED'), 0) dismissed,
                       COALESCE(SUM(prompt_status = 'PENDING'), 0) pending,
                       COALESCE(SUM(source = 'USER' AND auto_result IS NOT NULL), 0) compared,
                       COALESCE(SUM(source = 'USER' AND auto_result = result), 0) matched,
                       COALESCE(SUM(auto_reason = 'METRIC' AND auto_result = 'ACHIEVED' AND metric_percent < 100), 0) metric80
                FROM goal_outcome
                WHERE created_at >= :fromAt AND created_at < :toAt
                GROUP BY COALESCE(auto_reason, 'UNKNOWN')
                """, range(fromAt, toAt),
                (rs, i) -> new OutcomeCheckRow(rs.getString("reason"), rs.getLong("auto_judged"), rs.getLong("answered"),
                        rs.getLong("dismissed"), rs.getLong("pending"), rs.getLong("compared"), rs.getLong("matched"),
                        rs.getLong("metric80")));
    }

    private static MapSqlParameterSource range(LocalDateTime fromAt, LocalDateTime toAt) {
        return new MapSqlParameterSource("fromAt", Timestamp.valueOf(fromAt)).addValue("toAt", Timestamp.valueOf(toAt));
    }
}
