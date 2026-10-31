package com.naleul.naleul.domain.userColor.service;

import com.naleul.naleul.domain.generalCategory.repository.GeneralCategoryRepository;
import com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus;
import com.naleul.naleul.domain.goalCategory.repository.GoalCategoryRepository;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.service.UserFinder;
import com.naleul.naleul.domain.userColor.dto.response.UserColorResponse;
import com.naleul.naleul.domain.userColor.entity.DefaultColors;
import com.naleul.naleul.domain.userColor.entity.UserColor;
import com.naleul.naleul.domain.userColor.repository.UserColorRepository;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
@Service
@RequiredArgsConstructor

@Transactional(readOnly = true)
public class UserColorService {

    private final UserColorRepository userColorRepository;
    private final UserFinder userFinder;
    private final GoalCategoryRepository goalCategoryRepository;
    private final GeneralCategoryRepository generalCategoryRepository;

    @Transactional
    public void createDefaultColors(User user) {
        List<UserColor> defaultColors = DefaultColors.CODES.stream()
                .map(code -> UserColor.builder()
                        .user(user)
                        .colorCode(code)
                        .isDefault(true)
                        .build())
                .toList();
        userColorRepository.saveAll(defaultColors);
    }

    @Transactional(readOnly = true)
    public List<UserColorResponse> getUserColors(Long userId) {
        return userColorRepository.findByUser_UserId(userId)
                .stream()
                .filter(c -> !DefaultColors.isRetired(c))
                .map(UserColorResponse::from)
                .toList();
    }

    @Transactional
    public UserColorResponse addColorToUser(Long userId, String colorCode) {
        if (userColorRepository.existsByUser_UserIdAndColorCode(userId, colorCode)) {
            throw new CustomException(ErrorCode.COLOR_ALREADY_EXISTS);
        }

        User user = userFinder.findUser(userId);

        UserColor saved = userColorRepository.save(UserColor.builder()
                .user(user)
                .colorCode(colorCode)
                .isDefault(false)
                .build());

        return UserColorResponse.from(saved);  // 반환 추가
    }

    @Transactional
    public void deleteUserColor(Long userId, Long userColorId) {
        UserColor userColor = userColorRepository
                .findByUserColorIdAndUser_UserId(userColorId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.COLOR_NOT_FOUND));

        if (userColor.isDefault()) {
            throw new CustomException(ErrorCode.DEFAULT_COLOR_CANNOT_BE_DELETED);
        }

        // goal_category 체크 (DELETED 제외)
        if (goalCategoryRepository.existsByUserColorAndNotDeleted(userColor, GoalCategoryStatus.DELETED)) {
            throw new CustomException(ErrorCode.COLOR_IN_USE);
        }

        // general_category 체크 추가
        if (generalCategoryRepository.existsByColor(userColor)) {
            throw new CustomException(ErrorCode.COLOR_IN_USE);
        }

        userColorRepository.delete(userColor);
    }
}