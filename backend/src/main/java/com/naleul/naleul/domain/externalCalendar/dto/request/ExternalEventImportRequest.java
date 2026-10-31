package com.naleul.naleul.domain.externalCalendar.dto.request;

import com.naleul.naleul.domain.externalCalendar.enums.ExternalCalendarSource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * POST /api/v1/external-calendar/import — 사용자가 고른 외부 일정을 Task 로 만든다.
 *
 * 외부 캘린더를 서버가 직접 읽지 않고, 클라이언트(웹: 구글 · iOS: 아이폰 캘린더)가 읽어서 고른 것만 보낸다.
 * 그래서 서버는 구글 비밀키 · 토큰을 저장하지 않는다.
 *
 * @param goalCategoryId 가져온 Task 를 연결할 목표 (선택)
 */
public record ExternalEventImportRequest(
        @NotNull ExternalCalendarSource source,
        Long goalCategoryId,
        @NotEmpty @Size(max = 300) List<@Valid Event> events
) {

    /**
     * 외부 일정 1건. 시각은 한국 시간 벽시계 값 (yyyy-MM-ddTHH:mm:ss).
     *
     * @param externalId   외부 캘린더의 일정 id (반복 일정이면 회차마다 달라야 함). 다시 가져올 때 중복을 막는 데 쓴다
     * @param calendarName 어느 캘린더에서 왔는지 (로그 · 화면용, 선택)
     * @param allDay       하루 종일 일정이면 true → date 만 쓴다
     */
    public record Event(
            @NotBlank @Size(max = 255) String externalId,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 100) String calendarName,
            boolean allDay,
            LocalDateTime start,
            LocalDateTime end,
            LocalDate date
    ) {
    }
}
