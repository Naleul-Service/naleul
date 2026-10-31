package com.naleul.naleul.domain.purchase.controller;

import com.naleul.naleul.domain.purchase.service.IapNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/iap/notifications")
@RequiredArgsConstructor
public class AppStoreNotificationController {

    private final IapNotificationService iapNotificationService;

    @PostMapping(value = "/v2", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> receiveNotification(@RequestBody Map<String, String> body) {
        String signedPayload = body.get("signedPayload");
        iapNotificationService.handle(signedPayload);
        return ResponseEntity.ok().build();
    }
}