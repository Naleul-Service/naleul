package com.naleul.naleul.domain.actualTask.service;

import com.naleul.naleul.domain.actualTask.dto.request.TaskUpdateActualRequest.Segment;
import com.naleul.naleul.global.exception.CustomException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskActualSegmentsTest {

    static final LocalDateTime D = LocalDateTime.of(2026, 10, 13, 0, 0);

    @Test
    void 나눠서_한_구간을_시작순으로_정렬한다() {
        List<Segment> out = TaskActualService.validSegments(List.of(
                new Segment(D.withHour(16), D.withHour(18)),
                new Segment(D.withHour(13), D.withHour(15))));
        assertThat(out).extracting(Segment::start).containsExactly(D.withHour(13), D.withHour(16));
    }

    @Test
    void 겹치거나_끝이_시작보다_빠르면_400() {
        assertThatThrownBy(() -> TaskActualService.validSegments(List.of(
                new Segment(D.withHour(13), D.withHour(15)),
                new Segment(D.withHour(14), D.withHour(16))))).isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> TaskActualService.validSegments(List.of(
                new Segment(D.withHour(15), D.withHour(13))))).isInstanceOf(CustomException.class);
    }
}
