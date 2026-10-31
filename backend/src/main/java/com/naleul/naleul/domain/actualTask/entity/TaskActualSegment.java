package com.naleul.naleul.domain.actualTask.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 실제로 한 구간 하나 (V57) — 한 Task 를 나눠서 했을 때만 생긴다 (예: 13~15시, 16~18시).
 * task_actual 은 Task 당 한 줄(첫 시작 ~ 마지막 끝)로 유지해서 기존 집계가 Task 를 두 번 세지 않게 한다.
 */
@Entity
@Table(name = "task_actual_segment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskActualSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "task_actual_segment_id")
    private Long id;

    @Column(name = "task_actual_id", nullable = false)
    private Long taskActualId;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    public static TaskActualSegment of(Long taskActualId, LocalDateTime start, LocalDateTime end) {
        TaskActualSegment s = new TaskActualSegment();
        s.taskActualId = taskActualId;
        s.startAt = start;
        s.endAt = end;
        return s;
    }
}
