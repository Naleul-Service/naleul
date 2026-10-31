package com.naleul.naleul.domain.work.enums;

/**
 * 업무형 Task 보드의 칸 (V52) — 노션 보드처럼 시작 전 · 진행 중 · 완료.
 * DB 에는 "시작 전 / 진행 중"만 task.work_status 에 저장하고, 완료는 기존 task_status = COMPLETED 를 그대로 쓴다
 * (캘린더 · 통계 · 업무 일지가 모두 task_status 로 완료를 세기 때문에 완료 기준을 하나로 둔다).
 */
public enum WorkBoardColumn {
    NOT_STARTED,
    IN_PROGRESS,
    DONE
}
