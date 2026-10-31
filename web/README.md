# 나를(Naleul) · Web

> 나의 패턴으로 목표를 달성하도록 돕는 일정 관리 플랫폼 **나를**의 웹 클라이언트

🔗 [www.naleul.com](https://www.naleul.com)

Next.js 16 (App Router) · React 19 · TypeScript · TanStack Query · Zustand · Tailwind CSS v4 · Vercel

---

## 목차

- [주요 화면](#주요-화면)
- [브랜치 전략 · 배포 환경 분리 (develop / main)](#브랜치-전략--배포-환경-분리-develop--main)
- [아키텍처 — BFF 프록시](#아키텍처--bff-프록시)
- [인증 · 둘러보기 모드](#인증--둘러보기-모드)
- [이벤트 트래킹 (Amplitude)](#이벤트-트래킹-amplitude)
- [폴더 구조](#폴더-구조)
- [로컬 실행](#로컬-실행)

---

## 주요 화면

| 화면 | 내용 |
|---|---|
| 홈 | 오늘 할 일 · 다가오는 마감 · 오늘 회고 |
| 캘린더 · 타임테이블 | 일/주/월 캘린더, TimeBlock 자동 배치, AI 일정 수정 |
| 목표 | 목표형 · 업무형 · 습관, AI 목표 생성(인터뷰 → 초안 → 확정), 진행 그래프 |
| 브레인 덤프 | 할 일만 적으면 AI가 목표에 자동 연결 |
| 패턴 · 회고 | 시간 사용 패턴, 주간 · 월간 리포트 |
| 설정 | 멤버십(토스페이먼츠), 외부 캘린더 가져오기, 생활 패턴, 알림, 탈퇴 |
| 운영 대시보드 | 서비스 지표 · Aha-moment · AI 관심도 · AI 초안 품질 (관리자 전용) |
| 공개 페이지 | 서비스 소개 · 개인정보처리방침 · 이용약관 · 환불 정책 |

---

## 브랜치 전략 · 배포 환경 분리 (develop / main)

**Vercel 프로젝트를 2개로 나눠** 테스트와 운영을 완전히 분리했습니다.
기능은 `develop`에서 테스트 서버로 확인하고, 문제가 없을 때만 `main`으로 머지해 운영에 반영합니다.

```
feature/* ──▶ develop ──(Vercel 자동 배포)──▶ 테스트 웹  naleul-web-dev  ──▶ dev-api.naleul.com
                 │
                 └──merge──▶ main ──(Vercel 자동 배포)──▶ 운영 웹  www.naleul.com ──▶ api.naleul.com
```

| | `develop` (테스트 · 내부) | `main` (운영 · 외부) |
|---|---|---|
| Vercel 프로젝트 | `naleul-web-dev` | 운영 프로젝트 |
| 도메인 | Vercel 테스트 도메인 | `naleul.com` · `www.naleul.com` |
| 백엔드 (`API_BASE_URL`) | 테스트 API `dev-api.naleul.com` | 운영 API `api.naleul.com` |
| Amplitude | 테스트 프로젝트 키 (`NEXT_PUBLIC_AMPLITUDE_DEV_API_KEY`) | 운영 키 (`NEXT_PUBLIC_AMPLITUDE_API_KEY`) |
| 결제 | 토스 테스트 키 | 토스 라이브 키 |

**운영 지표를 지키는 장치**

- 분석 키를 환경변수가 아니라 **접속한 도메인**으로 고릅니다. 운영 도메인(`NEXT_PUBLIC_ANALYTICS_PROD_HOSTS`)일 때만 운영 키를 씁니다. 그래서 두 Vercel 프로젝트에 같은 환경변수가 들어가도 테스트 트래픽이 운영 집계에 섞이지 않아요.
- 운영자 계정(ADMIN 또는 `NEXT_PUBLIC_ANALYTICS_EXCLUDED_USER_IDS`)이 쓰는 브라우저는 운영 집계에서 빠집니다.
- 미리보기(Preview)와 로컬은 테스트 키로 보내고, 테스트 키가 없으면 아무것도 보내지 않습니다.

> 백엔드도 같은 기준으로 `develop` → 테스트 서버, `main` → 운영 서버(Blue-Green 무중단 배포)로 나뉘어 있습니다.

---

## 아키텍처 — BFF 프록시

```
브라우저 ──httpOnly 쿠키──▶ Next 서버 /api/v1/** ──Bearer 토큰──▶ 백엔드 /api/v1/**
```

- 브라우저는 백엔드를 직접 호출하지 않습니다. 그래서 CORS 설정이 필요 없어요.
- 토큰은 **httpOnly 쿠키**에만 있어서 JS로 탈취할 수 없습니다.
- `app/api/v1/[...path]/route.ts` 하나로 모든 API를 프록시합니다. 새 API가 생겨도 Route Handler를 따로 만들지 않고 클라이언트에서 바로 호출하면 돼요.
- 백엔드의 상태코드, `message`, `data`를 그대로 전달합니다. 422 `violations`도 포함돼요.
- 401만 토큰 문제로 처리합니다. 403은 비즈니스 에러(예: 무료 플랜 한도 초과)로 처리해요.

```ts
import { api, isApiError } from '@/lib/client/api'

const turn = await api.post<TurnResponse>('/v1/goal-creation/sessions', { initialMessage })
const active = await api.get<DetailResponse | null>('/v1/goal-creation/sessions/active') // 204 → null
```

## 인증 · 둘러보기 모드

- **로그인:** 카카오 · Apple · Google OAuth. 콜백을 서버에서 처리하고 쿠키에 저장합니다.
- **`proxy.ts`** (Next 16의 middleware)가 페이지 이동마다 실행됩니다.
  - **로그인 전:** 읽기 전용 **둘러보기(게스트) 토큰**을 발급해 바로 앱을 보여줍니다. 저장하려고 할 때 로그인을 안내해요(백엔드 `401 GUEST_LOGIN_REQUIRED`). 가입 전에 가치를 먼저 경험하게 하려는 장치입니다.
  - **토큰 만료 직전:** refreshToken으로 **미리 재발급**합니다. 새 토큰은 브라우저 응답과 현재 요청 양쪽에 넣어요. 그래야 첫 화면의 서버 컴포넌트가 옛 쿠키를 읽고 실패하는 일이 없습니다.
- 공개 페이지(`/about`, `/privacy`, `/terms`, `/refund`)는 토큰 없이 열립니다.

## 이벤트 트래킹 (Amplitude)

- 모든 이벤트는 `src/lib/client/analytics.ts` 한 곳에서 관리합니다. 화면에서 SDK를 직접 import하지 않아요. iOS · Android의 `AnalyticsService`와 같은 구조입니다.
- 이벤트 이름 · 속성은 Notion **Tracking Plan**을 따릅니다. 형식은 `page.action`(예: `login_modal.kakao_click`)이에요.
- 이메일 · 닉네임 · 자유 입력 원문은 보내지 않습니다.
- 페이지를 떠나기 직전 이벤트는 beacon으로 보내고, 로그아웃하면 식별자를 초기화합니다.

---

## 폴더 구조

```
naleul-web/src/
├─ proxy.ts                     # 접근 제어 · 둘러보기 토큰 · accessToken 선제 갱신
├─ app/
│  ├─ (auth)/login/             # 로그인
│  ├─ (main)/                   # 로그인 후 화면 (홈 · 캘린더 · 목표 · 패턴 · 회고 · 설정)
│  ├─ (flow)/goal/              # AI 목표 생성 플로우
│  ├─ (public)/                 # 소개 · 약관 · 개인정보 · 환불
│  ├─ redirect/                 # OAuth 콜백
│  └─ api/
│     ├─ v1/[...path]/          # BFF 프록시
│     ├─ auth/                  # 카카오 · Apple 로그인, 로그아웃
│     └─ billing/               # 토스페이먼츠 결제
├─ features/                    # 기능별 화면 · 훅 · API (goal, goal-creation, timetable, brain-dump, billing, admin-dashboard …)
├─ components/                  # ui · layout · auth · ads · analytics
├─ lib/
│  ├─ server/                   # 서버 전용 (백엔드 호출, 쿠키, 토큰 재발급)
│  └─ client/                   # api 클라이언트 · analytics
└─ stores/                      # Zustand (toast)
```

## 로컬 실행

```bash
cd naleul-web
cp .env.example .env.local   # API_BASE_URL, OAuth · Amplitude · 토스 키 등 채우기
npm install
npm run dev                  # http://localhost:3000

npm run lint && npm run typecheck
```

> `.env*`는 gitignore 대상입니다. `NEXT_PUBLIC_*`는 브라우저에 노출되는 공개 키만 씁니다.
