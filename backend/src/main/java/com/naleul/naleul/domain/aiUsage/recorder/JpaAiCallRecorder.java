package com.naleul.naleul.domain.aiUsage.recorder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.naleul.naleul.domain.aiUsage.entity.AiCallLog;
import com.naleul.naleul.domain.aiUsage.enums.AiFeature;
import com.naleul.naleul.domain.aiUsage.repository.AiCallLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * ai_call_log 저장 구현체.
 *
 * 왜 @Transactional 대신 TransactionTemplate(REQUIRES_NEW) 인가:
 *  - 호출한 쪽에 트랜잭션이 있으면 save 가 거기에 끼어든다. 그러면 저장이 실패했을 때
 *    바깥 트랜잭션까지 rollback-only 가 돼서, 기록 때문에 본 기능(리포트 저장 등)이 실패할 수 있다.
 *  - @Transactional(REQUIRES_NEW) 를 메서드에 붙이면 커밋 실패 예외가 메서드 "밖"(프록시)에서 터져서
 *    try/catch 로 잡을 수 없다.
 *  → 별도 트랜잭션을 try 안에서 직접 열고 닫아야 "어떤 경우에도 예외를 던지지 않는다"를 지킬 수 있다.
 *  또, 바깥 트랜잭션이 롤백돼도 "AI 를 불렀고 돈이 나갔다"는 기록은 남는다.
 */
@Slf4j
@Component
public class JpaAiCallRecorder implements AiCallRecorder {

    private final AiCallLogRepository repository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;

    public JpaAiCallRecorder(AiCallLogRepository repository, ObjectMapper objectMapper,
                             PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void recordSuccess(AiFeature feature, String responseJson, long latencyMs) {
        try {
            AnthropicUsage u = AnthropicUsage.parse(objectMapper, responseJson);
            if (u == null) {
                log.warn("[AiCallRecorder] usage 를 찾지 못해 기록하지 않음 feature={}", feature);
                return;
            }
            save(AiCallLog.of(feature, u.model(), u.inputTokens(), u.outputTokens(),
                    u.cacheCreationInputTokens(), u.cacheReadInputTokens(), latencyMs, true));
        } catch (Exception e) {
            log.warn("[AiCallRecorder] 기록 실패 feature={} msg={}", feature, e.getMessage());
        }
    }

    @Override
    public void recordFailure(AiFeature feature, String model, long latencyMs) {
        try {
            save(AiCallLog.of(feature, model, 0, 0, 0, 0, latencyMs, false));
        } catch (Exception e) {
            log.warn("[AiCallRecorder] 실패 기록 실패 feature={} msg={}", feature, e.getMessage());
        }
    }

    private void save(AiCallLog row) {
        tx.executeWithoutResult(status -> repository.save(row));
    }
}
