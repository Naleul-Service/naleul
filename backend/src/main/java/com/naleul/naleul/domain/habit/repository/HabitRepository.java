package com.naleul.naleul.domain.habit.repository;

import com.naleul.naleul.domain.habit.entity.Habit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface HabitRepository extends JpaRepository<Habit, Long> {

    /** 내 습관 (지운 것 제외) — 화면 순서대로 */
    @Query("""
        SELECT h FROM Habit h
        WHERE h.user.userId = :userId
          AND h.habitStatus <> com.naleul.naleul.domain.habit.enums.HabitStatus.DELETED
        ORDER BY h.sortOrder ASC, h.habitId ASC
    """)
    List<Habit> findAllOfUser(@Param("userId") Long userId);

    @Query("""
        SELECT h FROM Habit h
        WHERE h.habitId = :habitId
          AND h.user.userId = :userId
          AND h.habitStatus <> com.naleul.naleul.domain.habit.enums.HabitStatus.DELETED
    """)
    Optional<Habit> findOwned(@Param("habitId") Long habitId, @Param("userId") Long userId);

    @Query("""
        SELECT COALESCE(MAX(h.sortOrder), -1) FROM Habit h
        WHERE h.user.userId = :userId
          AND h.habitStatus <> com.naleul.naleul.domain.habit.enums.HabitStatus.DELETED
    """)
    int findMaxSortOrder(@Param("userId") Long userId);

    @Query("""
        SELECT COUNT(h) FROM Habit h
        WHERE h.user.userId = :userId
          AND h.habitStatus <> com.naleul.naleul.domain.habit.enums.HabitStatus.DELETED
    """)
    long countOfUser(@Param("userId") Long userId);

    /** 이 시각에 알림을 받는 체크 중인 습관 (요일 · 오늘 체크 여부는 스케줄러에서 거른다) */
    @Query("""
        SELECT h FROM Habit h
        JOIN FETCH h.user
        WHERE h.remindTime = :time
          AND h.habitStatus = com.naleul.naleul.domain.habit.enums.HabitStatus.ACTIVE
    """)
    List<Habit> findToRemindAt(@Param("time") LocalTime time);
}
