package com.naleul.naleul.domain.webBilling.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.naleul.naleul.domain.webBilling.config.TossPayProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 토스페이 자동결제(정기결제) API — https://docs-pay.toss.im/reference/billing
 *  - POST /api/v1/billing-key          빌링키 생성 → 토스 앱 인증 주소(checkoutUri)
 *  - POST /api/v1/billing-key/status   빌링키 상태 (ACTIVE 면 결제할 수 있음)
 *  - POST /api/v1/billing-key/bill     빌링키로 결제
 *  - POST /api/v1/billing-key/remove   빌링키 삭제 (더 결제하지 않음)
 * 인증: 요청 본문의 apiKey. 응답 code 0 = 성공, -1 = 실패(errorCode · msg).
 * 실패는 토스페이먼츠와 같은 TossPaymentsException 으로 던져서 결제 흐름을 함께 쓴다.
 */
@Slf4j
@Component
public class TossPayClient {

    /** 토스페이 승인 시각은 한국 시간 문자열 — Pro 만료 비교와 같은 JVM 시간대로 바꾼다 */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final DateTimeFormatter APPROVAL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TossPayProperties properties;
    private final RestClient restClient;

    public TossPayClient(TossPayProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.restClient = RestClient.builder()
                .baseUrl(properties.apiBaseUrl())
                .requestFactory(factory)
                .build();
    }

    // ── 결과 ──

    /** checkoutUri: 웹에서 열 토스 결제 인증 주소 (PC 는 QR, 모바일은 토스 앱으로) */
    public record Created(String billingKey, String checkoutUri) {
    }

    /** 토스 앱에서 고른 결제수단. label 예) "신한카드" · "토스머니" / number 예) 마스킹된 카드번호 */
    public record KeyStatus(String billingKey, String status, String payMethod, String label, String number) {
        public boolean active() {
            return "ACTIVE".equals(status);
        }

        /** 사용자가 토스 앱에서 해지했거나 토스가 지운 상태 */
        public boolean removed() {
            return "REMOVE".equals(status) || "CANCEL".equals(status) || "FAIL".equals(status);
        }
    }

    public record Bill(String payToken, LocalDateTime approvedAt) {
    }

    // ── 응답 본문 ──

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Base(Integer code, String errorCode, String msg) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CreateResponse(Integer code, String errorCode, String msg, String billingKey, String checkoutUri) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record StatusResponse(Integer code, String errorCode, String msg, String billingKey, String status,
                          String payMethod, String cardCompanyName, String cardNumber, String cardNum4Print,
                          String accountBankName, String accountNumber) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record BillResponse(Integer code, String errorCode, String msg, String payToken, String approvalTime) {
    }

    // ── API ──

    public Created createBillingKey(String userId, String productDesc, String successUrl, String failureUrl) {
        Map<String, Object> body = base();
        body.put("userId", userId);
        body.put("productDesc", productDesc);
        body.put("resultCallback", properties.callbackUrl());
        body.put("returnSuccessUrl", successUrl);
        body.put("returnFailureUrl", failureUrl);
        CreateResponse r = post("/api/v1/billing-key", body, CreateResponse.class);
        if (r.billingKey() == null || r.checkoutUri() == null) {
            throw new TossPaymentsException("NO_CHECKOUT", "토스페이 인증을 시작하지 못했어요.", false);
        }
        return new Created(r.billingKey(), r.checkoutUri());
    }

    public KeyStatus status(String userId) {
        Map<String, Object> body = base();
        body.put("userId", userId);
        StatusResponse r = post("/api/v1/billing-key/status", body, StatusResponse.class);
        boolean card = "CARD".equals(r.payMethod());
        String label = card ? r.cardCompanyName() : (r.accountBankName() != null ? r.accountBankName() : "토스머니");
        String number = card ? (r.cardNumber() != null ? r.cardNumber() : r.cardNum4Print()) : r.accountNumber();
        return new KeyStatus(r.billingKey(), r.status(), r.payMethod(), label, number);
    }

    /** orderNo 는 가맹점 안에서 유일해야 해요 (같은 번호로 두 번 결제되지 않음) */
    public Bill bill(String billingKey, String orderNo, String productDesc, long amount) {
        Map<String, Object> body = base();
        body.put("billingKey", billingKey);
        body.put("orderNo", orderNo);
        body.put("productDesc", productDesc);
        body.put("spreadOut", 0);              // 일시불
        body.put("amount", amount);
        body.put("amountTaxFree", 0);
        body.put("cashReceipt", true);
        body.put("sendFailPush", true);        // 결제 실패하면 토스가 사용자에게 알림
        body.put("cashReceiptTradeOption", "GENERAL");
        BillResponse r = post("/api/v1/billing-key/bill", body, BillResponse.class);
        LocalDateTime approvedAt = LocalDateTime.now(ZONE);
        if (r.approvalTime() != null) {
            try {
                approvedAt = LocalDateTime.parse(r.approvalTime(), APPROVAL_TIME).atZone(KST)
                        .withZoneSameInstant(ZONE).toLocalDateTime();
            } catch (RuntimeException ignored) {
                // 형식이 바뀌어도 결제는 성공으로 처리
            }
        }
        return new Bill(r.payToken(), approvedAt);
    }

    public void removeBillingKey(String billingKey) {
        Map<String, Object> body = base();
        body.put("billingKey", billingKey);
        post("/api/v1/billing-key/remove", body, Base.class);
    }

    private Map<String, Object> base() {
        if (!properties.configured()) {
            throw new TossPaymentsException("NOT_CONFIGURED", "토스페이 결제 설정이 아직 없어요.", false);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("apiKey", properties.apiKey());
        return body;
    }

    /** code != 0 이면 실패. 로그에는 빌링키 · apiKey 를 남기지 않는다 */
    private <T> T post(String path, Map<String, Object> body, Class<T> type) {
        try {
            T r = restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(type);
            if (r == null) throw new TossPaymentsException("EMPTY", "토스페이 응답이 비어 있어요.", true);
            Base b = toBase(r);
            if (b.code() == null || b.code() != 0) {
                log.warn("[TossPay] {} 실패 errorCode={} msg={}", path, b.errorCode(), b.msg());
                throw new TossPaymentsException(b.errorCode() != null ? b.errorCode() : "UNKNOWN",
                        b.msg() != null && !b.msg().isBlank() ? b.msg() : "토스페이 결제에 실패했어요.", false);
            }
            return r;
        } catch (ResourceAccessException e) {
            log.warn("[TossPay] 응답 없음 {} : {}", path, e.getMessage());
            throw new TossPaymentsException("NETWORK", "토스페이와 연결하지 못했어요. 잠시 후 다시 시도해 주세요.", true);
        } catch (RestClientResponseException e) {
            log.warn("[TossPay] {} HTTP {}", path, e.getStatusCode().value());
            throw new TossPaymentsException("HTTP_" + e.getStatusCode().value(), "토스페이 결제에 실패했어요.",
                    e.getStatusCode().is5xxServerError());
        }
    }

    private static Base toBase(Object r) {
        if (r instanceof Base b) return b;
        if (r instanceof CreateResponse c) return new Base(c.code(), c.errorCode(), c.msg());
        if (r instanceof StatusResponse s) return new Base(s.code(), s.errorCode(), s.msg());
        if (r instanceof BillResponse b) return new Base(b.code(), b.errorCode(), b.msg());
        return new Base(null, "UNKNOWN", null);
    }
}
