package com.naleul.naleul.domain.admin.service;

import com.naleul.naleul.domain.admin.dto.AiInterestResponse.Drop;
import com.naleul.naleul.domain.admin.dto.AiInterestResponse.PlanStat;
import com.naleul.naleul.domain.admin.repository.AiInterestQueryRepository.LimitRow;
import com.naleul.naleul.domain.admin.repository.AiInterestQueryRepository.SessionRow;
import com.naleul.naleul.domain.admin.repository.AiInterestQueryRepository.SuggestionRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** V70 AI 관심도 — 시도 · 완료 · 중단 분류와 Free / Pro 집계 */
class AiInterestRulesTest {

    @Test
    @DisplayName("목표 만들기: 확정 = 완료, 그만둠은 멈춘 화면으로, 진행 중은 따로, 한도에 막힌 시작도 시도")
    void goalCreate() {
        List<AiInterestService.Attempt> a = AiInterestService.goalCreate(List.of(
                new SessionRow(1, "FREE", "CONFIRMED", null, true, false, 1, 1),
                new SessionRow(2, null, "ABANDONED", "USER_CANCEL", true, false, 2, 1),
                new SessionRow(3, "PRO", "ABANDONED", null, false, true, 0, 0),
                new SessionRow(4, "FREE", "ABANDONED", "USER_CANCEL", true, false, 0, 0),
                new SessionRow(5, "FREE", "INTERVIEWING", null, false, false, 0, 0)),
                List.of(new LimitRow(6, "FREE", "GOAL_INTERVIEW", "WEEKLY"),
                        new LimitRow(6, "FREE", "GOAL_PLAN", "DAILY")));   // 초안 한도는 시도로 세지 않음

        assertThat(a).hasSize(6);
        assertThat(a.get(0).outcome()).isEqualTo(AiInterestService.Outcome.COMPLETED);
        assertThat(a.get(1).step()).isEqualTo("초안 화면 (확정 안 함)");
        assertThat(a.get(1).plan()).isEqualTo("FREE");                    // 당시 플랜이 없으면 FREE 로
        assertThat(a.get(2).detail()).isEqualTo("7일 동안 이어서 안 함");   // 예전 행: 만료 시각으로 추정
        assertThat(a.get(3).step()).isEqualTo("정리 화면");
        assertThat(a.get(4).outcome()).isEqualTo(AiInterestService.Outcome.IN_PROGRESS);
        assertThat(a.get(5).step()).isEqualTo("시작 전");
        assertThat(a.get(5).detail()).isEqualTo("주간 한도에 막힘");
    }

    @Test
    @DisplayName("제안형 기능: 적용 = 완료, 2시간 안 미적용은 진행 중, 제안 0개 · 그냥 나감 · 한도를 나눠 센다")
    void suggestions() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 25, 12, 0);
        List<AiInterestService.Attempt> a = AiInterestService.suggestions(List.of(
                new SuggestionRow(1, "FREE", "GOAL_EDIT", "APPLIED", true, 2, now.minusDays(1)),
                new SuggestionRow(1, "FREE", "GOAL_EDIT", "SUGGESTED", true, 0, now.minusDays(1)),
                new SuggestionRow(1, "FREE", "GOAL_EDIT", "SUGGESTED", true, 2, now.minusDays(1)),
                new SuggestionRow(2, "PRO", "SCHEDULE_EDIT", "SUGGESTED", true, 2, now.minusMinutes(30)),
                new SuggestionRow(3, "FREE", "BRAIN_DUMP", "SUGGESTED", false, 3, now.minusDays(1))),
                List.of(new LimitRow(1, "FREE", "GOAL_EDIT", "DAILY")), now);

        assertThat(a).extracting(AiInterestService.Attempt::detail).containsExactly(
                null, "쓸 수 있는 제안이 없음", "제안을 보고 적용 안 함", null,
                "AI 대신 규칙으로 해석 → 확정 안 함", "하루 한도에 막힘");
        assertThat(a.get(3).outcome()).isEqualTo(AiInterestService.Outcome.IN_PROGRESS);
    }

    @Test
    @DisplayName("플랜별 집계: 비율 분모는 시도 수, 써 본 비율 분모는 같은 플랜 활동 사용자")
    void planStatAndDrops() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 25, 12, 0);
        List<AiInterestService.Attempt> a = new ArrayList<>(AiInterestService.suggestions(List.of(
                new SuggestionRow(1, "FREE", "GOAL_EDIT", "APPLIED", true, 2, now.minusDays(2)),
                new SuggestionRow(1, "FREE", "GOAL_EDIT", "APPLIED", true, 2, now.minusDays(1)),
                new SuggestionRow(2, "FREE", "GOAL_EDIT", "SUGGESTED", true, 2, now.minusDays(1)),
                new SuggestionRow(3, "PRO", "GOAL_EDIT", "SUGGESTED", true, 0, now.minusDays(1))),
                List.of(), now));
        Map<Long, String> active = Map.of(1L, "FREE", 2L, "FREE", 3L, "PRO", 4L, "FREE");

        PlanStat free = AiInterestService.planStat(a, "FREE", active);
        assertThat(free.users()).isEqualTo(2);
        assertThat(free.triedRate()).isEqualTo(67);       // 2 / 3
        assertThat(free.completedRate()).isEqualTo(67);   // 2 / 3
        assertThat(free.repeatUsers()).isEqualTo(1);

        List<Drop> drops = AiInterestService.drops(a);
        assertThat(drops).hasSize(2);
        assertThat(drops).allMatch(d -> d.feature().equals("GOAL_EDIT"));
        Drop none = drops.stream().filter(d -> d.detail().equals("쓸 수 있는 제안이 없음")).findFirst().orElseThrow();
        assertThat(none.pro().count()).isEqualTo(1);
        assertThat(none.pro().ofDrops()).isEqualTo(100);
        assertThat(none.free().ofAttempts()).isZero();
    }
}
