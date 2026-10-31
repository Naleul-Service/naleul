package com.naleul.naleul.domain.admin.service;

import com.naleul.naleul.domain.admin.dto.AdminDashboardResponse.InProgressGroup;
import com.naleul.naleul.domain.admin.dto.AdminDashboardResponse.PathGroupRow;
import com.naleul.naleul.domain.admin.repository.AdminDashboardQueryRepository.GoalExecutionRow;
import com.naleul.naleul.domain.admin.repository.AdminDashboardQueryRepository.OriginRow;
import com.naleul.naleul.domain.admin.repository.AdminDashboardQueryRepository.PathOutcomeRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 서비스 지표 · 목표 달성률 / Task 실천률을 AI 초안 경로 · 출처별로 쪼개기 (2026-10-25) */
class PracticeBreakdownTest {

    @Test
    @DisplayName("경로별 달성률: 화면 순서대로, 실천률 · 보존율은 개수 가중 평균, 직접 고른 결과 수")
    void byPath() {
        List<PathGroupRow> rows = AdminDashboardService.byPath(List.of(
                new PathOutcomeRow("MANUAL_GOAL", "ACHIEVED", "USER", 1, 90.0, 90.0, null),
                new PathOutcomeRow("AS_IS", "ACHIEVED", "AUTO", 3, 80.0, 70.0, 100.0),
                new PathOutcomeRow("AS_IS", "NOT_ACHIEVED", "USER", 1, 20.0, null, 100.0),
                new PathOutcomeRow("MANUAL", "PARTIAL", "AUTO", 2, 60.0, 50.0, 82.5)));

        assertThat(rows).extracting(PathGroupRow::key).containsExactly("AS_IS", "MANUAL", "MANUAL_GOAL");
        PathGroupRow asIs = rows.get(0);
        assertThat(asIs.achievementRate()).isEqualTo(75);
        assertThat(asIs.avgRoutineRate()).isEqualTo(65);   // (80×3 + 20×1) / 4
        assertThat(asIs.avgTaskRate()).isEqualTo(70);      // 값 없는 행은 뺀다
        assertThat(asIs.userPicked()).isEqualTo(1);
        assertThat(rows.get(1).successRate()).isEqualTo(100);
        assertThat(rows.get(2).avgKeptRate()).isNull();
    }

    @Test
    @DisplayName("진행 중 실천률: AI 전체 + 경로별 + 직접, 해야 했던 게 없는 목표는 뺀다")
    void inProgress() {
        List<InProgressGroup> g = AdminDashboardService.inProgressByPath(List.of(
                new GoalExecutionRow(1, "AS_IS", 10, 9), new GoalExecutionRow(2, "REGEN", 10, 4),
                new GoalExecutionRow(3, "MANUAL_GOAL", 5, 5), new GoalExecutionRow(4, "AS_IS", 0, 0)));

        assertThat(g).extracting(InProgressGroup::key).containsExactly("AI", "AS_IS", "REGEN", "MANUAL_GOAL");
        assertThat(g.get(0).goals()).isEqualTo(2);
        assertThat(g.get(0).avgExecutionRate()).isEqualTo(65);
        assertThat(g.get(0).onTrackRate()).isEqualTo(50);
    }

    @Test
    @DisplayName("출처별 실천률: 루틴 · 일반 Task 를 나눠 합치고, 해야 했던 게 없는 출처는 뺀다")
    void origins() {
        var rows = AdminDashboardService.origins(List.of(
                new OriginRow("NO_GOAL", false, 10, 7, 5),
                new OriginRow("AI_KEPT", true, 20, 15, 3),
                new OriginRow("AI_KEPT", false, 4, 2, 2)));

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).key()).isEqualTo("AI_KEPT");
        assertThat(rows.get(0).routineRate()).isEqualTo(75);
        assertThat(rows.get(0).taskRate()).isEqualTo(50);
        assertThat(rows.get(1).routineRate()).isNull();
    }
}
