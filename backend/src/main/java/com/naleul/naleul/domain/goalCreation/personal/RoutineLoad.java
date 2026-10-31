package com.naleul.naleul.domain.goalCreation.personal;

import com.naleul.naleul.domain.routine.entity.Routine;
import com.naleul.naleul.domain.routine.enums.RoutineStatus;

import java.time.Duration;
import java.time.LocalDate;

/** 루틴 하나의 주간 부담(분) — 개인 상한 · 목표 결과 스냅샷에서 같이 쓴다 */
public final class RoutineLoad {

    /** 소요 시간도 시작·끝 시각도 없는 루틴은 30분으로 본다 */
    static final int UNKNOWN_MINUTES = 30;

    private RoutineLoad() {
    }

    public static int weeklyMinutes(Routine r) {
        int days = r.getRepeatDays() == null ? 0 : r.getRepeatDays().size();
        return minutesPerSession(r) * days;
    }

    public static int minutesPerSession(Routine r) {
        if (r.getDurationMinutes() != null && r.getDurationMinutes() > 0) return r.getDurationMinutes();
        if (r.getRepeatStartTime() != null && r.getRepeatEndTime() != null) {
            long m = Duration.between(r.getRepeatStartTime(), r.getRepeatEndTime()).toMinutes();
            if (m <= 0) m += 24 * 60;   // 자정을 넘기는 루틴
            return (int) m;
        }
        return UNKNOWN_MINUTES;
    }

    /** 지금 진행 중(또는 곧 시작)이고 아직 끝나지 않은 루틴 */
    public static boolean isActive(Routine r, LocalDate today) {
        boolean status = r.getRoutineStatus() == RoutineStatus.NOT_STARTED
                || r.getRoutineStatus() == RoutineStatus.IN_PROGRESS;
        return status && (r.getRepeatEndDate() == null || !r.getRepeatEndDate().isBefore(today));
    }
}
