package com.naleul.naleul.domain.scheduleEdit.dto;

import java.util.List;

/**
 * AI로 일정 조정 미리보기 (아직 아무것도 안 바뀜).
 * @param summary  AI 가 한 줄로 정리한 "이렇게 바꿀게요"
 * @param changes  화면에 보여줄 변경 목록 (op 는 적용할 때 그대로 다시 보낸다)
 * @param overlaps 지금 겹쳐 있는 루틴 (요청과 상관없이 알려 줘요) — 예) "화 16:00–17:00 '기출 풀기' · '코드 리뷰'"
 */
public record ScheduleEditPreviewResponse(String summary, List<Change> changes, List<String> overlaps) {

    /**
     * @param kind   ROUTINE · TASK
     * @param target 무엇을 ("루틴 '하체 근력 30분'", "Task '보고서 쓰기'")
     * @param goal   목표 이름 (없으면 null)
     * @param before 지금 ("월·수·금 16:00–17:00")
     * @param after  바뀐 뒤
     */
    public record Change(ScheduleEditOp op, String kind, String target, String goal, String colorCode,
                         String before, String after) {
    }
}
