package com.naleul.naleul.domain.task.entity;

import com.naleul.naleul.domain.task.enums.PlacedBy;
import com.naleul.naleul.domain.task.enums.TaskKind;
import com.naleul.naleul.domain.task.enums.TaskPriority;
import com.naleul.naleul.domain.task.enums.TaskSource;
import com.naleul.naleul.domain.task.enums.TaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskSchedulingTest {

    private static final LocalDate D = LocalDate.of(2026, 11, 18);

    private Task unplacedTask() {
        return Task.createOneTimeFromAi(null, null, null, null, "토익 LC 모의고사", "📘", 60, D, D.plusDays(1));
    }

    @Test
    @DisplayName("엔진 배치: ENGINE + 이유 저장, 잠기지 않음")
    void placeByEngine() {
        Task t = unplacedTask();
        t.placeByEngine(D.atTime(21, 30), D.atTime(22, 30), "마감이 1일 남아서 21:30에 배치했어요.", "DEADLINE_SOON");
        assertThat(t.isPlaced()).isTrue();
        assertThat(t.getPlacedBy()).isEqualTo(PlacedBy.ENGINE);
        assertThat(t.isScheduleLocked()).isFalse();
        assertThat(t.getPlacementReason()).contains("21:30");
        assertThat(t.getPlannedDurationMinutes()).isEqualTo(60L);
        assertThat(t.getRoutineDate()).isEqualTo(D);
    }

    @Test
    @DisplayName("사용자가 옮기면 잠기고 이유가 지워지며, 잠긴 Task는 엔진이 옮길 수 없다")
    void userScheduleLocks() {
        Task t = unplacedTask();
        t.placeByEngine(D.atTime(21, 0), D.atTime(22, 0), "r", "C");
        t.scheduleByUser(D.plusDays(1).atTime(9, 0), D.plusDays(1).atTime(10, 0));

        assertThat(t.isScheduleLocked()).isTrue();
        assertThat(t.getPlacedBy()).isEqualTo(PlacedBy.USER);
        assertThat(t.getPlacementReason()).isNull();
        assertThat(t.getRoutineDate()).isEqualTo(D.plusDays(1)); // 일회성 Task 는 날짜도 따라감
        assertThatThrownBy(() -> t.placeByEngine(D.atTime(8, 0), D.atTime(9, 0), "r", "C"))
                .isInstanceOf(IllegalStateException.class);

        t.unlock();
        t.placeByEngine(D.atTime(8, 0), D.atTime(9, 0), "r", "C");
        assertThat(t.getPlacedBy()).isEqualTo(PlacedBy.ENGINE);
    }

    @Test
    @DisplayName("unplace: 시간만 비우고 날짜·소요 시간은 유지")
    void unplaceKeepsDuration() {
        Task t = unplacedTask();
        t.placeByEngine(D.atTime(19, 0), D.atTime(19, 50), "r", "C");
        t.unplace();
        assertThat(t.isPlaced()).isFalse();
        assertThat(t.getPlannedDurationMinutes()).isEqualTo(50L);
        assertThat(t.getRoutineDate()).isEqualTo(D);
        assertThat(t.getPlacedBy()).isNull();
    }

    @Test
    @DisplayName("이월: 다음 날로 옮기고 이월 횟수 증가, 잠금 해제")
    void carryOver() {
        Task t = unplacedTask();
        t.scheduleByUser(D.atTime(20, 0), D.atTime(21, 0));
        t.carryOverTo(D.plusDays(1));
        assertThat(t.getRoutineDate()).isEqualTo(D.plusDays(1));
        assertThat(t.getScheduledDate()).isEqualTo(D.plusDays(1));
        assertThat(t.getCarryOverCount()).isEqualTo(1);
        assertThat(t.isScheduleLocked()).isFalse();
        assertThat(t.isPlaced()).isFalse();
        assertThat(t.durationMinutesOr(30)).isEqualTo(60);
    }

    @Test
    @DisplayName("완료하면 completedAt 기록, 완료 취소하면 지움")
    void completedAt() {
        Task t = unplacedTask();
        t.complete();
        assertThat(t.getTaskStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(t.getCompletedAt()).isNotNull();
        t.cancelComplete();
        assertThat(t.getCompletedAt()).isNull();
    }

    @Test
    @DisplayName("기존 update()로 시간을 넣으면 잠금, 시간을 지우면 잠금 해제")
    void legacyUpdateLocks() {
        Task t = Task.create(null, null, null, "회의", TaskPriority.B, null, null);
        assertThat(t.isScheduleLocked()).isFalse();
        LocalDateTime s = D.atTime(15, 0);
        t.update("회의", TaskPriority.B, null, null, s, s.plusHours(1), false, false);
        assertThat(t.isScheduleLocked()).isTrue();
        t.update("회의", TaskPriority.B, null, null, null, null, false, false);
        assertThat(t.isScheduleLocked()).isFalse();
        assertThat(t.getPlacedBy()).isNull();
    }

    @Test
    @DisplayName("빌더로 시간을 넣어 저장하면(@PrePersist) 사용자 시간으로 잠금 + 기본값 채움")
    void prePersistLocksBuilderTasks() {
        Task t = Task.builder()
                .taskName("직접 추가").taskPriority(TaskPriority.C).taskStatus(TaskStatus.TODO)
                .plannedStartAt(D.atTime(10, 0)).plannedEndAt(D.atTime(11, 0))
                .build();
        t.onCreate();
        assertThat(t.isScheduleLocked()).isTrue();
        assertThat(t.getPlacedBy()).isEqualTo(PlacedBy.USER);
        assertThat(t.getCreatedAt()).isNotNull();
        assertThat(t.getTaskKind()).isEqualTo(TaskKind.NORMAL);
        assertThat(t.getSource()).isEqualTo(TaskSource.USER_INPUT);
    }

    @Test
    @DisplayName("엔진이 배치한 Task는 저장 시 잠기지 않는다")
    void prePersistKeepsEnginePlacement() {
        Task t = unplacedTask();
        t.placeByEngine(D.atTime(9, 0), D.atTime(10, 0), "r", "C");
        t.onCreate();
        assertThat(t.isScheduleLocked()).isFalse();
        assertThat(t.getPlacedBy()).isEqualTo(PlacedBy.ENGINE);
    }

    @Test
    @DisplayName("자동 생성 Task 는 기본 우선순위 C 로 시작하고, changePriority 는 시간을 건드리지 않는다")
    void defaultPriorityAndChange() {
        Task t = unplacedTask();
        assertThat(t.getTaskPriority()).isEqualTo(TaskPriority.C);

        t.placeByEngine(D.atTime(9, 0), D.atTime(10, 0), "r", "C");
        t.changePriority(TaskPriority.A);
        assertThat(t.getTaskPriority()).isEqualTo(TaskPriority.A);
        assertThat(t.getPlannedStartAt()).isEqualTo(D.atTime(9, 0));
        assertThat(t.getPlacedBy()).isEqualTo(PlacedBy.ENGINE);

        t.changePriority(null);
        assertThat(t.getTaskPriority()).isEqualTo(TaskPriority.A);
    }

    @Test
    @DisplayName("이월 되돌리기: 원래 날짜로 돌아가고, 되돌린 Task 는 자동 이월 대상이 아니다")
    void revertCarry() {
        Task t = unplacedTask();
        t.scheduleByUser(D.atTime(14, 0), D.atTime(15, 0));
        t.carryOverTo(D.plusDays(1));
        assertThat(t.isCarryReverted()).isFalse();

        t.revertCarry();
        assertThat(t.getRoutineDate()).isEqualTo(D);
        assertThat(t.getScheduledDate()).isEqualTo(D);
        assertThat(t.getCarryOverCount()).isZero();
        assertThat(t.isCarryPending()).isFalse();
        assertThat(t.isCarryReverted()).isTrue();

        // 원래 시각으로 다시 놓아도 되돌린 표시는 유지
        t.scheduleByUser(D.atTime(14, 0), D.atTime(15, 0));
        assertThat(t.isCarryReverted()).isTrue();
        assertThat(com.naleul.naleul.domain.planner.service.NightlyPlanWorker.shouldCarry(t, D.atTime(23, 0))).isFalse();
    }

    @Test
    @DisplayName("이월 되돌리기: 두 번 이월된 Task 를 처음 날로 되돌리면 그날이 되돌린 날로 표시된다")
    void revertCarryToEarlierTrace() {
        Task t = unplacedTask();
        t.scheduleByUser(D.atTime(14, 0), D.atTime(15, 0));
        t.carryOverTo(D.plusDays(1));
        t.scheduleByUser(D.plusDays(1).atTime(22, 0), D.plusDays(1).atTime(23, 0));
        t.carryOverTo(D.plusDays(2));

        t.revertCarryTo(D);
        assertThat(t.currentDate()).isEqualTo(D);
        assertThat(t.getCarriedFromDate()).isEqualTo(D);
        assertThat(t.isCarryReverted()).isTrue();
        assertThat(t.getCarryOverCount()).isEqualTo(1);
    }
}
