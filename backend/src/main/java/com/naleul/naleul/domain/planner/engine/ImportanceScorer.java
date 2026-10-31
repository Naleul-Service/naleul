package com.naleul.naleul.domain.planner.engine;

import com.naleul.naleul.domain.task.enums.TaskKind;
import com.naleul.naleul.domain.task.enums.TaskPriority;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Task 중요도 점수 (0~100, 명세 6-3 ①).
 *
 * | 요소                                   | 점수                   |
 * | 우선순위 A/B/C/D/E                     | 30/24/18/12/6          |
 * | 마감: 지남/오늘/내일/3일 이내/7일 이내   | +45/+40/+30/+20/+10    |
 * | 약속(APPOINTMENT)인데 시간 없음          | +50                    |
 * | 이월 횟수 × 8 (최대 24)                 | +0~24                  |
 * | 연결 마일스톤 마감 7일 이내             | +10                    |
 *
 * "마감까지 남은 날"은 배치하려는 날짜(planDate) 기준이다.
 * 이월된 Task 는 점수가 낮아도 등급을 최소 MID 로 올린다 (효율 낮은 시간대에 다시 넣지 않기 위해).
 */
public final class ImportanceScorer {

    private ImportanceScorer() {
    }

    public static Importance score(TaskPriority priority, LocalDate dueDate, TaskKind kind,
                                   int carryOverCount, LocalDate milestoneDueDate, LocalDate planDate) {
        int score = priorityScore(priority);
        ReasonCode cause = null;
        int causeValue = 0;
        int causeWeight = 0;

        if (dueDate != null) {
            long days = ChronoUnit.DAYS.between(planDate, dueDate);
            int add;
            ReasonCode code;
            if (days < 0) { add = 45; code = ReasonCode.OVERDUE; }
            else if (days == 0) { add = 40; code = ReasonCode.DEADLINE_TODAY; }
            else if (days == 1) { add = 30; code = ReasonCode.DEADLINE_SOON; }
            else if (days <= 3) { add = 20; code = ReasonCode.DEADLINE_SOON; }
            else if (days <= 7) { add = 10; code = ReasonCode.DEADLINE_SOON; }
            else { add = 0; code = null; }
            score += add;
            if (code != null && add > causeWeight) { cause = code; causeValue = (int) Math.max(days, 0); causeWeight = add; }
        }

        if (kind == TaskKind.APPOINTMENT) {
            score += 50;
            if (50 > causeWeight) { cause = ReasonCode.APPOINTMENT; causeValue = 0; causeWeight = 50; }
        }

        if (carryOverCount > 0) {
            int add = Math.min(carryOverCount * 8, 24);
            score += add;
            // 이월은 마감보다 우선순위가 낮아도 사용자가 알아야 할 정보라 가중치를 조금 올려 비교
            if (add + 10 > causeWeight) { cause = ReasonCode.CARRIED_OVER; causeValue = carryOverCount; causeWeight = add + 10; }
        }

        if (milestoneDueDate != null) {
            long days = ChronoUnit.DAYS.between(planDate, milestoneDueDate);
            if (days >= 0 && days <= 7) {
                score += 10;
                if (10 > causeWeight) { cause = ReasonCode.MILESTONE_SOON; causeValue = (int) days; causeWeight = 10; }
            }
        }

        score = Math.min(score, 100);
        ImportanceLevel level = ImportanceLevel.of(score);
        // 못 해서 넘어온 일은 다시 "집중이 덜 되는 시간"에 넣으면 또 못 할 가능성이 높다 → 최소 MID
        if (carryOverCount > 0 && level == ImportanceLevel.LOW) level = ImportanceLevel.MID;
        return new Importance(score, level, cause, causeValue);
    }

    static int priorityScore(TaskPriority p) {
        if (p == null) return 18;
        return switch (p) {
            case A -> 30;
            case B -> 24;
            case C -> 18;
            case D -> 12;
            case E -> 6;
        };
    }
}
