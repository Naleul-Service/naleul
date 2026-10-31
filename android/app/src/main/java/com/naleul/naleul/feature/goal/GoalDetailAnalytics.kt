// feature/goal/GoalDetailAnalytics.kt
//
// iOS Features/Goal/GoalDetailAnalytics.swift — 목표 목록 · 추가 · 상세 · 습관 이벤트
// (Tracking Plan 분류 "목표 목록/상세" · 웹 goal_list / goal_add / goal_detail / goal_task_list / habit 과 같은 이름).
// 모바일은 웹의 "그 자리 입력창" 대신 시트를 써서, 시트가 어떻게 닫혔는지(close_method)를 각 시트가 사라질 때(onDispose) 보내요.
//
//   GD.track("goal_detail.edit_open", goal.analyticsBase + mapOf("entry_point" to "menu"))

package com.naleul.naleul.feature.goal

import com.naleul.naleul.core.analytics.AnalyticsService
import com.naleul.naleul.core.state.Mutation
import com.naleul.naleul.core.util.KST
import com.naleul.naleul.model.GoalCategory
import com.naleul.naleul.model.GoalFormat
import com.naleul.naleul.model.GoalMode
import kotlin.math.roundToInt

typealias EventProps = Map<String, Any?>

object GD {
    fun track(event: String, props: EventProps = emptyMap()) {
        AnalyticsService.track(event, *props.toList().toTypedArray())
    }

    /** 기본 속성 + 추가 속성 (뒤가 이겨요) */
    fun track(event: String, base: EventProps, extra: EventProps) = track(event, base + extra)

    /**
     * 요청 결과 공통 속성 — Mutation.run 이 끝난 직후에 불러요.
     * 성공/실패는 Mutation.lastErrorStatus 로 가려요 (API 가 원래 null 을 돌려주는 경우가 있어서 run 의 반환값으로는 못 가려요)
     */
    fun result(): EventProps {
        val s = Mutation.lastErrorStatus
        return if (s == null) mapOf("result" to "success") else mapOf("result" to "fail", "error_status" to s)
    }

    /** 결과를 직접 알 때 (로컬 검사 등) */
    fun result(ok: Boolean): EventProps =
        if (ok) mapOf("result" to "success") else mapOf("result" to "fail", "error_status" to (Mutation.lastErrorStatus ?: 0))

    /**
     * 시트가 닫힐 때(onDispose) — outcome 이 저장 · 삭제 · 다음 단계면 null(취소 이벤트 안 보냄).
     * '취소' 버튼은 outcome = "cancel_button", 머리의 X 는 AnalyticsService.sheetCloseHint,
     * 둘 다 없으면 아래로 쓸어 닫기 · 뒤로 가기 · 바깥 누르기 (swipe_down)
     */
    fun closeMethod(outcome: String?): String? {
        val hint = AnalyticsService.sheetCloseHint
        AnalyticsService.sheetCloseHint = null
        return when (outcome) {
            "saved", "deleted", "next" -> null
            null -> hint ?: "swipe_down"
            else -> outcome
        }
    }

    /** 오늘부터 며칠 뒤(+) · 며칠 전(-) */
    fun daysFromToday(ymd: String?): Int? {
        if (ymd.isNullOrEmpty()) return null
        return runCatching { KST.diffDays(KST.today(), ymd.take(10)) }.getOrNull()
    }

    /** 오늘부터 며칠 전 (오늘 0) */
    fun daysAgo(ymd: String?): Int? = daysFromToday(ymd)?.let { -it }
}

/** goal_mode 값 (ACHIEVEMENT · RECORD · HABIT) */
val GoalCategory.analyticsMode: String get() = (goalMode ?: GoalMode.ACHIEVEMENT).name

/** 모든 목표 이벤트에 붙는 기본 속성 */
val GoalCategory.analyticsBase: EventProps get() = mapOf("goal_category_id" to goalCategoryId, "goal_mode" to analyticsMode)

val GoalCategory.analyticsRoutineCount: Int get() = generalCategories.sumOf { it.routines.size }

val GoalCategory.analyticsDDay: Int?
    get() = if (!goalCategoryStatus.isOngoing) null
    else GoalFormat.periodProgress(goalCategoryStartDate, goalCategoryEndDate)?.remain

val GoalCategory.analyticsHasMetric: Boolean get() = targetValue != null || !metricName.isNullOrEmpty()

/** 목록 카드에 보이는 % (수치 목표면 수치 진행률, 아니면 기간 경과율) */
val GoalCategory.analyticsProgressPercent: Int?
    get() {
        val s = startValue
        val t = targetValue
        if (s != null && t != null && s != t) {
            val c = currentValue ?: s
            return (((c - s) / (t - s)).coerceIn(0.0, 1.0) * 100).roundToInt()
        }
        return GoalFormat.periodProgress(goalCategoryStartDate, goalCategoryEndDate)?.let { (it.ratio * 100).roundToInt() }
    }

/** 마무리 시트를 열 때의 안내 종류 (wrapup_prompt) — iOS GoalWrapUpSection.prompt */
fun goalWrapUpPrompt(goal: GoalCategory, metricDone: Boolean): String = when {
    metricDone -> "metric_done"
    (goal.goalCategoryEndDate ?: "9999") < KST.today() -> "period_over"
    else -> "normal"
}
