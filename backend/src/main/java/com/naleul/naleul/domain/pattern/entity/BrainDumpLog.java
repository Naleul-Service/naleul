package com.naleul.naleul.domain.pattern.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Brain dump 확정 1회 = 1행 (insert 전용, 명세 4 · 6-1).
 * "brain dump 주 2.3회 · 한 번 평균 4.6개 · 주로 일요일 밤"을 계산하는 재료.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "brain_dump_log")
public class BrainDumpLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long brainDumpLogId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private int itemCount;

    @Column(nullable = false)
    private int tempGoalCount;

    /** 한국 시간 */
    @Column(nullable = false, updatable = false)
    private LocalDateTime confirmedAt;

    public static BrainDumpLog of(Long userId, int itemCount, int tempGoalCount, LocalDateTime confirmedAt) {
        if (userId == null || confirmedAt == null) {
            throw new IllegalArgumentException("userId, confirmedAt 은 필수입니다");
        }
        BrainDumpLog log = new BrainDumpLog();
        log.userId = userId;
        log.itemCount = Math.max(itemCount, 0);
        log.tempGoalCount = Math.max(tempGoalCount, 0);
        log.confirmedAt = confirmedAt;
        return log;
    }
}
