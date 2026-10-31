package com.naleul.naleul.domain.goalCreation.dto.request;

import com.naleul.naleul.domain.goalCreation.dto.slot.DeadlineSlot;
import com.naleul.naleul.domain.goalCreation.dto.slot.MetricSlot;
import com.naleul.naleul.domain.goalCreation.dto.slot.PracticePreferenceSlot;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 정리 확인 화면에서 칩을 눌러 고친 슬롯만 보낸다 (PATCH).
 * 예) { "deadline": { "endDate": "2027-01-31", "isFlexible": false } }
 * null인 필드는 "안 바꿈".
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoalSlotsUpdateRequest {

    @Size(max = 100, message = "목표는 100자 이하로 입력해주세요.")
    private String goalStatement;

    private MetricSlot metric;

    @Size(max = 500, message = "이유는 500자 이하로 입력해주세요.")
    private String motivation;

    private DeadlineSlot deadline;

    /** V52: 어떻게 이루고 싶은지 · 정해 둔 계획 */
    @Size(max = 500, message = "계획은 500자 이하로 입력해주세요.")
    private String approach;

    private PracticePreferenceSlot practicePreference;

    /** 꼭 하고 싶은 일 (V42) — null 이면 안 바꿈, 빈 목록이면 지움 */
    @Size(max = 10, message = "꼭 할 일은 5개까지 적을 수 있어요.")
    private List<String> mustDoItems;
}
