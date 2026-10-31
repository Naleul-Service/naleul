package com.naleul.naleul.domain.aiUsage.quota;

import com.naleul.naleul.domain.aiUsage.enums.AiFeature;
import com.naleul.naleul.domain.goalCreation.config.GoalAiProperties;
import com.naleul.naleul.domain.goalCreation.support.GoalClock;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.service.UserFinder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 기능 사용 횟수 확인 · 기록 (GOAL_EDIT, BRAIN_DUMP).
 * AI 호출 전에 tryConsume → 실패하면 refund 로 돌려준다 (AI 가 안 돼서 못 쓴 건 세지 않는다).
 * 호출하는 쪽이 트랜잭션 밖(AI 호출 중 커넥션을 잡지 않으려고)이라 짧은 새 트랜잭션으로 처리한다.
 */
@Service
@RequiredArgsConstructor
public class AiQuotaService {

    private final AiQuotaUsageRepository repository;
    private final UserFinder userFinder;
    private final GoalAiProperties goalAiProperties;
    private final GoalClock clock;

    /** @return 기록한 사용 ID (refund 용), 한도를 넘었으면 null */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long tryConsume(Long userId, AiFeature feature) {
        if (used(userId, feature) >= limit(userId, feature)) return null;
        return repository.save(AiQuotaUsage.of(userId, feature, clock.now())).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void refund(Long usageId) {
        if (usageId != null) repository.deleteById(usageId);
    }

    @Transactional(readOnly = true)
    public long used(Long userId, AiFeature feature) {
        return repository.countByUserIdAndFeatureAndUsedAtGreaterThanEqual(userId, feature, clock.startOfUserToday());
    }

    @Transactional(readOnly = true)
    public int limit(Long userId, AiFeature feature) {
        User user = userFinder.findUser(userId);
        return AiQuotaPolicy.dailyLimit(feature, goalAiProperties.proLimitFor(userId, user.isCurrentlyPremium()));
    }
}
