package com.naleul.naleul.domain.planner.service;

import com.naleul.naleul.domain.goalCreation.dto.slot.GoalSlots;
import com.naleul.naleul.domain.goalCreation.dto.slot.PracticePreferenceSlot;
import com.naleul.naleul.domain.goalCreation.dto.slot.TimeRange;
import com.naleul.naleul.domain.goalCreation.support.GoalJsonConverter;
import com.naleul.naleul.domain.planner.engine.HourlyEfficiency;
import com.naleul.naleul.domain.planner.engine.HourlyEfficiencyCalculator;
import com.naleul.naleul.domain.planner.engine.HourlyEfficiencyCalculator.Sample;
import com.naleul.naleul.domain.planner.engine.PatternEvidence;
import com.naleul.naleul.domain.pattern.entity.TaskScheduleLog;
import com.naleul.naleul.domain.pattern.enums.TaskScheduleAction;
import com.naleul.naleul.domain.pattern.repository.TaskScheduleLogRepository;
import com.naleul.naleul.domain.planner.repository.PlacementEvidenceRepository;
import com.naleul.naleul.domain.planner.engine.PlacementCandidate.Window;
import com.naleul.naleul.domain.planner.repository.PlacementQueryRepository;
import com.naleul.naleul.domain.task.enums.TaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 배치에 쓰는 "사용자 패턴" 데이터를 모은다.
 *  - 시간대별 효율 (최근 28일)
 *  - 루틴별 실제 소요 시간 (최근 28일)
 *  - 목표별 선호 시간대 (목표 생성 인터뷰)
 *  - 내 기록 근거 (시간대별 · 목표별 "계획 n개 중 m개 완료", 옮기기 습관) — 배치 점수와 이유 문장에 쓴다
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlacementContextLoader {

    static final int LOOKBACK_DAYS = 28;
    /** 실제 소요 시간이 계획보다 이 비율 이상 길면 늘린다 */
    static final double DURATION_ADJUST_THRESHOLD = 1.2;
    /** 최대 1.5배까지만 늘린다 */
    static final double DURATION_ADJUST_CAP = 1.5;
    /** 중앙값을 믿을 수 있는 최소 기록 수 */
    static final int DURATION_MIN_RECORDS = 3;

    private final PlacementQueryRepository queryRepository;
    private final GoalJsonConverter jsonConverter;
    // 단위 테스트는 new PlacementContextLoader(null, null) 로 만들어 쓰므로 생성자를 늘리지 않고 주입 (없으면 근거 없이 배치)
    @Autowired(required = false)
    private PlacementEvidenceRepository evidenceRepository;
    @Autowired(required = false)
    private TaskScheduleLogRepository scheduleLogRepository;

    /**
     * 최근 4주 내 기록 원자료.
     *  - 시간대별 계획/완료 (+ 22시에 이월된 놓침도 그 시각의 놓침으로 — 나의 패턴과 같은 기준)
     *  - 목표별 시간대 계획/완료
     *  - 사용자가 블록을 어느 시간대에서 어느 시간대로 옮겼는지
     */
    public PatternEvidence evidence(Long userId, LocalDate today) {
        if (evidenceRepository == null) return PatternEvidence.empty();
        LocalDateTime from = today.minusDays(LOOKBACK_DAYS).atStartOfDay();
        LocalDateTime to = today.atStartOfDay();
        int[] planned = new int[24], done = new int[24], in = new int[24], out = new int[24];
        Map<Long, PatternEvidence.GoalHours> goals = new HashMap<>();
        try {
            for (Object[] r : evidenceRepository.findSamplesWithGoal(userId, from, to)) {
                int h = ((LocalDateTime) r[0]).getHour();
                boolean completed = r[1] == TaskStatus.COMPLETED;
                planned[h]++;
                if (completed) done[h]++;
                if (r[2] != null) {
                    PatternEvidence.GoalHours g = goals.computeIfAbsent((Long) r[2],
                            k -> new PatternEvidence.GoalHours((String) r[3], new int[24], new int[24]));
                    g.planned()[h]++;
                    if (completed) g.done()[h]++;
                }
            }
            if (scheduleLogRepository != null) {
                for (TaskScheduleLog l : scheduleLogRepository.findAllCreatedBetween(userId, from, to)) {
                    if (l.getAction() == TaskScheduleAction.CARRY_OVER && l.getFromStartAt() != null) {
                        planned[l.getFromStartAt().getHour()]++; // 이월 = 그 시각에 계획했는데 못 함
                    } else if (l.getAction() == TaskScheduleAction.USER_MOVE
                            && l.getFromStartAt() != null && l.getToStartAt() != null
                            && l.getFromStartAt().getHour() != l.getToStartAt().getHour()) {
                        out[l.getFromStartAt().getHour()]++;
                        in[l.getToStartAt().getHour()]++;
                    }
                }
            }
        } catch (RuntimeException e) {
            log.warn("[Placement] 기록 근거 읽기 실패 userId={} — 근거 없이 배치", userId, e);
            return PatternEvidence.empty();
        }
        return new PatternEvidence(planned, done, goals, in, out);
    }

    public HourlyEfficiency efficiency(Long userId, LocalDate today) {
        List<Object[]> rows = queryRepository.findEfficiencySamples(
                userId, today.minusDays(LOOKBACK_DAYS).atStartOfDay(), today.atStartOfDay());
        List<Sample> samples = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            samples.add(new Sample((LocalDateTime) r[0], r[1] == TaskStatus.COMPLETED));
        }
        return HourlyEfficiencyCalculator.calculate(samples);
    }

    /** routineId → 늘린 소요 시간(분). 늘릴 필요 없는 루틴은 맵에 없다 */
    public Map<Long, Integer> adjustedRoutineMinutes(Map<Long, Integer> plannedByRoutine, LocalDate today) {
        if (plannedByRoutine.isEmpty()) return Map.of();
        List<Object[]> rows = queryRepository.findRoutineActualMinutes(
                plannedByRoutine.keySet(), today.minusDays(LOOKBACK_DAYS).atStartOfDay());
        Map<Long, List<Long>> actuals = new HashMap<>();
        for (Object[] r : rows) {
            actuals.computeIfAbsent((Long) r[0], k -> new ArrayList<>()).add(((Number) r[1]).longValue());
        }
        Map<Long, Integer> result = new HashMap<>();
        actuals.forEach((routineId, list) -> {
            Integer planned = plannedByRoutine.get(routineId);
            Integer adjusted = adjust(planned, list);
            if (adjusted != null) result.put(routineId, adjusted);
        });
        return result;
    }

    static Integer adjust(Integer planned, List<Long> actualMinutes) {
        if (planned == null || planned <= 0 || actualMinutes.size() < DURATION_MIN_RECORDS) return null;
        List<Long> sorted = actualMinutes.stream().sorted().toList();
        double median = sorted.size() % 2 == 1
                ? sorted.get(sorted.size() / 2)
                : (sorted.get(sorted.size() / 2 - 1) + sorted.get(sorted.size() / 2)) / 2.0;
        if (median < planned * DURATION_ADJUST_THRESHOLD) return null;
        // 5분 단위로 올리고, 최대 1.5배를 넘지 않게 자른다
        int rounded = (int) (Math.ceil(median / 5.0) * 5);
        int cap = (int) Math.floor(planned * DURATION_ADJUST_CAP);
        int result = Math.min(rounded, cap);
        return result > planned ? result : null;
    }

    /** goalId → 선호 시간대 목록 (자정을 넘기는 시간대는 무시) */
    public Map<Long, List<Window>> preferredWindows(Collection<Long> goalIds) {
        if (goalIds.isEmpty()) return Map.of();
        Map<Long, List<Window>> result = new HashMap<>();
        for (Object[] r : queryRepository.findGoalSlotsJson(goalIds)) {
            Long goalId = (Long) r[0];
            try {
                GoalSlots slots = jsonConverter.toSlots((String) r[1]);
                if (slots == null || !slots.practicePreference().isFilled()) continue;
                PracticePreferenceSlot pref = slots.practicePreference().value();
                if (pref == null || pref.preferredTimeRanges() == null) continue;
                List<Window> windows = new ArrayList<>();
                for (TimeRange tr : pref.preferredTimeRanges()) {
                    if (tr != null && tr.start() != null && tr.end() != null && tr.end().isAfter(tr.start())) {
                        windows.add(new Window(tr.start(), tr.end()));
                    }
                }
                if (!windows.isEmpty()) result.put(goalId, windows);
            } catch (RuntimeException e) {
                // 오래된 세션 JSON 형식이 달라도 배치 자체는 계속한다
                log.warn("[Placement] 선호 시간 파싱 실패 goalId={} : {}", goalId, e.getMessage());
            }
        }
        return result;
    }
}
