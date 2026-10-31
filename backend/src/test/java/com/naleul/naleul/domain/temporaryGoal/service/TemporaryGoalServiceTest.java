package com.naleul.naleul.domain.temporaryGoal.service;

import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.enums.TaskPriority;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TemporaryGoalServiceTest {

    private static final LocalDate D = LocalDate.of(2026, 11, 18);

    @Test
    @DisplayName("AI 문맥: 이미 있는 할 일(마감·완료 여부)과 '같은 일을 다시 만들지 말라'는 안내")
    void context() {
        Task house = Task.createOneTimeFromAi(null, null, null, null, "이사할 집 매물 3곳 알아보기", null, 50, null, D.plusDays(2));
        Task call = Task.create(null, null, null, "부동산 방문 예약 전화", TaskPriority.C, D.atTime(12, 30), D.atTime(12, 45));
        call.complete();

        String text = TemporaryGoalService.format("🏠", "이사 준비", List.of(house, call));

        assertThat(text).startsWith("임시 목표 \"🏠 이사 준비\"를 구체화하는 중이에요.");
        assertThat(text).contains("- 이사할 집 매물 3곳 알아보기 (마감 11/20, 미완료)");
        assertThat(text).contains("- 부동산 방문 예약 전화 (11/18, 완료)");
        assertThat(text).contains("같은 할 일을 다시 만들지 말고");
    }

    @Test
    @DisplayName("할 일이 없는 임시 목표")
    void empty() {
        assertThat(TemporaryGoalService.format(null, "운동", List.of())).contains("아직 이 목표에 할 일은 없어요");
    }

    @Test
    @DisplayName("예전 목표 문맥: 루틴처럼 반복된 할 일은 이름별로 묶고, 세부 목표 · 동기를 알려준다")
    void legacyContext() {
        Task run1 = Task.create(null, null, null, "아침 러닝", TaskPriority.C, D.atTime(7, 0), D.atTime(7, 30));
        Task run2 = Task.create(null, null, null, "아침 러닝", TaskPriority.C, D.plusDays(1).atTime(7, 0), D.plusDays(1).atTime(7, 30));
        run1.complete();
        Task diet = Task.create(null, null, null, "식단 기록", TaskPriority.C, D.atTime(20, 0), D.atTime(20, 10));

        String text = TemporaryGoalService.formatLegacy("🏃", "다이어트", "여름 전에", List.of("유산소"), List.of(run1, run2, diet));

        assertThat(text).startsWith("사용자가 예전에 AI 없이 직접 만든 목표 \"🏃 다이어트\"를 AI 로 다시 설계하는 중이에요.");
        assertThat(text).contains("사용자가 적어 둔 동기: 여름 전에");
        assertThat(text).contains("이미 있는 세부 목표: 유산소");
        assertThat(text).contains("- 아침 러닝 (2회 중 1회 완료)");
        assertThat(text).contains("- 식단 기록 (1회 중 0회 완료)");
        assertThat(text).contains("같은 일을 계획에 다시 만들지 말고");
    }
}
