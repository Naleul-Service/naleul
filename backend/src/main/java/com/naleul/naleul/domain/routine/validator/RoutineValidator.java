package com.naleul.naleul.domain.routine.validator;

import com.naleul.naleul.domain.generalCategory.entity.GeneralCategory;
import com.naleul.naleul.domain.routine.dto.request.RoutineCreateRequest;
import com.naleul.naleul.domain.routine.dto.request.RoutineDayTime;
import com.naleul.naleul.domain.routine.dto.request.RoutineUpdateRequest;
import com.naleul.naleul.domain.routine.entity.Routine;
import com.naleul.naleul.domain.routine.repository.RoutineRepository;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import com.naleul.naleul.global.policy.PlanLimits;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@RequiredArgsConstructor
@Component
public class RoutineValidator {

    private final RoutineRepository routineRepository;

    public void validateCreate(RoutineCreateRequest request, User user, GeneralCategory generalCategory) {
        validateRoutineLimit(user, generalCategory);
        validateDateRange(request.getRepeatStartDate(), request.getRepeatEndDate());
        validateTimeRange(request.getRepeatStartTime(), request.getRepeatEndTime());
        validateDayTimes(request.getDayTimes());
    }

    public void validateUpdate(RoutineUpdateRequest request, Routine routine) {
        LocalDate startDate = request.getRepeatStartDate() != null
                ? request.getRepeatStartDate() : routine.getRepeatStartDate();
        LocalDate endDate = request.getRepeatEndDate() != null
                ? request.getRepeatEndDate() : routine.getRepeatEndDate();
        validateDateRange(startDate, endDate);

        LocalTime startTime = request.getRepeatStartTime() != null
                ? request.getRepeatStartTime() : routine.getRepeatStartTime();
        LocalTime endTime = request.getRepeatEndTime() != null
                ? request.getRepeatEndTime() : routine.getRepeatEndTime();
        validateTimeRange(startTime, endTime);
        validateDayTimes(request.getDayTimes());
    }

    /** 요일별 시간: 시작·종료 둘 다 있어야 하고, 끝이 더 이르면 자정을 넘기는 루틴 (12시간까지) */
    private void validateDayTimes(List<RoutineDayTime> dayTimes) {
        if (dayTimes == null) return;
        for (RoutineDayTime dt : dayTimes) {
            if (dt == null || dt.dayOfWeek() == null || dt.startTime() == null || dt.endTime() == null
                    || !isValidTimeRange(dt.startTime(), dt.endTime())) {
                throw new CustomException(ErrorCode.ROUTINE_INVALID_TIME);
            }
        }
    }

    private void validateRoutineLimit(User user, GeneralCategory generalCategory) {
        if (user.isCurrentlyPremium()) {
            return;
        }

        long currentCount = routineRepository.countActiveByGeneralCategoryId(generalCategory.getGeneralCategoryId());
        if (currentCount >= PlanLimits.ROUTINE_FREE_LIMIT) {
            throw new CustomException(ErrorCode.ROUTINE_LIMIT_EXCEEDED);
        }
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new CustomException(ErrorCode.ROUTINE_INVALID_DATE);
        }
    }

    /** 자정을 넘기는 루틴은 12시간까지 (23:00 ~ 00:00, 22:00 ~ 02:00). 그보다 길면 시작 · 끝을 바꿔 적은 실수로 봐요 */
    static final int MAX_OVERNIGHT_MINUTES = 12 * 60;

    /** 끝이 시작보다 이르면 자정을 넘기는 루틴 (23:00 ~ 00:00 = 오후 11시 ~ 오전 12시) */
    static boolean isValidTimeRange(LocalTime startTime, LocalTime endTime) {
        if (startTime == null || endTime == null) return true;
        if (startTime.equals(endTime)) return false;
        if (endTime.isAfter(startTime)) return true;
        long overnight = 24 * 60 - java.time.Duration.between(endTime, startTime).toMinutes();
        return overnight <= MAX_OVERNIGHT_MINUTES;
    }

    private void validateTimeRange(LocalTime startTime, LocalTime endTime) {
        if (!isValidTimeRange(startTime, endTime)) {
            throw new CustomException(ErrorCode.ROUTINE_INVALID_TIME);
        }
    }
}