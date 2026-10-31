package com.naleul.naleul.domain.habit.repository;

import com.naleul.naleul.domain.habit.entity.HabitPause;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface HabitPauseRepository extends JpaRepository<HabitPause, Long> {

    @Query("SELECT p FROM HabitPause p WHERE p.habit.habitId IN :habitIds")
    List<HabitPause> findAllOf(@Param("habitIds") Collection<Long> habitIds);

    /** 아직 닫히지 않은(쉬는 중) 기간 */
    @Query("SELECT p FROM HabitPause p WHERE p.habit.habitId = :habitId AND p.endDate IS NULL")
    Optional<HabitPause> findOpen(@Param("habitId") Long habitId);
}
