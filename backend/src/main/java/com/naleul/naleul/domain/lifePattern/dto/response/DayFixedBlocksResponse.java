package com.naleul.naleul.domain.lifePattern.dto.response;

import com.naleul.naleul.domain.lifePattern.support.DayFixedBlocks;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 하루치 고정 블록 + 하루 범위 (화면 기본 표시 범위 = 수면 종료 ~ 다음 수면 시작).
 * BE-4 의 /timetable 응답 days[].fixedBlocks / dayRange 에 그대로 재사용한다.
 */
public record DayFixedBlocksResponse(
        LocalDate date,
        DayRange dayRange,
        List<FixedBlockResponse> fixedBlocks
) {
    public record DayRange(LocalDateTime start, LocalDateTime end) {
    }

    public static DayFixedBlocksResponse from(DayFixedBlocks d) {
        return new DayFixedBlocksResponse(
                d.date(),
                new DayRange(d.dayRange().start(), d.dayRange().end()),
                d.displayBlocks().stream().map(FixedBlockResponse::from).toList());
    }
}
