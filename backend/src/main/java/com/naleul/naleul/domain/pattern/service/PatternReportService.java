package com.naleul.naleul.domain.pattern.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.naleul.naleul.domain.pattern.calculator.*;
import com.naleul.naleul.domain.pattern.dto.PatternReportAssembler;
import com.naleul.naleul.domain.pattern.dto.PatternReportResponse;
import com.naleul.naleul.domain.pattern.entity.PatternReport;
import com.naleul.naleul.domain.pattern.entity.PatternSuggestionAction;
import com.naleul.naleul.domain.pattern.enums.PatternAiStatus;
import com.naleul.naleul.domain.pattern.enums.PatternPeriod;
import com.naleul.naleul.domain.pattern.repository.PatternQueryRepository;
import com.naleul.naleul.domain.pattern.repository.PatternReportRepository;
import com.naleul.naleul.domain.pattern.repository.PatternSuggestionActionRepository;
import com.naleul.naleul.domain.planner.support.PlannerClock;
import com.naleul.naleul.domain.routine.entity.Routine;
import com.naleul.naleul.domain.routine.enums.RoutineStatus;
import com.naleul.naleul.domain.routine.repository.RoutineRepository;
import com.naleul.naleul.domain.routineRepeatDay.entity.RoutineRepeatDay;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 나의 패턴 리포트 (명세 4 · 7-1).
 *
 * 흐름: 캐시(사용자·기간·오늘) 확인 → 없으면 재료 로딩 → 카드 계산 → 스타일·제안 → 저장 → 반환
 *
 * 숫자는 항상 최신, AI 문장은 하루 1번:
 *  - 기록이 바뀌면(dataVersion) 숫자·그래프는 바로 다시 계산한다 (DB·자바 계산뿐이라 비용 없음).
 *  - AI 문장 다듬기는 그날 (사용자·기간) 첫 계산 때만. 그 뒤 다시 계산하면 템플릿 문장(aiStatus TEMPLATE)을 쓴다.
 *    다듬은 문장에는 그때의 숫자가 들어 있어 숫자가 바뀐 뒤에 재사용하면 틀린 문장이 되므로, 재사용 대신 템플릿.
 * 분석 범위가 어제까지라 같은 날에는 결과가 바뀌지 않으므로 하루 한 번만 계산한다.
 *
 * 트랜잭션을 메서드 전체에 걸지 않은 이유: 동시에 두 요청이 계산하면 두 번째 저장이 UNIQUE 충돌로 실패하는데,
 * 하나의 트랜잭션 안이면 그 실패로 트랜잭션 전체가 롤백 전용이 되어 이미 저장된 행을 다시 읽을 수 없다.
 * (조회는 PatternSampleLoader 의 읽기 전용 트랜잭션, 저장은 save() 자체 트랜잭션)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PatternReportService {

    private final PatternSampleLoader sampleLoader;
    private final RoutineRepository routineRepository;
    private final PatternReportRepository reportRepository;
    private final PatternSuggestionActionRepository suggestionActionRepository;
    private final PatternConflictCheckerFactory conflictCheckerFactory;
    private final PatternAccessPolicy accessPolicy;
    private final PatternTextPolisher textPolisher;
    private final ObjectMapper objectMapper;
    private final PlannerClock clock;
    private final PatternQueryRepository patternQueryRepository;

    public PatternReportResponse getReport(Long userId, PatternPeriod period) {
        return getReport(userId, period, false);
    }

    /**
     * @param refresh true 면 캐시를 무시하고 새로 계산 ("지금 다시 계산")
     */
    public PatternReportResponse getReport(Long userId, PatternPeriod period, boolean refresh) {
        accessPolicy.check(userId, period);
        LocalDate today = clock.today();
        String version = dataVersion(userId, today);
        // 오늘 이미 한 번 계산했으면(= 그때 AI 다듬기를 했으면) 이번엔 AI 를 부르지 않는다
        boolean polishedToday = reportRepository.findCached(userId, period, today).isPresent();

        if (!refresh) {
            Optional<PatternReportResponse> cached = readCache(userId, period, today, version);
            if (cached.isPresent()) return cached.get();
        } else {
            reportRepository.findCached(userId, period, today).ifPresent(row -> {
                reportRepository.delete(row);
                reportRepository.flush();
            });
        }

        PatternReportResponse report = build(userId, period, today, !polishedToday).withDataVersion(version);
        saveCache(userId, period, today, report);
        return report;
    }

    /**
     * 계산에 쓰는 기록이 바뀌었는지 알 수 있는 짧은 문자열.
     * 예전에는 "마지막 완료 시각"만 봐서 완료 취소·블록 이동·삭제·실제 시간 수정·시간이 지나 놓친 블록이
     * 다음 날까지 반영되지 않았다 (화면이 하루 늦게 맞아지는 원인).
     */
    static final String CALC_VERSION = "v2";

        String dataVersion(Long userId, LocalDate today) {
        if (patternQueryRepository == null) return null;
        try {
            Object[] t = first(patternQueryRepository.findTaskFingerprint(userId, today.atStartOfDay(), clock.now()));
            Object[] a = first(patternQueryRepository.findActualFingerprint(userId));
            Long lastLog = patternQueryRepository.findLastScheduleLogId(userId);
            // 맨 앞 = 계산 방식 버전. 계산 방식을 바꾸면 올려서 옛 캐시를 버려요 (v2: 히트맵 = 한 수 · 블록별 지키기 · 루틴 기준 완화)
            return String.join("|", CALC_VERSION, s(t, 0), s(t, 1), s(t, 2), s(t, 3), s(t, 4), String.valueOf(lastLog), s(a, 0), s(a, 1));
        } catch (RuntimeException e) {
            log.warn("[Pattern] 데이터 버전 계산 실패 userId={}", userId, e);
            return null;
        }
    }

    private static Object[] first(List<Object[]> rows) {
        return rows == null || rows.isEmpty() ? new Object[0] : rows.get(0);
    }

    private static String s(Object[] row, int i) {
        return i < row.length ? String.valueOf(row[i]) : "";
    }

    /** 캐시 없이 새로 계산 (캐시 저장 안 함) */
    public PatternReportResponse build(Long userId, PatternPeriod period, LocalDate today) {
        return build(userId, period, today, true);
    }

    /** @param polish false 면 AI 문장 다듬기를 건너뛰고 템플릿 문장 그대로 (하루 1번 제한) */
    public PatternReportResponse build(Long userId, PatternPeriod period, LocalDate today, boolean polish) {
        long started = System.currentTimeMillis();
        PatternSampleSet set = sampleLoader.load(userId, period, today, clock.now());
        PatternStats stats = PatternCalculator.calculate(set);

        StyleResult style = null;
        try {
            style = StyleClassifier.classify(set, stats);
        } catch (RuntimeException e) {
            log.error("[Pattern] 실행 스타일 계산 실패 userId={}", userId, e);
        }

        SuggestionResult suggestion = null;
        try {
            suggestion = findSuggestion(userId, set, stats, style, today);
        } catch (RuntimeException e) {
            log.error("[Pattern] 제안 계산 실패 userId={}", userId, e);
        }

        PatternReportResponse report = PatternReportAssembler.assemble(period, stats, style, suggestion,
                PatternAiStatus.TEMPLATE, clock.now());
        // AI 문장 다듬기 — 실패하면 템플릿 그대로. 트랜잭션 밖에서 호출한다 (AI 응답을 기다리는 동안 DB 연결을 잡지 않도록)
        if (polish) report = textPolisher.polish(report);
        log.info("[Pattern] 리포트 계산 userId={} period={} tasks={} ai={} {}ms",
                userId, period, set.tasks().size(), polish, System.currentTimeMillis() - started);
        return report;
    }

    private SuggestionResult findSuggestion(Long userId, PatternSampleSet set, PatternStats stats,
                                            StyleResult style, LocalDate today) {
        List<Long> routineIds = set.tasks().stream()
                .map(PatternTaskSample::routineId).filter(Objects::nonNull).distinct().toList();
        if (routineIds.isEmpty()) return null;
        List<RoutineInfo> routines = routineRepository.findAllByRoutineIdInWithRepeatDays(routineIds).stream()
                .filter(r -> r.getUser() != null && Objects.equals(r.getUser().getUserId(), userId))
                .map(PatternReportService::toInfo)
                .toList();
        Set<String> blocked = new HashSet<>(suggestionActionRepository.findBlockedKeys(userId,
                clock.now().minusDays(PatternSuggestionAction.DISMISS_DAYS)));
        LocalDate tomorrow = today.plusDays(1);
        ConflictChecker checker = conflictCheckerFactory.create(userId, tomorrow);
        return SuggestionFinder.find(set, stats, style, routines, blocked, tomorrow, checker);
    }

    static RoutineInfo toInfo(Routine r) {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (RoutineRepeatDay d : r.getRepeatDays()) days.add(d.getDayOfWeek());
        boolean active = r.getRoutineStatus() == RoutineStatus.NOT_STARTED
                || r.getRoutineStatus() == RoutineStatus.IN_PROGRESS;
        return new RoutineInfo(r.getRoutineId(), r.getRoutineName(), r.getEmoji(),
                r.getRepeatStartTime(), r.getRepeatEndTime(), r.getDurationMinutes(),
                days, active, r.getRepeatEndDate());
    }

    // ── 캐시 ──────────────────────────────────────────────

    private Optional<PatternReportResponse> readCache(Long userId, PatternPeriod period, LocalDate today, String version) {
        return reportRepository.findCached(userId, period, today).flatMap(row -> {
            try {
                PatternReportResponse cached = objectMapper.readValue(row.getPayloadJson(), PatternReportResponse.class);
                // 계산 뒤에 기록이 바뀌었으면 (완료·취소·이동·삭제·실제 시간·시간이 지난 블록) 다시 계산
                if (version == null || !version.equals(cached.dataVersion())) {
                    reportRepository.delete(row);
                    reportRepository.flush();
                    return Optional.empty();
                }
                return Optional.of(cached);
            } catch (JsonProcessingException e) {
                // 응답 구조가 바뀐 뒤의 옛 캐시 → 지우고 다시 계산
                log.warn("[Pattern] 캐시 읽기 실패, 다시 계산 userId={} period={}", userId, period);
                reportRepository.delete(row);
                return Optional.empty();
            }
        });
    }

    private void saveCache(Long userId, PatternPeriod period, LocalDate today, PatternReportResponse report) {
        try {
            String json = objectMapper.writeValueAsString(report);
            reportRepository.save(PatternReport.of(userId, period, today, json, report.aiStatus(), clock.now()));
        } catch (DataIntegrityViolationException e) {
            // 같은 사용자의 다른 요청이 먼저 저장함 → 그쪽 결과를 그대로 쓰면 되므로 무시
            log.debug("[Pattern] 캐시가 이미 있음 userId={} period={}", userId, period);
        } catch (JsonProcessingException e) {
            log.error("[Pattern] 캐시 저장 실패 userId={}", userId, e);
        }
    }

    /** 제안 반영 등으로 루틴이 바뀌면 호출 */
    public void evictCache(Long userId) {
        reportRepository.deleteAllByUserId(userId);
    }
}
