# 나를(Naleul) · Android

> 나의 패턴으로 목표를 달성하도록 돕는 일정 관리 플랫폼 **나를**의 Android 앱

🔗 [www.naleul.com](https://www.naleul.com) · Android 8.0 (API 26)+

Kotlin · Jetpack Compose · Material 3 · Coroutines · Retrofit + OkHttp · kotlinx.serialization · Navigation Compose

---

## 만든 방식 — iOS 1:1 포팅

iOS 앱(`Naleul-IOS`의 `feature/web-port` 브랜치)을 Kotlin + Jetpack Compose로 **화면 · 이름 · API 호출까지 1:1로** 옮겼습니다.

- iOS의 `Features/Goal/GoalCards.swift`는 여기서 `feature/goal/GoalCards.kt`예요.
- 기능을 고치면 iOS · Android · Web에 같이 반영합니다. 그래서 플랫폼마다 동작과 이벤트가 같아요.
- 1인 개발로 세 플랫폼을 유지하려고 고른 방식입니다. 파일 단위로 대응되니 어느 쪽을 고쳐도 반대편 위치를 바로 찾을 수 있습니다.

## 주요 기능

| 기능 | 내용 |
|---|---|
| 홈 | 오늘 할 일 · 다가오는 마감 · 체크인 · 회고 |
| 캘린더 · 타임테이블 | 월간 캘린더, TimeBlock 타임테이블, AI 일정 수정 |
| AI 목표 생성 | 인터뷰 → 초안 검토 · 수정 → 확정 |
| 목표 | 목표형 · 업무형 · 기본 생활 습관 |
| 할 일 추가 · 브레인 덤프 | 할 일만 적으면 AI가 목표에 자동 연결 |
| 패턴 · 기록 | 시간 사용 패턴, 주간 · 월간 리포트 |
| 외부 캘린더 가져오기 | 휴대폰 캘린더(CalendarContract) 읽기 |
| 알림 | FCM 푸시, 실천 도우미 알림(AlarmManager) |
| 멤버십 | Google Play Billing 구독 → 서버 검증(`/v1/play-billing/verify`) |
| 광고 | 무료 플랜 AdMob 배너 |
| 둘러보기 | 로그인 없이 읽기 전용으로 먼저 체험 |

## 폴더 구조 (`app/src/main/java/com/naleul/naleul/`)

| 폴더 | 내용 | iOS 대응 |
|---|---|---|
| `app/` | 루트 화면 · 탭 · 화면 이동(AppRouter) · 상단 바(NavBar) | `App/` |
| `core/network/` | API 클라이언트 · 토큰 저장/재발급 · 카카오/둘러보기 로그인 · FCM 토큰 등록 | `Core/Network/` |
| `core/state/` | 사용자 정보 · Invalidation(데이터 다시 불러오기) · Mutation · 토스트 · ScreenModel | `Core/State/` |
| `core/billing/` | Google Play Billing 구독 | `Core/Purchase/` |
| `core/analytics/` | AnalyticsService (Amplitude) | `Core/Analytics/` |
| `core/util/` | 한국 시간 도우미(KST) | `Core/Util/` |
| `designsystem/` | 색 · 글꼴 · 버튼 · 입력칸 · 시트 · 광고 배너 · SF Symbols → 머티리얼 아이콘 매핑 | `DesignSystem/` |
| `model/` | 공통 모델 · API | `Models/` |
| `feature/*` | home, calendar, goal, goalcreation, taskadd, habit, work, pattern, record, calendarimport, notifications, settings, auth | `Features/*` |

## iOS와 다른 점

| 항목 | iOS | Android |
|---|---|---|
| 로그인 | 카카오 · Apple · 둘러보기 | 카카오 · 둘러보기 |
| 결제 | StoreKit 2 | Google Play Billing |
| 실천 도우미 알림 | 로컬 알림 | AlarmManager. 정확한 시각 권한을 쓰지 않아서 몇 분 늦을 수 있어요 |
| 외부 캘린더 | EventKit · Google 캘린더 | CalendarContract (`READ_CALENDAR` 권한) |
| 위젯 | 홈 · 잠금 화면 위젯 | 미지원 |

## 테스트 · 운영 서버 분리

빌드 타입마다 바라보는 백엔드가 다릅니다. 테스트 빌드가 운영 데이터를 건드리지 않게 하려는 장치예요.

| 빌드 타입 | 백엔드 | 비고 |
|---|---|---|
| `debug` | 테스트 서버 (`dev-api.naleul.com`) | cleartext 허용 (로컬 서버 테스트용) |
| `release` | 운영 서버 (`api.naleul.com`) | cleartext 차단 |

## 이벤트 트래킹 (Amplitude)

- 모든 이벤트는 `core/analytics/AnalyticsService.kt`를 통해서만 보냅니다.
- 이벤트 이름 · 속성은 Notion **Tracking Plan**을 따르고, iOS · Web과 같은 이름을 씁니다.
- 비활성 버튼 탭(`DisabledTap`)까지 기록해서, 사용자가 어디서 막히는지 봅니다.

## 브랜치

| 브랜치 | 내용 |
|---|---|
| `main` | 배포 기준 |
| `feature/android-port` | iOS 1:1 포팅 작업, 이후 `main`에 머지 예정 |

## 빌드 전에

아래 파일들은 git에 올리지 않습니다.

- `local.properties`

  ```properties
  naleul.baseUrl.debug=https://dev-api.naleul.com
  naleul.baseUrl.release=https://api.naleul.com
  naleul.kakaoNativeAppKey=...
  naleul.amplitudeApiKey=...
  naleul.admobAppId=...       # 비어 있으면 Google 테스트 광고 ID
  naleul.admobBannerId=...
  ```

- `app/google-services.json`: Firebase 푸시

Android Studio로 열고 `app` 모듈을 실행하면 됩니다. (compileSdk 37, minSdk 26, JDK 11 타깃)
