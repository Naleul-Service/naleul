package com.naleul.naleul.feature.home

import com.naleul.naleul.core.network.*
import com.naleul.naleul.feature.goal.*

/**
 * 회고 이벤트 (Tracking Plan 분류 "회고" · 웹 reflections / reflection_timeline 과 같은 이름).
 * iOS Features/Home/ReflectionsAnalytics.swift
 * 같은 기간별 목록(ReflectionTimelineView)을 회고 모아보기와 목표 상세 "회고" 탭이 같이 써서 surface 로 구분해요
 * (ReflectionsViewModel.surface = reflections / goal_detail).
 *
 *   RF.pendingEntry = "home_retro"; AppRouter.openReflections()   // → reflections.pageview 의 entry_source
 */
object RF {
    fun track(event: String, props: EventProps = emptyMap()) = GD.track(event, props)

    /** 요청 실패의 HTTP 상태 (0 = 네트워크) */
    fun status(e: Throwable): Int = (e as? ApiError)?.httpStatus ?: 0

    /** 회고 모아보기를 열기 직전에 정해 둬요 (home_retro · work_compose · nudge) → 첫 pageview 가 가져가요 */
    var pendingEntry: String? = null
}

/** period 값 — 웹과 같이 DAY · WEEK · MONTH */
val ReflectionPeriod.analyticsValue: String get() = name.uppercase()
