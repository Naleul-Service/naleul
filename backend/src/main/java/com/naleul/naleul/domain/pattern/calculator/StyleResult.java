package com.naleul.naleul.domain.pattern.calculator;

import com.naleul.naleul.domain.pattern.enums.StyleRhythm;
import com.naleul.naleul.domain.pattern.enums.StyleTimeStrength;

import java.util.List;

/**
 * 실행 스타일 (명세 4 · 3장). 표본이 부족하면 StyleClassifier 가 null 대신 remaining 만 채운 결과를 준다.
 * @param highlight   제목에서 강조할 부분 (= 시간대 수식어)
 * @param remaining   스타일을 보여주려면 더 필요한 Task 수 (충분하면 null)
 */
public record StyleResult(StyleTimeStrength timeStrength, StyleRhythm rhythm, String title, String highlight,
                          List<Insight> insights, Integer remaining, Progress progress) {

    public StyleResult(StyleTimeStrength timeStrength, StyleRhythm rhythm, String title, String highlight,
                       List<Insight> insights, Integer remaining) {
        this(timeStrength, rhythm, title, highlight, insights, remaining, null);
    }

    /**
     * 스타일을 열기까지의 진행 (화면의 진행 바). 예) Task 5/20개 · 3/7일
     * @param evaluated     지금까지 평가된 Task 수
     * @param activeDays    1개라도 실행한 날 수
     * @param requiredTasks 필요한 Task 수
     * @param requiredDays  필요한 실행한 날 수
     */
    public record Progress(int evaluated, int activeDays, int requiredTasks, int requiredDays) {
        public int remainingTasks() {
            return Math.max(requiredTasks - evaluated, 0);
        }

        public int remainingDays() {
            return Math.max(requiredDays - activeDays, 0);
        }
    }

    public boolean ready() {
        return remaining == null;
    }

    /** 근거 한 줄. text 의 **…** 는 화면에서 강조 */
    public record Insight(String key, String icon, String text, double score) {
    }
}
