package com.naleul.naleul.domain.notification.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationCategory {
    MISSION_ALERT("미션 알림", "미션 신청 현황과 승인이 필요할 때 알려드려요", true),
    MISSION_CERTIFICATION("미션 인증", "팀원들이 미션을 인증할 때마다 알려드려요", true),
    WISHLIST("찜하기", "찜한 미션의 중요한 소식을 알려드려요", true),
    MISSION_PROGRESS("미션 진행", "미션이 시작되거나 완료되면 알려드려요.", true),
    MISSION_EDIT("미션 내용 수정", "미션 일정이나 내용이 변경되면 알려드려요", false),
    BADGE("뱃지 획득", "새로운 뱃지를 획득하면 알려드려요.", false),
    POKE("쪼기", "팀원이 미션 참여를 독려하면 알려드려요", true);

    private final String label;
    private final String description;
    private final boolean pushEnabledByDefault; // 이 카테고리가 원래 푸시를 보내는 카테고리인지
}