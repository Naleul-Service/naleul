package com.naleul.naleul.domain.goalOutcome.enums;

/**
 * "기한이 지났어요. 목표는 만족했나요?" 결과 확인 모달 상태 (V63).
 * 기간이 끝나 자동 판정(AUTO)된 목표만 PENDING 으로 시작한다. 사용자가 직접 마무리한 목표는 묻지 않는다(null).
 */
public enum OutcomePromptStatus {
    PENDING,    // 아직 안 물어봤거나 답을 안 함 → 앱을 열면 모달
    ANSWERED,   // 결과를 직접 골랐다
    DISMISSED   // "나중에" — 자동 판정 결과를 그대로 둔다 (다시 묻지 않음, 목표 상세에서는 고를 수 있음)
}
