package com.naleul.naleul.domain.planner.service;

import com.naleul.naleul.domain.lifePattern.service.FixedBlockResolver;
import com.naleul.naleul.domain.lifePattern.support.DayFixedBlocks;
import com.naleul.naleul.domain.planner.support.MorningCarryTime;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus;
import com.naleul.naleul.domain.pattern.service.TaskScheduleLogWriter;
import com.naleul.naleul.domain.planner.entity.DailyPlan;
import com.naleul.naleul.domain.planner.entity.PlannerSetting;
import com.naleul.naleul.domain.planner.repository.NightlyQueryRepository;
import com.naleul.naleul.domain.planner.support.PlannerClock;
import com.naleul.naleul.domain.task.entity.Task;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 사용자 한 명의 "내일 TimeTable 생성" (명세 6-6). 사용자마다 새 트랜잭션 → 한 명이 실패해도 다음 사용자는 진행.
 *
 *  0. 설정 행 잠금 + 자동 생성 토글 확인 (꺼져 있으면 아무것도 안 함)
 *  1. daily_plan(내일) 선점 — 이미 오늘 저녁 작업으로 만들었으면 건너뜀 (블루그린 중복 실행 방지)
 *  2. (2026-10 변경) 저녁엔 이월하지 않는다 — 못 한 일은 다음 날 아침 기상 시간에 morningCarry 가 옮긴다
 *  3. 내일 다시 배치 (REPLAN): 잠기지 않은 블록 + 시간 없는 Task(이월분, AI 목표 Task, 시간 없는 AI 루틴 포함)
 *  4. daily_plan GENERATED + 요약 문구
 * 푸시는 커밋이 끝난 뒤 스케줄러가 보낸다 (롤백됐는데 알림만 가는 일이 없도록).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NightlyPlanWorker {

    private final PlannerSettingService plannerSettingService;
    private final DailyPlanService dailyPlanService;
    private final DayPlacementService dayPlacementService;
    private final NightlyQueryRepository nightlyQueryRepository;
    private final PlannerClock clock;
    /** 나의 패턴: 이월 기록 (원래 날짜의 놓침으로 집계, 명세 4 · 1-3) */
    private final TaskScheduleLogWriter scheduleLogWriter;
    /** 아침 이월: 기상 시각 (기본 생활 패턴의 수면 끝) */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private FixedBlockResolver fixedBlockResolver;

    public record Result(Long userId, boolean generated, boolean pushEnabled,
                         int carried, int placed, int unplaced, String summary) {
        static Result skipped(Long userId) {
            return new Result(userId, false, false, 0, 0, 0, null);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Result run(Long userId, LocalDate today, boolean force) {
        LocalDate tomorrow = today.plusDays(1);
        PlannerSetting setting = plannerSettingService.getOrCreateForUpdate(userId);
        if (!setting.isAutoGenerateEnabled()) return Result.skipped(userId);

        DailyPlan plan = dailyPlanService.claimNightly(userId, tomorrow, today, force);
        if (plan == null) return Result.skipped(userId);

        // 2026-10: 저녁에는 이월하지 않는다 — 새벽까지 하는 일이 사라지지 않게, 다음 날 아침 기상 시간에 옮긴다 (morningCarry)
        int carried = 0;
        PlacementOutcome out = dayPlacementService.placeDay(userId, tomorrow, PlacementMode.REPLAN, null, true);

        String summary = DailyPlanService.summary(carried, out.totalPlaced(), out.unscheduled().size());
        plan.markGenerated(out.totalPlaced(), carried, out.unscheduled().size(), summary, clock.now());
        return new Result(userId, true, setting.isNightlyPushEnabled(), carried, out.totalPlaced(),
                out.unscheduled().size(), summary);
    }

    /**
     * 아침 이월 (2026-10): 어제 못 한 일을 오늘로 옮기고, 자동 생성이 켜져 있으면 오늘 빈 시간에 채운다
     * (이미 놓인 블록은 그대로 — FILL). 옮긴 일은 "옮길까요?" 제안(carryPending)으로 보이고, 어제 자리엔 점선 흔적이 남는다.
     * 예전엔 저녁(마지막 일정 + 60분 · 23:50)에 옮겨서, 아직 하던 일이나 새벽까지 할 일이 갑자기 내일로 사라졌다.
     * @return 이월한 개수
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int morningCarry(Long userId, LocalDate yesterday, LocalDate today) {
        // 아직 기상 시간 전이면 옮기지 않는다 (새벽까지 어제 일을 하고 있을 수 있어서)
        if (fixedBlockResolver != null) {
            DayFixedBlocks f = fixedBlockResolver.resolve(userId, today, today).get(today);
            LocalDateTime wake = f != null && f.wakeSleep() != null ? f.wakeSleep().end() : null;
            if (clock.now().isBefore(MorningCarryTime.at(today, wake))) return 0;
        }
        PlannerSetting setting = plannerSettingService.getOrCreateForUpdate(userId);
        if (!setting.isAutoCarryEnabled()) return 0;
        int carried = carryOver(userId, yesterday, today, 0);
        if (carried > 0 && setting.isAutoGenerateEnabled()) {
            dayPlacementService.placeDay(userId, today, PlacementMode.FILL, null, true);
        }
        return carried;
    }

    /**
     * 끝난 지 이 시간이 안 된 일정은 넘기지 않는다 (2026-10). 계획보다 늦게까지 하고 있을 수 있어서.
     * 넘긴 일은 원래 날에도 점선 흔적으로 남는다 (TimetableQueryService.carriedTraces).
     */
    static final int CARRY_GRACE_MINUTES = 60;

    /** @return 이월한 개수 */
    int carryOver(Long userId, LocalDate today, LocalDate tomorrow, int graceMinutes) {
        LocalDateTime now = clock.now();
        int count = 0;
        for (Task t : nightlyQueryRepository.findCarryOverCandidates(userId, today,
                today.atStartOfDay(), today.plusDays(1).atStartOfDay())) {
            if (!shouldCarry(t, now.minusMinutes(graceMinutes))) continue;
            scheduleLogWriter.carriedOver(userId, t, tomorrow); // carryOverTo 전에 — 원래 날짜·시각을 남겨야 함
            t.carryOverTo(tomorrow);
            count++;
        }
        return count;
    }

    public static boolean shouldCarry(Task t, LocalDateTime now) {
        GoalCategory g = t.getGoalCategory();
        if (g != null && (g.getGoalCategoryStatus() == GoalCategoryStatus.DELETED
                || g.getGoalCategoryStatus() == GoalCategoryStatus.COMPLETED)) {
            return false; // 끝난 목표의 일은 넘기지 않는다
        }
        if (t.isCarryReverted()) return false; // 사용자가 원래 날로 되돌린 일은 다시 옮기지 않는다
        if (!t.isPlaced()) return true;
        LocalDateTime end = t.getPlannedEndAt() != null ? t.getPlannedEndAt() : t.getPlannedStartAt();
        return !end.isAfter(now); // 아직 끝나지 않은 일정(진행 중 포함)은 넘기지 않는다
    }
}
