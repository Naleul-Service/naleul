package com.naleul.naleul.feature.calendar

import com.naleul.naleul.core.network.*
import com.naleul.naleul.core.util.*
import com.naleul.naleul.feature.goal.*
import com.naleul.naleul.feature.home.*

/**
 * 캘린더 이벤트 (Tracking Plan 분류 "캘린더" · 웹 calendar / timetable / task_detail / activity_form / actual_time /
 * fixed_detail / scope_chooser / fill_proposal / carry_proposal / calendar_month / calendar_day_list / calendar_ai 와 같은 이름).
 * 블록 상세 · 실제로 한 일 · 아래 확인 바는 홈 · Task 추가 결과 격자에서도 같은 부품이라 surface 로 구분해요
 * (TimetableBoardModel.analyticsSurface = calendar / home / task_add_result).
 * 격자 클릭 · 끌기(timetable.*)는 홈에서는 보내지 않아요 — 홈은 home.calendar_* 를 따로 보내요.
 *
 *   CAL.track("calendar.view_change", mapOf("view_mode" to "week", "from_view" to "day", "view_date" to d))
 */
object CAL {
    fun track(event: String, props: EventProps = emptyMap()) = GD.track(event, props)

    /** 요청 실패의 HTTP 상태 (0 = 네트워크) */
    fun status(e: Throwable): Int = (e as? ApiError)?.httpStatus ?: 0

    /** today · past · future */
    fun relation(date: String): String = HomeTracker.relation(date)

    /** 오늘부터 며칠 전 (오늘 0) */
    fun daysAgo(ymd: String): Int = runCatching { KST.diffDays(ymd.take(10), KST.today()) }.getOrDefault(0)
}
