package com.naleul.naleul.domain.goalCreation.personal;

import com.naleul.naleul.domain.goalCategory.kind.GoalKind;
import com.naleul.naleul.domain.goalOutcome.service.AchieverStats;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 초안을 만들 때 쓰는 개인 맞춤 정보 한 묶음 */
@Component
@RequiredArgsConstructor
public class PersonalPlanContextFactory {

    private final CapacityProfileReader capacityReader;
    private final AchieverStats achieverStats;

    public PersonalPlanContext create(Long userId, PlanIntensity intensity, GoalKind kind) {
        CapacityProfile profile = capacityReader.read(userId);
        AchieverSummary achievers = kind != null ? achieverStats.forSubType(kind.subType()).orElse(null) : null;
        return PersonalPlanContext.of(profile, intensity, achievers);
    }
}
