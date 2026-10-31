package com.naleul.naleul.domain.pattern.calculator;

import java.util.List;

/** 하단 제안 바 (명세 4 · 4장). expectedGain 이 1 미만이면 null */
public record SuggestionResult(String text, Integer expectedGain, List<Suggestion> items) {
}
