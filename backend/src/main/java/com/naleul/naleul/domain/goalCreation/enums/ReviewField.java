package com.naleul.naleul.domain.goalCreation.enums;

import com.naleul.naleul.domain.goalCreation.dto.slot.SlotKey;

/**
 * 정리 화면(goal_review)에서 AI 가 정리해 보여주는 항목 (V67, AI 채팅 정리 정확도).
 * 꼭 할 일(사용자가 시작 화면에서 고름) · 계획 강도(고정 기본값)는 AI 판단이 아니라 넣지 않는다.
 */
public enum ReviewField {
    GOAL_STATEMENT("목표", SlotKey.GOAL_STATEMENT, true),
    METRIC("수치", SlotKey.METRIC, true),
    DEADLINE("기한", SlotKey.DEADLINE, true),
    APPROACH("계획", SlotKey.APPROACH, true),
    /** 비어 있으면 화면에 줄이 안 보여 직접 채울 수 없다 → 누락률 제외 */
    MOTIVATION("이유", SlotKey.MOTIVATION, false),
    PRACTICE_PREFERENCE("실천 시간", SlotKey.PRACTICE_PREFERENCE, true),
    /** 항상 둘 중 하나가 골라져 있다 → 누락률 제외. 질문은 실천 시간을 물을 때 함께 한다 */
    PLANNING_STYLE("실행 성향", SlotKey.PRACTICE_PREFERENCE, false);

    private final String label;
    /** 이 항목을 물은 질문을 찾을 때 쓰는 슬롯 */
    private final SlotKey askedSlot;
    /** 누락률(AI 가 비워 둔 것을 사용자가 채움)을 재는 항목인지 */
    private final boolean missMeasured;

    ReviewField(String label, SlotKey askedSlot, boolean missMeasured) {
        this.label = label;
        this.askedSlot = askedSlot;
        this.missMeasured = missMeasured;
    }

    public String label() {
        return label;
    }

    public SlotKey askedSlot() {
        return askedSlot;
    }

    public boolean missMeasured() {
        return missMeasured;
    }
}
