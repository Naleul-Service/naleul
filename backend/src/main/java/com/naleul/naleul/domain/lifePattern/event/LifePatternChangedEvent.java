package com.naleul.naleul.domain.lifePattern.event;

import java.time.LocalDate;

/**
 * 고정 시간이 바뀜 (추가·수정·삭제·그날만 변경).
 * BE-3/4 에서 리스너가 "fromDate ~ toDate 의 자동 배치 블록 중 새 고정 시간과 겹치는 것"을 다시 배치한다.
 * 리스너는 @TransactionalEventListener(AFTER_COMMIT) 로 받아야 롤백된 변경에 반응하지 않는다.
 */
public record LifePatternChangedEvent(Long userId, LocalDate fromDate, LocalDate toDate) {
}
