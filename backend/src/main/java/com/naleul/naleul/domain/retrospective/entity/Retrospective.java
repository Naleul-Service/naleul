// retrospective/entity/Retrospective.java
package com.naleul.naleul.domain.retrospective.entity;

import com.naleul.naleul.domain.generalCategory.entity.GeneralCategory;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.retrospective.enums.ReviewType;
import com.naleul.naleul.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "retrospective")
public class Retrospective {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long retrospectiveId;

    // FK - User (N:1)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // ✅ nullable = true → false 로 변경 (이제 회고는 반드시 목표에 속함)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_category_id", nullable = false)
    private GoalCategory goalCategory;

    // generalCategory는 여전히 선택값으로 유지 (세부목표 지정은 선택사항인 채로 둠)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "general_category_id")
    private GeneralCategory generalCategory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewType reviewType;

    @Column(nullable = false)
    private LocalDate reviewDate;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean isDeleted;

    public void update(String content, GoalCategory goalCategory, GeneralCategory generalCategory) {
        this.content = content;
        this.goalCategory = goalCategory;
        this.generalCategory = generalCategory;
    }

    public void delete() {
        this.isDeleted = true;
    }
}