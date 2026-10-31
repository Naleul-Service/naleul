package com.naleul.naleul.domain.planner.engine;

/**
 * Task 중요도 점수 결과.
 *
 * @param cause      문장 앞부분에 쓸 가장 큰 이유 (없으면 null)
 * @param causeValue cause 에 들어갈 숫자 (마감까지 남은 날 수, 이월 횟수 등)
 */
public record Importance(int score, ImportanceLevel level, ReasonCode cause, int causeValue) {
}
