package com.naleul.naleul.domain.admin.repository;

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
 * 운영 대시보드 "Aha-moment" 집계 (V69 users.created_at · goal_category.created_at · user_visit_day).
 *
 * 시간 기준: 가입일(D0) = 가입 시각을 한국 날짜로 바꾼 날. "가입 후 7일 안" = D0 ~ D6.
 *  - users · goal_category · actual_activity 의 created_at 은 서버 시간 → :off(한국 − 서버, 분)를 더해 한국 날짜로
 *  - task.completed_at 은 한국 시간으로 저장돼 있어 그대로
 *  - user_visit_day.visit_date 는 한국 날짜
 * 운영진(ADMIN) 계정은 뺀다.
 */
@Repository
@RequiredArgsConstructor
public class AhaMomentQueryRepository {

    private static final String D0 = "DATE(DATE_ADD(u.created_at, INTERVAL :off MINUTE))";
    private static final String COHORT = """
            u.created_at >= :fromAt AND u.created_at < :toAt AND u.user_role <> 'ADMIN'
            """;

    private final NamedParameterJdbcTemplate jdbc;

    private static MapSqlParameterSource params(LocalDateTime fromAt, LocalDateTime toAt, int off) {
        return new MapSqlParameterSource("fromAt", Timestamp.valueOf(fromAt)).addValue("toAt", Timestamp.valueOf(toAt))
                .addValue("off", off);
    }

    /** @param signupDay 한국 가입일 */
    public record Signup(long userId, LocalDate signupDay) {
    }

    public List<Signup> signups(LocalDateTime fromAt, LocalDateTime toAt, int off) {
        return jdbc.query("SELECT u.user_id, " + D0 + " d0 FROM users u WHERE " + COHORT,
                params(fromAt, toAt, off), (rs, i) -> new Signup(rs.getLong("user_id"), rs.getDate("d0").toLocalDate()));
    }

    /** 가입 후 7일 안에 만든 목표 수 (임시 목표 제외, 나중에 지운 목표도 "만든 것"이라 포함) */
    public Map<Long, Long> goalsWithin7Days(LocalDateTime fromAt, LocalDateTime toAt, int off) {
        return toMap("""
                SELECT g.user_id uid, COUNT(*) n
                FROM goal_category g JOIN users u ON u.user_id = g.user_id
                WHERE %s AND g.is_temporary = FALSE AND g.created_at IS NOT NULL
                  AND DATE(DATE_ADD(g.created_at, INTERVAL :off MINUTE)) BETWEEN %s AND DATE_ADD(%s, INTERVAL 6 DAY)
                GROUP BY g.user_id
                """.formatted(COHORT, D0, D0), params(fromAt, toAt, off));
    }

    /** 가입 후 7일 안에 완료한 Task 수 (루틴 · 일반 모두) */
    public Map<Long, Long> tasksWithin7Days(LocalDateTime fromAt, LocalDateTime toAt, int off) {
        return toMap("""
                SELECT t.user_id uid, COUNT(*) n
                FROM task t JOIN users u ON u.user_id = t.user_id
                WHERE %s AND t.task_status = 'COMPLETED' AND t.completed_at IS NOT NULL
                  AND DATE(t.completed_at) BETWEEN %s AND DATE_ADD(%s, INTERVAL 6 DAY)
                GROUP BY t.user_id
                """.formatted(COHORT, D0, D0), params(fromAt, toAt, off));
    }

    /**
     * 가입일 기준 fromDay ~ toDay 일째에 "온 날" 수.
     * 온 날 = 오늘의 TimeBlock · Task 화면을 본 날(user_visit_day) ∪ Task 를 완료한 날 ∪ 실제 한 일을 추가한 날.
     */
    public Map<Long, Long> activeDays(LocalDateTime fromAt, LocalDateTime toAt, int off, int fromDay, int toDay) {
        String range = "BETWEEN DATE_ADD(%s, INTERVAL :fromDay DAY) AND DATE_ADD(%s, INTERVAL :toDay DAY)".formatted(D0, D0);
        return toMap("""
                SELECT x.uid, COUNT(DISTINCT x.d) n FROM (
                    SELECT v.user_id uid, v.visit_date d
                    FROM user_visit_day v JOIN users u ON u.user_id = v.user_id
                    WHERE %1$s AND v.visit_date %2$s
                    UNION ALL
                    SELECT t.user_id uid, DATE(t.completed_at) d
                    FROM task t JOIN users u ON u.user_id = t.user_id
                    WHERE %1$s AND t.task_status = 'COMPLETED' AND t.completed_at IS NOT NULL
                      AND DATE(t.completed_at) %2$s
                    UNION ALL
                    SELECT a.user_id uid, DATE(DATE_ADD(a.created_at, INTERVAL :off MINUTE)) d
                    FROM actual_activity a JOIN users u ON u.user_id = a.user_id
                    WHERE %1$s AND DATE(DATE_ADD(a.created_at, INTERVAL :off MINUTE)) %2$s
                ) x GROUP BY x.uid
                """.formatted(COHORT, range),
                params(fromAt, toAt, off).addValue("fromDay", fromDay).addValue("toDay", toDay));
    }

    /** 방문 기록을 시작한 날 (V69 배포일) — 그 전 날짜는 Task 완료 · 실제 한 일로만 "온 날"을 센다 */
    public LocalDate visitTrackingSince() {
        Date d = jdbc.queryForObject("SELECT MIN(visit_date) FROM user_visit_day", new MapSqlParameterSource(), Date.class);
        return d == null ? null : d.toLocalDate();
    }

    private Map<Long, Long> toMap(String sql, MapSqlParameterSource p) {
        Map<Long, Long> m = new HashMap<>();
        jdbc.query(sql, p, (RowCallbackHandler) rs -> m.put(rs.getLong("uid"), rs.getLong("n")));
        return m;
    }
}
