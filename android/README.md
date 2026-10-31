# Naleul-Android

iOS 앱(`Naleul-IOS` 의 `feature/web-port` 브랜치)을 Kotlin + Jetpack Compose 로 그대로 옮긴 Android 앱이에요.
화면 · 이름 · API 호출이 iOS 와 1:1 로 맞춰져 있어서, iOS 파일 `Features/Goal/GoalCards.swift` 는 여기 `feature/goal/GoalCards.kt` 에 있어요.

## 폴더 구조 (`app/src/main/java/com/naleul/naleul/`)

| 폴더 | 내용 | iOS 대응 |
|---|---|---|
| `app/` | 루트 화면 · 탭 · 화면 이동(AppRouter) · 윗줄(NavBar) | `App/` |
| `core/network/` | API 클라이언트 · 토큰 저장/재발급 · 카카오/둘러보기 로그인 · FCM 토큰 등록 | `Core/Network/` |
| `core/state/` | 사용자 정보 · 데이터 다시 불러오기(Invalidation) · Mutation · 토스트 · ScreenModel | `Core/State/` |
| `core/util/` | 한국 시간 도우미(KST) | `Core/Util/` |
| `designsystem/` | 색 · 글꼴 · 버튼 · 입력칸 · 시트 · SF Symbols → 머티리얼 아이콘 매핑 | `DesignSystem/` |
| `model/` | 공통 모델 · API | `Models/` |
| `feature/*` | 기능별 화면 (home, calendar, goal, goalcreation, pattern, settings, taskadd, habit, work, record, notifications, auth, calendarimport) | `Features/*` |

## 빌드 전에

`local.properties` 에 아래 값이 있어야 해요 (git 에 올리지 않는 파일).

```
naleul.baseUrl.debug=https://dev-api.naleul.com
naleul.baseUrl.release=https://...
naleul.kakaoNativeAppKey=...
```

`app/google-services.json` 도 필요해요 (Firebase 푸시 · 분석).

## iOS 와 다른 점

- 애플 로그인 없음 → 카카오 + 둘러보기
- 결제: 백엔드에 Google Play 결제 검증 API 가 없어서 멤버십 화면의 결제 버튼은 "Android 결제는 준비 중" 안내만 해요
- 실천 도우미 알림: AlarmManager 로 예약 (정확한 시각 권한을 쓰지 않아서 몇 분 늦을 수 있어요)
- 외부 캘린더 가져오기: 휴대폰 캘린더(CalendarContract)를 읽어요 (READ_CALENDAR 권한)
