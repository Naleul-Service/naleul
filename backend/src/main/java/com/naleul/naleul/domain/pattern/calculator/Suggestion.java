package com.naleul.naleul.domain.pattern.calculator;

import com.naleul.naleul.domain.pattern.enums.PatternSuggestionType;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

/**
 * 제안 1개 (명세 4 · 4-1). before/after 는 종류에 따라 시각 또는 요일만 채워진다.
 * @param gain   전체 실행률 기대 상승분 (%p, 반올림 전)
 * @param phrase 문장 조각 ("**토익 RC를 평일 12:40으로**") — 뒤에 "고정하고/옮기면"이 붙는다
 */
public record Suggestion(String key, PatternSuggestionType type, Long routineId, String routineName,
                         LocalTime beforeStart, LocalTime beforeEnd, LocalTime afterStart, LocalTime afterEnd,
                         List<DayOfWeek> beforeDays, List<DayOfWeek> afterDays,
                         double gain, String phrase) {
}
