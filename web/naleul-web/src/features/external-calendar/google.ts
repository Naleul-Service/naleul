/**
 * 구글 캘린더 읽기 (브라우저에서 직접).
 *
 * 왜 서버가 아니라 브라우저에서 읽나:
 *  - "버튼 눌러 한 번 가져오기"라 오래 쓰는 refresh token 이 필요 없어요.
 *    Google Identity Services(GIS)의 토큰 팝업으로 1시간짜리 access token 만 받아 바로 읽고 버려요.
 *  - 그래서 서버에 구글 client secret · 사용자 토큰을 저장하지 않아도 돼요.
 *  - 고른 일정만 서버 POST /v1/external-calendar/import 로 보내요 (iOS 아이폰 캘린더와 같은 API).
 *
 * 필요한 설정: .env 의 NEXT_PUBLIC_GOOGLE_CLIENT_ID (Google Cloud 의 "웹 애플리케이션" OAuth 클라이언트 ID)
 */
import type { ExternalEvent } from './types'

/** 캘린더 목록 보기 + 일정 보기만 (calendar.readonly 보다 좁은 범위 — 구글 OAuth 범위 심사) */
const SCOPE = ['https://www.googleapis.com/auth/calendar.calendarlist.readonly', 'https://www.googleapis.com/auth/calendar.events.readonly'].join(' ')
const API = 'https://www.googleapis.com/calendar/v3'
const KST_OFFSET_MS = 9 * 60 * 60 * 1000

export const googleClientId = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID ?? ''

/** 분석용 실패 코드 (fail_reason) — 권한 거부 · 창 닫음 · 연결 만료 · 그 밖의 실패 */
export type GoogleFailReason = 'access_denied' | 'popup_closed' | 'expired' | 'failed'

export class GoogleCalendarError extends Error {
  readonly reason: GoogleFailReason
  constructor(reason: GoogleFailReason, message: string) {
    super(message)
    this.reason = reason
  }
}

export const googleFailReasonOf = (e: unknown): GoogleFailReason =>
  e instanceof GoogleCalendarError ? e.reason : 'failed'

// ── GIS 타입 (필요한 것만) ──────────────────────────────────────
interface TokenResponse {
  access_token?: string
  /** 실제로 허용한 범위 (공백 구분). 구글 창에서 체크를 하나 빼면 일부만 와요 */
  scope?: string
  error?: string
  error_description?: string
}
interface TokenClient {
  requestAccessToken: (o?: { prompt?: string }) => void
}
declare global {
  interface Window {
    google?: {
      accounts: {
        oauth2: {
          initTokenClient: (c: {
            client_id: string
            scope: string
            callback: (r: TokenResponse) => void
            error_callback?: (e: { type: string; message?: string }) => void
          }) => TokenClient
        }
      }
    }
  }
}

let gisLoading: Promise<void> | null = null

function loadGis(): Promise<void> {
  if (window.google?.accounts?.oauth2) return Promise.resolve()
  gisLoading ??= new Promise((resolve, reject) => {
    const s = document.createElement('script')
    s.src = 'https://accounts.google.com/gsi/client'
    s.async = true
    s.onload = () => resolve()
    s.onerror = () => {
      gisLoading = null
      reject(new GoogleCalendarError('failed', '구글 로그인 창을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.'))
    }
    document.head.appendChild(s)
  })
  return gisLoading
}

/** 구글 계정 선택 · 권한 동의 팝업 → access token (1시간) */
export async function requestGoogleToken(): Promise<string> {
  if (!googleClientId)
    throw new GoogleCalendarError('failed', '구글 연동 설정(NEXT_PUBLIC_GOOGLE_CLIENT_ID)이 아직 없어요.')
  await loadGis()
  return new Promise((resolve, reject) => {
    const client = window.google!.accounts.oauth2.initTokenClient({
      client_id: googleClientId,
      scope: SCOPE,
      callback: (r) => {
        const granted = (r.scope ?? '').split(' ')
        if (r.access_token && SCOPE.split(' ').every((s) => granted.includes(s))) resolve(r.access_token)
        else if (r.access_token)
          reject(new GoogleCalendarError('access_denied', '구글 창에서 캘린더 권한을 모두 체크해 주세요.'))
        else
          reject(
            r.error === 'access_denied'
              ? new GoogleCalendarError('access_denied', '캘린더 읽기 권한을 허용해 주세요.')
              : new GoogleCalendarError('failed', '구글 연결에 실패했어요.')
          )
      },
      error_callback: (e) =>
        reject(
          e.type === 'popup_closed'
            ? new GoogleCalendarError('popup_closed', '구글 연결 창을 닫았어요.')
            : new GoogleCalendarError('failed', '구글 연결에 실패했어요.')
        ),
    })
    client.requestAccessToken({ prompt: 'select_account' })
  })
}

async function gget<T>(token: string, path: string, params?: Record<string, string>): Promise<T> {
  const url = new URL(API + path)
  Object.entries(params ?? {}).forEach(([k, v]) => url.searchParams.set(k, v))
  const res = await fetch(url, { headers: { Authorization: `Bearer ${token}` } })
  if (res.status === 401) throw new GoogleCalendarError('expired', '구글 연결이 만료됐어요. 다시 연결해 주세요.')
  if (!res.ok) {
    // 원인을 알 수 있게 구글 오류 코드 · 이유를 붙여요 (예: 403 insufficientPermissions · accessNotConfigured)
    const body = (await res.json().catch(() => null)) as { error?: { errors?: { reason?: string }[]; status?: string } } | null
    const why = body?.error?.errors?.[0]?.reason ?? body?.error?.status ?? ''
    throw new GoogleCalendarError('failed', `구글 캘린더를 불러오지 못했어요. (${res.status}${why ? ' ' + why : ''})`)
  }
  return res.json() as Promise<T>
}

export interface GoogleCalendar {
  id: string
  name: string
  color: string
  primary: boolean
}

export async function listGoogleCalendars(token: string): Promise<GoogleCalendar[]> {
  const r = await gget<{
    items?: { id: string; summary: string; summaryOverride?: string; backgroundColor?: string; primary?: boolean }[]
  }>(token, '/users/me/calendarList', { minAccessRole: 'reader', maxResults: '250' })
  return (r.items ?? [])
    .map((c) => ({
      id: c.id,
      name: c.summaryOverride || c.summary,
      color: c.backgroundColor ?? '#0F7A9C', // 구글이 색을 안 주면 브랜드 색 (데이터 값이라 hex 그대로)
      primary: !!c.primary,
    }))
    .sort((a, b) => Number(b.primary) - Number(a.primary))
}

interface GEvent {
  id: string
  status?: string
  summary?: string
  start?: { date?: string; dateTime?: string }
  end?: { date?: string; dateTime?: string }
  attendees?: { self?: boolean; responseStatus?: string }[]
}

/** "2026-10-09T10:00:00+09:00" / "...Z" → 한국 시간 벽시계 "2026-10-09T10:00:00" */
export function toKstLocal(iso: string): string {
  return new Date(new Date(iso).getTime() + KST_OFFSET_MS).toISOString().slice(0, 19)
}

/** 구글 일정 → 서버로 보낼 형태. 취소됐거나 내가 거절한 일정은 null */
export function toExternalEvent(e: GEvent, cal: GoogleCalendar): ExternalEvent | null {
  if (e.status === 'cancelled') return null
  if (e.attendees?.some((a) => a.self && a.responseStatus === 'declined')) return null
  const title = (e.summary ?? '').trim() || '(제목 없음)'
  // 반복 일정은 singleEvents=true 로 회차마다 id 가 달라요 (예: abc_20261009T010000Z)
  const externalId = `${cal.id}:${e.id}`.slice(0, 255)
  if (e.start?.date) {
    return { externalId, title, calendarName: cal.name, allDay: true, start: null, end: null, date: e.start.date }
  }
  if (!e.start?.dateTime || !e.end?.dateTime) return null
  return {
    externalId,
    title,
    calendarName: cal.name,
    allDay: false,
    start: toKstLocal(e.start.dateTime),
    end: toKstLocal(e.end.dateTime),
    date: toKstLocal(e.start.dateTime).slice(0, 10),
  }
}

/** from ~ to (둘 다 포함, YYYY-MM-DD) 사이 일정. 캘린더당 최대 1000개 */
export async function listGoogleEvents(token: string, cal: GoogleCalendar, from: string, to: string) {
  const timeMax = new Date(Date.parse(`${to}T00:00:00+09:00`) + 86_400_000).toISOString()
  const out: ExternalEvent[] = []
  let pageToken: string | undefined
  do {
    const r = await gget<{ items?: GEvent[]; nextPageToken?: string }>(
      token,
      `/calendars/${encodeURIComponent(cal.id)}/events`,
      {
        timeMin: `${from}T00:00:00+09:00`,
        timeMax,
        singleEvents: 'true', // 반복 일정을 회차별로 펼쳐서
        orderBy: 'startTime',
        maxResults: '250',
        timeZone: 'Asia/Seoul',
        ...(pageToken ? { pageToken } : {}),
      }
    )
    for (const e of r.items ?? []) {
      const ev = toExternalEvent(e, cal)
      if (ev) out.push(ev)
    }
    pageToken = r.nextPageToken
  } while (pageToken && out.length < 1000)
  return out
}
