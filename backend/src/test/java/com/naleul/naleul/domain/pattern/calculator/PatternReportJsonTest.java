package com.naleul.naleul.domain.pattern.calculator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.naleul.naleul.domain.pattern.dto.PatternReportAssembler;
import com.naleul.naleul.domain.pattern.dto.PatternReportResponse;
import com.naleul.naleul.domain.pattern.enums.PatternAiStatus;
import com.naleul.naleul.domain.pattern.enums.PatternPeriod;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static com.naleul.naleul.domain.pattern.calculator.PatternFixtures.START;
import static org.assertj.core.api.Assertions.assertThat;

/** 응답을 JSON 으로 캐시했다가 다시 읽어도 같은 값인지 (pattern_report.payload_json) */
class PatternReportJsonTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void 캐시_JSON_왕복() throws Exception {
        PatternFixtures f = new PatternFixtures();
        for (LocalDate d = START; !d.isAfter(PatternFixtures.END); d = d.plusDays(1)) {
            f.block(d, 19, true);
            f.block(d, 8, d.getDayOfMonth() % 3 == 0);
            if (d.getDayOfWeek() == DayOfWeek.THURSDAY) f.task().routine(44, "근력 운동").on(d).done(false).add();
        }
        f.custom("회사 업무", 9, 30, 18, 0, 31);
        PatternSampleSet set = f.set();
        PatternStats stats = PatternCalculator.calculate(set);
        StyleResult style = StyleClassifier.classify(set, stats);
        PatternReportResponse original = PatternReportAssembler.assemble(PatternPeriod.RECENT_4W, stats, style, null,
                PatternAiStatus.TEMPLATE, LocalDateTime.of(2026, 11, 30, 9, 0));

        String json = mapper.writeValueAsString(original);
        PatternReportResponse back = mapper.readValue(json, PatternReportResponse.class);

        assertThat(back).isEqualTo(original);
        assertThat(json.contains("\"start\":\"2026-11-02\"")).isTrue();
        assertThat(json.contains("\"generatedAt\":\"2026-11-30T09:00:00\"")).isTrue();
        assertThat(json.contains("\"state\":\"FIXED\"")).isTrue();
        assertThat(json.contains("\"dayOfWeek\":\"MONDAY\"")).isTrue();
        assertThat(back.weekday().insight().contains("**근력 운동**을 가장 자주 놓쳤어요")).isTrue();
    }
}
