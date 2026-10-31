package com.naleul.naleul.domain.planner.support;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** 같은 시각에 몇 개의 구간이 동시에 놓여 있는지 */
public final class Concurrency {

    /** 한 시각에 같이 놓을 수 있는 (밀어낼 수 없는) Task 최대 개수 — 드래그로 2개까지 겹쳐 둘 수 있다 */
    public static final int MAX_TASKS_AT_ONCE = 2;

    private Concurrency() {
    }

    /**
     * window 안에서 ranges 가 가장 많이 겹치는 순간의 개수.
     * 끝과 시작이 같은 구간(10~11, 11~12)은 겹치지 않는 것으로 본다 (끝 이벤트를 먼저 처리).
     */
    public static int maxOverlap(Collection<DateTimeRange> ranges, DateTimeRange window) {
        List<long[]> events = new ArrayList<>(); // [시각(epoch 분), +1/-1]
        for (DateTimeRange r : ranges) {
            if (!r.overlaps(window)) continue;
            LocalDateTime s = r.start().isAfter(window.start()) ? r.start() : window.start();
            LocalDateTime e = r.end().isBefore(window.end()) ? r.end() : window.end();
            events.add(new long[]{key(s), 1});
            events.add(new long[]{key(e), -1});
        }
        events.sort((a, b) -> a[0] != b[0] ? Long.compare(a[0], b[0]) : Long.compare(a[1], b[1]));
        int cur = 0, max = 0;
        for (long[] ev : events) {
            cur += (int) ev[1];
            max = Math.max(max, cur);
        }
        return max;
    }

    private static long key(LocalDateTime t) {
        return t.toEpochSecond(java.time.ZoneOffset.UTC);
    }
}
