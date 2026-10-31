package com.naleul.naleul.domain.task.entity;

import com.naleul.naleul.domain.task.enums.TaskPriority;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** 22시 이월은 "제안" — 사용자가 옮기기/안 옮기기를 고를 때까지 carryPending */
class TaskCarryPendingTest {

    private static final LocalDate D = LocalDate.of(2026, 11, 18);

    private Task placed() {
        return Task.create(null, null, null, "보고서", TaskPriority.B, D.atTime(15, 0), D.atTime(16, 0));
    }

    @Test
    @DisplayName("이월하면 제안 상태 + 원래 날짜를 기억한다")
    void carry() {
        Task t = placed();
        t.carryOverTo(D.plusDays(1));
        assertThat(t.isCarryPending()).isTrue();
        assertThat(t.getCarriedFromDate()).isEqualTo(D);
        assertThat(t.getRoutineDate()).isEqualTo(D.plusDays(1));
    }

    @Test
    @DisplayName("옮기기: 자리 그대로 확정 / 안 옮기기: 시간을 비워 시간 미정으로")
    void acceptAndDecline() {
        Task a = placed();
        a.carryOverTo(D.plusDays(1));
        a.placeByEngine(D.plusDays(1).atTime(10, 0), D.plusDays(1).atTime(11, 0), "r", "C");
        a.acceptCarry();
        assertThat(a.isCarryPending()).isFalse();
        assertThat(a.getPlannedStartAt()).isEqualTo(D.plusDays(1).atTime(10, 0));

        Task b = placed();
        b.carryOverTo(D.plusDays(1));
        b.placeByEngine(D.plusDays(1).atTime(10, 0), D.plusDays(1).atTime(11, 0), "r", "C");
        b.declineCarry();
        assertThat(b.isCarryPending()).isFalse();
        assertThat(b.getPlannedStartAt()).isNull();
        assertThat(b.getRoutineDate()).isEqualTo(D.plusDays(1));
    }

    @Test
    @DisplayName("직접 시간을 정하거나 완료하면 제안은 끝난다")
    void answeredByAction() {
        Task t = placed();
        t.carryOverTo(D.plusDays(1));
        t.scheduleByUser(D.plusDays(1).atTime(20, 0), D.plusDays(1).atTime(21, 0));
        assertThat(t.isCarryPending()).isFalse();
    }
}
