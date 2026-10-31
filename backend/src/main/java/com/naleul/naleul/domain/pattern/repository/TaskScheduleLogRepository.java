package com.naleul.naleul.domain.pattern.repository;

import com.naleul.naleul.domain.pattern.entity.TaskScheduleLog;
import com.naleul.naleul.domain.pattern.enums.TaskScheduleAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TaskScheduleLogRepository extends JpaRepository<TaskScheduleLog, Long> {

    /** 기간 안에 남은 기록 전체 (자주 옮기는 블록, AI 배치 수정률). 시간순 */
    @Query("""
        SELECT l FROM TaskScheduleLog l
        WHERE l.userId = :userId
          AND l.createdAt >= :from AND l.createdAt < :to
        ORDER BY l.createdAt ASC, l.taskScheduleLogId ASC
    """)
    List<TaskScheduleLog> findAllCreatedBetween(@Param("userId") Long userId,
                                                @Param("from") LocalDateTime from,
                                                @Param("to") LocalDateTime to);

    /**
     * 원래 날짜(from_date)가 기간 안인 특정 종류 기록. 이월(CARRY_OVER) 놓침 집계에 쓴다.
     * 이월은 22시에 일어나므로 created_at 이 아니라 "원래 그 Task 를 하려던 날"로 찾아야 한다.
     */
    @Query("""
        SELECT l FROM TaskScheduleLog l
        WHERE l.userId = :userId
          AND l.action = :action
          AND l.fromDate BETWEEN :startDate AND :endDate
        ORDER BY l.fromDate ASC, l.taskScheduleLogId ASC
    """)
    List<TaskScheduleLog> findAllByActionAndFromDateBetween(@Param("userId") Long userId,
                                                            @Param("action") TaskScheduleAction action,
                                                            @Param("startDate") LocalDate startDate,
                                                            @Param("endDate") LocalDate endDate);

    /**
     * 이 Task 들 중 사용자가 직접 옮긴 적이 있는 Task id.
     * 제안 반영(루틴 시간 변경) 때 "그날만 직접 옮긴 Task"는 건드리지 않기 위해 쓴다 (명세 4 · 4-3).
     */
    @Query("""
        SELECT DISTINCT l.taskId FROM TaskScheduleLog l
        WHERE l.taskId IN :taskIds
          AND l.action = com.naleul.naleul.domain.pattern.enums.TaskScheduleAction.USER_MOVE
    """)
    List<Long> findTaskIdsMovedByUser(@Param("taskIds") Collection<Long> taskIds);

    /** 이 Task 의 가장 최근 기록 (예: 마지막 이월 — 되돌릴 때 원래 시각을 찾는다) */
    Optional<TaskScheduleLog> findTopByTaskIdAndActionOrderByTaskScheduleLogIdDesc(
            Long taskId, com.naleul.naleul.domain.pattern.enums.TaskScheduleAction action);

    /** 이 Task 가 그날에서 옮겨 간 마지막 기록 (이월 흔적에서 그날로 되돌릴 때) */
    Optional<TaskScheduleLog> findTopByTaskIdAndActionAndFromDateOrderByTaskScheduleLogIdDesc(
            Long taskId, TaskScheduleAction action, LocalDate fromDate);
}
