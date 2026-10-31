package com.naleul.naleul.domain.aiUsage.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 오늘 쓸 수 있는 횟수가 정해진 기능의 사용량 — 화면에서 "오늘 3/5회 남음"처럼 미리 알려주려고.
 *
 * @param pro       Pro 한도를 쓰는 사용자인지
 * @param resetsAt  다시 채워지는 시각 (내일 0시, 한국 시간)
 * @param items     기능별 사용량
 * @param session   sessionId 를 넘겼을 때만: 그 목표 대화에서 계획을 다시 만들 수 있는 횟수
 */
public record AiUsageResponse(boolean pro, LocalDateTime resetsAt, List<Item> items, Item session) {

    /**
     * @param key       GOAL_SESSION · GOAL_DRAFT · TASK_PER_DAY · SESSION_REGENERATE
     * @param limit     null 이면 제한 없음
     * @param remaining null 이면 제한 없음
     */
    public record Item(String key, String label, String description, long used, Integer limit, Integer remaining) {
        public static Item of(String key, String label, String description, long used, Integer limit) {
            return new Item(key, label, description, used, limit,
                    limit == null ? null : (int) Math.max(0, limit - used));
        }
    }
}
