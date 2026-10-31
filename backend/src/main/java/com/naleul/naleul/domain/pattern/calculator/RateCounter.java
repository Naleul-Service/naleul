package com.naleul.naleul.domain.pattern.calculator;

/** 완료 수 / 전체 수 누적기 */
public final class RateCounter {

    private int total;
    private int done;

    public RateCounter add(boolean completed) {
        total++;
        if (completed) done++;
        return this;
    }

    public RateCounter merge(RateCounter other) {
        total += other.total;
        done += other.done;
        return this;
    }

    public int total() {
        return total;
    }

    public int done() {
        return done;
    }

    /** 0.0 ~ 1.0, 표본이 없으면 0 */
    public double ratio() {
        return total == 0 ? 0 : (double) done / total;
    }

    /** 반올림한 % (0~100), 표본이 없으면 null */
    public Integer percent() {
        return total == 0 ? null : (int) Math.round(ratio() * 100);
    }
}
