package com.naleul.naleul.domain.generalCategory.reader;

import com.naleul.naleul.domain.category.service.CategoryFinder;
import com.naleul.naleul.domain.generalCategory.dto.response.GeneralCategoryResponse;
import com.naleul.naleul.domain.generalCategory.entity.GeneralCategory;
import com.naleul.naleul.domain.generalCategory.repository.GeneralCategoryRepository;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.service.UserFinder;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GeneralCategoryReader {

    private final GeneralCategoryRepository generalCategoryRepository;
    private final UserFinder userFinder;
    private final CategoryFinder categoryFinder;

    // 재조회 (생성/수정 직후 등, 소유권이 이미 보장된 상황에서 사용)
    public GeneralCategoryResponse getResponse(Long generalCategoryId) {
        GeneralCategory generalCategory = generalCategoryRepository.findByIdWithAll(generalCategoryId)
                .orElseThrow(() -> new CustomException(ErrorCode.GENERAL_CATEGORY_NOT_FOUND));
        return GeneralCategoryResponse.from(generalCategory);
    }

    // 단건 조회
    public GeneralCategoryResponse getGeneralCategory(Long userId, Long generalCategoryId) {
        User user = userFinder.findUser(userId);
        GeneralCategory generalCategory = categoryFinder.findGeneralCategory(generalCategoryId, user);
        return GeneralCategoryResponse.from(generalCategory);
    }

    // 전체 조회
    public List<GeneralCategoryResponse> getGeneralCategories(Long userId) {
        User user = userFinder.findUser(userId);
        return generalCategoryRepository.findAllByUser(user)
                .stream()
                .map(GeneralCategoryResponse::from)
                .collect(Collectors.toList());
    }
}
