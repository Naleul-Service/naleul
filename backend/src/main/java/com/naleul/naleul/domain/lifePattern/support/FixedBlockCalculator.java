package com.naleul.naleul.domain.lifePattern.support;

import com.naleul.naleul.domain.lifePattern.entity.LifePattern;
import com.naleul.naleul.domain.lifePattern.entity.LifePatternOverride;
import com.naleul.naleul.domain.lifePattern.enums.LifePatternType;
import com.naleul.naleul.domain.planner.support.DateTimeRange;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 패턴 + 예외 → 날짜별 고정 블록 (순수 계산, DB 접근 없음 → 단위 테스트 가능).
 *
 * 규칙 (명세 3 · 3-1, 9장)
 *  - 블록은 "시작한 날짜" 소속이다. 요일 판정·그날만 변경도 시작한 날짜 기준.
 *  - 자정을 넘기는 블록(수면 23:00~07:00)은 시작한 날 화면에는 23:00~24:00,
 *    다음 날 화면에는 00:00~07:00 으로 잘라 보여준다.
 *  - 그날 예외가 skipped 면 블록 없음, 시간이 있으면 그 시간으로 대체.
 *  - 하루 범위: 그날 오전(00:00~12:00)에 끝나는 수면 중 가장 늦게 끝나는 시각 ~
 *              그날 12:00 ~ 다음 날 12:00 사이에 시작하는 수면 중 가장 이른 시작 시각.
 *  - 요일별 시간·그날만 변경에 "시작 날짜 차이(startDayOffset)"가 있으면 블록이 소속 날짜 전날/다음 날에 시작할 수 있다.
 *    그래서 앞뒤 이틀씩 펼쳐 놓고 실제 시각으로 그날과 겹치는 블록을 고른다.
 */
public final class FixedBlockCalculator {

    /** 앞뒤로 펼쳐 볼 날 수 (자정 넘김 1일 + 시작 날짜 차이 1일) */
    public static final int SPAN = 2;

    private FixedBlockCalculator() {
    }

    /**
     * @param overrides startDate-2 ~ endDate+2 범위의 예외 (자정 넘김·시작 날짜 차이·다음 날 수면 계산 때문에 앞뒤 이틀씩 필요)
     */
    public static Map<LocalDate, DayFixedBlocks> calculate(List<LifePattern> patterns,
                                                           List<LifePatternOverride> overrides,
                                                           LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate before startDate");
        }
        Map<String, LifePatternOverride> overrideByKey = new HashMap<>();
        for (LifePatternOverride o : overrides) {
            overrideByKey.put(key(o.getLifePattern().getLifePatternId(), o.getTargetDate()), o);
        }

        // 앞뒤 하루를 포함해 "시작한 날짜 기준" 원본 블록 생성
        Map<LocalDate, List<FixedBlock>> rawByStartDate = new HashMap<>();
        for (LocalDate d = startDate.minusDays(SPAN); !d.isAfter(endDate.plusDays(SPAN)); d = d.plusDays(1)) {
            List<FixedBlock> list = new ArrayList<>();
            for (LifePattern p : patterns) {
                FixedBlock b = occurrence(p, overrideByKey.get(key(p.getLifePatternId(), d)), d);
                if (b != null) list.add(b);
            }
            rawByStartDate.put(d, list);
        }

        Map<LocalDate, DayFixedBlocks> result = new LinkedHashMap<>();
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            LocalDateTime dayStart = d.atStartOfDay();
            LocalDateTime dayEnd = d.plusDays(1).atStartOfDay();

            // 이날 00:00~24:00 과 겹치는 블록 (전날 시작해 넘어온 블록, 시작 날짜 차이로 앞뒤 날짜 소속인 블록 포함)
            List<FixedBlock> display = new ArrayList<>();
            for (LocalDate s = d.minusDays(SPAN); !s.isAfter(d.plusDays(SPAN)); s = s.plusDays(1)) {
                for (FixedBlock b : rawByStartDate.get(s)) {
                    if (b.start().isBefore(dayEnd) && b.end().isAfter(dayStart)) display.add(b.clipTo(d));
                }
            }
            display.sort(Comparator.comparing(FixedBlock::start));

            SleepBounds sleepBounds = sleepBounds(d, rawByStartDate);
            DateTimeRange dayRange = sleepBounds.range();

            List<FixedBlock> busy = new ArrayList<>();
            for (LocalDate s = d.minusDays(SPAN); !s.isAfter(d.plusDays(SPAN)); s = s.plusDays(1)) {
                for (FixedBlock b : rawByStartDate.get(s)) {
                    if (b.start().isBefore(dayRange.end()) && b.end().isAfter(dayRange.start())) busy.add(b);
                }
            }
            busy.sort(Comparator.comparing(FixedBlock::start));

            result.put(d, new DayFixedBlocks(d, List.copyOf(display), List.copyOf(busy), dayRange,
                    sleepBounds.wake(), sleepBounds.bed()));
        }
        return result;
    }

    static FixedBlock occurrence(LifePattern p, LifePatternOverride override, LocalDate date) {
        DateTimeRange range;
        boolean overridden = false;
        if (override != null) {
            if (override.isSkipped()) return null;
            range = override.toRange();
            overridden = true;
        } else {
            range = p.occurrenceOn(date);
        }
        if (range == null) return null;
        return new FixedBlock(p.getLifePatternId(), p.getPatternType(), p.getTitle(), p.getEmoji(),
                date, range.start(), range.end(), overridden, false);
    }

    /** 하루 범위와 그 경계가 된 수면 블록 (기상 = wake 의 끝, 취침 = bed 의 시작) */
    record SleepBounds(DateTimeRange range, FixedBlock wake, FixedBlock bed) {
    }

    static DateTimeRange dayRange(LocalDate d, Map<LocalDate, List<FixedBlock>> rawByStartDate) {
        return sleepBounds(d, rawByStartDate).range();
    }

    static SleepBounds sleepBounds(LocalDate d, Map<LocalDate, List<FixedBlock>> rawByStartDate) {
        LocalDateTime dayStart = d.atStartOfDay();
        LocalDateTime noon = d.atTime(12, 0);
        LocalDateTime nextNoon = d.plusDays(1).atTime(12, 0);
        LocalDateTime dayEnd = d.plusDays(1).atStartOfDay();

        LocalDateTime wake = dayStart;
        LocalDateTime sleep = d.plusDays(1).atStartOfDay();
        FixedBlock wakeBlock = null;
        FixedBlock bedBlock = null;

        for (LocalDate s = d.minusDays(SPAN); !s.isAfter(d.plusDays(SPAN)); s = s.plusDays(1)) {
            for (FixedBlock b : rawByStartDate.getOrDefault(s, List.of())) {
                if (b.patternType() != LifePatternType.SLEEP) continue;
                // 그날 정오 전에 시작해(전날 밤 시작 포함) 그날 안에 끝나는 수면 → 기상 시각 후보.
                // 늦잠으로 오후에 일어나도(예: 오후 2시 기상) 기상으로 봐요 — 예전엔 "정오까지 끝나는 수면"만 봐서
                // 기상 블록이 없어지고 화면에서 수면이 사라졌어요
                if (b.start().isBefore(noon) && b.end().isAfter(dayStart) && !b.end().isAfter(dayEnd)
                        && b.end().isAfter(wake)) {
                    wake = b.end();
                    wakeBlock = b;
                }
                // 그날 정오 ~ 다음 날 정오 사이에 시작하는 수면 → 취침 시각 후보
                if (!b.start().isBefore(noon) && b.start().isBefore(nextNoon)
                        && (bedBlock == null || b.start().isBefore(sleep))) {
                    sleep = b.start();
                    bedBlock = b;
                }
            }
        }
        if (!sleep.isAfter(wake)) {   // 비정상 입력 방어 (예: 수면 블록이 하루 전체를 덮음)
            return new SleepBounds(new DateTimeRange(dayStart, d.plusDays(1).atStartOfDay()), null, null);
        }
        return new SleepBounds(new DateTimeRange(wake, sleep), wakeBlock, bedBlock);
    }

    private static String key(Long patternId, LocalDate date) {
        return patternId + "|" + date;
    }

    // ── 패턴끼리 겹침 검사 (설정 저장 시) ─────────────────────

    /**
     * 같은 요일(또는 자정 넘김으로 이어지는 다음 날)에서 두 패턴이 겹치는지.
     * 기준 주(월~일)와 앞뒤 하루를 펼쳐서 실제 구간끼리 비교한다.
     */
    public static boolean overlaps(LifePattern a, LifePattern b) {
        // 이날만 패턴이 끼면 그 실제 날짜 앞뒤로 비교 (기준 주로는 날짜가 안 맞아 늘 비어 보임)
        LocalDate oneOff = a.getOnlyDate() != null ? a.getOnlyDate() : b.getOnlyDate();
        if (a.getOnlyDate() != null && b.getOnlyDate() != null
                && Math.abs(java.time.temporal.ChronoUnit.DAYS.between(a.getOnlyDate(), b.getOnlyDate())) > SPAN + 1) {
            return false;
        }
        if (oneOff != null) {
            for (int i = -1; i <= 1; i++) {
                DateTimeRange ra = a.occurrenceOn(oneOff.plusDays(i));
                if (ra == null) continue;
                for (int j = i - SPAN; j <= i + SPAN; j++) {
                    DateTimeRange rb = b.occurrenceOn(oneOff.plusDays(j));
                    if (rb != null && ra.overlaps(rb)) return true;
                }
            }
            return false;
        }
        LocalDate refMonday = LocalDate.of(2024, 1, 1); // 월요일
        for (int i = -1; i <= 7; i++) {
            DateTimeRange ra = a.occurrenceOn(refMonday.plusDays(i));
            if (ra == null) continue;
            for (int j = i - SPAN; j <= i + SPAN; j++) {
                DateTimeRange rb = b.occurrenceOn(refMonday.plusDays(j));
                if (rb != null && ra.overlaps(rb)) return true;
            }
        }
        return false;
    }

    /**
     * 같은 패턴의 이웃한 날 블록끼리 겹치는지 (요일별 시간 때문에 생길 수 있음).
     * 예) 수면 기본 00:00~07:00 인데 토요일만 "금요일 06:00 시작"으로 바꾸면 금요일 블록(00~07시)과 겹친다.
     */
    public static boolean overlapsItself(LifePattern p) {
        if (p.getOnlyDate() != null) return false;   // 하루짜리는 이웃한 날 블록이 없다
        LocalDate refMonday = LocalDate.of(2024, 1, 1);
        for (int i = -1; i <= 7; i++) {
            DateTimeRange ra = p.occurrenceOn(refMonday.plusDays(i));
            if (ra == null) continue;
            for (int j = i + 1; j <= i + SPAN; j++) {
                DateTimeRange rb = p.occurrenceOn(refMonday.plusDays(j));
                if (rb != null && ra.overlaps(rb)) return true;
            }
        }
        return false;
    }
}
