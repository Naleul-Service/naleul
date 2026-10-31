package com.naleul.naleul.domain.aiQuality.service;

import com.naleul.naleul.domain.aiQuality.entity.AiSuggestionLog;
import com.naleul.naleul.domain.aiQuality.entity.AiSuggestionTarget;
import com.naleul.naleul.domain.aiQuality.enums.AiSuggestionStatus;
import com.naleul.naleul.domain.aiQuality.repository.AiSuggestionLogRepository;
import com.naleul.naleul.domain.aiQuality.repository.AiSuggestionTargetRepository;
import com.naleul.naleul.domain.aiUsage.enums.AiFeature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

/**
 * AI 제안 기록 (V64) — 미리보기 · 해석 때 suggested, 적용 · 확정 때 applied.
 *
 * 기록은 부가 기능이라 실패해도 본 기능(적용 · 확정)을 막지 않는다:
 *  - 별도 트랜잭션(REQUIRES_NEW)으로 저장해서, 기록이 실패해도 바깥 트랜잭션이 rollback-only 가 되지 않는다.
 *  - 예외는 로그만 남기고 삼킨다.
 * 적용은 "같은 사용자 · 기능(· 목표)의 가장 최근 미적용 제안"(PAIR_WINDOW 안)과 짝짓는다 → 클라이언트 변경 없이 기록할 수 있다.
 */
@Slf4j
@Component
public class AiSuggestionLogWriter {

    /** 미리보기 후 이 시간 안에 적용하면 같은 제안으로 본다 */
    static final long PAIR_WINDOW_MINUTES = 120;

    private final AiSuggestionLogRepository logRepository;
    private final AiSuggestionTargetRepository targetRepository;
    private final TransactionTemplate tx;

    /** 제안 당시 플랜 (V70) */
    @Autowired(required = false)
    private com.naleul.naleul.domain.aiUsage.limit.UserPlanReader planReader;

    @Autowired
    public AiSuggestionLogWriter(AiSuggestionLogRepository logRepository,
                                 AiSuggestionTargetRepository targetRepository,
                                 PlatformTransactionManager txManager) {
        this.logRepository = logRepository;
        this.targetRepository = targetRepository;
        this.tx = new TransactionTemplate(txManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** AI 가 제안을 냈다 (미리보기 · 해석) */
    public void suggested(Long userId, AiFeature feature, Long targetGoalId, boolean aiUsed,
                          int suggestedCount, int validCount, String snapshotJson) {
        try {
            String plan = planReader == null ? null : planReader.planOf(userId);
            tx.executeWithoutResult(s -> logRepository.save(AiSuggestionLog.suggested(userId, feature, targetGoalId,
                    aiUsed, suggestedCount, validCount, snapshotJson, LocalDateTime.now()).withPlan(plan)));
        } catch (RuntimeException e) {
            log.warn("[AiSuggestionLog] 제안 기록 실패 userId={} feature={}", userId, feature, e);
        }
    }

    /** 짝지을 가장 최근 미적용 제안 (브레인 덤프는 스냅샷 비교에 먼저 필요) */
    public Optional<AiSuggestionLog> latestPending(Long userId, AiFeature feature, Long targetGoalId) {
        try {
            return findPending(userId, feature, targetGoalId);
        } catch (RuntimeException e) {
            log.warn("[AiSuggestionLog] 제안 조회 실패 userId={} feature={}", userId, feature, e);
            return Optional.empty();
        }
    }

    private Optional<AiSuggestionLog> findPending(Long userId, AiFeature feature, Long targetGoalId) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(PAIR_WINDOW_MINUTES);
        return targetGoalId != null
                ? logRepository.findFirstByUserIdAndFeatureAndTargetGoalIdAndStatusAndCreatedAtAfterOrderByAiSuggestionLogIdDesc(
                userId, feature, targetGoalId, AiSuggestionStatus.SUGGESTED, since)
                : logRepository.findFirstByUserIdAndFeatureAndStatusAndCreatedAtAfterOrderByAiSuggestionLogIdDesc(
                userId, feature, AiSuggestionStatus.SUGGESTED, since);
    }

    /**
     * 사용자가 적용 · 확정했다. 짝지을 제안이 없으면(오래됐거나 기록 실패) 남기지 않는다.
     * @param taskIds    만든 · 옮긴 Task (결과 집계용)
     * @param routineIds 옮긴 루틴
     */
    public void applied(Long userId, AiFeature feature, Long targetGoalId, int appliedCount, Integer skippedCount,
                        Integer editedCount, Integer goalChangedCount,
                        Collection<Long> taskIds, Collection<Long> routineIds) {
        // 적용이 실제로 커밋된 뒤에만 남긴다 (적용이 롤백되면 "적용함"으로 세지 않게)
        Runnable work = () -> writeApplied(userId, feature, targetGoalId, appliedCount, skippedCount, editedCount,
                goalChangedCount, taskIds, routineIds);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    work.run();
                }
            });
        } else {
            work.run();
        }
    }

    private void writeApplied(Long userId, AiFeature feature, Long targetGoalId, int appliedCount, Integer skippedCount,
                              Integer editedCount, Integer goalChangedCount,
                              Collection<Long> taskIds, Collection<Long> routineIds) {
        try {
            // afterCommit 에서 불리므로 조회부터 새 트랜잭션 안에서 (방금 끝난 트랜잭션에 섞이지 않게)
            tx.executeWithoutResult(s -> {
                Optional<AiSuggestionLog> pending = findPending(userId, feature, targetGoalId);
                if (pending.isEmpty()) return;
                AiSuggestionLog l = pending.get();
                Long id = l.getAiSuggestionLogId();
                l.applied(appliedCount, skippedCount, editedCount, goalChangedCount, LocalDateTime.now());
                if (taskIds != null) taskIds.stream().distinct()
                        .forEach(t -> targetRepository.save(AiSuggestionTarget.of(id, AiSuggestionTarget.Type.TASK, t)));
                if (routineIds != null) routineIds.stream().distinct()
                        .forEach(r -> targetRepository.save(AiSuggestionTarget.of(id, AiSuggestionTarget.Type.ROUTINE, r)));
            });
        } catch (RuntimeException e) {
            log.warn("[AiSuggestionLog] 적용 기록 실패 userId={} feature={}", userId, feature, e);
        }
    }
}
