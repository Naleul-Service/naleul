# 나를(Naleul) 웹

Next.js 16 (App Router) · React 19 · TanStack Query · Tailwind CSS v4 · Vercel 배포

## 실행

```bash
cp .env.example .env.local   # 값 채우기
npm install
npm run dev                  # http://localhost:3000
```

## 구조

```
src/
├─ proxy.ts                     # 페이지 접근 보호 + accessToken 선제 갱신 (Next 16의 middleware)
├─ app/
│  ├─ (auth)/login/             # 로그인 화면
│  ├─ redirect/route.ts         # 카카오 로그인 콜백 → 쿠키 저장
│  ├─ (main)/                   # 로그인 후 화면 (사이드바 레이아웃)
│  ├─ api/v1/[...path]/route.ts # BFF 프록시: /api/v1/** → 백엔드 /api/v1/**
│  └─ api/auth/logout/route.ts  # 로그아웃 (쿠키 삭제)
├─ lib/
│  ├─ server/                   # 서버 전용 (백엔드 호출, 쿠키, 토큰 재발급)
│  └─ client/api.ts             # 브라우저용 api.get/post/patch/delete + ApiError
├─ components/ui/               # Button, Chip, Badge, Card, Modal, ConfirmDialog, Toaster
├─ components/layout/           # AppShell, Sidebar, PageHeader
└─ stores/toastStore.ts         # toast.success('...') / toast.error('...')
```

## 요청 흐름

```
브라우저 ──쿠키──▶ Next 서버 /api/v1/...  ──Bearer 토큰──▶ api.naleul.com/api/v1/...
```

- 브라우저는 백엔드에 직접 요청하지 않아요 → CORS 불필요, 토큰은 httpOnly 쿠키라 JS로 탈취 불가
- 백엔드의 HTTP 상태코드와 `message`, `data`를 그대로 전달해요 (422 `violations` 포함)
- 401만 토큰 문제로 처리해요. 403은 비즈니스 에러(예: 무료 플랜 목표 개수 초과)

## 새 API 쓰는 법

Route Handler를 만들 필요 없이 클라이언트에서 바로 호출하면 돼요.

```ts
import { api, isApiError } from '@/lib/client/api'

const turn = await api.post<TurnResponse>('/v1/goal-creation/sessions', { initialMessage })
const active = await api.get<DetailResponse | null>('/v1/goal-creation/sessions/active') // 204 → null

try { ... } catch (e) {
  if (isApiError(e) && e.httpStatus === 422) e.violations // [{ ruleId, path, message }]
}
```
