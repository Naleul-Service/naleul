package com.naleul.naleul.domain.activity.repository;

import com.naleul.naleul.domain.activity.entity.ActualActivity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ActualActivityRepository extends JpaRepository<ActualActivity, Long> {

    /** [from, to) 구간과 조금이라도 겹치는 기록 (자정을 넘긴 기록도 포함) */
    @Query("""
        SELECT a FROM ActualActivity a
        LEFT JOIN FETCH a.goalCategory g
        LEFT JOIN FETCH g.userColor
        WHERE a.user.userId = :userId
          AND a.startAt < :to
          AND a.endAt > :from
        ORDER BY a.startAt ASC
    """)
    List<ActualActivity> findOverlapping(@Param("userId") Long userId,
                                         @Param("from") LocalDateTime from,
                                         @Param("to") LocalDateTime to);

    @Query("""
        SELECT a FROM ActualActivity a
        WHERE a.actualActivityId = :id AND a.user.userId = :userId
    """)
    Optional<ActualActivity> findOwned(@Param("id") Long id, @Param("userId") Long userId);

    /** 목표에 쌓인 기록, 최근 것부터 (기록형 목표 상세의 "한 일" 타임라인). before 이전 것만 */
    @Query("""
        SELECT a FROM ActualActivity a
        WHERE a.user.userId = :userId
          AND a.goalCategory.goalCategoryId = :goalCategoryId
          AND a.startAt < :before
        ORDER BY a.startAt DESC
    """)
    List<ActualActivity> findRecentOfGoal(@Param("userId") Long userId,
                                          @Param("goalCategoryId") Long goalCategoryId,
                                          @Param("before") LocalDateTime before,
                                          Pageable pageable);

    /** 목표에 남긴 기록 중 [from, to) 에 시작한 것 (업무 일지) */
    @Query("""
        SELECT a FROM ActualActivity a
        WHERE a.user.userId = :userId
          AND a.goalCategory.goalCategoryId = :goalCategoryId
          AND a.startAt >= :from
          AND a.startAt < :to
        ORDER BY a.startAt ASC
    """)
    List<ActualActivity> findOfGoalBetween(@Param("userId") Long userId,
                                           @Param("goalCategoryId") Long goalCategoryId,
                                           @Param("from") LocalDateTime from,
                                           @Param("to") LocalDateTime to);

    /** 업무 일지 "더 보기": before 이전에 남긴 기록이 있는지 */
    boolean existsByUserUserIdAndGoalCategoryGoalCategoryIdAndStartAtBefore(Long userId, Long goalCategoryId,
                                                                           LocalDateTime before);
}
