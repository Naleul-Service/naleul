import { formatDuration, formatMonthDay } from '@/features/timetable/time'
import type { JournalDay, JournalItem } from './api'

const hm = (iso: string) => iso.slice(11, 16)

/** "· 09:00–09:30 코드 리뷰 (30분) — 메모" / "· 메일 정리" */
function itemLine(i: JournalItem) {
  const time = i.startAt && i.endAt ? `${hm(i.startAt)}–${hm(i.endAt)} ` : ''
  const dur = i.minutes ? ` (${formatDuration(i.minutes)})` : ''
  const memo = i.memo ? ` — ${i.memo}` : ''
  return `· ${time}${i.title}${dur}${memo}`
}

function dayBlock(d: JournalDay) {
  const total = d.minutes ? ` · ${formatDuration(d.minutes)}` : ''
  return [`■ ${formatMonthDay(d.date)}${total}`, ...d.items.map(itemLine)].join('\n')
}

/**
 * 업무 일지를 메신저·문서에 바로 붙여 넣을 수 있는 글로 바꿔요.
 * 날짜는 오래된 것부터 (보고서처럼 위에서 아래로 읽히게).
 */
export function journalText(title: string, days: JournalDay[]) {
  const ordered = [...days].sort((a, b) => a.date.localeCompare(b.date))
  return [`[${title}]`, '', ordered.map(dayBlock).join('\n\n')].join('\n')
}
