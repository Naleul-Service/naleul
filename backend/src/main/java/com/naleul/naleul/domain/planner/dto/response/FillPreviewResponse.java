package com.naleul.naleul.domain.planner.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * "AI로 TimeTable 배치하기" 미리보기 — 아직 아무것도 저장하지 않은 배치 제안.
 * 화면은 proposals 를 점선 블록으로 그리고, 사용자가 끌어서 고친 뒤 [이대로 진행] 하면 /fill/apply 로 확정한다.
 *
 * @param proposals   제안 블록 (taskId · 날짜 · 시각 · 배치 이유)
 * @param unscheduled 빈 시간이 없어 제안하지 못한 Task id
 */
public record FillPreviewResponse(List<Proposal> proposals, List<Long> unscheduled) {

    public record Proposal(Long taskId, LocalDate date, LocalDateTime start, LocalDateTime end, String reason) {
    }
}
