package com.naleul.naleul.domain.admin.service;

import com.naleul.naleul.domain.admin.dto.ChatReviewResponse.FieldRow;
import com.naleul.naleul.domain.admin.repository.ChatReviewQueryRepository.FieldStat;
import com.naleul.naleul.domain.goalCreation.enums.ReviewField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatReviewServiceTest {

    @Test
    @DisplayName("항목별: 수정률 = 고침 ÷ AI 가 채움, 진행률 = 100 − 수정률, 칩/직접 비율, 누락률 = 직접 채움 ÷ AI 가 비움")
    void fieldRow() {
        FieldRow r = ChatReviewService.row(ReviewField.PRACTICE_PREFERENCE, new FieldStat("PRACTICE_PREFERENCE", 89, 30, 10, 39, 35, 65));
        assertThat(r.editRate()).isEqualTo(34);
        assertThat(r.progressRate()).isEqualTo(66);
        assertThat(r.chipRate()).isEqualTo(35);
        assertThat(r.typedRate()).isEqualTo(65);
        assertThat(r.missRate()).isEqualTo(26);
    }

    @Test
    @DisplayName("이유 · 실행 성향은 누락률 없음, 실행 성향은 칩/직접 비율도 없음, 데이터 없으면 null")
    void excludedFields() {
        FieldRow motivation = ChatReviewService.row(ReviewField.MOTIVATION, new FieldStat("MOTIVATION", 21, 2, 0, 107, 0, 21));
        assertThat(motivation.missRate()).isNull();
        assertThat(motivation.typedRate()).isEqualTo(100);
        FieldRow style = ChatReviewService.row(ReviewField.PLANNING_STYLE, new FieldStat("PLANNING_STYLE", 128, 24, 0, 0, 40, 60));
        assertThat(style.editRate()).isEqualTo(19);
        assertThat(style.chipRate()).isNull();
        assertThat(style.missRate()).isNull();
        FieldRow none = ChatReviewService.row(ReviewField.METRIC, null);
        assertThat(none.editRate()).isNull();
        assertThat(none.aiFilled()).isZero();
    }
}
