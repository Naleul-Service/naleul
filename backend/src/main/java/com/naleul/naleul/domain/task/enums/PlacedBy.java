package com.naleul.naleul.domain.task.enums;

/** Task의 시간이 누구에 의해 정해졌는지 */
public enum PlacedBy {
    USER,   // 사용자가 직접 정함 (잠금)
    ENGINE  // 배치 엔진이 정함 (다시 배치될 수 있음)
}
