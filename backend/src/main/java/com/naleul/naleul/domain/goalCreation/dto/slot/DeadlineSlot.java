package com.naleul.naleul.domain.goalCreation.dto.slot;

import java.time.LocalDate;

/**
 * @param endDate    목표 종료일
 * @param isFlexible true: 사용자가 기한을 말하지 않아 AI가 제안한 기간
 * @param startDate  사용자가 말한 시작일 ("10월 29일부터", "다음 달부터"). 말하지 않았으면 null → 오늘부터.
 *                   루틴 · Task 가 이 날부터 생긴다.
 */
public record DeadlineSlot(LocalDate endDate, boolean isFlexible, LocalDate startDate) {

    public DeadlineSlot(LocalDate endDate, boolean isFlexible) {
        this(endDate, isFlexible, null);
    }
}
