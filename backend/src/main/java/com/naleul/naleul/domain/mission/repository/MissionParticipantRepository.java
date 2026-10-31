package com.naleul.naleul.domain.mission.repository;

import com.naleul.naleul.domain.mission.entity.MissionParticipant;
import com.naleul.naleul.domain.mission.enums.MissionParticipantStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MissionParticipantRepository extends JpaRepository<MissionParticipant, Long> {

    // 특정 미션의 특정 상태 참여자 목록
    @Query("""
            SELECT mp FROM MissionParticipant mp
            JOIN FETCH mp.user
            WHERE mp.mission.missionId = :missionId
            AND mp.status = :status
            """)
    List<MissionParticipant> findByMissionIdAndStatus(
            @Param("missionId") Long missionId,
            @Param("status") MissionParticipantStatus status
    );

    // 단건 조회
    @Query("""
    SELECT mp FROM MissionParticipant mp
    JOIN FETCH mp.mission
    JOIN FETCH mp.user
    WHERE mp.missionParticipantId = :id
    """)
    Optional<MissionParticipant> findByMissionParticipantId(@Param("id") Long id);

    // 특정 미션의 특정 상태 참여자 수
    long countByMissionMissionIdAndStatus(
            Long missionId,
            MissionParticipantStatus status
    );

    // 내가 참여중인 미션 (ACTIVE 상태 + 미션이 실제로 IN_PROGRESS 이상 진행된 것만, 방장 제외, 삭제 제외)
    @Query("""
SELECT mp FROM MissionParticipant mp
JOIN FETCH mp.mission m
JOIN FETCH m.participants
WHERE mp.user.userId = :userId
AND mp.status = 'ACTIVE'
AND mp.mission.host.userId != :userId
AND m.missionStatus NOT IN ('RECRUITING', 'DELETED')
""")
    List<MissionParticipant> findParticipatingMissions(@Param("userId") Long userId);


    // 개인 달성 목록 (COMPLETED or FAILED, 삭제된 미션 제외)
    @Query("""
    SELECT mp FROM MissionParticipant mp
    JOIN FETCH mp.mission m
    WHERE mp.user.userId = :userId
    AND mp.status IN ('COMPLETED', 'FAILED')
    AND m.missionStatus != 'DELETED'
    """)
    List<MissionParticipant> findPersonalAchievements(@Param("userId") Long userId);

    // 팀 달성 목록 (삭제된 미션 제외)
    @Query("""
    SELECT mp FROM MissionParticipant mp
    JOIN FETCH mp.mission m
    JOIN FETCH m.participants p
    JOIN FETCH p.user
    WHERE mp.user.userId = :userId
    AND m.missionStatus = 'COMPLETED'
    AND m.missionStatus != 'DELETED'
    """)
    List<MissionParticipant> findTeamAchievements(@Param("userId") Long userId);

    // ACTIVE, COMPLETED, FAILED 모두 조회 (삭제된 미션 제외)
    @Query("""
    SELECT mp FROM MissionParticipant mp
    JOIN FETCH mp.user
    WHERE mp.mission.missionId = :missionId
    AND mp.status IN ('ACTIVE', 'COMPLETED', 'FAILED')
    AND mp.mission.missionStatus != 'DELETED'
    """)
    List<MissionParticipant> findActiveParticipantsByMissionId(@Param("missionId") Long missionId);

    // 내가 신청한 미션 (PENDING이거나, ACTIVE인데 아직 모집중이라 시작 전인 것)
// ✅ 방장 본인 제외 — 방장은 "신청"한 게 아니라 "관리"하는 것이므로 hosting 탭에만 나와야 함
    @Query("""
SELECT mp FROM MissionParticipant mp
JOIN FETCH mp.mission m
WHERE mp.user.userId = :userId
AND mp.mission.host.userId != :userId
AND (
    mp.status = 'PENDING'
    OR (mp.status = 'ACTIVE' AND m.missionStatus = 'RECRUITING')
)
AND m.missionStatus != 'DELETED'
""")
    List<MissionParticipant> findPendingMissions(@Param("userId") Long userId);

    // MissionParticipantRepository
    @Query("""
    SELECT mp FROM MissionParticipant mp
    JOIN FETCH mp.mission m
    WHERE mp.user.userId = :userId
    AND mp.status IN ('COMPLETED', 'FAILED')
    AND mp.resultConfirmed = false
    """)
    List<MissionParticipant> findUnconfirmedResults(@Param("userId") Long userId);

    Optional<MissionParticipant> findByMission_MissionIdAndUser_UserId(Long missionId, Long userId);

    // 유저가 현재 ACTIVE 상태로 참여 중인 미션 개수
    // ⚠️ DELETED 미션은 제외 — Mission.delete()가 참여자 상태를 안 바꾸기 때문에,
    //    이 필터가 없으면 삭제한 미션도 계속 한도를 차지하는 버그가 생김
    @Query("""
    SELECT COUNT(mp) FROM MissionParticipant mp
    WHERE mp.user.userId = :userId
    AND mp.status = 'ACTIVE'
    AND mp.mission.missionStatus != 'DELETED'
    """)
    long countActiveParticipations(@Param("userId") Long userId);

    // MissionParticipantRepository.java

    // 특정 미션의 여러 상태 참여자 목록 (PENDING + ACTIVE 등 복수 상태 조회용)
    @Query("""
        SELECT mp FROM MissionParticipant mp
        JOIN FETCH mp.user
        WHERE mp.mission.missionId = :missionId
        AND mp.status IN :statuses
        """)
    List<MissionParticipant> findByMissionIdAndStatusIn(
            @Param("missionId") Long missionId,
            @Param("statuses") List<MissionParticipantStatus> statuses
    );

    // ⭐ 추가 — 취소 처리 시 동시 요청으로부터 안전하게 잠금을 걸고 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT mp FROM MissionParticipant mp
        JOIN FETCH mp.user
        WHERE mp.mission.missionId = :missionId
        AND mp.user.userId = :userId
        """)
    Optional<MissionParticipant> findForCancelUpdate(
            @Param("missionId") Long missionId,
            @Param("userId") Long userId
    );
}