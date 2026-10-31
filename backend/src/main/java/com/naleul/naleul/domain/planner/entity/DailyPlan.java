package com.naleul.naleul.domain.planner.entity;

import com.naleul.naleul.domain.planner.enums.DailyPlanSource;
import com.naleul.naleul.domain.planner.enums.DailyPlanStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 날짜별 TimeTable 생성 기록 (사용자, 날짜) UNIQUE.
 *
 * - 22시 작업은 이 행을 GENERATING 으로 먼저 INSERT 하고 시작한다.
 *   블루그린 배포로 두 서버가 동시에 돌면 두 번째 서버는 UNIQUE 충돌이 나서 그 사용자를 건너뛴다.
 * - 배너("내일의 TimeTable을 만들었어요")는 status = GENERATED 이고 reviewedAt 이 없을 때 띄운다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "daily_plan")
public class DailyPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long dailyPlanId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private LocalDate planDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DailyPlanSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DailyPlanStatus status;

    @Column(nullable = false)
    private int placedCount;

    @Column(nullable = false)
    private int carriedCount;

    @Column(nullable = false)
    private int unplacedCount;

    @Column(length = 300)
    private String summary;

    private LocalDateTime generatedAt;
    private LocalDateTime reviewedAt;

    public static DailyPlan start(Long userId, LocalDate planDate, DailyPlanSource source) {
        DailyPlan p = new DailyPlan();
        p.userId = userId;
        p.planDate = planDate;
        p.source = source;
        p.status = DailyPlanStatus.GENERATING;
        return p;
    }

    /** 이미 행이 있는 날짜를 다시 배치할 때 (Brain dump 후 22시 작업, 다시 배치 등) */
    public void restart(DailyPlanSource source) {
        this.source = source;
        this.status = DailyPlanStatus.GENERATING;
    }

    public void markGenerated(int placedCount, int carriedCount, int unplacedCount,
                              String summary, LocalDateTime now) {
        this.placedCount = placedCount;
        this.carriedCount = carriedCount;
        this.unplacedCount = unplacedCount;
        this.summary = summary;
        this.generatedAt = now;
        this.status = DailyPlanStatus.GENERATED;
        this.reviewedAt = null; // 새로 만들었으니 다시 확인 받는다
    }

    public void markReviewed(LocalDateTime now) {
        this.status = DailyPlanStatus.REVIEWED;
        this.reviewedAt = now;
    }

    public void markFailed() {
        this.status = DailyPlanStatus.FAILED;
    }

    public boolean needsReview() {
        return status == DailyPlanStatus.GENERATED && reviewedAt == null;
    }
}
