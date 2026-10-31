package com.naleul.naleul.domain.mission.enums;

public enum MissionBadgeType {
    ITEM_PUZZLE,      // 카테고리 항목별 퍼즐 뱃지 (레벨업 있음) — 기존 CATEGORY_PUZZLE
    FIRST_PUZZLE,     // 생애 첫 퍼즐 획득
    STREAK_3,         // 3일 연속 인증
    COUNT_10,         // 누적 인증 10회
    COUNT_50,         // 누적 인증 50회
    COUNT_100         // 누적 인증 100회
}