package com.naleul.naleul.domain.actualTask.repository;

import com.naleul.naleul.domain.actualTask.entity.TaskActualSegment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface TaskActualSegmentRepository extends JpaRepository<TaskActualSegment, Long> {

    @Modifying
    @Query("DELETE FROM TaskActualSegment s WHERE s.taskActualId = :taskActualId")
    int deleteAllByTaskActualId(@Param("taskActualId") Long taskActualId);

    /** [taskId, 시작, 끝] — Task 별 구간 (시작 순) */
    @Query("""
        SELECT ta.task.taskId, s.startAt, s.endAt
        FROM TaskActualSegment s, TaskActual ta
        WHERE s.taskActualId = ta.taskActualId
          AND ta.task.taskId IN :taskIds
        ORDER BY ta.task.taskId, s.startAt
    """)
    List<Object[]> findByTaskIds(@Param("taskIds") Collection<Long> taskIds);
}
