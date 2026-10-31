package com.naleul.naleul.domain.actualTask.dto.request;

import java.time.LocalDateTime;

/**
 * 실제로 한 시간 기록 + 완료.
 * 한 번에 했으면 actualStartAt · actualEndAt, 여러 번 나눠서 했으면 segments
 * (예: 계획 13~18시 → 실제 13~15시, 16~18시). segments 가 있으면 그걸 쓴다.
 */
public record TaskUpdateActualRequest(

        LocalDateTime actualStartAt,
        LocalDateTime actualEndAt,
        java.util.List<Segment> segments
) {
    public TaskUpdateActualRequest(LocalDateTime actualStartAt, LocalDateTime actualEndAt) {
        this(actualStartAt, actualEndAt, null);
    }

    public record Segment(LocalDateTime start, LocalDateTime end) {
    }
}