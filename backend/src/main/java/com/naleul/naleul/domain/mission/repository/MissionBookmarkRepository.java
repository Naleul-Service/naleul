package com.naleul.naleul.domain.mission.repository;

import com.naleul.naleul.domain.mission.entity.MissionBookmark;
import com.naleul.naleul.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set; // ✅ 추가 — 이게 없어서 Set<Long> 반환 타입을 못 읽음

public interface MissionBookmarkRepository extends JpaRepository<MissionBookmark, Long> {

    boolean existsByMission_MissionIdAndUser_UserId(Long missionId, Long userId);

    Optional<MissionBookmark> findByMission_MissionIdAndUser_UserId(Long missionId, Long userId);

    @Query("""
            SELECT mb FROM MissionBookmark mb
            JOIN FETCH mb.mission m
            WHERE mb.user.userId = :userId
            """)
    List<MissionBookmark> findAllByUserId(@Param("userId") Long userId);

    @Query("""
        SELECT mb FROM MissionBookmark mb
        JOIN FETCH mb.mission m
        JOIN FETCH m.participants
        WHERE mb.user.userId = :userId
        """)
    List<MissionBookmark> findBookmarkedMissions(@Param("userId") Long userId);

    @Query("SELECT b.mission.missionId FROM MissionBookmark b " +
            "WHERE b.user.userId = :userId AND b.mission.missionId IN :missionIds")
    Set<Long> findBookmarkedMissionIds(@Param("userId") Long userId,
                                       @Param("missionIds") List<Long> missionIds);

    // MissionBookmarkRepository에 추가
    @Query("SELECT b.user FROM MissionBookmark b WHERE b.mission.missionId = :missionId")
    List<User> findUsersByMissionId(@Param("missionId") Long missionId);
}