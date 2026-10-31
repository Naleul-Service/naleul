package com.naleul.naleul.domain.planner.engine;

import com.naleul.naleul.domain.planner.engine.PlacementInput.BusyTask;
import com.naleul.naleul.domain.planner.engine.PlacementResult.Placed;
import com.naleul.naleul.domain.planner.engine.PlacementResult.Unplaced;
import com.naleul.naleul.domain.planner.engine.PlacementResult.UnplacedCause;
import com.naleul.naleul.domain.planner.support.DateTimeRange;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * TimeBlock 배치 엔진 (명세 6장). 규칙 기반 · 순수 계산 · AI 호출 없음.
 * 같은 입력이면 항상 같은 결과가 나온다 (테스트·재현 가능).
 *
 * 동작
 *  1. 하루 범위를 10분 칸으로 나눈다. 고정 블록 칸은 막고, 이미 있는 Task 칸은 앞뒤 간격까지 막는다.
 *  2. 후보를 중요도 ↓, 길이 ↓ 순으로 정렬 (같으면 taskId 순 — 결과 고정용)
 *  3. 후보마다 들어갈 수 있는 모든 시작 칸의 점수를 매겨 가장 높은 곳에 넣는다 (동점이면 이른 시각)
 *       HIGH : 효율          → 집중이 잘 되는 시간대
 *       MID  : 0.5·효율 + 0.5·이른 시각
 *       LOW  : 1 − 효율      → 마감이 여유 있는 일은 덜 지키는 시간대에 (잘 지키는 시간은 급한 일에)
 *       + 0.15 선호 시간대 안
 *       + 0.40 같은 목표 Task 와 이어짐 (2026-10: 같은 목표는 최대한 묶어서 — 예전엔 떨어뜨렸다. 이른 시각·효율보다 우선)
 *       + 0.10 앞뒤 일정과 쉬는 시간이 30분 이내 (2026-10: 블록 사이가 너무 벌어지지 않게)
 *       효율은 "같은 목표의 일"을 그 시간대에 얼마나 지켰는지가 쌓였으면 그 기록으로 당긴다 (목표별 시간 궁합)
 *       + 최대 ±0.10 옮기기 습관 (사용자가 블록을 자주 옮겨 오는 시간대는 +, 자주 빼는 시간대는 −)
 *  4. 넣은 칸 + 앞뒤 간격을 막는다.
 *     한 번에 넣을 연속된 빈 시간이 없으면(점심이 끼어 있는 등) 고정 시간 앞뒤로 나눠 넣는다 (최대 3조각, 조각당 30분 이상).
 *       예) 4시간 · 빈 시간 09~12시, (점심 12~13시), 13~15시 → 09:00~12:00 + 13:00~14:00
 *     나눠도 못 넣으면 Unplaced (다른 날로 넘기기는 호출하는 서비스가 처리)
 *  5. 이유: "왜 먼저 · 왜 이 시간" 한 문장 + 내 기록 근거 한 문장 ("최근 4주 19–21시에 계획한 일 12개 중 10개를 해냈어요…")
 */
public final class TimeBlockPlacementEngine {

    static final int SLOT_MINUTES = 10;
    private static final double PREFERRED_BONUS = 0.15;
    /** 같은 목표 Task 와 이어지게 놓으면 (2026-10: 같은 목표는 묶어서) */
    private static final double GROUP_BONUS = 0.40;
    /** 앞뒤 일정과 쉬는 시간이 COMPACT_REST_MINUTES 이내면 (블록 사이가 너무 벌어지지 않게) */
    private static final double COMPACT_BONUS = 0.10;
    /** 같은 목표 Task 가 이 시간 안에 있으면 "붙어 있다"고 본다 (간격 20분 + 여유) */
    private static final int SAME_GOAL_NEAR_MINUTES = 30;
    /** 쉬는 시간이 이 안이면 "이어서 한다"고 본다 */
    static final int COMPACT_REST_MINUTES = 30;
    /** 나눠 넣을 때 한 조각의 최소 길이 (10분 칸 3개 = 30분) */
    static final int MIN_PIECE_SLOTS = 3;
    /** 나눠 넣을 때 최대 조각 수 */
    static final int MAX_PIECES = 3;
    /** "효율이 높다"고 말하려면 그날 범위 평균보다 이만큼은 높아야 한다 (5%p) */
    private static final double HIGH_MARGIN = 0.05;
    /** 목표별 시간대 기록을 쓰려면 그 목표의 계획 기록이 이만큼은 있어야 한다 */
    static final int GOAL_MIN_SAMPLES = 6;
    /** 목표별 기록을 섞을 때 "평균적인 가짜 기록" 수 (HourlyEfficiencyCalculator.K 와 같은 뜻) */
    private static final double GOAL_K = 3.0;
    /** 옮기기 습관을 믿으려면 최근 이동 기록이 이만큼은 있어야 한다 */
    static final int MOVE_MIN_COUNT = 5;
    private static final double MOVE_WEIGHT = 0.10;
    /** 이유 문장에 "자주 옮기셔서"라고 말할 만큼의 습관 */
    private static final double MOVE_HABIT_THRESHOLD = 0.15;

    private TimeBlockPlacementEngine() {
    }

    public static PlacementResult place(PlacementInput in) {
        Grid grid = Grid.of(in);
        int gapSlots = ceilDiv(Math.max(in.gapMinutes(), 0), SLOT_MINUTES);

        for (DateTimeRange r : in.fixedBusy()) grid.block(r, 0);
        List<BusyTask> placedRanges = new ArrayList<>(in.taskBusy());
        for (BusyTask b : in.taskBusy()) grid.block(b.range(), gapSlots);

        // "너무 길다(TOO_LONG)"는 다른 후보를 넣기 전 빈 시간 기준으로 판단한다.
        // (먼저 넣은 Task 때문에 자리가 없어진 경우는 NO_SLOT)
        int initialLongestRun = grid.longestFreeRun();
        double dayAverage = in.efficiency().average(in.dayRange().start(), in.dayRange().end());
        PatternEvidence ev = in.evidenceOrEmpty();
        boolean useMoves = ev.moveCount() >= MOVE_MIN_COUNT;

        List<PlacementCandidate> sorted = new ArrayList<>(in.candidates());
        sorted.sort(Comparator
                .comparingInt((PlacementCandidate c) -> c.importance().score()).reversed()
                .thenComparing(Comparator.comparingInt(PlacementCandidate::durationMinutes).reversed())
                .thenComparing(PlacementCandidate::taskId, Comparator.nullsLast(Comparator.naturalOrder())));

        List<Placed> placed = new ArrayList<>();
        List<Unplaced> unplaced = new ArrayList<>();

        for (PlacementCandidate c : sorted) {
            int need = ceilDiv(Math.max(c.durationMinutes(), SLOT_MINUTES), SLOT_MINUTES);
            List<Integer> starts = grid.feasibleStarts(need);
            if (starts.isEmpty()) {
                // 연속된 빈 시간이 없으면 고정 시간 앞뒤로 나눠 넣는다
                List<int[]> pieces = c.splittable() ? grid.bestSplit(need) : null;
                if (pieces == null) {
                    UnplacedCause cause = need > initialLongestRun ? UnplacedCause.TOO_LONG : UnplacedCause.NO_SLOT;
                    unplaced.add(new Unplaced(c.taskId(), cause));
                    continue;
                }
                List<DateTimeRange> segments = new ArrayList<>();
                // 10분 칸으로 올림한 만큼(0~9분)은 가장 긴 조각에서 덜어낸다 — 짧은 조각이 30분 밑으로 내려가지 않게.
                // 첫 조각이면 앞을 줄여 다음 조각과의 간격을 그대로 두고, 나머지 조각이면 끝을 줄인다
                int loss = Math.max(0, need * SLOT_MINUTES - Math.max(c.durationMinutes(), SLOT_MINUTES));
                int longest = 0;
                for (int k = 1; k < pieces.size(); k++) if (pieces.get(k)[1] > pieces.get(longest)[1]) longest = k;
                for (int k = 0; k < pieces.size(); k++) {
                    int[] piece = pieces.get(k);
                    LocalDateTime ps = grid.timeOf(piece[0]);
                    int minutes = piece[1] * SLOT_MINUTES;
                    if (k == longest && loss > 0) {
                        minutes -= loss;
                        if (k == 0) ps = ps.plusMinutes(loss);
                    }
                    DateTimeRange seg = new DateTimeRange(ps, ps.plusMinutes(minutes));
                    segments.add(seg);
                    grid.block(seg, gapSlots);
                    placedRanges.add(new BusyTask(seg, c.goalId()));
                }
                placed.add(explainSplit(c, segments));
                continue;
            }

            List<DateTimeRange> anchors = anchors(in.fixedBusy(), placedRanges);
            int best = -1;
            double bestScore = Double.NEGATIVE_INFINITY;
            boolean bestPreferred = false;
            for (int i : starts) {
                LocalDateTime s = grid.timeOf(i);
                LocalDateTime e = grid.timeOf(i + need);
                double eff = candidateEfficiency(ev, c.goalId(), in.efficiency(), s, e);
                double base = switch (c.importance().level()) {
                    case HIGH -> eff;
                    case MID -> 0.5 * eff + 0.5 * (1.0 - (double) i / Math.max(grid.size(), 1));
                    case LOW -> 1.0 - eff;
                };
                boolean preferred = inPreferred(c, s, e);
                double score = base
                        + (preferred ? PREFERRED_BONUS : 0)
                        + (nearSameGoal(placedRanges, c.goalId(), s, e) ? GROUP_BONUS : 0)
                        + (compact(anchors, s, e) ? COMPACT_BONUS : 0)
                        + (useMoves ? MOVE_WEIGHT * ev.moveAffinity(s, e) : 0);
                if (score > bestScore + 1e-9) {
                    bestScore = score;
                    best = i;
                    bestPreferred = preferred;
                }
            }

            LocalDateTime start = grid.timeOf(best);
            LocalDateTime end = start.plusMinutes(c.durationMinutes());
            DateTimeRange range = new DateTimeRange(start, end);
            boolean grouped = nearSameGoal(placedRanges, c.goalId(), start, end);
            grid.block(range, gapSlots);
            placedRanges.add(new BusyTask(range, c.goalId()));

            placed.add(explain(c, start, end, in.efficiency(), dayAverage, starts.size() == 1, bestPreferred, ev, useMoves,
                    grouped));
        }
        return new PlacementResult(placed, unplaced);
    }

    // ── 이유 ─────────────────────────────────────────────

    /**
     * 이 Task 에 쓸 효율: 같은 목표의 일을 이 시간대에 몇 번 중 몇 번 했는지가 쌓였으면 그쪽으로 당긴다.
     * 예) 사용자 전체로는 아침이 강해도, '운동' 목표 일은 저녁에 5/5 지켰다면 운동은 저녁 쪽 점수가 오른다.
     */
    static double candidateEfficiency(PatternEvidence ev, Long goalId, HourlyEfficiency eff, LocalDateTime s, LocalDateTime e) {
        PatternEvidence.GoalHours g = ev.goal(goalId);
        if (g == null || g.total() < GOAL_MIN_SAMPLES) return eff.average(s, e);
        // 10분 단위로: 그 시(hour)의 목표 기록 (완료 + K×내 평소 효율) / (계획 + K) — 기록이 없는 시간대는 내 평소 효율 그대로
        double sum = 0;
        int n = 0;
        for (LocalDateTime t = s; t.isBefore(e); t = t.plusMinutes(10)) {
            int h = t.getHour();
            double user = eff.at(t);
            sum += (g.done()[h] + GOAL_K * user) / (g.planned()[h] + GOAL_K);
            n++;
        }
        return n == 0 ? eff.at(s) : sum / n;
    }

    /**
     * 나눠 넣은 Task 의 이유.
     *  예) "마감이 2일 남아서 한 번에 할 연속된 빈 시간이 없어 09:00–12:00, 13:00–14:00 두 번에 나눠 배치했어요."
     */
    static Placed explainSplit(PlacementCandidate c, List<DateTimeRange> segments) {
        List<ReasonCode> codes = new ArrayList<>();
        Importance imp = c.importance();
        if (imp.cause() != null) codes.add(imp.cause());
        codes.add(ReasonCode.SPLIT_BLOCK);
        if (c.durationAdjusted()) codes.add(ReasonCode.DURATION_ADJUSTED);
        String cause = ReasonComposer.cause(imp.cause(), imp.causeValue());
        String text = ReasonComposer.split(cause, segments,
                c.durationAdjusted() ? c.originalDurationMinutes() : null);
        DateTimeRange first = segments.get(0);
        DateTimeRange last = segments.get(segments.size() - 1);
        return new Placed(c.taskId(), first.start(), last.end(), List.copyOf(codes), text, List.copyOf(segments));
    }

    private static Placed explain(PlacementCandidate c, LocalDateTime start, LocalDateTime end,
                                  HourlyEfficiency eff, double dayAverage, boolean onlySlot, boolean preferred,
                                  PatternEvidence ev, boolean useMoves, boolean grouped) {
        List<ReasonCode> codes = new ArrayList<>();
        Importance imp = c.importance();
        if (imp.cause() != null) codes.add(imp.cause());

        double slotEff = eff.average(start, end);
        boolean clearlyHigh = slotEff >= dayAverage + HIGH_MARGIN;
        ReasonCode slot;
        if (onlySlot) slot = ReasonCode.ONLY_SLOT;
        else if (preferred) slot = ReasonCode.PREFERRED_TIME;
        else if (imp.level() == ImportanceLevel.LOW) slot = ReasonCode.LOW_LOAD_SLOT;
        else if (eff.coldStart()) slot = ReasonCode.DEFAULT_CURVE;
        else if (clearlyHigh) slot = ReasonCode.HIGH_EFFICIENCY;
        else if (imp.level() == ImportanceLevel.HIGH) slot = ReasonCode.BEST_REMAINING;
        else slot = ReasonCode.EARLY_SLOT;
        codes.add(slot);
        if (c.durationAdjusted()) codes.add(ReasonCode.DURATION_ADJUSTED);

        // 내 기록 근거
        PatternEvidence.GoalHours goal = ev.goal(c.goalId());
        PatternEvidence.Counts goalSlot = goal != null ? ev.goalSlot(c.goalId(), start, end) : null;
        boolean goalFit = goalSlot != null && goalSlot.planned() >= 3 && goalSlot.percent() >= 60;
        boolean moveHabit = useMoves && ev.moveAffinity(start, end) >= MOVE_HABIT_THRESHOLD;
        if (goalFit) codes.add(ReasonCode.GOAL_TIME_FIT);
        if (moveHabit) codes.add(ReasonCode.MOVE_HABIT);
        String evidence = ReasonComposer.evidence(
                ev.slot(start, end), ev.overallPercent(), ReasonComposer.hourLabel(start, end),
                imp.level() == ImportanceLevel.LOW, goalFit ? goal.name() : null, goalSlot, moveHabit);

        int percent = (int) Math.round(slotEff * 100);
        String window = c.preferredWindows().stream()
                .filter(w -> w.containsAll(start.toLocalTime(), endTimeOf(start, end)))
                .findFirst().map(Object::toString).orElse("");
        String text = ReasonComposer.compose(
                ReasonComposer.cause(imp.cause(), imp.causeValue()),
                ReasonComposer.slotPhrase(slot, start.getHour(), percent, window, imp.cause() != null),
                start,
                c.durationAdjusted() ? c.originalDurationMinutes() : null);
        // 마감이 여유 있어 덜 지키는 시간에 뒀다면, 잘 지키는 시간을 누구에게 남겼는지까지 말한다 (근거 문장에 이미 있으면 생략)
        //  (기록이 없거나 이 시간이 평균보다 잘 지키는 시간이면 사실과 달라서 붙이지 않는다)
        if (slot == ReasonCode.LOW_LOAD_SLOT && !eff.coldStart() && slotEff < dayAverage
                && !evidence.startsWith(ReasonComposer.LOW_LOAD_NOTE_PREFIX)) {
            evidence = evidence.isEmpty() ? ReasonComposer.LOW_LOAD_NOTE : ReasonComposer.LOW_LOAD_NOTE + " " + evidence;
        }
        if (!evidence.isEmpty()) text = text + " " + evidence;
        if (grouped) {
            codes.add(ReasonCode.GOAL_GROUPED);
            text = text + " 같은 목표의 일과 이어서 할 수 있게 붙여 뒀어요.";
        }
        return new Placed(c.taskId(), start, end, List.copyOf(codes), text);
    }

    private static boolean inPreferred(PlacementCandidate c, LocalDateTime s, LocalDateTime e) {
        if (c.preferredWindows() == null || c.preferredWindows().isEmpty()) return false;
        if (!s.toLocalDate().equals(e.minusNanos(1).toLocalDate())) return false; // 자정 넘김 슬롯은 제외
        LocalTime st = s.toLocalTime();
        LocalTime et = endTimeOf(s, e);
        return c.preferredWindows().stream().anyMatch(w -> w.containsAll(st, et));
    }

    private static LocalTime endTimeOf(LocalDateTime s, LocalDateTime e) {
        // 24:00 에 끝나는 슬롯은 23:59:59 로 비교 (LocalTime 은 24:00 이 없음)
        return e.toLocalDate().isAfter(s.toLocalDate()) ? LocalTime.MAX : e.toLocalTime();
    }

    private static boolean nearSameGoal(List<BusyTask> placed, Long goalId, LocalDateTime s, LocalDateTime e) {
        if (goalId == null) return false;
        LocalDateTime ns = s.minusMinutes(SAME_GOAL_NEAR_MINUTES);
        LocalDateTime ne = e.plusMinutes(SAME_GOAL_NEAR_MINUTES);
        for (BusyTask b : placed) {
            if (goalId.equals(b.goalId()) && b.range().start().isBefore(ne) && ns.isBefore(b.range().end())) {
                return true;
            }
        }
        return false;
    }

    /** 쉬는 시간 기준이 되는 앞뒤 일정: 고정 시간(점심 등) + 이미 자리를 차지한 Task */
    private static List<DateTimeRange> anchors(List<DateTimeRange> fixed, List<BusyTask> tasks) {
        List<DateTimeRange> out = new ArrayList<>(fixed);
        for (BusyTask b : tasks) out.add(b.range());
        return out;
    }

    /** 바로 앞 일정이 끝난 뒤나 바로 뒤 일정이 시작하기 전까지 쉬는 시간이 30분 이내인지 */
    static boolean compact(List<DateTimeRange> anchors, LocalDateTime s, LocalDateTime e) {
        for (DateTimeRange a : anchors) {
            long before = Duration.between(a.end(), s).toMinutes();   // 앞 일정 끝 → 이 블록 시작
            long after = Duration.between(e, a.start()).toMinutes();  // 이 블록 끝 → 뒤 일정 시작
            if ((before >= 0 && before <= COMPACT_REST_MINUTES) || (after >= 0 && after <= COMPACT_REST_MINUTES)) {
                return true;
            }
        }
        return false;
    }

    private static int ceilDiv(int a, int b) {
        return (a + b - 1) / b;
    }

    // ── 10분 칸 ──────────────────────────────────────────

    /** 하루 범위를 10분 단위 칸으로 나눈 지도. free[i] = i 번째 칸이 비었는지 */
    static final class Grid {
        private final LocalDateTime origin; // 0번 칸 시작 (10분 단위로 올림)
        private final boolean[] free;

        private Grid(LocalDateTime origin, int size) {
            this.origin = origin;
            this.free = new boolean[Math.max(size, 0)];
            Arrays.fill(this.free, true);
        }

        static Grid of(PlacementInput in) {
            LocalDateTime start = in.dayRange().start();
            if (in.notBefore() != null && in.notBefore().isAfter(start)) start = in.notBefore();
            LocalDateTime origin = roundUp(start);
            LocalDateTime end = in.dayRange().end();
            int size = origin.isBefore(end) ? (int) (Duration.between(origin, end).toMinutes() / SLOT_MINUTES) : 0;
            return new Grid(origin, size);
        }

        static LocalDateTime roundUp(LocalDateTime t) {
            LocalDateTime base = t.withSecond(0).withNano(0);
            if (base.isBefore(t)) base = base.plusMinutes(1);
            int rem = base.getMinute() % SLOT_MINUTES;
            return rem == 0 ? base : base.plusMinutes(SLOT_MINUTES - rem);
        }

        int size() {
            return free.length;
        }

        LocalDateTime timeOf(int i) {
            return origin.plusMinutes((long) i * SLOT_MINUTES);
        }

        /** 구간과 조금이라도 겹치는 칸 + 앞뒤 padSlots 칸을 막는다 */
        void block(DateTimeRange r, int padSlots) {
            if (free.length == 0) return;
            long fromMin = Duration.between(origin, r.start()).toMinutes();
            long toMin = Duration.between(origin, r.end()).toMinutes();
            int from = (int) Math.floorDiv(fromMin, SLOT_MINUTES) - padSlots;
            int to = (int) Math.floorDiv(toMin + SLOT_MINUTES - 1, SLOT_MINUTES) + padSlots; // exclusive
            for (int i = Math.max(from, 0); i < Math.min(to, free.length); i++) free[i] = false;
        }

        List<Integer> feasibleStarts(int need) {
            List<Integer> starts = new ArrayList<>();
            int run = 0;
            for (int i = 0; i < free.length; i++) {
                run = free[i] ? run + 1 : 0;
                if (run >= need) starts.add(i - need + 1);
            }
            return starts;
        }

        /**
         * 연속된 빈 시간이 없을 때 나눠 넣을 조각 [시작 칸, 칸 수].
         * 30분 이상 비어 있는 구간을 시간 순으로 이어 붙여 need 칸을 채운다.
         *  - 조각 수가 적을수록, 첫 조각 시작 ~ 마지막 조각 끝이 짧을수록(= 최대한 연속으로) 좋다. 같으면 이른 시각
         *  - 첫 조각은 그 빈 구간 끝까지, 마지막 조각은 빈 구간 처음부터 → 조각 사이가 고정 시간만큼만 벌어진다
         * 채울 수 없으면 null
         */
        List<int[]> bestSplit(int need) {
            List<int[]> runs = new ArrayList<>();
            int runStart = -1;
            for (int i = 0; i <= free.length; i++) {
                boolean f = i < free.length && free[i];
                if (f && runStart < 0) runStart = i;
                if (!f && runStart >= 0) {
                    if (i - runStart >= MIN_PIECE_SLOTS) runs.add(new int[]{runStart, i - runStart});
                    runStart = -1;
                }
            }
            List<int[]> best = null;
            int bestSpan = Integer.MAX_VALUE;
            for (int from = 0; from < runs.size(); from++) {
                List<int[]> pieces = new ArrayList<>();
                int left = need;
                for (int j = from; j < runs.size() && left > 0 && pieces.size() < MAX_PIECES; j++) {
                    int[] run = runs.get(j);
                    int take = Math.min(run[1], left);
                    // 마지막 조각이 30분보다 짧아지면 앞 조각을 줄여 30분을 맞춘다 (자투리 10분 블록 방지)
                    if (left - take == 0 && take < MIN_PIECE_SLOTS && !pieces.isEmpty()) {
                        int[] prev = pieces.get(pieces.size() - 1);
                        int borrow = MIN_PIECE_SLOTS - take;
                        if (prev[1] - borrow >= MIN_PIECE_SLOTS && run[1] >= MIN_PIECE_SLOTS) {
                            // 첫 조각은 끝을 다음 조각 쪽에 붙여 둔 채 앞을 줄이고, 가운데 조각은 끝을 줄인다
                            if (pieces.size() == 1) prev[0] += borrow;
                            prev[1] -= borrow;
                            take = MIN_PIECE_SLOTS;
                        }
                    }
                    // 앞 조각을 줄여도 30분을 못 맞추면 이 출발점은 버린다 (30분 미만 조각은 만들지 않음)
                    if (take < MIN_PIECE_SLOTS) {
                        pieces.clear();
                        break;
                    }
                    // 첫 조각은 빈 구간 끝쪽에 붙여 다음 조각과 가깝게
                    int start = pieces.isEmpty() && j == from && take < run[1] ? run[0] + run[1] - take : run[0];
                    pieces.add(new int[]{start, take});
                    left -= take;
                }
                if (left > 0 || pieces.size() < 2) continue;
                int[] last = pieces.get(pieces.size() - 1);
                int span = last[0] + last[1] - pieces.get(0)[0];
                if (best == null || pieces.size() < best.size()
                        || (pieces.size() == best.size() && span < bestSpan)) {
                    best = pieces;
                    bestSpan = span;
                }
            }
            return best;
        }

        int longestFreeRun() {
            int best = 0, run = 0;
            for (boolean f : free) {
                run = f ? run + 1 : 0;
                best = Math.max(best, run);
            }
            return best;
        }
    }
}
