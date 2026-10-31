package com.naleul.naleul.domain.notification.nudge;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 루틴 실천 알림 발송 기록 (2026-10) — 같은 Task 에 같은 알림을 두 번 보내지 않고, 하루 개수를 제한하려고 남긴다.
 */
@Entity
@Table(name = "routine_nudge_log",
        uniqueConstraints = @UniqueConstraint(name = "uk_routine_nudge_task_kind", columnNames = {"task_id", "kind"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoutineNudgeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RoutineNudgeKind kind;

    @Column(name = "sent_date", nullable = false)
    private LocalDate sentDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static RoutineNudgeLog of(Long userId, Long taskId, RoutineNudgeKind kind, LocalDateTime now) {
        RoutineNudgeLog log = new RoutineNudgeLog();
        log.userId = userId;
        log.taskId = taskId;
        log.kind = kind;
        log.sentDate = now.toLocalDate();
        log.createdAt = now;
        return log;
    }
}
