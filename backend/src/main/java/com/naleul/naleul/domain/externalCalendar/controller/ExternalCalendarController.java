package com.naleul.naleul.domain.externalCalendar.controller;

import com.naleul.naleul.domain.externalCalendar.dto.request.ExternalEventImportRequest;
import com.naleul.naleul.domain.externalCalendar.dto.response.ExternalEventImportResponse;
import com.naleul.naleul.domain.externalCalendar.service.ExternalCalendarImportService;
import com.naleul.naleul.global.common.response.ApiResponse;
import com.naleul.naleul.global.common.response.SuccessCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * POST /api/v1/external-calendar/import — 고른 외부 일정을 Task 로 만든다.
 * 웹(구글 캘린더) · iOS(아이폰 기본 캘린더) 모두 같은 API 를 쓴다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/external-calendar")
public class ExternalCalendarController {

    private final ExternalCalendarImportService importService;

    @PostMapping("/import")
    public ResponseEntity<ApiResponse<ExternalEventImportResponse>> importEvents(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ExternalEventImportRequest request) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.EXTERNAL_EVENTS_IMPORTED,
                importService.importEvents(userId, request)));
    }
}
