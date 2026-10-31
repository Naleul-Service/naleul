package com.naleul.naleul.domain.pattern.entity;

import com.naleul.naleul.domain.pattern.enums.TaskScheduleAction;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Task 일정 변경 기록 (insert 전용, 명세 4 · 6-1).
 *
 * 왜 필요한가: 사용자가 엔진 배치 블록을 드래그하면 task.placed_by 가 ENGINE → USER 로 덮어써지고
 * 원래 시각도 사라진다. 22시 이월도 같은 Task 의 날짜만 바꾸므로 "원래 날짜에 놓쳤다"는 사실이 남지 않는다.
 * 그래서 바뀌기 전/후 값을 이 테이블에 따로 남겨 AI 배치 수정률 · 자주 옮기는 블록 · 이월 놓침을 계산한다.
 *
 * task FK 를 일부러 두지 않았다 — Task 가 물리 삭제(루틴 수정 시 재생성 등)돼도 기록은 남아야 하기 때문.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "task_schedule_log")
public class TaskScheduleLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long taskScheduleLogId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    /** 루틴 Task 면 루틴 id (자주 옮기는 블록을 루틴 단위로 묶을 때 사용) */
    @Column(name = "routine_id")
    private Long routineId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskScheduleAction action;

    private LocalDate fromDate;
    private LocalDateTime fromStartAt;
    private LocalDate toDate;
    private LocalDateTime toStartAt;

    /** 한국 시간. JVM 시간대(EC2 UTC)에 의존하지 않도록 호출하는 쪽이 PlannerClock.now() 를 넘긴다 */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static TaskScheduleLog of(Long userId, Long taskId, Long routineId, TaskScheduleAction action,
                                     LocalDate fromDate, LocalDateTime fromStartAt,
                                     LocalDate toDate, LocalDateTime toStartAt,
                                     LocalDateTime now) {
        if (userId == null || taskId == null || action == null || now == null) {
            throw new IllegalArgumentException("userId, taskId, action, now 는 필수입니다");
        }
        TaskScheduleLog log = new TaskScheduleLog();
        log.userId = userId;
        log.taskId = taskId;
        log.routineId = routineId;
        log.action = action;
        // 시각만 넘어오고 날짜가 비어 있으면 시각의 날짜로 채운다 (집계 쿼리를 날짜 컬럼 하나로 하기 위해)
        log.fromStartAt = fromStartAt;
        log.fromDate = fromDate != null ? fromDate : (fromStartAt != null ? fromStartAt.toLocalDate() : null);
        log.toStartAt = toStartAt;
        log.toDate = toDate != null ? toDate : (toStartAt != null ? toStartAt.toLocalDate() : null);
        log.createdAt = now;
        return log;
    }

    /** 날짜가 바뀐 이동인지 (자주 옮기는 블록에서 "목 → 금" 표시용) */
    public boolean isDayChanged() {
        return fromDate != null && toDate != null && !fromDate.equals(toDate);
    }
}
