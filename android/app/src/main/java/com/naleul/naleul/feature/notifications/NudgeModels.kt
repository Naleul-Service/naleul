// feature/notifications/NudgeModels.kt
//
// iOS Features/Notifications/NudgeModels.swift
// "실천 도우미" 알림 — 계획을 실행으로 옮기도록 돕는 이 기기의 로컬 알림.
//
// 왜 이런 알림인가요? (Fogg 행동 모델 B = M · A · P)
//  행동은 동기(Motivation) · 능력(Ability, 지금 바로 할 만큼 쉬운가) · 계기(Prompt, "지금 해" 신호)가
//  같은 순간에 맞아야 일어나요. 실천을 못 하는 건 대부분 셋 중 하나가 빠져서예요.
//   - P 계기   : 하루 마무리 · 아침 첫걸음
//   - A 능력   : 아침에는 "하나만" 보여주기
//   (2026-10: "할 일이 많아 보여요" · "내일 할 일 n개" 는 실천에 도움이 안 돼서 뺐어요 — 알림은 적을수록 좋아요)
//   - M 동기   : 이루고 난 나(시작한 이유) · 끝이 보여요(해방까지 D-n) · 주간/월간 돌아보기
//   - 다시 시작 : 며칠 안 들어오면 "완벽하지 않아도 괜찮아요"
//
// 서버 푸시(일정 알림 · 미션 알림)도 알림함에 같이 모아요.
//
// Swift → Kotlin 메모
//  - iOS Date(울린 시각) → Kotlin Long (1970년부터 밀리초, System.currentTimeMillis() 와 같은 값).
//    가이드는 Date 대신 "YYYY-MM-DD" 문자열을 쓰라고 하지만, 알림은 "몇 시 몇 분에 울릴지"가 필요해서 밀리초로 들고 있어요.
//  - UserDefaults → SharedPreferences("naleul_prefs"), 키 이름은 iOS 와 같아요 (nudge.enabled.morning …)

package com.naleul.naleul.feature.notifications

import com.naleul.naleul.app.*
import com.naleul.naleul.core.analytics.*
import com.naleul.naleul.core.network.*
import com.naleul.naleul.core.state.*
import com.naleul.naleul.core.util.*
import com.naleul.naleul.designsystem.*
import com.naleul.naleul.model.*
import com.naleul.naleul.feature.auth.*
import com.naleul.naleul.feature.calendar.*
import com.naleul.naleul.feature.calendarimport.*
import com.naleul.naleul.feature.goal.*
import com.naleul.naleul.feature.goalcreation.*
import com.naleul.naleul.feature.habit.*
import com.naleul.naleul.feature.home.*
import com.naleul.naleul.feature.notifications.*
import com.naleul.naleul.feature.pattern.*
import com.naleul.naleul.feature.record.*
import com.naleul.naleul.feature.settings.*
import com.naleul.naleul.feature.taskadd.*
import com.naleul.naleul.feature.work.*

import android.content.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// MARK: - 알림 종류

@Serializable(with = NudgeKind.Serializer::class)
enum class NudgeKind {
    /** 아침 — 오늘 할 일 중 "하나만" 먼저 보여주기 (A) */
    firstStep,
    /** [더 이상 만들지 않음 2026-10] 할 일이 많은 날 — 예전에 받은 알림을 읽을 수 있게 값만 남겨요 */
    overwhelm,
    /** 오늘 일정이 끝나면 — 돌아보기 · 못 한 이유 남기기 (P) */
    dayWrapUp,
    /** [더 이상 만들지 않음 2026-10] 자기 전 내일 미리 보기 — 예전에 받은 알림을 읽을 수 있게 값만 남겨요 */
    tomorrowCheck,
    /** 시작한 이유 · 과거의 내가 보내는 말 (M) */
    why,
    /** 끝이 있다 — 해방까지 D-n, 긴 목표는 "남은 주말 n번" (M) */
    countdown,
    /** 주간 돌아보기 보고서 (M · 지표) */
    weeklyReport,
    /** 월간 돌아보기 (M · 지표) */
    monthlyReport,
    /** 며칠 안 들어오면 — 다시 시작하면 돼요 */
    comeback,
    /** 서버 푸시 ROUTINE_NUDGE — "어제 10:00에 독서를 했어요 · 연속 7일" · "실천률 85% 루틴, 시간을 바꿔 볼까요?" */
    routineNudge,
    /** 서버에서 온 푸시 (일정 · 미션 알림) */
    server;

    val rawValue: String get() = name

    val group: NudgeGroup?
        get() = when (this) {
            firstStep, overwhelm -> NudgeGroup.morning
            dayWrapUp, tomorrowCheck -> NudgeGroup.evening
            why, countdown -> NudgeGroup.motivation
            weeklyReport, monthlyReport -> NudgeGroup.report
            comeback -> NudgeGroup.comeback
            routineNudge, server -> null
        }

    /** 알림함 아이콘 (SF Symbol 이름 → SfImage 가 머티리얼 아이콘으로 바꿔요) */
    val icon: String
        get() = when (this) {
            firstStep -> "figure.walk"
            overwhelm -> "leaf"
            dayWrapUp -> "moon.stars"
            tomorrowCheck -> "sunrise"
            why -> "envelope.open"
            countdown -> "flag.checkered"
            weeklyReport, monthlyReport -> "chart.bar.doc.horizontal"
            comeback -> "arrow.counterclockwise"
            routineNudge -> "repeat"
            server -> "bell"
        }

    /** 알림함에서 작게 붙는 이름 */
    val label: String
        get() = when (this) {
            firstStep -> "아침 첫걸음"
            overwhelm -> "가볍게 가기"
            dayWrapUp -> "하루 마무리"
            tomorrowCheck -> "내일 미리 보기"
            why -> "시작한 이유"
            countdown -> "끝이 보여요"
            weeklyReport -> "주간 돌아보기"
            monthlyReport -> "월간 돌아보기"
            comeback -> "다시 시작"
            routineNudge -> "루틴 실천"
            server -> "알림"
        }

    /** 모르는 값이 와도(앱 버전이 달라 새 종류가 생겨도) 죽지 않고 "알림"으로 */
    object Serializer : FallbackEnumSerializer<NudgeKind>("NudgeKind", NudgeKind.entries.toTypedArray(), NudgeKind.server)

    companion object {
        fun fromRaw(raw: String?): NudgeKind? = entries.firstOrNull { it.rawValue == raw }
    }
}

/** 설정에서 켜고 끄는 묶음 */
enum class NudgeGroup {
    morning, evening, motivation, report, comeback;

    val rawValue: String get() = name
    val id: String get() = rawValue

    val title: String
        get() = when (this) {
            morning -> "아침 첫걸음"
            evening -> "하루 마무리"
            motivation -> "목표를 떠올리게"
            report -> "주간 · 월간 돌아보기"
            comeback -> "다시 시작 응원"
        }

    val description: String
        get() = when (this) {
            morning -> "일어나면 오늘 할 일 중 하나만 먼저 알려드려요."
            evening -> "오늘 일정이 끝나면 3분 돌아보기를 권해요."
            motivation -> "목표를 시작한 이유와 끝까지 남은 날을 알려드려요."
            report -> "일요일 저녁과 월말에 한 일 · 못 한 일을 돌아보게 해 드려요."
            comeback -> "며칠 쉬었다면 부담 없이 다시 시작할 수 있게 말을 걸어요."
        }

    private val storageKey: String get() = "nudge.enabled.$rawValue"

    /**
     * 기본은 켜짐. 이 기기에만 저장해요 (iOS UserDefaults → SharedPreferences).
     * 미리보기처럼 앱 Context 가 없을 때는 "켜짐"으로 봐요.
     */
    var isEnabled: Boolean
        get() = runCatching { NudgePrefs.prefs().getBoolean(storageKey, true) }.getOrDefault(true)
        set(value) {
            runCatching { NudgePrefs.prefs().edit().putBoolean(storageKey, value).apply() }
        }

    companion object {
        fun fromRaw(raw: String?): NudgeGroup? = entries.firstOrNull { it.rawValue == raw }
    }
}

/** 실천 도우미가 쓰는 저장소 (ThemeStore · TokenManager 와 같은 "naleul_prefs" 파일) */
object NudgePrefs {
    fun prefs(): android.content.SharedPreferences =
        AppContext.app.getSharedPreferences("naleul_prefs", Context.MODE_PRIVATE)
}

// MARK: - 누르면 어디로 갈지

/**
 * 알림 데이터의 "route" 에 문자열로 넣어요 — "calendar:2026-10-15", "goal:12", "ai:문장" …
 * (Swift 연관값 enum → Kotlin sealed class. case 이름은 iOS 와 같은 소문자예요: NudgeRoute.goal(12))
 */
sealed class NudgeRoute {
    object home : NudgeRoute()
    /** 홈 + 오늘 회고 시트 */
    object homeRetro : NudgeRoute()
    data class calendar(val date: String) : NudgeRoute()
    /** 그 날짜 캘린더 + 그 Task 상세 시트 (루틴 실천 알림 "시간을 바꿔 볼까요?") */
    data class calendarTask(val date: String, val taskId: Int) : NudgeRoute()
    object taskAdd : NudgeRoute()
    data class goal(val id: Int) : NudgeRoute()
    object reflections : NudgeRoute()
    object patterns : NudgeRoute()
    /** 캘린더 "AI로 일정 조정"에 문장을 채워서 */
    data class calendarAi(val prompt: String) : NudgeRoute()

    val raw: String
        get() = when (this) {
            is home -> "home"
            is homeRetro -> "home-retro"
            is calendar -> "calendar:$date"
            is calendarTask -> "calendar-task:$date|$taskId"
            is taskAdd -> "task-add"
            is goal -> "goal:$id"
            is reflections -> "reflections"
            is patterns -> "patterns"
            is calendarAi -> "ai:$prompt"
        }

    /** 버튼 글자 (알림함에서) */
    val actionLabel: String
        get() = when (this) {
            is home -> "홈으로"
            is homeRetro -> "돌아보기"
            is calendar -> "일정 보기"
            is calendarTask -> "시간 바꾸기"
            is taskAdd -> "할 일 정하기"
            is goal -> "목표 보기"
            is reflections -> "회고 모아보기"
            is patterns -> "나의 패턴"
            is calendarAi -> "AI로 다시 짜기"
        }

    companion object {
        /** iOS init?(raw:) — 모르는 문자열이면 null */
        fun fromRaw(raw: String?): NudgeRoute? {
            if (raw.isNullOrEmpty()) return null
            val parts = raw.split(":", limit = 2)
            val head = parts[0]
            val rest = if (parts.size > 1) parts[1] else ""
            return when (head) {
                "home" -> home
                "home-retro" -> homeRetro
                "calendar" -> calendar(date = if (KST.isYmd(rest)) rest else KST.today())
                "calendar-task" -> {
                    val p = rest.split("|")
                    val id = p.getOrNull(1)?.toIntOrNull()
                    if (p.size == 2 && KST.isYmd(p[0]) && id != null) calendarTask(p[0], id) else null
                }
                "task-add" -> taskAdd
                "goal" -> rest.toIntOrNull()?.let { goal(it) }
                "reflections" -> reflections
                "patterns" -> patterns
                "ai" -> calendarAi(rest)
                else -> null
            }
        }
    }
}

// MARK: - 알림 한 건

@Serializable
data class NudgeEntry(
    /** 로컬 알림이면 "nudge.<kind>.<날짜>…", 서버 푸시면 "push.<메시지 id>" */
    val id: String,
    val kind: NudgeKind,
    val title: String,
    val body: String,
    /** 울린 시각 (예정 목록에서는 울릴 시각) — 밀리초 (iOS Date) */
    val date: Long,
    val route: String? = null,
    val read: Boolean = false,
    /** 서버 푸시의 data.type (TASK_START · HABIT_REMIND …). 로컬 알림 · 예전에 저장된 알림이면 null */
    val serverType: String? = null,
    /** ROUTINE_NUDGE 의 kind (YESTERDAY_TIME · RATE_RESCHEDULE). 다른 알림이면 null */
    val routineKind: String? = null,
) {
    val nudgeRoute: NudgeRoute? get() = NudgeRoute.fromRaw(route)

    /**
     * 알림함에 넣지 않는 알림 — "'○○' 할 시간이에요"(Task 시작 알림)는 너무 많아서 핵심 알림이 묻혀요.
     * 푸시로는 그대로 울리고 목록에만 안 보여요. (type 을 모르는 알림은 제목으로 걸러요)
     */
    val isHiddenFromInbox: Boolean
        get() {
            if (kind != NudgeKind.server) return false
            if (serverType == "TASK_START") return true
            return serverType.isNullOrEmpty() && title.contains("Task 시작 시간")
        }

    companion object {
        /**
         * 서버 루틴 실천 알림 → 알림 한 건. data 의 kind 는 YESTERDAY_TIME · RATE_RESCHEDULE (로컬 NudgeKind 가 아니에요).
         * 어제 한 시간 알림은 그날 캘린더로, 실천률 알림은 그 Task 상세까지 열어요.
         */
        fun routine(id: String, title: String, body: String, date: Long, data: (String) -> String?): NudgeEntry {
            val rk = data("kind")
            val raw = data("date").orEmpty()
            val day = if (KST.isYmd(raw)) raw else KST.today()
            val taskId = data("taskId")?.toIntOrNull()
            val route = if (rk == "RATE_RESCHEDULE" && taskId != null) NudgeRoute.calendarTask(day, taskId) else NudgeRoute.calendar(day)
            return NudgeEntry(
                id = id, kind = NudgeKind.routineNudge, title = title.ifEmpty { "나를" }, body = body, date = date,
                route = route.raw, serverType = "ROUTINE_NUDGE", routineKind = rk,
            )
        }
    }
}
