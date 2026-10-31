package com.naleul.naleul.domain.goalCreation.event;

/**
 * AI 목표 확정 완료 이벤트 (명세 5-2의 6번).
 * TimeBlock 자동 배치(3번 명세) 등 후속 처리가 이 이벤트를 듣는다.
 * 듣는 쪽은 반드시 @TransactionalEventListener(phase = AFTER_COMMIT)로 받아야
 * 확정 트랜잭션이 롤백됐을 때 잘못 처리하지 않는다.
 */
public record GoalCreatedEvent(Long goalCategoryId, Long userId) {
}
