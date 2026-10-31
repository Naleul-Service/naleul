package com.naleul.naleul.domain.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 서버 시작 시 둘러보기 계정 준비. 실패해도 서버는 그대로 뜬다 (첫 요청 때 다시 만든다) */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuestAccountInitializer {

    private final GuestAccountService guestAccountService;

    @EventListener(ApplicationReadyEvent.class)
    public void prepare() {
        try {
            guestAccountService.provisionDefaults(guestAccountService.ensureGuest());
        } catch (RuntimeException e) {
            log.warn("[Guest] 둘러보기 계정 준비 실패 (첫 요청 때 다시 시도) : {}", e.getMessage());
        }
    }
}
