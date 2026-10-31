package com.naleul.naleul.domain.routine.service;

import com.naleul.naleul.domain.routine.entity.Routine;
import com.naleul.naleul.domain.routine.repository.RoutineRepository;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RoutineFinder {

    private final RoutineRepository routineRepository;

    // 소유권 확인된 조회 — update()에서 재조회할 때도 사용
    public Routine findByIdAndUser(Long id, User user) {
        return routineRepository.findByRoutineIdAndUser(id, user)
                .orElseThrow(() -> new CustomException(ErrorCode.ROUTINE_NOT_FOUND));
    }
}