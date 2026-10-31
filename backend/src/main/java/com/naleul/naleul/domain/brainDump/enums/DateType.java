package com.naleul.naleul.domain.brainDump.enums;

/**
 * Brain dump 날짜 해석 결과.
 * ON  : 그날 할 일 (오늘, 내일, 금요일에)
 * DUE : 그날까지 끝낼 일 (금요일까지, 이번 주) — 실행일은 배치 엔진이 고른다
 * NONE: 날짜 표현 없음 → 오늘 할 일로 취급
 */
public enum DateType {
    ON, DUE, NONE
}
