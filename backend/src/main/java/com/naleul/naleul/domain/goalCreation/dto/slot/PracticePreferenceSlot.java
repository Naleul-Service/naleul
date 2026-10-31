package com.naleul.naleul.domain.goalCreation.dto.slot;

import com.naleul.naleul.domain.goalCreation.dto.plan.Weekday;

import java.util.List;

/**
 * 예: { "preferredDays": ["MON","WED"], "preferredTimeRanges": [{"start":"19:00","end":"22:00"}],
 *       "unavailableNote": "평일 9~18시 회사", "workStyleAnswer": "몰아서 하는 편이에요" }
 */
public record PracticePreferenceSlot(
        List<Weekday> preferredDays,
        List<TimeRange> preferredTimeRanges,
        String unavailableNote,
        String workStyleAnswer
) {
}
