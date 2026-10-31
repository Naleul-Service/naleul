package com.naleul.naleul.domain.goalCreation.client;

import java.time.LocalDate;

/**
 * 목표 상세 "말로 고치기" AI 입력.
 * @param today       오늘 ("2주 늦춰줘" 같은 표현을 날짜로 바꾸려고)
 * @param goalContext 지금 목표 상태 JSON (목표·영역·루틴·점검 시점, 각 항목 id 포함)
 * @param instruction 사용자가 적은 한 문장 (최대 200자)
 */
public record GoalEditAiRequest(LocalDate today, String goalContext, String instruction) {
}
