package com.naleul.naleul.domain.user.visit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 방문한 날 기록 (V69, Aha-moment "서로 다른 3일 이상 방문" · 운영 대시보드 전용).
 *
 * 앱 실행만으로는 세지 않고 오늘의 TimeBlock · Task 화면(홈 오늘 · 타임테이블)을 불러온 날만 센다.
 * 웹 · iOS · Android 가 모두 같은 API 를 부르므로 앱 수정이 필요 없다.
 * 사용자 · 날짜당 1줄 (INSERT IGNORE). 같은 날 두 번째부터는 메모리에서 걸러 DB 를 건드리지 않는다.
 * 측정용이라 실패해도 화면 응답에는 영향이 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VisitRecorder {

    static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public enum Source { HOME, TIMETABLE }

    private final NamedParameterJdbcTemplate jdbc;
    /** 사용자별 마지막으로 기록한 한국 날짜 (서버를 다시 켜면 비지만 INSERT IGNORE 라 중복은 안 생김) */
    private final Map<Long, LocalDate> recorded = new ConcurrentHashMap<>();

    public void record(Long userId, Source source) {
        if (userId == null) return;
        LocalDate today = LocalDate.now(KST);
        if (today.equals(recorded.get(userId))) return;
        try {
            jdbc.update("""
                    INSERT IGNORE INTO user_visit_day (user_id, visit_date, first_at, source)
                    VALUES (:user, :date, :at, :source)
                    """, new MapSqlParameterSource("user", userId).addValue("date", Date.valueOf(today))
                    .addValue("at", Timestamp.valueOf(LocalDateTime.now())).addValue("source", source.name()));
            recorded.put(userId, today);
        } catch (RuntimeException e) {
            log.debug("[Visit] 방문 기록 실패 userId={} reason={}", userId, e.getMessage());
        }
    }
}
