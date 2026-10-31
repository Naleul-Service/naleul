package com.naleul.naleul.domain.work.entity;

import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 업무형 목표 안의 주제 (V52). 예) "취업 준비" → 포트폴리오 · 이력서 제출 · 코딩 테스트 · 면접
 * 프로젝트마다 그때그때 다른 묶음을 사용자가 직접 만든다. Task 는 주제 없이도 만들 수 있다.
 *
 * V53: 업무형은 목표 전체 마감이 없고, 주제마다 마감(dueDate)을 둔다.
 * 상세 화면은 주제를 마감 순서대로 세로 타임라인(로드맵)으로 보여준다.
 * 완료: 사용자가 직접 끝냈다고 표시했거나(completedAt), 주제의 Task 가 모두 끝났을 때.
 *
 * 세부 목표(general_category)를 쓰지 않는 이유: 세부 목표는 기간 · 수치 · 상태가 있는 "목표형"의 구조라
 * 단순한 묶음 이름에는 무겁고, 루틴 · Task 의 필수 그릇("기타 할 일")으로도 쓰이고 있어 의미가 섞인다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "work_topic")
public class WorkTopic {

    public static final int NAME_MAX = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long workTopicId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_category_id", nullable = false)
    private GoalCategory goalCategory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = NAME_MAX)
    private String topicName;

    @Column(length = 16)
    private String emoji;

    @Column(nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean isDeleted = false;

    /** 이 주제의 마감 (V53, 선택) */
    private LocalDate dueDate;

    /** 사용자가 "끝냈어요"로 표시한 시각 (V53) — Task 가 없는 주제(예: 이력서 제출)도 끝낼 수 있게 */
    private LocalDateTime completedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static WorkTopic create(GoalCategory goal, String name, String emoji, LocalDate dueDate, int sortOrder) {
        WorkTopic t = new WorkTopic();
        t.goalCategory = goal;
        t.user = goal.getUser();
        t.topicName = name.strip();
        t.emoji = blankToNull(emoji);
        t.dueDate = dueDate;
        t.sortOrder = sortOrder;
        return t;
    }

    /** null 이면 그대로. clearDueDate 면 마감을 지운다 */
    public void update(String name, String emoji, LocalDate dueDate, boolean clearDueDate) {
        if (name != null && !name.isBlank()) this.topicName = name.strip();
        if (emoji != null) this.emoji = blankToNull(emoji);
        if (clearDueDate) this.dueDate = null;
        else if (dueDate != null) this.dueDate = dueDate;
    }

    /** 끝냈어요 / 다시 열기 */
    public void markCompleted(boolean completed, LocalDateTime now) {
        this.completedAt = completed ? (completedAt != null ? completedAt : now) : null;
    }

    public boolean isMarkedCompleted() {
        return completedAt != null;
    }

    public void changeSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public void delete() {
        this.isDeleted = true;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
