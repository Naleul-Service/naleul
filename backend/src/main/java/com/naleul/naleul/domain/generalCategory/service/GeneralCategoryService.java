package com.naleul.naleul.domain.generalCategory.service;


import com.naleul.naleul.domain.category.service.CategoryFinder;
import com.naleul.naleul.domain.generalCategory.deleter.GeneralCategoryCascadeDeleter;
import com.naleul.naleul.domain.generalCategory.dto.request.GeneralCategoryCompleteRequest;
import com.naleul.naleul.domain.generalCategory.dto.request.GeneralCategoryCreateRequest;
import com.naleul.naleul.domain.generalCategory.dto.request.GeneralCategoryUpdateRequest;
import com.naleul.naleul.domain.generalCategory.dto.response.GeneralCategoryResponse;
import com.naleul.naleul.domain.generalCategory.entity.GeneralCategory;
import com.naleul.naleul.domain.generalCategory.factory.GeneralCategoryFactory;
import com.naleul.naleul.domain.generalCategory.reader.GeneralCategoryReader;
import com.naleul.naleul.domain.generalCategory.repository.GeneralCategoryRepository;
import com.naleul.naleul.domain.generalCategory.validator.GeneralCategoryValidator;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.routine.service.RoutineService;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.service.UserFinder;
import com.naleul.naleul.domain.userColor.entity.UserColor;
import com.naleul.naleul.domain.userColor.service.UserColorFinder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
@Transactional
public class GeneralCategoryService {

    private final GeneralCategoryRepository generalCategoryRepository;
    private final UserFinder userFinder;
    private final UserColorFinder userColorFinder;
    private final CategoryFinder categoryFinder;
    private final GeneralCategoryReader generalCategoryReader;
    private final GeneralCategoryFactory generalCategoryFactory;
    private final GeneralCategoryValidator generalCategoryValidator;
    private final RoutineService routineService;
    private final GeneralCategoryCascadeDeleter generalCategoryCascadeDeleter;

    // 생성
    public GeneralCategoryResponse create(Long userId, GeneralCategoryCreateRequest request) {
        User user = userFinder.findUser(userId);
        GoalCategory goalCategory = categoryFinder.findGoalCategory(request.getGoalCategoryId(), user);

        generalCategoryValidator.validateCreate(user, goalCategory, request);
        UserColor color = userColorFinder.findUserColor(request.getColorId(), user);

        GeneralCategory generalCategory = generalCategoryFactory.create(user, goalCategory, color, request);
        generalCategoryRepository.save(generalCategory);

        return generalCategoryReader.getResponse(generalCategory.getGeneralCategoryId());
    }

    // 목표 완료 처리
    public GeneralCategoryResponse complete(Long generalCategoryId, Long userId, GeneralCategoryCompleteRequest request) {
        User user = userFinder.findUser(userId);
        GeneralCategory generalCategory = categoryFinder.findGeneralCategory(generalCategoryId, user);

        generalCategory.complete(request.getGeneralCategoryEndDate(), request.getAchievement(), request.getAchievementImageUrl());
        return generalCategoryReader.getResponse(generalCategoryId);
    }

    // 수정
    public GeneralCategoryResponse update(Long userId, Long generalCategoryId, GeneralCategoryUpdateRequest request) {
        User user = userFinder.findUser(userId);
        GeneralCategory generalCategory = categoryFinder.findGeneralCategory(generalCategoryId, user);
        generalCategoryValidator.validateUpdate(generalCategory, request);

        // goalCategoryId가 없거나, 지금 소속된 것과 같은 id면 조회 자체를 건너뜀
        Long currentGoalCategoryId = generalCategory.getGoalCategory().getGoalCategoryId();
        boolean goalCategoryChanged = request.getGoalCategoryId() != null
                && !request.getGoalCategoryId().equals(currentGoalCategoryId);

        GoalCategory newGoalCategory = goalCategoryChanged
                ? categoryFinder.findGoalCategory(request.getGoalCategoryId(), user)
                : null;

        UserColor userColor = request.getColorId() != null
                ? userColorFinder.findUserColor(request.getColorId(), user)
                : null;

        generalCategory.update(request.getGeneralCategoryName(), newGoalCategory, userColor,
                request.getGeneralCategoryStartDate(), request.getGeneralCategoryEndDate());

        if (goalCategoryChanged) {
            routineService.syncGoalCategoryForGeneralCategory(generalCategoryId, newGoalCategory);
        }

        return generalCategoryReader.getResponse(generalCategoryId);
    }

    // 삭제
    public void delete(Long userId, Long generalCategoryId) {
        User user = userFinder.findUser(userId);
        GeneralCategory generalCategory = categoryFinder.findGeneralCategory(generalCategoryId, user);

        generalCategoryCascadeDeleter.deleteCascade(generalCategory);
    }
}