package com.naleul.naleul.domain.aiUsage.limit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * AI 기능 한도에 막힌 시도 기록 (V70 ai_limit_hit) — 운영 대시보드 "AI 관심도"에서 "써 보려 했는데 막힌" 수를 본다.
 *
 * 한도 예외를 던지기 직전에 부른다. 바깥 트랜잭션은 예외로 롤백되므로 새 트랜잭션(REQUIRES_NEW)에 저장하고,
 * 기록이 실패해도 본 흐름(한도 안내)은 그대로 가도록 예외를 삼킨다. 막힌 순간의 플랜도 같이 남긴다.
 */
@Slf4j
@Component
public class AiLimitHitRecorder {

    /** feature 값: GOAL_INTERVIEW · GOAL_PLAN · GOAL_EDIT · SCHEDULE_EDIT (AiFeature 이름과 같다) */
    public enum Reason { DAILY, WEEKLY, MONTHLY, REGENERATE }

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public AiLimitHitRecorder(NamedParameterJdbcTemplate jdbc, PlatformTransactionManager txManager) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void record(Long userId, String feature, Reason reason) {
        if (userId == null) return;
        try {
            tx.executeWithoutResult(s -> jdbc.update("""
                    INSERT INTO ai_limit_hit (user_id, feature, reason, user_plan, created_at)
                    SELECT u.user_id, :feature, :reason, %s, NOW(6) FROM users u WHERE u.user_id = :userId
                    """.formatted(UserPlanSql.planOf("u")),
                    new MapSqlParameterSource("userId", userId).addValue("feature", feature)
                            .addValue("reason", reason.name())));
        } catch (RuntimeException e) {
            log.warn("[AiLimitHit] 기록 실패 userId={} feature={}", userId, feature, e);
        }
    }
}
