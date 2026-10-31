package com.naleul.naleul.domain.mission.repository;

import com.naleul.naleul.domain.mission.entity.MissionTaskLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MissionTaskLikeRepository extends JpaRepository<MissionTaskLike, Long> {

    // 이미 좋아요 눌렀는지 확인
    boolean existsByMissionTaskComplete_MissionTaskCompleteIdAndUser_UserId(
            Long missionTaskCompleteId, Long userId);

    // 좋아요 취소용
    Optional<MissionTaskLike> findByMissionTaskComplete_MissionTaskCompleteIdAndUser_UserId(
            Long missionTaskCompleteId, Long userId);

    // 좋아요 개수
    int countByMissionTaskComplete_MissionTaskCompleteId(Long missionTaskCompleteId);
}