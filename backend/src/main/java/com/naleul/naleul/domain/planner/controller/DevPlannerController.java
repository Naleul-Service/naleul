package com.naleul.naleul.domain.planner.controller;

import com.naleul.naleul.domain.planner.service.NightlyPlanWorker;
import com.naleul.naleul.domain.planner.support.PlannerClock;
import com.naleul.naleul.global.common.response.ApiResponse;
import com.naleul.naleul.global.common.response.SuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * ⚠️ 로컬 전용 (local 프로필에서만 등록 — 운영 서버에는 이 주소가 아예 없다).
 * 22시를 기다리지 않고 "내 계정"의 내일 TimeTable 생성을 바로 실행해 본다.
 *
 * POST /api/v1/dev/planner/nightly            오늘 기준 (이미 만들었어도 다시)
 * POST /api/v1/dev/planner/nightly?today=2026-11-18   특정 날짜를 오늘로 가정
 * (푸시는 보내지 않는다)
 */
@Profile("local")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/dev/planner")
public class DevPlannerController {

    private final NightlyPlanWorker worker;
    private final PlannerClock clock;

    @PostMapping("/nightly")
    public ResponseEntity<ApiResponse<NightlyPlanWorker.Result>> runNightly(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate today) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.OK,
                worker.run(userId, today != null ? today : clock.today(), true)));
    }
}
