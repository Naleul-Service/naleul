// feature/settings/lifepattern/LifePatternRules.kt
//
// iOS Features/Settings/LifePattern/LifePatternRules.swift
// 웹 원본:
//   features/life-pattern/presets.ts  (추천 · 종류 이름/색 · 요일 표기)
//   features/life-pattern/dayTimes.ts (요일별 시간 · 수면 "밤" 계산 — 백엔드 LifePattern.occurrenceOn 과 같은 규칙)
//
// 화면이 아니라 계산만 모아 둔 파일이에요. 이름이 겹치지 않게 전역 함수 대신 `LifePatternRules` 안에 넣었어요.

package com.naleul.naleul.feature.settings

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

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject

// MARK: - 추천 (웹 LifePatternPreset)

data class LifePatternPreset(
    val key: String,
    /** 추천 칩에 보이는 이름 */
    val label: String,
    val patternType: LifePatternType,
    val title: String,
    val emoji: String,
    /** "HH:mm" */
    val startTime: String,
    val endTime: String,
    val days: List<JavaDayOfWeek>,
    /** 제목을 입력할 때 이 단어가 들어가면 추천해요 */
    val keywords: List<String>,
    /** 사용자당 하나만 두는 종류 (수면·점심·저녁) */
    val single: Boolean,
) {
    val id: String get() = key

    companion object {}
}

/** 하룻밤: bed = 그날 밤 취침, wake = 다음 날 아침 기상 ("HH:mm"). 바꿀 때는 copy() */
data class LifePatternSleepNight(
    val bed: String,
    val wake: String,
) {
    companion object {}
}

/** 요일별 시간 계산에 필요한 값만 (LifePattern · 입력 중인 폼 둘 다에서 만들 수 있게) */
data class LifePatternTimes(
    val startTime: String,
    val endTime: String,
    val days: List<JavaDayOfWeek>,
    val dayTimes: List<LifePatternDayTime>? = null,
) {
    constructor(p: LifePattern) : this(startTime = p.startTime, endTime = p.endTime, days = p.days, dayTimes = p.dayTimes)

    companion object {}
}

object LifePatternRules {
    /** occurrenceOf 의 결과 (Swift 튜플 (start:, end:)) — 그 요일 0시 기준 분 */
    data class Occurrence(val start: Int, val end: Int)

    // MARK: 요일

    val allDays: List<JavaDayOfWeek> = JavaDayOfWeek.entries.toList()
    val weekdays: List<JavaDayOfWeek> = JavaDayOfWeek.entries.take(5)
    val weekend: List<JavaDayOfWeek> = JavaDayOfWeek.entries.takeLast(2)

    /** 백엔드 LifePatternService.MAX_PATTERNS */
    const val maxPatterns = 10

    // MARK: 종류

    fun typeLabel(t: LifePatternType): String = when (t) {
        LifePatternType.SLEEP -> "수면"
        LifePatternType.LUNCH -> "점심"
        LifePatternType.DINNER -> "저녁"
        LifePatternType.CUSTOM -> "고정 일정"
    }

    /** 종류별 색 (주간 미리보기 막대) — 데이터 색이라 고정값이에요 */
    fun typeColor(t: LifePatternType): Color = when (t) {
        LifePatternType.SLEEP -> Color(GoalPalette.purple.lightAccent)
        LifePatternType.LUNCH -> Color(GoalPalette.amber.lightAccent)
        LifePatternType.DINNER -> Color(GoalPalette.orange.lightAccent)
        LifePatternType.CUSTOM -> Color(GoalPalette.mint.lightAccent)
    }

    // MARK: 추천

    /** 제목 추천 (+이동시간, +수면시간, +외출준비, +쉬는시간 …) */
    val presets: List<LifePatternPreset> = listOf(
        LifePatternPreset(key = "sleep", label = "수면시간", patternType = LifePatternType.SLEEP, title = "수면", emoji = "🌙",
            startTime = "00:00", endTime = "07:00", days = allDays,
            keywords = listOf("수면", "잠", "취침", "기상"), single = true),
        LifePatternPreset(key = "getReady", label = "외출준비", patternType = LifePatternType.CUSTOM, title = "외출준비", emoji = "🪞",
            startTime = "07:30", endTime = "08:10", days = weekdays,
            keywords = listOf("외출", "준비", "씻", "샤워"), single = false),
        LifePatternPreset(key = "commute", label = "이동시간", patternType = LifePatternType.CUSTOM, title = "이동시간", emoji = "🚇",
            startTime = "08:10", endTime = "09:00", days = weekdays,
            keywords = listOf("이동", "출근", "퇴근", "통학", "등교", "하교"), single = false),
        LifePatternPreset(key = "break", label = "쉬는시간", patternType = LifePatternType.CUSTOM, title = "쉬는시간", emoji = "☕",
            startTime = "15:00", endTime = "15:30", days = allDays,
            keywords = listOf("쉬는", "휴식", "쉼", "브레이크", "낮잠"), single = false),
        LifePatternPreset(key = "lunch", label = "점심", patternType = LifePatternType.LUNCH, title = "점심", emoji = "🍚",
            startTime = "12:00", endTime = "13:00", days = allDays,
            keywords = listOf("점심"), single = true),
        LifePatternPreset(key = "dinner", label = "저녁", patternType = LifePatternType.DINNER, title = "저녁", emoji = "🍽️",
            startTime = "18:00", endTime = "19:00", days = allDays,
            keywords = listOf("저녁"), single = true),
    )

    /** 수면·점심·저녁이 이미 있으면 그 패턴 (추천을 누르면 새로 만들지 않고 그걸 수정해요) */
    fun existingOfType(patterns: List<LifePattern>, preset: LifePatternPreset): LifePattern? {
        if (!preset.single) return null
        return patterns.firstOrNull { it.patternType == preset.patternType }
    }

    /** 입력한 제목과 맞는 추천. 제목이 비어 있으면 전부 */
    fun matchPresets(title: String): List<LifePatternPreset> {
        val q = title.trim()
        if (q.isEmpty()) return presets
        return presets.filter { p ->
            p.title.contains(q) || p.label.contains(q) || p.keywords.any { q.contains(it) }
        }
    }

    /** 추천 칩 문구: 이미 있으면 "🌙 수면시간 수정", 없으면 "+ 수면시간" */
    fun presetChipTitle(preset: LifePatternPreset, existing: LifePattern?): String =
        if (existing != null) "${preset.emoji} ${preset.label} 수정" else "+ ${preset.label}"

    // MARK: 표기

    /** "HH:mm:ss" → "HH:mm" */
    fun toHm(t: String): String = t.take(5)

    /** ["MONDAY",…,"FRIDAY"] → "평일", 7개 → "매일", 그 외 → "월·수·금" */
    fun formatDays(days: List<JavaDayOfWeek>): String {
        if (days.isEmpty() || days.size == 7) return "매일"
        if (sameSet(days, weekdays)) return "평일"
        if (sameSet(days, weekend)) return "주말"
        return allDays.filter { days.contains(it) }.joinToString("·") { it.label }
    }

    fun sameSet(a: List<JavaDayOfWeek>, b: List<JavaDayOfWeek>): Boolean =
        a.size == b.size && b.all { a.contains(it) }

    /** 패턴 길이 ("1시간 30분") — 끝이 시작보다 이르면 다음 날까지 */
    fun duration(p: LifePattern): String {
        val s = KST.timeToMinutes(p.startTime)
        var e = KST.timeToMinutes(p.endTime)
        if (e <= s) e += 1440
        return KST.formatDuration(e - s)
    }

    // MARK: 요일별 시간 (웹 dayTimes.ts)

    /** JS Math.floor(a / b) 와 같은 내림 나눗셈 (Kotlin 의 / 도 음수에서 0 쪽으로 잘라서 Math.floorDiv 를 써요) */
    fun floorDiv(a: Int, b: Int): Int = Math.floorDiv(a, b)

    fun applies(p: LifePatternTimes, d: JavaDayOfWeek): Boolean = p.days.isEmpty() || p.days.contains(d)

    /** 소속 요일 day 의 블록 구간 — 그 요일 0시 기준 분 (start 는 음수·1440 이상일 수 있고, end > start) */
    fun occurrenceOf(p: LifePatternTimes, day: JavaDayOfWeek): Occurrence {
        val dt = p.dayTimes?.firstOrNull { it.day == day }
        val start = KST.timeToMinutes(toHm(dt?.startTime ?: p.startTime)) + (dt?.startDayOffset ?: 0) * 1440
        var end = floorDiv(start, 1440) * 1440 + KST.timeToMinutes(toHm(dt?.endTime ?: p.endTime))
        if (end <= start) end += 1440
        return Occurrence(start, end)
    }

    private const val noon = 720

    /** 밤 W 가 몇 요일 블록인지: 기본 취침이 정오 전(00:00 등)이면 다음 날 소속(1), 정오 뒤(23:00 등)면 그날 소속(0) */
    fun nightShift(defaultStart: String): Int = if (KST.timeToMinutes(toHm(defaultStart)) < noon) 1 else 0

    /** 패턴 → 요일별 밤 (월요일 밤 ~ 일요일 밤). 그 밤에 수면이 없으면 null */
    fun sleepNights(p: LifePatternTimes): List<LifePatternSleepNight?> {
        val shift = nightShift(p.startTime)
        return (0 until 7).map { w ->
            val key = allDays[(w + shift) % 7]
            if (!applies(p, key)) {
                null
            } else {
                val occ = occurrenceOf(p, key)
                LifePatternSleepNight(bed = KST.minutesToTime(occ.start), wake = KST.minutesToTime(occ.end))
            }
        }
    }

    /** 밤 W 의 취침 시각을 밤 W 날짜 0시 기준 분으로 (정오 전이면 다음 날 새벽) */
    private fun bedFromNight(bed: String): Int {
        val m = KST.timeToMinutes(bed)
        return if (m >= noon) m else m + 1440
    }

    /** 요일별 밤 → 요일별 시간 (기본 시간과 같은 밤은 빼요) */
    fun nightsToDayTimes(
        defaultStart: String,
        defaultEnd: String,
        nights: List<LifePatternSleepNight?>,
    ): List<LifePatternDayTime> {
        val shift = nightShift(defaultStart)
        val out = mutableListOf<LifePatternDayTime>()
        nights.forEachIndexed { w, night ->
            val n = night ?: return@forEachIndexed
            val relKey = bedFromNight(n.bed) - shift * 1440   // 소속 요일 0시 기준
            val offset = floorDiv(relKey, 1440)
            val startTime = KST.minutesToTime(relKey)
            if (offset == 0 && startTime == toHm(defaultStart) && n.wake == toHm(defaultEnd)) return@forEachIndexed
            out.add(
                LifePatternDayTime(
                    day = allDays[(w + shift) % 7], startTime = startTime,
                    endTime = n.wake, startDayOffset = offset,
                )
            )
        }
        return out
    }

    /**
     * 밤끼리 겹치는지 — W 밤에 일어나는 시각이 그다음 밤 취침보다 늦으면 안 돼요.
     * @return 문제가 있는 요일(기상하는 날) 인덱스, 없으면 -1
     */
    fun sleepConflictDay(nights: List<LifePatternSleepNight?>): Int {
        if (nights.size != 7) return -1
        for (w in 0 until 7) {
            val n = nights[w] ?: continue
            val next = nights[(w + 1) % 7] ?: continue
            val bed = bedFromNight(n.bed)
            var wake = floorDiv(bed, 1440) * 1440 + KST.timeToMinutes(n.wake)
            if (wake <= bed) wake += 1440
            val nextBed = 1440 + bedFromNight(next.bed)
            if (wake >= nextBed) return (w + 1) % 7
        }
        return -1
    }
}

// MARK: - 요청 (웹 features/life-pattern/api.ts)

/** 추가 · 수정에 보내는 값 (수정할 때는 patternType 을 보내지 않아요) */
data class LifePatternInput(
    val patternType: LifePatternType,
    val title: String,
    val emoji: String?,
    /** "HH:mm" */
    val startTime: String,
    val endTime: String,
    /** 빈 배열이면 매일 */
    val days: List<JavaDayOfWeek>,
    /** 빈 배열이면 모든 요일 기본 시간 */
    val dayTimes: List<LifePatternDayTime>,
) {
    companion object {}
}

/**
 * 백엔드 /v1/life-patterns (LifePatternController).
 * 같은 요일끼리 겹치면 409, 10개 넘으면 400 → 백엔드 메시지를 그대로 토스트로 보여줘요 (Mutation.run)
 * 고정 시간이 바뀌면 백엔드가 겹치는 자동 배치 블록을 다시 배치해서, 화면에서 lifePatterns 와 timetable 을 같이 다시 불러와요.
 */
object LifePatternAPI {
    suspend fun create(v: LifePatternInput) {
        val body = JsonObject(commonBody(v) + ("patternType" to toJsonValue(v.patternType.rawValue)))
        API.send("POST", "/v1/life-patterns", body)
    }

    /** 종류(patternType)는 바꿀 수 없어요 (수면은 하루 범위 계산에 쓰이므로) */
    suspend fun update(lifePatternId: Int, v: LifePatternInput) {
        API.send("PUT", "/v1/life-patterns/$lifePatternId", JsonObject(commonBody(v)))
    }

    suspend fun delete(lifePatternId: Int) {
        API.send("DELETE", "/v1/life-patterns/$lifePatternId")
    }

    private fun commonBody(v: LifePatternInput): Map<String, kotlinx.serialization.json.JsonElement> = json(
        "title" to v.title,
        "emoji" to v.emoji,   // null 이면 JSON null (iOS .of(nil))
        "startTime" to v.startTime,
        "endTime" to v.endTime,
        "days" to v.days.map { it.name },
        "dayTimes" to v.dayTimes.map { t ->
            json(
                "day" to t.day.name,
                "startTime" to LifePatternRules.toHm(t.startTime),
                "endTime" to LifePatternRules.toHm(t.endTime),
                "startDayOffset" to t.startDayOffset,
            )
        },
    )
}

// MARK: - 미리보기 샘플

object LifePatternSamples {
    val patterns: List<LifePattern> by lazy {
        val json = """
        [
          {"lifePatternId":1,"patternType":"SLEEP","title":"수면","emoji":"🌙","startTime":"00:00:00","endTime":"07:00:00",
           "days":[],"everyDay":true,"crossesMidnight":false,"sortOrder":0,
           "dayTimes":[{"day":"SATURDAY","startTime":"01:00:00","endTime":"09:30:00","startDayOffset":0}]},
          {"lifePatternId":2,"patternType":"LUNCH","title":"점심","emoji":"🍚","startTime":"12:00:00","endTime":"13:00:00",
           "days":[],"everyDay":true,"crossesMidnight":false,"sortOrder":1},
          {"lifePatternId":3,"patternType":"CUSTOM","title":"이동시간","emoji":"🚇","startTime":"08:10:00","endTime":"09:00:00",
           "days":["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY"],"everyDay":false,"crossesMidnight":false,"sortOrder":2},
          {"lifePatternId":4,"patternType":"CUSTOM","title":"회사","emoji":"💼","startTime":"09:30:00","endTime":"18:00:00",
           "days":["MONDAY","WEDNESDAY","FRIDAY"],"everyDay":false,"crossesMidnight":false,"sortOrder":3}
        ]
        """.trimIndent()
        runCatching { NJson.decodeFromString(ListSerializer(LifePattern.serializer()), json) }.getOrDefault(emptyList())
    }
}
