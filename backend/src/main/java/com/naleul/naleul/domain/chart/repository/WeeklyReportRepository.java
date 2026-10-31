package com.naleul.naleul.domain.chart.repository;

import com.naleul.naleul.domain.chart.entity.WeeklyReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WeeklyReportRepository extends JpaRepository<WeeklyReport, Long> {

    boolean existsByUserUserIdAndWeekStartDate(Long userId, LocalDate weekStartDate);

    Optional<WeeklyReport> findByUserUserIdAndWeekStartDate(Long userId, LocalDate weekStartDate);

    // 리포트 리스트 화면용 — 최신 주가 맨 위로 오도록 최신순 정렬
    List<WeeklyReport> findAllByUserUserIdOrderByWeekStartDateDesc(Long userId);

    // ✅ 신규: 월간 리포트 생성용 — 오래된 주부터 순서대로 4개씩 묶기 위해 오래된순 정렬
    List<WeeklyReport> findAllByUserUserIdOrderByWeekStartDateAsc(Long userId);
}