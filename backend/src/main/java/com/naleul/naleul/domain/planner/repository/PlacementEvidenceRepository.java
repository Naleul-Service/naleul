package com.naleul.naleul.domain.planner.repository;

import com.naleul.naleul.domain.task.entity.Task;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 배치 이유를 "내 기록"으로 설명하기 위한 원자료 조회 (PlacementContextLoader.evidence).
 * PlacementQueryRepository 와 나눈 이유: 그쪽은 테스트에서 익명 구현으로 갈아 끼우고 있어, 메서드를 늘리면 테스트가 모두 깨진다.
 */
public interface PlacementEvidenceRepository extends Repository<Task, Long> {

    /** [from, to) 에 계획됐던 Task: [계획 시작, 상태, 목표 id, 목표 이름] (미션·삭제 제외) */
    @Query("""
        SELECT t.plannedStartAt, t.taskStatus, g.goalCategoryId, g.goalCategoryName FROM Task t
        LEFT JOIN t.goalCategory g
        WHERE t.user.userId = :userId
          AND t.plannedStartAt >= :from AND t.plannedStartAt < :to
          AND t.taskStatus <> com.naleul.naleul.domain.task.enums.TaskStatus.DELETED
          AND t.taskStatus <> com.naleul.naleul.domain.task.enums.TaskStatus.SKIPPED
          AND t.missionParticipant IS NULL
    """)
    List<Object[]> findSamplesWithGoal(@Param("userId") Long userId,
                                       @Param("from") LocalDateTime from,
                                       @Param("to") LocalDateTime to);
}
