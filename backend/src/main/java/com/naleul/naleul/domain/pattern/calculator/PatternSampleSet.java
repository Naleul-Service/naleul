package com.naleul.naleul.domain.pattern.calculator;

import com.naleul.naleul.domain.pattern.entity.BrainDumpLog;
import com.naleul.naleul.domain.pattern.entity.TaskScheduleLog;

import java.util.List;

/**
 * 리포트 계산 입력 전체 (DB 조회 결과를 한 번에 묶은 것).
 * 계산기들은 이것만 받는 순수 함수라 같은 입력이면 같은 결과가 나온다.
 *
 * @param tasks             평가 대상 Task (1-1 조건)
 * @param carryMisses       기간 안의 날짜에서 이월된 기록 (V35 이후)
 * @param legacyCarryMisses V35 이전 이월 횟수 합 (날짜를 몰라 전체 실행률 분모에만 더함)
 * @param scheduleLogs      기간 앞뒤 하루를 포함해 생성된 일정 변경 기록 (시간순)
 * @param brainDumpLogs     기간 안의 Brain dump 확정 기록
 * @param lifePatterns      현재 생활 패턴
 */
public record PatternSampleSet(
        PatternRange range,
        List<PatternTaskSample> tasks,
        List<CarryMissSample> carryMisses,
        int legacyCarryMisses,
        List<TaskScheduleLog> scheduleLogs,
        List<BrainDumpLog> brainDumpLogs,
        List<LifePatternSample> lifePatterns
) {
    public PatternSampleSet {
        tasks = tasks != null ? List.copyOf(tasks) : List.of();
        carryMisses = carryMisses != null ? List.copyOf(carryMisses) : List.of();
        scheduleLogs = scheduleLogs != null ? List.copyOf(scheduleLogs) : List.of();
        brainDumpLogs = brainDumpLogs != null ? List.copyOf(brainDumpLogs) : List.of();
        lifePatterns = lifePatterns != null ? List.copyOf(lifePatterns) : List.of();
        legacyCarryMisses = Math.max(legacyCarryMisses, 0);
    }

    public int timeBlockCount() {
        return (int) tasks.stream().filter(PatternTaskSample::timed).count();
    }
}
