// feature/goalcreation/DraftPlanUtils.kt
//
// iOS Features/GoalCreation/DraftPlanUtils.swift
// 초안(GoalPlan) 날짜 표시 · 자동 보정 · 검증.
// 웹 원본: features/goal-creation/ui/draft/planUtils.ts
//
// 날짜는 "YYYY-MM-DD" 문자열이라 문자열 비교(<, >)가 곧 날짜 비교예요 (웹과 같아요).

package com.naleul.naleul.feature.goalcreation

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

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

/** 항목 키 → 고쳐야 할 이유들 (iOS typealias DraftIssueMap = [String: [String]]) */
typealias DraftIssueMap = Map<String, List<String>>

object DraftPlanUtils {
    // MARK: 날짜

    private fun pad(n: Int): String = if (n < 10) "0$n" else "$n"

    /** "2026-10-02" → "10.02 (금)" */
    fun formatMd(value: String?, withWeekday: Boolean = true): String {
        if (value.isNullOrEmpty()) return ""
        val p = KST.parts(value)
        val md = "${pad(p.m)}.${pad(p.d)}"
        return if (withWeekday) "$md (${KST.weekdayLabel[KST.weekdayIndex(value)]})" else md
    }

    /** "2026-10-02" → "2026.10.02" */
    fun formatYmdDot(value: String): String = value.replace("-", ".")

    fun daysBetween(from: String, to: String): Int = KST.diffDays(from, to)

    /** 오늘 기준 D-day 숫자 (남은 일수) */
    fun dDay(to: String): Int = daysBetween(KST.today(), to)

    fun formatDays(days: List<GoalCreationWeekday>?): String {
        if (days.isNullOrEmpty()) return ""
        if (days.size == 7) return "매일"
        return days.joinToString("·") { it.label }
    }

    /** 숫자를 웹(JS)처럼: 72.0 → "72", 72.5 → "72.5" */
    fun num(v: Double): String {
        if (v.isFinite() && v == Math.rint(v) && abs(v) < 1e15) return v.toLong().toString()
        return v.toString()
    }

    // MARK: 편집 도우미

    private var seq = 0

    /** 새로 추가하는 항목의 tempId (중복만 안 되면 돼요) */
    fun newTempId(prefix: String): String {
        seq += 1
        val t = System.currentTimeMillis().toString(36)
        return "$prefix-new-$t-$seq"
    }

    /** 루틴 주간 총 시간(분) */
    fun weeklyRoutineMinutes(tasks: List<DraftPlanTask>): Int =
        tasks.filter { it.isRoutine }.sumOf { it.durationMinutes * (it.routineDays?.size ?: 0) }

    /** 60 → "1시간", 75 → "1시간 15분", 40 → "40분" (웹 hoursText) */
    fun hoursText(minutes: Int): String {
        if (minutes < 60) return "${minutes}분"
        val h = minutes / 60
        val m = minutes % 60
        return if (m > 0) "${h}시간 ${m}분" else "${h}시간"
    }

    // MARK: 자동 보정

    /** 시작값·목표값 자리수에 맞춰 반올림 (80 → 72 면 정수, 80.5 → 72 면 소수 첫째 자리) */
    private fun roundLike(value: Double, vararg refs: Double): Double {
        val decimals = refs.map { r ->
            val s = num(r)
            val dot = s.indexOf('.')
            if (dot < 0) 0 else s.length - dot - 1
        }.maxOrNull() ?: 0
        val f = 10.0.pow(decimals.coerceIn(0, 2).toDouble())
        // Swift .rounded() 는 0.5 를 0 에서 먼 쪽으로 올려요 (Math.round 는 음수에서 다름 → 직접)
        val x = value * f
        val r = if (x >= 0) (x + 0.5).let { kotlin.math.floor(it) } else -kotlin.math.floor(-x + 0.5)
        return r / f
    }

    /**
     * 사용자가 무엇을 고치든 계획이 "깨지지 않게" 자동으로 맞춰요.
     *  - 마일스톤: 날짜순 정렬 · 목표 기간 안으로 · 겹치는 날짜는 하루씩 밀기 · 마지막은 종료일
     *  - 마일스톤 수치: 시작값 → 목표값 직선 위의 값 (날짜 비율대로) — 그래프가 항상 한 방향
     *  - 일회성 할 일: 마감이 연결된 마일스톤보다 늦으면 마일스톤 날짜로, 실행일이 마감보다 늦으면 마감으로
     *  - 시작일을 미뤘을 때: 루틴 시작일이 그보다 앞이면 비워서(= 목표 시작일부터), 일회성 날짜도 시작일 이후로
     */
    fun normalizePlan(plan: DraftGoalPlan): DraftGoalPlan {
        val goal = plan.goal
        val start = goal.startDate
        val end = goal.endDate
        if (start.isEmpty() || end.isEmpty() || end <= start) return plan

        // 1) 날짜
        val milestones = plan.milestones.sortedBy { it.dueDate }.toMutableList()
        for (i in milestones.indices) {
            val m = milestones[i]
            var due = if (m.dueDate < start) KST.addDays(start, 1) else (if (m.dueDate > end) end else m.dueDate)
            if (i > 0 && due <= milestones[i - 1].dueDate) due = KST.addDays(milestones[i - 1].dueDate, 1)
            milestones[i] = m.copy(dueDate = if (due > end) end else due)
        }
        if (milestones.isNotEmpty()) milestones[milestones.lastIndex] = milestones.last().copy(dueDate = end)

        // 2) 수치 — 직선 보간
        val total = maxOf(daysBetween(start, end), 1)
        val metric = goal.metric
        for (i in milestones.indices) {
            if (metric == null) {
                milestones[i] = milestones[i].copy(targetValue = null)
                continue
            }
            val ratio = (daysBetween(start, milestones[i].dueDate).toDouble() / total.toDouble()).coerceIn(0.0, 1.0)
            val raw = metric.startValue + (metric.targetValue - metric.startValue) * ratio
            milestones[i] = milestones[i].copy(targetValue = roundLike(raw, metric.startValue, metric.targetValue))
        }
        if (metric != null && milestones.isNotEmpty()) {
            milestones[milestones.lastIndex] = milestones.last().copy(targetValue = metric.targetValue)
        }

        // 3) 할 일 날짜
        val dueOf = milestones.associate { it.tempId to it.dueDate }
        val tasks = plan.tasks.map { t ->
            if (t.isRoutine) {
                var t2 = t
                val s = t.startDate
                if (s != null && s < start) t2 = t2.copy(startDate = null)
                val e = t.endDate
                if (e != null && (e > end || e < start)) t2 = t2.copy(endDate = null)
                return@map t2
            }
            val msDue = t.milestoneTempId?.let { dueOf[it] }
            var due = t.dueDate
            if (due != null && msDue != null && due > msDue) due = msDue
            if (due != null && due > end) due = end
            if (due != null && due < start) due = msDue ?: start
            var scheduled = t.scheduledDate
            if (scheduled != null && due != null && scheduled > due) scheduled = due
            if (scheduled != null && scheduled < start) scheduled = null
            t.copy(dueDate = due, scheduledDate = scheduled)
        }

        return plan.copy(milestones = milestones, tasks = tasks)
    }

    // MARK: 검증
    // 서버 HARD 규칙 중 앱에서 바로 확인 가능한 것들. path 형식을 서버 violations 와 같게 맞춰서
    // 화면 표시 로직을 하나로 써요.

    /** 최소 · 최대 (iOS 튜플 (min:, max:)) */
    data class Range(val min: Int, val max: Int) { companion object }

    /** 사용자가 초안을 고칠 때의 한도 = 서버 GoalPlanValidator.USER_LIMITS */
    object Limits {
        const val title = 40
        val subGoals = Range(min = 1, max = 5)
        // V52: AI 초안은 마일스톤 없이 와요 (서버 USER_LIMITS 0~8)
        val milestones = Range(min = 0, max = 8)
        val routines = Range(min = 0, max = 10)
        val oneTimes = Range(min = 0, max = 30)
        val routineMinutes = Range(min = 1, max = 1440)
        val oneTimeMinutes = Range(min = 1, max = 1440)
        const val routineDescription = 300
    }

    fun validatePlan(plan: DraftGoalPlan): List<DraftPlanIssue> {
        val issues = mutableListOf<DraftPlanIssue>()
        fun add(path: String, message: String) {
            issues.add(DraftPlanIssue(ruleId = "CLIENT", path = path, message = message))
        }
        fun titleOk(t: String): Boolean {
            val n = GoalCreationForm.count(t.trim())
            return n >= 1 && n <= Limits.title
        }

        if (!titleOk(plan.goal.title)) add("goal.title", "목표 이름은 1~${Limits.title}자로 적어 주세요.")

        val sg = Limits.subGoals
        if (plan.subGoals.size < sg.min || plan.subGoals.size > sg.max) {
            add("subGoals", "세부 목표는 ${sg.min}~${sg.max}개여야 해요.")
        }
        plan.subGoals.forEachIndexed { i, s ->
            if (!titleOk(s.title)) add("subGoals[$i].title", "제목은 1~${Limits.title}자로 적어 주세요.")
        }

        val ms = Limits.milestones
        if (plan.milestones.size < ms.min || plan.milestones.size > ms.max) {
            add("milestones", "마일스톤은 ${ms.min}~${ms.max}개여야 해요.")
        }
        plan.milestones.forEachIndexed { i, m ->
            if (!titleOk(m.title)) add("milestones[$i].title", "제목은 1~${Limits.title}자로 적어 주세요.")
            if (i > 0 && m.dueDate <= plan.milestones[i - 1].dueDate) {
                add("milestones[$i].dueDate", "마일스톤 날짜는 앞 단계보다 뒤여야 해요.")
            }
        }
        val last = plan.milestones.lastOrNull()
        if (last != null && last.dueDate != plan.goal.endDate) {
            add(
                "milestones[${plan.milestones.size - 1}].dueDate",
                "마지막 마일스톤은 목표 종료일(${formatYmdDot(plan.goal.endDate)})이어야 해요.",
            )
        }

        val routineCount = plan.tasks.count { it.isRoutine }
        if (plan.tasks.isEmpty()) add("tasks", "할 일을 하나 이상 남겨 주세요.")
        if (routineCount < Limits.routines.min || routineCount > Limits.routines.max) {
            add("tasks", "루틴은 ${Limits.routines.max}개까지 만들 수 있어요.")
        }
        // 일회성 Task 개수는 검사하지 않아요 — AI 목표는 루틴 중심이라 0개가 기본이에요

        plan.tasks.forEachIndexed { i, t ->
            val p = "tasks[$i]"
            if (!titleOk(t.title)) add("$p.title", "제목은 1~${Limits.title}자로 적어 주세요.")
            if (t.isRoutine) {
                val r = Limits.routineMinutes
                if (t.durationMinutes < r.min || t.durationMinutes > r.max) {
                    add("$p.durationMinutes", "루틴은 ${r.min}~${r.max}분으로 설정해 주세요.")
                }
                if (t.routineDays.isNullOrEmpty()) add("$p.routineDays", "요일을 하나 이상 골라 주세요.")
            } else {
                val r = Limits.oneTimeMinutes
                if (t.durationMinutes < r.min || t.durationMinutes > r.max) {
                    add("$p.durationMinutes", "할 일은 ${r.min}~${r.max}분으로 설정해 주세요.")
                }
                val due = t.dueDate
                if (due == null) add("$p.dueDate", "마감일을 정해 주세요.")
                val s = t.scheduledDate
                if (s != null && due != null && s > due) {
                    add("$p.scheduledDate", "실행일은 마감일보다 늦을 수 없어요.")
                }
                val m = plan.milestones.firstOrNull { it.tempId == t.milestoneTempId }
                if (m != null && due != null && due > m.dueDate) {
                    add("$p.dueDate", "마감일은 연결된 마일스톤(${formatMd(m.dueDate, withWeekday = false)})보다 늦을 수 없어요.")
                }
            }
        }
        return issues
    }

    private val itemKeyRegex = Regex("^[a-zA-Z]+(\\[\\d+])?")

    /**
     * violation path → 화면 항목 키
     *  "tasks[1].durationMinutes" → "tasks[1]",  "goal.title" → "goal",  "milestones" → "milestones"
     */
    fun itemKeyOf(path: String): String = itemKeyRegex.find(path)?.value ?: path

    /** 항목 키별 메시지 묶기 */
    fun groupIssues(issues: List<DraftPlanIssue>): Map<String, List<String>> {
        val map = linkedMapOf<String, MutableList<String>>()
        for (issue in issues) {
            val key = itemKeyOf(issue.path)
            val list = map.getOrPut(key) { mutableListOf() }
            if (!list.contains(issue.message)) list.add(issue.message)
        }
        return map
    }
}
