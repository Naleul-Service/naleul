package com.naleul.naleul.domain.planner.support;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * TimeBlock 배치 기능의 "오늘/지금" 기준.
 *
 * task.planned_start_at 은 한국 시간 벽시계 값으로 저장하므로(명세 3 · 2장),
 * 비교에 쓰는 "지금"도 JVM 시간대(EC2는 UTC일 수 있음)가 아니라 Asia/Seoul 로 구한다.
 * GoalClock.now()(JVM 시간대, created_at 비교용)와 용도가 다르니 섞어 쓰지 않는다.
 * 테스트에서는 mock 으로 날짜를 고정한다.
 */
@Component
public class PlannerClock {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public LocalDate today() {
        return LocalDate.now(KST);
    }

    public LocalDateTime now() {
        return LocalDateTime.now(KST);
    }
}
