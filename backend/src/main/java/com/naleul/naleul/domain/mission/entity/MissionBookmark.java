package com.naleul.naleul.domain.mission.entity;

import com.naleul.naleul.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "mission_bookmarks",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"mission_id", "user_id"}  // 한 유저가 같은 미션 중복 찜 방지
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MissionBookmark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long missionBookmarkId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mission_id", nullable = false)
    private Mission mission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public static MissionBookmark create(Mission mission, User user) {
        MissionBookmark bookmark = new MissionBookmark();
        bookmark.mission = mission;
        bookmark.user = user;
        return bookmark;
    }
}