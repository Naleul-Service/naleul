package com.naleul.naleul.domain.planner.service;

import com.naleul.naleul.domain.planner.dto.response.DailyPlanResponse;
import com.naleul.naleul.domain.planner.dto.response.FillResponse;
import com.naleul.naleul.domain.planner.dto.response.ReplanResponse;
import com.naleul.naleul.domain.planner.entity.DailyPlan;
import com.naleul.naleul.domain.planner.enums.DailyPlanSource;
import com.naleul.naleul.domain.planner.enums.DailyPlanStatus;
import com.naleul.naleul.domain.planner.repository.DailyPlanRepository;
import com.naleul.naleul.domain.planner.support.PlannerClock;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 날짜별 TimeTable 생성 기록(daily_plan) 관리 (명세 3 · 3-3, 5-5).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class DailyPlanService {

    private final DailyPlanRepository dailyPlanRepository;
    private final DayPlacementService dayPlacementService;
    private final PlannerClock clock;

    @Transactional(readOnly = true)
    public DailyPlanResponse get(Long userId, LocalDate date) {
        return dailyPlanRepository.findByUserIdAndPlanDate(userId, date)
                .map(DailyPlanResponse::from)
                .orElseGet(() -> DailyPlanResponse.none(date));
    }

    /** "이대로 진행하기" — 기록이 없으면 아무것도 하지 않는다 */
    public DailyPlanResponse review(Long userId, LocalDate date) {
        Optional<DailyPlan> plan = dailyPlanRepository.findByUserIdAndPlanDate(userId, date);
        plan.ifPresent(p -> p.markReviewed(clock.now()));
        return plan.map(DailyPlanResponse::from).orElseGet(() -> DailyPlanResponse.none(date));
    }

    /**
     * "다시 배치": 그날 잠기지 않은 블록을 모두 비우고 다시 배치. 지난 날짜는 400.
     * 결과를 다시 확인받아야 하므로 GENERATED(needsReview=true)로 둔다.
     */
    public ReplanResponse replan(Long userId, LocalDate date) {
        if (date.isBefore(clock.today())) {
            throw new CustomException(ErrorCode.DAILY_PLAN_PAST_DATE);
        }
        PlacementOutcome out = dayPlacementService.placeDay(userId, date, PlacementMode.REPLAN, null, true);
        DailyPlan plan = upsert(userId, date, DailyPlanSource.REPLAN);
        plan.markGenerated(out.totalPlaced(), plan.getCarriedCount(), out.unscheduled().size(),
                summary(plan.getCarriedCount(), out.totalPlaced(), out.unscheduled().size()), clock.now());
        return new ReplanResponse(DailyPlanResponse.from(plan),
                out.placed().stream().map(DailyPlanService::block).toList(),
                out.movedToOtherDays().stream().map(DailyPlanService::block).toList(),
                out.unscheduled());
    }

    /**
     * "AI로 TimeTable 배치하기" — [startDate, endDate] 의 시간 미정 Task 만 빈 시간에 채운다 (FILL).
     *  - 이미 시간이 있는 블록(직접 정한 🔒 · 자동 배치 · 완료)은 그대로 둔다
     *  - 오늘은 지금 + 10분 이후 빈 시간만 쓴다
     *  - 지난 날짜는 건너뛴다. 최대 7일.
     * 결과를 화면에서 바로 보여주므로 daily_plan 기록(확인 배너)은 만들지 않는다.
     */
    public FillResponse fill(Long userId, LocalDate startDate, LocalDate endDate) {
        LocalDate today = clock.today();
        if (startDate == null || endDate == null || endDate.isBefore(startDate)
                || ChronoUnit.DAYS.between(startDate, endDate) > 6) {
            throw new CustomException(ErrorCode.TIMETABLE_INVALID_RANGE);
        }
        List<FillResponse.Day> days = new ArrayList<>();
        int placed = 0, unscheduled = 0;
        for (LocalDate d = startDate.isBefore(today) ? today : startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            PlacementOutcome out = dayPlacementService.placeDay(userId, d, PlacementMode.FILL, null, false);
            days.add(new FillResponse.Day(d, out.totalPlaced(), out.unscheduled().size()));
            placed += out.totalPlaced();
            unscheduled += out.unscheduled().size();
        }
        return new FillResponse(days, placed, unscheduled);
    }

    /**
     * Brain dump 확정으로 배치된 날짜 기록. 사용자가 3단계 화면에서 이미 봤으므로 바로 REVIEWED.
     * 이미 기록이 있는 날짜(예: 22시에 만든 내일 계획)는 건드리지 않는다 — 그 계획의 확인 배너는 유지.
     */
    public void recordBrainDump(Long userId, Collection<LocalDate> dates, int placedCount) {
        for (LocalDate d : dates) {
            if (dailyPlanRepository.findByUserIdAndPlanDate(userId, d).isPresent()) continue;
            DailyPlan p = DailyPlan.start(userId, d, DailyPlanSource.BRAIN_DUMP);
            p.markGenerated(placedCount, 0, 0, null, clock.now());
            p.markReviewed(clock.now());
            dailyPlanRepository.save(p);
        }
    }

    /**
     * 22시 작업이 이 사용자·날짜를 맡을 수 있는지 확인하고 잠근다.
     * @return 진행할 DailyPlan (GENERATING), 이미 오늘 22시 작업으로 만들었으면 null
     */
    public DailyPlan claimNightly(Long userId, LocalDate planDate, LocalDate today, boolean force) {
        dailyPlanRepository.insertIfAbsent(userId, planDate, DailyPlanSource.NIGHTLY.name());
        DailyPlan plan = dailyPlanRepository.findForUpdate(userId, planDate)
                .orElseThrow(() -> new IllegalStateException("daily_plan 행이 없음"));
        if (nightlyDoneOn(plan, today) && !force) return null;
        plan.restart(DailyPlanSource.NIGHTLY);
        return plan;
    }

    /** 이 내일 계획을 오늘 저녁 작업이 이미 만들었는지 (실패한 건 다시 시도) */
    public static boolean nightlyDoneOn(DailyPlan plan, LocalDate today) {
        return plan.getSource() == DailyPlanSource.NIGHTLY
                && plan.getStatus() != DailyPlanStatus.FAILED
                && plan.getGeneratedAt() != null
                && plan.getGeneratedAt().toLocalDate().equals(today);
    }

    static String summary(int carried, int placed, int unplaced) {
        StringBuilder sb = new StringBuilder();
        if (carried > 0) sb.append("이월 ").append(carried).append("개");
        if (placed > 0) sb.append(sb.isEmpty() ? "" : " · ").append("배치 ").append(placed).append("개");
        if (unplaced > 0) sb.append(sb.isEmpty() ? "" : " · ").append("시간 미정 ").append(unplaced).append("개");
        return sb.isEmpty() ? "새로 배치할 일이 없어요" : sb.toString();
    }

    private DailyPlan upsert(Long userId, LocalDate date, DailyPlanSource source) {
        DailyPlan plan = dailyPlanRepository.findByUserIdAndPlanDate(userId, date).orElse(null);
        if (plan == null) {
            plan = dailyPlanRepository.save(DailyPlan.start(userId, date, source));
        } else {
            plan.restart(source);
        }
        return plan;
    }

    private static ReplanResponse.Block block(PlacementOutcome.PlacedBlock b) {
        return new ReplanResponse.Block(b.taskId(), b.date(), b.start(), b.end(), b.reason());
    }

}
