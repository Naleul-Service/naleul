package com.naleul.naleul.domain.goalProgress.service;

import com.naleul.naleul.domain.goalProgress.dto.response.GoalProgressResponse.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 목표 진행 도표 계산 (DB · 스프링 없이 순수 계산 → 단위 테스트 대상).
 */
public final class GoalProgressCalculator {

    /** 계획 대비 ±5% 안이면 "계획대로" */
    static final BigDecimal ON_TRACK_RATIO = new BigDecimal("0.05");
    /** 추세(도달 예상일)는 최근 28일 기록으로, 3개 이상 · 4일 이상 걸쳐 있어야 계산 */
    static final int TREND_WINDOW_DAYS = 28;
    static final int TREND_MIN_LOGS = 3;
    static final int TREND_MIN_SPAN_DAYS = 4;
    /** Task 누적 그래프: 이보다 길면 주 단위로 줄인다 */
    static final int DAILY_SERIES_MAX_DAYS = 120;

    private GoalProgressCalculator() {
    }

    // ── 입력 ──────────────────────────────────────────────

    public record MetricInput(String name, String unit,
                              BigDecimal startValue, BigDecimal currentValue, BigDecimal targetValue) {
    }

    public record MilestoneInput(String title, LocalDate dueDate, BigDecimal targetValue) {
    }

    /** plannedDate: 하기로 한 날 (없으면 null), doneDate: 완료한 날 (미완료면 null) */
    public record TaskInput(LocalDate plannedDate, LocalDate doneDate) {
    }

    // ── 수치 목표 ──────────────────────────────────────────

    public static MetricProgress metric(LocalDate today, LocalDate startDate, LocalDate endDate,
                                        MetricInput m, List<MilestoneInput> milestones, List<Log> logs) {
        if (m == null || m.startValue() == null || m.targetValue() == null) return null;

        List<Log> sorted = logs.stream().sorted(Comparator.comparing(Log::date)).toList();
        Log last = sorted.isEmpty() ? null : sorted.get(sorted.size() - 1);
        BigDecimal current = m.currentValue() != null ? m.currentValue()
                : last != null ? last.value() : m.startValue();

        List<Point> planLine = planLine(startDate, endDate, m, milestones);
        BigDecimal expected = (endDate != null && planLine.size() >= 2) ? expectedAt(planLine, today) : null;

        return new MetricProgress(
                m.name(), m.unit(), m.startValue(), m.targetValue(), current,
                expected == null ? null : expected.setScale(2, RoundingMode.HALF_UP),
                percent(m.startValue(), current, m.targetValue()),
                status(today, startDate, m.startValue(), current, m.targetValue(), expected),
                projectedDate(today, m.targetValue(), current, m.startValue(), sorted),
                last == null ? null : last.date(),
                last != null && last.date().equals(today),
                planLine,
                sorted,
                endDate);
    }

    /** 시작(시작일, 시작값) → 수치가 있는 마일스톤 → (종료일, 목표값) */
    static List<Point> planLine(LocalDate startDate, LocalDate endDate, MetricInput m, List<MilestoneInput> milestones) {
        List<Point> points = new ArrayList<>();
        if (startDate == null) return points;
        points.add(new Point(startDate, m.startValue(), "시작"));

        milestones.stream()
                .filter(ms -> ms.dueDate() != null && ms.targetValue() != null && ms.dueDate().isAfter(startDate))
                .filter(ms -> endDate == null || !ms.dueDate().isAfter(endDate))
                .sorted(Comparator.comparing(MilestoneInput::dueDate))
                .forEach(ms -> {
                    Point prev = points.get(points.size() - 1);
                    if (prev.date().equals(ms.dueDate())) points.remove(points.size() - 1);   // 같은 날 마일스톤은 뒤의 것
                    points.add(new Point(ms.dueDate(), ms.targetValue(), ms.title()));
                });

        if (endDate != null && endDate.isAfter(startDate)) {
            Point prev = points.get(points.size() - 1);
            if (prev.date().equals(endDate)) {
                // 마지막 마일스톤 = 종료일이면 그 점을 목표값으로 맞춘다 (사용자가 목표값만 바꾼 경우 대비)
                points.set(points.size() - 1, new Point(endDate, m.targetValue(), prev.label()));
            } else {
                points.add(new Point(endDate, m.targetValue(), "목표"));
            }
        }
        return points;
    }

    /** 꺾은선 위 date 시점의 값. 범위 밖이면 양 끝 값 */
    static BigDecimal expectedAt(List<Point> line, LocalDate date) {
        if (!date.isAfter(line.get(0).date())) return line.get(0).value();
        for (int i = 1; i < line.size(); i++) {
            Point a = line.get(i - 1), b = line.get(i);
            if (!date.isAfter(b.date())) {
                long span = ChronoUnit.DAYS.between(a.date(), b.date());
                long passed = ChronoUnit.DAYS.between(a.date(), date);
                BigDecimal ratio = BigDecimal.valueOf(passed).divide(BigDecimal.valueOf(span), 6, RoundingMode.HALF_UP);
                return a.value().add(b.value().subtract(a.value()).multiply(ratio));
            }
        }
        return line.get(line.size() - 1).value();
    }

    static BigDecimal percent(BigDecimal start, BigDecimal current, BigDecimal target) {
        BigDecimal whole = target.subtract(start);
        if (whole.signum() == 0) return null;
        return current.subtract(start).multiply(BigDecimal.valueOf(100))
                .divide(whole, 1, RoundingMode.HALF_UP);
    }

    static Status status(LocalDate today, LocalDate startDate, BigDecimal start, BigDecimal current,
                         BigDecimal target, BigDecimal expected) {
        int dir = target.compareTo(start);              // -1 줄이기(감량), 1 늘리기
        if (dir != 0 && current.compareTo(target) * dir >= 0) return Status.ACHIEVED;
        if (dir == 0) return current.compareTo(target) == 0 ? Status.ACHIEVED : Status.NO_PLAN;
        if (startDate != null && today.isBefore(startDate)) return Status.NOT_STARTED;
        if (expected == null) return Status.NO_PLAN;

        BigDecimal tolerance = target.subtract(start).abs().multiply(ON_TRACK_RATIO);
        BigDecimal ahead = current.subtract(expected).multiply(BigDecimal.valueOf(dir));   // +면 앞섬
        if (ahead.compareTo(tolerance) > 0) return Status.AHEAD;
        if (ahead.compareTo(tolerance.negate()) < 0) return Status.BEHIND;
        return Status.ON_TRACK;
    }

    /**
     * 최근 기록의 직선 추세(최소제곱)로 목표값에 닿는 날.
     * 추세가 목표 반대 방향이거나 기록이 부족하면 null. 3년 넘게 걸리는 예측도 null.
     */
    static LocalDate projectedDate(LocalDate today, BigDecimal target, BigDecimal current,
                                   BigDecimal start, List<Log> sortedLogs) {
        int dir = target.compareTo(start);
        if (dir == 0 || current.compareTo(target) * dir >= 0) return null;

        LocalDate from = today.minusDays(TREND_WINDOW_DAYS);
        List<Log> recent = sortedLogs.stream().filter(l -> !l.date().isBefore(from) && !l.date().isAfter(today)).toList();
        if (recent.size() < TREND_MIN_LOGS) return null;
        LocalDate first = recent.get(0).date();
        if (ChronoUnit.DAYS.between(first, recent.get(recent.size() - 1).date()) < TREND_MIN_SPAN_DAYS) return null;

        int n = recent.size();
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (Log l : recent) {
            double x = ChronoUnit.DAYS.between(first, l.date());
            double y = l.value().doubleValue();
            sx += x; sy += y; sxx += x * x; sxy += x * y;
        }
        double denom = n * sxx - sx * sx;
        if (denom == 0) return null;
        double slope = (n * sxy - sx * sy) / denom;
        double intercept = (sy - slope * sx) / n;
        if (slope == 0 || Math.signum(slope) != dir) return null;

        double x = (target.doubleValue() - intercept) / slope;
        LocalDate projected = first.plusDays((long) Math.ceil(x - 1e-9));   // 부동소수 오차(14.0000001 → 15일) 방지
        if (projected.isBefore(today)) projected = today;
        if (projected.isAfter(today.plusYears(3))) return null;
        return projected;
    }

    // ── Task 진행 ─────────────────────────────────────────

    public static TaskProgress tasks(LocalDate today, LocalDate startDate, LocalDate endDate, List<TaskInput> tasks) {
        int total = tasks.size();
        int completed = (int) tasks.stream().filter(t -> t.doneDate() != null).count();
        int dueByToday = (int) tasks.stream()
                .filter(t -> t.doneDate() != null || (t.plannedDate() != null && !t.plannedDate().isAfter(today)))
                .count();
        BigDecimal rate = dueByToday == 0 ? null
                : BigDecimal.valueOf(completed * 100L).divide(BigDecimal.valueOf(dueByToday), 1, RoundingMode.HALF_UP);

        return new TaskProgress(total, completed, dueByToday, rate, series(today, startDate, endDate, tasks));
    }

    static List<TaskPoint> series(LocalDate today, LocalDate startDate, LocalDate endDate, List<TaskInput> tasks) {
        LocalDate from = startDate != null ? startDate
                : tasks.stream().map(TaskInput::plannedDate).filter(Objects::nonNull).min(LocalDate::compareTo).orElse(today);
        if (from.isAfter(today) && endDate == null) return List.of();
        LocalDate lastPlanned = tasks.stream().map(TaskInput::plannedDate).filter(Objects::nonNull)
                .max(LocalDate::compareTo).orElse(today);
        LocalDate to = endDate != null ? endDate : (lastPlanned.isAfter(today) ? lastPlanned : today);
        if (to.isBefore(from)) return List.of();

        long days = ChronoUnit.DAYS.between(from, to);
        int step = days > DAILY_SERIES_MAX_DAYS ? 7 : 1;

        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(step)) dates.add(d);
        if (!dates.get(dates.size() - 1).equals(to)) dates.add(to);
        // 주 단위로 줄여도 "오늘" 점은 꼭 넣는다 (현재 위치 표시용)
        if (step > 1 && !today.isBefore(from) && !today.isAfter(to) && !dates.contains(today)) {
            dates.add(today);
            dates.sort(LocalDate::compareTo);
        }

        List<TaskPoint> points = new ArrayList<>(dates.size());
        for (LocalDate d : dates) {
            int planned = (int) tasks.stream().filter(t -> t.plannedDate() != null && !t.plannedDate().isAfter(d)).count();
            Integer done = d.isAfter(today) ? null
                    : (int) tasks.stream().filter(t -> t.doneDate() != null && !t.doneDate().isAfter(d)).count();
            points.add(new TaskPoint(d, planned, done));
        }
        return points;
    }
}
