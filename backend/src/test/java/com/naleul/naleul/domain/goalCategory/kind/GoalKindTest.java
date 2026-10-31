package com.naleul.naleul.domain.goalCategory.kind;

import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoalKindTest {

    @Test
    void 아무것도_안_고르면_null() {
        assertThat(GoalKind.of(null, null, null)).isNull();
    }

    @Test
    void 일반_2단계는_이름을_무시하고_표시명을_만든다() {
        GoalKind kind = GoalKind.of(GoalType.HEALTH, GoalSubType.DIET, "무시됨");
        assertThat(kind.customLabel()).isNull();
        assertThat(kind.displayName()).isEqualTo("건강 · 다이어트");
    }

    @Test
    void 기타는_직접_적은_이름을_쓴다() {
        assertThat(GoalKind.of(GoalType.LEARNING, GoalSubType.LEARNING_OTHER, " 주식 공부 ").displayName())
                .isEqualTo("학습·자기개발 · 주식 공부");
        // 1단계 기타는 2단계를 안 보내도 된다
        assertThat(GoalKind.of(GoalType.ETC, null, "악기 연습").subType()).isEqualTo(GoalSubType.OTHER);
    }

    @Test
    void 기타_이름은_공백_포함_10자까지() {
        assertThat(GoalKind.of(GoalType.ETC, null, "가 나 다 라 마").customLabel()).hasSize(9);
        assertThatThrownBy(() -> GoalKind.of(GoalType.ETC, null, "열한글자짜리이름입니다"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_GOAL_KIND_LABEL);
        assertThatThrownBy(() -> GoalKind.of(GoalType.HEALTH, GoalSubType.HEALTH_OTHER, "  "))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void 다른_1단계의_2단계를_고르면_400() {
        assertThatThrownBy(() -> GoalKind.of(GoalType.HEALTH, GoalSubType.READING, null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_GOAL_KIND);
    }
}
