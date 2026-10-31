package com.naleul.naleul.domain.webBilling.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.naleul.naleul.domain.webBilling.config.TossPaymentsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 토스페이먼츠 자동결제(빌링) API.
 *  - POST /v1/billing/authorizations/issue : 카드 등록 후 받은 authKey → billingKey
 *  - POST /v1/billing/{billingKey}         : billingKey 로 결제 (Idempotency-Key = 주문번호)
 * 인증: Basic base64("시크릿키:")
 */
@Slf4j
@Component
public class TossPaymentsClient {

    /** Pro 만료 비교(User.isCurrentlyPremium · IAP)가 JVM 시간대 LocalDateTime 이라 같은 기준으로 */
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final TossPaymentsProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TossPaymentsClient(TossPaymentsProperties properties) {
        this.properties = properties;
        // 기동 때 키 종류만 남겨요 (test_sk_ 여야 웹의 test_ck_ 와 짝이 맞아요)
        log.info("[Toss] 결제 키: {}", properties.keyKind());
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(30));   // 결제 승인은 카드사 응답을 기다려서 넉넉히
        this.restClient = RestClient.builder()
                .baseUrl(properties.apiBaseUrl())
                .requestFactory(factory)
                .build();
    }

    public record BillingKey(String billingKey, String cardCompany, String cardNumber) {
    }

    public record Charge(String paymentKey, String status, LocalDateTime approvedAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record IssueResponse(String billingKey, String cardCompany, String cardNumber) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PaymentResponse(String paymentKey, String status, String approvedAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ErrorResponse(String code, String message) {
    }

    public BillingKey issueBillingKey(String authKey, String customerKey) {
        IssueResponse r = post("/v1/billing/authorizations/issue",
                Map.of("authKey", authKey, "customerKey", customerKey), null, IssueResponse.class);
        if (r == null || r.billingKey() == null) {
            throw new TossPaymentsException("NO_BILLING_KEY", "카드 등록 결과를 받지 못했어요.", false);
        }
        return new BillingKey(r.billingKey(), r.cardCompany(), r.cardNumber());
    }

    public Charge charge(String billingKey, String customerKey, long amount, String orderId, String orderName,
                         String customerName, String customerEmail) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerKey", customerKey);
        body.put("amount", amount);
        body.put("orderId", orderId);
        body.put("orderName", orderName);
        if (customerName != null && !customerName.isBlank()) body.put("customerName", customerName);
        if (customerEmail != null && !customerEmail.isBlank()) body.put("customerEmail", customerEmail);

        PaymentResponse r = post("/v1/billing/" + billingKey, body, orderId, PaymentResponse.class);
        if (r == null || !"DONE".equals(r.status())) {
            throw new TossPaymentsException("NOT_DONE", "결제가 완료되지 않았어요.", false);
        }
        LocalDateTime approvedAt = r.approvedAt() != null
                ? OffsetDateTime.parse(r.approvedAt()).atZoneSameInstant(ZONE).toLocalDateTime()
                : LocalDateTime.now(ZONE);
        return new Charge(r.paymentKey(), r.status(), approvedAt);
    }

    private <T> T post(String path, Object body, String idempotencyKey, Class<T> type) {
        if (!properties.configured()) {
            throw new TossPaymentsException("NOT_CONFIGURED", "결제 설정이 아직 없어요.", false);
        }
        try {
            return restClient.post()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, basicAuth())
                    .headers(h -> {
                        if (idempotencyKey != null) h.set("Idempotency-Key", idempotencyKey);
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(s -> s.isError(), (req, res) -> {
                        ErrorResponse e = readError(res.getBody().readAllBytes(), res.getStatusCode().value());
                        log.warn("[Toss] {} 실패 status={} code={} message={}", path.startsWith("/v1/billing/a") ? "billingKey 발급" : "결제",
                                res.getStatusCode().value(), e.code(), e.message());
                        throw new TossPaymentsException(e.code(), e.message(), res.getStatusCode().is5xxServerError());
                    })
                    .body(type);
        } catch (ResourceAccessException e) {
            log.warn("[Toss] 응답 없음 path={} : {}", path.startsWith("/v1/billing/a") ? path : "/v1/billing/{key}", e.getMessage());
            throw new TossPaymentsException("NETWORK", "결제사와 연결하지 못했어요. 잠시 후 다시 시도해 주세요.", true);
        }
    }

    /**
     * 토스 오류 응답 {"code": "...", "message": "..."} 읽기 (2026-10-28).
     * 예전엔 record 로 바로 읽다가 실패하면 원래 이유를 버리고 "결제에 실패했어요"만 남아서 원인을 알 수 없었다
     * → JsonNode 로 읽고, 못 읽으면 응답 본문 앞부분을 로그에 남긴다.
     */
    private ErrorResponse readError(byte[] bytes, int status) {
        String raw = bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
        try {
            com.fasterxml.jackson.databind.JsonNode n = objectMapper.readTree(raw);
            String code = n.path("code").asText(null);
            String message = n.path("message").asText(null);
            if (code != null && !code.isBlank()) {
                return new ErrorResponse(code, message != null && !message.isBlank() ? message : "결제에 실패했어요.");
            }
        } catch (Exception ignored) {
            // 아래에서 본문을 남긴다
        }
        log.warn("[Toss] 읽을 수 없는 오류 응답 status={} body={} key={}", status,
                raw.length() > 500 ? raw.substring(0, 500) : raw, properties.keyKind());
        if (status == 401) {
            // 시크릿 키가 틀림 — 웹 클라이언트 키(test_ck_)와 같은 상점의 API 개별 연동 시크릿 키(test_sk_)여야 해요
            return new ErrorResponse("UNAUTHORIZED_KEY", "결제 설정(시크릿 키)이 맞지 않아요. 운영진에게 알려 주세요.");
        }
        return new ErrorResponse("HTTP_" + status, "결제사 응답 오류(" + status + ")로 결제에 실패했어요. 잠시 후 다시 시도해 주세요.");
    }

    private String basicAuth() {
        return "Basic " + Base64.getEncoder()
                .encodeToString((properties.secretKey() + ":").getBytes(StandardCharsets.UTF_8));
    }
}
