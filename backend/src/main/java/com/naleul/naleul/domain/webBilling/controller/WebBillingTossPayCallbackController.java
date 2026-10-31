package com.naleul.naleul.domain.webBilling.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.naleul.naleul.domain.webBilling.service.WebBillingService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * 토스페이 빌링키 처리결과 콜백 (resultCallback) — 토스가 서버로 직접 보내서 로그인 없음.
 *  - 200 을 돌려줘야 받은 것으로 쳐요 (아니면 3분마다 최대 4번 다시 보냄)
 *  - JSON · form 어느 쪽으로 와도 읽어요. 처리 중 오류가 나도 200 (다시 보내도 결과가 같아서)
 *  - 본문은 믿지 않고 서비스에서 토스페이에 빌링키 상태를 다시 확인한 뒤 반영해요
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class WebBillingTossPayCallbackController {

    private final WebBillingService billingService;
    private final ObjectMapper objectMapper;

    @PostMapping("/api/v1/web-billing/toss-pay/callback")
    public ResponseEntity<Void> callback(HttpServletRequest request) {
        String action = request.getParameter("action");
        String billingKey = request.getParameter("billingKey");
        try {
            if (action == null && billingKey == null) {
                String body = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                if (!body.isBlank()) {
                    JsonNode json = objectMapper.readTree(body);
                    action = json.path("action").asText(null);
                    billingKey = json.path("billingKey").asText(null);
                }
            }
            billingService.handleTossPayCallback(action, billingKey);
        } catch (Exception e) {
            log.warn("[TossPay] 콜백 처리 오류 action={} : {}", action, e.getMessage());
        }
        return ResponseEntity.ok().build();
    }
}
