package com.naleul.naleul.domain.scheduleEdit.dto;

import java.util.List;

/**
 * @param applied 적용한 변경 수
 * @param skipped 못 한 변경 (예: "Task '보고서 쓰기' — 그 시간에 잠긴 일정이 있어요")
 */
public record ScheduleEditApplyResponse(int applied, List<String> skipped) {
}
