package com.naleul.naleul.domain.habit.repository;

import com.naleul.naleul.domain.habit.entity.HabitCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface HabitCheckRepository extends JpaRepository<HabitCheck, Long> {

    @Query("""
        SELECT c FROM HabitCheck c
        WHERE c.habit.habitId = :habitId AND c.checkDate = :date
    """)
    Optional<HabitCheck> findOne(@Param("habitId") Long habitId, @Param("date") LocalDate date);

    /** 여러 습관의 [from, to] 체크 — 잔디맵 · 목록용 */
    @Query("""
        SELECT c FROM HabitCheck c
        WHERE c.habit.habitId IN :habitIds
          AND c.checkDate BETWEEN :from AND :to
    """)
    List<HabitCheck> findBetween(@Param("habitIds") Collection<Long> habitIds,
                                 @Param("from") LocalDate from,
                                 @Param("to") LocalDate to);

    /** 오늘 이미 체크한 습관 id (알림 스케줄러용) */
    @Query("""
        SELECT c.habit.habitId FROM HabitCheck c
        WHERE c.habit.habitId IN :habitIds AND c.checkDate = :date
    """)
    List<Long> findCheckedHabitIds(@Param("habitIds") Collection<Long> habitIds, @Param("date") LocalDate date);

    /** 이미 있으면 아무것도 안 한다 (동시에 두 번 체크해도 1행) */
    @Modifying
    @Query(value = """
        INSERT IGNORE INTO habit_check (habit_id, user_id, check_date, created_at)
        VALUES (:habitId, :userId, :date, :createdAt)
    """, nativeQuery = true)
    int insertIgnore(@Param("habitId") Long habitId, @Param("userId") Long userId,
                     @Param("date") LocalDate date, @Param("createdAt") LocalDateTime createdAt);

    @Modifying
    @Query("DELETE FROM HabitCheck c WHERE c.habit.habitId = :habitId AND c.checkDate = :date")
    int deleteOne(@Param("habitId") Long habitId, @Param("date") LocalDate date);
}
