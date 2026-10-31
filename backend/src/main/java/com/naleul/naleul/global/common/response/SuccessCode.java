package com.naleul.naleul.global.common.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SuccessCode {
    // 공통
    OK(HttpStatus.OK, "요청이 성공했습니다."),
    CREATED(HttpStatus.CREATED, "생성이 완료되었습니다."),
    IMAGE_UPLOADED(HttpStatus.CREATED, "이미지 생성이 완료되었습니다."),
    IMAGE_DELETED(HttpStatus.CREATED, "이미지 삭제가 완료되었습니다."),
    DEVICE_TOKEN_SAVED(HttpStatus.OK, "디바이스 토큰이 저장되었습니다."),
    DEVICE_TOKEN_DELETED(HttpStatus.OK, "디바이스 토큰이 삭제되었습니다."),

    //결제
    PURCHASE_VERIFIED(HttpStatus.CREATED, "결제 검증이 완료되었습니다."),
    WEB_BILLING_FOUND(HttpStatus.OK, "웹 구독 정보를 불러왔어요."),
    WEB_BILLING_PREPARED(HttpStatus.OK, "카드 등록을 시작해요."),
    WEB_BILLING_SUBSCRIBED(HttpStatus.OK, "Pro 구독을 시작했어요."),
    WEB_BILLING_CANCELED(HttpStatus.OK, "구독을 해지했어요. 남은 기간까지 Pro를 이용할 수 있어요."),
    WEB_BILLING_RESUMED(HttpStatus.OK, "구독 해지를 취소했어요."),
    WEB_BILLING_TOSS_PAY_PREPARED(HttpStatus.OK, "토스페이 인증을 시작해요."),
    PLAY_BILLING_FOUND(HttpStatus.OK, "Google Play 구독 정보를 불러왔어요."),
    PLAY_BILLING_VERIFIED(HttpStatus.OK, "Google Play 결제를 확인했어요."),

    //통계
    USER_PATTERN_TYPE_UPDATED(HttpStatus.OK, "AI 패턴 유형이 설정되었습니다."),

    //미션
    MISSION_CREATED(HttpStatus.CREATED, "미션 생성 성공"),
    MISSION_JOINED(HttpStatus.OK, "미션 참여 신청 성공"),
    MISSION_FOUND(HttpStatus.OK, "미션 조회 성공"),
    MISSION_UPDATED(HttpStatus.OK, "미션이 업데이트되었습니다."),
    MISSION_TASK_COMPLETED(HttpStatus.OK, "미션 Task가 완료 처리되었습니다."),
    MISSION_DELETED(HttpStatus.OK, "미션 Task가 삭제되었습니다."),

    //알람
    NOTIFICATION_FOUND(HttpStatus.OK, "알림 목록을 조회했습니다."),
    NOTIFICATION_READ(HttpStatus.OK, "알림을 읽음 처리했습니다."),
    NOTIFICATION_SETTINGS_FOUND(HttpStatus.OK, "알림 설정을 조회했습니다."),
    NOTIFICATION_SETTINGS_UPDATED(HttpStatus.OK, "알림 설정을 수정했습니다."),
    NOTIFICATION_ALL_READ(HttpStatus.OK, "전체 알림을 읽음 처리했습니다."),

    // 유저
    USERS_FOUND(HttpStatus.OK, "전체 유저 조회 성공"),
    USER_FOUND(HttpStatus.OK, "유저 조회 성공"),
    USER_CREATED(HttpStatus.CREATED, "회원가입 성공"),
    LOGIN_SUCCESS(HttpStatus.OK, "로그인 성공"),
    CHARACTER_FOUND(HttpStatus.OK, "캐릭터 조회 성공"),
    CHARACTER_UPDATED(HttpStatus.OK, "캐릭터가 변경되었습니다."),
    TERMS_AGREED(HttpStatus.OK, "약관 동의가 완료되었습니다."),
    USER_NICKNAME_UPDATED(HttpStatus.OK, "닉네임이 수정되었습니다."),

    // 자서전
    AUTOBIOGRAPHY_QUESTION_CREATED(HttpStatus.CREATED, "질문이 생성되었습니다."),
    AUTOBIOGRAPHY_QUESTION_FOUND(HttpStatus.FOUND, "질문 조회 성공"),
    AUTOBIOGRAPHY_QUESTION_UPDATED(HttpStatus.OK, "질문이 수정되었습니다."),
    AUTOBIOGRAPHY_QUESTION_DELETED(HttpStatus.OK, "질문이 삭제되었습니다."),
    AUTOBIOGRAPHY_ANSWER_CREATED(HttpStatus.OK, "답변이 생성되었습니다."),
    AUTOBIOGRAPHY_ANSWER_UPDATED(HttpStatus.OK, "답변이 수정되었습니다."),
    AUTOBIOGRAPHY_ANSWER_DELETED(HttpStatus.OK, "답변이 삭제되었습니다."),

    //차트
    CHART_FOUND(HttpStatus.OK, "차트 조회 성공"),

    // 컬러
    COLOR_CREATED(HttpStatus.CREATED, "색상 저장 성공"),
    COLOR_FOUND(HttpStatus.FOUND, "색상 조회 성공"),
    COLORS_FOUND(HttpStatus.FOUND, "전체 색상 조회 성공"),
    COLOR_DELETED(HttpStatus.OK, "색상이 삭제되었습니다."),

    // 아이디어
    IDEA_CREATED(HttpStatus.CREATED, "아이디어가 생성되었습니다."),
    IDEA_FOUND(HttpStatus.FOUND, "아이디어 조회 성공"),
    IDEA_UPDATED(HttpStatus.OK, "아이디어가 수정되었습니다."),
    IDEA_DELETED(HttpStatus.OK, "아이디어가 삭제되었습니다."),

    // 드림 보드
    DREAM_BOARD_CREATED(HttpStatus.CREATED, "드림보드가 생성되었습니다."),
    DREAM_BOARD_FOUND(HttpStatus.FOUND, "드림보드 조회 성공"),
    DREAM_BOARD_UPDATED(HttpStatus.OK, "드림보드가 수정되었습니다."),
    DREAM_BOARD_DELETED(HttpStatus.OK, "드림보드가 삭제되었습니다."),

    // 목표
    GOAL_CREATED(HttpStatus.CREATED, "목표 카테고리 생성 성공"),
    GOAL_FOUND(HttpStatus.OK, "목표 카테고리 조회 성공"),
    GOAL_UPDATED(HttpStatus.OK, "목표 카테고리 수정 성공"),
    GOAL_DELETED(HttpStatus.OK, "목표 카테고리 삭제 성공"),
    GOAL_COMPLETED(HttpStatus.OK, "목표 카테고리 완료 처리 성공"),
    GOAL_ACHIEVEMENT_FILLED(HttpStatus.OK, "목표 달성 내용이 입력되었습니다."),
    GOAL_ASSIGNED(HttpStatus.OK, "일반 카테고리 연결 성공"),
    GOAL_CATEGORIES_FOUND(HttpStatus.OK, "완료된 목표 조회 성공"),

    // 회고
    RETROSPECTIVE_CREATED(HttpStatus.CREATED, "회고 생성 성공"),
    RETROSPECTIVE_FOUND(HttpStatus.OK, "회고 조회 성공"),
    RETROSPECTIVES_FOUND(HttpStatus.OK, "회고 전체 조회 성공"),
    RETROSPECTIVE_UPDATED(HttpStatus.OK, "회고 수정 성공"),
    RETROSPECTIVE_DELETED(HttpStatus.OK, "회고 삭제 성공"),

    // 일반 카테고리
    GENERAL_CREATED(HttpStatus.CREATED, "일반 카테고리 생성 성공"),
    GENERALS_FOUND(HttpStatus.OK, "일반 카테고리 전체 조회 성공"),
    GENERAL_FOUND(HttpStatus.OK, "일반 카테고리 조회 성공"),
    GENERAL_COMPLETED(HttpStatus.OK, "일반 카테고리 완료 처리 성공"),
    GENERAL_UPDATED(HttpStatus.OK, "일반 카테고리 수정 성공"),
    GENERAL_DELETED(HttpStatus.OK, "일반 카테고리 삭제 성공"),

    // 할 일 카테고리
    TASK_CREATED(HttpStatus.CREATED, "할 일 생성 성공"),
    EXTERNAL_EVENTS_IMPORTED(HttpStatus.OK, "외부 일정을 가져왔어요."),
    TASKS_FOUND(HttpStatus.OK, "할 일 전체 조회 성공"),
    TASK_FOUND(HttpStatus.OK, "할 일 조회 성공"),
    TASK_UPDATED(HttpStatus.OK, "할 일 수정 성공"),
    TASK_COMPLETED(HttpStatus.OK, "할 일 실제 기록 성공"),
    TASK_DELETED(HttpStatus.OK, "할 일 삭제 성공"),

    // 실제로 한 일 카테고리
    ACTUAL_TASK_CREATED(HttpStatus.CREATED, "실제 할 일 생성 성공"),
    ACTUAL_TASK_UPDATED(HttpStatus.OK, "실제 기록이 수정되었습니다."),
    ACTUAL_TASK_DELETED(HttpStatus.OK, "실제 기록이 삭제되었습니다."),
    ACTUAL_TASKS_FOUND(HttpStatus.OK, "할 일 전체 조회 성공"),

    USER_WITHDRAWN(HttpStatus.OK, "회원 탈퇴가 완료되었습니다."),

    // 루틴
    ROUTINE_CREATED(HttpStatus.CREATED, "루틴 생성 성공"),
    ROUTINES_FOUND(HttpStatus.OK, "루틴 전체 조회 성공"),
    ROUTINE_UPDATED(HttpStatus.OK, "루틴 수정 성공"),
    ROUTINE_DELETED(HttpStatus.OK, "루틴 삭제 성공"),
    ROUTINE_COMPLETED(HttpStatus.OK, "루틴 완료 처리 성공"),
    ROUTINE_FOUND(HttpStatus.OK, "루틴 조회 성공"),

    // AI 목표 생성
    GOAL_SESSION_STARTED(HttpStatus.CREATED, "목표 만들기 대화를 시작했어요."),
    GOAL_SESSION_ANSWERED(HttpStatus.OK, "답변이 반영되었어요."),
    GOAL_SESSION_FOUND(HttpStatus.OK, "목표 만들기 대화 조회 성공"),
    GOAL_SESSION_SLOTS_UPDATED(HttpStatus.OK, "정리된 내용이 수정되었어요."),
    GOAL_SESSION_CANCELLED(HttpStatus.OK, "목표 만들기를 취소했어요."),
    GOAL_DRAFT_REQUESTED(HttpStatus.ACCEPTED, "계획을 만들고 있어요."),
    GOAL_DRAFT_FOUND(HttpStatus.OK, "목표 초안 조회 성공"),
    GOAL_DRAFT_CONFIRMED(HttpStatus.CREATED, "목표가 만들어졌어요."),

    // 기본 생활 패턴 · 플래너 (명세 3)
    LIFE_PATTERNS_FOUND(HttpStatus.OK, "기본 생활 패턴 조회 성공"),
    LIFE_PATTERN_CREATED(HttpStatus.CREATED, "고정 시간을 추가했어요."),
    LIFE_PATTERN_UPDATED(HttpStatus.OK, "고정 시간을 수정했어요."),
    LIFE_PATTERN_DELETED(HttpStatus.OK, "고정 시간을 삭제했어요."),
    LIFE_PATTERN_OVERRIDE_SAVED(HttpStatus.OK, "이날의 고정 시간을 바꿨어요."),
    LIFE_PATTERN_OVERRIDE_DELETED(HttpStatus.OK, "이날의 고정 시간을 원래대로 되돌렸어요."),
    FIXED_BLOCKS_FOUND(HttpStatus.OK, "고정 블록 조회 성공"),
    PLANNER_SETTING_FOUND(HttpStatus.OK, "플래너 설정 조회 성공"),
    PLANNER_SETTING_UPDATED(HttpStatus.OK, "플래너 설정을 수정했어요."),
    TIMETABLE_FOUND(HttpStatus.OK, "TimeTable 조회 성공"),
    GOAL_TASKS_FOUND(HttpStatus.OK, "목표 관련 Task 조회 성공"),
    ROUTINE_SCHEDULE_CHANGED(HttpStatus.OK, "루틴 시간을 바꿨어요."),
    LIFE_PATTERN_SCOPED_CHANGED(HttpStatus.OK, "고정 시간을 바꿨어요."),
    GOAL_TASK_CREATED(HttpStatus.CREATED, "Task를 추가했어요."),
    GOAL_TASK_UPDATED(HttpStatus.OK, "Task를 수정했어요."),
    MILESTONE_CREATED(HttpStatus.CREATED, "마일스톤을 추가했어요."),
    MILESTONE_UPDATED(HttpStatus.OK, "마일스톤을 수정했어요."),
    MILESTONE_DELETED(HttpStatus.OK, "마일스톤을 삭제했어요."),
    TASK_SCHEDULED(HttpStatus.OK, "시간을 옮겼어요."),
    TASK_UNSCHEDULED(HttpStatus.OK, "시간 미정으로 옮겼어요."),
    TASK_DATE_MOVED(HttpStatus.OK, "다른 날로 옮겼어요."),
    TASK_UNLOCKED(HttpStatus.OK, "자동 배치가 이 일정을 옮길 수 있어요."),
    TASK_QUOTA_FOUND(HttpStatus.OK, "오늘 추가할 수 있는 Task 개수 조회 성공"),
    BRAIN_DUMP_PARSED(HttpStatus.OK, "할 일을 정리했어요."),
    BRAIN_DUMP_CONFIRMED(HttpStatus.CREATED, "할 일을 TimeBlock에 배치했어요."),
    BRAIN_DUMP_CONFLICTED(HttpStatus.OK, "다른 일정과 겹치는 시간이 있어요."),
    DAILY_PLAN_FOUND(HttpStatus.OK, "TimeTable 생성 기록 조회 성공"),
    DAILY_PLAN_REVIEWED(HttpStatus.OK, "이대로 진행할게요."),
    DAILY_PLAN_REPLANNED(HttpStatus.OK, "TimeBlock을 다시 배치했어요."),
    DAILY_PLAN_FILLED(HttpStatus.OK, "시간 미정 Task를 빈 시간에 배치했어요."),
    GOAL_PROGRESS_FOUND(HttpStatus.OK, "목표 진행 조회 성공"),
    GOAL_ADJUSTMENT_APPLIED(HttpStatus.OK, "루틴을 조정했어요. 내일부터 바뀌어요."),
    GOAL_ADJUSTMENT_DISMISSED(HttpStatus.OK, "이번 주는 그대로 둘게요."),
    GOAL_OUTCOME_UPDATED(HttpStatus.OK, "목표 결과를 남겼어요."),
    GOAL_OUTCOME_PROMPTS_FOUND(HttpStatus.OK, "결과 확인할 목표 조회 성공"),
    GOAL_OUTCOME_PROMPT_DISMISSED(HttpStatus.OK, "자동 판정 결과를 그대로 둘게요."),
    AI_USAGE_FOUND(HttpStatus.OK, "오늘 사용량 조회 성공"),
    ACTIVITIES_FOUND(HttpStatus.OK, "실제로 한 일 조회 성공"),
    ACTIVITY_CREATED(HttpStatus.CREATED, "실제로 한 일을 기록했어요."),
    ACTIVITY_UPDATED(HttpStatus.OK, "기록을 수정했어요."),
    ACTIVITY_DELETED(HttpStatus.OK, "기록을 지웠어요."),
    RECORD_PATTERN_FOUND(HttpStatus.OK, "목표 패턴 조회 성공"),
    RECORD_JOURNAL_FOUND(HttpStatus.OK, "업무 일지 조회 성공"),
    GOAL_METRIC_LOGGED(HttpStatus.OK, "기록했어요."),
    GOAL_METRIC_LOG_DELETED(HttpStatus.OK, "기록을 지웠어요."),

    // 나의 패턴 (명세 4)
    PATTERN_REPORT_FOUND(HttpStatus.OK, "나의 패턴 조회 성공"),
    PATTERN_SUGGESTION_APPLIED(HttpStatus.OK, "제안을 TimeBlock에 반영했어요. 내일부터 적용돼요."),
    PATTERN_SUGGESTION_DISMISSED(HttpStatus.OK, "이 제안은 일주일 동안 보이지 않아요."),

    // 기본 생활 습관 (V52)
    HABIT_FOUND(HttpStatus.OK, "습관 조회 성공"),
    HABIT_CREATED(HttpStatus.CREATED, "습관을 추가했어요."),
    HABIT_UPDATED(HttpStatus.OK, "습관을 수정했어요."),
    HABIT_PAUSED(HttpStatus.OK, "습관을 잠시 쉬어요. 쉬는 동안은 안 한 날로 세지 않아요."),
    HABIT_RESUMED(HttpStatus.OK, "습관을 다시 시작했어요."),
    HABIT_DELETED(HttpStatus.OK, "습관을 지웠어요."),
    HABIT_CHECKED(HttpStatus.OK, "체크했어요."),
    HABIT_UNCHECKED(HttpStatus.OK, "체크를 풀었어요."),

    // 홈 (오늘 모아 보기)
    HOME_FOUND(HttpStatus.OK, "홈 조회 성공"),
    REFLECTION_FEED_FOUND(HttpStatus.OK, "회고 모아보기 조회 성공"),

    // 업무형 보드 (V52)
    WORK_BOARD_FOUND(HttpStatus.OK, "업무 보드 조회 성공"),
    WORK_TOPIC_SAVED(HttpStatus.OK, "세부 목표를 저장했어요."),
    WORK_TOPIC_DELETED(HttpStatus.OK, "세부 목표를 지웠어요. 들어 있던 Task는 '세부 목표 없음'으로 옮겼어요."),
    WORK_TASKS_CREATED(HttpStatus.CREATED, "Task를 추가했어요."),
    WORK_TASK_UPDATED(HttpStatus.OK, "Task를 수정했어요."),

    // 목표형 추가 그래프 (V52)
    GOAL_METRICS_FOUND(HttpStatus.OK, "그래프 조회 성공"),
    GOAL_EXTRA_METRIC_SAVED(HttpStatus.OK, "그래프를 저장했어요."),
    GOAL_EXTRA_METRIC_DELETED(HttpStatus.OK, "그래프를 지웠어요."),

    // 운영 대시보드
    ADMIN_DASHBOARD_FOUND(HttpStatus.OK, "운영 대시보드 조회 성공"),
    ADMIN_VIEW_PLAN_FOUND(HttpStatus.OK, "보고 있는 플랜 조회 성공"),
    ADMIN_VIEW_PLAN_CHANGED(HttpStatus.OK, "보고 있는 플랜을 바꿨어요.");

    private final HttpStatus status;
    private final String message;
}
