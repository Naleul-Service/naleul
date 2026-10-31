package com.naleul.naleul.domain.externalCalendar.dto.response;

/**
 * @param created          새로 만든 Task 수
 * @param skippedDuplicate 이미 가져왔거나, 같은 이름 · 같은 시작 시각의 Task 가 이미 있어서 건너뜀
 * @param skippedPast      오늘 이전 일정이라 건너뜀 (지난 일정을 Task 로 만들면 "못 한 일"로 잡혀 실행률이 떨어져서)
 * @param skippedInvalid   날짜 · 시간이 비었거나 맞지 않아 건너뜀
 */
public record ExternalEventImportResponse(int created, int skippedDuplicate, int skippedPast, int skippedInvalid) {
}
