package com.naleul.naleul.domain.generalCategory.service;

import com.naleul.naleul.domain.generalCategory.entity.GeneralCategory;
import com.naleul.naleul.domain.generalCategory.repository.GeneralCategoryRepository;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 영역(세부 목표)을 고르지 않고 루틴·Task 를 만들 때 쓸 그릇.
 *
 * 왜 필요한가: 루틴·Task 는 DB 상 세부 목표(general_category) 아래에만 붙을 수 있어서,
 * 화면이 "루틴은 세부 목표 아래에 만들어요. 세부 목표를 먼저 추가해 주세요"라고 막았다.
 * 사용자가 트리 구조를 몰라도 루틴을 바로 추가할 수 있게, 안 고르면 목표의 "기타 할 일"(없으면 만든다)에 넣는다.
 * (Brain dump 가 쓰는 것과 같은 그릇)
 */
@Component
@RequiredArgsConstructor
public class DefaultBucketResolver {

    private final GeneralCategoryRepository generalCategoryRepository;

    public GeneralCategory bucketOf(GoalCategory goal) {
        List<GeneralCategory> existing = generalCategoryRepository.findDefaultBuckets(goal.getGoalCategoryId());
        if (!existing.isEmpty()) return existing.get(0);
        return generalCategoryRepository.save(GeneralCategory.createDefaultBucket(goal));
    }
}
