package com.naleul.naleul.domain.ideaBoard.entity;

import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.ideaBoard.enums.IdeaStatus;
import com.naleul.naleul.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Table(name = "idea_board")
public class IdeaBoard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long ideaBoardId;

    // ── FK ───────────────────────────────────────────────
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // ⭐ 추가
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_category_id", nullable = false)
    private GoalCategory goalCategory;

    // ── 컬럼 ─────────────────────────────────────────────
    @Column(nullable = false, columnDefinition = "TEXT")
    private String idea;

    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IdeaStatus ideaStatus;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // ── 빌더 ─────────────────────────────────────────────
    @Builder
    public IdeaBoard(User user, GoalCategory goalCategory, String idea, String imageUrl, IdeaStatus ideaStatus) {
        this.user = user;
        this.goalCategory = goalCategory;
        this.idea = idea;
        this.imageUrl = imageUrl;
        this.ideaStatus = ideaStatus;
    }

    // ── 도메인 메서드 ──────────────────────────────────────
    public void updateStatus(IdeaStatus ideaStatus) {
        this.ideaStatus = ideaStatus;
    }

    public void update(String idea, String imageUrl, IdeaStatus ideaStatus) {
        if (idea != null) this.idea = idea;
        if (imageUrl != null) this.imageUrl = imageUrl;
        if (ideaStatus != null) this.ideaStatus = ideaStatus;
    }
}