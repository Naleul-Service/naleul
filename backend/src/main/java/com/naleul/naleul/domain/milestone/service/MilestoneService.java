package com.naleul.naleul.domain.milestone.service;

import com.naleul.naleul.domain.category.service.CategoryFinder;
import com.naleul.naleul.domain.goalCategory.dto.response.GoalCategoryResponse.MilestoneInfo;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.milestone.dto.request.MilestoneCreateRequest;
import com.naleul.naleul.domain.milestone.dto.request.MilestoneUpdateRequest;
import com.naleul.naleul.domain.milestone.entity.Milestone;
import com.naleul.naleul.domain.milestone.enums.MilestoneStatus;
import com.naleul.naleul.domain.milestone.repository.MilestoneRepository;
import com.naleul.naleul.domain.planner.support.PlannerClock;
import com.naleul.naleul.domain.user.service.UserFinder;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * 마일스톤 추가·수정·삭제 (목표 상세 화면).
 * 순서(sortOrder)는 항상 기한(dueDate) 순으로 다시 매긴다 — 화면·AI 컨텍스트가 sortOrder 순으로 읽기 때문.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class MilestoneService {

    private final MilestoneRepository milestoneRepository;
    private final CategoryFinder categoryFinder;
    private final UserFinder userFinder;
    private final PlannerClock clock;
    private final MilestoneAutoPlanner autoPlanner;

    public MilestoneInfo create(Long userId, Long goalCategoryId, MilestoneCreateRequest request) {
        GoalCategory goal = categoryFinder.findGoalCategory(goalCategoryId, userFinder.findUser(userId));
        Milestone milestone = Milestone.create(goal, request.title().strip(), blankToNull(request.description()),
                request.dueDate(), null, Integer.MAX_VALUE);
        if (request.targetValue() != null) milestone.setManualTargetValue(request.targetValue());
        milestoneRepository.save(milestone);
        replan(goal);
        return MilestoneInfo.from(milestone);
    }

    public MilestoneInfo update(Long userId, Long milestoneId, MilestoneUpdateRequest request) {
        Milestone milestone = findOwned(userId, milestoneId);
        milestone.update(request.title() != null ? request.title().strip() : null,
                request.description(), request.dueDate(), null);
        if (request.targetValue() != null) milestone.setManualTargetValue(request.targetValue());
        if (Boolean.TRUE.equals(request.clearTargetValue())) milestone.clearTargetValue();
        if (Boolean.TRUE.equals(request.autoTargetValue())) milestone.applyAutoTargetValue(milestone.getTargetValue());

        if (request.status() == MilestoneStatus.ACHIEVED && milestone.getStatus() != MilestoneStatus.ACHIEVED) {
            milestone.achieve(clock.now());
        } else if (request.status() == MilestoneStatus.PENDING) {
            milestone.reopen();
        }
        // 날짜·수치를 바꿨으면 다른 시점들도 다시 맞춘다 (달성 체크만 한 경우는 그대로)
        if (request.dueDate() != null || request.targetValue() != null
                || Boolean.TRUE.equals(request.clearTargetValue()) || Boolean.TRUE.equals(request.autoTargetValue())) {
            replan(milestone.getGoalCategory());
        }
        return MilestoneInfo.from(milestone);
    }

    public void delete(Long userId, Long milestoneId) {
        Milestone milestone = findOwned(userId, milestoneId);
        GoalCategory goal = milestone.getGoalCategory();
        Long goalId = goal.getGoalCategoryId();
        milestoneRepository.detachTasks(milestone);   // 연결된 Task 는 남기고 연결만 끊는다
        milestoneRepository.delete(milestone);
        milestoneRepository.flush();
        reorder(goalId);
        replan(goal);
    }

    /** 점검 시점 날짜·수치 자동 보정 (MilestoneAutoPlanner). 직접 정한 값은 유지 */
    private void replan(GoalCategory goal) {
        if (autoPlanner != null) autoPlanner.replan(goal, false);
    }

    private Milestone findOwned(Long userId, Long milestoneId) {
        return milestoneRepository.findOwned(milestoneId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.MILESTONE_NOT_FOUND));
    }

    /** 기한 순으로 sortOrder 를 0, 1, 2 … 로 다시 매긴다 */
    private void reorder(Long goalCategoryId) {
        List<Milestone> list = milestoneRepository.findAllByGoalCategory_GoalCategoryIdOrderBySortOrderAsc(goalCategoryId)
                .stream()
                .sorted(Comparator.comparing(Milestone::getDueDate).thenComparing(Milestone::getMilestoneId))
                .toList();
        for (int i = 0; i < list.size(); i++) list.get(i).changeSortOrder(i);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
