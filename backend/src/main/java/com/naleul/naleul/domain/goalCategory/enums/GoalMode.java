package com.naleul.naleul.domain.goalCategory.enums;

/**
 * 목표 형태 (V39, V52).
 * <ul>
 *   <li>ACHIEVEMENT — 목표형: 수치·기간이 있는 목표 (AI 설계 포함). 진행 그래프(여러 개 가능)로 "오늘 어디까지"를 보여준다.</li>
 *   <li>RECORD — 업무형: 회사 업무·사이드 프로젝트처럼 마감(선택)과 Task 관리가 중심인 목표.
 *       수치 없이 주제별 Task 보드(시작 전 · 진행 중 · 완료)와 완료 아카이브 · 회고를 쌓는다. 마감일이 지나도 자동 완료되지 않는다.</li>
 *   <li>HABIT — 기본 생활 습관 (V52): 사용자마다 1개씩 자동으로 생기는 그릇.
 *       영양제 먹기 · 샤워하기 같은 "했는지 안 했는지"만 체크하는 습관(habit)을 담는다. 종료일·수치 없음,
 *       Free 목표 개수 한도 · 자동 만료 · 목표 결과 판정 대상이 아니다.</li>
 * </ul>
 */
public enum GoalMode {
    ACHIEVEMENT,
    RECORD,
    HABIT
}
