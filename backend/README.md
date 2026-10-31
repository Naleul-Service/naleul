# Naleul (나를) Backend

자기관리/메타인지 iOS 앱 **나를(Naleul)**의 백엔드 서비스입니다. iOS(SwiftUI) / watchOS(NaleulWatch) 클라이언트에 REST API를 제공합니다.

이 문서는 내부 참고용이면서 동시에 새로 합류하는 개발자가 서비스 구조와 배포 흐름을 파악할 수 있도록 작성되었습니다.

---

## 목차

- [기술 스택](#기술-스택)
- [도메인 구조](#도메인-구조)
- [인프라](#인프라)
- [배포 방식 (Blue-Green)](#배포-방식-blue-green)
- [nginx 라우팅](#nginx-라우팅)
- [CI/CD](#cicd)
- [로그 / 모니터링](#로그--모니터링)
- [트러블슈팅 노트](#트러블슈팅-노트)

---

## 기술 스택

| 영역 | 스택 |
|---|---|
| 언어 / 프레임워크 | Java 21, Spring Boot |
| ORM | JPA / Hibernate |
| DB | MySQL 8.4.8 (AWS RDS) |
| DB 마이그레이션 | Flyway (운영: `ddl-auto: validate`) |
| 인증 | Kakao / Apple OAuth, JWT |
| 푸시 알림 | Firebase Admin SDK (FCM) |
| 결제 | Apple IAP (StoreKit 2, App Store Server Notifications) |
| AI 리포트 | Claude Haiku (Anthropic API) — 주간/월간/목표 완료 리포트 생성 |
| 배포 | AWS EC2 (Amazon Linux 2023), GitHub Actions |
| 리버스 프록시 | nginx + Let's Encrypt(Certbot) |

## 도메인 구조

계층 구조: `GoalCategory → GeneralCategory → Routine → Task`

`domain` 패키지 하위에 도메인별로 구성되어 있으며, 각 도메인의 API 기본 경로는 다음과 같습니다.

| 도메인 패키지 | API 기본 경로 | 설명 |
|---|---|---|
| goalCategory | `/api/v1/goal-categories` | 최상위 목표 카테고리. `goalMode` 로 3가지 형태 — 목표형(ACHIEVEMENT, 자동 만료 대상) · 업무형(RECORD) · 기본 생활 습관(HABIT, 사용자당 1개 자동 생성) |
| habit | `/api/v1/habits` | 기본 생활 습관 — 했는지만 체크하는 습관, 쉬기 · 잔디맵 · 알림 (V52) |
| home | `/api/v1/home/today?date=` | 홈 (date 없으면 오늘, 홈에서 날짜 ‹ › 이동 · 오늘 ±1년) — 다가오는 마감(Task · 업무형 주제 · 목표형 종료일) + 목표별 오늘 끝낸 일 · 못 한 일 · 오늘 회고 |
| work | `/api/v1/goal-categories/{id}/work-*` | 업무형 상세 — 주제(주제마다 마감 · 로드맵, V53) · Task 보드(시작 전/진행 중/완료) · 오늘 할 일 · 아카이브 · 공유 텍스트 (V52) |
| goalProgress | `/api/v1/goal-categories/{id}/progress`, `/metrics` | 목표형 진행 그래프 — 메인 수치 + 추가 그래프(V52) |
| generalCategory | `/api/v1/general-categories` | GoalCategory 하위 일반 카테고리 |
| category | *(별도 API 없음 — 내부 컴포넌트)* | `CategoryFinder`가 GoalCategory/GeneralCategory 조회 및 소유권 검증(요청 유저 소유가 아니면 예외) 공통 로직 담당 |
| routine | `/api/v1/routines` | 반복되는 루틴 단위 |
| routineRepeatDay | *(별도 API 없음 — Routine 하위 엔티티)* | 루틴의 반복 요일(`DayOfWeek`) 저장. `(routine_id, day_of_week)` 조합에 DB 유니크 제약으로 같은 루틴에 같은 요일 중복 저장 방지 |
| task | `/api/v1/tasks` | 실제 수행 단위, watchOS와 동기화되는 일정 |
| actualTask | `/api/v1/task-actuals` | Task의 실제 수행 기록 (Planned vs Actual) |
| mission | `/api/v1/missions` | 호스트/참가자 역할 기반 미션 참여 기능 |
| notification | `/api/v1/notifications` | 인앱 알림 히스토리 + 카테고리별 푸시 토글 |
| fcm | `/api/v1/device-token` | FCM 디바이스 토큰 등록/관리 |
| user | `/api/v1/users` | 사용자 정보, 회원 탈퇴 포함 |
| userColor | `/api/v1/user-colors` | 사용자별 색상 테마 설정 |
| purchase | `/api/v1/iap/` | Apple IAP 결제/구독 처리 |
| chart | `/api/v1/charts` | 통계 차트 |
| retrospective | `/api/v1/retrospectives` | 회고 기록 |
| autobiography | `/api/v1/autobiography` | 자서전(자기 서사) 기능 |
| dreamBoard | `/api/v1/dream-boards` | 드림보드(비전보드) |
| ideaBoard | `/api/v1/idea-boards` | 아이디어보드 |
| s3 | `/api/v1/images` | 이미지 업로드(S3 연동) |

> HTTP method 및 세부 엔드포인트(하위 경로, 파라미터 등)는 추후 컨트롤러 기준으로 채워 넣을 예정입니다.

## 인프라

| 항목 | 내용 |
|---|---|
| 리전 | `ap-northeast-2` (서울) |
| EC2 | Amazon Linux 2023, 메모리 ~1GB(961Mi), 디스크 8GB (67% 사용, 여유 2.7GB) |
| DB | AWS RDS MySQL 8.4.8, `naleul-db`, `db.t4g.micro`, 단일 AZ, 20GiB, 월 약 $21 |
| 리버스 프록시 | nginx 1.28.3 |
| SSL | Let's Encrypt (Certbot) |

과거에는 EC2 한 대에서 Spring Boot + MySQL + nginx를 함께 운영했으나, DB를 RDS로 분리하여 EC2의 디스크/메모리 부담을 줄였습니다.

## 배포 방식 (Blue-Green)

무중단 배포를 위해 두 개의 포트를 번갈아 사용하는 Blue-Green 방식을 채택했습니다.

- **포트**: `8081` / `8082` 중 유휴 포트에 새 버전을 기동
- **프로세스 관리**: systemd 템플릿 유닛 `naleul@.service` → `naleul@8081`, `naleul@8082`로 개별 기동/종료
- **JAR 참조**: 각 포트는 `current-8081.jar` / `current-8082.jar` 심볼릭 링크를 바라봄. 배포 시 타임스탬프가 붙은 새 jar로 심볼릭 링크만 교체(덮어쓰기 없음)
- **JVM 옵션**: `-Xmx400m` (EC2 메모리 제약 고려)
- **자동 복구**: `Restart=on-failure`, `RestartSec=5` — 크래시/재부팅 시 자동 재기동

### 배포 흐름

1. 유휴 포트(8081 또는 8082)에 새 jar로 애플리케이션 기동
2. 헬스체크 통과 확인 (최대 60초 대기)
3. nginx upstream을 새 포트로 전환 후 `nginx reload` → 무중단 트래픽 전환
4. 기존(구) 포트의 프로세스 종료
5. 오래된 jar 파일 중 최신 3개를 초과하는 파일 정리

## nginx 라우팅

도메인별 설정은 전부 `conf.d/`로 위임하고, `nginx.conf`에는 전역 설정만 남깁니다.

| 파일 | 역할 |
|---|---|
| `/etc/nginx/conf.d/naleul-upstream.conf` | `upstream springboot { server 127.0.0.1:8081 또는 8082; }` — 배포 스크립트가 이 파일의 포트만 갱신 |
| `/etc/nginx/conf.d/naleul.conf` | `www.naleul.com` → `http://springboot` |
| `/etc/nginx/conf.d/api-naleul.conf` | `api.naleul.com` → `http://springboot` (기존 `nginx.conf`에 하드코딩되어 있던 설정을 분리하여 upstream 방식으로 통일) |

## CI/CD

**GitHub Actions**: `main` 브랜치 push 시 자동 배포

```
main push
  → Gradle 빌드 (JDK 21)
  → scp로 jar를 EC2에 전송
  → SSH 접속하여 deploy-bluegreen.sh 실행
  → (위 "배포 흐름" 참고)
```

- `sudoers.d/naleul-deploy`: `systemctl start/stop naleul@*`, `switch-upstream.sh`를 비밀번호 없이 실행 허용 (배포 스크립트가 별도 인증 없이 서비스 제어 가능하도록)

## 로그 / 모니터링

| 항목 | 내용 |
|---|---|
| 앱 로그 | `/home/ec2-user/app/deploy/app-8081.log`, `app-8082.log` |
| Hibernate SQL 로그 | 비활성화 (`show-sql: false`, `org.hibernate.SQL: WARN`) — 운영 환경 로그량 절감 |
| journald | `SystemMaxUse=300M` 제한 — 디스크 재발 방지 |

## 트러블슈팅 노트

과거 발생했던 주요 이슈와 원인을 기록해 반복을 방지합니다.

- **FCM 401 에러**: 앱이 Development 프로비저닝 프로파일로 아카이브되었고, Firebase Console에 Production APNs 키가 등록되지 않아 발생. Distribution 프로파일 + Production APNs 키 등록으로 해결.
- **디스크 사용률 93% 근접**: EC2 한 대에 MySQL까지 함께 운영하며 발생. RDS 분리 + jar 파일 3개 초과분 자동 정리 + journald 용량 제한으로 해결.
