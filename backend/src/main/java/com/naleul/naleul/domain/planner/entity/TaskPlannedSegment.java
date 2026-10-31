package com.naleul.naleul.domain.planner.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 나눠 넣은 Task 의 계획 구간 하나 (V65).
 * 연속된 빈 시간이 없어 고정 시간 앞뒤로 나눠 배치했을 때만 생긴다 (한 번에 넣었으면 행이 없다).
 * Task 의 planned_start_at ~ planned_end_at 은 첫 구간 시작 ~ 마지막 구간 끝.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "task_planned_segment")
public class TaskPlannedSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long taskPlannedSegmentId;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(nullable = false)
    private LocalDateTime startAt;

    @Column(nullable = false)
    private LocalDateTime endAt;

    @Column(nullable = false)
    private int sortOrder;

    public static TaskPlannedSegment of(Long taskId, LocalDateTime start, LocalDateTime end, int sortOrder) {
        TaskPlannedSegment s = new TaskPlannedSegment();
        s.taskId = taskId;
        s.startAt = start;
        s.endAt = end;
        s.sortOrder = sortOrder;
        return s;
    }
}
