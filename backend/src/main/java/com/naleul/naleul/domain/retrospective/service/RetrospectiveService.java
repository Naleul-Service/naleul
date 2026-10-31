// retrospective/service/RetrospectiveService.java
package com.naleul.naleul.domain.retrospective.service;

import com.naleul.naleul.domain.category.service.CategoryFinder;
import com.naleul.naleul.domain.generalCategory.entity.GeneralCategory;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.retrospective.dto.request.RetrospectiveCreateRequest;
import com.naleul.naleul.domain.retrospective.dto.request.RetrospectiveUpdateRequest;
import com.naleul.naleul.domain.retrospective.dto.response.RetrospectiveResponse;
import com.naleul.naleul.domain.retrospective.entity.Retrospective;
import com.naleul.naleul.domain.retrospective.enums.ReviewType;
import com.naleul.naleul.domain.retrospective.repository.RetrospectiveRepository;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.service.UserFinder;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import com.naleul.naleul.global.policy.PlanLimits;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RetrospectiveService {

    private final RetrospectiveRepository retrospectiveRepository;
    private final UserFinder userFinder;
    private final CategoryFinder categoryFinder;

    // 생성
    @Transactional
    public RetrospectiveResponse create(Long userId, RetrospectiveCreateRequest request) {
        User user = userFinder.findUser(userId);
        if (request.getGoalCategoryId() == null) {
            throw new CustomException(ErrorCode.RETROSPECTIVE_GOAL_CATEGORY_REQUIRED);
        }
        GoalCategory goalCategory = categoryFinder.findGoalCategory(request.getGoalCategoryId(), user);
        validateRetrospectiveLimit(user, goalCategory);
        LocalDate reviewDate = resolveReviewDate(request.getReviewType(), request.getReviewDate());

        GeneralCategory generalCategory = request.getGeneralCategoryId() != null
                ? categoryFinder.findGeneralCategory(request.getGeneralCategoryId(), user) : null;

        Retrospective retrospective = Retrospective.builder()
                .user(user)
                .reviewType(request.getReviewType())
                .reviewDate(reviewDate)
                .content(request.getContent())
                .goalCategory(goalCategory)
                .generalCategory(generalCategory)
                .isDeleted(false)
                .build();

        return RetrospectiveResponse.from(retrospectiveRepository.save(retrospective));
    }

    // 단건 조회
    public RetrospectiveResponse getOne(Long retrospectiveId) {
        return RetrospectiveResponse.from(findActiveById(retrospectiveId));
    }

    // 목록 조회 (필터 + 페이지네이션)
    public Page<RetrospectiveResponse> getList(
            Long userId,
            ReviewType reviewType,
            LocalDate baseDate,
            Long goalCategoryId,
            Long generalCategoryId,
            Pageable pageable
    ) {
        userFinder.validateUserExists(userId);

        DateRange range = resolveDateRange(reviewType, baseDate);

        return retrospectiveRepository
                .findAllByFilter(userId, reviewType, range.start(), range.end(), goalCategoryId, generalCategoryId, pageable)
                .map(RetrospectiveResponse::from);
    }

    @Transactional
    public RetrospectiveResponse update(Long userId, Long retrospectiveId, RetrospectiveUpdateRequest request) {
        Retrospective retrospective = findActiveByIdAndUserId(retrospectiveId, userId);
        User user = retrospective.getUser();

        GoalCategory goalCategory = request.getGoalCategoryId() != null
                ? categoryFinder.findGoalCategory(request.getGoalCategoryId(), user)
                : retrospective.getGoalCategory();

        GeneralCategory generalCategory = request.getGeneralCategoryId() != null
                ? categoryFinder.findGeneralCategory(request.getGeneralCategoryId(), user)
                : retrospective.getGeneralCategory();

        retrospective.update(request.getContent(), goalCategory, generalCategory);
        return RetrospectiveResponse.from(retrospective);
    }

    // 삭제 (소프트, 소유권 검증 포함)
    @Transactional
    public void delete(Long userId, Long retrospectiveId) {
        Retrospective retrospective = findActiveByIdAndUserId(retrospectiveId, userId);
        retrospective.delete();
    }

    // ── private 헬퍼 ──────────────────────────────────────

    /**
     * Free 플랜 유저의 "목표(GoalCategory) 하나당 회고 생성 개수" 제한을 체크.
     * 유저 전체 기준이 아니라, 이 특정 목표 안에서만 개수를 셈.
     */
    private void validateRetrospectiveLimit(User user, GoalCategory goalCategory) {
        if (user.isCurrentlyPremium()) {
            return;
        }

        long currentCount = retrospectiveRepository.countActiveByGoalCategoryId(goalCategory.getGoalCategoryId());
        if (currentCount >= PlanLimits.RETROSPECTIVE_FREE_LIMIT) {
            throw new CustomException(ErrorCode.RETROSPECTIVE_LIMIT_EXCEEDED);
        }
    }

    private LocalDate resolveReviewDate(ReviewType type, LocalDate requestDate) {
        if (requestDate != null) return requestDate;

        LocalDate today = LocalDate.now();
        return switch (type) {
            case DAILY -> today;
            case WEEKLY -> today.with(WeekFields.of(Locale.KOREA).dayOfWeek(), 1);
            case MONTHLY -> today.withDayOfMonth(1);
        };
    }

    private DateRange resolveDateRange(ReviewType reviewType, LocalDate baseDate) {
        if (reviewType == null) return new DateRange(null, null);
        if (baseDate == null) return new DateRange(null, null);

        return switch (reviewType) {
            case DAILY -> new DateRange(baseDate, baseDate);
            case WEEKLY -> {
                LocalDate monday = baseDate.with(WeekFields.of(Locale.KOREA).dayOfWeek(), 1);
                LocalDate sunday = monday.plusDays(6);
                yield new DateRange(monday, sunday);
            }
            case MONTHLY -> {
                LocalDate start = baseDate.withDayOfMonth(1);
                LocalDate end = baseDate.withDayOfMonth(baseDate.lengthOfMonth());
                yield new DateRange(start, end);
            }
        };
    }

    private Retrospective findActiveById(Long id) {
        return retrospectiveRepository.findActiveById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.RETROSPECTIVE_NOT_FOUND));
    }

    private Retrospective findActiveByIdAndUserId(Long id, Long userId) {
        return retrospectiveRepository.findActiveByIdAndUserId(id, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.RETROSPECTIVE_NOT_FOUND));
    }

    private record DateRange(LocalDate start, LocalDate end) {}
}