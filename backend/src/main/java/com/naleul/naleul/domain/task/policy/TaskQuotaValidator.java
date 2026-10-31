// domain/task/policy/TaskQuotaValidator.java
package com.naleul.naleul.domain.task.policy;

import com.naleul.naleul.domain.actualTask.repository.TaskActualRepository;
import com.naleul.naleul.domain.planner.repository.TimetableQueryRepository;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import com.naleul.naleul.global.policy.PlanLimits;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Free 플랜 Task 일일 생성 한도.
 *
 * ✅ 명세 3(7장)으로 변경: "직접 만든 Task" 개수를 계획 날짜가 아니라 **생성일(한국 시간 오늘)** 기준으로 센다.
 *    - 이전: planned_start_at 날짜 기준 → 시간 없는 Task(Brain dump)는 세지 못하고,
 *            AI 목표 Task 가 배치되면 그 날짜의 한도를 차지하는 문제가 있었음
 *    - 이후: created_at 이 오늘인 source=USER_INPUT, 루틴·미션 아닌 Task
 * 독립 실제 기록(TaskActual) 개수는 기존 방식 그대로 더한다.
 */
@Component
@RequiredArgsConstructor
public class TaskQuotaValidator {

    private static final int KST_OFFSET_HOURS = 9;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final TimetableQueryRepository timetableQueryRepository;
    private final TaskActualRepository taskActualRepository;

    /** 기존 호출부 호환용 (TaskService.createTask, TaskActualService) — 1개 추가 가능한지 */
    public void validateDailyManualQuota(User user, LocalDate targetDate) {
        validateCanCreate(user, 1, targetDate);
    }

    /** count 개를 한 번에 추가할 수 있는지 (Brain dump 확정) */
    public void validateCanCreate(User user, int count, LocalDate actualTargetDate) {
        if (user.isCurrentlyPremium() || PlanLimits.TASK_PER_DAY_FREE_LIMIT == PlanLimits.UNLIMITED) {
            return;
        }
        if (used(user, actualTargetDate) + count > PlanLimits.TASK_PER_DAY_FREE_LIMIT) {
            throw new CustomException(ErrorCode.TASK_DAILY_LIMIT_EXCEEDED);
        }
    }

    /** 남은 개수 (Pro 면 null = 무제한) */
    public Integer remaining(User user) {
        if (user.isCurrentlyPremium() || PlanLimits.TASK_PER_DAY_FREE_LIMIT == PlanLimits.UNLIMITED) return null;
        return (int) Math.max(PlanLimits.TASK_PER_DAY_FREE_LIMIT - used(user, LocalDate.now(KST)), 0);
    }

    public long used(User user, LocalDate actualTargetDate) {
        LocalDate today = LocalDate.now(KST);
        long manual = timetableQueryRepository.countManualCreatedBetween(
                user.getUserId(), today.atStartOfDay(), today.plusDays(1).atStartOfDay());

        LocalDate actualDate = actualTargetDate != null ? actualTargetDate : today;
        long independentActual = taskActualRepository.countIndependentActualsByUserAndDateRange(
                user.getUserId(), toKstDayStart(actualDate), toKstDayEnd(actualDate));
        return manual + independentActual;
    }

    /**
     * ✅ 추가 — 푸시 알림 기능은 Pro 전용.
     * Free 유저가 notificationEnabled를 true로 설정(또는 null로 두어 기본값 true가 적용)하려는
     * 모든 경우를 차단합니다. 개수 제한이 아니라 기능 자체의 on/off 권한이라 카운트 로직은 없습니다.
     *
     * @param user 검증 대상 유저
     * @param effectiveNotificationEnabled Service 단에서 null 처리까지 끝낸 "최종 반영될 값"
     */
    public void validatePushNotificationAccess(User user, boolean effectiveNotificationEnabled) {
        // BM(2026-10): 푸시 알림은 Free 도 쓸 수 있다 (AI 만 횟수 제한). 다시 막을 때를 위해 자리만 남김
    }

    // 독립 실제 기록은 기존 시간 처리(UTC 저장 가정)를 그대로 유지
    private LocalDateTime toKstDayStart(LocalDate date) {
        return date.atStartOfDay().minusHours(KST_OFFSET_HOURS);
    }

    private LocalDateTime toKstDayEnd(LocalDate date) {
        return date.plusDays(1).atStartOfDay().minusHours(KST_OFFSET_HOURS);
    }
}
