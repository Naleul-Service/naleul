package com.naleul.naleul.domain.goalEdit.dto;

import java.util.List;

/**
 * 말로 고치기 미리보기.
 * @param summary AI 가 한 줄로 정리한 "이렇게 바꿀게요"
 * @param changes 화면에 보여줄 변경 목록 (op 는 적용할 때 그대로 다시 보낸다)
 */
public record GoalEditPreviewResponse(String summary, List<Change> changes) {

    /**
     * @param action "추가" · "변경" · "삭제"
     * @param target 무엇을 ("루틴 '하체 근력 30분'", "목표")
     * @param before 지금 (추가면 null)
     * @param after  바뀐 뒤 (삭제면 null)
     */
    public record Change(GoalEditOp op, String action, String target, String before, String after) {
    }
}
