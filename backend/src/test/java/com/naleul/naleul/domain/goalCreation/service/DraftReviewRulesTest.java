package com.naleul.naleul.domain.goalCreation.service;

import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto.PlanFit;
import com.naleul.naleul.domain.goalCreation.dto.request.GoalDraftRequest;
import com.naleul.naleul.domain.goalCreation.entity.GoalDraft;
import com.naleul.naleul.domain.goalCreation.enums.DraftFieldResult;
import com.naleul.naleul.domain.goalCreation.enums.DraftItemKind;
import com.naleul.naleul.domain.goalCreation.enums.DraftPath;
import com.naleul.naleul.domain.goalCreation.enums.DraftSource;
import com.naleul.naleul.domain.goalCreation.enums.DraftField;
import com.naleul.naleul.domain.goalCreation.personal.PlanIntensity;
import com.naleul.naleul.domain.goalCreation.support.DraftPlanComparer.Diff;
import com.naleul.naleul.domain.user.enums.PlanningStyle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** V68 AI 초안 정확도 — 요청 종류 · 세션 경로 · 항목 결과 규칙 */
class DraftReviewRulesTest {

    static GoalDraft draft(PlanningStyle style) {
        return GoalDraft.generating(null, 1, style, null);
    }

    static GoalPlanDto plan(String intensity) {
        return new GoalPlanDto(null, List.of(), List.of(), List.of(), List.of(),
                intensity == null ? null : new PlanFit(intensity, 300, 240, false, List.of()));
    }

    static Diff diff(DraftFieldResult r) {
        return new Diff(DraftItemKind.ROUTINE, "t1", "헬스", DraftField.TITLE, r, null, null, "a", "b");
    }

    @Test
    @DisplayName("요청 종류: 첫 초안 · 실패 뒤 재시도 · 말로 고치기 · 강도 · 성향 · 다시 생성")
    void draftSource() {
        GoalDraft prev = draft(PlanningStyle.PLANNER);
        GoalDraftRequest empty = GoalDraftRequest.builder().build();
        assertThat(GoalDraftTxService.draftSource(null, null, empty, false)).isEqualTo(DraftSource.INITIAL);
        assertThat(GoalDraftTxService.draftSource(prev, null, empty, false)).isEqualTo(DraftSource.RETRY);
        assertThat(GoalDraftTxService.draftSource(prev, plan("STEADY"), empty, true)).isEqualTo(DraftSource.QUICK_FIX);
        assertThat(GoalDraftTxService.draftSource(prev, plan("STEADY"),
                GoalDraftRequest.builder().intensity(PlanIntensity.CHALLENGE).build(), false)).isEqualTo(DraftSource.INTENSITY);
        // 같은 강도 · 같은 성향을 다시 보내면 (다시 생성 창에서 성향을 함께 보냄) 다시 생성
        assertThat(GoalDraftTxService.draftSource(prev, plan("STEADY"),
                GoalDraftRequest.builder().intensity(PlanIntensity.STEADY).planningStyle(PlanningStyle.PLANNER).build(), false))
                .isEqualTo(DraftSource.REGEN);
        assertThat(GoalDraftTxService.draftSource(prev, plan(null),
                GoalDraftRequest.builder().planningStyle(PlanningStyle.SPONTANEOUS).build(), false)).isEqualTo(DraftSource.STYLE);
        assertThat(GoalDraftTxService.draftSource(prev, plan("STEADY"),
                GoalDraftRequest.builder().feedback("더 가볍게").build(), false)).isEqualTo(DraftSource.REGEN);
    }

    @Test
    @DisplayName("세션 경로는 가장 강한 신호 하나: 다시 생성 > 말로 고치기 > 직접 수정 > 그대로")
    void path() {
        List<Diff> kept = List.of(diff(DraftFieldResult.KEPT), diff(DraftFieldResult.AUTO));
        List<Diff> edited = List.of(diff(DraftFieldResult.KEPT), diff(DraftFieldResult.EDITED));
        assertThat(DraftReviewRecorder.pathOf(1, 3, edited)).isEqualTo(DraftPath.REGEN);
        assertThat(DraftReviewRecorder.pathOf(0, 2, kept)).isEqualTo(DraftPath.QUICK_FIX);
        assertThat(DraftReviewRecorder.pathOf(0, 0, edited)).isEqualTo(DraftPath.MANUAL);
        assertThat(DraftReviewRecorder.pathOf(0, 0, kept)).isEqualTo(DraftPath.AS_IS);   // 자동 조정만 있으면 그대로
    }

    @Test
    @DisplayName("항목 결과: 추가 > 삭제 > 고침 > 그대로 (자동 조정은 그대로)")
    void itemResult() {
        assertThat(DraftReviewRecorder.itemResult(List.of(diff(DraftFieldResult.KEPT), diff(DraftFieldResult.AUTO))))
                .isEqualTo(DraftFieldResult.KEPT);
        assertThat(DraftReviewRecorder.itemResult(List.of(diff(DraftFieldResult.KEPT), diff(DraftFieldResult.EDITED))))
                .isEqualTo(DraftFieldResult.EDITED);
        assertThat(DraftReviewRecorder.itemResult(List.of(diff(DraftFieldResult.DELETED)))).isEqualTo(DraftFieldResult.DELETED);
    }
}
