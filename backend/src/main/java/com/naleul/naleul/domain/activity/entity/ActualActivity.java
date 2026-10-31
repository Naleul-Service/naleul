package com.naleul.naleul.domain.activity.entity;

import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 실제로 한 일 (계획과 상관없이 그 시간에 실제로 한 활동).
 * 예) 19:00~20:30 계획은 "영어 공부"였지만 실제로는 "친구 만남"
 *
 * Task 와 따로 둔 이유
 *  - Task 는 계획·자동 배치·실행률(완료/전체)·이월의 대상이다. 실제 기록을 Task(완료)로 만들면
 *    실행률이 부풀고, 자동 배치가 빈 시간 계산에 섞이고, 목표 진행에도 잘못 잡힌다.
 *  - "계획 vs 실제"를 비교하려면 두 기록이 서로 독립적이어야 한다.
 *
 * 시각은 task.planned_start_at 과 같이 한국 시간 벽시계 값.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "actual_activity")
public class ActualActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long actualActivityId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 60)
    private String title;

    @Column(length = 16)
    private String emoji;

    @Column(nullable = false)
    private LocalDateTime startAt;

    @Column(nullable = false)
    private LocalDateTime endAt;

    /** 연결한 목표 (선택) — 목표에 쓴 실제 시간 통계용 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_category_id")
    private GoalCategory goalCategory;

    /**
     * 이 시간에 원래 계획했던 Task (선택). "계획: 영어 공부 → 실제: 친구 만남"
     * Task 는 목표 삭제 등으로 지워질 수 있어 FK 없이 id + 이름 스냅샷으로 둔다.
     */
    private Long replacedTaskId;

    @Column(length = 100)
    private String replacedTaskName;

    @Column(length = 200)
    private String memo;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static ActualActivity create(User user, String title, String emoji, LocalDateTime startAt, LocalDateTime endAt,
                                        GoalCategory goal, Long replacedTaskId, String replacedTaskName, String memo) {
        ActualActivity a = new ActualActivity();
        a.user = user;
        a.apply(title, emoji, startAt, endAt, goal, replacedTaskId, replacedTaskName, memo);
        return a;
    }

    public void apply(String title, String emoji, LocalDateTime startAt, LocalDateTime endAt,
                      GoalCategory goal, Long replacedTaskId, String replacedTaskName, String memo) {
        this.title = title.strip();
        this.emoji = blankToNull(emoji);
        this.startAt = startAt;
        this.endAt = endAt;
        this.goalCategory = goal;
        this.replacedTaskId = replacedTaskId;
        this.replacedTaskName = replacedTaskName;
        this.memo = blankToNull(memo);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
