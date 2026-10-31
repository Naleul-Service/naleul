package com.naleul.naleul.domain.pattern.calculator;

import java.util.List;

/**
 * 블록을 어떻게 지키나요? (명세 4 · 2-6)
 * onTime + late + missed = 100 (블록이 없으면 모두 null)
 * items = 루틴 · Task 별로 제시간 · 늦게 · 놓침 횟수 — 화면에서 "놓침"을 누르면 어떤 루틴을 놓쳤는지 걸러 봐요
 */
public record BlockKeepingStats(int total, Integer onTime, Integer late, Integer missed, List<FrequentMove> frequentMoves,
                                List<KeepItem> items) {

    /**
     * @param key     루틴이면 "R{routineId}", 일회성 Task 면 "T{이름}"
     * @param routine 루틴인지
     */
    public record KeepItem(String key, String title, String emoji, String goalName, String goalColorCode,
                           boolean routine, int onTime, int late, int missed) {
        public int total() {
            return onTime + late + missed;
        }
    }

    /**
     * @param fromLabel "08:00" 또는 요일 이동이면 "목"
     * @param toLabel   "12:40" 또는 "금"
     */
    public record FrequentMove(Long routineId, String title, String emoji, String fromLabel, String toLabel,
                               boolean dayChange, String description, int count) {
    }
}
