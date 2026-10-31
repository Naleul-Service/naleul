// core/state/Invalidation.kt
//
// iOS Core/State/Invalidation.swift — 웹 TanStack Query "invalidateQueries" 를 대신하는 아주 작은 도구.
//
//   // 1) 화면: 데이터 종류(DataKey)를 구독하면서 불러오기 — 처음 뜰 때 + 그 종류가 바뀔 때마다 다시 실행
//   ReloadOn(listOf(DataKey.timetable)) { vm.load() }
//   ReloadOn(listOf(DataKey.goals), id = goalId) { vm.load(goalId) }   // 값(id)이 바뀌어도 다시
//
//   // 2) 바꾸는 쪽: 저장이 끝나면 관련 데이터를 "낡았다"고 표시
//   Invalidation.invalidate(DataKey.timetable, DataKey.goals)
//
// 왜 이렇게? → 한 화면에서 바꾼 데이터(예: Task 완료)가 다른 탭/화면(목표 상세, 나의 패턴)에도
// 영향을 주는데, 화면끼리 서로를 몰라도 다시 불러오게 하려고요.

package com.naleul.naleul.core.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf

/** 다시 불러올 데이터 종류 (웹 queryKey 의 첫 단어와 맞춰요) */
enum class DataKey {
    /** 캘린더(TimeTable) — 주간/일간/월간 모두 */
    timetable,
    /** 목표 목록 · 상세 · 목표의 Task · 진행 · 히트맵 (['goals']) */
    goals,
    /** 실제로 한 일 (['activities']) */
    activities,
    /** 기본 생활 패턴 (['life-patterns']) */
    lifePatterns,
    /** 기록형 목표의 나의 패턴 · 업무 일지 (['record']) */
    record,
    /** 나의 패턴 (['pattern']) */
    pattern,
    /** 오늘 AI 사용량 (['ai-usage']) */
    usage,
    /** 목표 만들기 대화 세션 (['goal-creation']) */
    goalCreation,
    /** 사용자 색상 (['user-colors']) */
    userColors,
    /** 알림 설정 */
    notificationSettings,
    /** 멤버십 · 사용자 정보 */
    account,
    /** 기본 생활 습관 (['habits'], V52) */
    habits,
    /** 업무형 보드 (['work'], V52) */
    work,
    /** 홈 "오늘" — 다가오는 마감 · 목표별 회고 (['home']) */
    home,
}

object Invalidation {
    private val versions = mutableStateMapOf<DataKey, Int>()

    fun version(key: DataKey): Int = versions[key] ?: 0

    fun invalidate(vararg keys: DataKey) = invalidate(keys.toList())

    fun invalidate(keys: List<DataKey>) {
        for (k in keys) versions[k] = (versions[k] ?: 0) + 1
    }

    fun invalidateAll() = invalidate(DataKey.entries.toList())
}

/**
 * 처음 화면에 나타날 때 + keys 중 하나가 invalidate 될 때마다 perform 을 다시 실행해요.
 * (이전 실행 중이면 취소하고 새로 시작 — iOS .task(id:) / .reloadOn 과 같아요)
 * id 가 바뀌어도 다시 실행 (예: 다른 목표로 이동, 주 이동)
 */
@Composable
fun ReloadOn(keys: List<DataKey>, id: Any? = Unit, perform: suspend () -> Unit) {
    val versions = keys.map { Invalidation.version(it) }
    LaunchedEffect(id, versions) { perform() }
}
