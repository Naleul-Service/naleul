package com.naleul.naleul.domain.planner.engine;

import com.naleul.naleul.domain.planner.support.DateTimeRange;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 배치 엔진 입력 (하루치).
 *
 * @param dayRange   배치 가능 범위 (수면 종료 ~ 다음 수면 시작)
 * @param notBefore  이 시각 이전에는 배치하지 않음 (오늘이면 "지금", 미래 날짜면 null)
 * @param fixedBusy  고정 블록 — 간격 없이 붙여서 배치 가능 (점심 끝나는 13:00 에 바로 시작 OK)
 * @param taskBusy   이미 자리를 차지한 Task (잠긴 Task, 완료된 Task, 유지할 엔진 배치) — 앞뒤로 간격을 둔다
 * @param gapMinutes Task 사이 간격 (기본 10분)
 * @param evidence   내 기록 원자료 (목표별 잘 지키는 시간대 · 옮기기 습관 · 이유 문장의 "n개 중 m개"). 없으면 empty
 */
public record PlacementInput(
        LocalDate date,
        DateTimeRange dayRange,
        LocalDateTime notBefore,
        List<DateTimeRange> fixedBusy,
        List<BusyTask> taskBusy,
        List<PlacementCandidate> candidates,
        HourlyEfficiency efficiency,
        int gapMinutes,
        PatternEvidence evidence
) {
    public PlacementInput(LocalDate date, DateTimeRange dayRange, LocalDateTime notBefore, List<DateTimeRange> fixedBusy,
                          List<BusyTask> taskBusy, List<PlacementCandidate> candidates, HourlyEfficiency efficiency,
                          int gapMinutes) {
        this(date, dayRange, notBefore, fixedBusy, taskBusy, candidates, efficiency, gapMinutes, PatternEvidence.empty());
    }

    public PatternEvidence evidenceOrEmpty() {
        return evidence != null ? evidence : PatternEvidence.empty();
    }

    /** 이미 자리를 차지한 Task. goalId 는 "같은 목표 연속 배치 피하기" 판단용 */
    public record BusyTask(DateTimeRange range, Long goalId) {
    }
}
