package com.naleul.naleul.domain.aiQuality.repository;

import com.naleul.naleul.domain.aiQuality.entity.AiSuggestionLog;
import com.naleul.naleul.domain.aiQuality.enums.AiSuggestionStatus;
import com.naleul.naleul.domain.aiUsage.enums.AiFeature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AiSuggestionLogRepository extends JpaRepository<AiSuggestionLog, Long> {

    /** 같은 사용자 · 기능의 가장 최근 "아직 적용 안 한" 제안 (적용과 짝짓기) */
    Optional<AiSuggestionLog> findFirstByUserIdAndFeatureAndStatusAndCreatedAtAfterOrderByAiSuggestionLogIdDesc(
            Long userId, AiFeature feature, AiSuggestionStatus status, LocalDateTime since);

    /** 말로 고치기: 같은 목표의 가장 최근 제안 */
    Optional<AiSuggestionLog> findFirstByUserIdAndFeatureAndTargetGoalIdAndStatusAndCreatedAtAfterOrderByAiSuggestionLogIdDesc(
            Long userId, AiFeature feature, Long targetGoalId, AiSuggestionStatus status, LocalDateTime since);
}
