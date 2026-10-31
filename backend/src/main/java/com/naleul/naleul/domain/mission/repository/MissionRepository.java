package com.naleul.naleul.domain.mission.repository;

import com.naleul.naleul.domain.mission.entity.Mission;
import com.naleul.naleul.domain.mission.entity.MissionParticipant;
import com.naleul.naleul.domain.mission.enums.MissionParticipantStatus;
import com.naleul.naleul.domain.mission.enums.MissionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MissionRepository extends JpaRepository<Mission, Long> {

    // ✅ 수정 — 실제 필드명 'missionStatus'로 변경
    List<Mission> findByMissionStatusAndIsPrivate(MissionStatus missionStatus, Boolean isPrivate);

    // 모집중이고 모집 종료일이 지난 미션 조회
    List<Mission> findByMissionStatusAndRecruitEndDateBefore(
            MissionStatus status,
            LocalDate date
    );
    // MissionRepository.java 쿼리 수정

    @Query("""
        SELECT m FROM Mission m
        WHERE m.missionStatus = :status
        AND m.missionStatus != 'DELETED'
        AND (:isPrivate IS NULL OR m.isPrivate = :isPrivate)
        AND (:keyword IS NULL OR m.missionTitle LIKE %:keyword%)
        """)
    List<Mission> searchMissions(
            @Param("status") MissionStatus status,
            @Param("isPrivate") Boolean isPrivate,
            @Param("keyword") String keyword
    );

    // MissionRepository.java에 추가
    @Query("""
        SELECT DISTINCT m FROM Mission m
        LEFT JOIN FETCH m.participants p
        LEFT JOIN FETCH p.user
        LEFT JOIN FETCH m.repeatDays
        WHERE m.missionId = :missionId
        """)
    Optional<Mission> findDetailById(@Param("missionId") Long missionId);

    // 내가 방장인 미션 (삭제된 미션 제외)
    @Query("""
    SELECT m FROM Mission m
    JOIN FETCH m.participants
    WHERE m.host.userId = :userId
    AND m.missionStatus != 'DELETED'
    AND m.missionStatus != 'COMPLETED'
    """)
    List<Mission> findHostingMissions(@Param("userId") Long userId);

    List<Mission> findByMissionStatus(MissionStatus missionStatus);

    // MissionRepository에 추가
    List<Mission> findByMissionStatusAndRecruitStartDate(MissionStatus status, LocalDate recruitStartDate);
    List<Mission> findByMissionStatusAndRecruitEndDate(MissionStatus status, LocalDate recruitEndDate);
}