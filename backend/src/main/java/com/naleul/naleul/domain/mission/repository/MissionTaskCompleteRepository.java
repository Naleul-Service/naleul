package com.naleul.naleul.domain.mission.repository;

import com.naleul.naleul.domain.mission.entity.MissionTaskComplete;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MissionTaskCompleteRepository extends JpaRepository<MissionTaskComplete, Long> {

    // 이미 완료 처리된 Task인지 체크
    boolean existsByTaskTaskId(Long taskId);

    // Task로 인증 기록 조회
    Optional<MissionTaskComplete> findByTaskTaskId(Long taskId);

    // ✅ 추가 — 특정 유저가 특정 날짜에 인증 기록이 있는지 (연속일 판정용)
    boolean existsByUser_UserIdAndCompleteDate(Long userId, LocalDate date);

    // ✅ 추가 — 유저의 전체 누적 인증 횟수 (미션 통틀어서)
    long countByUser_UserId(Long userId);

    @Query("""
        SELECT mtc FROM MissionTaskComplete mtc
        JOIN FETCH mtc.task t
        JOIN FETCH mtc.user u
        WHERE t.missionParticipant.mission.missionId = :missionId
        AND mtc.completeDate = :date
        """)
    List<MissionTaskComplete> findDailyCompletes(
            @Param("missionId") Long missionId,
            @Param("date") LocalDate date
    );

    // 월별 인증사진 조회
    @Query("""
        SELECT mtc FROM MissionTaskComplete mtc
        JOIN FETCH mtc.task t
        JOIN FETCH mtc.user u
        WHERE t.missionParticipant.mission.missionId = :missionId
        AND t.user.userId = :userId
        AND YEAR(mtc.completeDate) = :year
        AND MONTH(mtc.completeDate) = :month
        ORDER BY mtc.completeDate ASC
        """)
    List<MissionTaskComplete> findMonthlyCompletes(
            @Param("missionId") Long missionId,
            @Param("userId") Long userId,
            @Param("year") int year,
            @Param("month") int month
    );

    // 특정 미션 참여자의 인증 기록 전체 (날짜 오름차순)
    @Query("""
        SELECT mtc FROM MissionTaskComplete mtc
        JOIN FETCH mtc.task t
        WHERE t.missionParticipant.mission.missionId = :missionId
        AND t.user.userId = :userId
        ORDER BY mtc.completeDate ASC
        """)
    List<MissionTaskComplete> findAllByMissionAndUser(
            @Param("missionId") Long missionId,
            @Param("userId") Long userId
    );
}