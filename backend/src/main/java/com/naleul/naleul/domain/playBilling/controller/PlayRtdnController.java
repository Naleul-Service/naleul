package com.naleul.naleul.domain.playBilling.controller;

import com.naleul.naleul.domain.playBilling.service.PlayBillingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Google Play 실시간 개발자 알림 (Cloud Pub/Sub push 구독의 엔드포인트) — 로그인 없음.
 * Pub/Sub push 주소: https://{서버}/api/v1/play-billing/rtdn?token={google.play.rtdn-token}
 * 2xx 를 돌려주면 받은 것으로 쳐요. 서비스가 예외를 던지면 500 → Pub/Sub 이 다시 보내요.
 * 본문은 믿지 않고 구매 토큰으로 Play 에 다시 조회한 결과만 반영해요.
 */
@RestController
@RequiredArgsConstructor
public class PlayRtdnController {

    private final PlayBillingService service;

    @PostMapping("/api/v1/play-billing/rtdn")
    public ResponseEntity<Void> receive(@RequestParam(value = "token", required = false) String token,
                                        @RequestBody String body) {
        service.handleRtdn(token, body);
        return ResponseEntity.noContent().build();
    }
}
