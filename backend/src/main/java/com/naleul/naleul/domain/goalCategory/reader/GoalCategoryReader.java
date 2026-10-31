package com.naleul.naleul.domain.goalCategory.reader;

import com.naleul.naleul.domain.category.service.CategoryFinder;
import com.naleul.naleul.domain.goalCategory.dto.response.GoalCategoryResponse;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.goalCategory.repository.GoalCategoryRepository;
import com.naleul.naleul.domain.goalCategory.support.GoalPauseCalculator;
import com.naleul.naleul.domain.goalProgress.repository.GoalLastMetricLogProjection;
import com.naleul.naleul.domain.goalProgress.repository.GoalMetricLogRepository;
import com.naleul.naleul.domain.planner.support.PlannerClock;
import com.naleul.naleul.domain.task.enums.TaskStatus;
import com.naleul.naleul.domain.task.repository.TaskRepository;
import com.naleul.naleul.domain.task.repository.projection.GoalLastDoneProjection;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.service.UserFinder;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GoalCategoryReader {

    private final GoalCategoryRepository goalCategoryRepository;
    private final UserFinder userFinder;
    private final CategoryFinder categoryFinder;

    /** 목표 "멈춤" 계산용 — 기존 테스트(@InjectMocks) 호환을 위해 필드 주입, 없으면 멈춤 계산을 건너뛴다 */
    @Autowired(required = false)
    private TaskRepository taskRepository;
    @Autowired(required = false)
    private GoalMetricLogRepository metricLogRepository;
    @Autowired(required = false)
    private PlannerClock clock;

    /**
     * ⚠️ 소유권 검증 없이 재조회만 함. 반드시 이 메서드를 호출하기 전에
     * 호출부에서 CategoryFinder 등으로 소유권 검증이 끝나있어야 함.
     * Controller에서 직접 호출 금지 — Service 내부 재조회 용도로만 사용.
     */
    public GoalCategoryResponse getResponse(Long goalCategoryId) {
        GoalCategory goalCategory = goalCategoryRepository.findByIdWithAll(goalCategoryId)
                .orElseThrow(() -> new CustomException(ErrorCode.GOAL_CATEGORY_NOT_FOUND));
        return GoalCategoryResponse.from(goalCategory);
    }

    public GoalCategoryResponse getGoalCategory(Long goalCategoryId, Long userId) {
        User user = userFinder.findUser(userId);
        GoalCategory goalCategory = categoryFinder.findGoalCategory(goalCategoryId, user);   // 소유권 확인된 조회
        return GoalCategoryResponse.from(goalCategory);
    }

    public List<GoalCategoryResponse> getGoalCategories(Long userId) {
        userFinder.validateUserExists(userId);

        List<GoalCategory> goals = goalCategoryRepository.findAllByUserIdWithAll(userId);
        if (taskRepository == null || metricLogRepository == null || clock == null) {
            return goals.stream().map(GoalCategoryResponse::from).toList();
        }

        // 목표별 마지막 활동일 = max(마지막 Task 완료일, 마지막 수치 기록일) — 쿼리 2번으로 전체 목표를 한 번에
        Map<Long, LocalDate> lastActivity = new HashMap<>();
        for (GoalLastDoneProjection p : taskRepository.findLastDoneByGoal(userId, TaskStatus.COMPLETED)) {
            if (p.getLastAt() != null) lastActivity.merge(p.getGoalCategoryId(), p.getLastAt().toLocalDate(), GoalCategoryReader::later);
        }
        for (GoalLastMetricLogProjection p : metricLogRepository.findLastLogByGoal(userId)) {
            if (p.getLastDate() != null) lastActivity.merge(p.getGoalCategoryId(), p.getLastDate(), GoalCategoryReader::later);
        }

        LocalDate today = clock.today();
        return goals.stream()
                .map(goal -> {
                    LocalDate last = lastActivity.get(goal.getGoalCategoryId());
                    Integer paused = goal.isHabit() ? null : GoalPauseCalculator.pausedDays(goal.getGoalCategoryStatus(), goal.isTemporary(),
                            goal.isRecord(), goal.getGoalCategoryStartDate(), last, today);
                    return GoalCategoryResponse.from(goal).toBuilder()
                            .lastActivityDate(last)
                            .pausedDays(paused)
                            .build();
                })
                .toList();
    }

    private static LocalDate later(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }
}