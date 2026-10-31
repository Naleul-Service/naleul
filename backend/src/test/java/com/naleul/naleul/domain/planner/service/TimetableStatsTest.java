package com.naleul.naleul.domain.planner.service;

import com.naleul.naleul.domain.planner.dto.response.TimetableResponse;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.enums.TaskPriority;
import com.naleul.naleul.domain.task.enums.TaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TimetableStatsTest {

    private static final LocalDate D = LocalDate.of(2026, 11, 18);
    private static final LocalDateTime NOW = D.atTime(14, 0);

    private static Task placed(int hour, TaskStatus status) {
        Task t = Task.create(null, null, null, "t", TaskPriority.C, D.atTime(hour, 0), D.atTime(hour, 30));
        ReflectionTestUtils.setField(t, "taskStatus", status);
        return t;
    }

    @Test
    @DisplayName("실행률 = 완료/전체, 놓침 = 시간이 지난 TODO, 건너뜀은 분모에서 제외")
    void stats() {
        List<Task> placed = List.of(
                placed(8, TaskStatus.COMPLETED),
                placed(10, TaskStatus.TODO),      // 지남 → 놓침
                placed(16, TaskStatus.TODO),      // 아직 → 놓침 아님
                placed(11, TaskStatus.SKIPPED));  // 분모 제외
        TimetableResponse.Stats s = TimetableQueryService.stats(placed, List.of(), NOW);
        assertThat(s.total()).isEqualTo(3);
        assertThat(s.completed()).isEqualTo(1);
        assertThat(s.missed()).isEqualTo(1);
        assertThat(s.rate()).isEqualTo(33);
    }

    @Test
    @DisplayName("Task가 없으면 실행률은 null")
    void empty() {
        assertThat(TimetableQueryService.stats(List.of(), List.of(), NOW).rate()).isNull();
    }

    @Test
    @DisplayName("이월된 Task 는 원래 날의 분모·놓침에 남는다 → 다음 날 아침에도 실천률이 그대로")
    void carriedOverKeepsRate() {
        // 어제: 5개 중 3개 완료 = 60%. 못 한 2개가 오늘로 이월돼 어제 목록에서 사라졌다
        List<Task> remaining = List.of(
                placed(8, TaskStatus.COMPLETED),
                placed(9, TaskStatus.COMPLETED),
                placed(10, TaskStatus.COMPLETED));
        TimetableResponse.Stats s = TimetableQueryService.stats(remaining, List.of(), NOW, 2);
        assertThat(s.total()).isEqualTo(5);
        assertThat(s.completed()).isEqualTo(3);
        assertThat(s.missed()).isEqualTo(2);
        assertThat(s.rate()).isEqualTo(60);
        assertThat(s.carriedOver()).isEqualTo(2);
    }

    @Test
    @DisplayName("이월 후 다시 그날로 옮겨 놓은 Task 는 두 번 세지 않는다")
    void carriedButMovedBackCountsOnce() {
        Task back = placed(10, TaskStatus.TODO);
        ReflectionTestUtils.setField(back, "taskId", 7L);
        assertThat(TimetableQueryService.carriedCount(java.util.Set.of(7L, 8L), List.of(back), List.of())).isEqualTo(1);
    }
}
