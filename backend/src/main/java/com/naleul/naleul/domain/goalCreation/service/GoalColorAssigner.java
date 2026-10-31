package com.naleul.naleul.domain.goalCreation.service;

import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus;
import com.naleul.naleul.domain.goalCategory.repository.GoalCategoryRepository;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.userColor.entity.UserColor;
import com.naleul.naleul.domain.userColor.repository.UserColorRepository;
import com.naleul.naleul.domain.userColor.service.UserColorFinder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AI 목표의 색상 결정.
 *  1) 사용자가 컬러칩을 골랐으면(colorId) 그 색 — 본인 색인지 소유권까지 확인
 *  2) 안 골랐으면 사용자의 색 중 "진행 중/시작 전 목표가 아직 안 쓰는 색"을 id 순서로 첫 번째
 *  3) 전부 쓰고 있으면 첫 번째 색, 색이 하나도 없으면 null (color_id는 NULL 허용)
 */
@Component
@RequiredArgsConstructor
public class GoalColorAssigner {

    private static final Set<GoalCategoryStatus> ACTIVE = Set.of(GoalCategoryStatus.NOT_STARTED, GoalCategoryStatus.IN_PROGRESS);

    private final UserColorFinder userColorFinder;
    private final UserColorRepository userColorRepository;
    private final GoalCategoryRepository goalCategoryRepository;

    public UserColor assign(User user, Long selectedColorId) {
        if (selectedColorId != null) {
            return userColorFinder.findUserColor(selectedColorId, user);   // 남의 색이면 404 COLOR_NOT_FOUND
        }

        List<UserColor> colors = userColorRepository.findByUser_UserId(user.getUserId()).stream()
                .filter(c -> !com.naleul.naleul.domain.userColor.entity.DefaultColors.isRetired(c))
                .sorted(Comparator.comparing(UserColor::getUserColorId))
                .toList();
        if (colors.isEmpty()) return null;

        Set<Long> usedColorIds = goalCategoryRepository.findAllByUserIdWithAll(user.getUserId()).stream()
                .filter(g -> ACTIVE.contains(g.getGoalCategoryStatus()))
                .map(GoalCategory::getUserColor)
                .filter(Objects::nonNull)
                .map(UserColor::getUserColorId)
                .collect(Collectors.toSet());

        return colors.stream()
                .filter(c -> !usedColorIds.contains(c.getUserColorId()))
                .findFirst()
                .orElse(colors.get(0));
    }
}
