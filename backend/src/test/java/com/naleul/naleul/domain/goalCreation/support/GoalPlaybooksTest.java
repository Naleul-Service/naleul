package com.naleul.naleul.domain.goalCreation.support;

import com.naleul.naleul.domain.goalCategory.kind.GoalKind;
import com.naleul.naleul.domain.goalCategory.kind.GoalSubType;
import com.naleul.naleul.domain.goalCategory.kind.GoalType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class GoalPlaybooksTest {

    private final GoalPlaybooks playbooks = new GoalPlaybooks();   // 실제 resources/ai/goal-creation/playbooks.txt

    @Test
    void 기타가_아닌_2단계_카테고리는_전부_자기_가이드가_있다() {
        // 업무(WORK)는 업무형 목표 전용이라 AI 설계를 거치지 않아요 → 가이드 없음
        Arrays.stream(GoalSubType.values()).filter(s -> !s.isCustom() && s.getType() != GoalType.WORK).forEach(sub -> {
            String guide = playbooks.forPlan(new GoalKind(sub.getType(), sub, null));
            assertThat(guide).as(sub.name()).isNotBlank();
            assertThat(guide).as(sub.name() + " 은 1단계 공통 가이드가 아니라 자기 가이드").contains("꼭 알아야 할 것");
        });
    }

    @Test
    void 기타는_1단계_공통_가이드를_쓴다() {
        String guide = playbooks.forPlan(GoalKind.of(GoalType.HEALTH, GoalSubType.HEALTH_OTHER, "필라테스"));
        assertThat(guide).contains("루틴 위주로");
        assertThat(playbooks.forPlan(GoalKind.of(GoalType.ETC, null, "악기 연습"))).isNotBlank();
    }

    @Test
    void 다이어트_가이드는_운동_루틴과_식단을_담고_준비_Task를_막는다() {
        String guide = playbooks.forPlan(GoalKind.of(GoalType.HEALTH, GoalSubType.DIET, null));
        assertThat(guide).contains("운동 세션", "식단", "만들지 말 것", "저칼로리 식재료 구매");
    }

    @Test
    void 인터뷰용은_꼭_알아야_할_것_한_줄만() {
        String hint = playbooks.forInterview(GoalKind.of(GoalType.HEALTH, GoalSubType.EXERCISE, null));
        assertThat(hint).contains("운동 장소").doesNotContain("\n").doesNotStartWith("꼭 알아야 할 것");
        assertThat(playbooks.forInterview(null)).isNull();
    }

    @Test
    void 주석과_섹션을_나눠_읽는다() {
        GoalPlaybooks custom = new GoalPlaybooks("# 주석\n### HEALTH\n건강 공통\n# 섹션 안 주석\n### DIET\n꼭 알아야 할 것: 체중\n식단\n");
        assertThat(custom.forPlan(GoalKind.of(GoalType.HEALTH, GoalSubType.DIET, null))).isEqualTo("꼭 알아야 할 것: 체중\n식단");
        assertThat(custom.forPlan(GoalKind.of(GoalType.HEALTH, GoalSubType.EXERCISE, null))).isEqualTo("건강 공통");
    }
}
