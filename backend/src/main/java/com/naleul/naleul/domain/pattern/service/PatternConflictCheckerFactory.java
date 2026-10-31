package com.naleul.naleul.domain.pattern.service;

import com.naleul.naleul.domain.lifePattern.service.FixedBlockResolver;
import com.naleul.naleul.domain.lifePattern.support.DayFixedBlocks;
import com.naleul.naleul.domain.lifePattern.support.FixedBlock;
import com.naleul.naleul.domain.pattern.calculator.ConflictChecker;
import com.naleul.naleul.domain.pattern.calculator.RoutineInfo;
import com.naleul.naleul.domain.pattern.repository.PatternQueryRepository;
import com.naleul.naleul.domain.planner.support.DateTimeRange;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * 제안 겹침 검사기 만들기 (명세 4 · 4-1 "겹침 검사").
 * 내일부터 2주 동안의 고정 시간(생활 패턴, 그날만 바꾼 것 포함)과 옮길 수 없는 블록을 한 번에 읽어 두고,
 * 제안 후보마다 메모리에서 비교한다 (후보마다 DB 를 읽지 않음).
 */
@Component
@RequiredArgsConstructor
public class PatternConflictCheckerFactory {

    static final int HORIZON_DAYS = 14;

    private final FixedBlockResolver fixedBlockResolver;
    private final PatternQueryRepository patternQueryRepository;

    public ConflictChecker create(Long userId, LocalDate tomorrow) {
        LocalDate last = tomorrow.plusDays(HORIZON_DAYS - 1L);
        Map<LocalDate, DayFixedBlocks> fixed = fixedBlockResolver.resolve(userId, tomorrow, last);

        List<DateTimeRange> fixedRanges = new ArrayList<>();
        fixed.values().forEach(d -> d.busyBlocks().forEach((FixedBlock b) -> fixedRanges.add(b.range())));

        List<Object[]> rows = patternQueryRepository.findFixedTaskBlocks(userId,
                tomorrow.atStartOfDay(), last.plusDays(1).atStartOfDay());
        List<TaskBlock> taskBlocks = new ArrayList<>();
        for (Object[] r : rows) {
            LocalDateTime s = (LocalDateTime) r[2], e = (LocalDateTime) r[3];
            if (e.isAfter(s)) taskBlocks.add(new TaskBlock((Long) r[1], new DateTimeRange(s, e)));
        }
        return new Checker(tomorrow, last, fixedRanges, taskBlocks);
    }

    record TaskBlock(Long routineId, DateTimeRange range) {
    }

    /** 순수 비교 — 테스트에서 직접 만들 수 있게 package-private */
    record Checker(LocalDate first, LocalDate last, List<DateTimeRange> fixed, List<TaskBlock> tasks)
            implements ConflictChecker {

        @Override
        public double conflictRatio(RoutineInfo routine, Set<DayOfWeek> days, LocalTime start, LocalTime end) {
            int total = 0, conflicts = 0;
            for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) {
                if (!days.contains(d.getDayOfWeek())) continue;
                if (routine.repeatEndDate() != null && d.isAfter(routine.repeatEndDate())) continue;
                LocalDateTime s = d.atTime(start);
                LocalDateTime e = end.isAfter(start) ? d.atTime(end) : d.plusDays(1).atTime(end);
                if (!e.isAfter(s)) continue;
                DateTimeRange target = new DateTimeRange(s, e);
                total++;
                boolean hit = fixed.stream().anyMatch(f -> f.overlaps(target))
                        || tasks.stream().anyMatch(t -> !Objects.equals(t.routineId(), routine.routineId())
                        && t.range().overlaps(target));
                if (hit) conflicts++;
            }
            return total == 0 ? 0 : (double) conflicts / total;
        }
    }
}
