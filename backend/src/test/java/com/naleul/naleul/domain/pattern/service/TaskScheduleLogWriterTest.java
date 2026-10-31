package com.naleul.naleul.domain.pattern.service;

import com.naleul.naleul.domain.pattern.entity.BrainDumpLog;
import com.naleul.naleul.domain.pattern.entity.TaskScheduleLog;
import com.naleul.naleul.domain.pattern.enums.TaskScheduleAction;
import com.naleul.naleul.domain.pattern.repository.BrainDumpLogRepository;
import com.naleul.naleul.domain.pattern.repository.TaskScheduleLogRepository;
import com.naleul.naleul.domain.planner.support.PlannerClock;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.enums.TaskPriority;
import com.naleul.naleul.domain.task.enums.TaskStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TaskScheduleLogWriterTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 18, 22, 0);

    private final TaskScheduleLogRepository logRepo = mock(TaskScheduleLogRepository.class);
    private final BrainDumpLogRepository brainDumpRepo = mock(BrainDumpLogRepository.class);
    private final PlannerClock clock = new PlannerClock() {
        @Override
        public LocalDateTime now() {
            return NOW;
        }
    };
    private final TaskScheduleLogWriter writer = new TaskScheduleLogWriter(logRepo, brainDumpRepo, clock);

    private static Task task(LocalDateTime start, LocalDate routineDate) {
        return Task.builder()
                .taskId(10L)
                .taskName("토익 RC")
                .taskStatus(TaskStatus.TODO)
                .taskPriority(TaskPriority.C)
                .plannedStartAt(start)
                .plannedEndAt(start != null ? start.plusMinutes(30) : null)
                .routineDate(routineDate)
                .build();
    }

    private TaskScheduleLog savedLog() {
        ArgumentCaptor<TaskScheduleLog> captor = ArgumentCaptor.forClass(TaskScheduleLog.class);
        verify(logRepo).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void 사용자가_시간을_바꾸면_전후_시각이_남는다() {
        Task t = task(LocalDateTime.of(2026, 11, 19, 12, 40), null);

        writer.userMoved(1L, t, LocalDate.of(2026, 11, 19), LocalDateTime.of(2026, 11, 19, 8, 0));

        TaskScheduleLog log = savedLog();
        assertThat(log.getAction()).isEqualTo(TaskScheduleAction.USER_MOVE);
        assertThat(log.getFromStartAt()).isEqualTo(LocalDateTime.of(2026, 11, 19, 8, 0));
        assertThat(log.getToStartAt()).isEqualTo(LocalDateTime.of(2026, 11, 19, 12, 40));
        assertThat(log.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void 같은_시각이면_기록하지_않는다() {
        LocalDateTime s = LocalDateTime.of(2026, 11, 19, 8, 0);
        writer.userMoved(1L, task(s, null), s.toLocalDate(), s);
        writer.enginePlaced(1L, task(s, null), s);

        verify(logRepo, never()).save(any());
    }

    @Test
    void 이월은_원래_날짜와_시각을_남긴다() {
        Task t = task(LocalDateTime.of(2026, 11, 18, 8, 0), LocalDate.of(2026, 11, 18));

        writer.carriedOver(1L, t, LocalDate.of(2026, 11, 19));

        TaskScheduleLog log = savedLog();
        assertThat(log.getAction()).isEqualTo(TaskScheduleAction.CARRY_OVER);
        assertThat(log.getFromDate()).isEqualTo(LocalDate.of(2026, 11, 18));
        assertThat(log.getFromStartAt()).isEqualTo(LocalDateTime.of(2026, 11, 18, 8, 0));
        assertThat(log.getToDate()).isEqualTo(LocalDate.of(2026, 11, 19));
        assertThat(log.getToStartAt()).isNull();
    }

    @Test
    void 시간이_없던_Task를_미정으로_되돌리면_기록하지_않는다() {
        writer.userUnscheduled(1L, task(null, LocalDate.of(2026, 11, 19)), LocalDate.of(2026, 11, 19), null);
        verify(logRepo, never()).save(any());
    }

    @Test
    void 기록_저장이_실패해도_예외를_던지지_않는다() {
        when(logRepo.save(any())).thenThrow(new RuntimeException("DB 오류"));
        Task t = task(LocalDateTime.of(2026, 11, 19, 12, 40), null);

        assertThatCode(() -> writer.userMoved(1L, t, null, null)).doesNotThrowAnyException();
    }

    @Test
    void 브레인덤프_확정_기록() {
        writer.brainDumpConfirmed(1L, 5, 1);

        ArgumentCaptor<BrainDumpLog> captor = ArgumentCaptor.forClass(BrainDumpLog.class);
        verify(brainDumpRepo).save(captor.capture());
        assertThat(captor.getValue().getItemCount()).isEqualTo(5);
        assertThat(captor.getValue().getConfirmedAt()).isEqualTo(NOW);
    }

    @Test
    void noop_기록기는_아무것도_하지_않는다() {
        Task t = task(LocalDateTime.of(2026, 11, 19, 12, 40), null);
        assertThatCode(() -> {
            TaskScheduleLogWriter.noop().userMoved(1L, t, null, null);
            TaskScheduleLogWriter.noop().carriedOver(1L, t, LocalDate.of(2026, 11, 20));
            TaskScheduleLogWriter.noop().brainDumpConfirmed(1L, 1, 0);
        }).doesNotThrowAnyException();
    }
}
