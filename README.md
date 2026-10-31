# 나를 (Naleul)

> 자기관리·메타인지 앱 — 목표를 세우고, 실행하고, 돌아보는 흐름을 하나로 연결합니다.

이 레포는 실제 서비스 중인 나를의 **iOS · Android · Web · Backend** 코드를 포트폴리오용으로 모아 둔 스냅샷입니다.
운영 레포는 비공개로 관리하며, 이 레포에는 공개 시점의 코드만 담겨 있습니다.

## 구조

| 폴더 | 내용 | 스택 |
|---|---|---|
| `backend/` | API 서버 | Spring Boot, JPA, MySQL, Flyway, JWT, FCM |
| `web/` | 웹 클라이언트 | Next.js, TypeScript |
| `ios/` | iOS 앱 + Apple Watch | SwiftUI, watchOS |
| `android/` | Android 앱 | Kotlin, Jetpack Compose |

**인프라:** AWS EC2, GitHub Actions(dev/prod 분리 배포)
**분석:** Amplitude 이벤트 트래킹 (Tracking Plan 기반 이벤트 설계)

## 주요 기능

- 목표 설정 및 AI 기반 목표 계획 생성
- 루틴·할 일 관리, 타임블록 자동 배치
- 미션(함께 하는 목표), 배지
- 주간·월간 회고 리포트
- 카카오 / Apple 로그인, 구독(BM)

## 역할

1인 개발 · 기획 · PM — 기획, 이벤트 설계, 백엔드·웹·iOS·Android 개발, 배포까지 담당
디자인은 공동창업자(UX/UI)와 협업

## 실행

각 폴더의 README를 참고하세요. 비밀값(DB, OAuth, Firebase 등)은 레포에 포함되어 있지 않으며 환경변수로 주입합니다.
