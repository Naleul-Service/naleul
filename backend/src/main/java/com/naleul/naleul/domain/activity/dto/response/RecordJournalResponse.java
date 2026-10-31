package com.naleul.naleul.domain.activity.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 기록형 목표의 업무 일지 — 날짜별로 "그날 완료한 Task"와 "직접 남긴 기록"을 함께 모은다.
 * days 는 최근 날짜부터. 더 오래된 일지가 있으면 nextBefore 로 다음 쪽을 부른다 (없으면 null).
 */
public record RecordJournalResponse(List<Day> days, LocalDate nextBefore) {

    public record Day(
            LocalDate date,
            int taskCount,
            int recordCount,
            /** 시간을 알 수 있는 항목의 합 */
            long minutes,
            /** 시각순 (시각이 없는 Task 는 뒤로) */
            List<Item> items
    ) {
    }

    /**
     * type: TASK(완료한 Task) · RECORD(직접 남긴 기록).
     * startAt·endAt·minutes 는 모르면 null (시간 미정으로 완료한 Task).
     */
    public record Item(
            String type,
            Long id,
            String title,
            String emoji,
            LocalDateTime startAt,
            LocalDateTime endAt,
            Long minutes,
            boolean routine,
            String memo
    ) {
    }
}
