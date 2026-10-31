package com.naleul.naleul.domain.goalCreation.client;

import com.fasterxml.jackson.annotation.JsonCreator;

import com.naleul.naleul.domain.goalCreation.dto.slot.DeadlineSlot;
import com.naleul.naleul.domain.goalCreation.dto.slot.MetricSlot;
import com.naleul.naleul.domain.goalCreation.dto.slot.PracticePreferenceSlot;
import com.naleul.naleul.domain.goalCreation.dto.slot.SlotKey;

import java.util.List;

/** 이번 턴에 AI가 사용자 답변에서 뽑아낸 값. null = 이번 턴에 새로 알게 된 것 없음 */
public record ExtractedSlots(
        String goalStatement,
        MetricSlot metric,
        String motivation,
        DeadlineSlot deadline,
        PracticePreferenceSlot practicePreference,
        List<SlotKey> skippedSlots,
        /** V52: 어떻게 이루고 싶은지 · 정해 둔 계획 */
        String approach
) {
    @JsonCreator
    public ExtractedSlots {
        skippedSlots = skippedSlots != null ? skippedSlots : List.of();
    }

    /** approach 가 없던 예전 코드 · 테스트 호환 */
    public ExtractedSlots(String goalStatement, MetricSlot metric, String motivation, DeadlineSlot deadline,
                          PracticePreferenceSlot practicePreference, List<SlotKey> skippedSlots) {
        this(goalStatement, metric, motivation, deadline, practicePreference, skippedSlots, null);
    }

    public static ExtractedSlots none() {
        return new ExtractedSlots(null, null, null, null, null, List.of(), null);
    }
}
