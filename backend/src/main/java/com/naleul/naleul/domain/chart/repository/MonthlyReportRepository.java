package com.naleul.naleul.domain.chart.repository;

import com.naleul.naleul.domain.chart.entity.MonthlyReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MonthlyReportRepository extends JpaRepository<MonthlyReport, Long> {

    boolean existsByUserUserIdAndPeriodStartDate(Long userId, LocalDate periodStartDate);

    Optional<MonthlyReport> findByUserUserIdAndPeriodStartDate(Long userId, LocalDate periodStartDate);

    List<MonthlyReport> findAllByUserUserIdOrderByPeriodStartDateDesc(Long userId);

    // ✅ 지금까지 몇 개의 월간 리포트를 만들었는지 — "다음 4주가 언제 준비되는지" 판단용
    long countByUserUserId(Long userId);
}