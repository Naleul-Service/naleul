package com.naleul.naleul.domain.planner.engine;

import java.time.LocalTime;
import java.util.List;

/**
 * 배치할 Task 하나 (엔진 입력).
 *
 * @param goalId                같은 목표 Task 를 붙여 놓지 않기 위한 값 (없으면 null)
 * @param durationMinutes       배치할 길이 (DURATION_ADJUSTED 반영 후)
 * @param originalDurationMinutes 늘리기 전 길이 (늘리지 않았으면 durationMinutes 와 같음)
 * @param preferredWindows      목표 생성 때 고른 선호 시간대 (없으면 빈 목록)
 * @param splittable            연속된 빈 시간이 없을 때 나눠 넣어도 되는지 (약속 일정은 false)
 */
public record PlacementCandidate(
        Long taskId,
        Long goalId,
        int durationMinutes,
        int originalDurationMinutes,
        Importance importance,
        List<Window> preferredWindows,
        boolean splittable
) {
    public PlacementCandidate(Long taskId, Long goalId, int durationMinutes, int originalDurationMinutes,
                              Importance importance, List<Window> preferredWindows) {
        this(taskId, goalId, durationMinutes, originalDurationMinutes, importance, preferredWindows, true);
    }

    public record Window(LocalTime start, LocalTime end) {
        /** [start, end] 안에 slotStart~slotEnd 가 모두 들어가는지 (자정 넘김 창은 지원하지 않음) */
        boolean containsAll(LocalTime slotStart, LocalTime slotEnd) {
            return !slotStart.isBefore(start) && !slotEnd.isAfter(end) && !slotEnd.isBefore(slotStart);
        }

        @Override
        public String toString() {
            return String.format("%02d:%02d~%02d:%02d", start.getHour(), start.getMinute(), end.getHour(), end.getMinute());
        }
    }

    public boolean durationAdjusted() {
        return durationMinutes != originalDurationMinutes;
    }
}
