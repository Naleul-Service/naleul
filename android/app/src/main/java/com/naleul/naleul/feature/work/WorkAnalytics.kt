// feature/work/WorkAnalytics.kt
//
// iOS Features/Work/WorkAnalytics.swift — 업무형 목표 이벤트 (Tracking Plan 분류 "업무형 목표" ·
// 웹 work_board / work_roadmap / work_task / work_task_modal / work_compose / work_share / work_journal / record_stats 와 같은 이름).
// 모바일은 웹의 오른쪽 "적기" 패널 대신 시트, 보드 세 칸 대신 칸 세그먼트 + 카드 … 메뉴를 써요.
//
//   WK.track("work_board.share_click", goalId = gid, mapOf("open_task_count" to n))

package com.naleul.naleul.feature.work

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import com.naleul.naleul.feature.goal.EventProps
import com.naleul.naleul.feature.goal.GD

object WK {
    /** 미리보기에서는 보내지 않아요 (Composable 밖에서 쓰는 모델은 isPreview 를 직접 넘겨요) */
    fun track(event: String, props: EventProps = emptyMap()) = GD.track(event, props)

    /** goal_category_id + 추가 속성 */
    fun track(event: String, goalId: Int, props: EventProps = emptyMap()) =
        GD.track(event, mapOf("goal_category_id" to goalId) + props)

    /** Task 공통 속성 (칸 옮기기 · 열기) */
    fun taskProps(t: WorkTask, surface: String): EventProps = mapOf(
        "surface" to surface, "task_id" to t.taskId, "topic_id" to t.topicId, "is_overdue" to t.overdue, "is_routine" to t.routine,
    )
}

/** topic_filter 값 (ALL · NONE · TOPIC) */
val WorkTopicFilter.analyticsName: String
    get() = when (this) {
        is WorkTopicFilter.all -> "ALL"
        is WorkTopicFilter.noTopic -> "NONE"
        is WorkTopicFilter.topic -> "TOPIC"
    }

val WorkTopicFilter.analyticsTopicId: Int? get() = (this as? WorkTopicFilter.topic)?.id

/** 통계 탭: 칸 누르기 이벤트가 어느 목표인지 (카드가 목표 id 를 몰라서 내려 줘요) */
val LocalRecordStatsGoalId = compositionLocalOf<Int?> { null }

/**
 * 화면에 절반 이상 처음 보였을 때 한 번 (record_stats.card_view).
 * 탭 내용이 LazyColumn 의 한 항목이라 항목 단위 노출로는 못 재서, 창 좌표로 직접 계산해요.
 */
fun Modifier.onFirstHalfVisible(action: () -> Unit): Modifier = composed {
    if (LocalInspectionMode.current) return@composed this
    var done by remember { mutableStateOf(false) }
    val view = LocalView.current
    onGloballyPositioned { c ->
        if (done) return@onGloballyPositioned
        val h = c.size.height
        if (h <= 0) return@onGloballyPositioned
        val b = c.boundsInWindow()
        val visible = (b.bottom - b.top).coerceAtLeast(0f)
        if (visible >= minOf(h, view.height) * 0.5f) {
            done = true
            action()
        }
    }
}
