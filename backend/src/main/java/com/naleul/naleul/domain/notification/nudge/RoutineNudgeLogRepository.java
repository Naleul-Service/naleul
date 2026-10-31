package com.naleul.naleul.domain.notification.nudge;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface RoutineNudgeLogRepository extends JpaRepository<RoutineNudgeLog, Long> {

    boolean existsByTaskIdAndKind(Long taskId, RoutineNudgeKind kind);

    long countByUserIdAndSentDate(Long userId, LocalDate sentDate);

    List<RoutineNudgeLog> findAllByTaskIdIn(Collection<Long> taskIds);
}
