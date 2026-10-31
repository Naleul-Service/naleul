package com.naleul.naleul.domain.pattern.service;

import com.naleul.naleul.domain.pattern.client.PatternAiClient;
import com.naleul.naleul.domain.pattern.dto.PatternReportResponse;
import com.naleul.naleul.domain.pattern.enums.PatternAiStatus;
import com.naleul.naleul.domain.pattern.enums.PatternPeriod;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PatternTextPolisherTest {

    private static final String SUGGESTION =
            "아침 블록을 자주 놓치고 저녁에 강한 패턴이에요. **토익 RC를 평일 19:00으로** 고정하면 실행률이 **약 6%p** 오를 것으로 예상돼요.";
    private static final String INSIGHT = "저녁 18–24시 실행률이 **91%**로 아침(57%)보다 훨씬 높아요.";

    private static PatternReportResponse report() {
        return new PatternReportResponse(PatternPeriod.RECENT_4W,
                new PatternReportResponse.Range(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 29), 28),
                10, 10, LocalDateTime.of(2026, 11, 30, 9, 0), PatternAiStatus.TEMPLATE,
                new PatternReportResponse.Style(null, null, "저녁에 강한 꾸준형", "저녁에 강한",
                        List.of(new PatternReportResponse.Insight("TIME_STRENGTH", "moon", INSIGHT)), null),
                null, null,
                new PatternReportResponse.Weekday(84, List.of(), null, null, "목요일 실행률이 가장 낮아요."),
                null, null, null, null, null,
                new PatternReportResponse.SuggestionBar(SUGGESTION, 6, List.of()));
    }

    @Test
    void 검증을_통과한_문장만_바꾸고_AI로_표시() {
        PatternAiClient ai = (ctx, texts) -> {
            Map<String, String> m = new HashMap<>();
            m.put("insight:0", "저녁 18–24시엔 실행률이 **91%**예요. 아침(57%)보다 훨씬 높아요.");     // 통과
            m.put("weekday", "금요일 실행률이 가장 낮아요.");                                          // 요일 바뀜 → 거절
            m.put("suggestion", "**토익 RC를 평일 19:00으로** 옮겨보세요. 실행률이 **약 10%p** 오를 거예요."); // 숫자 바뀜 → 거절
            return m;
        };
        PatternReportResponse out = new PatternTextPolisher(ai, true).polish(report());

        assertThat(out.aiStatus()).isEqualTo(PatternAiStatus.AI);
        assertThat(out.style().insights().get(0).text()).isEqualTo("저녁 18–24시엔 실행률이 **91%**예요. 아침(57%)보다 훨씬 높아요.");
        assertThat(out.weekday().insight()).isEqualTo("목요일 실행률이 가장 낮아요.");
        assertThat(out.suggestion().text()).isEqualTo(SUGGESTION);
    }

    @Test
    void AI가_실패하거나_그대로면_템플릿() {
        PatternAiClient broken = (ctx, texts) -> { throw new IllegalStateException("timeout"); };
        assertThat(new PatternTextPolisher(broken, true).polish(report()).aiStatus()).isEqualTo(PatternAiStatus.TEMPLATE);

        PatternAiClient echo = (ctx, texts) -> {
            Map<String, String> m = new HashMap<>();
            texts.forEach(t -> m.put(t.id(), t.text()));
            return m;
        };
        PatternReportResponse same = report();
        assertThat(new PatternTextPolisher(echo, true).polish(same) == same).isTrue();
    }

    @Test
    void 꺼져_있으면_호출하지_않는다() {
        PatternAiClient fail = (ctx, texts) -> { throw new AssertionError("호출되면 안 됨"); };
        assertThat(new PatternTextPolisher(fail, false).polish(report()).aiStatus()).isEqualTo(PatternAiStatus.TEMPLATE);
    }

    @Test
    void 검증_규칙() {
        assertThat(PatternTextGuard.accept(INSIGHT, "저녁 18–24시 실행률이 **91%**로, 아침(57%)보다 훨씬 높아요!")).isTrue();
        assertThat(PatternTextGuard.accept(INSIGHT, "저녁 실행률이 **91%**로 아침(57%)보다 훨씬 높아요.")).isFalse(); // 18–24 빠짐
        assertThat(PatternTextGuard.accept(INSIGHT, "저녁 18–24시 실행률이 91%로 아침(57%)보다 훨씬 높아요.")).isFalse(); // 강조 빠짐
        assertThat(PatternTextGuard.accept(INSIGHT, INSIGHT + " ".repeat(10) + "피곤해서 아침엔 잘 못 하는 것 같아요. 저녁에 집중해보세요!!"))
                .isFalse(); // 너무 김
    }
}
