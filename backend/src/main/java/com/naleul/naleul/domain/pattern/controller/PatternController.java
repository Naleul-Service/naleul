package com.naleul.naleul.domain.pattern.controller;

import com.naleul.naleul.domain.pattern.dto.PatternReportResponse;
import com.naleul.naleul.domain.pattern.dto.PatternSuggestionApplyResponse;
import com.naleul.naleul.domain.pattern.enums.PatternPeriod;
import com.naleul.naleul.domain.pattern.enums.PatternPeriodParser;
import com.naleul.naleul.domain.pattern.service.PatternReportService;
import com.naleul.naleul.domain.pattern.service.PatternSuggestionService;
import com.naleul.naleul.global.common.response.ApiResponse;
import com.naleul.naleul.global.common.response.SuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 나의 패턴 (명세 4 · 7장) */
@RestController
@RequestMapping("/api/v1/patterns")
@RequiredArgsConstructor
public class PatternController {

    private final PatternReportService patternReportService;
    private final PatternSuggestionService patternSuggestionService;

    /**
     * 패턴 리포트. period = RECENT_4W(기본) | ALL
     * refresh=true 면 캐시를 무시하고 지금 다시 계산 (평소에는 기록이 바뀌었을 때만 다시 계산)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PatternReportResponse>> getReport(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String period,
            @RequestParam(required = false, defaultValue = "false") boolean refresh
    ) {
        PatternPeriod p = PatternPeriodParser.parse(period);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.PATTERN_REPORT_FOUND,
                patternReportService.getReport(userId, p, refresh)));
    }

    /**
     * 제안을 TimeBlock 에 반영 (내일부터). key 예: ROUTINE_TIME:31:12:40 — 프론트는 encodeURIComponent 로 보낸다.
     * 404 PATTERN_SUGGESTION_NOT_FOUND, 409 PATTERN_SUGGESTION_STALE (루틴이 이미 바뀜·끝남·겹침)
     */
    @PostMapping("/suggestions/{key}/apply")
    public ResponseEntity<ApiResponse<PatternSuggestionApplyResponse>> apply(
            @AuthenticationPrincipal Long userId,
            @PathVariable String key
    ) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.PATTERN_SUGGESTION_APPLIED,
                patternSuggestionService.apply(userId, key)));
    }

    /** "다음에" — 7일 동안 같은 제안을 만들지 않음 */
    @PostMapping("/suggestions/{key}/dismiss")
    public ResponseEntity<ApiResponse<Void>> dismiss(
            @AuthenticationPrincipal Long userId,
            @PathVariable String key
    ) {
        patternSuggestionService.dismiss(userId, key);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.PATTERN_SUGGESTION_DISMISSED));
    }
}
