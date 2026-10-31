package com.naleul.naleul.domain.planner.enums;

public enum DailyPlanStatus {
    GENERATING, // 배치 진행 중 (22시 작업이 행을 먼저 넣고 시작 → 다른 서버는 UNIQUE 충돌로 건너뜀)
    GENERATED,  // 생성 완료, 사용자 미확인
    REVIEWED,   // 사용자가 "이대로 진행하기"
    FAILED      // 배치 실패 (다음 22시 작업에서 다시 시도)
}
