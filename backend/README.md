# 나를(Naleul) · Backend

> 나의 패턴으로 목표를 달성하도록 돕는 일정 관리 플랫폼 **나를**의 API 서버
> iOS · Android · Web 세 클라이언트에 같은 REST API(`/api/v1/**`)를 제공합니다.

🔗 [www.naleul.com](https://www.naleul.com) · API `api.naleul.com` (운영) / `dev-api.naleul.com` (테스트)

---

## 목차

- [기술 스택](#기술-스택)
- [아키텍처](#아키텍처)
- [브랜치 전략 · 배포 환경 분리 (develop / main)](#브랜치-전략--배포-환경-분리-develop--main)
- [운영 배포 — Blue-Green 무중단 배포](#운영-배포--blue-green-무중단-배포)
- [도메인 구조](#도메인-구조)
- [AI 기능](#ai-기능)
- [결제](#결제)
- [인프라 · 로그](#인프라--로그)
- [로컬 실행](#로컬-실행)
- [트러블슈팅 노트](#트러블슈팅-노트)

---

## 기술 스택

| 영역 | 스택 |
|---|---|
| 언어 / 프레임워크 | Java 21, Spring Boot 3.4 |
| ORM / DB | Spring Data JPA (Hibernate), MySQL 8.4 (AWS RDS) |
| DB 마이그레이션 | Flyway (`V1` ~ `V67`, 운영 `ddl-auto: validate`) |
| 인증 | Kakao · Apple · Google OAuth, JWT (Access / Refresh), 둘러보기(게스트) 토큰 |
| AI | Anthropic Claude API — 목표 생성 · 타임테이블 · 브레인 덤프 · 리포트 |
| 결제 | Apple IAP (StoreKit 2 · App Store Server Notifications), Google Play Billing, 토스페이먼츠(웹) |
| 푸시 | Firebase Admin SDK (FCM) |
| 스토리지 | AWS S3 (Spring Cloud AWS) |
| 배포 | AWS EC2 (Amazon Linux 2023), GitHub Actions, nginx + Let's Encrypt |
| 운영 대시보드 | AWS Cost Explorer 연동 (서버 비용 자동 집계), AI 호출 비용 · 품질 지표 |

## 아키텍처

```
 iOS (SwiftUI) ─┐
 Android (Compose) ─┼──▶ nginx (api.naleul.com) ──▶ Spring Boot ──▶ RDS MySQL
 Web (Next.js BFF) ─┘                                  │
                                                       ├──▶ Claude API (AI 목표 생성 · 리포트)
                                                       ├──▶ FCM (푸시) · S3 (이미지)
                                                       └──▶ Apple / Google / 토스 (결제 검증)
```

- 패키지 구조: `domain/{도메인}/{controller, service, repository, entity, dto, ...}` + `global/{security, jwt, exception, scheduler, ...}`
- 핵심 계층: `GoalCategory(목표) → GeneralCategory(세부 목표) → Milestone → Routine → Task`

---

## 브랜치 전략 · 배포 환경 분리 (develop / main)

테스트 서버와 운영 서버를 **브랜치 단위로 완전히 분리**했습니다.
기능은 먼저 `develop`에서 테스트 서버로 검증하고, 문제가 없을 때만 `main`으로 머지해 운영에 반영합니다.

```
feature/* ──PR──▶ develop ──(자동 배포)──▶ 테스트 서버  dev-api.naleul.com
                     │
                     └──merge──▶ main ──(자동 배포)──▶ 운영 서버  api.naleul.com
```

| | `develop` (테스트 · 내부) | `main` (운영 · 외부) |
|---|---|---|
| 워크플로 | `.github/workflows/deploy-dev.yml` | `.github/workflows/deploy.yml` |
| 트리거 | `develop` push + 수동 실행(`workflow_dispatch`) | `main` push |
| 서버 | 테스트 EC2 (`dev-api.naleul.com`) | 운영 EC2 (`api.naleul.com`) |
| 배포 방식 | 단일 인스턴스 재시작 (`systemctl restart naleul-dev`) | **Blue-Green 무중단 배포** |
| 연속 push | `concurrency`로 이전 배포 취소, 마지막 커밋만 배포 | 순차 배포 |
| 헬스체크 | 최대 180초 대기, 실패 시 최근 로그 80줄 출력 후 워크플로 실패 | 최대 60초 대기, 통과해야 트래픽 전환 |
| 접속 정보 | `DEV_EC2_HOST` · `DEV_EC2_USERNAME` · `DEV_EC2_SSH_KEY` | `EC2_HOST` · `EC2_USERNAME` · `EC2_SSH_KEY` |

> 서버 접속 정보는 모두 GitHub Repository Secrets에 있고, 코드에는 들어 있지 않습니다.

### 테스트 서버 배포 흐름 (`deploy-dev.yml`)

1. JDK 21로 `bootJar` 빌드 (테스트 생략, Gradle 캐시 사용)
2. jar를 `naleul.jar`로 이름을 고정해 테스트 EC2의 `incoming/` 폴더로 전송
3. **실행 중인 jar를 덮어쓰지 않고** `mv`로 교체 → 실행 중 프로세스 보호
4. `systemctl restart naleul-dev`
5. 5초 간격으로 API를 호출해 `200 / 401 / 403` 응답이 오면 구동 완료로 판단

### 왜 나눴나

- 운영 사용자에게 검증되지 않은 기능이 바로 노출되는 것을 막기 위해서입니다.
- 클라이언트도 같은 기준으로 나뉘어 있습니다. Web은 Vercel 프로젝트 2개, Android는 debug/release `BASE_URL`로 나눠서 각각 테스트 · 운영 API를 바라봅니다.
- Amplitude 분석 데이터도 테스트 · 운영 프로젝트 키를 따로 써서, 테스트 트래픽이 운영 지표에 섞이지 않게 했습니다.

---

## 운영 배포 — Blue-Green 무중단 배포

두 포트를 번갈아 쓰면서 사용자 요청이 끊기지 않게 새 버전으로 전환합니다.

- **포트:** `8081` / `8082` 중 쉬고 있는 포트에 새 버전을 띄움
- **프로세스:** systemd 템플릿 유닛 `naleul@.service` → `naleul@8081`, `naleul@8082`
- **jar 교체:** `current-8081.jar` / `current-8082.jar` 심볼릭 링크만 새 jar로 교체 (덮어쓰기 없음)
- **JVM:** `-Xmx400m` (EC2 메모리 제약)
- **자동 복구:** `Restart=on-failure`, `RestartSec=5`

**배포 흐름** (`deploy.yml` → `deploy-bluegreen.sh`)

1. GitHub Actions에서 빌드한 jar를 운영 EC2로 전송
2. 쉬고 있는 포트에 새 버전 기동
3. 헬스체크 통과 확인 (최대 60초)
4. nginx upstream을 새 포트로 바꾸고 `nginx reload` → 무중단 전환
5. 이전 포트 프로세스 종료
6. 최신 3개를 넘는 오래된 jar 정리

**nginx 라우팅**

| 파일 | 역할 |
|---|---|
| `conf.d/naleul-upstream.conf` | `upstream springboot { server 127.0.0.1:8081 or 8082; }` — 배포 스크립트가 포트만 갱신 |
| `conf.d/api-naleul.conf` | `api.naleul.com` → `http://springboot` |
| `conf.d/naleul.conf` | `www.naleul.com` → `http://springboot` |

배포 계정은 `sudoers.d/naleul-deploy`로 `systemctl start/stop naleul@*`와 upstream 전환 스크립트만 비밀번호 없이 실행할 수 있습니다.

---

## 도메인 구조

`domain/` 아래 도메인별 패키지로 나뉘어 있습니다. (모든 경로는 `/api/v1` 하위)

### 목표 · 일정

| 도메인 | 경로 | 설명 |
|---|---|---|
| goalCategory | `/goal-categories` | 최상위 목표. `goalMode` 3종 — 목표형(ACHIEVEMENT) · 업무형(RECORD) · 기본 생활 습관(HABIT) |
| generalCategory · milestone | `/general-categories` | 세부 목표 · 마일스톤 |
| routine · routineRepeatDay | `/routines` | 반복 루틴, 반복 요일 (`(routine_id, day_of_week)` 유니크) |
| task · actualTask | `/tasks`, `/task-actuals` | 실제 수행 단위, 계획 대비 실제 기록 (Planned vs Actual) |
| habit | `/habits` | 체크형 습관 · 잔디맵 · 리마인더 |
| work | `/goal-categories/{id}/work-*` | 업무형 목표 — 주제 · Task 보드 · 아카이브 |
| goalProgress · goalOutcome | `/goal-categories/{id}/metrics`, `/goal-outcomes` | 목표 진행 그래프, 목표 종료 회고 |
| home | `/home` | 홈 — 다가오는 마감, 목표별 오늘 한 일 · 못 한 일 |
| planner · timetable | `/timetable`, `/daily-plans`, `/planner-settings` | TimeBlock 자동 배치 타임테이블 |
| externalCalendar | `/external-calendar` | 외부 캘린더 가져오기 |

### AI

| 도메인 | 경로 | 설명 |
|---|---|---|
| goalCreation | `/goal-creation/sessions` | 인터뷰형 AI 목표 생성 → 초안 → 확정 |
| goalEdit · scheduleEdit | `/goal-categories/{id}/ai-edit`, `/timetable/ai-edit` | 목표 · 일정 AI 수정 |
| brainDump | `/brain-dump` | 할 일만 적으면 목표에 자동 연결 |
| pattern · lifePattern | `/patterns`, `/life-patterns` | 시간 사용 패턴 분석 |
| aiUsage | `/ai-usage` | 사용자별 AI 사용량 · 한도(주/월 단위) |

### 회고 · 커뮤니티 · 계정

| 도메인 | 경로 | 설명 |
|---|---|---|
| retrospective | `/retrospectives`, `/reports/weekly`, `/reports/monthly` | 회고, 주간 · 월간 AI 리포트 |
| mission | `/missions` | 친구와 함께하는 목표(호스트 · 참가자), 배지 · 콕 찌르기 |
| autobiography · dreamBoard · ideaBoard | `/autobiography`, `/dream-boards`, `/idea-boards` | 자서전 · 드림보드 · 아이디어보드 |
| notification · fcm | `/notifications`, `/notification-settings`, `/device-token` | 인앱 알림, 카테고리별 푸시 설정, FCM 토큰 |
| user · userColor | `/auth`, `/users`, `/user-colors` | 로그인 · 회원 · 약관 동의 · 탈퇴, 색상 테마 |
| purchase · playBilling · webBilling | `/iap`, `/play-billing`, `/web-billing` | 플랫폼별 구독 결제 |
| s3 | `/images` | 이미지 업로드 |

### 운영

| 도메인 | 경로 | 설명 |
|---|---|---|
| admin · aiQuality | `/admin/dashboard/**` | 운영 대시보드 — 서비스 지표 · Aha-moment · AI 관심도 · AI 초안 품질 · AI 비용 |
| activity | `/activities` | 사용자 활동 기록 (지표 집계용) |

**스케줄러:** 목표 기한 만료 처리, 프리미엄 만료, 루틴 · 습관 리마인더 푸시 등

---

## AI 기능

| 기능 | 내용 |
|---|---|
| AI 목표 생성 | 최대 5문항 인터뷰로 `목표 → 세부 목표 → 루틴 → Task` 초안 생성. 사용자는 승인 · 수정만 |
| TimeBlock 자동 배치 | 루틴 · 요일 · 시간대 패턴을 바탕으로 타임테이블에 일정 배치 |
| 브레인 덤프 | 자유 입력한 할 일을 파싱해 목표에 자동 연결 |
| 리포트 | 주간 · 월간 · 목표 완료 리포트 |

- 모델은 용도별로 나눠 씁니다. 계획 생성은 상위 모델, 리포트 같은 가벼운 작업은 Haiku 계열을 써요. 둘 다 환경변수로 교체할 수 있습니다.
- 프롬프트는 `src/main/resources/ai/`에 기능별로 분리했습니다.
- 모든 AI 호출의 토큰 사용량과 비용을 기록합니다. 운영 대시보드에서 기능별 초안 승인 · 수정 · 삭제 · 재생성 비율과 함께 봅니다.

## 결제

| 플랫폼 | 방식 |
|---|---|
| iOS | Apple IAP (StoreKit 2) + App Store Server Notifications로 구독 상태 동기화 |
| Android | Google Play Billing 구매 검증 |
| Web | 토스페이먼츠 |

플랫폼이 달라도 같은 사용자의 프리미엄 상태는 한 곳에서 관리합니다. 만료는 스케줄러가 처리합니다.

---

## 인프라 · 로그

| 항목 | 내용 |
|---|---|
| 리전 | `ap-northeast-2` (서울) |
| EC2 | Amazon Linux 2023, 운영 · 테스트 서버 분리 |
| DB | AWS RDS MySQL 8.4 (`db.t4g.micro`) |
| 리버스 프록시 · SSL | nginx, Let's Encrypt (Certbot) |
| 앱 로그 | 운영 `app-8081.log` / `app-8082.log`, 테스트 `logs/app.log` |
| 로그 관리 | 운영 SQL 로그 비활성화, journald `SystemMaxUse=300M` |

처음에는 EC2 한 대에서 Spring Boot · MySQL · nginx를 함께 운영했습니다. 이후 DB를 RDS로 분리해 EC2의 디스크와 메모리 부담을 줄였습니다.

## 로컬 실행

```bash
# src/main/resources/application.yml (gitignore) 에 DB · JWT · OAuth · Anthropic · 결제 키 설정
# src/main/resources/firebase-service-account.json (gitignore) 배치
./gradlew bootRun --args='--spring.profiles.active=local'

# 테스트 (H2 인메모리, application-test.yml)
./gradlew test
```

> `*.yml`, `firebase-service-account.json`, `.env`는 모두 gitignore 대상입니다. 비밀값은 저장소에 커밋하지 않습니다.

## 트러블슈팅 노트

- **FCM 401:** 앱이 Development 프로비저닝으로 아카이브됐고, Firebase에 Production APNs 키가 없었음 → Distribution 프로파일 + Production APNs 키 등록으로 해결
- **디스크 사용률 93%:** EC2 한 대에 MySQL까지 운영한 것이 원인 → RDS 분리, jar 3개 초과분 자동 정리, journald 용량 제한으로 해결
- **실행 중 jar 덮어쓰기:** 배포 중 실행 파일을 덮어쓰면 프로세스가 비정상 종료될 수 있음 → 운영은 심볼릭 링크 교체, 테스트는 `incoming/` 전송 후 `mv`로 교체
