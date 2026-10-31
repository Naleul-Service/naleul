package com.naleul.naleul.domain.goalCreation.client;

import java.time.LocalDate;

/**
 * 캘린더 "AI로 일정 조정" AI 입력.
 * @param today       오늘
 * @param context     지금 일정 JSON (루틴 전부 · 보고 있는 기간의 Task · 하루 범위 · 겹치는 루틴, 각 항목 id 포함)
 * @param instruction 사용자가 적은 문장 (최대 300자)
 */
public record ScheduleEditAiRequest(LocalDate today, String context, String instruction) {
}
