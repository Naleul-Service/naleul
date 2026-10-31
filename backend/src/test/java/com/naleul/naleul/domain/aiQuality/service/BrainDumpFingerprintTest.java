package com.naleul.naleul.domain.aiQuality.service;

import com.naleul.naleul.domain.brainDump.dto.request.BrainDumpConfirmRequest;
import com.naleul.naleul.domain.brainDump.dto.response.BrainDumpParseResponse;
import com.naleul.naleul.domain.brainDump.dto.response.BrainDumpParseResponse.GoalLink;
import com.naleul.naleul.domain.brainDump.enums.DateType;
import com.naleul.naleul.domain.brainDump.enums.GoalMatchType;
import com.naleul.naleul.domain.task.enums.TaskKind;
import com.naleul.naleul.domain.task.enums.TaskPriority;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 브레인 덤프 AI 해석 ↔ 확정 값 비교 */
class BrainDumpFingerprintTest {

    static final LocalDate D = LocalDate.of(2026, 10, 24);

    private static BrainDumpParseResponse.Item parsed(String key, String title, Long goalId) {
        GoalLink link = goalId == null ? GoalLink.none()
                : new GoalLink(GoalMatchType.EXISTING, goalId, "g", null, null, false, null, null, null, null, null, "r");
        return new BrainDumpParseResponse.Item(key, title, DateType.ON, D, null, LocalTime.of(9, 0), null, 30,
                TaskPriority.C, TaskKind.NORMAL, link);
    }

    private static BrainDumpConfirmRequest.Item confirmed(String key, String title, LocalDate date, Long goalId) {
        return new BrainDumpConfirmRequest.Item(key, title, DateType.ON, date, null, LocalTime.of(9, 0), null, 30,
                TaskPriority.C, TaskKind.NORMAL, goalId, null, null, null);
    }

    @Test
    void 바뀐_항목과_목표를_바꾼_항목을_센다() {
        String snap = BrainDumpFingerprint.of(new BrainDumpParseResponse(List.of(
                parsed("a", "영어 단어 30개", 1L),
                parsed("b", "헬스장 가기", 2L),
                parsed("c", "보고서 쓰기", 3L)), List.of(), false));
        assertThat(snap).doesNotContain("영어");   // 제목 원문은 남기지 않는다

        BrainDumpFingerprint.Diff diff = BrainDumpFingerprint.diff(snap, List.of(
                confirmed("a", "영어 단어 30개", D, 1L),            // 그대로
                confirmed("b", "헬스장 가기", D.plusDays(1), 2L),    // 날짜만 바꿈
                confirmed("c", "보고서 쓰기", D, 9L),               // 목표 바꿈
                confirmed("d", "직접 추가", D, 1L)));               // 해석 뒤 추가
        assertThat(diff.edited()).isEqualTo(3);
        assertThat(diff.goalChanged()).isEqualTo(1);
    }
}
