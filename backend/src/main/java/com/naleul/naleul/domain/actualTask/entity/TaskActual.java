package com.naleul.naleul.domain.actualTask.entity;

import com.naleul.naleul.domain.generalCategory.entity.GeneralCategory;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;

// com/naleul/naleul/domain/task/entity/TaskActual.java
@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "task_actual")
public class TaskActual {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long taskActualId;

    // Task와 연결 (nullable — 독립 입력 시 null)
    @ManyToOne(fetch = FetchType.LAZY)          // OneToOne → ManyToOne
    @JoinColumn(name = "task_id", nullable = true)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_category_id", nullable = false)
    private GoalCategory goalCategory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "general_category_id", nullable = false)
    private GeneralCategory generalCategory;

    @Column(nullable = false)
    private String taskName;

    private LocalDateTime actualStartAt;
    private LocalDateTime actualEndAt;
    private Long actualDurationMinutes;

    public void update(LocalDateTime startAt, LocalDateTime endAt) {
        this.actualStartAt = startAt;
        this.actualEndAt = endAt;
        if (startAt != null && endAt != null) {
            this.actualDurationMinutes = Duration.between(startAt, endAt).toMinutes();
        }
    }

    public void updateAll(String taskName, GoalCategory goalCategory,
                          GeneralCategory generalCategory,
                          LocalDateTime startAt, LocalDateTime endAt) {
        this.taskName = taskName;
        this.goalCategory = goalCategory;
        this.generalCategory = generalCategory;
        this.actualStartAt = startAt;
        this.actualEndAt = endAt;
        if (startAt != null && endAt != null) {
            this.actualDurationMinutes = Duration.between(startAt, endAt).toMinutes();
        }
    }

    /** 여러 구간으로 나눠서 한 경우: 첫 시작 ~ 마지막 끝, 소요 시간은 구간 합계 (쉰 시간 제외) */
    public void updateSpan(LocalDateTime firstStart, LocalDateTime lastEnd, long workedMinutes) {
        this.actualStartAt = firstStart;
        this.actualEndAt = lastEnd;
        this.actualDurationMinutes = workedMinutes;
    }

    // ✅ 완료 취소 시 실제 수행 기록 초기화
    public void reset() {
        this.actualStartAt = null;
        this.actualEndAt = null;
        this.actualDurationMinutes = null;
    }
}