package com.naleul.naleul.domain.generalCategory.deleter;

import com.naleul.naleul.domain.generalCategory.entity.GeneralCategory;
import com.naleul.naleul.domain.routine.service.RoutineService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * GeneralCategory 삭제 시, 하위 Routine까지 연쇄적으로 정리하는 정책을 담당.
 * "무엇을, 어떤 순서로 정리할지"가 바뀌면 이 클래스만 수정하면 됨.
 */
@Component
@RequiredArgsConstructor
public class GeneralCategoryCascadeDeleter {

    private final RoutineService routineService;

    public void deleteCascade(GeneralCategory generalCategory) {
        generalCategory.delete();   // 소프트 삭제 (색상 참조 해제)
        routineService.deleteAllByGeneralCategoryId(generalCategory.getGeneralCategoryId());
    }
}