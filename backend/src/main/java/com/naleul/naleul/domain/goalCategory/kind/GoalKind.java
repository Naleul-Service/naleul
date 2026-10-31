package com.naleul.naleul.domain.goalCategory.kind;

import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;

/**
 * 목표 종류 = 1단계(type) + 2단계(subType) + 직접 적은 이름(customLabel, "기타"일 때만).
 * 예) 건강 · 운동 / 건강 · 필라테스(기타) / 기타 · 악기 연습
 */
public record GoalKind(GoalType type, GoalSubType subType, String customLabel) {

    public static final int CUSTOM_LABEL_MAX = 10;

    /**
     * 요청 값 검증 후 만든다. 셋 다 비어 있으면 null (종류 미지정 — AI 가 목표 문장으로 판단).
     * - type 만 ETC 면 subType 은 OTHER 로 본다
     * - subType 은 type 에 속해야 한다
     * - "기타"(custom) 면 이름 1~10자 필수, 아니면 이름은 무시
     */
    public static GoalKind of(GoalType type, GoalSubType subType, String customLabel) {
        if (type == null && subType == null) return null;
        if (type == null) type = subType.getType();
        if (subType == null && type == GoalType.ETC) subType = GoalSubType.OTHER;
        if (subType == null || subType.getType() != type) {
            throw new CustomException(ErrorCode.INVALID_GOAL_KIND);
        }
        String label = null;
        if (subType.isCustom()) {
            label = customLabel == null ? null : customLabel.strip();
            if (label == null || label.isEmpty() || label.length() > CUSTOM_LABEL_MAX) {
                throw new CustomException(ErrorCode.INVALID_GOAL_KIND_LABEL);
            }
        }
        return new GoalKind(type, subType, label);
    }

    /** DB 에서 읽을 때 — 검증 없이 (예전 데이터가 깨져 있어도 화면은 떠야 하므로) */
    public static GoalKind restore(GoalType type, GoalSubType subType, String customLabel) {
        if (type == null && subType == null) return null;
        if (type == null) type = subType.getType();
        return new GoalKind(type, subType, customLabel);
    }

    /** 2단계 이름 — 기타면 사용자가 적은 이름 */
    public String subLabel() {
        if (subType == null) return null;
        return subType.isCustom() && customLabel != null ? customLabel : subType.getLabel();
    }

    /** "건강 · 운동", "기타 · 악기 연습" */
    public String displayName() {
        String sub = subLabel();
        return sub == null ? type.getLabel() : type.getLabel() + " · " + sub;
    }
}
