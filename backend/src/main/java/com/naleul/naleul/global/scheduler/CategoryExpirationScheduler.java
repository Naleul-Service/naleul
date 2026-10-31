package com.naleul.naleul.global.scheduler;

import com.naleul.naleul.domain.goalOutcome.enums.OutcomeSource;
import com.naleul.naleul.domain.goalOutcome.service.GoalOutcomeRecorder;
import org.springframework.beans.factory.annotation.Autowired;
import com.naleul.naleul.domain.generalCategory.entity.GeneralCategory;
import com.naleul.naleul.domain.generalCategory.repository.GeneralCategoryRepository;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.goalCategory.repository.GoalCategoryRepository;
import com.naleul.naleul.domain.routine.entity.Routine;
import com.naleul.naleul.domain.routine.enums.RoutineStatus;
import com.naleul.naleul.domain.routine.repository.RoutineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CategoryExpirationScheduler {

    private final GoalCategoryRepository goalCategoryRepository;
    private final GeneralCategoryRepository generalCategoryRepository;
    private final RoutineRepository routineRepository;

    /** 목표 결과 기록 (V47) */
    @Autowired(required = false)
    private GoalOutcomeRecorder outcomeRecorder;

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    @Transactional
    public void expireOutdatedCategories() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        // 1) GoalCategory 시작 전환
        List<GoalCategory> startedGoals = goalCategoryRepository.findGoalsToStart(today);
        startedGoals.forEach(GoalCategory::start);
        log.info("[GoalCategory 자동 시작 전환] {}건 처리", startedGoals.size());

        // 2) GeneralCategory 시작 전환
        List<GeneralCategory> startedGenerals = generalCategoryRepository.findGeneralCategoriesToStart(today);
        startedGenerals.forEach(GeneralCategory::start);
        log.info("[GeneralCategory 자동 시작 전환] {}건 처리", startedGenerals.size());

        // 3) Routine 시작 전환
        List<Routine> startedRoutines = routineRepository.findRoutinesToStart(today);
        startedRoutines.forEach(Routine::start);
        log.info("[Routine 자동 시작 전환] {}건 처리", startedRoutines.size());

        // 4) GoalCategory 만료
        List<GoalCategory> expiredGoals = goalCategoryRepository.findExpiredCategories(today);
        // 결과 기록 (V47) — 기간이 끝나 자동 완료되는 목표는 수치·실천률로 자동 판정. 한 목표가 실패해도 나머지는 계속
        if (outcomeRecorder != null) {
            for (GoalCategory goal : expiredGoals) {
                try {
                    outcomeRecorder.record(goal, null, OutcomeSource.AUTO);
                } catch (RuntimeException e) {
                    log.warn("[GoalOutcome] 자동 결과 기록 실패 goalCategoryId={}", goal.getGoalCategoryId(), e);
                }
            }
        }
        expiredGoals.forEach(GoalCategory::expire);
        log.info("[GoalCategory 자동 만료] {}건 처리", expiredGoals.size());

        // 5) GeneralCategory 만료
        List<GeneralCategory> expiredGenerals = generalCategoryRepository.findExpiredCategories(today);
        expiredGenerals.forEach(GeneralCategory::expire);
        log.info("[GeneralCategory 자동 만료] {}건 처리", expiredGenerals.size());

        // 6) Routine 만료
        List<Routine> expiredRoutines = routineRepository.findExpiredRoutines(
                today, List.of(RoutineStatus.COMPLETED, RoutineStatus.DELETED));
        expiredRoutines.forEach(Routine::expire);
        log.info("[Routine 자동 만료] {}건 처리", expiredRoutines.size());
    }
}