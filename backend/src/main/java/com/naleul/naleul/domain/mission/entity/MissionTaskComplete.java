package com.naleul.naleul.domain.mission.entity;

import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "mission_task_completes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MissionTaskComplete {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long missionTaskCompleteId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String imageUrl; // S3 URL

    @Column(length = 500)
    private String comment; // 팀원들에게 한마디

    @Column(nullable = false)
    private LocalDate completeDate; // 완료 날짜

    @OneToMany(mappedBy = "missionTaskComplete", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MissionTaskLike> likes = new ArrayList<>();

    public int getLikeCount() {
        return this.likes.size();
    }

    public static MissionTaskComplete create(Task task, User user,
                                             String imageUrl, String comment) {
        MissionTaskComplete complete = new MissionTaskComplete();
        complete.task = task;
        complete.user = user;
        complete.imageUrl = imageUrl;
        complete.comment = comment;
        complete.completeDate = LocalDate.now();
        return complete;
    }
}