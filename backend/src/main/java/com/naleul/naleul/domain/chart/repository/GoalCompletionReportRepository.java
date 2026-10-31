package com.naleul.naleul.domain.chart.repository;


import com.naleul.naleul.domain.chart.entity.GoalCompletionReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GoalCompletionReportRepository extends JpaRepository<GoalCompletionReport, Long> {

    Optional<GoalCompletionReport> findByGoalCategoryGoalCategoryId(Long goalCategoryId);

    boolean existsByGoalCategoryGoalCategoryId(Long goalCategoryId);
}