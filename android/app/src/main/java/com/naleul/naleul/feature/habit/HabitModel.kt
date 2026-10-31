// feature/habit/HabitModel.kt
//
// iOS Features/Habit/HabitModel.swift
// 기본 생활 습관 화면(HabitGoalBody)의 데이터 — 웹 HabitView 의 useHabitHome + HabitHeatmap 의 useHabitHeatmap.
//
// - home: 고른 날(day)의 습관 목록 (오늘이면 date 없이 불러요)
// - heatmap: 습관별 잔디 (from ~ 오늘, 서버 최대 400일)
// 체크는 누르자마자 화면에 반영하고(낙관적 업데이트), 실패하면 되돌려요.

package com.naleul.naleul.feature.habit

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

import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException

class HabitModel(day: String = KST.today()) : ScreenModel() {
    /** 지금 보고 있는 날 (오늘 또는 지난 날) */
    var day by mutableStateOf(day)
    var phase by mutableStateOf<LoadPhase<HabitHome>>(LoadPhase.Loading)
    var heatmap by mutableStateOf<HabitHeatmap?>(null)
    /** 잔디를 불러온 from (같으면 다시 안 불러요 — 바뀐 게 있으면 habits 로 다시) */
    var heatmapFrom by mutableStateOf<String?>(null)
    var heatmapFailed by mutableStateOf(false)
    /** 체크 중인 습관 (두 번 연속 눌림 막기) */
    var togglingIds by mutableStateOf<Set<Int>>(emptySet())
    /** 추천 습관을 추가하는 중 */
    var addingSuggestion by mutableStateOf(false)

    /** 미리보기용 (네트워크 안 씀) */
    var isPreview = false

    // 앰플리튜드 — 화면당 1회 보내는 이벤트 기록
    private var pageviewSent = false
    private val allDoneSeen = mutableSetOf<String>()

    /** 오늘에서 며칠 전인지 (오늘 = 0) */
    val daysAgo: Int get() = GD.daysAgo(day) ?: 0

    fun track(event: String, props: EventProps = emptyMap()) {
        if (isPreview) return
        GD.track(event, props)
    }

    val home: HabitHome? get() = phase.value
    val habits: List<Habit> get() = home?.habits ?: emptyList()
    val today: String get() = KST.today()
    val isToday: Boolean get() = day == today

    /** 그날 해야 하는 습관 (진행 중 · 그 요일) */
    val due: List<Habit> get() = habits.filter { it.isActive && it.scheduled }
    /** 그날은 안 해도 되는 습관 */
    val off: List<Habit> get() = habits.filter { it.isActive && !it.scheduled }
    /** 쉬는 중 */
    val paused: List<Habit> get() = habits.filter { it.isPaused }
    val doneCount: Int get() = due.count { it.checked }

    // MARK: 불러오기

    suspend fun load(day: String) {
        if (isPreview) return
        // 다른 날로 넘기면 그날 목록이 올 때까지 스피너
        if (home?.date != day) phase = LoadPhase.Loading
        try {
            val isToday = day == KST.today()
            val h = HabitAPI.home(date = if (isToday) null else day)
            if (h != null) {
                phase = LoadPhase.Loaded(h)
                if (!pageviewSent) {
                    pageviewSent = true
                    track("habit.pageview", mapOf(
                        "goal_category_id" to h.goalCategoryId, "habit_count" to h.habits.size,
                        "due_today_count" to due.size, "done_count" to doneCount, "paused_count" to paused.size,
                    ))
                }
            } else {
                phase = LoadPhase.Failed("습관을 불러오지 못했어요")
                track("habit.load_fail", mapOf("error_status" to 404, "days_ago" to daysAgo))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (home == null || home?.date != day) {
                phase = LoadPhase.Failed(errorMessage(e))
                track("habit.load_fail", mapOf("error_status" to ((e as? ApiError)?.httpStatus ?: 0), "days_ago" to daysAgo))
            }
        }
    }

    /** 잔디 — from(포함) ~ 오늘 */
    suspend fun loadHeatmap(from: String) {
        if (isPreview) return
        try {
            heatmap = HabitAPI.heatmap(from = from)
            heatmapFrom = from
            heatmapFailed = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (heatmap == null) heatmapFailed = true
        }
    }

    // MARK: 체크

    /** 체크 · 체크 풀기 (낙관적 — 실패하면 되돌려요) */
    suspend fun toggle(habit: Habit) {
        if (habit.isPaused || togglingIds.contains(habit.habitId)) return
        val before = (phase as? LoadPhase.Loaded)?.value ?: return
        val target = !habit.checked
        val d = day
        val action = if (target) "complete" else "uncomplete"
        track("habit.check_toggle", mapOf(
            "habit_id" to habit.habitId, "check_action" to action, "days_ago" to daysAgo,
            "is_due_day" to habit.scheduled, "streak" to habit.streak,
        ))
        apply(habitId = habit.habitId, checked = target)
        if (target && due.isNotEmpty() && doneCount == due.size && allDoneSeen.add(d)) {
            track("habit.all_done_view", mapOf("days_ago" to daysAgo, "due_today_count" to due.size))
        }
        if (isPreview) return

        togglingIds = togglingIds + habit.habitId
        val ok: Unit? = Mutation.run(invalidate = listOf(DataKey.habits, DataKey.home), successMessage = null) {
            HabitAPI.setChecked(habitId = habit.habitId, day = d, checked = target)
        }
        togglingIds = togglingIds - habit.habitId
        val failed = Mutation.lastErrorStatus != null
        track("habit.check_result", GD.result() + mapOf("habit_id" to habit.habitId, "check_action" to action))
        if ((ok == null || failed) && home?.date == before.date) {
            // 실패: 그 습관만 원래대로
            before.habits.firstOrNull { it.habitId == habit.habitId }?.let { replace(it) }
        }
    }

    private fun apply(habitId: Int, checked: Boolean) {
        val h = home ?: return
        val i = h.habits.indexOfFirst { it.habitId == habitId }
        if (i < 0) return
        val x = h.habits[i].copy(checked = checked)
        if (checked) {
            x.state = HabitDayState.DONE
        } else if (x.scheduled) {
            x.state = if (h.date == KST.today()) HabitDayState.TODO else HabitDayState.MISSED
        }
        val last = x.recent7.lastIndex
        if (last >= 0 && x.recent7[last].date == h.date) {
            x.recent7 = x.recent7.mapIndexed { j, r -> if (j == last) r.copy(state = x.state) else r }
        }
        val list = h.habits.toMutableList().also { it[i] = x }
        phase = LoadPhase.Loaded(
            h.copy(habits = list, doneCount = list.count { it.isActive && it.scheduled && it.checked }),
        )
    }

    private fun replace(habit: Habit) {
        val h = home ?: return
        val i = h.habits.indexOfFirst { it.habitId == habit.habitId }
        if (i < 0) return
        val list = h.habits.toMutableList().also { it[i] = habit }
        phase = LoadPhase.Loaded(
            h.copy(habits = list, doneCount = list.count { it.isActive && it.scheduled && it.checked }),
        )
    }

    // MARK: 추천 습관 바로 추가

    suspend fun add(s: HabitSuggestion) {
        if (addingSuggestion || isPreview) return
        addingSuggestion = true
        val input = HabitInput(name = s.name, emoji = s.emoji, repeatDays = s.repeatDays, remindTime = null)
        try {
            Mutation.run(
                invalidate = listOf(DataKey.habits, DataKey.home, DataKey.timetable),
                successMessage = "'${s.name}' 습관을 추가했어요.",
            ) {
                HabitAPI.create(input)
            }
            track("habit.result", GD.result() + mapOf("habit_action" to "create"))
        } finally {
            addingSuggestion = false
        }
    }

    companion object {
        // MARK: 미리보기

        fun preview(empty: Boolean = false): HabitModel {
            val m = HabitModel()
            m.isPreview = true
            val home = if (empty) HabitPreviewData.emptyHome else HabitPreviewData.home
            m.phase = LoadPhase.Loaded(home)
            if (!empty) {
                val hm = HabitPreviewData.heatmap
                m.heatmap = hm
                m.heatmapFrom = hm.from
            }
            return m
        }
    }
}

// MARK: - 미리보기 샘플 (네트워크 없이)

object HabitPreviewData {
    private val today: String get() = KST.today()

    private fun recent(states: List<HabitDayState>): List<HabitRecentDay> {
        val n = states.size
        return states.mapIndexed { i, s -> HabitRecentDay(date = KST.addDays(today, i - (n - 1)), state = s) }
    }

    val habits: List<Habit>
        get() = listOf(
            Habit(
                habitId = 1, name = "영양제 먹기", emoji = "💊", repeatDays = emptyList(), daily = true, remindTime = "08:30",
                status = HabitStatus.ACTIVE, startDate = KST.addDays(today, -75), sortOrder = 0, scheduled = true, checked = true,
                state = HabitDayState.DONE, streak = 12, rate30 = 90,
                recent7 = recent(listOf(HabitDayState.DONE, HabitDayState.DONE, HabitDayState.MISSED, HabitDayState.DONE, HabitDayState.DONE, HabitDayState.DONE, HabitDayState.DONE)),
            ),
            Habit(
                habitId = 2, name = "물 2L 마시기", emoji = "💧", repeatDays = emptyList(), daily = true, remindTime = null,
                status = HabitStatus.ACTIVE, startDate = KST.addDays(today, -40), sortOrder = 1, scheduled = true, checked = false,
                state = HabitDayState.TODO, streak = 3, rate30 = 70,
                recent7 = recent(listOf(HabitDayState.MISSED, HabitDayState.DONE, HabitDayState.DONE, HabitDayState.MISSED, HabitDayState.DONE, HabitDayState.DONE, HabitDayState.TODO)),
            ),
            Habit(
                habitId = 3, name = "분리수거", emoji = "🗑️", repeatDays = listOf(JavaDayOfWeek.WEDNESDAY, JavaDayOfWeek.SUNDAY), daily = false, remindTime = "21:00",
                status = HabitStatus.ACTIVE, startDate = KST.addDays(today, -20), sortOrder = 2, scheduled = false, checked = false,
                state = HabitDayState.OFF, streak = 2, rate30 = 100,
                recent7 = recent(listOf(HabitDayState.OFF, HabitDayState.DONE, HabitDayState.OFF, HabitDayState.OFF, HabitDayState.OFF, HabitDayState.DONE, HabitDayState.OFF)),
            ),
            Habit(
                habitId = 4, name = "일기 쓰기", emoji = "📓", repeatDays = emptyList(), daily = true, remindTime = null,
                status = HabitStatus.PAUSED, startDate = KST.addDays(today, -50), sortOrder = 3, scheduled = false, checked = false,
                state = HabitDayState.PAUSED, streak = 0, rate30 = 40,
                recent7 = recent(listOf(HabitDayState.DONE, HabitDayState.MISSED, HabitDayState.PAUSED, HabitDayState.PAUSED, HabitDayState.PAUSED, HabitDayState.PAUSED, HabitDayState.PAUSED)),
            ),
        )

    val home: HabitHome
        get() {
            val hs = habits
            val due = hs.filter { it.isActive && it.scheduled }
            return HabitHome(
                goalCategoryId = 9, goalName = "기본 생활 습관", emoji = "🌱", date = today, today = today,
                scheduledCount = due.size, doneCount = due.count { it.checked }, habits = hs,
            )
        }

    val emptyHome: HabitHome
        get() = HabitHome(
            goalCategoryId = 9, goalName = "기본 생활 습관", emoji = "🌱", date = today, today = today,
            scheduledCount = 0, doneCount = 0, habits = emptyList(),
        )

    /** 가장 먼저 시작한 습관의 그 달 1일부터 오늘까지 */
    val heatmap: HabitHeatmap
        get() {
            val hs = habits
            val t = today
            val earliest = hs.map { it.startDate }.minOrNull() ?: t
            val p = KST.parts(earliest)
            val from = KST.ymd(year = p.y, month = p.m, day = 1)
            val count = KST.diffDays(from, t) + 1
            val rows = mutableListOf<HabitHeatmap.Row>()
            for (h in hs) {
                val cells = mutableListOf<HabitDayState>()
                for (i in 0 until maxOf(count, 0)) {
                    val d = KST.addDays(from, i)
                    when {
                        d < h.startDate -> cells.add(HabitDayState.BEFORE_START)
                        d == t -> cells.add(
                            if (h.checked) HabitDayState.DONE
                            else if (h.scheduled) HabitDayState.TODO
                            else if (h.isPaused) HabitDayState.PAUSED
                            else HabitDayState.OFF,
                        )
                        h.isPaused && KST.diffDays(d, t) <= 5 -> cells.add(HabitDayState.PAUSED)
                        !h.daily && !h.repeatDays.contains(JavaDayOfWeek.of(d)) -> cells.add(HabitDayState.OFF)
                        else -> cells.add(if ((i * 7 + h.habitId * 3) % 5 == 0) HabitDayState.MISSED else HabitDayState.DONE)
                    }
                }
                rows.add(
                    HabitHeatmap.Row(
                        habitId = h.habitId, name = h.name, emoji = h.emoji, status = h.status,
                        streak = h.streak, rate = h.rate30,
                        doneDays = cells.count { it == HabitDayState.DONE }, cells = cells,
                    ),
                )
            }
            return HabitHeatmap(from = from, to = t, today = t, days = emptyList(), habits = rows)
        }

    /** 기본 생활 습관 목표 (goalMode = HABIT) */
    val goal: GoalCategory
        get() = GoalCategory(
            goalCategoryId = 9,
            goalCategoryName = "기본 생활 습관",
            goalCategoryStatus = GoalStatus.IN_PROGRESS,
            colorCode = "12B76A",
            emoji = "🌱",
            goalMode = GoalMode.HABIT,
            generalCategories = emptyList(),
        )

    /** 일회성 할 일 (병원 가기 · 친구 만나기) */
    val tasks: GoalTasksResponse
        get() = GoalTasksResponse(
            totalCount = 2, todoCount = 1, completedCount = 1,
            tasks = listOf(
                TimeBlockTask(
                    taskId = 901, taskName = "치과 가기", emoji = "🦷", taskStatus = TaskStatus.TODO,
                    sourceType = TaskSourceType.MANUAL, goalCategoryId = 9, goalColorCode = "12B76A",
                    date = KST.addDays(today, 2),
                ),
                TimeBlockTask(
                    taskId = 902, taskName = "친구 만나기", taskStatus = TaskStatus.COMPLETED,
                    sourceType = TaskSourceType.MANUAL, goalCategoryId = 9, goalColorCode = "12B76A",
                    date = KST.addDays(today, -3),
                ),
            ),
        )
}
