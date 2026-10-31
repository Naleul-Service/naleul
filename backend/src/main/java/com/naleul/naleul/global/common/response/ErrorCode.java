package com.naleul.naleul.global.common.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Goal Category
    GOAL_CATEGORY_NAME_DUPLICATED(HttpStatus.BAD_REQUEST, "이미 존재하는 목표 카테고리 이름입니다."),
    GOAL_CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 목표 카테고리입니다."),
    GOAL_CATEGORY_DEFAULT_CANNOT_MODIFY(HttpStatus.BAD_REQUEST, "기본 카테고리(기타)는 수정할 수 없습니다."),
    GOAL_CATEGORY_DEFAULT_CANNOT_DELETE(HttpStatus.BAD_REQUEST, "기본 카테고리(기타)는 삭제할 수 없습니다."),
    GOAL_CATEGORY_INVALID_DATE(HttpStatus.BAD_REQUEST, "시작 날짜는 종료 날짜보다 늦을 수 없습니다."),
    GOAL_CATEGORY_ALREADY_DELETED(HttpStatus.BAD_REQUEST, "삭제된 목표는 완료 처리할 수 없습니다."),
    GOAL_CATEGORY_NOT_COMPLETED(HttpStatus.BAD_REQUEST, "완료된 목표만 달성 내용을 입력할 수 있습니다."),

    // 통계
    WEEKLY_REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "주간 리포트를 찾을 수 없습니다."),
    WEEKLY_REPORT_NOT_YET_AVAILABLE(HttpStatus.CONFLICT, "주간 패턴 분석은 일요일에 확인 가능해요."), // ✅ 이 줄 추가
    MONTHLY_REPORT_NOT_YET_AVAILABLE(HttpStatus.CONFLICT, "월간 패턴 분석은 마지막 주 일요일 확인 가능해요."), // ✅ 추가

    // 요금제 제한
    GOAL_CATEGORY_LIMIT_EXCEEDED(HttpStatus.FORBIDDEN, "무료 플랜은 목표를 최대 2개까지 생성할 수 있어요. Pro로 업그레이드하면 무제한으로 만들 수 있어요."),
    GENERAL_CATEGORY_LIMIT_EXCEEDED(HttpStatus.FORBIDDEN, "무료 플랜은 세부 목표를 최대 1개까지 생성할 수 있어요. Pro로 업그레이드하면 무제한으로 만들 수 있어요."),
    ROUTINE_LIMIT_EXCEEDED(HttpStatus.FORBIDDEN, "무료 플랜은 루틴을 최대 1개까지 생성할 수 있어요. Pro로 업그레이드하면 무제한으로 만들 수 있어요."),
    TASK_DAILY_LIMIT_EXCEEDED(HttpStatus.FORBIDDEN, "무료 플랜은 하루에 최대 2개까지 할 일을 생성할 수 있어요. Pro로 업그레이드하면 무제한으로 만들 수 있어요."),
    IDEA_BOARD_LIMIT_EXCEEDED(HttpStatus.FORBIDDEN, "무료 플랜은 목표 하나당 아이디어 보드를 최대 2개까지 생성할 수 있어요. Pro로 업그레이드하면 무제한으로 만들 수 있어요."),
    DREAM_BOARD_LIMIT_EXCEEDED(HttpStatus.FORBIDDEN, "무료 플랜은 목표 하나당 드림보드를 최대 2개까지 생성할 수 있어요. Pro로 업그레이드하면 무제한으로 만들 수 있어요."),
    AUTOBIOGRAPHY_QUESTION_LIMIT_EXCEEDED(HttpStatus.FORBIDDEN, "무료 플랜은 자문자답 질문을 최대 1개까지 생성할 수 있어요. Pro로 업그레이드하면 무제한으로 만들 수 있어요."),
    AUTOBIOGRAPHY_ANSWER_LIMIT_EXCEEDED(HttpStatus.FORBIDDEN, "무료 플랜은 하나의 질문에 답변을 최대 2개까지 작성할 수 있어요. Pro로 업그레이드하면 무제한으로 작성할 수 있어요."),
    RETROSPECTIVE_GOAL_CATEGORY_REQUIRED(HttpStatus.BAD_REQUEST, "회고는 목표를 반드시 선택해야 합니다."),
    RETROSPECTIVE_LIMIT_EXCEEDED(HttpStatus.FORBIDDEN, "무료 플랜은 목표 하나당 회고를 최대 3개까지 생성할 수 있어요. Pro로 업그레이드하면 무제한으로 만들 수 있어요."),
    MISSION_CREATE_LIMIT_EXCEEDED(HttpStatus.FORBIDDEN, "진행 중인 미션은 3개까지 만들 수 있어요. 끝난 미션을 정리한 뒤 다시 만들어 주세요."),
    MISSION_PARTICIPATION_LIMIT_EXCEEDED(HttpStatus.FORBIDDEN, "신청자가 참여 가능한 미션 개수를 모두 사용해서 미션에 참여할 수 없어요."),
    TASK_NOTIFICATION_PRO_ONLY(HttpStatus.FORBIDDEN, "푸시 알림 기능은 Pro 플랜에서만 사용할 수 있어요. Pro로 업그레이드하면 이용할 수 있어요."),
    NOTIFICATION_SETTINGS_PRO_ONLY(HttpStatus.FORBIDDEN, "푸시 알림 설정은 PRO부터 사용할 수 있어요. Pro로 업그레이드하면 이용할 수 있어요."),

    // General Category
    GENERAL_CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 일반 카테고리입니다."),
    GENERAL_CATEGORY_DEFAULT_CANNOT_MODIFY(HttpStatus.BAD_REQUEST, "기본 카테고리(ETC)는 수정할 수 없습니다."),
    GENERAL_CATEGORY_DEFAULT_CANNOT_DELETE(HttpStatus.BAD_REQUEST, "기본 카테고리(ETC)는 삭제할 수 없습니다."),

    // 미션
    MISSION_NOT_FOUND(HttpStatus.NOT_FOUND, "미션을 찾을 수 없습니다."),
    MISSION_DELETED(HttpStatus.NOT_FOUND, "삭제된 미션입니다."),
    MISSION_NOT_RECRUITING(HttpStatus.BAD_REQUEST, "모집 중인 미션이 아닙니다."),
    MISSION_FULL(HttpStatus.BAD_REQUEST, "참여 인원이 가득 찼습니다."),
    MISSION_ACCESS_DENIED(HttpStatus.FORBIDDEN, "방장만 접근할 수 있습니다."),
    MISSION_ALREADY_JOINED(HttpStatus.BAD_REQUEST, "이미 참여한 미션입니다."),
    MISSION_INVALID_DATE(HttpStatus.BAD_REQUEST, "모집 시작일은 종료일보다 빨라야 합니다."),
    MISSION_PASSWORD_REQUIRED(HttpStatus.BAD_REQUEST, "비공개 미션은 비밀번호가 필요합니다."),
    MISSION_REPEAT_DAY_REQUIRED(HttpStatus.BAD_REQUEST, "요일 선택 시 최소 1개 이상의 요일을 선택해야 합니다."),
    MISSION_PARTICIPANTS_LIMIT(HttpStatus.BAD_REQUEST, "제한 인원은 현재 참여자 수보다 작을 수 없습니다."),
    MISSION_NOT_PRIVATE(HttpStatus.BAD_REQUEST, "공개 미션입니다."),
    MISSION_CATEGORY_ITEM_REQUIRED(HttpStatus.BAD_REQUEST, "카테고리가 '기타'가 아니면 항목은 필수입니다."),
    MISSION_CATEGORY_ITEM_MISMATCH(HttpStatus.BAD_REQUEST, "선택한 항목이 카테고리에 속하지 않습니다."),
    MISSION_CUSTOM_CATEGORY_NAME_REQUIRED(HttpStatus.BAD_REQUEST, "카테고리가 '기타'이면 기타 입력값이 필수입니다."),
    MISSION_CUSTOM_ITEM_NAME_REQUIRED(HttpStatus.BAD_REQUEST, "항목이 '기타'이면 기타 입력값이 필수입니다."),
    TASK_NOT_MISSION_TYPE(HttpStatus.BAD_REQUEST, "미션 Task만 이 API를 사용할 수 있습니다."),
    MISSION_ALREADY_POKED_TODAY(HttpStatus.BAD_REQUEST, "오늘은 이미 쪼기를 보냈어요. 내일 다시 시도해주세요."),

    // 참여자
    PARTICIPANT_NOT_FOUND(HttpStatus.NOT_FOUND, "참여자를 찾을 수 없습니다."),
    PARTICIPANT_NOT_PENDING(HttpStatus.BAD_REQUEST, "대기 중인 신청만 처리할 수 있습니다."),
    PARTICIPANT_NOT_ACTIVE(HttpStatus.BAD_REQUEST, "진행 중인 참여자만 성공/실패 처리할 수 있습니다."),
    PARTICIPANT_STATUS_INVALID(HttpStatus.BAD_REQUEST, "성공 또는 실패 상태만 설정할 수 있습니다."),

    // Task 인증
    TASK_NOT_FOUND(HttpStatus.NOT_FOUND, "Task를 찾을 수 없습니다."),
    TASK_NOT_MISSION(HttpStatus.BAD_REQUEST, "미션 Task만 완료 처리할 수 있습니다."),
    TASK_NOT_OWNER(HttpStatus.FORBIDDEN, "본인의 Task만 완료 처리할 수 있습니다."),
    TASK_ALREADY_COMPLETED(HttpStatus.BAD_REQUEST, "이미 완료 처리된 Task입니다."),
    TASK_NOT_COMPLETED(HttpStatus.BAD_REQUEST, "완료 상태의 Task만 완료 취소할 수 있습니다."),

    // 좋아요
    LIKE_SELF_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "본인 인증에는 좋아요를 누를 수 없습니다."),
    COMPLETE_NOT_FOUND(HttpStatus.NOT_FOUND, "인증 기록을 찾을 수 없습니다."),

    //아이디어 보드
    IDEA_BOARD_NOT_FOUND(HttpStatus.NOT_FOUND, "아이디어를 찾을 수 없습니다."),

    //드림 보드
    DREAM_BOARD_NOT_FOUND(HttpStatus.NOT_FOUND, "드림보드를 찾을 수 없습니다."),

    // 자서전
    AUTOBIOGRAPHY_QUESTION_NOT_FOUND(HttpStatus.NOT_FOUND, "질문을 찾을 수 없습니다."),
    AUTOBIOGRAPHY_ANSWER_NOT_FOUND(HttpStatus.NOT_FOUND, "답변을 찾을 수 없습니다."),
    AUTOBIOGRAPHY_ANSWER_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 답변이 존재합니다."),
    AUTOBIOGRAPHY_DEFAULT_QUESTION_CANNOT_MODIFY(HttpStatus.FORBIDDEN, "기본 질문은 수정할 수 없습니다."),
    AUTOBIOGRAPHY_DEFAULT_QUESTION_CANNOT_DELETE(HttpStatus.FORBIDDEN, "기본 질문은 삭제할 수 없습니다."),

    // Task
    TASK_INVALID_TIME_RANGE(HttpStatus.BAD_REQUEST, "시작 시간이 종료 시간보다 늦을 수 없습니다."),
    TASK_INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "시작일이 종료일보다 늦을 수 없습니다."),
    TASK_INVALID_WEEK_RANGE(HttpStatus.BAD_REQUEST, "조회 범위는 최대 7일입니다."),
    TASK_INVALID_DAY_OF_WEEK(HttpStatus.BAD_REQUEST, "올바르지 않은 요일 값입니다. (MONDAY~SUNDAY)"),
    TASK_INVALID_WEEK_DAY_ID(HttpStatus.BAD_REQUEST, "존재하지 않는 요일 ID가 포함되어 있습니다."),
    ACTUAL_TASK_NOT_FOUND(HttpStatus.NOT_FOUND, "실제 기록을 찾을 수 없습니다."),

    //회고
    RETROSPECTIVE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 회고입니다."),
    RETROSPECTIVE_FORBIDDEN(HttpStatus.FORBIDDEN, "해당 회고에 대한 접근 권한이 없습니다."),

    // Color
    COLOR_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 색상입니다."),
    COLOR_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 존재하는 색상입니다."),
    DEFAULT_COLOR_CANNOT_BE_DELETED(HttpStatus.BAD_REQUEST, "기본 색상은 삭제할 수 없습니다."),
    COLOR_IN_USE(HttpStatus.CONFLICT, "해당 색상은 카테고리에서 사용 중이에요. 카테고리 색상을 먼저 변경해주세요."),

    // Validation
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    INVALID_ENUM_VALUE(HttpStatus.BAD_REQUEST, "올바르지 않은 상태값입니다."),
    INVALID_DATE_FORMAT(HttpStatus.BAD_REQUEST, "날짜 형식이 올바르지 않습니다. (yyyy-MM-dd)"),

    //토큰
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "토큰이 만료되었습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "토큰이 없습니다."),

    // 공통
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "리소스를 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
    DATA_INTEGRITY_VIOLATION(HttpStatus.CONFLICT, "데이터 제약 조건 위반이에요."),

    // 유저
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "유저를 찾을 수 없습니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    APPLE_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "유효하지 않은 Apple 토큰입니다."),
    APPLE_PUBLIC_KEY_FETCH_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Apple 공개키 조회에 실패했습니다."),
    REQUIRED_TERMS_NOT_AGREED(HttpStatus.BAD_REQUEST, "필수 약관에 모두 동의해야 서비스를 이용할 수 있어요."), // ✅ 추가

    INVALID_PURCHASE_RECEIPT(HttpStatus.BAD_REQUEST, "유효하지 않은 인앱결제 영수증입니다."),

    // Web Billing (토스페이먼츠 웹 구독)
    WEB_BILLING_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "웹 결제를 준비 중이에요."),
    WEB_BILLING_ALREADY_SUBSCRIBED(HttpStatus.CONFLICT, "이미 웹에서 Pro를 구독 중이에요."),
    WEB_BILLING_IAP_ACTIVE(HttpStatus.CONFLICT, "iOS 앱에서 Pro를 이용 중이에요. 앱 구독이 끝난 뒤 웹에서 구독할 수 있어요."),
    WEB_BILLING_INVALID_SESSION(HttpStatus.BAD_REQUEST, "결제 정보가 맞지 않아요. 처음부터 다시 시도해 주세요."),
    WEB_BILLING_SUBSCRIPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "웹 구독 정보를 찾을 수 없어요."),
    WEB_BILLING_PAYMENT_FAILED(HttpStatus.BAD_REQUEST, "결제에 실패했어요."),
    WEB_BILLING_TOSS_PAY_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "토스페이 결제를 준비 중이에요."),
    WEB_BILLING_TOSS_PAY_NOT_AUTHORIZED(HttpStatus.BAD_REQUEST, "토스 앱에서 인증이 끝나지 않았어요. 인증을 마친 뒤 다시 시도해 주세요."),
    WEB_BILLING_INVALID_RETURN_URL(HttpStatus.BAD_REQUEST, "결제 후 돌아갈 주소가 올바르지 않아요."),

    // Play Billing (Android Google Play 구독)
    PLAY_BILLING_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "Google Play 결제를 준비 중이에요."),
    PLAY_BILLING_INVALID_PURCHASE(HttpStatus.BAD_REQUEST, "Google Play 결제 정보를 찾을 수 없어요."),
    PLAY_BILLING_OTHER_ACCOUNT(HttpStatus.CONFLICT, "다른 나를 계정으로 결제한 구독이에요. 결제한 계정으로 로그인해 주세요."),
    PLAY_BILLING_VERIFY_FAILED(HttpStatus.BAD_GATEWAY, "Google Play 결제를 확인하지 못했어요. 잠시 후 다시 시도해 주세요."),
    WITHDRAWAL_DETAIL_REQUIRED(HttpStatus.BAD_REQUEST, "기타 사유를 선택한 경우 상세 내용을 입력해주세요."),

    //루틴
    ROUTINE_CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 루틴입니다."),
    ROUTINE_NOT_FOUND(HttpStatus.BAD_REQUEST, "루틴을 찾을 수 없습니다."),
    ROUTINE_INVALID_DATE(HttpStatus.BAD_REQUEST, "시작 날짜는 종료 날짜보다 늦을 수 없습니다."),
    ROUTINE_EXTRA_DAY_EXISTS(HttpStatus.CONFLICT, "이날은 이미 이 루틴이 있어요. 캘린더에서 그 블록을 완료해 주세요."),
    ROUTINE_EXTRA_DAY_INVALID(HttpStatus.BAD_REQUEST, "추가할 날을 다시 확인해 주세요. 앞날은 '이미 했어요'로 남길 수 없고, 60일 뒤까지만 추가할 수 있어요."),
    ROUTINE_NOT_ACTIVE(HttpStatus.BAD_REQUEST, "끝났거나 멈춘 루틴에는 날을 추가할 수 없어요."),
    ROUTINE_INVALID_TIME(HttpStatus.BAD_REQUEST, "루틴 시간을 다시 확인해 주세요. 시작과 끝이 같거나, 자정을 넘겨 12시간보다 길 수 없어요."),

    // AI 목표 생성
    REQUIRED_SLOT_MISSING(HttpStatus.BAD_REQUEST, "이루고 싶은 목표를 먼저 알려주세요."),
    SLOT_NOT_SKIPPABLE(HttpStatus.BAD_REQUEST, "이 질문은 건너뛸 수 없어요."),
    INVALID_SOURCE_GOAL(HttpStatus.BAD_REQUEST, "구체화할 수 없는 목표예요."),
    GOAL_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "목표 만들기 대화를 찾을 수 없어요."),
    GOAL_DRAFT_NOT_FOUND(HttpStatus.NOT_FOUND, "목표 초안을 찾을 수 없어요."),
    GOAL_SESSION_INVALID_STATE(HttpStatus.CONFLICT, "현재 단계에서는 할 수 없는 요청이에요."),
    DRAFT_VALIDATION_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "계획에 수정이 필요한 부분이 있어요."),
    GOAL_REJECTED(HttpStatus.UNPROCESSABLE_ENTITY, "이 목표는 함께 계획을 세우기 어려워요."),
    GOAL_CREATION_DAILY_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "오늘 AI 목표 만들기 횟수를 모두 썼어요. 내일 0시에 다시 채워져요."),
    GOAL_CREATION_WEEKLY_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "이번 주 AI 목표 만들기 횟수를 모두 썼어요. 월요일 0시에 다시 채워져요. Pro는 주 10개까지 만들 수 있어요."),
    GOAL_CREATION_MONTHLY_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "이번 달 AI 목표 만들기 횟수를 모두 썼어요. 다음 달 1일 0시에 다시 채워져요. Pro는 한 달 20개까지 만들 수 있어요."),
    AI_SCHEDULE_EDIT_DAILY_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "오늘 AI로 일정 조정 횟수를 모두 썼어요. 내일 0시에 다시 채워져요. 캘린더에서 끌어서 옮기는 건 언제든 할 수 있어요."),
    AI_EDIT_DAILY_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "오늘 AI로 목표 고치기 횟수를 모두 썼어요. 내일 0시에 다시 채워져요. 직접 고치는 건 언제든 할 수 있어요."),
    GOAL_DRAFT_DAILY_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "오늘 AI 계획 초안 만들기 횟수를 모두 썼어요. 내일 0시에 다시 채워져요."),
    GOAL_DRAFT_WEEKLY_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "이번 주 AI 계획 초안 횟수를 모두 썼어요. 월요일 0시에 다시 채워져요. Pro는 주 10번까지 만들 수 있어요."),
    GOAL_DRAFT_MONTHLY_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "이번 달 AI 계획 초안 횟수를 모두 썼어요. 다음 달 1일 0시에 다시 채워져요. Pro는 한 달 20번까지 만들 수 있어요."),
    DRAFT_REGENERATE_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "이 목표는 계획을 더 만들 수 없어요 (한 대화에서 최대 5번). 지금 초안을 직접 고치거나 새 목표 대화를 시작해 주세요."),
    AI_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "AI가 잠시 응답하지 않아요. 잠시 후 다시 시도해주세요."),

    // 기본 생활 패턴 · 플래너 (명세 3)
    LIFE_PATTERN_NOT_FOUND(HttpStatus.NOT_FOUND, "고정 시간을 찾을 수 없어요."),
    MILESTONE_NOT_FOUND(HttpStatus.NOT_FOUND, "마일스톤을 찾을 수 없어요."),
    GOAL_TASK_NOT_FOUND(HttpStatus.NOT_FOUND, "이 목표에서 Task를 찾을 수 없어요."),
    GOAL_TASK_NOT_EDITABLE(HttpStatus.BAD_REQUEST, "루틴·미션 Task는 여기서 수정할 수 없어요. 루틴은 루틴에서 바꿔 주세요."),
    SCOPE_ROUTINE_ONLY(HttpStatus.BAD_REQUEST, "이번 주·앞으로 바꾸기는 루틴에서만 할 수 있어요."),
    SCOPE_SAME_DAY_ONLY(HttpStatus.BAD_REQUEST, "다른 날로 옮길 때는 이날만 바꿀 수 있어요."),
    SCOPE_NO_MIDNIGHT(HttpStatus.BAD_REQUEST, "자정을 넘기는 루틴은 12시간까지 정할 수 있어요."),
    GOAL_TASK_INVALID_TIME(HttpStatus.BAD_REQUEST, "시작과 종료 시간을 함께 입력해 주세요."),
    LIFE_PATTERN_INVALID_TIME(HttpStatus.BAD_REQUEST, "시작 시간과 종료 시간이 같을 수 없어요."),
    LIFE_PATTERN_OVERLAP(HttpStatus.CONFLICT, "같은 요일의 다른 고정 시간과 겹쳐요."),
    LIFE_PATTERN_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "고정 시간은 최대 10개까지 만들 수 있어요."),
    LIFE_PATTERN_NOT_ON_DATE(HttpStatus.BAD_REQUEST, "이 날짜에는 해당 고정 시간이 없어요."),
    TIMETABLE_INVALID_RANGE(HttpStatus.BAD_REQUEST, "조회 기간은 시작일부터 최대 7일까지예요."),
    GOAL_TIME_INVALID_RANGE(HttpStatus.BAD_REQUEST, "목표별 시간은 최대 42일까지 볼 수 있어요."),
    TIMEBLOCK_CONFLICT(HttpStatus.CONFLICT, "이 시간에는 이미 다른 일정이 있어요."),
    TIMEBLOCK_NOT_UNSCHEDULABLE(HttpStatus.BAD_REQUEST, "루틴과 미션은 시간을 비울 수 없어요. 시간을 옮겨 주세요."),
    BRAIN_DUMP_GOAL_REQUIRED(HttpStatus.BAD_REQUEST, "목표를 연결하지 않은 할 일이 있어요. 목표를 골라주세요."),
    BRAIN_DUMP_TEMP_GOAL_UNKNOWN(HttpStatus.BAD_REQUEST, "임시 목표 정보가 올바르지 않아요."),
    BRAIN_DUMP_MILESTONE_MISMATCH(HttpStatus.BAD_REQUEST, "선택한 마일스톤이 목표에 속하지 않아요."),
    TEMP_GOAL_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "임시 목표는 최대 5개까지 만들 수 있어요. 기존 임시 목표를 구체화하거나 정리해주세요."),
    DAILY_PLAN_PAST_DATE(HttpStatus.BAD_REQUEST, "지난 날짜는 다시 배치할 수 없어요."),

    // 목표 종류 (명세 6.A.1.2)
    INVALID_GOAL_KIND(HttpStatus.BAD_REQUEST, "목표 카테고리와 세부 카테고리가 맞지 않아요."),
    INVALID_GOAL_KIND_LABEL(HttpStatus.BAD_REQUEST, "기타 카테고리 이름을 1~10자로 입력해주세요."),

    // 실제로 한 일
    ACTIVITY_NOT_FOUND(HttpStatus.NOT_FOUND, "기록을 찾을 수 없어요."),
    ACTIVITY_INVALID_TIME(HttpStatus.BAD_REQUEST, "끝난 시각은 시작 시각보다 늦어야 해요."),
    ACTIVITY_TOO_LONG(HttpStatus.BAD_REQUEST, "한 번에 24시간까지만 기록할 수 있어요."),
    ACTIVITY_FUTURE(HttpStatus.BAD_REQUEST, "아직 오지 않은 시간은 기록할 수 없어요. 실제로 한 일만 남겨 주세요."),
    ACTIVITY_OVERLAP(HttpStatus.CONFLICT, "이 시간에 이미 기록한 일이 있어요."),
    ACTIVITY_INVALID_RANGE(HttpStatus.BAD_REQUEST, "조회 기간은 최대 42일까지예요."),

    // 목표 진행 기록
    GOAL_METRIC_NOT_SET(HttpStatus.BAD_REQUEST, "이 목표에는 시작값과 목표값이 없어서 기록할 수 없어요. 목표 수치를 먼저 정해주세요."),
    GOAL_METRIC_LOG_FUTURE_DATE(HttpStatus.BAD_REQUEST, "미래 날짜는 기록할 수 없어요."),
    GOAL_METRIC_LOG_NOT_FOUND(HttpStatus.NOT_FOUND, "이 날짜의 기록이 없어요."),

    // 나의 패턴 (명세 4)
    PATTERN_INVALID_PERIOD(HttpStatus.BAD_REQUEST, "기간은 RECENT_4W 또는 ALL 이에요."),
    PATTERN_SUGGESTION_NOT_FOUND(HttpStatus.NOT_FOUND, "제안을 찾을 수 없어요. 화면을 새로고침해 주세요."),
    PATTERN_SUGGESTION_STALE(HttpStatus.CONFLICT, "루틴이 이미 바뀌어서 이 제안을 반영할 수 없어요. 화면을 새로고침해 주세요."),

    // 기본 생활 습관 (V52)
    HABIT_HOME_NOT_REMOVABLE(HttpStatus.BAD_REQUEST, "기본 생활 습관은 완료하거나 삭제할 수 없어요. 필요 없는 습관만 쉬거나 지워 주세요."),
    HABIT_NOT_FOUND(HttpStatus.NOT_FOUND, "습관을 찾을 수 없어요."),
    HABIT_HOME_USE_HABITS(HttpStatus.BAD_REQUEST, "기본 생활 습관에는 루틴 대신 습관을 추가해 주세요. 한 번만 할 일은 Task로 추가할 수 있어요."),
    TASK_ACTUAL_INVALID_SEGMENT(HttpStatus.BAD_REQUEST, "실제로 한 시간은 끝이 시작보다 뒤여야 하고, 구간끼리 겹치면 안 돼요."),
    HABIT_INVALID_TIME_RANGE(HttpStatus.BAD_REQUEST, "습관 시간은 시작과 끝을 함께 정하고, 끝이 시작보다 뒤여야 해요."),
    HABIT_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "습관은 최대 30개까지 만들 수 있어요. 안 쓰는 습관을 정리해 주세요."),
    HABIT_CHECK_FUTURE_DATE(HttpStatus.BAD_REQUEST, "미래 날짜는 체크할 수 없어요."),
    HABIT_HEATMAP_INVALID_PERIOD(HttpStatus.BAD_REQUEST, "잔디맵 기간은 시작일이 종료일보다 앞서고, 최대 400일까지예요."),

    // 업무형 보드 (V52)
    WORK_GOAL_ONLY(HttpStatus.BAD_REQUEST, "업무형 목표에서만 쓸 수 있어요."),
    WORK_TOPIC_NOT_FOUND(HttpStatus.NOT_FOUND, "세부 목표를 찾을 수 없어요."),
    WORK_TOPIC_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "세부 목표는 목표 하나에 최대 20개까지 만들 수 있어요."),
    WORK_TASK_NOT_FOUND(HttpStatus.NOT_FOUND, "이 업무에서 Task를 찾을 수 없어요."),
    WORK_TASK_NOT_EDITABLE(HttpStatus.BAD_REQUEST, "루틴·미션 Task는 보드에서 옮기거나 고칠 수 없어요."),
    WORK_TASK_BULK_INVALID(HttpStatus.BAD_REQUEST, "한 번에 1~50개까지 추가할 수 있어요."),
    WORK_DEADLINE_INVALID(HttpStatus.BAD_REQUEST, "마감일은 시작일보다 빠를 수 없어요."),

    // 목표형 추가 그래프 (V52)
    GOAL_ACHIEVEMENT_ONLY(HttpStatus.BAD_REQUEST, "수치가 있는 목표형 목표에서만 쓸 수 있어요."),
    GOAL_CONVERT_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "진행 중인 목표형 목표만 업무형으로 바꿀 수 있어요."),
    GOAL_EXTRA_METRIC_NOT_FOUND(HttpStatus.NOT_FOUND, "그래프를 찾을 수 없어요."),
    GOAL_EXTRA_METRIC_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "그래프는 목표 하나에 최대 5개까지 추가할 수 있어요."),
    GOAL_EXTRA_METRIC_INVALID(HttpStatus.BAD_REQUEST, "시작값과 목표값을 서로 다르게 입력해 주세요."),

    // 운영 대시보드
    ADMIN_ONLY(HttpStatus.FORBIDDEN, "운영진만 볼 수 있어요.");


    private final HttpStatus status;
    private final String message;
}