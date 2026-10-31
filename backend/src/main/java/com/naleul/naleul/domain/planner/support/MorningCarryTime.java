package com.naleul.naleul.domain.planner.support;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 아침 이월 시각 (2026-10) — 어제 못 한 일을 오늘로 옮기는 때.
 *
 * 저녁(마지막 일정 + 60분 · 23:50)에 옮기던 예전 방식은 아직 하던 일 · 새벽까지 할 일이 갑자기 내일로 사라졌다.
 * 그래서 "하루가 확실히 끝난 뒤" = 다음 날 기상 시간에 옮긴다.
 *  - 기본 생활 패턴의 수면이 있으면 그날 기상 시각 (예: 07:30)
 *  - 없으면 DEFAULT_WAKE (07:00)
 *  - EARLIEST(04:00) ~ LATEST(11:00) 사이로 맞춘다 — 새벽 4시 전엔 아직 어제 일을 하고 있을 수 있고,
 *    늦잠 패턴이어도 오전 중에는 옮겨 두어야 그날 배치에 들어간다
 */
public final class MorningCarryTime {

    public static final LocalTime EARLIEST = LocalTime.of(4, 0);
    public static final LocalTime LATEST = LocalTime.of(11, 0);
    public static final LocalTime DEFAULT_WAKE = LocalTime.of(7, 0);

    private MorningCarryTime() {
    }

    /** @param wake 오늘 아침 기상 시각 (수면 패턴의 끝). 없으면 null */
    public static LocalDateTime at(LocalDate today, LocalDateTime wake) {
        LocalTime t = wake != null && wake.toLocalDate().equals(today) ? wake.toLocalTime() : DEFAULT_WAKE;
        if (t.isBefore(EARLIEST)) t = EARLIEST;
        if (t.isAfter(LATEST)) t = LATEST;
        return today.atTime(t);
    }
}
