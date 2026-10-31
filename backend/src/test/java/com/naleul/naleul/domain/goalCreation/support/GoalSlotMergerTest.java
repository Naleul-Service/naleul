package com.naleul.naleul.domain.goalCreation.support;

import com.naleul.naleul.domain.goalCreation.client.ExtractedSlots;
import com.naleul.naleul.domain.goalCreation.dto.slot.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GoalSlotMerger는 스프링/DB 의존이 없는 순수 로직이라 Mock 없이 바로 테스트.
 */
class GoalSlotMergerTest {

    private final GoalSlotMerger merger = new GoalSlotMerger();

    private static final MetricSlot METRIC_78_68 =
            new MetricSlot("체중", BigDecimal.valueOf(78), BigDecimal.valueOf(68), "kg");

    @Test
    void 한_답변에서_여러_슬롯이_뽑히면_모두_채운다() {
        // "살 10kg 빼고 싶어, 78에서 68로. 6개월 안에"
        ExtractedSlots extracted = new ExtractedSlots(
                "몸무게 10kg 감량", METRIC_78_68, null,
                new DeadlineSlot(LocalDate.of(2027, 4, 1), false), null, List.of());

        GoalSlots result = merger.merge(GoalSlots.empty(), extracted, SlotKey.GOAL_STATEMENT);

        assertThat(result.goalStatement().isFilled()).isTrue();
        assertThat(result.metric().value()).isEqualTo(METRIC_78_68);
        assertThat(result.deadline().isFilled()).isTrue();
        // 다음에 물을 슬롯은 metric/deadline이 아니라 approach (V52: 이유(motivation)는 묻지 않고 맨 뒤로)
        assertThat(result.firstEmpty()).contains(SlotKey.APPROACH);
    }

    @Test
    void 이미_채워진_슬롯은_방금_답한_슬롯이_아니면_덮어쓰지_않는다() {
        GoalSlots current = GoalSlots.empty().withMetric(Slot.filled(METRIC_78_68));
        MetricSlot aiReinterpreted = new MetricSlot("체중", BigDecimal.valueOf(80), BigDecimal.valueOf(70), "kg");
        ExtractedSlots extracted = new ExtractedSlots(null, aiReinterpreted, "건강검진", null, null, List.of());

        // 지금 답한 건 motivation 질문
        GoalSlots result = merger.merge(current, extracted, SlotKey.MOTIVATION);

        assertThat(result.metric().value()).isEqualTo(METRIC_78_68);
        assertThat(result.motivation().value()).isEqualTo("건강검진");
    }

    @Test
    void 방금_답한_슬롯이면_기존_값을_사용자가_고친_것으로_보고_덮어쓴다() {
        GoalSlots current = GoalSlots.empty().withMetric(Slot.filled(METRIC_78_68));
        MetricSlot corrected = new MetricSlot("체중", BigDecimal.valueOf(78), BigDecimal.valueOf(70), "kg");
        ExtractedSlots extracted = new ExtractedSlots(null, corrected, null, null, null, List.of());

        GoalSlots result = merger.merge(current, extracted, SlotKey.METRIC);

        assertThat(result.metric().value()).isEqualTo(corrected);
    }

    @Test
    void skippedSlots는_비어있는_슬롯만_SKIPPED로_바꾼다() {
        GoalSlots current = GoalSlots.empty().withMotivation(Slot.filled("예전 옷 입기"));
        ExtractedSlots extracted = new ExtractedSlots(null, null, null, null, null,
                List.of(SlotKey.METRIC, SlotKey.MOTIVATION));

        GoalSlots result = merger.merge(current, extracted, SlotKey.METRIC);

        assertThat(result.metric().status()).isEqualTo(SlotStatus.SKIPPED);
        assertThat(result.motivation().status()).isEqualTo(SlotStatus.FILLED);
    }

    @Test
    void goalStatement는_AI가_skippedSlots에_넣어도_SKIPPED가_되지_않는다() {
        ExtractedSlots extracted = new ExtractedSlots(null, null, null, null, null, List.of(SlotKey.GOAL_STATEMENT));

        GoalSlots result = merger.merge(GoalSlots.empty(), extracted, SlotKey.GOAL_STATEMENT);

        assertThat(result.goalStatement().status()).isEqualTo(SlotStatus.EMPTY);
    }

    @Test
    void 빈_문자열_goalStatement는_무시한다() {
        ExtractedSlots extracted = new ExtractedSlots("   ", null, null, null, null, List.of());

        GoalSlots result = merger.merge(GoalSlots.empty(), extracted, SlotKey.GOAL_STATEMENT);

        assertThat(result.hasGoalStatement()).isFalse();
    }

    @Test
    void approach_를_뽑으면_채운다_V52() {
        ExtractedSlots extracted = new ExtractedSlots(null, null, null, null, null, List.of(),
                "2주는 이론 강의, 남은 기간은 기출 5회독");

        GoalSlots result = merger.merge(GoalSlots.empty(), extracted, SlotKey.APPROACH);

        assertThat(result.approach().isFilled()).isTrue();
        assertThat(result.approach().value()).isEqualTo("2주는 이론 강의, 남은 기간은 기출 5회독");
    }

    @Test
    void 예전_세션_JSON_에_approach_가_없어도_EMPTY_로_읽는다_V52() throws Exception {
        String old = "{\"goalStatement\":{\"status\":\"FILLED\",\"value\":\"SQLD 합격\"},"
                + "\"metric\":{\"status\":\"EMPTY\",\"value\":null},"
                + "\"motivation\":{\"status\":\"SKIPPED\",\"value\":null},"
                + "\"deadline\":{\"status\":\"EMPTY\",\"value\":null},"
                + "\"practicePreference\":{\"status\":\"EMPTY\",\"value\":null}}";
        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper()
                .findAndRegisterModules()
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        GoalSlots slots = om.readValue(old, GoalSlots.class);

        assertThat(slots.goalStatement().value()).isEqualTo("SQLD 합격");
        assertThat(slots.approach().isEmpty()).isTrue();
        // 다시 써도 approach 가 들어간다
        assertThat(om.readValue(om.writeValueAsString(slots.withApproach(
                com.naleul.naleul.domain.goalCreation.dto.slot.Slot.filled("기출 위주"))), GoalSlots.class)
                .approach().value()).isEqualTo("기출 위주");
    }
}
