package com.naleul.naleul.domain.goalCreation.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 기동과 같은 방식(생성자 바인딩)으로 goal.ai.* 가 묶이는지.
 * record 에 생성자를 하나 더 만들면 "No default constructor found" 로 서버가 안 뜨는데,
 * DB 없이 이 테스트로 미리 잡는다.
 */
class GoalAiPropertiesBindingTest {

    private GoalAiProperties bind(Map<String, String> values) {
        return new Binder(new MapConfigurationPropertySource(values))
                .bindOrCreate("goal.ai", GoalAiProperties.class);
    }

    @Test
    void 설정이_없으면_기본값() {
        GoalAiProperties p = bind(Map.of());

        assertThat(p.dailySessionLimit()).isEqualTo(1);
        assertThat(p.weeklySessionLimit()).isEqualTo(3);
        assertThat(p.dailyDraftLimit()).isEqualTo(5);
        assertThat(p.proDailySessionLimit()).isEqualTo(10);
        assertThat(p.proUserIds()).containsExactly(1L, 2L);
        assertThat(p.sessionLimitFor(2L, false)).isEqualTo(10);   // 지정 사용자
        assertThat(p.sessionLimitFor(99L, false)).isEqualTo(1);   // 무료
        assertThat(p.weeklySessionLimitFor(99L, false)).isEqualTo(3);
        assertThat(p.weeklySessionLimitFor(99L, true)).isEqualTo(Integer.MAX_VALUE);
        assertThat(p.sessionLimitFor(99L, true)).isEqualTo(10);   // Pro
    }

    @Test
    void yml_값으로_덮어쓴다() {
        GoalAiProperties p = bind(Map.of(
                "goal.ai.pro-daily-session-limit", "30",
                "goal.ai.pro-user-ids", "7,8"));

        assertThat(p.proDailySessionLimit()).isEqualTo(30);
        assertThat(p.proUserIds()).containsExactly(7L, 8L);
    }
}
