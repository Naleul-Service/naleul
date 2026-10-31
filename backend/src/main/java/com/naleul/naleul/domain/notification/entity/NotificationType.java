package com.naleul.naleul.domain.notification.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationType {

    // 미션 알림 (방장 관점)
    APPLICATION_RECEIVED(NotificationCategory.MISSION_ALERT,
            "\"%s\"님이 미션을 신청했어요 마감되기 전에 승인을 눌러주세요", NotificationActionType.APPLICANT_LIST),
    RECRUIT_DEADLINE_D1_FOR_HOST(NotificationCategory.MISSION_ALERT,
            "모집마감 하루 전이에요! 승인하지 않은 참여자를 승인해주세요!", NotificationActionType.APPLICANT_LIST),
    APPLICATION_CANCELED(NotificationCategory.MISSION_ALERT,
            "\"%s\"님이 신청을 취소했어요", NotificationActionType.APPLICANT_LIST),

    // 미션 알림 (참여자 관점)
    APPLICATION_APPROVED(NotificationCategory.MISSION_ALERT,
            "신청한 미션이 승인되었어요. 내일부터 미션을 시작해보세요!", NotificationActionType.MISSION_DETAIL),
    APPLICATION_REJECTED(NotificationCategory.MISSION_ALERT,
            "아쉽게도 이번 미션은 함께하지 못했어요. 다른 미션을 탐색하러 가볼까요?", NotificationActionType.MISSION_EXPLORE),

    // 미션 인증
    MISSION_CERTIFIED(NotificationCategory.MISSION_CERTIFICATION,
            "%s님이 미션을 인증했어요 가서 확인해볼까요?", NotificationActionType.CERTIFICATION_DETAIL),

    // 찜하기
    WISHLIST_RECRUIT_STARTED(NotificationCategory.WISHLIST,
            "미션 모집이 시작되었어요. 기간 내에 신청해서 미션을 신청해보아요!", NotificationActionType.MISSION_DETAIL),
    WISHLIST_RECRUIT_DEADLINE_D1(NotificationCategory.WISHLIST,
            "찜한 미션 모집이 내일 마감돼요. 끝나기 전에 신청해보아요", NotificationActionType.MISSION_DETAIL),
    WISHLIST_RECRUIT_CLOSED(NotificationCategory.WISHLIST,
            "찜한 미션이 아쉽게도 마감되었어요", NotificationActionType.NONE),

    // 미션 진행
    MISSION_STARTED(NotificationCategory.MISSION_PROGRESS,
            "미션을 시작했어요! 인증해서 퍼즐을 얻어봐요 화이팅!", NotificationActionType.MISSION_DETAIL),
    MISSION_COMPLETED(NotificationCategory.MISSION_PROGRESS,
            "미션이 모두 종료됐어요 노력한 과정이 결실을 맺을 날이 올거에요!", NotificationActionType.MISSION_DETAIL),

    // 미션 내용 수정 (앱 내 알림 전용, 푸시 X)
    MISSION_CONTENT_EDITED(NotificationCategory.MISSION_EDIT,
            "승인 된 미션 내용이 수정되었어요 확인하러 가볼까요?", NotificationActionType.MISSION_DETAIL),

    // 뱃지 획득 (앱 내 알림 전용, 푸시 X)
    BADGE_ACQUIRED(NotificationCategory.BADGE,
            "함께한 노력으로 새로운 뱃지를 획득했어요.", NotificationActionType.BADGE_MODAL),
    // 미션 퍼즐 조각 획득
    MISSION_PUZZLE_ACQUIRED(NotificationCategory.MISSION_PROGRESS,
            "\"%s\" 미션 퍼즐 조각 %d개를 획득했어요", NotificationActionType.MISSION_DETAIL),

    // 쪼기
    POKE_RECEIVED(NotificationCategory.POKE,
            "미션을 완료하고 팀과 함께 목표를 이어가 보아요", NotificationActionType.MISSION_DETAIL);

    private final NotificationCategory category;
    private final String messageTemplate; // %s는 String.format으로 채움
    private final NotificationActionType actionType;
}