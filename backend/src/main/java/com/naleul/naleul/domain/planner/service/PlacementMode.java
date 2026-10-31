package com.naleul.naleul.domain.planner.service;

public enum PlacementMode {
    /** 시간 없는 Task 만 빈 시간에 넣는다. 이미 배치된 블록은 그대로 (Brain dump 확정, AI 목표 확정) */
    FILL,
    /** 잠기지 않은 블록을 모두 비우고 다시 배치 ("다시 배치", 22시 생성) */
    REPLAN,
    /** 고정 시간·하루 범위와 충돌하는 엔진 블록만 다시 배치 (생활 패턴 변경 후) */
    REPAIR
}
