package com.naleul.naleul.domain.goalCreation.repository;

import com.naleul.naleul.domain.goalCreation.entity.GoalReviewField;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalReviewFieldRepository extends JpaRepository<GoalReviewField, Long> {

    boolean existsBySessionId(Long sessionId);
}
