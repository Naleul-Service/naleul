// Features/Goal/GoalDetailLogic.kt
//
// iOS GoalDetailLogic.swift — 목표 화면들이 같이 쓰는 계산 (화면과 분리한 순수 함수).
// 웹 원본:
//   goal/ui/SubGoalsSection.tsx (activeSubGoals · areasOf · bucketOf · defaultPeriod)
//   goal/edit/inline.tsx (colorIdOf · toNum)
//   goal/ui/metricSentence.ts
//   goal/ui/GoalHeader.tsx (motiveLabel)
//   goal/ui/RoutinesSection.tsx (routineTimeText)
//   goal/ui/GoalTaskList.tsx (dayOf · dayLabel · isDone)
//   goal/ui/progress/ProgressSection.tsx (fmtNum · md)

package com.naleul.naleul.feature.goal

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

import java.util.Locale
import kotlin.math.max

object GoalDetailLogic {
    /** defaultPeriod 결과 (iOS 튜플 (start:, end:)) */
    data class DefaultPeriod(val start: String, val end: String) {
        companion object {}
    }

    // MARK: 세부 목표(영역)

    /** 화면에 보여줄 세부 목표 (삭제된 것 제외 — 백엔드가 소프트 삭제된 것도 내려줘요) */
    fun activeSubGoals(goal: GoalCategory): List<SubGoalInfo> =
        goal.generalCategories.filter { it.generalCategoryStatus != GoalStatus.DELETED }

    /** 사용자가 만든 영역만 ("기타 할 일" 그릇 제외) */
    fun areas(goal: GoalCategory): List<SubGoalInfo> =
        activeSubGoals(goal).filter { !(it.defaultBucket ?: false) }

    /** 목표의 "기타 할 일" 그릇 (영역을 안 고른 루틴·Task 가 들어가요) */
    fun bucket(goal: GoalCategory): SubGoalInfo? =
        activeSubGoals(goal).firstOrNull { it.defaultBucket ?: false }

    /** 새로 만들 때 기본 기간: 목표 기간 (기록형은 종료일이 없어서 오늘부터 3개월) */
    fun defaultPeriod(goal: GoalCategory, today: String = KST.today()): DefaultPeriod {
        val start = goal.goalCategoryStartDate ?: today
        val end = goal.goalCategoryEndDate ?: (if (goal.isRecordGoal) KST.addDays(today, 90) else start)
        return DefaultPeriod(start, if (end < start) start else end)
    }

    /** 마일스톤을 날짜순으로 */
    fun sortedMilestones(goal: GoalCategory): List<MilestoneInfo> =
        (goal.milestones ?: emptyList()).sortedBy { it.dueDate }

    // MARK: 색 · 숫자

    /** colorCode("3D5AFE" | "#3d5afe") → 내 색상 목록의 id (없으면 기본 색 → 첫 색) */
    fun colorIdOf(colors: List<UserColor>, code: String?): Int? {
        if (colors.isEmpty()) return null
        fun norm(v: String): String = v.replace("#", "").uppercase()
        if (code != null) {
            val hit = colors.firstOrNull { norm(it.hex) == norm(code) }
            if (hit != null) return hit.userColorId
        }
        return (colors.firstOrNull { it.isDefault } ?: colors[0]).userColorId
    }

    /** 웹 toNum: "" → null, "1.5" → 1.5, 숫자가 아니면 NaN */
    fun toNum(v: String): Double? {
        val t = v.trim()
        if (t.isEmpty()) return null
        return t.toDoubleOrNull() ?: Double.NaN
    }

    /** 소수 digits 자리까지 쓰고 뒤의 0 은 지워요 (웹 String(Number(v.toFixed(2)))) */
    fun trim(v: Double, digits: Int = 2): String {
        if (!v.isFinite()) return "-"
        // Locale.US: 기기 언어에 따라 소수점이 쉼표(,)로 바뀌지 않게
        var s = String.format(Locale.US, "%.${digits}f", v)
        if (s.contains(".")) {
            s = s.trimEnd('0')
            if (s.endsWith(".")) s = s.dropLast(1)
        }
        if (s == "-0") s = "0"
        return s
    }

    /** 72 → "72", 72.456 → "72.46" */
    fun fmtNum(v: Double): String = trim(v, 2)

    /** 값 + 단위 (소수 2자리) */
    fun withUnit(v: Double, unit: String?): String = "${trim(v, 2)}${unit ?: ""}"

    /** 계산으로 나온 값(계획값 · 차이)은 소수 1자리까지만 */
    fun withUnit1(v: Double, unit: String?): String = "${trim(v, 1)}${unit ?: ""}"

    /** "2026-10-08" → "10월 8일" */
    fun md(ymd: String): String = "${KST.monthOf(ymd)}월 ${KST.dayOfMonth(ymd)}일"

    // MARK: 수치 목표 한 문장

    /** "체중 80kg → 72kg, 1월 8일까지 · 주 평균 -0.6kg" */
    fun metricSentence(
        name: String,
        unit: String,
        start: Double?,
        target: Double?,
        startDate: String?,
        endDate: String?,
    ): String? {
        if (start == null || target == null || start.isNaN() || target.isNaN()) return null
        val u = unit.trim()
        val trimmedName = name.trim()
        val n = if (trimmedName.isEmpty()) "수치" else trimmedName
        var text = "$n ${trim(start, 2)}$u → ${trim(target, 2)}$u"
        if (!endDate.isNullOrEmpty()) {
            text += ", ${KST.monthOf(endDate)}월 ${KST.dayOfMonth(endDate)}일까지"
        }
        if (!startDate.isNullOrEmpty() && !endDate.isNullOrEmpty() && endDate > startDate) {
            val days = KST.diffDays(startDate, endDate).toDouble()
            val perWeek = (target - start) / max(days / 7, 1.0)
            if (perWeek.isFinite() && perWeek != 0.0) {
                val sign = if (perWeek > 0) "+" else ""
                text += " · 주 평균 $sign${trim(perWeek, 1)}$u"
            }
        }
        return text
    }

    // MARK: 머리말

    /** motive 칸의 제목 — 업무형은 "시작한 이유" 대신 만들 때 받은 한 줄 설명 */
    fun motiveLabel(goal: GoalCategory): String {
        if (!goal.isRecordGoal) return "이 목표를 시작한 이유"
        // 업무형은 회사 업무 · 취업 준비 · 동아리처럼 종류를 나누지 않아요
        return "무엇을 하나요"
    }

    // MARK: 루틴

    /** "19:30:00" → "19:30" (없으면 "") */
    fun hhmmOrEmpty(t: String?): String = GoalFormat.hhmm(t) ?: ""

    /** 루틴 시간 한 줄 — 요일별 시간이 있으면 시간이 같은 요일끼리 묶어서 "월·금 20:00~20:25 · 수 07:00~07:30" */
    fun routineTimeText(r: RoutineSummary): String {
        fun fmt(s: String?, e: String?): String {
            if (s.isNullOrEmpty()) return "시간 자동"
            if (!e.isNullOrEmpty()) return "$s~$e"
            return s
        }
        val base = GoalFormat.hhmm(r.repeatStartTime)
        val baseEnd = GoalFormat.hhmm(r.repeatEndTime)
        val dayTimes = r.dayTimes ?: emptyList()
        if (dayTimes.isEmpty()) return fmt(base, baseEnd)
        // 순서를 지키는 묶음 (LinkedHashMap 은 넣은 순서를 기억해요)
        val groups = LinkedHashMap<String, MutableList<JavaDayOfWeek>>()
        for (d in JavaDayOfWeek.entries) {
            if (!r.repeatDays.contains(d)) continue
            val own = dayTimes.firstOrNull { it.dayOfWeek == d }
            val key = own?.let { fmt(GoalFormat.hhmm(it.startTime), GoalFormat.hhmm(it.endTime)) } ?: fmt(base, baseEnd)
            groups.getOrPut(key) { mutableListOf() }.add(d)
        }
        return groups.entries.joinToString(" · ") { (k, days) -> "${days.joinToString("·") { it.label }} $k" }
    }

    // MARK: Task

    fun isDone(t: TimeBlockTask): Boolean = t.taskStatus == TaskStatus.COMPLETED

    /** 이 Task 가 화면에 놓이는 날짜 (완료 후 실제 → 계획 → 시간 미정 날짜 → 권장일 → 마감일) */
    fun dayOf(t: TimeBlockTask): String? {
        val shown = TimetableLayout.shownTime(t)
        if (shown != null) return shown.startAt.take(10)
        return t.date ?: t.scheduledDate ?: t.dueDate
    }

    /** "오늘" · "내일" · "어제" · "10.08 (목)" (해가 다르면 "2027.01.03 (일)") */
    fun dayLabel(ymd: String, today: String): String {
        if (ymd == today) return "오늘"
        if (ymd == KST.addDays(today, 1)) return "내일"
        if (ymd == KST.addDays(today, -1)) return "어제"
        val p = KST.parts(ymd)
        val md = String.format(Locale.US, "%02d.%02d", p.m, p.d)
        val date = if (p.y == KST.yearOf(today)) md else "${p.y}.$md"
        return "$date (${KST.weekdayLabel[KST.weekdayIndex(ymd)]})"
    }
}
