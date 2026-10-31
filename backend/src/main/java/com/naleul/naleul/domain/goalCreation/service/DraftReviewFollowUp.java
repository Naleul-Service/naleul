package com.naleul.naleul.domain.goalCreation.service;

import com.naleul.naleul.domain.goalCreation.dto.plan.Weekday;
import com.naleul.naleul.domain.goalCreation.enums.DraftFieldResult;
import com.naleul.naleul.domain.goalCreation.enums.FirstActionType;
import com.naleul.naleul.domain.goalCreation.support.DraftPlanComparer;
import com.naleul.naleul.domain.goalCreation.support.GoalClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

/**
 * AI 초안 정확도 — 확정 뒤 (V68, 운영 대시보드 전용).
 *
 *  1. 첫 행동 찾기: 확정 뒤 그 목표의 루틴 체크 · Task 완료 / 실제 한 일 추가 / 수치 기록 중 가장 이른 것.
 *     사용자가 들어오지 않은 기간은 세지 않으려고 확정 시각이 아니라 첫 행동부터 센다.
 *  2. 첫 행동부터 14일 · 30일이 지나면: 확정한 값 ↔ 지금 루틴 · Task · 목표를 칸별로 비교 (고침 · 삭제)
 *     + 그 기간 루틴 실천률 (해야 했던 루틴 중 체크한 비율).
 *
 * 비교 형식은 확정 때와 같다 (DraftPlanComparer 의 화면 표기). 날짜 · 시각은 한국 시간.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DraftReviewFollowUp {

    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    /** 확정 뒤 이 기간 안에 첫 행동이 없으면 더 찾지 않는다 ("확정 뒤 아직 안 씀"으로 남음) */
    static final int FIRST_ACTION_LOOKUP_DAYS = 90;
    public static final int[] WINDOWS = {14, 30};

    private final NamedParameterJdbcTemplate jdbc;
    private final GoalClock clock;

    // ───────────── 1. 첫 행동 ─────────────

    @Transactional
    public int findFirstActions() {
        LocalDateTime now = clock.now();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT goal_draft_review_id id, goal_category_id g, confirmed_at c
                FROM goal_draft_review
                WHERE first_action_at IS NULL AND confirmed_at >= :since
                """, new MapSqlParameterSource("since", Timestamp.valueOf(now.minusDays(FIRST_ACTION_LOOKUP_DAYS))));
        int found = 0;
        for (Map<String, Object> r : rows) {
            long id = ((Number) r.get("id")).longValue();
            long goal = ((Number) r.get("g")).longValue();
            LocalDateTime confirmedAt = ldt(r.get("c"));
            Map.Entry<LocalDateTime, FirstActionType> first = firstAction(goal, confirmedAt);
            if (first == null) continue;
            jdbc.update("""
                    UPDATE goal_draft_review SET first_action_at = :at, first_action_type = :type
                    WHERE goal_draft_review_id = :id
                    """, new MapSqlParameterSource("at", Timestamp.valueOf(first.getKey()))
                    .addValue("type", first.getValue().name()).addValue("id", id));
            found++;
        }
        return found;
    }

    private Map.Entry<LocalDateTime, FirstActionType> firstAction(long goal, LocalDateTime confirmedAt) {
        MapSqlParameterSource p = new MapSqlParameterSource("g", goal)
                .addValue("c", Timestamp.valueOf(confirmedAt))
                .addValue("cKst", Timestamp.valueOf(toKst(confirmedAt)));
        // Task.completedAt 은 한국 시간으로 저장된다 → 서버 시간으로 바꿔서 비교
        Timestamp done = jdbc.queryForObject("""
                SELECT MIN(completed_at) FROM task
                WHERE goal_category_id = :g AND task_status = 'COMPLETED' AND completed_at >= :cKst
                """, p, Timestamp.class);
        Timestamp actual = jdbc.queryForObject(
                "SELECT MIN(created_at) FROM actual_activity WHERE goal_category_id = :g AND created_at >= :c", p, Timestamp.class);
        Timestamp metric = jdbc.queryForObject(
                "SELECT MIN(created_at) FROM goal_metric_log WHERE goal_category_id = :g AND created_at >= :c", p, Timestamp.class);
        Map.Entry<LocalDateTime, FirstActionType> best = null;
        if (done != null) best = Map.entry(fromKst(done.toLocalDateTime()), FirstActionType.TASK_DONE);
        if (actual != null && (best == null || actual.toLocalDateTime().isBefore(best.getKey()))) {
            best = Map.entry(actual.toLocalDateTime(), FirstActionType.ACTUAL);
        }
        if (metric != null && (best == null || metric.toLocalDateTime().isBefore(best.getKey()))) {
            best = Map.entry(metric.toLocalDateTime(), FirstActionType.METRIC);
        }
        return best;
    }

    // ───────────── 2. 첫 행동부터 14일 · 30일 ─────────────

    @Transactional
    public int evaluate(int window) {
        String w = "d" + window;   // d14 · d30 (WINDOWS 값만 들어옴)
        if (Arrays.stream(WINDOWS).noneMatch(x -> x == window)) throw new IllegalArgumentException("window");
        LocalDateTime now = clock.now();
        List<Map<String, Object>> reviews = jdbc.queryForList("""
                SELECT goal_draft_review_id id, goal_category_id g, first_action_at f
                FROM goal_draft_review
                WHERE first_action_at IS NOT NULL AND %s_checked_at IS NULL AND first_action_at <= :cut
                LIMIT 500
                """.formatted(w), new MapSqlParameterSource("cut", Timestamp.valueOf(now.minusDays(window))));
        for (Map<String, Object> r : reviews) {
            long reviewId = ((Number) r.get("id")).longValue();
            long goal = ((Number) r.get("g")).longValue();
            LocalDate start = toKst(ldt(r.get("f"))).toLocalDate();
            LocalDate end = start.plusDays(window - 1L);
            evaluateReview(reviewId, goal, start, end, w, now);
        }
        return reviews.size();
    }

    private void evaluateReview(long reviewId, long goal, LocalDate start, LocalDate end, String w, LocalDateTime now) {
        MapSqlParameterSource rp = new MapSqlParameterSource("r", reviewId);
        List<Map<String, Object>> items = jdbc.queryForList("""
                SELECT goal_draft_review_item_id id, item_kind kind, entity_id e
                FROM goal_draft_review_item
                WHERE goal_draft_review_id = :r AND entity_id IS NOT NULL AND result <> 'DELETED'
                """, rp);
        List<Map<String, Object>> fields = jdbc.queryForList("""
                SELECT goal_draft_review_field_id id, goal_draft_review_item_id item, field, final_value v
                FROM goal_draft_review_field
                WHERE goal_draft_review_id = :r AND stage = 'CONFIRM' AND result <> 'DELETED'
                """, rp);
        Map<Long, List<Map<String, Object>>> fieldsByItem = new HashMap<>();
        for (Map<String, Object> f : fields) {
            if (f.get("item") == null) continue;
            fieldsByItem.computeIfAbsent(((Number) f.get("item")).longValue(), k -> new ArrayList<>()).add(f);
        }

        for (Map<String, Object> it : items) {
            long itemId = ((Number) it.get("id")).longValue();
            String kind = (String) it.get("kind");
            long entity = ((Number) it.get("e")).longValue();
            Current cur = switch (kind) {
                case "ROUTINE" -> routine(entity, start, end);
                case "ONE_TIME" -> oneTime(entity, end);
                case "GOAL" -> goal(entity);
                default -> null;
            };
            if (cur == null) continue;
            boolean anyEdited = false;
            for (Map<String, Object> f : fieldsByItem.getOrDefault(itemId, List.of())) {
                String field = (String) f.get("field");
                DraftFieldResult res;
                if (cur.deleted()) {
                    res = DraftFieldResult.DELETED;
                } else if (!cur.values().containsKey(field)) {
                    continue;   // 확정 뒤에는 비교하지 않는 칸 (연결 · 실행일 등)
                } else {
                    res = Objects.equals(f.get("v"), cur.values().get(field)) ? DraftFieldResult.KEPT : DraftFieldResult.EDITED;
                    anyEdited |= res == DraftFieldResult.EDITED;
                }
                jdbc.update("UPDATE goal_draft_review_field SET %s_result = :res WHERE goal_draft_review_field_id = :id".formatted(w),
                        new MapSqlParameterSource("res", res.name()).addValue("id", f.get("id")));
            }
            String itemRes = cur.deleted() ? "DELETED" : anyEdited ? "EDITED" : "KEPT";
            jdbc.update("""
                    UPDATE goal_draft_review_item SET %1$s_result = :res, %1$s_planned = :planned, %1$s_done = :done
                    WHERE goal_draft_review_item_id = :id
                    """.formatted(w), new MapSqlParameterSource("res", itemRes)
                    .addValue("planned", cur.planned()).addValue("done", cur.done()).addValue("id", itemId));
        }

        Map<String, Object> rate = jdbc.queryForMap("""
                SELECT COUNT(*) planned, COALESCE(SUM(task_status = 'COMPLETED'), 0) done
                FROM task
                WHERE goal_category_id = :g AND routine_id IS NOT NULL AND task_status <> 'DELETED'
                  AND routine_date BETWEEN :s AND :e
                """, new MapSqlParameterSource("g", goal).addValue("s", Date.valueOf(start)).addValue("e", Date.valueOf(end)));
        jdbc.update("""
                UPDATE goal_draft_review SET %1$s_checked_at = :now, %1$s_routine_planned = :planned, %1$s_routine_done = :done
                WHERE goal_draft_review_id = :id
                """.formatted(w), new MapSqlParameterSource("now", Timestamp.valueOf(now))
                .addValue("planned", ((Number) rate.get("planned")).intValue())
                .addValue("done", ((Number) rate.get("done")).intValue()).addValue("id", reviewId));
    }

    /** 확정 뒤 지금 값 (확정 때와 같은 표기) · 그 기간 실천 */
    record Current(boolean deleted, Map<String, String> values, Integer planned, Integer done) {
    }

    private Current routine(long routineId, LocalDate start, LocalDate end) {
        MapSqlParameterSource p = new MapSqlParameterSource("id", routineId);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT routine_name, emoji, routine_status, repeat_start_time, duration_minutes, description
                FROM routine WHERE routine_id = :id
                """, p);
        Map<String, Object> rate = jdbc.queryForMap("""
                SELECT COUNT(*) planned, COALESCE(SUM(task_status = 'COMPLETED'), 0) done
                FROM task WHERE routine_id = :id AND task_status <> 'DELETED' AND routine_date BETWEEN :s AND :e
                """, new MapSqlParameterSource("id", routineId).addValue("s", Date.valueOf(start)).addValue("e", Date.valueOf(end)));
        Integer planned = ((Number) rate.get("planned")).intValue(), done = ((Number) rate.get("done")).intValue();
        if (rows.isEmpty()) return new Current(true, Map.of(), planned, done);
        Map<String, Object> r = rows.get(0);
        String status = (String) r.get("routine_status");
        if ("DELETED".equals(status) || "STOPPED".equals(status)) return new Current(true, Map.of(), planned, done);
        List<String> dayNames = jdbc.queryForList(
                "SELECT day_of_week FROM routine_repeat_day WHERE routine_id = :id", p, String.class);
        List<Weekday> days = dayNames.stream().map(d -> Weekday.from(DayOfWeek.valueOf(d))).toList();
        Map<String, String> v = new HashMap<>();
        v.put("TITLE", DraftPlanComparer.text((String) r.get("routine_name")));
        v.put("EMOJI", DraftPlanComparer.text((String) r.get("emoji")));
        v.put("DAYS", DraftPlanComparer.days(days));
        Object t = r.get("repeat_start_time");
        v.put("START_TIME", DraftPlanComparer.time(t instanceof Time time ? time.toLocalTime() : t instanceof LocalTime lt ? lt : null));
        v.put("DURATION", DraftPlanComparer.minutes((Number) r.get("duration_minutes")));
        v.put("HOW_TO", DraftPlanComparer.text((String) r.get("description")));
        return new Current(false, v, planned, done);
    }

    private Current oneTime(long taskId, LocalDate end) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT task_name, emoji, task_status, due_date, planned_duration_minutes, completed_at
                FROM task WHERE task_id = :id
                """, new MapSqlParameterSource("id", taskId));
        if (rows.isEmpty() || "DELETED".equals(rows.get(0).get("task_status"))) return new Current(true, Map.of(), 1, 0);
        Map<String, Object> r = rows.get(0);
        Object completed = r.get("completed_at");
        LocalDateTime completedAt = completed == null ? null : ldt(completed);
        boolean done = "COMPLETED".equals(r.get("task_status")) && completedAt != null
                && !completedAt.toLocalDate().isAfter(end);
        Map<String, String> v = new HashMap<>();
        v.put("TITLE", DraftPlanComparer.text((String) r.get("task_name")));
        v.put("EMOJI", DraftPlanComparer.text((String) r.get("emoji")));
        Object due = r.get("due_date");
        v.put("DUE_DATE", DraftPlanComparer.date(ld(due)));
        v.put("DURATION", DraftPlanComparer.minutes((Number) r.get("planned_duration_minutes")));
        return new Current(false, v, 1, done ? 1 : 0);
    }

    private Current goal(long goalId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT goal_category_name, emoji, goal_category_status, goal_category_end_date, target_value, metric_unit
                FROM goal_category WHERE goal_category_id = :id
                """, new MapSqlParameterSource("id", goalId));
        if (rows.isEmpty() || "DELETED".equals(rows.get(0).get("goal_category_status"))) {
            return new Current(true, Map.of(), null, null);
        }
        Map<String, Object> r = rows.get(0);
        Map<String, String> v = new HashMap<>();
        v.put("TITLE", DraftPlanComparer.text((String) r.get("goal_category_name")));
        v.put("EMOJI", DraftPlanComparer.text((String) r.get("emoji")));
        Object endDate = r.get("goal_category_end_date");
        v.put("END_DATE", DraftPlanComparer.date(ld(endDate)));
        if (r.get("target_value") != null) {
            v.put("METRIC_TARGET", DraftPlanComparer.number((BigDecimal) r.get("target_value"), (String) r.get("metric_unit")));
        }
        return new Current(false, v, null, null);
    }

    /** 드라이버에 따라 DATETIME 이 Timestamp 또는 LocalDateTime 으로 온다 */
    static LocalDateTime ldt(Object v) {
        if (v instanceof Timestamp ts) return ts.toLocalDateTime();
        if (v instanceof LocalDateTime l) return l;
        throw new IllegalArgumentException("not a datetime: " + v);
    }

    static LocalDate ld(Object v) {
        if (v instanceof Date d) return d.toLocalDate();
        if (v instanceof LocalDate l) return l;
        if (v instanceof LocalDateTime l) return l.toLocalDate();
        return null;
    }

    private static LocalDateTime toKst(LocalDateTime server) {
        return server.atZone(ZoneId.systemDefault()).withZoneSameInstant(KST).toLocalDateTime();
    }

    private static LocalDateTime fromKst(LocalDateTime kst) {
        return kst.atZone(KST).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }
}
