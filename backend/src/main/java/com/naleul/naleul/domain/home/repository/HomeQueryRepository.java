package com.naleul.naleul.domain.home.repository;

import com.naleul.naleul.domain.task.entity.Task;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 홈 "오늘" 조회 전용 (읽기만) */
public interface HomeQueryRepository extends Repository<Task, Long> {

    /**
     * 그날과 닿는 Task — 그날 시작으로 잡힌 것 · 그날 시간 미정인 것 · 그날 끝낸 것.
     * 지운 것 · 건너뛴 것은 뺀다. 목표별 "오늘 할 일 · 끝낸 일" 집계용.
     */
    @Query("""
        SELECT t FROM Task t
        LEFT JOIN FETCH t.goalCategory g
        WHERE t.user.userId = :userId
          AND t.taskStatus <> com.naleul.naleul.domain.task.enums.TaskStatus.DELETED
          AND t.taskStatus <> com.naleul.naleul.domain.task.enums.TaskStatus.SKIPPED
          AND ( (t.plannedStartAt >= :from AND t.plannedStartAt < :to)
             OR (t.plannedStartAt IS NULL AND t.routineDate = :day)
             OR (t.completedAt >= :from AND t.completedAt < :to) )
        ORDER BY t.plannedStartAt ASC, t.taskId ASC
    """)
    List<Task> findTouchingDay(@Param("userId") Long userId,
                               @Param("from") LocalDateTime from,
                               @Param("to") LocalDateTime to,
                               @Param("day") LocalDate day);
}
