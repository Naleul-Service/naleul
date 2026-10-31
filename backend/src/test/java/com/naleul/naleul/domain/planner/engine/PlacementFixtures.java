package com.naleul.naleul.domain.planner.engine;

import com.naleul.naleul.domain.planner.engine.PlacementInput.BusyTask;
import com.naleul.naleul.domain.planner.support.DateTimeRange;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** 엔진 테스트용 입력 생성기 + 불변식 검사 (테스트끼리 공유) */
final class PlacementFixtures {

    static final LocalDate D = LocalDate.of(2026, 11, 18); // 수요일

    private PlacementFixtures() {
    }

    static PlacementCandidate cand(long id, int minutes, int score) {
        return new PlacementCandidate(id, null, minutes, minutes,
                new Importance(score, ImportanceLevel.of(score), null, 0), List.of());
    }

    static PlacementInput input(List<DateTimeRange> fixed, List<BusyTask> busy, List<PlacementCandidate> cands,
                                HourlyEfficiency eff) {
        return new PlacementInput(D, new DateTimeRange(D.atTime(7, 0), D.plusDays(1).atStartOfDay()), null,
                fixed, busy, cands, eff, 10);
    }

    static DateTimeRange r(int sh, int sm, int eh, int em) {
        LocalDateTime s = D.atTime(sh, sm);
        LocalDateTime e = eh == 24 ? D.plusDays(1).atStartOfDay() : D.atTime(eh, em);
        return new DateTimeRange(s, e);
    }

    /** 무작위 입력 (속성 기반 테스트용) */
    static PlacementInput random(Random rnd) {
        LocalDateTime dayStart = D.atTime(5 + rnd.nextInt(5), rnd.nextInt(4) * 15);
        LocalDateTime dayEnd = D.atTime(21, 0).plusMinutes(rnd.nextInt(5 * 60));
        DateTimeRange dayRange = new DateTimeRange(dayStart, dayEnd);

        List<DateTimeRange> fixed = new ArrayList<>();
        for (int i = 0, n = rnd.nextInt(5); i < n; i++) {
            LocalDateTime s = dayStart.plusMinutes(rnd.nextInt(16 * 60));
            fixed.add(new DateTimeRange(s, s.plusMinutes(15 + rnd.nextInt(240))));
        }
        List<BusyTask> busy = new ArrayList<>();
        for (int i = 0, n = rnd.nextInt(5); i < n; i++) {
            LocalDateTime s = dayStart.plusMinutes(rnd.nextInt(16 * 60) + rnd.nextInt(7)); // 10분 단위가 아닌 시각도
            busy.add(new BusyTask(new DateTimeRange(s, s.plusMinutes(5 + rnd.nextInt(120))),
                    rnd.nextBoolean() ? (long) rnd.nextInt(3) : null));
        }
        List<PlacementCandidate> cands = new ArrayList<>();
        for (int i = 0, n = rnd.nextInt(13); i < n; i++) {
            int score = rnd.nextInt(101);
            List<PlacementCandidate.Window> windows = rnd.nextInt(4) == 0
                    ? List.of(new PlacementCandidate.Window(LocalTime.of(19, 0), LocalTime.of(22, 0)))
                    : List.of();
            int dur = 5 + rnd.nextInt(180);
            cands.add(new PlacementCandidate(100L + i, rnd.nextBoolean() ? (long) rnd.nextInt(3) : null,
                    dur, dur, new Importance(score, ImportanceLevel.of(score), null, 0), windows));
        }
        LocalDateTime notBefore = rnd.nextInt(3) == 0 ? dayStart.plusMinutes(rnd.nextInt(14 * 60) + rnd.nextInt(10)) : null;
        HourlyEfficiency eff = rnd.nextBoolean() ? HourlyEfficiency.defaultCurve(0) : randomEff(rnd);
        return new PlacementInput(D, dayRange, notBefore, fixed, busy, cands, eff, rnd.nextBoolean() ? 10 : 0);
    }

    static HourlyEfficiency randomEff(Random rnd) {
        double[] a = new double[24];
        for (int i = 0; i < 24; i++) a[i] = rnd.nextDouble();
        return new HourlyEfficiency(a, a.clone(), false, 50);
    }

    /** 배치 결과 불변식. 위반이면 설명 문자열, 정상이면 null */
    static String violation(PlacementInput in, PlacementResult res) {
        if (res.placed().size() + res.unplaced().size() != in.candidates().size()) return "count mismatch";
        Duration gap = Duration.ofMinutes(in.gapMinutes());
        List<DateTimeRange> placed = new ArrayList<>();
        for (PlacementResult.Placed p : res.placed()) {
            PlacementCandidate c = in.candidates().stream().filter(x -> x.taskId().equals(p.taskId())).findFirst().orElseThrow();
            // 나눠 넣었으면 구간마다, 아니면 한 덩어리
            List<DateTimeRange> parts = p.split() ? p.segments() : List.of(new DateTimeRange(p.start(), p.end()));
            long total = parts.stream().mapToLong(DateTimeRange::minutes).sum();
            if (total != c.durationMinutes()) return "duration changed " + p;
            if (p.split()) {
                if (!c.splittable()) return "split unsplittable " + p;
                if (parts.size() > TimeBlockPlacementEngine.MAX_PIECES) return "too many pieces " + p;
                if (!p.start().equals(parts.get(0).start()) || !p.end().equals(parts.get(parts.size() - 1).end()))
                    return "start/end not first/last segment " + p;
                if (!p.reasonCodes().contains(ReasonCode.SPLIT_BLOCK)) return "missing SPLIT_BLOCK " + p;
            }
            for (DateTimeRange pr : parts) {
                if (pr.start().getMinute() % 10 != 0) return "not on 10-min grid " + p;
                if (pr.start().isBefore(in.dayRange().start()) || pr.end().isAfter(in.dayRange().end())) return "outside dayRange " + p;
                if (in.notBefore() != null && pr.start().isBefore(in.notBefore())) return "before notBefore " + p;
                for (DateTimeRange f : in.fixedBusy()) if (pr.overlaps(f)) return "overlaps fixed " + p + " vs " + f;
                DateTimeRange padded = new DateTimeRange(pr.start().minus(gap), pr.end().plus(gap));
                for (BusyTask b : in.taskBusy()) if (padded.overlaps(b.range())) return "too close to busy task " + p + " vs " + b;
                for (DateTimeRange other : placed) if (padded.overlaps(other)) return "overlaps/too close to placed " + p + " vs " + other;
            }
            if (p.reasonText() == null || p.reasonText().isBlank()) return "missing reason " + p;
            placed.addAll(parts);
        }
        return null;
    }
}
