package com.naleul.naleul.domain.goalCreation.support;

import com.naleul.naleul.domain.goalCreation.client.ExtractedSlots;
import com.naleul.naleul.domain.goalCreation.dto.slot.GoalSlots;
import com.naleul.naleul.domain.goalCreation.dto.slot.Slot;
import com.naleul.naleul.domain.goalCreation.dto.slot.SlotKey;
import org.springframework.stereotype.Component;

/**
 * AI가 뽑은 값(ExtractedSlots)을 현재 슬롯에 합친다 (명세 8-2 "서버 처리").
 *
 * 규칙
 *  1. 비어 있거나(EMPTY) 건너뛴(SKIPPED) 슬롯 → 새 값으로 FILLED
 *  2. 이미 FILLED인 슬롯 → "방금 사용자가 답한 슬롯"일 때만 덮어씀
 *     (예: metric을 물었는데 사용자가 "아 70까지로 할게요" → 사용자가 직접 고친 것이므로 반영)
 *     다른 슬롯은 AI가 이전 대화를 다시 해석해서 값을 바꿔버리는 것을 막는다.
 *  3. skippedSlots는 EMPTY인 슬롯만 SKIPPED로 (값이 있는 슬롯을 AI가 지우지 못하게)
 *  4. goalStatement는 절대 SKIPPED가 되지 않음
 *
 * DB나 스프링에 의존하지 않는 순수 로직이라 단위 테스트가 쉽다.
 */
@Component
public class GoalSlotMerger {

    public GoalSlots merge(GoalSlots current, ExtractedSlots extracted, SlotKey answeredSlot) {
        if (extracted == null) return current;
        GoalSlots result = current;

        if (extracted.goalStatement() != null && !extracted.goalStatement().isBlank()
                && canWrite(result.goalStatement(), SlotKey.GOAL_STATEMENT, answeredSlot)) {
            result = result.withGoalStatement(Slot.filled(extracted.goalStatement().strip()));
        }
        if (extracted.metric() != null && canWrite(result.metric(), SlotKey.METRIC, answeredSlot)) {
            result = result.withMetric(Slot.filled(extracted.metric()));
        }
        if (extracted.motivation() != null && !extracted.motivation().isBlank()
                && canWrite(result.motivation(), SlotKey.MOTIVATION, answeredSlot)) {
            result = result.withMotivation(Slot.filled(extracted.motivation().strip()));
        }
        if (extracted.approach() != null && !extracted.approach().isBlank()
                && canWrite(result.approach(), SlotKey.APPROACH, answeredSlot)) {
            result = result.withApproach(Slot.filled(extracted.approach().strip()));
        }
        if (extracted.deadline() != null && extracted.deadline().endDate() != null
                && canWrite(result.deadline(), SlotKey.DEADLINE, answeredSlot)) {
            result = result.withDeadline(Slot.filled(extracted.deadline()));
        }
        if (extracted.practicePreference() != null
                && canWrite(result.practicePreference(), SlotKey.PRACTICE_PREFERENCE, answeredSlot)) {
            result = result.withPracticePreference(Slot.filled(extracted.practicePreference()));
        }

        for (SlotKey key : extracted.skippedSlots()) {
            if (key != null && key.isSkippable() && result.get(key).isEmpty()) {
                result = result.skip(key);
            }
        }
        return result;
    }

    private boolean canWrite(Slot<?> slot, SlotKey key, SlotKey answeredSlot) {
        return !slot.isFilled() || key == answeredSlot;
    }
}
