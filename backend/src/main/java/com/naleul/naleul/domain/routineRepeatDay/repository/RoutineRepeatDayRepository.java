package com.naleul.naleul.domain.routineRepeatDay.repository;

import com.naleul.naleul.domain.routine.entity.Routine;
import com.naleul.naleul.domain.routineRepeatDay.entity.RoutineRepeatDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoutineRepeatDayRepository extends JpaRepository<RoutineRepeatDay, Long> {

    @Modifying(clearAutomatically = true)  // ✅ 영속성 컨텍스트 자동 초기화
    @Query("DELETE FROM RoutineRepeatDay rd WHERE rd.routine = :routine")
    void deleteAllByRoutine(@Param("routine") Routine routine);
}