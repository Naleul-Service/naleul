package com.naleul.naleul.domain.goalCreation.repository;

import com.naleul.naleul.domain.goalCreation.entity.GoalCreationSession;
import com.naleul.naleul.domain.goalCreation.enums.GoalCreationSessionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GoalCreationSessionRepository extends JpaRepository<GoalCreationSession, Long> {

    /** 소유권까지 한 번에 확인. 남의 세션이면 empty → 404 */
    Optional<GoalCreationSession> findByGoalCreationSessionIdAndUser_UserId(Long sessionId, Long userId);

    /**
     * 확정용: 소유권 확인 + 행 잠금 (SELECT ... FOR UPDATE).
     * "이 목표로 시작하기"를 두 번 빠르게 누르면 두 요청이 동시에 DRAFT_READY를 보고
     * 목표가 2개 만들어질 수 있다. 잠금을 걸면 두 번째 요청은 첫 번째가 커밋될 때까지 기다렸다가
     * CONFIRMED를 보고 409로 끝난다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from GoalCreationSession s join fetch s.user where s.goalCreationSessionId = :sessionId and s.user.userId = :userId")
    Optional<GoalCreationSession> findOwnedForUpdate(@Param("sessionId") Long sessionId, @Param("userId") Long userId);

    /** 이어하기: 진행 중인 가장 최근 세션 */
    Optional<GoalCreationSession> findFirstByUser_UserIdAndStatusInOrderByCreatedAtDesc(
            Long userId, Collection<GoalCreationSessionStatus> statuses);

    /** 일일 세션 생성 한도 체크 */
    long countByUser_UserIdAndCreatedAtAfter(Long userId, LocalDateTime from);

    /** 7일 만료 정리 스케줄러용 */
    List<GoalCreationSession> findAllByStatusInAndExpiresAtBefore(
            Collection<GoalCreationSessionStatus> statuses, LocalDateTime now);
}
