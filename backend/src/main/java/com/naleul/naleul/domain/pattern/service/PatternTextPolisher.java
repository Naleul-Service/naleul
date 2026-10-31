package com.naleul.naleul.domain.pattern.service;

import com.naleul.naleul.domain.pattern.client.PatternAiClient;
import com.naleul.naleul.domain.pattern.client.PatternAiClient.Text;
import com.naleul.naleul.domain.pattern.dto.PatternReportResponse;
import com.naleul.naleul.domain.pattern.enums.PatternAiStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 리포트 문장을 AI 로 다듬는다 (명세 4 · 5장).
 * 대상: 실행 스타일 근거 3줄, 요일별 문장, 하단 제안 문장.
 * 문장마다 PatternTextGuard 를 통과한 것만 바꾸고, 하나라도 바뀌면 aiStatus = AI.
 * AI 호출이 실패하거나 꺼져 있으면 받은 리포트를 그대로 돌려준다 (TEMPLATE).
 */
@Slf4j
@Component
public class PatternTextPolisher {

    private final PatternAiClient client;
    private final boolean enabled;

    public PatternTextPolisher(PatternAiClient client, @Value("${pattern.ai.enabled:true}") boolean enabled) {
        this.client = client;
        this.enabled = enabled;
    }

    public PatternReportResponse polish(PatternReportResponse r) {
        if (!enabled) return r;
        List<Text> texts = collect(r);
        if (texts.isEmpty()) return r;

        Map<String, String> polished;
        try {
            polished = client.polish(r.style() != null ? r.style().title() : null, texts);
        } catch (RuntimeException e) {
            log.warn("[PatternAi] 문장 다듬기 실패, 템플릿 사용: {}", e.toString());
            return r;
        }

        int[] changed = {0};
        java.util.function.BiFunction<String, String, String> pick = (id, original) -> {
            String p = polished.get(id);
            if (p == null || p.equals(original)) return original;
            if (!PatternTextGuard.accept(original, p)) {
                log.info("[PatternAi] 검증 실패로 템플릿 사용 id={}", id);
                return original;
            }
            changed[0]++;
            return p;
        };

        PatternReportResponse.Style style = r.style();
        if (style != null && style.insights() != null) {
            List<PatternReportResponse.Insight> insights = new ArrayList<>();
            for (int i = 0; i < style.insights().size(); i++) {
                PatternReportResponse.Insight in = style.insights().get(i);
                insights.add(new PatternReportResponse.Insight(in.key(), in.icon(), pick.apply("insight:" + i, in.text())));
            }
            style = new PatternReportResponse.Style(style.timeStrength(), style.rhythm(), style.title(), style.highlight(),
                    insights, style.remainingForStyle(), style.progress());
        }
        PatternReportResponse.Weekday weekday = r.weekday();
        if (weekday != null && weekday.insight() != null) {
            weekday = new PatternReportResponse.Weekday(weekday.average(), weekday.days(), weekday.lowest(),
                    weekday.lowestMissed(), pick.apply("weekday", weekday.insight()));
        }
        PatternReportResponse.SuggestionBar suggestion = r.suggestion();
        if (suggestion != null && suggestion.text() != null) {
            suggestion = new PatternReportResponse.SuggestionBar(pick.apply("suggestion", suggestion.text()),
                    suggestion.expectedGain(), suggestion.items());
        }
        if (changed[0] == 0) return r;

        return new PatternReportResponse(r.period(), r.range(), r.timeBlockCount(), r.evaluatedCount(), r.generatedAt(),
                PatternAiStatus.AI, style, r.summary(), r.heatmap(), weekday, r.weekly(), r.timeUsage(),
                r.blockKeeping(), r.routines(), r.taskHabits(), suggestion);
    }

    static List<Text> collect(PatternReportResponse r) {
        List<Text> out = new ArrayList<>();
        if (r.style() != null && r.style().insights() != null) {
            for (int i = 0; i < r.style().insights().size(); i++) {
                out.add(new Text("insight:" + i, r.style().insights().get(i).text()));
            }
        }
        if (r.weekday() != null && r.weekday().insight() != null) out.add(new Text("weekday", r.weekday().insight()));
        if (r.suggestion() != null && r.suggestion().text() != null) out.add(new Text("suggestion", r.suggestion().text()));
        return out;
    }
}
