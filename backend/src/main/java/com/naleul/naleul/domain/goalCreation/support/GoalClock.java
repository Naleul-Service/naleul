package com.naleul.naleul.domain.goalCreation.support;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 목표 생성 기능의 "오늘/지금" 기준.
 *
 * 왜 따로 두나
 *  - EC2의 JVM 시간대가 UTC면 LocalDate.now()는 한국 날짜와 하루 어긋날 수 있다
 *    (한국 오전 8시 = UTC 전날 23시). 사용자 기준 날짜는 항상 Asia/Seoul로 계산한다.
 *  - DB의 created_at(@CreationTimestamp)은 JVM 기본 시간대로 저장되므로,
 *    "오늘 0시 이후 생성된 세션 수" 같은 비교값은 JVM 시간대로 다시 바꿔야 한다.
 *  - 테스트에서 mock으로 날짜를 고정할 수 있다.
 */
@Component
public class GoalClock {

    private static final ZoneId USER_ZONE = ZoneId.of("Asia/Seoul");

    /** 사용자 기준 오늘 (AI 프롬프트, 날짜 계산용) */
    public LocalDate today() {
        return LocalDate.now(USER_ZONE);
    }

    /** DB 저장/비교용 현재 시각 (JVM 시간대) */
    public LocalDateTime now() {
        return LocalDateTime.now();
    }

    /** 사용자 기준 오늘 0시를 JVM 시간대로 바꾼 값 (일일 한도 집계 시작점) */
    public LocalDateTime startOfUserToday() {
        return ZonedDateTime.now(USER_ZONE)
                .truncatedTo(ChronoUnit.DAYS)
                .withZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime();
    }

    /** 사용자 기준 이번 주 월요일 0시를 JVM 시간대로 (주간 한도 집계 시작점) */
    public LocalDateTime startOfUserWeek() {
        ZonedDateTime today = ZonedDateTime.now(USER_ZONE).truncatedTo(ChronoUnit.DAYS);
        return today.minusDays(today.getDayOfWeek().getValue() - 1L)
                .withZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime();
    }

    /** 이번 달 1일 0시 (한국 시간) — 월 한도의 시작 */
    public LocalDateTime startOfUserMonth() {
        return ZonedDateTime.now(USER_ZONE).truncatedTo(ChronoUnit.DAYS).withDayOfMonth(1)
                .withZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime();
    }
}
