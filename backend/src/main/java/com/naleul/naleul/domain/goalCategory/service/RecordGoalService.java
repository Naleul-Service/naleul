package com.naleul.naleul.domain.goalCategory.service;

import com.naleul.naleul.global.exception.CustomException;

import com.naleul.naleul.global.common.response.ErrorCode;

import com.naleul.naleul.domain.category.service.CategoryFinder;
import com.naleul.naleul.domain.generalCategory.entity.GeneralCategory;
import com.naleul.naleul.domain.generalCategory.repository.GeneralCategoryRepository;
import com.naleul.naleul.domain.goalCategory.dto.request.RecordGoalCreateRequest;
import com.naleul.naleul.domain.goalCategory.dto.response.GoalCategoryResponse;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus;
import com.naleul.naleul.domain.goalCategory.kind.GoalKind;
import com.naleul.naleul.domain.goalCategory.reader.GoalCategoryReader;
import com.naleul.naleul.domain.goalCategory.repository.GoalCategoryRepository;
import com.naleul.naleul.domain.goalCategory.validator.GoalCategoryValidator;
import com.naleul.naleul.domain.goalCreation.service.GoalColorAssigner;
import com.naleul.naleul.domain.planner.support.PlannerClock;
import com.naleul.naleul.domain.temporaryGoal.service.TemporaryGoalService;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.service.UserFinder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기록형 목표 (V39).
 *
 * 왜 따로 있나: 회사 업무처럼 "달성 기준"이 없는 일을 AI 목표 설계(수치·세부 목표·마일스톤)에 넣으면
 * AI 가 빈칸을 지어내서 뜬구름 잡는 목표가 나온다. 이런 일은 "오늘 무엇을 했는지"를 쌓는 게 목적이라
 * 이름만으로 바로 만들고, 기록은 ActualActivity(실제로 한 일)로 모은다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class RecordGoalService {

    static final String DEFAULT_EMOJI = "📝";

    private final GoalCategoryRepository goalCategoryRepository;
    private final GeneralCategoryRepository generalCategoryRepository;
    private final GoalCategoryValidator goalCategoryValidator;
    private final GoalCategoryReader goalCategoryReader;
    private final GoalColorAssigner colorAssigner;
    private final TemporaryGoalService temporaryGoalService;
    private final UserFinder userFinder;
    private final CategoryFinder categoryFinder;
    private final PlannerClock clock;

    /** 이름(+이모지·색·카테고리)만으로 기록형 목표 만들기 */
    public GoalCategoryResponse create(Long userId, RecordGoalCreateRequest request) {
        User user = userFinder.findUser(userId);
        goalCategoryValidator.validateCanAdd(user);

        String emoji = request.emoji() != null && !request.emoji().isBlank() ? request.emoji().strip() : DEFAULT_EMOJI;
        String description = request.description() == null || request.description().isBlank()
                ? null : request.description().strip();
        GoalCategory goal = GoalCategory.createRecord(user, colorAssigner.assign(user, request.colorId()),
                request.name().strip(), emoji, description, clock.today());
        if (request.goalType() != null || request.goalSubType() != null) {
            goal.applyKind(GoalKind.of(request.goalType(), request.goalSubType(), request.goalKindLabel()));
        }
        goalCategoryRepository.save(goal);
        ensureDefaultBucket(goal);
        return goalCategoryReader.getResponse(goal.getGoalCategoryId());
    }

    /**
     * 임시 목표를 기록형 목표로 그대로 두기.
     * 브레인덤프가 만든 "나를 업무" 같은 임시 목표는 AI 로 구체화하기보다 기록형이 맞는 경우가 많다.
     * 정식 목표가 되므로 Free 목표 개수 한도를 확인한다. 이미 있던 Task·기록은 그대로 이 목표에 남는다.
     */
    public GoalCategoryResponse keepTemporaryAsRecord(Long userId, Long goalCategoryId) {
        GoalCategory goal = temporaryGoalService.findOwnedTemporary(userId, goalCategoryId);
        goalCategoryValidator.validateCanAdd(goal.getUser());
        goal.keepAsRecord();
        ensureDefaultBucket(goal);
        return goalCategoryReader.getResponse(goal.getGoalCategoryId());
    }

    /**
     * 예전에 직접 만든 목표(목표형, AI 없이 만들어 수치 · 마일스톤 없음)를 업무형으로 바꾸기 (2026-10).
     * develop 배포 전 목표는 모두 goal_mode = ACHIEVEMENT 로 들어와서, 회사 업무처럼 "이룰 수치"가 없는 목표는
     * 목표 상세에서 업무형으로 고를 수 있게 한다.
     *  - 이미 정식 목표라 Free 목표 개수 한도는 다시 보지 않는다 (개수가 늘지 않음)
     *  - Task · 루틴 · 세부 목표 · 기록은 그대로 두고 형태만 바꾼다 (업무형은 마감이 주제마다라 목표 종료일은 비운다)
     *  - 임시 목표는 keep-as-record, 이미 업무형 · 습관 · 완료/삭제된 목표는 400
     */
    public GoalCategoryResponse convertToRecord(Long userId, Long goalCategoryId) {
        User user = userFinder.findUser(userId);
        GoalCategory goal = categoryFinder.findGoalCategory(goalCategoryId, user);
        if (goal.isTemporary() || !goal.isAchievement()
                || goal.getGoalCategoryStatus() == GoalCategoryStatus.COMPLETED) {
            throw new CustomException(ErrorCode.GOAL_CONVERT_NOT_ALLOWED);
        }
        goal.keepAsRecord();
        ensureDefaultBucket(goal);
        return goalCategoryReader.getResponse(goal.getGoalCategoryId());
    }

    /**
     * "기타 할 일" 세부 목표를 미리 만든다.
     * 기록형은 세부 목표를 보여주지 않지만, 루틴·Task 는 세부 목표(general_category) 아래에만 만들 수 있어서
     * 사용자가 "주간 회의" 같은 반복 루틴을 붙일 때 바로 쓸 그릇이 필요하다.
     */
    private void ensureDefaultBucket(GoalCategory goal) {
        if (generalCategoryRepository.findDefaultBuckets(goal.getGoalCategoryId()).isEmpty()) {
            generalCategoryRepository.save(GeneralCategory.createDefaultBucket(goal));
        }
    }
}
