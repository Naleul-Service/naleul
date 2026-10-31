package com.naleul.naleul.domain.mission.entity;

import com.naleul.naleul.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "mission_task_likes",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"mission_task_complete_id", "user_id"}  // 한 유저가 같은 인증에 중복 좋아요 방지
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MissionTaskLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long missionTaskLikeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mission_task_complete_id", nullable = false)
    private MissionTaskComplete missionTaskComplete;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public static MissionTaskLike create(MissionTaskComplete complete, User user) {
        MissionTaskLike like = new MissionTaskLike();
        like.missionTaskComplete = complete;
        like.user = user;
        return like;
    }
}