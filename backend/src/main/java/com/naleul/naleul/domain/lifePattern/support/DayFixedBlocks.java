package com.naleul.naleul.domain.lifePattern.support;

import com.naleul.naleul.domain.planner.support.DateTimeRange;

import java.time.LocalDate;
import java.util.List;

/**
 * 하루치 고정 블록 계산 결과.
 *
 * @param displayBlocks 그날 화면(00:00~24:00)에 보여줄 블록. 자정을 넘긴 블록은 잘라서 담는다
 * @param busyBlocks    배치 엔진용 원본 구간 (자르지 않음). 그날 하루 범위와 겹치는 모든 블록
 * @param dayRange      하루 범위 = 수면이 끝나는 시각 ~ 다음 수면이 시작하는 시각. 수면 패턴이 없으면 00:00~24:00
 * @param wakeSleep     이날 아침에 끝나는 수면 (끝 = 기상). 자르지 않은 원본. 없으면 null
 * @param bedSleep      이날 밤에 시작하는 수면 (시작 = 취침). 자르지 않은 원본. 없으면 null
 */
public record DayFixedBlocks(
        LocalDate date,
        List<FixedBlock> displayBlocks,
        List<FixedBlock> busyBlocks,
        DateTimeRange dayRange,
        FixedBlock wakeSleep,
        FixedBlock bedSleep
) {
    public DayFixedBlocks(LocalDate date, List<FixedBlock> displayBlocks, List<FixedBlock> busyBlocks,
                          DateTimeRange dayRange) {
        this(date, displayBlocks, busyBlocks, dayRange, null, null);
    }
}
