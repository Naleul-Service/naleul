package com.naleul.naleul.domain.goalCreation.dto.slot;


import java.util.Arrays;
import java.util.Optional;

/**
 * 세션의 슬롯 전체. goal_creation_session.slots JSON 컬럼에 이 구조 그대로 저장되고,
 * API 응답의 "slots"로도 그대로 나간다.
 *
 * record라서 값을 바꾸면 항상 새 객체를 돌려준다(with...). 원본이 몰래 바뀌는 버그를 막기 위함.
 */
public record GoalSlots(
        Slot<String> goalStatement,
        Slot<MetricSlot> metric,
        Slot<String> motivation,
        Slot<DeadlineSlot> deadline,
        Slot<PracticePreferenceSlot> practicePreference,
        /** V52: 어떻게 이루고 싶은지 · 이미 정해 둔 계획이나 꼭 할 일 (예: "2주는 이론, 남은 기간은 기출 5회독") */
        Slot<String> approach
) {

    public static GoalSlots empty() {
        return new GoalSlots(Slot.empty(), Slot.empty(), Slot.empty(), Slot.empty(), Slot.empty(), Slot.empty());
    }

    /** approach 가 없던 예전 코드 · 테스트 호환 */
    public GoalSlots(Slot<String> goalStatement, Slot<MetricSlot> metric, Slot<String> motivation,
                     Slot<DeadlineSlot> deadline, Slot<PracticePreferenceSlot> practicePreference) {
        this(goalStatement, metric, motivation, deadline, practicePreference, Slot.empty());
    }

    /** JSON에 일부 슬롯이 빠져 있어도(null) EMPTY로 취급 (예전 세션 JSON 에는 approach 가 없다) */
    public GoalSlots {
        goalStatement = goalStatement != null ? goalStatement : Slot.empty();
        metric = metric != null ? metric : Slot.empty();
        motivation = motivation != null ? motivation : Slot.empty();
        deadline = deadline != null ? deadline : Slot.empty();
        practicePreference = practicePreference != null ? practicePreference : Slot.empty();
        approach = approach != null ? approach : Slot.empty();
    }

    public Slot<?> get(SlotKey key) {
        return switch (key) {
            case GOAL_STATEMENT -> goalStatement;
            case METRIC -> metric;
            case MOTIVATION -> motivation;
            case DEADLINE -> deadline;
            case PRACTICE_PREFERENCE -> practicePreference;
            case APPROACH -> approach;
        };
    }

    /** 기본 질문 순서상 아직 비어 있는 첫 슬롯 */
    public Optional<SlotKey> firstEmpty() {
        return Arrays.stream(SlotKey.values())
                .filter(k -> get(k).isEmpty())
                .findFirst();
    }

    public boolean hasGoalStatement() {
        return goalStatement.isFilled() && goalStatement.value() != null && !goalStatement.value().isBlank();
    }

    public GoalSlots withGoalStatement(Slot<String> s) {
        return new GoalSlots(s, metric, motivation, deadline, practicePreference, approach);
    }

    public GoalSlots withMetric(Slot<MetricSlot> s) {
        return new GoalSlots(goalStatement, s, motivation, deadline, practicePreference, approach);
    }

    public GoalSlots withMotivation(Slot<String> s) {
        return new GoalSlots(goalStatement, metric, s, deadline, practicePreference, approach);
    }

    public GoalSlots withDeadline(Slot<DeadlineSlot> s) {
        return new GoalSlots(goalStatement, metric, motivation, s, practicePreference, approach);
    }

    public GoalSlots withPracticePreference(Slot<PracticePreferenceSlot> s) {
        return new GoalSlots(goalStatement, metric, motivation, deadline, s, approach);
    }

    public GoalSlots withApproach(Slot<String> s) {
        return new GoalSlots(goalStatement, metric, motivation, deadline, practicePreference, s);
    }

    /** 해당 슬롯을 SKIPPED로 */
    public GoalSlots skip(SlotKey key) {
        return switch (key) {
            case GOAL_STATEMENT -> throw new IllegalArgumentException("goalStatement는 건너뛸 수 없음");
            case METRIC -> withMetric(Slot.skipped());
            case MOTIVATION -> withMotivation(Slot.skipped());
            case DEADLINE -> withDeadline(Slot.skipped());
            case PRACTICE_PREFERENCE -> withPracticePreference(Slot.skipped());
            case APPROACH -> withApproach(Slot.skipped());
        };
    }
}
