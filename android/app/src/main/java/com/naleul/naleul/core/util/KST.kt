// core/util/KST.kt
//
// iOS Core/Util/KST.swift 를 그대로 옮긴 날짜·시간 도우미.
//
// 백엔드는 "한국 시간 벽시계" 문자열을 주고받아요. (Z, +09:00 없음)
//   LocalDate      → "2026-11-18"
//   LocalDateTime  → "2026-11-18T09:30:00"
//   LocalTime      → "09:30:00"
// 기기 시간대가 한국이 아니어도 어긋나지 않게, 날짜는 "YYYY-MM-DD" 문자열, 시간은 "그날 0시부터 몇 분" 숫자로만 다뤄요.
//
//   KST.today()                       // "2026-11-18"
//   KST.addDays("2026-11-18", 1)      // "2026-11-19"
//   KST.minutesFrom(day, "2026-11-19T01:00:00")   // 1500 (다음 날 01:00)
//   KST.formatMonthDay("2026-11-18")  // "11월 18일 (수)"
//
// 계산은 java.time.LocalDate(시간대 없는 날짜)로 해서 서머타임 영향이 없어요.

package com.naleul.naleul.core.util

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Date
import kotlin.math.floor
import kotlin.math.roundToInt

object KST {
    val timeZone: ZoneId = ZoneId.of("Asia/Seoul")

    /** 드래그 · 시간 선택 단위 (분) */
    const val snapMinutes = 10

    private fun pad(n: Int): String = if (n < 10) "0$n" else "$n"

    data class YMD(val y: Int, val m: Int, val d: Int)

    /** "YYYY-MM-DD" → (y, m, d) */
    fun parts(ymd: String): YMD {
        val p = ymd.take(10).split("-").mapNotNull { it.toIntOrNull() }
        if (p.size != 3) return YMD(1970, 1, 1)
        return YMD(p[0], p[1], p[2])
    }

    private fun toLocal(ymd: String): LocalDate {
        val p = parts(ymd)
        return runCatching { LocalDate.of(p.y, p.m, p.d) }.getOrElse {
            // 2월 30일 같은 값 → 그 달 1일에서 일수만큼 더해요 (iOS Calendar 와 비슷하게)
            runCatching { LocalDate.of(p.y, p.m, 1).plusDays((p.d - 1).toLong()) }.getOrElse { LocalDate.of(1970, 1, 1) }
        }
    }

    private fun fromLocal(date: LocalDate): String = ymd(date.year, date.monthValue, date.dayOfMonth)

    fun ymd(year: Int, month: Int, day: Int): String = "$year-${pad(month)}-${pad(day)}"

    /** 올바른 "YYYY-MM-DD" 인지 */
    fun isYmd(v: String?): Boolean {
        if (v == null || v.length != 10 || !Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(v)) return false
        return fromLocal(toLocal(v)) == v
    }

    fun addDays(ymd: String, n: Int): String = fromLocal(toLocal(ymd).plusDays(n.toLong()))

    /** 두 날짜 차이 (b - a, 일) */
    fun diffDays(a: String, b: String): Int = ChronoUnit.DAYS.between(toLocal(a), toLocal(b)).toInt()

    /** 0=월 … 6=일 */
    fun weekdayIndex(ymd: String): Int = toLocal(ymd).dayOfWeek.value - 1

    /** 그 주 월요일 */
    fun startOfWeek(ymd: String): String = addDays(ymd, -weekdayIndex(ymd))

    val weekdayLabel = listOf("월", "화", "수", "목", "금", "토", "일")

    fun dayOfMonth(ymd: String): Int = parts(ymd).d
    fun monthOf(ymd: String): Int = parts(ymd).m
    fun yearOf(ymd: String): Int = parts(ymd).y

    data class Now(val date: String, val minutes: Int)

    /** 지금 한국 시간 (date: "YYYY-MM-DD", minutes: 0~1439) */
    fun now(at: Date = Date()): Now {
        val z = ZonedDateTime.ofInstant(at.toInstant(), timeZone)
        return Now(ymd(z.year, z.monthValue, z.dayOfMonth), z.hour * 60 + z.minute)
    }

    fun today(): String = now().date

    data class DateMinutes(val date: String, val minutes: Int)

    /** "2026-11-18T09:30:00" → (date: "2026-11-18", minutes: 570) */
    fun splitDateTime(v: String): DateMinutes {
        val comps = v.split("T", limit = 2)
        val date = comps.firstOrNull() ?: v
        val time = if (comps.size > 1) comps[1] else "00:00"
        val hm = time.split(":").mapNotNull { it.take(2).toIntOrNull() }
        val h = hm.getOrNull(0) ?: 0
        val m = hm.getOrNull(1) ?: 0
        return DateMinutes(date, h * 60 + m)
    }

    /** 어떤 날짜(dayYmd) 기준 0시부터 몇 분인지. 다음 날 01:00 이면 1500 */
    fun minutesFrom(dayYmd: String, v: String): Int {
        val s = splitDateTime(v)
        return diffDays(dayYmd, s.date) * 1440 + s.minutes
    }

    /** 날짜 + 분 → "YYYY-MM-DDTHH:mm:00" (분이 1440 이상이면 다음 날로) */
    fun toDateTime(dayYmd: String, minutes: Int): String {
        val dayShift = floor(minutes.toDouble() / 1440).toInt()
        val m = minutes - dayShift * 1440
        return "${addDays(dayYmd, dayShift)}T${pad(m / 60)}:${pad(m % 60)}:00"
    }

    /** 570 → "09:30", 1440 → "24:00" */
    fun formatMinutes(minutes: Int): String {
        val m = ((minutes % 1440) + 1440) % 1440
        val h = if (minutes > 0 && m == 0 && minutes % 1440 == 0) 24 else m / 60
        return "${pad(h)}:${pad(m % 60)}"
    }

    /** "2026-11-18T09:30:00" → "09:30" */
    fun hm(v: String?): String {
        if (v == null || v.length < 16) return ""
        return v.substring(11, 16)
    }

    /** "09:30:00" | "09:30" → 570 */
    fun timeToMinutes(t: String): Int {
        val p = t.split(":").mapNotNull { it.toIntOrNull() }
        return (p.getOrNull(0) ?: 0) * 60 + (p.getOrNull(1) ?: 0)
    }

    /**
     * 루틴 시간 범위가 맞는지 (서버 RoutineValidator 와 같아요)
     * 끝이 시작보다 이르면 자정을 넘기는 루틴 — 23:00 ~ 00:00 처럼 12시간까지 돼요
     */
    fun isValidRoutineRange(start: String, end: String): Boolean {
        val s = timeToMinutes(start)
        val e = timeToMinutes(end)
        if (s == e) return false
        if (e > s) return true
        return 1440 - (s - e) <= 12 * 60
    }

    /** 570 → "09:30" (LocalTime 요청용, 24:00 은 00:00) */
    fun minutesToTime(minutes: Int): String {
        val m = ((minutes % 1440) + 1440) % 1440
        return "${pad(m / 60)}:${pad(m % 60)}"
    }

    fun snap(minutes: Int, step: Int = snapMinutes): Int = (minutes.toDouble() / step).roundToInt() * step

    /** "11월 18일 (수)" */
    fun formatMonthDay(ymd: String): String =
        "${monthOf(ymd)}월 ${dayOfMonth(ymd)}일 (${weekdayLabel[weekdayIndex(ymd)]})"

    /** 주간 제목: "2026년 11월 16일 – 22일" / 달이 바뀌면 "2026년 11월 30일 – 12월 6일" */
    fun formatRange(start: String, end: String): String {
        if (start == end) return "${yearOf(start)}년 ${formatMonthDay(start)}"
        val head = "${yearOf(start)}년 ${monthOf(start)}월 ${dayOfMonth(start)}일"
        if (yearOf(start) != yearOf(end)) return "$head – ${yearOf(end)}년 ${monthOf(end)}월 ${dayOfMonth(end)}일"
        if (monthOf(start) != monthOf(end)) return "$head – ${monthOf(end)}월 ${dayOfMonth(end)}일"
        return "$head – ${dayOfMonth(end)}일"
    }

    /** 75 → "1시간 15분" */
    fun formatDuration(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        if (h > 0 && m > 0) return "${h}시간 ${m}분"
        if (h > 0) return "${h}시간"
        return "${m}분"
    }

    /** 그 달의 마지막 날 */
    fun lastDayOfMonth(year: Int, month: Int): Int =
        runCatching { LocalDate.of(year, month, 1).lengthOfMonth() }.getOrDefault(30)

    /** 달 이동. 31일 → 다음 달에 31일이 없으면 그 달 마지막 날 */
    fun addMonths(ymd: String, n: Int): String {
        val p = parts(ymd)
        val m0 = p.m - 1 + n
        val ny = p.y + floor(m0.toDouble() / 12).toInt()
        val nm = ((m0 % 12) + 12) % 12 + 1
        return ymd(ny, nm, minOf(p.d, lastDayOfMonth(ny, nm)))
    }

    /** 월간 달력 칸: 그 달 1일이 있는 주 월요일부터, 마지막 날이 있는 주 일요일까지 (주 단위 배열) */
    fun monthWeeks(year: Int, month: Int): List<List<String>> {
        val first = ymd(year, month, 1)
        val last = ymd(year, month, lastDayOfMonth(year, month))
        val weeks = mutableListOf<List<String>>()
        var d = startOfWeek(first)
        while (d <= last) {
            val start = d
            weeks.add((0 until 7).map { addDays(start, it) })
            d = addDays(d, 7)
        }
        return weeks
    }

    /** 오늘 기준 D-day 문구: 오늘 / 내일 / D-3 / 3일 지남 */
    fun dDay(ymd: String, today: String): String {
        val diff = diffDays(today, ymd)
        if (diff == 0) return "오늘"
        if (diff == 1) return "내일"
        if (diff < 0) return "${-diff}일 지남"
        return "D-$diff"
    }

    /** "YYYY-MM-DD" → 한국 자정 Date (날짜 선택기 등과 주고받을 때) */
    fun date(fromYmd: String): Date = Date.from(toLocal(fromYmd).atStartOfDay(timeZone).toInstant())

    /** Date → 한국 시간 기준 "YYYY-MM-DD" */
    fun ymd(fromDate: Date): String = now(fromDate).date

    /** "YYYY-MM-DDTHH:mm:ss" (한국 벽시계) → Date */
    fun dateFromDateTime(v: String): Date {
        val s = splitDateTime(v)
        return Date(date(s.date).time + s.minutes * 60_000L)
    }

    /** Date → "YYYY-MM-DDTHH:mm:ss" (한국 벽시계) */
    fun dateTime(fromDate: Date): String {
        val z = ZonedDateTime.ofInstant(fromDate.toInstant(), timeZone)
        return "${ymd(z.year, z.monthValue, z.dayOfMonth)}T${pad(z.hour)}:${pad(z.minute)}:${pad(z.second)}"
    }

    /** "2026-11-18" → "11/18" */
    fun slashDate(ymd: String): String = "${monthOf(ymd)}/${dayOfMonth(ymd)}"

    /** "2026-11-18" → "2026.11.18" */
    fun dotDate(ymd: String): String {
        val p = parts(ymd)
        return "${p.y}.${pad(p.m)}.${pad(p.d)}"
    }

    /** "YYYY-MM-DD" → UTC 자정 epoch millis (Material3 DatePicker 가 쓰는 값) */
    fun utcMillis(ymd: String): Long = toLocal(ymd).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()

    /** UTC 자정 epoch millis (Material3 DatePicker) → "YYYY-MM-DD" */
    fun ymdFromUtcMillis(millis: Long): String =
        fromLocal(java.time.Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate())
}

// MARK: - 요일 (백엔드 java.time.DayOfWeek)

@kotlinx.serialization.Serializable
enum class JavaDayOfWeek {
    MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY;

    val id: String get() = name

    /** 0=월 … 6=일 */
    val index: Int get() = ordinal

    /** "월" */
    val label: String get() = KST.weekdayLabel[index]

    companion object {
        fun from(index: Int): JavaDayOfWeek = entries[((index % 7) + 7) % 7]

        /** 그 날짜의 요일 */
        fun of(ymd: String): JavaDayOfWeek = from(KST.weekdayIndex(ymd))
    }
}
