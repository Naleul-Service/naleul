package com.naleul.naleul.domain.planner.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 하루 배치 결과 (화면 3단계 "TimeBlock 확인", 22시 요약 문구에 쓰임).
 *
 * placed            : 그날 배치된 블록
 * movedToOtherDays  : 빈 시간이 부족해 다른 날로 옮겨 배치된 블록
 * unscheduled       : 어디에도 못 넣어 날짜만 남은 Task ("시간 미정")
 */
public record PlacementOutcome(LocalDate date,
                               List<PlacedBlock> placed,
                               List<PlacedBlock> movedToOtherDays,
                               List<Long> unscheduled) {

    public record PlacedBlock(Long taskId, LocalDate date, LocalDateTime start, LocalDateTime end, String reason) {
    }

    public static PlacementOutcome empty(LocalDate date) {
        return new PlacementOutcome(date, List.of(), List.of(), List.of());
    }

    static PlacementOutcome mutable(LocalDate date) {
        return new PlacementOutcome(date, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    public int totalPlaced() {
        return placed.size() + movedToOtherDays.size();
    }
}
