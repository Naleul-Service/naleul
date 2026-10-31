package com.naleul.naleul.domain.planner.repository;

import com.naleul.naleul.domain.planner.entity.TaskPlannedSegment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface TaskPlannedSegmentRepository extends JpaRepository<TaskPlannedSegment, Long> {

    List<TaskPlannedSegment> findAllByTaskIdInOrderByTaskIdAscSortOrderAsc(Collection<Long> taskIds);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM TaskPlannedSegment s WHERE s.taskId = :taskId")
    void deleteAllOfTask(@Param("taskId") Long taskId);
}
