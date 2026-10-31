package com.naleul.naleul.domain.planner.engine;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 배치 이유를 "내 기록"으로 설명하기 위한 원자료 (최근 4주, 보정하지 않은 실제 횟수).
 *
 * HourlyEfficiency 는 배치 점수용이라 표본이 적은 시간대를 평균 쪽으로 당긴 값이다 (그대로 보여주면 "82%" 같은
 * 숫자가 실제 기록과 안 맞아 보인다). 사람에게 보여줄 때는 여기 있는 "계획 n개 중 m개 완료"를 그대로 쓴다.
 *
 * @param planned   시(0~23)별 계획한 Task 수 (요일 구분 없이)
 * @param done      시별 그중 완료한 수
 * @param goals     목표별 시간대 기록 — 같은 목표의 일을 잘 지키던 시간대에 놓으려고
 * @param movedIn   사용자가 블록을 그 시간대로 옮겨 온 횟수 (시작 시각 기준)
 * @param movedOut  그 시간대에서 다른 곳으로 옮겨 간 횟수
 */
public record PatternEvidence(int[] planned, int[] done, Map<Long, GoalHours> goals,
                              int[] movedIn, int[] movedOut) {

    public record GoalHours(String name, int[] planned, int[] done) {
        public int total() {
            int n = 0;
            for (int p : planned) n += p;
            return n;
        }
    }

    /** 구간 안 시간대들의 합계 */
    public record Counts(int planned, int done) {
        public int percent() {
            return planned == 0 ? 0 : (int) Math.round(100.0 * done / planned);
        }
    }

    public static PatternEvidence empty() {
        return new PatternEvidence(new int[24], new int[24], Map.of(), new int[24], new int[24]);
    }

    public boolean isEmpty() {
        return totalPlanned() == 0 && moveCount() == 0;
    }

    public int totalPlanned() {
        int n = 0;
        for (int p : planned) n += p;
        return n;
    }

    /** 전체 완료율(%) — "평소보다 높아요" 비교용. 기록이 없으면 null */
    public Integer overallPercent() {
        int p = 0, d = 0;
        for (int h = 0; h < 24; h++) {
            p += planned[h];
            d += done[h];
        }
        return p == 0 ? null : (int) Math.round(100.0 * d / p);
    }

    public int moveCount() {
        int n = 0;
        for (int v : movedIn) n += v;
        return n;
    }

    public Counts slot(LocalDateTime s, LocalDateTime e) {
        return sum(planned, done, s, e);
    }

    public Counts goalSlot(Long goalId, LocalDateTime s, LocalDateTime e) {
        GoalHours g = goalId == null ? null : goals.get(goalId);
        return g == null ? new Counts(0, 0) : sum(g.planned(), g.done(), s, e);
    }

    public GoalHours goal(Long goalId) {
        return goalId == null ? null : goals.get(goalId);
    }

    /**
     * 옮기기 습관: 이 시간대로 옮겨 온 횟수 − 옮겨 간 횟수를 전체 이동 수로 나눈 값 (−1 ~ 1).
     * 양수면 "사용자가 이 시간대를 더 좋아한다", 음수면 "자주 빼는 시간대".
     */
    public double moveAffinity(LocalDateTime s, LocalDateTime e) {
        int total = moveCount();
        if (total == 0) return 0;
        // 10분 단위로 훑어 평균 (블록이 한 시간대에 살짝 걸친 것만으로 그 시간대 값을 다 받지 않게)
        double sum = 0;
        int n = 0;
        for (LocalDateTime t = s; t.isBefore(e); t = t.plusMinutes(10)) {
            int h = t.getHour();
            sum += (double) (movedIn[h] - movedOut[h]) / total;
            n++;
        }
        double avg = n == 0 ? 0 : sum / n;
        return Math.max(-1, Math.min(1, avg));
    }

    /** 구간이 걸친 시(hour)들 — 21:30~22:30 이면 21, 22 */
    static int[] hours(LocalDateTime s, LocalDateTime e) {
        int first = s.getHour();
        LocalDateTime last = e.minusMinutes(1);
        int lastHour = last.toLocalDate().isAfter(s.toLocalDate()) ? 23 : last.getHour();
        if (lastHour < first) lastHour = first;
        int[] out = new int[lastHour - first + 1];
        for (int i = 0; i < out.length; i++) out[i] = first + i;
        return out;
    }

    private static Counts sum(int[] p, int[] d, LocalDateTime s, LocalDateTime e) {
        int planned = 0, done = 0;
        for (int h : hours(s, e)) {
            planned += p[h];
            done += d[h];
        }
        return new Counts(planned, done);
    }
}
