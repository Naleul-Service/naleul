package com.naleul.naleul.domain.mission.repository;

import com.naleul.naleul.domain.mission.entity.MissionPoke;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface MissionPokeRepository extends JpaRepository<MissionPoke, Long> {

    // 오늘 이미 이 조합(미션+보낸사람+받는사람)으로 쪼기를 했는지
    boolean existsByMission_MissionIdAndSender_UserIdAndTargetUser_UserIdAndPokeDate(
            Long missionId, Long senderId, Long targetUserId, LocalDate pokeDate);
}