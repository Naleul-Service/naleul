/** 서버로 보내는 외부 일정 1건 (POST /v1/external-calendar/import) — 시각은 한국 시간 "YYYY-MM-DDTHH:mm:ss" */
export interface ExternalEvent {
  externalId: string
  title: string
  calendarName: string | null
  allDay: boolean
  start: string | null
  end: string | null
  date: string | null
}

export type ExternalCalendarSource = 'GOOGLE' | 'APPLE'

export interface ImportResult {
  created: number
  skippedDuplicate: number
  skippedPast: number
  skippedInvalid: number
}
