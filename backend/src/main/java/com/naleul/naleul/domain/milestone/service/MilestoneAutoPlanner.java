package com.naleul.naleul.domain.milestone.service;

import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.milestone.entity.Milestone;
import com.naleul.naleul.domain.milestone.enums.MilestoneStatus;
import com.naleul.naleul.domain.milestone.repository.MilestoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * 점검 시점(마일스톤) 자동 보정 (V40).
 *
 * 왜 필요한가
 *  목표 상세에서 수치를 고치는 곳이 여러 군데(목표 시작·목표값, 마일스톤 수치)라서
 *  목표값을 80→72kg 에서 80→75kg 으로 바꿔도 마일스톤은 예전 값(72kg 기준)으로 남아 그래프가 지그재그가 됐다.
 *  사용자는 "시작값 · 목표값 · 종료일" 세 가지만 정하고, 나머지는 서버가 맞춘다.
 *
 * 규칙
 *  1) 날짜: 목표 기간 안으로 옮기고, 같은 날짜가 겹치면 하루씩 뒤로. 수치 목표면 마지막 시점 = 종료일.
 *  2) 수치(수치 목표일 때만): 시작값 → 목표값 직선 위의 값 (날짜 비율). 마지막 시점 = 목표값.
 *     - 사용자가 직접 정한 값(targetValueManual)은 그대로 둔다. 단 시작값~목표값 범위를 벗어나면 자동으로 되돌린다.
 *     - resetManual=true (목표의 시작값·목표값·종료일이 바뀜)면 직접 정한 값도 전부 다시 자동으로.
 *  3) 이미 달성(ACHIEVED)한 시점은 기록이라 날짜·수치를 건드리지 않는다.
 */
@Component
@RequiredArgsConstructor
public class MilestoneAutoPlanner {

    private final MilestoneRepository milestoneRepository;

    public void replan(GoalCategory goal, boolean resetManual) {
        List<Milestone> milestones = milestoneRepository
                .findAllByGoalCategory_GoalCategoryIdOrderBySortOrderAsc(goal.getGoalCategoryId());
        plan(goal.getGoalCategoryStartDate(), goal.getGoalCategoryEndDate(),
                goal.getStartValue(), goal.getTargetValue(), milestones, resetManual);
        reorder(milestones);
    }

    /** "수치 없이 진행하기" — 목표 수치를 지우면 점검 시점의 수치도 함께 비운다 */
    public void clearValues(GoalCategory goal) {
        milestoneRepository.findAllByGoalCategory_GoalCategoryIdOrderBySortOrderAsc(goal.getGoalCategoryId())
                .stream().filter(m -> !isAchieved(m)).forEach(Milestone::clearTargetValue);
    }

    /** DB 없이 테스트할 수 있는 본체 */
    static void plan(LocalDate start, LocalDate end, BigDecimal startValue, BigDecimal targetValue,
                     List<Milestone> milestones, boolean resetManual) {
        if (milestones.isEmpty()) return;
        List<Milestone> sorted = milestones.stream()
                .sorted(Comparator.comparing(Milestone::getDueDate)
                        .thenComparing(m -> m.getMilestoneId() == null ? Long.MAX_VALUE : m.getMilestoneId()))
                .toList();
        boolean hasMetric = startValue != null && targetValue != null && startValue.compareTo(targetValue) != 0;

        // 1) 날짜
        if (start != null && end != null && end.isAfter(start)) {
            LocalDate prev = null;
            for (Milestone m : sorted) {
                if (isAchieved(m)) {
                    prev = m.getDueDate();
                    continue;
                }
                LocalDate due = m.getDueDate();
                if (!due.isAfter(start)) due = start.plusDays(1);
                if (due.isAfter(end)) due = end;
                if (prev != null && !due.isAfter(prev)) due = prev.plusDays(1).isAfter(end) ? end : prev.plusDays(1);
                if (!due.equals(m.getDueDate())) m.moveDueDate(due);
                prev = due;
            }
            Milestone last = sorted.get(sorted.size() - 1);
            if (hasMetric && !isAchieved(last) && !last.getDueDate().equals(end)) last.moveDueDate(end);
        }

        // 2) 수치
        if (!hasMetric || start == null || end == null || !end.isAfter(start)) return;
        long total = ChronoUnit.DAYS.between(start, end);
        int scale = scaleOf(startValue, targetValue);
        BigDecimal low = startValue.min(targetValue);
        BigDecimal high = startValue.max(targetValue);

        for (int i = 0; i < sorted.size(); i++) {
            Milestone m = sorted.get(i);
            if (isAchieved(m)) continue;
            boolean isLast = i == sorted.size() - 1;
            if (isLast && m.getDueDate().equals(end)) {
                if (m.getTargetValue() == null || m.getTargetValue().compareTo(targetValue) != 0 || m.isTargetValueManual()) {
                    m.applyAutoTargetValue(targetValue);
                }
                continue;
            }
            boolean keepManual = m.isTargetValueManual() && !resetManual && m.getTargetValue() != null
                    && m.getTargetValue().compareTo(low) >= 0 && m.getTargetValue().compareTo(high) <= 0;
            if (keepManual) continue;

            long days = Math.max(0, Math.min(total, ChronoUnit.DAYS.between(start, m.getDueDate())));
            BigDecimal ratio = BigDecimal.valueOf(days).divide(BigDecimal.valueOf(total), 10, RoundingMode.HALF_UP);
            BigDecimal value = startValue.add(targetValue.subtract(startValue).multiply(ratio))
                    .setScale(scale, RoundingMode.HALF_UP);
            if (m.getTargetValue() == null || m.getTargetValue().compareTo(value) != 0 || m.isTargetValueManual()) {
                m.applyAutoTargetValue(value);
            }
        }
    }

    /** 80 → 72 면 정수, 80.5 → 72 면 소수 첫째 자리 (최대 2자리) */
    static int scaleOf(BigDecimal a, BigDecimal b) {
        int sa = Math.max(a.stripTrailingZeros().scale(), 0);
        int sb = Math.max(b.stripTrailingZeros().scale(), 0);
        return Math.min(Math.max(sa, sb), 2);
    }

    private static boolean isAchieved(Milestone m) {
        return m.getStatus() == MilestoneStatus.ACHIEVED;
    }

    private static void reorder(List<Milestone> milestones) {
        List<Milestone> sorted = milestones.stream()
                .sorted(Comparator.comparing(Milestone::getDueDate)
                        .thenComparing(m -> m.getMilestoneId() == null ? Long.MAX_VALUE : m.getMilestoneId()))
                .toList();
        for (int i = 0; i < sorted.size(); i++) sorted.get(i).changeSortOrder(i);
    }
}
