package com.naleul.naleul.domain.pattern.service;

import com.naleul.naleul.domain.lifePattern.entity.LifePattern;
import com.naleul.naleul.domain.lifePattern.repository.LifePatternRepository;
import com.naleul.naleul.domain.pattern.calculator.*;
import com.naleul.naleul.domain.pattern.entity.TaskScheduleLog;
import com.naleul.naleul.domain.pattern.enums.PatternPeriod;
import com.naleul.naleul.domain.pattern.enums.TaskScheduleAction;
import com.naleul.naleul.domain.pattern.repository.BrainDumpLogRepository;
import com.naleul.naleul.domain.pattern.repository.PatternQueryRepository;
import com.naleul.naleul.domain.pattern.repository.TaskScheduleLogRepository;
import com.naleul.naleul.domain.task.enums.TaskStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 리포트 계산 재료를 DB 에서 읽어 PatternSampleSet 으로 묶는다 (명세 4 · 8장).
 * 조회는 6~7번으로 끝나고, 나머지 계산은 자바에서 한다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PatternSampleLoader {

    /** MySQL IN 절 하나에 넣을 최대 id 수 */
    static final int IN_CHUNK = 1000;

    private final PatternQueryRepository patternQueryRepository;
    private final TaskScheduleLogRepository taskScheduleLogRepository;
    private final BrainDumpLogRepository brainDumpLogRepository;
    private final LifePatternRepository lifePatternRepository;

    public PatternSampleSet load(Long userId, PatternPeriod period, LocalDate today) {
        return load(userId, period, today, null);
    }

    /**
     * @param now 지금 시각. 있으면 오늘 시간이 이미 지난 미완료 블록을 "놓침"으로 바로 센다.
     *            (없으면 오늘 미완료는 모두 진행 중으로 보고 뺀다 — 22시 이월 뒤에야 놓침으로 잡혀 하루 늦게 반영되던 문제)
     */
    public PatternSampleSet load(Long userId, PatternPeriod period, LocalDate today, LocalDateTime now) {
        LocalDate firstDate = period == PatternPeriod.ALL ? firstDate(userId) : null;
        // 기간 끝은 어제지만, 오늘 "완료한" Task 는 바로 반영한다 (오늘 아직 안 한 Task 는 놓침으로 세지 않게 뺌)
        PatternRange base = PatternRange.of(period, today, firstDate);
        PatternRange range = new PatternRange(base.start(), today);
        LocalDateTime from = range.start().atStartOfDay();
        LocalDateTime to = range.end().plusDays(1).atStartOfDay();

        // 1) 평가 대상 Task
        List<Object[]> rows = patternQueryRepository.findEvaluatedTasks(userId, from, to, range.start(), range.end());
        List<Long> taskIds = rows.stream().map(r -> (Long) r[0]).toList();

        // 2) 실제 수행 기록
        Map<Long, Object[]> actuals = new HashMap<>();
        for (List<Long> chunk : chunks(taskIds)) {
            patternQueryRepository.findActualSummaries(chunk).forEach(a -> actuals.put((Long) a[0], a));
        }
        List<PatternTaskSample> tasks = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            PatternTaskSample sample = toSample(r, actuals.get((Long) r[0]));
            if (today.equals(sample.date()) && !sample.completed() && !endedBefore(sample, now)) continue; // 오늘 아직 진행 중
            tasks.add(sample);
        }

        // 3) 이월 놓침 (V35 이후) + 옛 이월 횟수
        List<CarryMissSample> carryMisses = taskScheduleLogRepository
                .findAllByActionAndFromDateBetween(userId, TaskScheduleAction.CARRY_OVER, range.start(), range.end())
                .stream()
                .map(l -> new CarryMissSample(l.getTaskId(), l.getRoutineId(), l.getFromDate(), l.getFromStartAt()))
                .toList();
        int legacy = legacyCarryMisses(tasks);

        // 4) 일정 변경 기록 — 엔진 배치는 전날 22시에 생기므로 하루 앞부터
        List<TaskScheduleLog> logs = taskScheduleLogRepository.findAllCreatedBetween(userId, from.minusDays(1), to);

        // 5) Brain dump, 6) 생활 패턴
        var brainDumps = brainDumpLogRepository.findAllConfirmedBetween(userId, from, to);
        List<LifePatternSample> patterns = lifePatternRepository.findAllByUserId(userId).stream()
                .filter(p -> !p.isOneOff())   // 이날만 패턴은 매주 생활 패턴 분석에서 뺀다
                .map(PatternSampleLoader::toPattern)
                .toList();

        return new PatternSampleSet(range, tasks, carryMisses, legacy, logs, brainDumps, patterns);
    }

    /** 시간이 정해진 블록이 now 전에 이미 끝났는지 (끝이 없으면 소요 시간·30분으로) */
    static boolean endedBefore(PatternTaskSample t, LocalDateTime now) {
        if (now == null || t.plannedStart() == null) return false;
        LocalDateTime end = t.plannedEnd() != null && t.plannedEnd().isAfter(t.plannedStart())
                ? t.plannedEnd()
                : t.plannedStart().plusMinutes(t.plannedMinutes() > 0 ? t.plannedMinutes() : 30);
        return end.isBefore(now);
    }

    /** 전체 기간의 시작: 가장 이른 계획 시각 날짜와 가장 이른 routine_date 중 빠른 것 */
    LocalDate firstDate(Long userId) {
        List<Object[]> rows = patternQueryRepository.findFirstDates(userId);
        if (rows.isEmpty()) return null;
        Object[] r = rows.get(0);
        LocalDate a = r[0] != null ? ((LocalDateTime) r[0]).toLocalDate() : null;
        LocalDate b = (LocalDate) r[1];
        if (a == null) return b;
        if (b == null) return a;
        return a.isBefore(b) ? a : b;
    }

    /** carry_over_count 중 이월 기록이 없는 만큼 = V35 이전 이월 */
    private int legacyCarryMisses(List<PatternTaskSample> tasks) {
        List<Long> carriedIds = tasks.stream().filter(t -> t.carryOverCount() > 0).map(PatternTaskSample::taskId).toList();
        if (carriedIds.isEmpty()) return 0;
        Map<Long, Long> logged = new HashMap<>();
        for (List<Long> chunk : chunks(carriedIds)) {
            patternQueryRepository.countCarryLogs(chunk).forEach(r -> logged.put((Long) r[0], (Long) r[1]));
        }
        int legacy = 0;
        for (PatternTaskSample t : tasks) {
            if (t.carryOverCount() > 0) {
                legacy += (int) Math.max(0, t.carryOverCount() - logged.getOrDefault(t.taskId(), 0L));
            }
        }
        return legacy;
    }

    /** findEvaluatedTasks 한 행 + 실제 기록 요약 → 샘플 */
    static PatternTaskSample toSample(Object[] r, Object[] actual) {
        LocalDateTime plannedStart = (LocalDateTime) r[13];
        LocalDate routineDate = (LocalDate) r[16];
        LocalDate date = plannedStart != null ? plannedStart.toLocalDate() : routineDate;
        return new PatternTaskSample(
                (Long) r[0],
                (Long) r[1], (String) r[2], (String) r[3],
                (Long) r[4], (String) r[5], (String) r[6], normalizeColor((String) r[7]),
                Boolean.TRUE.equals(r[8]), (LocalDate) r[9],
                (String) r[10], (String) r[11],
                r[12] == TaskStatus.COMPLETED,
                plannedStart, (LocalDateTime) r[14], toInt(r[15]),
                date,
                (LocalDate) r[17],
                (LocalDateTime) r[18],
                r[19] != null ? ((Number) r[19]).intValue() : 0,
                actual != null ? (LocalDateTime) actual[1] : null,
                actual != null ? (LocalDateTime) actual[2] : null,
                actual != null ? toInt(actual[3]) : null
        );
    }

    private static LifePatternSample toPattern(LifePattern p) {
        return new LifePatternSample(p.getTitle(), p.getPatternType(), p.getStartTime(), p.getEndTime(), p.getDaysMask());
    }

    /** UserColor 는 "#RRGGBB" 와 "RRGGBB" 가 섞여 있다 → 항상 # 붙여서 */
    static String normalizeColor(String code) {
        if (code == null || code.isBlank()) return null;
        return code.startsWith("#") ? code : "#" + code;
    }

    private static Integer toInt(Object o) {
        return o == null ? null : ((Number) o).intValue();
    }

    static <T> List<List<T>> chunks(List<T> list) {
        List<List<T>> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i += IN_CHUNK) {
            out.add(list.subList(i, Math.min(list.size(), i + IN_CHUNK)));
        }
        return out;
    }
}
