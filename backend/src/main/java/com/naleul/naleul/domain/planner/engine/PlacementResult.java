package com.naleul.naleul.domain.planner.engine;

import com.naleul.naleul.domain.planner.support.DateTimeRange;

import java.time.LocalDateTime;
import java.util.List;

public record PlacementResult(List<Placed> placed, List<Unplaced> unplaced) {

    /**
     * @param start    첫 구간 시작
     * @param end      마지막 구간 끝
     * @param segments 연속된 빈 시간이 없어 나눠 넣었을 때만 각 구간 (예: 09~12시, 13~14시). 한 번에 넣었으면 빈 목록
     */
    public record Placed(Long taskId, LocalDateTime start, LocalDateTime end,
                         List<ReasonCode> reasonCodes, String reasonText, List<DateTimeRange> segments) {
        public Placed(Long taskId, LocalDateTime start, LocalDateTime end, List<ReasonCode> reasonCodes, String reasonText) {
            this(taskId, start, end, reasonCodes, reasonText, List.of());
        }

        public boolean split() {
            return segments != null && segments.size() > 1;
        }

        public String reasonCodesCsv() {
            return String.join(",", reasonCodes.stream().map(Enum::name).toList());
        }
    }

    public enum UnplacedCause {
        NO_SLOT,  // 빈 시간이 모자람
        TOO_LONG  // 가장 긴 빈 구간보다 Task 가 김
    }

    public record Unplaced(Long taskId, UnplacedCause cause) {
    }
}
