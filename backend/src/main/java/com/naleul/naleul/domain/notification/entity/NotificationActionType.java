package com.naleul.naleul.domain.notification.entity;

// 알림 클릭했을 때 어디로 이동할지 (iOS에서 딥링크 분기용)
public enum NotificationActionType {
    NONE,
    MISSION_DETAIL,       // 미션 상세 페이지
    APPLICANT_LIST,       // 신청자 목록(승인 화면)
    CERTIFICATION_DETAIL, // 미션 인증 화면
    MISSION_EXPLORE,      // 미션 탐색 화면
    BADGE_MODAL           // 뱃지 획득 모달
}