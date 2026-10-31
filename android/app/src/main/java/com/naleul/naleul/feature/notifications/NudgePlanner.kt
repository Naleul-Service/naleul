// feature/notifications/NudgePlanner.kt
//
// iOS Features/Notifications/NudgePlanner.swift
// 앞으로 사흘 동안 울릴 "실천 도우미" 알림을 계산해요 (화면 · 네트워크 없이 데이터만 받아서 → 테스트 · 미리보기 쉬움).
// 앱을 열 때마다 다시 계산해서 예약을 통째로 바꿔요. 그래서
//  - 일정이 바뀌면 다음에 앱을 열 때 알림 내용도 바뀌고
//  - "다시 시작" 알림은 앱을 열 때마다 뒤로 밀려서, 정말 며칠 안 들어왔을 때만 울려요.
//
// 시간 규칙
//  - 07:00 전 · 23:45 뒤에는 울리지 않아요 (잠을 깨우지 않게)
//  - 기상 · 취침 시각은 그날 캘린더의 수면 블록에서 읽고, 없으면 08:00 기상 · 23:30 취침으로 봐요
//  - 하루에 많아야 3개 (가장 중요한 순서대로) — 알림은 적을수록 피로가 덜해요
//  - 2026-10: "오늘 할 일이 많아 보여요" · "내일 할 일 n개가 기다려요"는 뺐어요 (실천에 도움이 안 돼서).
//    루틴을 놓친 순간의 알림은 서버가 구체적인 숫자로 보내요 (ROUTINE_NUDGE)

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

import java.util.Date
import java.util.Locale

data class NudgeSnapshot(
    /** 지금 (밀리초) */
    val now: Long,
    /** 오늘부터 사흘 (오늘 · 내일 · 모레) */
    val days: List<TimetableDay>,
    /** 이번 주 월요일 ~ 오늘 (주간 돌아보기 숫자용) */
    val week: List<TimetableDay>,
    val goals: List<GoalCategory>,
    /** 꺼 둔 묶음은 빼요 */
    val enabled: Set<NudgeGroup>,
) {
    companion object {}
}

object NudgePlanner {
    const val quietStart = 7 * 60
    const val quietEnd = 23 * 60 + 45
    const val maxPerDay = 3
    const val defaultWake = 8 * 60
    const val defaultBed = 23 * 60 + 30

    /** 우선순위가 높은 것부터 (하루 상한에 걸리면 뒤의 것이 빠져요) */
    private val priority: List<NudgeKind> = listOf(
        NudgeKind.dayWrapUp, NudgeKind.firstStep, NudgeKind.countdown,
        NudgeKind.weeklyReport, NudgeKind.monthlyReport, NudgeKind.why, NudgeKind.comeback,
    )

    /** 알림 문구 한 쌍 (Swift 튜플 (title:, body:)) */
    data class Line(val title: String, val body: String)

    fun plan(s: NudgeSnapshot): List<NudgeEntry> {
        val now = KST.now(Date(s.now))
        val today = now.date
        val out = mutableListOf<NudgeEntry>()

        fun add(kind: NudgeKind, day: String, minutes: Int, key: String = "", title: String, body: String, route: NudgeRoute) {
            val g = kind.group ?: return
            if (!s.enabled.contains(g)) return
            // 조용한 시간 · 이미 지난 시각은 빼요
            if (minutes < quietStart || minutes > quietEnd) return
            val fire = KST.dateFromDateTime(KST.toDateTime(day, minutes)).time
            if (fire <= s.now + 60_000L) return
            out.add(
                NudgeEntry(
                    id = "nudge.${kind.rawValue}.$day${if (key.isEmpty()) "" else ".$key"}",
                    kind = kind, title = title, body = body, date = fire, route = route.raw,
                )
            )
        }

        val byDate = LinkedHashMap<String, TimetableDay>()
        for (d in s.days) if (!byDate.containsKey(d.date)) byDate[d.date] = d

        for (offset in 0 until 3) {
            val d = KST.addDays(today, offset)
            val day = byDate[d] ?: continue
            val wake = wakeMinutes(day) ?: defaultWake
            val bed = bedMinutes(day) ?: defaultBed
            val tasks = scheduled(day)
            val open = tasks.filter { !it.task.isCompleted }

            // ① 아침 첫걸음 (A) — 전부가 아니라 "하나만"
            val first = open.firstOrNull { it.start >= wake } ?: open.firstOrNull()
            if (first != null) {
                val rest = open.size - 1
                add(
                    NudgeKind.firstStep, day = d, minutes = wake + 20,
                    title = "오늘은 이것 하나만 먼저 👣",
                    body = "${KST.formatMinutes(first.start)} ${name(first.task)}" +
                        (if (rest > 0) " — 나머지 ${rest}개는 이것부터 끝내고 봐요." else " — 오늘은 이것만 하면 돼요."),
                    route = NudgeRoute.calendar(date = d),
                )
            }

            // ③ 하루 마무리 (P) — 마지막 일정이 끝나고 10분 뒤 (18:00 ~ 23:00 사이)
            if (tasks.isNotEmpty()) {
                val lastEnd = tasks.maxOfOrNull { it.end } ?: (21 * 60)
                val m = minOf(maxOf(lastEnd + 10, 18 * 60), minOf(23 * 60, bed - 40))
                add(
                    NudgeKind.dayWrapUp, day = d, minutes = m,
                    title = "오늘 일정이 끝났어요 🌙",
                    body = "3분만 오늘을 돌아볼까요? 한 일은 체크하고 못 한 이유는 한 줄로 남겨 봐요. 체크한 기록으로 나의 패턴이 잘 지키는 시간을 찾아요.",
                    route = NudgeRoute.homeRetro,
                )
            }

            // ⑤ 주간 돌아보기 — 일요일 20:30
            if (KST.weekdayIndex(d) == 6) {
                add(
                    NudgeKind.weeklyReport, day = d, minutes = 20 * 60 + 30,
                    title = "이번 주 돌아보기 📊",
                    body = weekLine(s.week) + " 잘한 것과 못 한 이유를 한 줄씩 남겨 봐요.",
                    route = NudgeRoute.reflections,
                )
            }

            // ⑥ 월간 돌아보기 — 그 달 마지막 날 21:00
            if (KST.dayOfMonth(d) == KST.lastDayOfMonth(KST.yearOf(d), KST.monthOf(d))) {
                add(
                    NudgeKind.monthlyReport, day = d, minutes = 21 * 60,
                    title = "${KST.monthOf(d)}월을 돌아볼 시간이에요",
                    body = "한 달 동안 쌓인 기록을 모아 뒀어요. 다음 달에 가져갈 것 하나만 골라 봐요.",
                    route = NudgeRoute.patterns,
                )
            }
        }

        // ⑦ 목표: 끝이 보여요 · 시작한 이유 (M)
        val active = s.goals.filter { it.goalCategoryStatus == GoalStatus.IN_PROGRESS && !it.isTemporary && !it.isHabitGoal }
        for (g in active) {
            val end = g.goalCategoryEndDate ?: continue
            val left = KST.diffDays(today, end)
            val title = goalName(g)
            for (offset in 0 until 3) {
                val d = KST.addDays(today, offset)
                val l = left - offset
                val line = countdownLine(goal = title, left = l, start = g.goalCategoryStartDate, end = end, day = d)
                if (line != null) {
                    add(
                        NudgeKind.countdown, day = d, minutes = 9 * 60, key = "${g.goalCategoryId}",
                        title = line.title, body = line.body, route = NudgeRoute.goal(g.goalCategoryId),
                    )
                }
            }
        }
        // 시작한 이유 — 수 · 토 19:30 에 목표 하나씩 돌아가며 (이유를 적어 둔 목표 먼저)
        val whyGoals = active.sortedWith(
            compareBy<GoalCategory>({ if (it.motive?.isEmpty() == false) 0 else 1 }, { it.goalCategoryId })
        )
        if (whyGoals.isNotEmpty()) {
            for (offset in 0 until 3) {
                val d = KST.addDays(today, offset)
                val wd = KST.weekdayIndex(d)
                if (wd != 2 && wd != 5) continue
                val pick = whyGoals[Math.floorMod(KST.diffDays("2026-01-01", d) / 3, whyGoals.size)]
                val line = whyLine(pick, day = d)
                add(
                    NudgeKind.why, day = d, minutes = 19 * 60 + 30, key = "${pick.goalCategoryId}",
                    title = line.title, body = line.body, route = NudgeRoute.goal(pick.goalCategoryId),
                )
            }
        }

        // ⑧ 다시 시작 — 지금부터 이틀 · 닷새 동안 안 들어오면 (앱을 열면 다시 뒤로 밀려요)
        add(
            NudgeKind.comeback, day = KST.addDays(today, 2), minutes = 20 * 60, key = "2",
            title = "완벽하지 않아도 괜찮아요",
            body = "다시 시작하면 돼요. 오늘은 10분짜리 하나만 해볼까요?",
            route = NudgeRoute.calendar(date = KST.addDays(today, 2)),
        )
        add(
            NudgeKind.comeback, day = KST.addDays(today, 5), minutes = 20 * 60, key = "5",
            title = "쉬어 가도 괜찮아요",
            body = "돌아오면 밀린 일은 같이 정리해 줄게요. 다시 짜기만 하면 돼요.",
            route = NudgeRoute.calendarAi("며칠 쉬어서 밀린 일정이 있어요. 무리하지 않게 다시 짜줘"),
        )

        return limitPerDay(out)
    }

    // MARK: - 문구

    /** 끝이 있다는 걸 알려줘요. 얼마 안 남았으면 "해방까지 n일", 길면 다른 단위(남은 주말 · 지난 기간 %)로. */
    fun countdownLine(goal: String, left: Int, start: String?, end: String, day: String): Line? {
        return when (left) {
            0 -> Line("오늘이 '$goal' 마지막 날이에요 🏁", "여기까지 온 것만으로도 대단해요. 마지막 하나만 해내면 해방이에요.")
            1 -> Line("내일이면 해방! 🎉", "'$goal' 마지막 하루 전이에요. 오늘 할 것만 끝내면 돼요.")
            3 -> Line("'$goal' 해방까지 3일", "끝이 보여요. 남은 사흘은 꼭 해야 할 것만 해도 충분해요.")
            7 -> Line("'$goal' 끝까지 일주일", "마지막 스퍼트예요. 이번 주만 버티면 돼요.")
            else -> {
                // 긴 목표: 월요일마다 한 번, 남은 주말 수 + 지난 기간 %
                if (left <= 14 || KST.weekdayIndex(day) != 0) return null
                val weekends = maxOf(1, left / 7)
                var body = "주말 ${weekends}번만 더 보내면 끝나요."
                if (start != null && KST.diffDays(start, end) > 0) {
                    val total = KST.diffDays(start, end)
                    val passed = maxOf(0, minOf(total, KST.diffDays(start, day)))
                    body += " 벌써 기간의 ${passed * 100 / total}%를 지나왔어요."
                }
                Line("'$goal' 끝까지 ${left}일", body)
            }
        }
    }

    /** 시작한 이유 — 적어 둔 이유가 있으면 "n일 전의 내가 보낸 말", 없으면 이루고 난 나를 떠올리게 */
    fun whyLine(g: GoalCategory, day: String): Line {
        val goal = goalName(g)
        val motive = g.motive?.trim()
        if (!motive.isNullOrEmpty()) {
            val start = g.goalCategoryStartDate
            if (start != null && KST.diffDays(start, day) > 0) {
                return Line("${KST.diffDays(start, day)}일 전의 내가 보낸 말 💌", "“$motive” — '$goal'을(를) 시작한 이유예요.")
            }
            return Line("'$goal'을(를) 시작한 이유 💌", "“$motive”")
        }
        val target = g.targetValue
        val metric = g.metricName
        if (target != null && !metric.isNullOrEmpty()) {
            val unit = g.metricUnit ?: ""
            val v = if (target % 1.0 == 0.0) target.toLong().toString() else String.format(Locale.US, "%.1f", target)
            return Line("이루고 난 나를 떠올려 봐요 ✨", "$metric $v${unit}에 닿은 날, 무엇이 달라져 있을까요? 오늘 한 걸음이 거기로 가요.")
        }
        return Line("이루고 난 나를 떠올려 봐요 ✨", "${TimetableRules.eulReul("'$goal'")} 이루면 무엇이 달라질까요? 오늘 한 걸음이면 충분해요.")
    }

    /** 주간 돌아보기 첫 줄 — 지금까지 이번 주 숫자 */
    fun weekLine(week: List<TimetableDay>): String {
        val total = week.sumOf { it.stats.total }
        val done = week.sumOf { it.stats.completed }
        if (total <= 0) return "이번 주는 기록이 거의 없었어요. 괜찮아요, 다음 주는 하나부터."
        return "이번 주 ${total}개 중 ${done}개를 했어요 (${done * 100 / total}%)."
    }

    // MARK: - 데이터 읽기

    data class Slot(
        val task: TimeBlockTask,
        val start: Int,
        val end: Int,
    )

    /** 시간을 정한 Task (그날 0시 기준 분, 시작 순) */
    fun scheduled(day: TimetableDay): List<Slot> =
        day.tasks
            .filter { it.taskStatus != TaskStatus.DELETED && it.taskStatus != TaskStatus.SKIPPED && it.proposed != true }
            .mapNotNull { t ->
                val s = t.plannedStartAt ?: return@mapNotNull null
                val start = KST.minutesFrom(day.date, s)
                val e = t.plannedEndAt
                val end = if (e != null) KST.minutesFrom(day.date, e) else start + (t.plannedDurationMinutes ?: 30)
                Slot(task = t, start = start, end = end)
            }
            .sortedBy { it.start }

    fun wakeMinutes(day: TimetableDay): Int? {
        val w = day.sleep?.wake ?: return null
        val m = KST.minutesFrom(day.date, w.end)
        return if (m in 0 until 14 * 60) m else null
    }

    fun bedMinutes(day: TimetableDay): Int? {
        val b = day.sleep?.bed ?: return null
        val m = KST.minutesFrom(day.date, b.start)
        return if (m in (18 * 60)..(26 * 60)) minOf(m, 24 * 60) else null
    }

    private fun name(t: TimeBlockTask): String = "'${TimetableRules.title(t.emoji, t.taskName)}'"

    private fun goalName(g: GoalCategory): String = TimetableRules.title(g.emoji, g.goalCategoryName)

    /** 하루에 많아야 maxPerDay 개 — 우선순위 순으로 남겨요 */
    private fun limitPerDay(list: List<NudgeEntry>): List<NudgeEntry> {
        fun rank(k: NudgeKind): Int = priority.indexOf(k).let { if (it < 0) priority.size else it }
        val byDay = list.groupBy { KST.ymd(Date(it.date)) }
        return byDay.values
            .flatMap { entries -> entries.sortedBy { rank(it.kind) }.take(maxPerDay) }
            .sortedBy { it.date }
    }
}
