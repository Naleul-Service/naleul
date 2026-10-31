# 나를(Naleul) · iOS

> 나의 패턴으로 목표를 달성하도록 돕는 일정 관리 플랫폼 **나를**의 iOS 앱

🔗 [www.naleul.com](https://www.naleul.com) · App Store 출시 · iOS 17.5+

SwiftUI · Swift Concurrency (async/await) · Observation · StoreKit 2 · WidgetKit

---

## 주요 기능

| 기능 | 내용 |
|---|---|
| 홈 | 오늘 할 일 · 다가오는 마감 · 오늘 체크인 · 회고 |
| 캘린더 · 타임테이블 | 월간 캘린더, TimeBlock 타임테이블(계획 vs 실제 시간), AI 일정 수정, 미완료 자동 이월 |
| AI 목표 생성 | 인터뷰 → 생성 중 → 초안 검토 · 수정 → 확정. 중간에 나가도 이어서 진행 가능 |
| 목표 | 목표형 · 업무형(주제 · Task 보드) · 기본 생활 습관(잔디맵) |
| 할 일 추가 · 브레인 덤프 | 할 일만 적으면 AI가 목표에 자동 연결 |
| 패턴 · 기록 | 시간 사용 패턴, 목표별 시간 통계, 주간 · 월간 리포트 |
| 외부 캘린더 가져오기 | 기기 캘린더 · Google 캘린더 연동 |
| 위젯 | 홈 화면 · 잠금 화면 위젯 (App Group으로 앱과 데이터 동기화) |
| 알림 | FCM 푸시, 루틴 · 습관 실천 도우미 알림 |
| 멤버십 | StoreKit 2 구독 (Free / Pro), 서버 검증 |
| 둘러보기 | 로그인 없이 읽기 전용으로 먼저 체험 → 저장할 때 로그인 안내 |

## 구조

```
naleul/
├─ naleul/                     # 앱 타깃
│  ├─ App/                     # 진입점 · RootView · MainTabView · AppRouter(화면 이동)
│  ├─ Core/
│  │  ├─ Network/              # APIClient · 토큰 저장/재발급 · 로그인(카카오 · Apple · 둘러보기) · FCM 토큰
│  │  ├─ State/                # UserStore · Invalidation(데이터 다시 불러오기) · Mutation · Toast · LoginPrompt
│  │  ├─ Purchase/             # StoreKit 2 구독 · 서버 영수증 검증
│  │  ├─ Analytics/            # AnalyticsService (Amplitude)
│  │  ├─ Widget/               # 위젯 데이터 동기화
│  │  └─ Util/                 # KST(한국 시간) · 디코딩 안전장치
│  ├─ DesignSystem/            # 색 · 글꼴 토큰, 버튼 · 카드 · 입력칸 · 시트 · 광고 배너
│  ├─ Models/                  # 공통 모델 · API (Goal, Timetable, BrainDump …)
│  └─ Features/                # Home, Calendar, Goal, GoalCreation, TaskAdd, Habit, Work,
│                              # Pattern, Record, CalendarImport, Notifications, Settings, Auth
├─ NaleulWidget/               # 위젯 익스텐션 (홈 · 잠금 화면)
└─ Shared/                     # 앱 · 위젯 공용 코드
```

- 기능마다 `Screen`(View) + `Models` + `API` + `Analytics` 파일을 같은 폴더에 둡니다. 한 기능을 고칠 때 한 폴더만 보면 돼요.
- 상태 관리는 Swift Observation(`@Observable`)을 씁니다. 한 화면에서 데이터를 바꾸면 `Invalidation`으로 관련 화면이 다시 불러와요.
- 네트워크는 `APIClient` 하나로 처리합니다. 401이 오면 토큰을 재발급하고 원래 요청을 자동으로 다시 보내요.

## 플랫폼 공통 설계

Android 앱은 이 iOS 앱을 **화면 · 이름 · API 호출까지 1:1로** 옮겨 만들었습니다.
예를 들어 iOS의 `Features/Goal/GoalCards.swift`는 Android에서 `feature/goal/GoalCards.kt`예요. 기능을 고치면 iOS · Android · Web에 같이 반영합니다.

## 이벤트 트래킹 (Amplitude)

- 모든 이벤트는 `Core/Analytics/AnalyticsService.swift`를 통해서만 보냅니다. 기능별 `*Analytics.swift`에 그 화면의 이벤트를 모아둬요.
- 이벤트 이름 · 속성은 Notion **Tracking Plan**을 따르고, Web · Android와 같은 이름을 씁니다.
- 퍼널(온보딩 → 목표 생성 → Task 완료)과 Aha-moment 지표를 측정하는 데 씁니다.

## 의존성 (Swift Package Manager)

| 패키지 | 용도 |
|---|---|
| Kakao iOS SDK | 카카오 로그인 |
| Firebase iOS SDK | FCM 푸시 |
| Amplitude-Swift | 이벤트 트래킹 |
| Google Mobile Ads | 무료 플랜 배너 광고 |

## 브랜치

| 브랜치 | 내용 |
|---|---|
| `main` | App Store 배포 기준 |
| `feature/web-port` | 웹 2.0 기능(AI 목표 생성 · 타임테이블 · 브레인 덤프 등) 이식 작업, 이후 `main`에 머지 예정 |

## 빌드 전에

아래 두 파일은 git에 올리지 않습니다. 직접 만들어 넣어야 해요.

- `naleul/naleul/Core/Config.swift`: API 주소 · 카카오 앱 키 · Amplitude 키 · Google OAuth 클라이언트 ID

  ```swift
  enum Config {
      static let baseURL = "https://dev-api.naleul.com"   // 운영: https://api.naleul.com
      static let kakaoNativeAppKey = "..."
      static let amplitudeApiKey = "..."
      static let googleIOSClientId = "..."
  }
  ```

- `naleul/naleul/Core/Config.xcconfig`

`naleul.xcodeproj`를 Xcode로 열고 `naleul` 스킴으로 실행하면 됩니다.
