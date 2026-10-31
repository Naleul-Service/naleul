package com.naleul.naleul.domain.pattern.repository;

import com.naleul.naleul.domain.pattern.entity.PatternSuggestionAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PatternSuggestionActionRepository extends JpaRepository<PatternSuggestionAction, Long> {

    /**
     * 제안 찾기에서 제외할 key 목록: 한 번이라도 반영(APPLIED)했거나, since 이후에 미룬(DISMISSED) 제안.
     * 제안 후보를 만든 뒤 이 목록에 있는 key 를 걸러낸다 (쿼리 1번).
     */
    @Query("""
        SELECT DISTINCT a.suggestionKey FROM PatternSuggestionAction a
        WHERE a.userId = :userId
          AND (a.action = com.naleul.naleul.domain.pattern.enums.PatternSuggestionActionType.APPLIED
               OR (a.action = com.naleul.naleul.domain.pattern.enums.PatternSuggestionActionType.DISMISSED
                   AND a.createdAt >= :dismissedSince))
    """)
    List<String> findBlockedKeys(@Param("userId") Long userId,
                                 @Param("dismissedSince") LocalDateTime dismissedSince);
}
