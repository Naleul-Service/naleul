package com.naleul.naleul.domain.externalCalendar.enums;

/**
 * 외부 일정을 어디서 가져왔는지.
 *  - GOOGLE: 웹에서 구글 계정을 연결해 가져옴 (노션 캘린더에 연결한 구글 캘린더 일정도 여기)
 *  - APPLE : iOS 앱이 아이폰 기본 캘린더(EventKit)에서 가져옴
 *            → 아이폰에 추가한 iCloud · 구글 · Outlook 계정 일정, 노션 캘린더 일정까지 모두 포함
 * 노션 캘린더는 자체 저장소·공개 API 가 없고, 연결된 구글 · iCloud · Outlook 캘린더를 보여주는 앱이라 따로 두지 않는다.
 */
public enum ExternalCalendarSource {
    GOOGLE,
    APPLE
}
