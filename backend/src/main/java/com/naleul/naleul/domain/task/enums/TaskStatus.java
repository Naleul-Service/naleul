package com.naleul.naleul.domain.task.enums;

// Task에는 상태가 없었는데, 루틴 Task 완료 처리를 위해 필요함
public enum TaskStatus {
    TODO,        // 아직 안 함
    COMPLETED,   // 완료
    SKIPPED,     // 건너뜀
    DELETED      // 삭제
}