package com.naleul.naleul.domain.mission.entity;

import com.naleul.naleul.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * "쪼기" 이력. 하루에 (미션, 보낸사람, 받는사람) 조합으로 1회 제한을 걸기 위한 기록 테이블.
 */
@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "mission_poke")
public class MissionPoke {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long pokeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mission_id", nullable = false)
    private Mission mission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_user_id", nullable = false)
    private User targetUser;

    @Column(nullable = false)
    private LocalDate pokeDate;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static MissionPoke create(Mission mission, User sender, User targetUser, LocalDate pokeDate) {
        MissionPoke poke = new MissionPoke();
        poke.mission = mission;
        poke.sender = sender;
        poke.targetUser = targetUser;
        poke.pokeDate = pokeDate;
        poke.createdAt = LocalDateTime.now();
        return poke;
    }
}