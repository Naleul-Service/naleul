package com.naleul.naleul.domain.goalCreation.repository;

import com.naleul.naleul.domain.goalCreation.entity.GoalDraft;
import com.naleul.naleul.domain.goalCreation.enums.GoalDraftStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface GoalDraftRepository extends JpaRepository<GoalDraft, Long> {

    Optional<GoalDraft> findByGoalDraftIdAndSession_GoalCreationSessionId(Long draftId, Long sessionId);

    /** 다음 version 계산 + 확정 시 "최신 초안인지" 확인 */
    Optional<GoalDraft> findTopBySession_GoalCreationSessionIdOrderByVersionDesc(Long sessionId);

    /** V68: 세션의 모든 초안 (요청 종류 집계용) */
    List<GoalDraft> findAllBySession_GoalCreationSessionIdOrderByVersionAsc(Long sessionId);

    /** 재생성 한도(세션당 5회) */
    long countBySession_GoalCreationSessionId(Long sessionId);

    /** 서버 재시작 등으로 GENERATING에 멈춘 좀비 초안 정리 */
    List<GoalDraft> findAllByStatusAndUpdatedAtBefore(GoalDraftStatus status, LocalDateTime threshold);

    /** 사용자별 일일 초안 생성 한도 */
    long countBySession_User_UserIdAndCreatedAtAfter(Long userId, LocalDateTime from);

    /**
     * 상태를 바꾸기 전에 행을 잠그고 조회 (SELECT ... FOR UPDATE).
     * 비동기 작업의 "완료 처리"와 폴링의 "좀비 → 실패 처리"가 동시에 일어나도
     * 한쪽이 끝난 뒤에 다른 쪽이 최신 상태를 보고 판단하게 하기 위함.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from GoalDraft d join fetch d.session where d.goalDraftId = :draftId")
    Optional<GoalDraft> findForUpdate(@Param("draftId") Long draftId);
}
