package com.naleul.naleul.domain.fcm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.naleul.naleul.domain.fcm.repository.DeviceTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class FcmService {

    private final DeviceTokenRepository deviceTokenRepository;
    private final GoogleCredentials firebaseCredentials;
    private final String firebaseProjectId;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void sendToDevice(String fcmToken, String title, String body, Map<String, String> data) {
        sendSingle(fcmToken, title, body, data);
    }

    public void sendToDevices(List<String> fcmTokens, String title, String body, Map<String, String> data) {
        for (String token : fcmTokens) {
            sendSingle(token, title, body, data);
        }
    }

    private void sendSingle(String fcmToken, String title, String body, Map<String, String> data) {
        try {
            String accessToken = getAccessToken();
            String url = "https://fcm.googleapis.com/v1/projects/" + firebaseProjectId + "/messages:send";

            Map<String, Object> notification = new HashMap<>();
            notification.put("title", title);
            notification.put("body", body);

            Map<String, Object> message = new HashMap<>();
            message.put("token", fcmToken);
            message.put("notification", notification);
            if (data != null) {
                message.put("data", data);
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("message", message);

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(payload), headers);

            String response = restTemplate.postForObject(url, request, String.class);
            log.info("FCM 전송 성공: {}", response);

        } catch (HttpClientErrorException e) {
            log.error("FCM 전송 실패 - status: {}, body: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());

            String responseBody = e.getResponseBodyAsString();
            if (e.getStatusCode() == HttpStatus.NOT_FOUND || responseBody.contains("UNREGISTERED")
                    || responseBody.contains("INVALID_ARGUMENT")) {
                deviceTokenRepository.deleteByFcmToken(fcmToken);
            }
        } catch (Exception e) {
            log.error("FCM 전송 중 알 수 없는 에러", e);
        }
    }

    private String getAccessToken() throws Exception {
        firebaseCredentials.refreshIfExpired();
        return firebaseCredentials.getAccessToken().getTokenValue();
    }
}