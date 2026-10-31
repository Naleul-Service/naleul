package com.naleul.naleul.domain.pattern.dto;

import com.naleul.naleul.domain.pattern.enums.PatternSuggestionType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * POST /api/v1/patterns/suggestions/{key}/apply 응답 (명세 4 · 4-3)
 * @param affectedTaskCount 바뀐(시각 변경·삭제·생성) 루틴 Task 수
 * @param rePlaced          새 루틴 시간과 겹쳐서 다시 배치된 자동 배치 블록
 * @param unscheduled       다시 배치할 자리가 없어 시간 미정이 된 Task
 */
public record PatternSuggestionApplyResponse(String key, PatternSuggestionType type, Long routineId, String routineName,
                                             PatternReportResponse.Change before, PatternReportResponse.Change after,
                                             int affectedTaskCount, List<RePlaced> rePlaced, List<Long> unscheduled) {

    public record RePlaced(Long taskId, LocalDateTime from, LocalDateTime to) {
    }
}
