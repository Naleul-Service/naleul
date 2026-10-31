package com.naleul.naleul.domain.goalCategory.service;

import com.naleul.naleul.global.common.response.ErrorCode;

import com.naleul.naleul.global.exception.CustomException;

import com.naleul.naleul.domain.goalOutcome.enums.OutcomeSource;
import com.naleul.naleul.domain.goalOutcome.service.GoalOutcomeRecorder;
import org.springframework.beans.factory.annotation.Autowired;
import com.naleul.naleul.domain.category.service.CategoryFinder;
import com.naleul.naleul.domain.goalCategory.deleter.GoalCategoryCascadeDeleter;
import com.naleul.naleul.domain.goalCategory.dto.request.*;
import com.naleul.naleul.domain.goalCategory.dto.response.GoalCategoryResponse;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.goalCategory.kind.GoalKind;
import com.naleul.naleul.domain.goalCategory.event.GoalCategoryCompletedEvent;
import com.naleul.naleul.domain.goalCategory.factory.GoalCategoryFactory;
import com.naleul.naleul.domain.goalCategory.reader.GoalCategoryReader;
import com.naleul.naleul.domain.goalCategory.repository.GoalCategoryRepository;
import com.naleul.naleul.domain.goalCategory.validator.GoalCategoryValidator;
import com.naleul.naleul.domain.goalProgress.service.GoalProgressService;
import com.naleul.naleul.domain.milestone.service.MilestoneAutoPlanner;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.service.UserFinder;
import com.naleul.naleul.domain.userColor.entity.UserColor;
import com.naleul.naleul.domain.userColor.service.UserColorFinder;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class GoalCategoryService {

    private final GoalCategoryRepository goalCategoryRepository;
    private final GoalCategoryValidator goalCategoryValidator;
    private final UserFinder userFinder;
    private final UserColorFinder userColorFinder;
    private final CategoryFinder categoryFinder;
    private final GoalCategoryReader goalCategoryReader;
    private final GoalCategoryFactory goalCategoryFactory;
    private final ApplicationEventPublisher eventPublisher; // 이벤트 발행용
    private final GoalCategoryCascadeDeleter goalCategoryCascadeDeleter;
    private final GoalProgressService goalProgressService;
    private final MilestoneAutoPlanner milestoneAutoPlanner;

    /** 목표 결과 기록 (V47) — 기존 테스트 생성자 호환을 위해 필드 주입 */
    @Autowired(required = false)
    private GoalOutcomeRecorder outcomeRecorder;

    // ── CREATE ────────────────────────────────────────────────
    // 목표 카테고리 생성
    public GoalCategoryResponse create(Long userId, GoalCategoryCreateRequest request) {
        User user = userFinder.findUser(userId);
        UserColor color = userColorFinder.findUserColor(request.getColorId(), user);

        goalCategoryValidator.validateCreate(user, request);

        GoalCategory goalCategory = goalCategoryFactory.create(user, color, request);
        // 직접 만들 때 함께 받은 이모지·수치 목표 (현재값은 시작값에서 출발)
        goalCategory.updateDetails(request.getEmoji(), null, request.getMetricName(), request.getMetricUnit(),
                request.getStartValue(), request.getStartValue(), request.getTargetValue());
        goalCategory.applyKind(GoalKind.of(request.getGoalType(), request.getGoalSubType(), request.getGoalKindLabel()));
        goalCategoryRepository.save(goalCategory);

        return goalCategoryReader.getResponse(goalCategory.getGoalCategoryId());  // 재조회+DTO변환 위임
    }

    // ── UPDATE ────────────────────────────────────────────────
    // 목표 완료 처리
    public GoalCategoryResponse complete(Long goalCategoryId, Long userId, GoalCategoryCompleteRequest request) {
        User user = userFinder.findUser(userId);
        GoalCategory goalCategory = categoryFinder.findGoalCategory(goalCategoryId, user);
        rejectHabitHome(goalCategory);

        // 결과 기록 (V47) — complete() 가 종료일을 덮어쓰기 전에 원래 기간으로 스냅샷을 남긴다
        if (outcomeRecorder != null) {
            outcomeRecorder.record(goalCategory, request.getResult(), OutcomeSource.USER, request.getGoalCategoryEndDate());
        }
        goalCategory.complete(request.getGoalCategoryEndDate(), request.getAchievement(), request.getAchievementImageUrl());
        // 기간보다 먼저 달성했으면 남은 루틴 · 일정이 캘린더에 남지 않게 정리 (지난 기록 · 완료한 일은 그대로)
        goalCategoryCascadeDeleter.wrapUpOnComplete(goalCategoryId);
        // "목표가 완료됐다"는 이벤트만 발행. -> GoalCategoryCompletionEventListener 에서 감지
        eventPublisher.publishEvent(new GoalCategoryCompletedEvent(goalCategoryId));

        return goalCategoryReader.getResponse(goalCategory.getGoalCategoryId());
    }

    public GoalCategoryResponse fillAchievement(Long goalCategoryId, Long userId, GoalCategoryAchievementRequest request) {
        User user = userFinder.findUser(userId);
        GoalCategory goalCategory = categoryFinder.findGoalCategory(goalCategoryId, user);

        goalCategory.fillAchievement(request.getAchievement(), request.getAchievementImageUrl());

        // ✅ 여기서 완료 이벤트 발행 — achievement가 채워진 "이후"라 리포트 생성 준비가 됐음
        eventPublisher.publishEvent(new GoalCategoryCompletedEvent(goalCategoryId));

        return goalCategoryReader.getResponse(goalCategoryId);
    }

    // 목표 카테고리 수정
    public GoalCategoryResponse update(Long goalCategoryId, Long userId, GoalCategoryUpdateRequest request) {
        User user = userFinder.findUser(userId);
        GoalCategory goalCategory = categoryFinder.findGoalCategory(goalCategoryId, user);

        // 기본 생활 습관 그릇(V52)은 이름 · 이모지 · 색만 바꿀 수 있다 (기간 · 수치가 생기면 만료 · 시작 전 상태로 바뀌어 버린다)
        if (goalCategory.isHabit()) {
            if (request.getColorId() != null) goalCategory.updateColor(userColorFinder.findUserColor(request.getColorId(), user));
            goalCategory.update(request.getGoalCategoryName(), null, null, null);
            goalCategory.updateDetails(request.getEmoji(), null, null, null, null, null, null);
            return goalCategoryReader.getResponse(goalCategoryId);
        }

        goalCategoryValidator.validateUpdate(goalCategory, request);
        BigDecimal previousCurrent = goalCategory.getCurrentValue();
        // 점검 시점 자동 보정이 필요한지 보려고 "그래프를 정하는 값"을 기억해 둔다
        List<Object> previousShape = shapeOf(goalCategory);
        if (request.getColorId() != null) {
            UserColor color = userColorFinder.findUserColor(request.getColorId(), user);
            goalCategory.updateColor(color);
        }
        // 목표 기간을 바꿔도 수치 마감은 그대로 (2026-10 — 수치 마감을 안 정했던 목표면 지금 종료일로 고정)
        goalCategory.pinMetricTargetDateBeforeEndChange(request.getGoalCategoryEndDate());
        goalCategory.update(
                request.getGoalCategoryName(),
                request.getGoalCategoryStartDate(),
                request.getGoalCategoryEndDate(),
                request.getMotive()
        );
        // 업무형은 목표 전체 마감이 없다 (V53 — 마감은 주제마다). 예전 앱이 종료일을 보내도 저장하지 않는다
        if (goalCategory.isRecord()) {
            goalCategory.changeDeadline(null, true);
        }
        goalCategory.updateDetails(
                request.getEmoji(),
                request.getDescription(),
                request.getMetricName(),
                request.getMetricUnit(),
                request.getStartValue(),
                request.getCurrentValue(),
                request.getTargetValue()
        );
        // 수치 마감일 (목표 종료일과 따로)
        goalCategory.changeMetricTargetDate(request.getMetricTargetDate(),
                Boolean.TRUE.equals(request.getClearMetricTargetDate()));
        boolean metricCleared = Boolean.TRUE.equals(request.getClearMetric());
        if (metricCleared) goalCategory.clearMetric();
        // 시작값·목표값·기간이 바뀌면 점검 시점 날짜·수치를 다시 자동으로 (직접 정한 값도 초기화)
        if (milestoneAutoPlanner != null) {
            if (metricCleared) milestoneAutoPlanner.clearValues(goalCategory);
            else if (!previousShape.equals(shapeOf(goalCategory))) milestoneAutoPlanner.replan(goalCategory, true);
        }
        // 카테고리를 바꿨으면 (안 보냈으면 그대로)
        goalCategory.applyKind(GoalKind.of(request.getGoalType(), request.getGoalSubType(), request.getGoalKindLabel()));
        // 현재 값을 직접 고쳤으면 오늘 기록으로도 남겨 진행 도표와 숫자를 맞춘다
        // (goalProgressService null 검사: 이 의존성을 모르는 기존 단위 테스트의 @InjectMocks 대비)
        if (goalProgressService != null && request.getCurrentValue() != null
                && (previousCurrent == null || previousCurrent.compareTo(request.getCurrentValue()) != 0)) {
            goalProgressService.recordFromHeader(goalCategory, user, request.getCurrentValue());
        }

        return goalCategoryReader.getResponse(goalCategoryId);
    }

    /** 점검 시점 그래프 모양을 정하는 값: 시작일 · 종료일 · 시작값 · 목표값 (BigDecimal 은 스케일 무시 비교) */
    private static List<Object> shapeOf(GoalCategory g) {
        return java.util.Arrays.asList(g.getGoalCategoryStartDate(), g.getGoalCategoryEndDate(), g.getMetricTargetDate(),
                g.getStartValue() != null ? g.getStartValue().stripTrailingZeros() : null,
                g.getTargetValue() != null ? g.getTargetValue().stripTrailingZeros() : null);
    }

    // ── DELETE ────────────────────────────────────────────────
    // 목표 카테고리 소프트 삭제
    public void delete(Long goalCategoryId, Long userId) {
        User user = userFinder.findUser(userId);
        GoalCategory goalCategory = categoryFinder.findGoalCategory(goalCategoryId, user);
        rejectHabitHome(goalCategory);

        goalCategoryCascadeDeleter.deleteCascade(goalCategoryId);
        goalCategory.delete();
    }

    /**
     * 기본 생활 습관 그릇(V52)은 모든 사용자에게 하나씩 있는 목표라 완료 · 삭제하지 않는다.
     * 필요 없는 습관은 습관 단위로 쉬거나 지운다 (/api/v1/habits).
     */
    private static void rejectHabitHome(GoalCategory goal) {
        if (goal.isHabit()) {
            throw new CustomException(ErrorCode.HABIT_HOME_NOT_REMOVABLE);
        }
    }
}