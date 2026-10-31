// feature/taskadd/TaskAddAnalytics.kt
//
// iOS Features/TaskAdd/TaskAddAnalytics.swift — Task 추가 이벤트 (Tracking Plan 분류 "Task 추가" ·
// 웹 task_add / task_add_input / task_add_goals / task_add_item_edit / task_add_result / task_add_manual / task_add_log 와 같은 이름).
// 모바일은 웹의 /tasks/new 페이지 · 캘린더 옆 패널 대신 캘린더 위 시트라서 surface 가 "sheet" 예요.
// 시간 안 정한 Task 의 점선 제안은 시트가 닫힌 뒤 캘린더에서 물어서 그 이벤트만 surface = "calendar".
//
//   TA.track("task_add_input.line_remove", mapOf("list_position" to 0, "line_count" to 2))

package com.naleul.naleul.feature.taskadd

import com.naleul.naleul.core.analytics.AnalyticsService
import com.naleul.naleul.core.network.ApiError
import com.naleul.naleul.feature.goal.EventProps
import com.naleul.naleul.feature.goal.GD
import com.naleul.naleul.model.ConfirmResponse

object TA {
    /** surface = "sheet" 를 붙여서 (props 에 surface 가 있으면 그 값) */
    fun track(event: String, props: EventProps = emptyMap()) = GD.track(event, mapOf("surface" to "sheet") + props)

    /** surface 없이 (task_add_log.* 처럼 Tracking Plan 에 surface 가 없는 이벤트) */
    fun plain(event: String, props: EventProps = emptyMap()) = GD.track(event, props)

    /** 요청 실패의 HTTP 상태 (0 = 네트워크) */
    fun status(e: Throwable): Int = (e as? ApiError)?.httpStatus ?: 0

    /** ai_step 값 (input · goals · result) */
    fun aiStep(step: Int): String = listOf("input", "goals", "result")[step.coerceIn(0, 2)]

    /** goal_match — existing: 내 목표 / temp: 이번 임시 목표 / none: 없음 */
    fun goalMatch(goalId: Int?, tempKey: String?): String =
        if (goalId != null) "existing" else if (tempKey != null) "temp" else "none"

    /** 확정 결과 공통 개수 */
    fun resultCounts(r: ConfirmResponse): EventProps = mapOf(
        "placed_count" to r.placements.size, "moved_count" to r.movedToOtherDays.size,
        "unscheduled_count" to r.unscheduled.size, "pending_count" to (r.pending ?: emptyList()).size,
        "temp_count" to r.tempGoals.size,
    )

    /** 시트가 닫힐 때 close_method — outcome(코드가 닫음 = "next") > 머리 X(sheetCloseHint) > 쓸어 닫기 · 뒤로 가기 */
    fun closeMethod(outcome: String?): String {
        val hint = AnalyticsService.sheetCloseHint
        AnalyticsService.sheetCloseHint = null
        return outcome ?: hint ?: "swipe_down"
    }
}
