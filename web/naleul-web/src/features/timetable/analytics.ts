/**
 * TimeTable 부품이 쓰인 화면 (캘린더 이벤트의 surface)
 * - home: 홈 일간 캘린더 — 격자 클릭·끌기는 home.calendar_* 로 따로 보내요 (timetable.* 는 안 보냄)
 * - calendar: /calendar
 * - task_add_result: Task 추가 3단계 7일 격자
 */
export type TimetableSurface = 'home' | 'calendar' | 'task_add_result'
