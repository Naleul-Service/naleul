// task/enums/TaskSourceType.java
package com.naleul.naleul.domain.task.enums;

/**
 * Task가 어떤 경로로 생성되었는지 구분하는 타입.
 * - MANUAL: 사용자가 "할 일 추가"로 직접 생성
 * - ROUTINE: 루틴 등록 시 자동 생성됨
 * - MISSION: 미션 참여 시 자동 생성됨
 */
public enum TaskSourceType {
    MANUAL,
    ROUTINE,
    MISSION
}