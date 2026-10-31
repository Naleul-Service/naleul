package com.naleul.naleul.domain.planner.repository;

import com.naleul.naleul.domain.planner.entity.DailyPlan;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyPlanRepository extends JpaRepository<DailyPlan, Long> {

    @Query("SELECT p FROM DailyPlan p WHERE p.userId = :userId AND p.planDate = :planDate")
    Optional<DailyPlan> findByUserIdAndPlanDate(@Param("userId") Long userId,
                                                @Param("planDate") LocalDate planDate);

    @Query("""
        SELECT p FROM DailyPlan p
        WHERE p.userId = :userId AND p.planDate BETWEEN :startDate AND :endDate
    """)
    List<DailyPlan> findAllInRange(@Param("userId") Long userId,
                                   @Param("startDate") LocalDate startDate,
                                   @Param("endDate") LocalDate endDate);

    @Query("SELECT p FROM DailyPlan p WHERE p.userId IN :userIds AND p.planDate = :planDate")
    List<DailyPlan> findAllByUserIdsAndPlanDate(@Param("userIds") List<Long> userIds,
                                                @Param("planDate") LocalDate planDate);

    /** 행을 잠근 채 조회 (22시 작업 중복 실행 방지) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM DailyPlan p WHERE p.userId = :userId AND p.planDate = :planDate")
    Optional<DailyPlan> findForUpdate(@Param("userId") Long userId, @Param("planDate") LocalDate planDate);

    /**
     * (user_id, plan_date) 행이 없으면 만든다. 있으면 아무것도 안 함.
     * 두 서버가 동시에 실행하면 두 번째 INSERT 는 첫 번째 트랜잭션이 끝날 때까지 기다렸다가 0을 반환한다.
     */
    @Modifying
    @Query(value = """
        INSERT IGNORE INTO daily_plan (user_id, plan_date, source, status)
        VALUES (:userId, :planDate, :source, 'GENERATING')
    """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") Long userId, @Param("planDate") LocalDate planDate, @Param("source") String source);
}
