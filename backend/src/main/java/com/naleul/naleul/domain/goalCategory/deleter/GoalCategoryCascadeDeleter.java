package com.naleul.naleul.domain.goalCategory.deleter;

import com.naleul.naleul.domain.generalCategory.entity.GeneralCategory;
import com.naleul.naleul.domain.generalCategory.repository.GeneralCategoryRepository;
import com.naleul.naleul.domain.planner.support.PlannerClock;
import com.naleul.naleul.domain.routine.entity.Routine;
import com.naleul.naleul.domain.routine.enums.RoutineStatus;
import com.naleul.naleul.domain.routine.repository.RoutineRepository;
import com.naleul.naleul.domain.routine.service.RoutineService;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.enums.TaskStatus;
import com.naleul.naleul.domain.task.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * GoalCategory 삭제 시 하위 데이터 정리 정책.
 * "무엇을, 어떤 순서로 정리할지"가 바뀌면 이 클래스만 수정하면 됨.
 *
 *  1) 세부 목표(GeneralCategory) 소프트 삭제
 *  2) 루틴은 멈추기만 (소프트 삭제) — 더 이상 새 Task 가 생기지 않음
 *  3) 목표의 Task 는 "지금" 기준으로
 *     - 지금 이전 Task (이미 시작했거나 지난 날짜) → 남김 (완료·놓친 기록 = 나의 패턴·통계에 필요)
 *     - 지금 이후 Task (아직 시작 전, 오늘 이후 날짜, 날짜 없음) → 삭제
 *     - 완료한 Task 는 시점과 상관없이 남김 (미리 해 둔 기록도 기록)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoalCategoryCascadeDeleter {

    private final GeneralCategoryRepository generalCategoryRepository;
    private final RoutineService routineService;
    private final RoutineRepository routineRepository;
    private final TaskRepository taskRepository;
    private final PlannerClock clock;

    public void deleteCascade(Long goalCategoryId) {
        List<GeneralCategory> generalCategories =
                generalCategoryRepository.findAllActiveByGoalCategoryId(goalCategoryId);

        for (GeneralCategory generalCategory : generalCategories) {
            generalCategory.delete();   // 소프트 삭제 (색상 참조 해제)
            routineService.stopAllByGeneralCategoryId(generalCategory.getGeneralCategoryId());
        }

        LocalDateTime now = clock.now();
        List<Task> upcoming = taskRepository.findAllActiveByGoalCategoryId(goalCategoryId).stream()
                .filter(t -> isUpcoming(t, now))
                .toList();
        taskRepository.deleteAll(upcoming);
        log.info("[GoalDelete] goalCategoryId={} 지금 이후 Task {}개 삭제", goalCategoryId, upcoming.size());
    }

    /**
     * 목표를 달성(완료)했을 때 — 기간이 남았어도 앞으로의 일정은 캘린더에서 빼요.
     *  - 진행 중 · 시작 전 루틴 → 오늘로 끝냄 (COMPLETED, 실제 종료일 = 오늘). 루틴 기록 · 실천 히트맵은 그대로
     *  - 지금 이후 Task (완료 안 한 것) → 삭제. 지난 기록 · 완료한 Task 는 남겨요 (목표 결과 · 나의 패턴에 필요)
     * 목표 삭제(deleteCascade)와 다른 점: 세부 목표 · 루틴을 지우지 않고 "끝난 상태"로 남겨 목표 상세에서 계속 볼 수 있어요.
     *
     * @return 정리한 수 [끝낸 루틴, 지운 Task]
     */
    public int[] wrapUpOnComplete(Long goalCategoryId) {
        LocalDateTime now = clock.now();
        int routines = 0;
        for (Routine r : routineRepository.findAllByGoalCategoryId(goalCategoryId)) {
            if (r.getRoutineStatus() == RoutineStatus.NOT_STARTED || r.getRoutineStatus() == RoutineStatus.IN_PROGRESS) {
                r.complete(now.toLocalDate());
                routines++;
            }
        }
        List<Task> upcoming = taskRepository.findAllActiveByGoalCategoryId(goalCategoryId).stream()
                .filter(t -> isUpcoming(t, now))
                .toList();
        taskRepository.deleteAll(upcoming);
        log.info("[GoalComplete] goalCategoryId={} 루틴 {}개 끝냄 · 지금 이후 Task {}개 삭제", goalCategoryId, routines, upcoming.size());
        return new int[]{routines, upcoming.size()};
    }

    /**
     * 지금(now) 이후에 할 예정이던, 아직 끝내지 않은 Task 인지.
     *  - 시간이 정해진 Task : 시작 시각이 지금 이후
     *  - 날짜만 있는 Task   : 오늘 또는 그 이후 날짜 (오늘 "시간 미정"도 아직 안 한 일이라 지움)
     *  - 날짜도 없는 Task   : 앞으로 할 일
     *  - 완료 Task · 미션 Task 는 지우지 않음 (미션은 다른 참여자와 연결돼 있어 미션 쪽에서 관리)
     */
    static boolean isUpcoming(Task t, LocalDateTime now) {
        if (t.getTaskStatus() == TaskStatus.COMPLETED || t.isMissionTask()) return false;
        if (t.getPlannedStartAt() != null) return !t.getPlannedStartAt().isBefore(now);
        LocalDate date = t.getRoutineDate() != null ? t.getRoutineDate()
                : t.getScheduledDate() != null ? t.getScheduledDate()
                : t.getDueDate();
        return date == null || !date.isBefore(now.toLocalDate());
    }
}
