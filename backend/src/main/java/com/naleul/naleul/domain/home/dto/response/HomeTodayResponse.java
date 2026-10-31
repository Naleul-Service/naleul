package com.naleul.naleul.domain.home.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * GET /api/v1/home/today — 웹 · 앱 홈 "오늘" 화면에서 캘린더 · 습관 외에 필요한 것.
 * (오늘 일정 · 루틴 Task 는 GET /timetable, 생활 습관은 GET /habits 를 그대로 쓴다)
 *
 * deadlines : 다가오는 마감 — 지난 7일 안에 마감이 지난 것부터 앞으로 14일(Task) · 1년(주제 · 목표형 종료일)까지, 날짜 순
 * goals     : 진행 중인 목표별 "오늘" — 오늘 할 Task 수 · 끝낸 Task · 못 한 Task · 오늘 쓴 회고.
 *             오늘 할 일이나 회고가 있는 목표(activeToday)가 먼저 온다.
 */
public record HomeTodayResponse(
        LocalDate today,
        List<Deadline> deadlines,
        List<GoalToday> goals
) {

    /**
     * type      : TASK(Task 마감) · TOPIC(업무형 주제 마감, V53) · GOAL(목표형 종료일)
     * id        : taskId · workTopicId · goalCategoryId
     * dDay      : 오늘 0, 지났으면 음수
     * remaining : TOPIC 일 때 그 주제의 남은 Task 수 (나머지는 null)
     */
    public record Deadline(
            DeadlineType type,
            Long id,
            String title,
            String emoji,
            LocalDate date,
            int dDay,
            boolean overdue,
            Long goalCategoryId,
            String goalName,
            String goalEmoji,
            String colorCode,
            String goalMode,
            Integer remaining
    ) {
    }

    public enum DeadlineType { TASK, TOPIC, GOAL }

    /**
     * planned     : 오늘로 잡힌 Task 수 (시간 미정 포함, 건너뛴 것 제외)
     * done        : 오늘 끝낸 Task 수 (다른 날 잡혀 있었어도 오늘 끝냈으면 센다)
     * doneTasks   : 오늘 끝낸 Task 이름 (최대 5개) — 회고 쓸 때 떠올리기 좋게
     * openTasks   : 오늘 잡혔는데 아직 못 한 Task 이름 (최대 5개)
     * retros      : 오늘 날짜로 쓴 회고 (업무형은 여기에 써요. 목표형은 예전에 쓴 것만 보여요)
     * checkIn     : 목표형(ACHIEVEMENT)만 — 그날 수치 + 기분 + 한 줄 회고 (목표 상세 "오늘 기록"과 같은 데이터).
     *               쓰기는 PUT /goal-categories/{id}/check-in. 업무형 · 기타는 null
     */
    public record GoalToday(
            Long goalCategoryId,
            String goalName,
            String emoji,
            String colorCode,
            String goalMode,
            boolean activeToday,
            int planned,
            int done,
            List<String> doneTasks,
            List<String> openTasks,
            List<Retro> retros,
            CheckIn checkIn
    ) {
    }

    /**
     * 목표형 "오늘 기록".
     * metricName · unit · currentValue : 수치 목표일 때만 (수치가 없는 목표면 null — 기분 · 회고만 남겨요)
     * value : 그날 기록한 수치 (없으면 null)
     * mood · note : 그날 회고 (없으면 null)
     */
    public record CheckIn(
            boolean hasMetric,
            String metricName,
            String unit,
            BigDecimal currentValue,
            BigDecimal value,
            String mood,
            String note
    ) {
        public boolean written() {
            return value != null || mood != null || note != null;
        }
    }

    public record Retro(Long retrospectiveId, String reviewType, String content) {
    }
}
