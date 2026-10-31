package com.naleul.naleul.domain.planner.service;

import com.naleul.naleul.domain.planner.dto.request.PlannerSettingUpdateRequest;
import com.naleul.naleul.domain.planner.dto.response.PlannerSettingResponse;
import com.naleul.naleul.domain.planner.entity.PlannerSetting;
import com.naleul.naleul.domain.planner.repository.PlannerSettingRepository;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class PlannerSettingService {

    private final PlannerSettingRepository plannerSettingRepository;

    public PlannerSettingResponse get(Long userId) {
        return PlannerSettingResponse.from(getOrCreate(userId));
    }

    public PlannerSettingResponse update(Long userId, PlannerSettingUpdateRequest request) {
        PlannerSetting setting = getOrCreateForUpdate(userId);
        setting.update(request.autoGenerateEnabled(), request.nightlyPushEnabled(), request.autoCarryEnabled());
        return PlannerSettingResponse.from(setting);
    }

    /** 설정 행 (없으면 기본값으로 생성) */
    public PlannerSetting getOrCreate(Long userId) {
        plannerSettingRepository.insertDefaultIfAbsent(userId);
        return plannerSettingRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * 설정 행을 잠근 채 가져온다 (없으면 생성).
     * 같은 사용자에 대한 패턴 초기화·22시 생성·Brain dump 확정이 동시에 돌지 않게 하는 용도.
     */
    public PlannerSetting getOrCreateForUpdate(Long userId) {
        plannerSettingRepository.insertDefaultIfAbsent(userId);
        return plannerSettingRepository.findForUpdate(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
