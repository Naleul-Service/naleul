package com.naleul.naleul.domain.routine.util;

import com.naleul.naleul.domain.routine.entity.Routine;
import com.naleul.naleul.domain.routineRepeatDay.entity.RoutineRepeatDay;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RoutineStreakCalculator {

    private RoutineStreakCalculator() {
    }

    /**
     * 루틴의 현재 연속 달성일수를 계산.
     * (기존 로직 그대로 유지)
     */
    public static int calculateCurrentStreak(Routine routine, List<LocalDate> completedDates) {
        Set<DayOfWeek> scheduledDays = extractScheduledDays(routine);
        if (scheduledDays.isEmpty()) {
            return 0;
        }

        Set<LocalDate> completedSet = new HashSet<>(completedDates);

        LocalDate today = LocalDate.now();
        LocalDate cursor = today.isAfter(routine.getRepeatEndDate())
                ? routine.getRepeatEndDate()
                : today;

        int streak = 0;
        boolean isFirstScheduledDay = true;

        while (!cursor.isBefore(routine.getRepeatStartDate())) {
            if (scheduledDays.contains(cursor.getDayOfWeek())) {
                boolean isCompleted = completedSet.contains(cursor);

                if (isCompleted) {
                    streak++;
                } else if (isFirstScheduledDay && cursor.equals(today)) {
                    // 오늘이 실행일인데 아직 미완료 -> 건너뜀
                } else {
                    break;
                }

                isFirstScheduledDay = false;
            }
            cursor = cursor.minusDays(1);
        }

        return streak;
    }

    /**
     * 루틴이 시작된 이후 지금까지 통틀어서 가장 길게 이어졌던 연속 달성일수를 계산.
     *
     * 현재 스트릭과 다르게, "최신부터 거꾸로"가 아니라
     * "시작일부터 끝까지 순서대로" 훑으면서 진행 중인 연속 기록을 계속 갱신하고,
     * 그 중 가장 큰 값을 최종적으로 반환함.
     *
     * 오늘 날짜는 아직 하루가 끝나지 않았을 수 있으므로 계산 대상에서 제외함
     * (완료 못 했다고 해서 "끊긴 것"으로 잘못 처리되는 걸 방지).
     */
    public static int calculateLongestStreak(Routine routine, List<LocalDate> completedDates) {
        Set<DayOfWeek> scheduledDays = extractScheduledDays(routine);
        if (scheduledDays.isEmpty()) {
            return 0;
        }

        Set<LocalDate> completedSet = new HashSet<>(completedDates);

        LocalDate today = LocalDate.now();
        // 오늘까지 포함하면 "아직 안 한 오늘"이 끊긴 것으로 오인될 수 있으므로,
        // 계산 종료 지점을 어제까지로 제한 (단, 루틴이 이미 끝났으면 종료일까지)
        LocalDate endBoundary = routine.getRepeatEndDate().isBefore(today)
                ? routine.getRepeatEndDate()
                : today.minusDays(1);

        int longestStreak = 0;
        int currentRunningStreak = 0;

        LocalDate cursor = routine.getRepeatStartDate();
        while (!cursor.isAfter(endBoundary)) {
            if (scheduledDays.contains(cursor.getDayOfWeek())) {
                if (completedSet.contains(cursor)) {
                    currentRunningStreak++;
                    longestStreak = Math.max(longestStreak, currentRunningStreak);
                } else {
                    currentRunningStreak = 0; // 완료 못 했으면 이어지던 기록 리셋
                }
            }
            cursor = cursor.plusDays(1);
        }

        return longestStreak;
    }

    /**
     * ✅ 신규: 지정된 기간(rangeStart~rangeEnd) 안에서만의 최대 연속 달성일수를 계산.
     * 월간 리포트처럼 "이번 4주 안에서의 최대 연속"이 필요할 때 사용.
     *
     * "요청한 기간(rangeStart~rangeEnd)"과 "루틴이 실제로 존재했던 기간
     * (routine.getRepeatStartDate()~routine.getRepeatEndDate())"의 교집합만
     * 계산 대상으로 삼는다 (clamp). 예를 들어 루틴이 3월부터 진행 중인데
     * rangeStart가 6월이라면, 3월~5월은 계산하지 않고 6월 범위 안에서만 계산한다.
     */
    public static int calculateLongestStreakInRange(
            Routine routine, List<LocalDate> completedDates, LocalDate rangeStart, LocalDate rangeEnd
    ) {
        Set<DayOfWeek> scheduledDays = extractScheduledDays(routine);
        if (scheduledDays.isEmpty()) {
            return 0;
        }

        Set<LocalDate> completedSet = new HashSet<>(completedDates);

        // 요청 범위와 루틴 자체 기간의 "교집합"만 계산 대상으로 삼음
        LocalDate clampedStart = rangeStart.isAfter(routine.getRepeatStartDate())
                ? rangeStart : routine.getRepeatStartDate();
        LocalDate clampedEnd = rangeEnd.isBefore(routine.getRepeatEndDate())
                ? rangeEnd : routine.getRepeatEndDate();

        // 교집합이 없는 경우 (루틴 기간과 요청 기간이 아예 안 겹칠 때) 방어 처리
        if (clampedStart.isAfter(clampedEnd)) {
            return 0;
        }

        int longestStreak = 0;
        int currentRunningStreak = 0;

        LocalDate cursor = clampedStart;
        while (!cursor.isAfter(clampedEnd)) {
            if (scheduledDays.contains(cursor.getDayOfWeek())) {
                if (completedSet.contains(cursor)) {
                    currentRunningStreak++;
                    longestStreak = Math.max(longestStreak, currentRunningStreak);
                } else {
                    currentRunningStreak = 0;
                }
            }
            cursor = cursor.plusDays(1);
        }

        return longestStreak;
    }

    private static Set<DayOfWeek> extractScheduledDays(Routine routine) {
        Set<DayOfWeek> days = new HashSet<>();
        for (RoutineRepeatDay repeatDay : routine.getRepeatDays()) {
            days.add(repeatDay.getDayOfWeek());
        }
        return days;
    }
}