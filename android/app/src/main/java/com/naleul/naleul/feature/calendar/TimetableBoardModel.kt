// feature/calendar/TimetableBoardModel.kt
//
// iOS Features/Calendar/TimetableBoardModel.swift 를 옮긴 파일.
// TimeTable 위에서 하는 모든 조작(상세 시트 · 완료 · 시간 변경 · 삭제 · 드래그 · AI 배치 미리보기 · 이월 제안)을
// 한곳에 모은 화면 상태. 캘린더(주간 · 일간 · 월간)와 Task 추가 결과 미리보기가 같은 동작을 쓰도록 분리했어요.
// 웹 원본: features/timetable/ui/useTimetableInteractions.tsx (+ CalendarView.tsx 의 useTimetable · useActivities 조회)
//
// 사용:
//   val board = rememberScreenModel { TimetableBoardModel() }
//   ReloadOn(listOf(DataKey.timetable, DataKey.activities), id = rangeKey) { board.setRange(s, e); board.load(withActivities = true) }
//   TimetableSheets(board)              // 블록을 누르면 뜨는 시트 (iOS .timetableSheets(board))
//   TimetableBottomBars(board = board)  // 범위 고르기 · AI 배치 확인 · 이월 제안 바
//
// Swift 와 다른 점
//  - iOS 는 Task { } 로 저장 요청을 보냈어요. 여기서는 scope.launch { } (화면이 사라지면 취소).
//    단, "범위를 고르지 않고 떠나면 이날만으로 저장"은 화면이 사라져도 끝까지 보내야 해서 따로 오래 사는 범위(persistScope)로 보내요.
//  - 진동(햅틱)은 화면(격자) 쪽에서 해요 (모델은 화면 정보를 몰라서).
//  - Mutation.run 결과가 null 이어도 "성공" 일 수 있어서(응답 본문이 없는 경우) 성공 여부는 따로 표시해요.

package com.naleul.naleul.feature.calendar

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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.roundToInt

class TimetableBoardModel : ScreenModel() {
    // MARK: 데이터 (서버에서 받은 그대로)

    var rawDays by mutableStateOf<List<TimetableDay>>(emptyList())
        private set
    var activities by mutableStateOf<List<ActualActivity>>(emptyList())
        private set
    var rangeStart by mutableStateOf("")
        private set
    var rangeEnd by mutableStateOf("")
        private set
    /** 다시 불러오는 중 (처음 불러오기 포함) */
    var isFetching by mutableStateOf(false)
        private set
    var loadedOnce by mutableStateOf(false)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    /** 불러오기 실패의 HTTP 상태 (calendar.load_fail 의 error_status · 0 = 네트워크) */
    var loadErrorStatus by mutableStateOf<Int?>(null)
        private set
    private var fetchSeq = 0
    /** 미리보기(@Preview)용 — 네트워크를 쓰지 않아요 */
    private var offline = false

    /** 이벤트 출처 — home 이면 home.calendar_* 이벤트를 보내요 (캘린더 화면 이벤트는 캘린더 분류에서 붙여요) */
    var analyticsSurface = "calendar"
    private val isHome: Boolean get() = analyticsSurface == "home" && !offline

    private fun homeTrack(event: String, vararg props: Pair<String, Any?>) {
        if (isHome) HomeTracker.track(event, *props)
    }

    private fun dragSourceName(src: TimetableDragSource) = when (src) {
        is TimetableDragSource.task -> "task"
        is TimetableDragSource.fixed -> "fixed"
        is TimetableDragSource.actual -> "actual"
    }

    private fun dragModeName(m: TimetableDragMode) = when (m) {
        TimetableDragMode.move -> "move"
        TimetableDragMode.resize -> "resize"
        TimetableDragMode.resizeTop -> "resize_top"
    }

    // MARK: - 이벤트 (캘린더 분류 — 웹 useTimetableInteractions 와 같은 이름)

    /** 시트 · 아래 바 이벤트 (홈 · 캘린더 · Task 추가 결과 모두) — surface 를 붙여요 */
    fun sheetTrack(event: String, props: EventProps = emptyMap()) {
        if (offline) return
        CAL.track(event, mapOf("surface" to analyticsSurface) + props)
    }

    /** 격자 클릭 · 끌기 (timetable.*) — 홈은 home.calendar_* 를 따로 보내서 여기선 안 보내요 */
    fun gridTrack(event: String, props: EventProps = emptyMap()) {
        if (analyticsSurface == "home") return
        sheetTrack(event, props)
    }

    /** 캘린더 격자 보기 (일간 1일 · 3일 · 주간 7일). 캘린더가 아니면 null */
    val gridViewMode: String?
        get() = if (analyticsSurface != "calendar" || rangeStart.isEmpty() || rangeEnd.isEmpty()) null else when (KST.diffDays(rangeStart, rangeEnd)) {
            0 -> "day"
            2 -> "three_day"
            else -> "week"
        }

    /** drag_source — proposal(점선 제안) · group(묶음) · task · fixed · actual */
    private fun gridDragSource(src: TimetableDragSource): String = when (src) {
        is TimetableDragSource.task -> if (src.task.proposed == true) "proposal" else if (src.group.isEmpty()) "task" else "group"
        is TimetableDragSource.fixed -> "fixed"
        is TimetableDragSource.actual -> "actual"
    }

    /** drag_mode — move · resize_start(위 끝) · resize_end(아래 끝) */
    private fun gridDragMode(m: TimetableDragMode): String = when (m) {
        TimetableDragMode.move -> "move"
        TimetableDragMode.resize -> "resize_end"
        TimetableDragMode.resizeTop -> "resize_start"
    }

    /** 놓을 수 없던 이유 문구 → cancel_reason (validate 문구와 맞춰요) */
    private fun invalidReason(message: String): String = when {
        message.contains("겹쳐 있어요") -> "crowded"
        message.contains("기상") || message.contains("취침") -> "sleep_rule"
        message.contains("나눠서") -> "actual_overlap"
        else -> "invalid"
    }

    /** scope_target — routine · fixed · sleep_wake · sleep_bed */
    private fun scopeTarget(a: TimetableScopeAsk): String = when (val k = a.kind) {
        is TimetableScopeAskKind.routine -> "routine"
        is TimetableScopeAskKind.fixed -> when (k.edge) {
            TimetableLayout.SleepEdge.wake -> "sleep_wake"
            TimetableLayout.SleepEdge.bed -> "sleep_bed"
            else -> "fixed"
        }
    }

    /** '어디까지 바꿀까요?' 바가 뜰 때 */
    private fun showAsk(a: TimetableScopeAsk) {
        ask = a
        sheetTrack(
            "scope_chooser.view",
            mapOf(
                "scope_target" to scopeTarget(a),
                "needs_scope" to a.options.any { it.scope != ChangeScope.DAY && it.disabledReason == null },
            ),
        )
    }

    /** 범위를 고르지 않은 채 다른 동작을 해서 "이날만"으로 저장 (other_block_tap · next_drag · leave_page) */
    private fun autoDay(reason: String) {
        val a = ask ?: return
        sheetTrack("scope_chooser.auto_day", mapOf("scope_target" to scopeTarget(a), "auto_reason" to reason))
        chooseScope(ChangeScope.DAY)
    }

    /** 아래 바에서 범위를 직접 고름 */
    fun chooseScopeByUser(scope: ChangeScope) {
        val a = ask ?: return
        sheetTrack(
            "scope_chooser.choose",
            mapOf("scope_target" to scopeTarget(a), "change_scope" to scope.rawValue, "submit_method" to "button"),
        )
        chooseScope(scope)
    }

    /** 아래 바 X — 원래 자리로 */
    fun cancelAskByUser() {
        ask?.let { sheetTrack("scope_chooser.cancel", mapOf("scope_target" to scopeTarget(it), "close_method" to "x_button")) }
        cancelAsk()
    }

    /** 묶음 바 '묶음 풀기' */
    fun clearGroupByUser() {
        gridTrack("timetable.group_clear", mapOf("group_size" to groupIds.size, "close_method" to "cancel_button"))
        clearGroup()
    }

    /** 상세 시트에서 그 자리 고치기 저장 결과 (task_detail.field_result) */
    fun trackFieldResult(field: String, ok: Boolean) {
        sheetTrack("task_detail.field_result", GD.result(ok) + mapOf("edit_field" to field))
    }

    // MARK: 화면 상태

    /** 지금 떠 있는 시트 (블록 상세 · 고정 시간 · 실제로 한 일 · 시간 입력) */
    var sheet by mutableStateOf<TimetableSheet?>(null)

    /** 불러온 뒤 열 Task 상세 (알림에서 들어올 때) */
    private var openAfterLoadId: Int? = null

    /** 알림에서 들어오면 — 이미 불러온 기간에 있으면 바로, 아니면 다음 불러오기 뒤에 상세 시트를 열어요 */
    fun openTaskWhenLoaded(taskId: Int) {
        openAfterLoadId = taskId
        openPendingTask()
    }

    private fun openPendingTask() {
        val id = openAfterLoadId ?: return
        for (d in rawDays) {
            val t = d.tasks.firstOrNull { it.taskId == id } ?: continue
            openAfterLoadId = null
            sheet = TimetableSheet.task(t)
            return
        }
    }
    /** "삭제할까요?"를 띄운 Task (상세 시트 안에서 확인해요) */
    var confirmDeleteId by mutableStateOf<Int?>(null)
    /** 드래그로 옮긴 뒤 서버 응답을 기다리는 동안 새 위치에 그려 둘 블록들 */
    var pending by mutableStateOf<List<TimetablePendingEntry>>(emptyList())
        private set
    /** 저장은 끝났고 다시 불러오기를 기다리는 임시 표시 (다 불러오면 지워요 — 먼저 지우면 원래 자리가 잠깐 보여서) */
    private var settling: MutableSet<String> = mutableSetOf()
    /** 드래그 직후 "어디까지 바꿀까요?" */
    var ask by mutableStateOf<TimetableScopeAsk?>(null)
        private set
    /** 묶음 드래그로 같이 옮길 Task id ("이어진 N개 묶어 옮기기") */
    var group by mutableStateOf<List<Int>>(emptyList())
        private set
    /** "AI로 배치하기" 미리보기 제안 (저장 전 점선 블록). null 이면 미리보기 중 아님 */
    var proposals by mutableStateOf<List<TimetableLayout.Proposal>?>(null)
        private set
    var proposalUnscheduled by mutableStateOf(0)
        private set
    var proposalKind by mutableStateOf(TimetableProposalKind.fill)
        private set
    private var resolveHandler: ((Boolean, List<TimetableLayout.Proposal>) -> Unit)? = null
    /** 끄는 중인 블록 */
    var drag by mutableStateOf<TimetableDragState?>(null)
        private set
    /** 길게 눌러 "들어 올린" 블록 (아직 움직이기 전) — 이 블록은 커지고 나머지는 흐려져요 */
    var liftedOwnerId by mutableStateOf<String?>(null)
        private set
    /** 자정을 넘기는 고정 시간 수정 때만 필요 → 고정 시간 시트를 열 때 불러와요 */
    private var lifePatterns: List<LifePattern>? = null

    // MARK: 진행 중 표시

    var togglingId by mutableStateOf<Int?>(null)
        private set
    var busyTask by mutableStateOf(false)
        private set
    var deleting by mutableStateOf(false)
        private set
    var busyFixed by mutableStateOf(false)
        private set
    var editSaving by mutableStateOf(false)
        private set
    var fillPreviewing by mutableStateOf(false)
        private set
    var fillApplying by mutableStateOf(false)
        private set
    var carryBusy by mutableStateOf<TimetableCarryAction?>(null)
        private set

    val isOffline: Boolean get() = offline

    // MARK: - 불러오기

    /** 보여줄 기간을 바꿔요 (바로 바꿔야 이전 기간 블록이 새 기간에 섞여 보이지 않아요) */
    fun setRange(start: String, end: String) {
        if (rangeStart != start) rangeStart = start
        if (rangeEnd != end) rangeEnd = end
    }

    suspend fun load(withActivities: Boolean) {
        if (offline) return
        val s = rangeStart
        val e = rangeEnd
        fetchSeq += 1
        val seq = fetchSeq
        isFetching = true
        try {
            val res = TimetableAPI.timetable(start = s, end = e)
            var acts: List<ActualActivity> = emptyList()
            if (withActivities) {
                // 실제 기록을 못 받아도 TimeTable 은 보여줘요
                acts = try {
                    TimetableAPI.activities(start = s, end = e)
                } catch (c: CancellationException) {
                    throw c
                } catch (_: Exception) {
                    emptyList()
                }
            }
            // 그사이 기간이 바뀌었으면(주 이동 등) 버려요
            if (s == rangeStart && e == rangeEnd) {
                rawDays = res.days
                activities = acts
                loadError = null
                loadedOnce = true
                finishSettling()
                openPendingTask()
            }
        } catch (c: CancellationException) {
            // 다음 불러오기가 이어받아요
            if (seq == fetchSeq) isFetching = false
            throw c
        } catch (err: Exception) {
            if (s == rangeStart && e == rangeEnd) {
                loadError = errorMessage(err)
                loadErrorStatus = CAL.status(err)
                finishSettling()
            }
        }
        if (seq == fetchSeq) isFetching = false
    }

    /** 지금 보는 기간의 서버 데이터 (임시 표시 없음) */
    val rangeDays: List<TimetableDay> by derivedStateOf {
        val s = rangeStart
        val e = rangeEnd
        rawDays.filter { it.date >= s && it.date <= e }
    }

    /** 화면에 그릴 날짜들: 서버 데이터 + 드래그 임시 위치 + AI 제안(점선) */
    val days: List<TimetableDay> by derivedStateOf {
        TimetableLayout.applyProposals(TimetableLayout.applyPending(rangeDays, pending.map { it.value }), proposals)
    }

    /** 묶음 중 지금도 옮길 수 있는 Task 만 (완료했거나 시간 미정이 된 Task 는 빠져요) */
    val groupIds: Set<Int> by derivedStateOf {
        val g = group
        if (g.isEmpty()) {
            emptySet()
        } else {
            val live = mutableSetOf<Int>()
            for (d in days) {
                for (t in d.tasks) if (TimetableRules.groupable(t)) live.add(t.taskId)
            }
            g.filter { live.contains(it) }.toSet()
        }
    }

    /** 22시 이월 제안 (아직 확인 안 한 것) — 오늘 이후, 시간순 */
    val carryProposals: List<TimeBlockTask> by derivedStateOf {
        val today = KST.today()
        val seen = mutableSetOf<Int>()
        val out = mutableListOf<TimeBlockTask>()
        for (d in days) {
            for (t in d.tasks + d.unscheduledTasks) {
                if (t.carryPending != true || t.taskStatus != TaskStatus.TODO || seen.contains(t.taskId)) continue
                seen.add(t.taskId)
                val day = TimetableRules.blockDay(t) ?: t.date ?: today
                if (day >= today) out.add(t)
            }
        }
        out.sortedBy { it.plannedStartAt ?: "${it.date ?: today}T99" }
    }

    val proposing: Boolean get() = proposals != null

    /** 확인을 기다리는 블록 — AI 배치 미리보기면 제안들(나머지는 흐리게), 아니면 화면 아래에서 묻고 있는 이월 제안 하나 */
    val focusIds: Set<Int>
        get() {
            val p = proposals
            if (p != null) return p.map { it.taskId }.toSet()
            val c = carryProposals.firstOrNull()
            if (ask == null && groupIds.isEmpty() && c != null) return setOf(c.taskId)
            return emptySet()
        }

    /** 사용자가 끌어서 고친 제안 수 */
    val editedProposals: Int
        get() = (proposals ?: emptyList()).count { it.start != it.proposedStart || it.end != it.proposedEnd }

    /** 데이터가 새로 오면 시트 안의 Task 도 최신 값으로 */
    fun liveTask(t: TimeBlockTask): TimeBlockTask {
        for (d in days) {
            d.tasks.firstOrNull { it.taskId == t.taskId }?.let { return it }
            d.unscheduledTasks.firstOrNull { it.taskId == t.taskId }?.let { return it }
        }
        return t
    }

    // MARK: 선택 표시

    val selectedTaskId: Int? get() = (sheet as? TimetableSheet.task)?.task?.taskId

    val selectedFixedKey: String? get() = (sheet as? TimetableSheet.fixed)?.block?.key

    val selectedActivityId: Int? get() = (sheet as? TimetableSheet.activity)?.draft?.activity?.activityId

    // MARK: - 시트 열기 · 닫기

    fun close() {
        sheet = null
        confirmDeleteId = null
    }

    /** 시트를 아래로 끌어 닫았을 때 */
    fun sheetDismissed() {
        confirmDeleteId = null
    }

    /**
     * 스펙 13.1: 격자 Task 블록 누르기. 묶음 고르기 중이면 묶음에 넣기 · 빼기만 하고 (묶을 수 없는 블록은 아무 일도 없음)
     * 상세 시트는 절대 열지 않아요. 묶음이 풀린 뒤엔 다시 상세 시트.
     */
    fun tapTaskBlock(t: TimeBlockTask) {
        if (groupIds.isNotEmpty()) {
            if (TimetableRules.groupable(t)) toggleGroupMember(t.taskId, method = "touch_tap")
            return
        }
        selectTask(t)
    }

    /** trackGrid: 격자 블록을 눌렀을 때만 true (홈 할 일 줄은 home.task_row_click 을 따로 보내요) */
    fun selectTask(t: TimeBlockTask, trackGrid: Boolean = true) {
        // 스펙 13.1: 묶음 고르기 중엔 상세 시트가 먼저 뜨지 않게
        if (groupIds.isNotEmpty()) return
        if (trackGrid) {
            homeTrack(
                HomeEvent.calendarTaskBlockClick,
                "view_date" to rangeStart, "task_id" to t.taskId, "source_type" to t.sourceType.rawValue,
                "task_status" to t.taskStatus.rawValue, "is_scheduled" to (t.plannedStartAt != null),
                "goal_category_id" to t.goalCategoryId,
            )
            gridTrack(
                "timetable.task_block_click",
                mapOf(
                    "view_mode" to gridViewMode, "task_id" to t.taskId, "source_type" to t.sourceType.rawValue,
                    "task_status" to t.taskStatus.rawValue, "is_scheduled" to (t.plannedStartAt != null),
                    "is_locked" to t.locked, "drag_source" to if (t.proposed == true) "proposal" else "task",
                ),
            )
        }
        // 범위를 고르지 않고 다른 블록을 누르면 앞의 변경은 "이날만"으로 저장해요 (웹: 바깥 클릭)
        if (ask != null) autoDay("other_block_tap")
        confirmDeleteId = null
        sheet = TimetableSheet.task(t)
    }

    fun selectFixed(b: FixedBlock) {
        homeTrack(HomeEvent.calendarFixedBlockClick, "view_date" to rangeStart, "pattern_type" to b.patternType.rawValue)
        gridTrack("timetable.fixed_block_click", mapOf("pattern_type" to b.patternType.rawValue, "is_overridden" to b.overridden))
        if (ask != null) autoDay("other_block_tap")
        sheet = TimetableSheet.fixed(b)
        scope.launch { ensurePatterns() }
    }

    fun selectActivity(a: ActualActivity) {
        homeTrack(HomeEvent.calendarActivityBlockClick, "view_date" to rangeStart)
        gridTrack("timetable.activity_block_click", mapOf("days_ago" to CAL.daysAgo(a.startAt)))
        if (ask != null) autoDay("other_block_tap")
        confirmDeleteId = null
        sheet = TimetableSheet.activity(TimetableActivityDraft.of(a))
    }

    /** 새 기록 열기 (끝은 지금을 넘지 않게) */
    fun openActivity(draft: TimetableActivityDraft) {
        val now = KST.now()
        val nowMin = if (draft.date == now.date) now.minutes else Int.MAX_VALUE
        val end = minOf(draft.end, nowMin)
        val start = minOf(draft.start, end - 10)
        confirmDeleteId = null
        sheet = TimetableSheet.activity(draft.copy(start = maxOf(start, 0), end = end))
    }

    /** 빈 칸을 누름 → 캘린더는 + 메뉴를 띄워요 (스펙 11.4) — 같은 empty_slot_click 이벤트를 보내요 */
    fun emptyTapForMenu(date: String, minutes: Int) {
        gridTrack(
            "timetable.empty_slot_click",
            mapOf("view_date" to date, "date_relation" to CAL.relation(date), "slot_minutes" to minutes),
        )
        if (ask != null) autoDay("other_block_tap")
    }

    /** 빈 칸을 누름 → 그 시각부터 1시간 실제 기록 (홈 격자) */
    fun emptyClick(date: String, minutes: Int) {
        homeTrack(
            HomeEvent.calendarEmptySlotClick,
            "view_date" to date, "date_relation" to HomeTracker.relation(date), "slot_minutes" to minutes,
        )
        gridTrack(
            "timetable.empty_slot_click",
            mapOf("view_date" to date, "date_relation" to CAL.relation(date), "slot_minutes" to minutes),
        )
        if (ask != null) autoDay("other_block_tap")
        openActivity(TimetableActivityDraft(activity = null, date = date, start = minutes, end = minutes + 60, replaced = null))
    }

    private suspend fun ensurePatterns() {
        if (offline || lifePatterns != null) return
        lifePatterns = try {
            TimetableAPI.lifePatterns()
        } catch (c: CancellationException) {
            throw c
        } catch (_: Exception) {
            null
        }
    }

    // MARK: - Task 작업

    /** 계획했던 시간에 다른 일을 했어요 → 그 시간으로 실제 기록 (계획 Task 연결) */
    fun recordInstead(t: TimeBlockTask) {
        val ps = t.plannedStartAt ?: return
        val day = ps.take(10)
        val s = KST.minutesFrom(day, ps)
        val e = t.plannedEndAt?.let { KST.minutesFrom(day, it) } ?: (s + (t.plannedDurationMinutes ?: 30))
        openActivity(
            TimetableActivityDraft(
                activity = null, date = day, start = s, end = e,
                replaced = TimetableReplacedTask(id = t.taskId, name = t.taskName),
            )
        )
    }

    /** "계획과 다른 시간에 · 나눠서 했어요" — 실제 시각 입력 창 (이미 끝낸 Task 는 남긴 실제 시간부터 고쳐요) */
    fun completeWithTime(t: TimeBlockTask) {
        val ps = t.plannedStartAt ?: return
        val aS = t.actualStartAt
        val aE = t.actualEndAt
        val hasActual = t.taskStatus == TaskStatus.COMPLETED && aS != null && aE != null
        // 실제로 다른 날 했으면 그 날 기준
        val day = (if (hasActual) aS!! else ps).take(10)
        val s = KST.minutesFrom(day, if (hasActual) aS!! else ps)
        val e = if (hasActual) {
            KST.minutesFrom(day, aE!!)
        } else {
            t.plannedEndAt?.let { KST.minutesFrom(day, it) } ?: (s + (t.plannedDurationMinutes ?: 30))
        }
        confirmDeleteId = null
        sheet = TimetableSheet.timeEdit(
            TimetableTimeEditing(
                target = TimetableTimeEditTarget.actual(t),
                initial = TimetableTimeValue(date = day, start = s, end = maxOf(e, s + 10)),
            )
        )
    }

    suspend fun toggleComplete(t: TimeBlockTask, closeOnSuccess: Boolean) {
        if (togglingId != null) return
        togglingId = t.taskId
        val msg = if (t.taskStatus == TaskStatus.COMPLETED) "완료를 취소했어요." else "완료했어요."
        val ok = Mutation.run(invalidate = listOf(DataKey.timetable, DataKey.goals), successMessage = msg) {
            TimetableAPI.toggleComplete(taskId = t.taskId, currentStatus = t.taskStatus)
        }
        val res = GD.result()
        togglingId = null
        // 상세 시트에서 누른 것만 (목록 · 월간 체크는 각자 이벤트)
        if (closeOnSuccess) {
            sheetTrack(
                "task_detail.action_result",
                res + mapOf("task_action" to if (t.taskStatus == TaskStatus.COMPLETED) "uncomplete" else "complete"),
            )
        }
        if (ok != null && closeOnSuccess) close()
    }

    suspend fun unlock(t: TimeBlockTask) {
        busyTask = true
        val ok = Mutation.run(
            invalidate = listOf(DataKey.timetable),
            successMessage = "AI 자동 배치에 맡겼어요. 다음 자동 배치 때 빈 시간으로 옮겨질 수 있어요.",
        ) {
            TimetableAPI.unlockTask(taskId = t.taskId)
        }
        sheetTrack("task_detail.action_result", GD.result() + mapOf("task_action" to "unlock"))
        busyTask = false
        if (ok != null) close()
    }

    suspend fun unschedule(t: TimeBlockTask) {
        busyTask = true
        val ok = unscheduleRequest(t.taskId)
        sheetTrack("task_detail.action_result", GD.result(ok) + mapOf("task_action" to "unschedule"))
        busyTask = false
        if (ok) close()
    }

    /** 상세 시트 "다른 날로" (내일 · 모레 · 날짜 지정) — 시간이 있으면 같은 시각으로 */
    suspend fun moveToDate(t: TimeBlockTask, date: String, option: String) {
        busyTask = true
        val moved = moveDateRequest(t.taskId, date = date, keepTime = true)
        sheetTrack("task_detail.action_result", GD.result(moved != null) + mapOf(
            "task_action" to "move_date", "move_option" to option, "to_date" to date,
            "kept_time" to (moved?.plannedStartAt != null),
        ))
        busyTask = false
        if (moved != null) close()
    }

    private suspend fun unscheduleRequest(taskId: Int): Boolean {
        val ok = Mutation.run(invalidate = listOf(DataKey.timetable, DataKey.goals), successMessage = "시간 미정으로 옮겼어요.") {
            TimetableAPI.unscheduleTask(taskId = taskId)
        }
        return ok != null
    }

    /**
     * 다른 날로 옮기기 (2026-10) — 상세 시트 "다른 날로" · 시간 미정 칸끼리 끌기.
     * @return 옮긴 Task (실패면 null)
     */
    suspend fun moveDateRequest(taskId: Int, date: String, keepTime: Boolean): TimeBlockTask? {
        var moved: TimeBlockTask? = null
        Mutation.run<TimeBlockTask?>(
            invalidate = listOf(DataKey.timetable, DataKey.goals),
            success = { r ->
                val label = KST.formatMonthDay(date)
                if (r?.plannedStartAt != null) "${label}로 옮겼어요." else "${label} 시간 미정으로 옮겼어요."
            },
        ) {
            val r = TimetableAPI.moveTaskDate(taskId = taskId, date = date, keepTime = keepTime)
            moved = r
            r
        }
        return moved
    }

    /** 삭제는 바로 지우지 않고 상세 시트 안에 "삭제할까요?"를 띄워요 */
    fun askDelete(t: TimeBlockTask) {
        confirmDeleteId = t.taskId
    }

    fun cancelDelete() {
        confirmDeleteId = null
    }

    suspend fun confirmDelete() {
        val id = confirmDeleteId ?: return
        if (deleting) return
        deleting = true
        val ok = Mutation.run(invalidate = listOf(DataKey.timetable, DataKey.goals), successMessage = "삭제했어요.") {
            TimetableAPI.deleteTask(taskId = id)
        }
        sheetTrack("task_detail.action_result", GD.result() + mapOf("task_action" to "delete"))
        deleting = false
        if (ok != null) close()
    }

    /** 앞뒤로 바로 이어진 Task 개수 (자기 포함) */
    fun chainSize(t: TimeBlockTask): Int = TimetableRules.chainOf(t, days).size

    /** 이어진 Task 들을 묶음으로 선택 → 하나를 끌면 같이 움직여요 */
    fun selectChain(t: TimeBlockTask) {
        group = TimetableRules.chainOf(t, days).map { it.taskId }
        close()
    }

    fun clearGroup() {
        group = emptyList()
    }

    /**
     * 묶음 고르기 (스펙 10.5): 이 Task 를 묶음에 넣거나 빼요.
     * method = "long_press"(2초 꾹 — 넣기만) · "touch_tap"(고르기 중 탭) → 웹과 같은 timetable.group_select
     */
    fun toggleGroupMember(taskId: Int, method: String) {
        val all = days.flatMap { it.tasks }
        val task = all.firstOrNull { it.taskId == taskId } ?: return
        // 시간이 정해져 있고 옮길 수 있는 Task 만
        if (!TimetableRules.groupable(task)) return
        // 묶음은 같은 날 Task 끼리만 — 다른 날 Task 를 고르면 그 Task 하나로 새로 시작해요 (웹과 같이)
        val date = TimetableRules.blockDay(task)
        val base = groupIds.filter { id -> all.firstOrNull { it.taskId == id }?.let { TimetableRules.blockDay(it) } == date }
        val removing = base.contains(taskId)
        if (removing && method == "long_press") return
        val next = if (removing) base - taskId else base + taskId
        val nextSize = next.size
        val action = if (removing) "remove" else "add"
        gridTrack(
            "timetable.group_select",
            mapOf("task_id" to taskId, "group_action" to action, "group_size" to nextSize, "select_method" to method),
        )
        homeTrack(HomeEvent.calendarTaskGroupSelect, "task_id" to taskId, "group_action" to action, "group_size" to nextSize)
        group = next
    }

    /** 묶음 고르기 중 빈 곳 탭 → 묶음 풀기 (웹 close_method: empty_tap) */
    fun clearGroupByEmptyTap() {
        gridTrack("timetable.group_clear", mapOf("group_size" to groupIds.size, "close_method" to "empty_tap"))
        clearGroup()
    }

    /** 상세 시트에서 이름 · 세부 목표 · 마일스톤 · 마감일 고치기 (웹 useUpdateGoalTask) */
    /** 상세 시트에서 업무형 목표의 세부 목표(로드맵 · WorkTopic) 바꾸기 — null = 세부 목표 없음 (웹 saveWorkTopic) */
    suspend fun updateWorkTopic(goalId: Int, taskId: Int, topicId: Int?): Boolean {
        var done = false
        val patch = if (topicId != null) WorkTaskPatch(topicId = topicId) else WorkTaskPatch(clearTopic = true)
        Mutation.run<WorkTask?>(invalidate = WorkAPI.changed, success = { "Task를 수정했어요." }) {
            val r = WorkAPI.updateTask(goalId = goalId, taskId = taskId, patch = patch)
            done = true
            r
        }
        return done
    }

    /** 상세 시트에서 중요도 바꾸기 — 루틴 · 미션은 그날 Task 만 (웹 useChangePriority) */
    suspend fun changePriority(taskId: Int, priority: TaskPriority): Boolean {
        var done = false
        Mutation.run<TimeBlockTask?>(invalidate = listOf(DataKey.timetable)) {
            val r = TimetableAPI.changePriority(taskId = taskId, priority = priority)
            done = true
            r
        }
        return done
    }

    suspend fun updateTaskContent(goalId: Int, taskId: Int, input: GoalTaskInput): Boolean {
        var done = false
        Mutation.run<TimeBlockTask?>(invalidate = listOf(DataKey.goals, DataKey.timetable), success = { "Task를 수정했어요." }) {
            val r = GoalAPI.updateGoalTask(goalId = goalId, taskId = taskId, input = input)
            done = true
            r
        }
        return done
    }

    /** 상세 시트에서 시간 바로 고치기 (루틴이면 범위와 함께) */
    suspend fun saveTaskTime(t: TimeBlockTask, date: String, start: Int, end: Int, scope: ChangeScope): Boolean {
        if (t.sourceType == TaskSourceType.ROUTINE) return saveRoutine(t, date = date, start = start, end = end, scope = scope)
        return reschedule(t, date = date, start = start, end = end)
    }

    // MARK: - 고정 시간 작업

    fun editFixedTime(b: FixedBlock) {
        val patternEnd = lifePatterns?.firstOrNull { it.lifePatternId == b.lifePatternId }?.endTime
        val initial = TimetableTimeValue(
            date = b.targetDate,
            start = KST.minutesFrom(b.targetDate, b.start),
            end = TimetableRules.fullFixedEnd(b, days = days, patternEnd = patternEnd),
        )
        confirmDeleteId = null
        sheet = TimetableSheet.timeEdit(TimetableTimeEditing(target = TimetableTimeEditTarget.fixed(b), initial = initial))
    }

    suspend fun skipFixed(b: FixedBlock) {
        busyFixed = true
        val ok = Mutation.run(invalidate = listOf(DataKey.timetable), successMessage = "이날만 비웠어요.") {
            TimetableAPI.patternOverride(lifePatternId = b.lifePatternId, targetDate = b.targetDate, skipped = true)
        }
        sheetTrack("fixed_detail.result", GD.result() + mapOf("fixed_action" to "skip"))
        busyFixed = false
        if (ok != null) close()
    }

    suspend fun resetFixed(b: FixedBlock) {
        busyFixed = true
        val ok = Mutation.run(invalidate = listOf(DataKey.timetable), successMessage = "기본 시간으로 되돌렸어요.") {
            TimetableAPI.deletePatternOverride(lifePatternId = b.lifePatternId, targetDate = b.targetDate)
        }
        sheetTrack("fixed_detail.result", GD.result() + mapOf("fixed_action" to "reset"))
        busyFixed = false
        if (ok != null) close()
    }

    // MARK: - 저장 요청 (범위 · 드래그 · 시간 입력 공통)

    private suspend fun reschedule(t: TimeBlockTask, date: String, start: Int, end: Int): Boolean {
        var done = false
        Mutation.run<TaskScheduleResponse?>(
            invalidate = listOf(DataKey.timetable, DataKey.goals),
            success = { r -> TimetableAPI.scheduleResultMessage(r) },
        ) {
            val r = TimetableAPI.reschedule(
                taskId = t.taskId,
                plannedStartAt = KST.toDateTime(date, start),
                plannedEndAt = KST.toDateTime(date, end),
            )
            done = true
            r
        }
        return done
    }

    private suspend fun rescheduleBatch(moves: List<TimetableGroupMember>): Boolean {
        val items = moves.map {
            TimetableAPI.ScheduleItem(
                taskId = it.task.taskId,
                plannedStartAt = KST.toDateTime(it.date, it.start),
                plannedEndAt = KST.toDateTime(it.date, it.end),
            )
        }
        val count = items.size
        var done = false
        Mutation.run<TaskBatchScheduleResponse?>(
            invalidate = listOf(DataKey.timetable, DataKey.goals),
            success = { r -> TimetableAPI.batchResultMessage(r, count = count) },
        ) {
            val r = TimetableAPI.rescheduleBatch(items = items)
            done = true
            r
        }
        return done
    }

    private suspend fun saveRoutine(t: TimeBlockTask, date: String, start: Int, end: Int, scope: ChangeScope): Boolean {
        var done = false
        Mutation.run<RoutineScheduleResponse?>(
            invalidate = listOf(DataKey.timetable, DataKey.goals),
            success = { r -> TimetableAPI.routineScopeMessage(r, scope = scope) },
        ) {
            val r = TimetableAPI.routineScopedReschedule(
                taskId = t.taskId,
                plannedStartAt = KST.toDateTime(date, start),
                plannedEndAt = KST.toDateTime(date, end),
                scope = scope,
            )
            done = true
            r
        }
        return done
    }

    /**
     * - date: start/end 의 기준 날짜 (끌어놓은 칸). 블록 소속 날짜(targetDate)와 시작 날짜가 다르면
     *         startDayOffset 으로 알려줘요 — 예: 토요일 소속 수면(00:00)을 금요일 밤 23:30 취침으로 → -1
     */
    private suspend fun saveFixed(b: FixedBlock, date: String, start: Int, end: Int, scope: ChangeScope): Boolean {
        val startDate = KST.addDays(date, floor(start.toDouble() / 1440).toInt())
        val offset = (KST.minutesFrom(b.targetDate, "${startDate}T00:00:00").toDouble() / 1440).roundToInt()
        val changesDefault = scope == ChangeScope.FOLLOWING || scope == ChangeScope.WEEKDAY
        // FOLLOWING · WEEKDAY 는 기본값이 바뀌니 생활 패턴 목록도 다시 불러와요
        val keys = if (changesDefault) listOf(DataKey.timetable, DataKey.lifePatterns) else listOf(DataKey.timetable)
        val ok = Mutation.run(invalidate = keys, successMessage = TimetableAPI.patternScopeMessage(scope)) {
            TimetableAPI.patternScopedChange(
                lifePatternId = b.lifePatternId, targetDate = b.targetDate,
                startTime = KST.minutesToTime(start), endTime = KST.minutesToTime(end),
                scope = scope, startDayOffset = offset,
            )
        }
        if (changesDefault) lifePatterns = null
        return ok != null
    }

    /**
     * 실제로 한 시간 저장 — 한 구간이면 예전처럼, 여러 구간이면 segments 로
     * - fromSheet: 실제로 한 시간 시트에서 저장 (actual_time.result) · false 면 끌어 옮긴 실제 블록 (timetable.move_result)
     */
    suspend fun submitActual(
        t: TimeBlockTask,
        date: String,
        segments: List<TimetableTimeValue>,
        successMessage: String = "완료했어요. 실제로 한 시간도 남겼어요.",
        fromSheet: Boolean = true,
    ): Boolean {
        if (editSaving || segments.isEmpty()) return false
        editSaving = true
        val r = Mutation.run(
            invalidate = listOf(DataKey.timetable, DataKey.goals, DataKey.home),
            successMessage = successMessage,
        ) {
            if (segments.size > 1) {
                TimetableAPI.completeWithSegments(
                    taskId = t.taskId,
                    segments = segments.map {
                        ActualSegment(start = KST.toDateTime(date, it.start), end = KST.toDateTime(date, it.end))
                    },
                )
            } else {
                TimetableAPI.completeWithActual(
                    taskId = t.taskId,
                    actualStartAt = KST.toDateTime(date, segments[0].start),
                    actualEndAt = KST.toDateTime(date, segments[0].end),
                )
            }
        }
        val res = GD.result()
        editSaving = false
        if (fromSheet) sheetTrack("actual_time.result", res)
        if (r != null) close()
        return r != null
    }

    /** 시간 입력 창 저장 (실제로 한 시간 · 고정 시간) */
    suspend fun submitEdit(editing: TimetableTimeEditing, value: TimetableTimeValue, scope: ChangeScope) {
        if (editSaving) return
        editSaving = true
        val v = value
        val ok = when (val target = editing.target) {
            is TimetableTimeEditTarget.actual -> {
                val r = Mutation.run(
                    invalidate = listOf(DataKey.timetable, DataKey.goals),
                    successMessage = "완료했어요. 실제로 한 시간도 남겼어요.",
                ) {
                    TimetableAPI.completeWithActual(
                        taskId = target.task.taskId,
                        actualStartAt = KST.toDateTime(v.date, v.start),
                        actualEndAt = KST.toDateTime(v.date, v.end),
                    )
                }
                r != null
            }
            is TimetableTimeEditTarget.fixed -> {
                val ok = saveFixed(target.block, date = v.date, start = v.start, end = v.end, scope = scope)
                sheetTrack("fixed_detail.result", GD.result(ok) + mapOf("fixed_action" to "time"))
                ok
            }
        }
        editSaving = false
        if (ok) close()
    }

    // MARK: - 임시 표시

    private fun addPending(p: TimetableLayout.Pending): String {
        val e = TimetablePendingEntry(value = p)
        pending = pending + e
        return e.id
    }

    private fun removePending(id: String) {
        pending = pending.filter { it.id != id }
    }

    /** 저장 끝 → 다시 불러오기가 끝나면 임시 표시를 지워요 */
    private fun settle(ids: List<String>) {
        if (offline) {
            pending = pending.filter { !ids.contains(it.id) }
            return
        }
        settling.addAll(ids)
    }

    private fun finishSettling() {
        if (settling.isEmpty()) return
        val done = settling.toSet()
        settling = mutableSetOf()
        pending = pending.filter { !done.contains(it.id) }
    }

    // MARK: - 범위 고르기 (이날만 / 이번 주 / 앞으로)

    fun chooseScope(scope: ChangeScope) {
        val a = ask ?: return
        ask = null
        // 화면을 떠나도 끝까지 저장하도록 오래 사는 범위에서 보내요
        persistScope.launch {
            val ok = when (val k = a.kind) {
                is TimetableScopeAskKind.routine ->
                    saveRoutine(k.task, date = a.date, start = a.start, end = a.end, scope = scope)
                is TimetableScopeAskKind.fixed ->
                    saveFixed(k.block, date = a.date, start = a.start, end = a.end, scope = scope)
            }
            sheetTrack(
                "scope_chooser.result",
                GD.result(ok) + mapOf("scope_target" to scopeTarget(a), "change_scope" to scope.rawValue),
            )
            // 성공 · 실패 모두 TimeTable 을 다시 불러온 뒤 임시 표시를 지워요
            settle(listOf(a.pendingId))
        }
    }

    /** 취소하고 원래대로 */
    fun cancelAsk() {
        ask?.let { removePending(it.pendingId) }
        ask = null
    }

    /** 범위를 고르지 않고 화면을 떠나면 "이날만"으로 저장해요 */
    fun saveAskOnLeave() {
        if (ask != null) autoDay("leave_page")
    }

    /** 화면이 사라질 때도 고르지 않은 범위는 "이날만"으로 저장 */
    override fun onDispose() {
        saveAskOnLeave()
        super.onDispose()
    }

    // MARK: - 드래그 앤 드롭 (웹 useGridDrag + onDrop)

    /** 길게 누르기가 끝난 순간 (손가락은 아직 그대로) — 들어 올린 표시. 진동은 격자가 해요. 들어 올렸으면 true */
    fun lift(ownerId: String): Boolean {
        if (drag != null || liftedOwnerId == ownerId) return false
        liftedOwnerId = ownerId
        return true
    }

    /** 끌기 시작. 진동이 필요하면(아직 들어 올리지 않았던 블록) true */
    fun beginDrag(source: TimetableDragSource, mode: TimetableDragMode, grab: Double?, ownerId: String): Boolean {
        // 범위를 고르지 않고 다음 드래그를 하면 앞의 변경은 "이날만"으로 저장해요
        if (ask != null) autoDay("next_drag")
        close()
        val alreadyLifted = liftedOwnerId == ownerId
        liftedOwnerId = ownerId
        drag = TimetableDragState(source = source, mode = mode, target = null, invalid = null, grab = grab, ownerId = ownerId)
        return !alreadyLifted
    }

    private fun dropLift(ownerId: String) {
        if (liftedOwnerId == ownerId) liftedOwnerId = null
    }

    /** 놓을 자리가 바뀌면 true (격자가 가벼운 진동) */
    fun updateDrag(target: TimetableDropTarget?): Boolean {
        val st = drag ?: return false
        val invalid = target?.let { validate(st.source, it) }
        if (st.target == target && st.invalid == invalid) return false
        val moved = st.target != null
        drag = st.copy(target = target, invalid = invalid)
        return moved
    }

    /** 손을 뗌 → 놓을 수 있으면 저장 */
    fun endDrag(ownerId: String) {
        dropLift(ownerId)
        val st = drag ?: return
        if (st.ownerId != ownerId) return
        drag = null
        val target = st.target
        if (target == null) {
            gridTrack(
                "timetable.drag_cancel",
                mapOf("drag_source" to gridDragSource(st.source), "drag_mode" to gridDragMode(st.mode), "cancel_reason" to "no_target"),
            )
            return
        }
        val invalid = st.invalid
        if (invalid != null) {
            gridTrack(
                "timetable.drag_cancel",
                mapOf(
                    "drag_source" to gridDragSource(st.source), "drag_mode" to gridDragMode(st.mode),
                    "cancel_reason" to invalidReason(invalid), "validation_error" to invalid,
                ),
            )
            if (isHome) {
                HomeTracker.system(
                    HomeEvent.calendarDragCancel,
                    "drag_source" to dragSourceName(st.source), "drag_mode" to dragModeName(st.mode),
                    "drag_cancel_reason" to "invalid_drop", "view_date" to rangeStart,
                )
            }
            Toast.error(invalid)
            return
        }
        if (TimetableDrag.sameAsSource(st.source, target)) return
        if (isHome) {
            val src = st.source
            val task = (src as? TimetableDragSource.task)?.task ?: (src as? TimetableDragSource.actual)?.task
            val needsScope = src is TimetableDragSource.fixed ||
                (src is TimetableDragSource.task && src.group.isEmpty() && src.task.sourceType == TaskSourceType.ROUTINE)
            HomeTracker.track(
                HomeEvent.calendarDragDrop,
                "view_date" to rangeStart, "drag_source" to dragSourceName(src), "drag_mode" to dragModeName(st.mode),
                "drop_target" to if (target is TimetableDropTarget.grid) "grid" else "unscheduled",
                "task_id" to task?.taskId, "source_type" to task?.sourceType?.rawValue, "needs_scope" to needsScope,
                "group_size" to if (src is TimetableDragSource.task) maxOf(1, src.group.size) else null,
            )
        } else {
            val src = st.source
            val dragged = src.taskItem
            val needsScope = target is TimetableDropTarget.grid && dragged?.proposed != true && (
                src is TimetableDragSource.fixed ||
                    (src is TimetableDragSource.task && src.group.isEmpty() && src.task.sourceType == TaskSourceType.ROUTINE &&
                        TimetableRules.routineDay(src.task) == target.date)
                )
            gridTrack(
                "timetable.drag_drop",
                mapOf(
                    "view_mode" to gridViewMode, "drag_source" to gridDragSource(src), "drag_mode" to gridDragMode(st.mode),
                    "drop_target" to if (target is TimetableDropTarget.unscheduled) "unscheduled" else "grid",
                    "task_id" to dragged?.taskId, "source_type" to dragged?.sourceType?.rawValue,
                    "needs_scope" to needsScope, "group_size" to if (src.group.isEmpty()) null else src.group.size,
                ),
            )
        }
        handleDrop(st.source, target)
    }

    /** 제스처가 끝나지 못하고 취소됨 (전화가 오는 등) */
    fun cancelDrag(ownerId: String) {
        dropLift(ownerId)
        val st = drag
        if (st != null && st.ownerId == ownerId) {
            gridTrack(
                "timetable.drag_cancel",
                mapOf(
                    "drag_source" to gridDragSource(st.source), "drag_mode" to gridDragMode(st.mode),
                    "cancel_reason" to "gesture_cancelled",
                ),
            )
        }
        if (st != null && st.ownerId == ownerId && isHome) {
            HomeTracker.system(
                HomeEvent.calendarDragCancel,
                "drag_source" to dragSourceName(st.source), "drag_mode" to dragModeName(st.mode),
                "drag_cancel_reason" to "gesture_cancelled", "view_date" to rangeStart,
            )
        }
        if (drag?.ownerId == ownerId) drag = null
    }

    /** 한 시각에 Task 는 2개까지 겹쳐 둘 수 있어요 (백엔드 TaskScheduleService 와 같은 규칙) */
    private fun validate(source: TimetableDragSource, target: TimetableDropTarget): String? {
        val g = target as? TimetableDropTarget.grid ?: return null
        val date = g.date
        val start = g.start
        val end = g.end
        return when (source) {
            is TimetableDragSource.fixed -> validateSleep(source.block, date = date, start = start, end = end)
            is TimetableDragSource.actual -> {
                // 나눠서 한 다른 구간과 겹치면 안 돼요 (붙는 건 괜찮아요)
                val others = actualRanges(source.task, date = source.date).filterIndexed { i, _ -> i != source.part }
                when {
                    others.any { start < it.end && it.start < end } -> "나눠서 한 다른 구간과 겹쳐요"
                    end - start > 1440 -> "한 구간은 하루를 넘을 수 없어요"
                    else -> null
                }
            }
            is TimetableDragSource.task -> validateTaskMoves(source, date = date, start = start, end = end)
        }
    }

    private fun validateTaskMoves(source: TimetableDragSource, date: String, start: Int, end: Int): String? {
        val moves = TimetableDrag.groupMoves(source, date = date, start = start, end = end)
        val moving = moves.map { it.task.taskId }.toSet()
        val ds = days
        for (i in moves.indices) {
            val m = moves[i]
            val day = ds.firstOrNull { it.date == m.date } ?: continue
            // 같이 옮기는 다른 Task 들의 새 자리도 자리를 차지해요
            val others = mutableListOf<TimetableLayout.MinuteRange>()
            for (j in moves.indices) {
                if (j != i) others.add(TimetableLayout.MinuteRange(start = moves[j].start, end = moves[j].end))
            }
            if (TimetableLayout.crowdedTasks(day, start = m.start, end = m.end, exclude = moving, extra = others) != null) {
                val who = if (moves.size > 1) "\"${m.task.taskName}\" 자리에 " else ""
                return "${who}이미 Task ${TimetableLayout.maxTasksAtOnce}개가 겹쳐 있어요"
            }
        }
        return null
    }

    private fun validateSleep(b: FixedBlock, date: String, start: Int, end: Int): String? {
        if (b.patternType != LifePatternType.SLEEP) return null
        val day = days.firstOrNull { it.date == date } ?: return null
        val s = day.sleep ?: return null
        val w = s.wake
        val bed = s.bed
        if (w != null && w.key == b.key && bed != null &&
            end > KST.minutesFrom(day.date, bed.start) - TimetableRules.minAwakeMinutes
        ) {
            return "취침 시간보다 늦게 일어날 수 없어요"
        }
        if (bed != null && bed.key == b.key && w != null &&
            start < KST.minutesFrom(day.date, w.end) + TimetableRules.minAwakeMinutes
        ) {
            return "기상 시간보다 일찍 잘 수 없어요"
        }
        return null
    }

    /** 실제 시간 블록을 옮기거나 늘리고 줄였을 때 — 그 구간만 바꾸고 나머지 구간은 그대로 저장해요 */
    private fun dropActual(t: TimeBlockTask, date: String, part: Int, start: Int, end: Int) {
        var ranges = actualRanges(t, date = date).toMutableList()
        val moved = TimetableLayout.MinuteRange(start = start, end = end)
        if (part in ranges.indices) ranges[part] = moved else ranges = mutableListOf(moved)
        ranges.sortBy { it.start }
        val segments = ranges.map { TimetableTimeValue(date = date, start = it.start, end = it.end) }
        scope.launch {
            val ok = submitActual(t, date = date, segments = segments, successMessage = "실제로 한 시간을 바꿨어요.", fromSheet = false)
            gridTrack("timetable.move_result", GD.result(ok) + mapOf("drag_source" to "actual", "drop_target" to "grid"))
        }
    }

    private fun handleDrop(source: TimetableDragSource, target: TimetableDropTarget) {
        if (source is TimetableDragSource.actual) {
            if (target is TimetableDropTarget.grid) dropActual(source.task, date = target.date, part = source.part, start = target.start, end = target.end)
            return
        }
        // 미리보기 제안 블록: 저장하지 않고 제안 자리만 바꿔요 (시간 미정 칸으로 끌면 이번 배치에서 빼요)
        val item = source.taskItem
        if (item != null && item.proposed == true) {
            val next = mutableListOf<TimetableLayout.Proposal>()
            for (p in proposals ?: emptyList()) {
                if (p.taskId != item.taskId) {
                    next.add(p)
                } else if (target is TimetableDropTarget.grid) {
                    val q = p.copy()
                    q.start = KST.toDateTime(target.date, target.start)
                    q.end = KST.toDateTime(target.date, target.end)
                    next.add(q)
                }
            }
            proposals = next
            return
        }
        if (ask != null) autoDay("next_drag")

        // 묶음 드래그: 끈 만큼 묶음 전체를 같이 옮기고 한 번에 저장 (루틴도 이날만 바뀌어요)
        if (source.group.isNotEmpty() && target is TimetableDropTarget.grid) {
            val moves = TimetableDrag.groupMoves(source, date = target.date, start = target.start, end = target.end)
            val ids = moves.map {
                addPending(TimetableLayout.Pending.task(taskId = it.task.taskId, date = it.date, start = it.start, end = it.end))
            }
            scope.launch {
                val ok = rescheduleBatch(moves)
                gridTrack(
                    "timetable.move_result",
                    GD.result(ok) + mapOf("drag_source" to "group", "drop_target" to "grid", "group_size" to moves.size),
                )
                settle(ids)
            }
            return
        }

        val p: TimetableLayout.Pending = when {
            source is TimetableDragSource.task && target is TimetableDropTarget.grid ->
                TimetableLayout.Pending.task(taskId = source.task.taskId, date = target.date, start = target.start, end = target.end)
            source is TimetableDragSource.task && target is TimetableDropTarget.unscheduled ->
                TimetableLayout.Pending.task(taskId = source.task.taskId, date = null, start = 0, end = 0)
            source is TimetableDragSource.fixed && target is TimetableDropTarget.grid ->
                TimetableLayout.Pending.fixed(key = source.block.key, date = target.date, start = target.start, end = target.end)
            else -> return
        }
        val pid = addPending(p)

        // 루틴을 같은 날 안에서 옮겼거나, 고정 시간을 옮겼으면 → 범위를 물어봐요
        if (item != null && target is TimetableDropTarget.grid &&
            item.sourceType == TaskSourceType.ROUTINE && TimetableRules.routineDay(item) == target.date
        ) {
            showAsk(
                TimetableScopeAsk(
                    kind = TimetableScopeAskKind.routine(item), date = target.date,
                    start = target.start, end = target.end, pendingId = pid,
                )
            )
            return
        }
        if (source is TimetableDragSource.fixed && target is TimetableDropTarget.grid) {
            val b = source.block
            val edge: TimetableLayout.SleepEdge? = if (b.patternType == LifePatternType.SLEEP) {
                if (target.start != source.start) TimetableLayout.SleepEdge.bed else TimetableLayout.SleepEdge.wake
            } else {
                null
            }
            showAsk(
                TimetableScopeAsk(
                    kind = TimetableScopeAskKind.fixed(b, edge = edge), date = target.date,
                    start = target.start, end = target.end, pendingId = pid,
                )
            )
            return
        }

        val t = item ?: return
        scope.launch {
            val ok = if (target is TimetableDropTarget.grid) {
                reschedule(t, date = target.date, start = target.start, end = target.end)
            } else if (target is TimetableDropTarget.unscheduled && target.date != source.date) {
                // 다른 날 시간 미정 칸으로 (미결정 → 미결정 포함, 2026-10)
                moveDateRequest(t.taskId, date = target.date, keepTime = false) != null
            } else {
                unscheduleRequest(t.taskId)
            }
            gridTrack(
                "timetable.move_result",
                GD.result(ok) + mapOf(
                    "drag_source" to "task",
                    "drop_target" to if (target is TimetableDropTarget.grid) "grid" else "unscheduled",
                ),
            )
            settle(listOf(pid))
        }
    }

    // MARK: - AI로 배치하기: 미리보기 → (끌어서 고치기) → 이대로 진행 / 취소

    suspend fun previewFill(
        target: TimetableFillTarget,
        kind: TimetableProposalKind = TimetableProposalKind.fill,
        onPreview: ((FillPreviewResponse) -> Unit)? = null,
        onResolve: ((Boolean, List<TimetableLayout.Proposal>) -> Unit)? = null,
        onError: (() -> Unit)? = null,
    ) {
        if (offline || fillPreviewing) return
        fillPreviewing = true
        try {
            val r: FillPreviewResponse = when (target) {
                is TimetableFillTarget.range -> TimetableAPI.fillPreview(startDate = target.start, endDate = target.end)
                is TimetableFillTarget.tasks -> TimetableAPI.fillPreview(taskIds = target.ids)
            }
            resolveHandler = onResolve
            proposalKind = kind
            sheetTrack(
                "fill_proposal.view",
                mapOf(
                    "proposal_kind" to if (kind == TimetableProposalKind.added) "new" else "fill",
                    "placed_count" to r.proposals.size, "unscheduled_count" to r.unscheduled.size,
                ),
            )
            onPreview?.invoke(r)
            proposals = r.proposals.map {
                TimetableLayout.Proposal(
                    taskId = it.taskId, reason = it.reason, proposedStart = it.start,
                    proposedEnd = it.end, start = it.start, end = it.end,
                )
            }
            proposalUnscheduled = r.unscheduled.size
        } catch (c: CancellationException) {
            // 화면을 떠났어요
            throw c
        } catch (e: Exception) {
            Toast.error(errorMessage(e))
            onError?.invoke()
        } finally {
            fillPreviewing = false
        }
    }

    /** 아래 바 '취소' · '아니요' · '닫기' */
    fun cancelProposalsByUser() {
        sheetTrack(
            "fill_proposal.cancel",
            mapOf(
                "proposal_kind" to if (proposalKind == TimetableProposalKind.added) "new" else "fill",
                "close_method" to "cancel_button", "dragged_count" to editedProposals,
            ),
        )
        cancelProposals()
    }

    fun cancelProposals() {
        resolveHandler?.invoke(false, proposals ?: emptyList())
        resolveHandler = null
        proposals = null
    }

    suspend fun applyProposalsNow() {
        val ps = proposals
        if (ps.isNullOrEmpty() || fillApplying) return
        val kindName = if (proposalKind == TimetableProposalKind.added) "new" else "fill"
        sheetTrack(
            "fill_proposal.apply_click",
            mapOf("proposal_kind" to kindName, "placed_count" to ps.size, "dragged_count" to editedProposals),
        )
        fillApplying = true
        val items = ps.map {
            TimetableAPI.FillApplyItem(
                taskId = it.taskId, proposedStart = it.proposedStart, proposedEnd = it.proposedEnd,
                start = it.start, end = it.end, reason = it.reason,
            )
        }
        var done = false
        Mutation.run<FillApplyResponse?>(
            invalidate = listOf(DataKey.timetable, DataKey.goals),
            success = { res ->
                if (res == null) {
                    null
                } else {
                    "${res.placed}개 Task를 배치했어요." +
                        (if (res.skipped.isEmpty()) "" else " ${res.skipped.size}개는 그사이 자리가 바뀌어 시간 미정으로 남겼어요.")
                }
            },
        ) {
            val r = TimetableAPI.fillApply(items = items)
            done = true
            r
        }
        sheetTrack("fill_proposal.result", GD.result(done) + mapOf("proposal_kind" to kindName))
        fillApplying = false
        if (done) {
            resolveHandler?.invoke(true, ps)
            resolveHandler = null
            proposals = null
        }
    }

    // MARK: - 22시 이월 제안

    suspend fun acceptCarry(t: TimeBlockTask) {
        if (carryBusy != null) return
        carryBusy = TimetableCarryAction.accept
        Mutation.run(invalidate = listOf(DataKey.timetable), successMessage = "옮겼어요.") {
            TimetableAPI.acceptCarry(taskId = t.taskId)
        }
        sheetTrack("carry_proposal.result", GD.result() + mapOf("carry_action" to "accept"))
        carryBusy = null
    }

    suspend fun declineCarry(t: TimeBlockTask) {
        if (carryBusy != null) return
        carryBusy = TimetableCarryAction.decline
        Mutation.run(
            invalidate = listOf(DataKey.timetable),
            successMessage = "시간 미정으로 두었어요. 원하는 시간에 끌어다 놓거나 지울 수 있어요.",
        ) {
            TimetableAPI.declineCarry(taskId = t.taskId)
        }
        sheetTrack("carry_proposal.result", GD.result() + mapOf("carry_action" to "decline"))
        carryBusy = null
    }

    /** 이월 되돌리기 (2026-10) — 원래 날짜 · 시각으로. thenActual 이면 이어서 실제로 한 시간 입력 ("그때 했어요") */
    suspend fun revertCarry(taskId: Int, from: String? = null, thenActual: Boolean = false, fromBar: Boolean = false) {
        if (carryBusy != null) return
        carryBusy = TimetableCarryAction.revert
        var reverted: TimeBlockTask? = null
        Mutation.run<TimeBlockTask?>(invalidate = listOf(DataKey.timetable), success = { t ->
            if (thenActual) null
            else if (t?.plannedStartAt != null) "원래 시간으로 되돌렸어요."
            else "원래 날로 되돌렸어요. 시간이 차 있어서 시간 미정에 두었어요."
        }) {
            val r = TimetableAPI.revertCarry(taskId = taskId, from = from)
            reverted = r
            r
        }
        if (fromBar) sheetTrack("carry_proposal.result", GD.result() + mapOf("carry_action" to "revert"))
        carryBusy = null
        val t = reverted
        if (thenActual && t?.plannedStartAt != null) completeWithTime(t)
    }

    suspend fun acceptAllCarry(date: String) {
        if (carryBusy != null) return
        carryBusy = TimetableCarryAction.acceptAll
        Mutation.run<Int>(invalidate = listOf(DataKey.timetable), success = { n -> "${n}개를 옮겼어요." }) {
            TimetableAPI.acceptAllCarry(date = date)
        }
        sheetTrack("carry_proposal.result", GD.result() + mapOf("carry_action" to "accept_all"))
        carryBusy = null
    }

    // MARK: - 실제로 한 일

    suspend fun saveActivity(input: ActivityInput, activityId: Int?): Boolean {
        val msg = if (activityId != null) "기록을 수정했어요." else "실제로 한 일을 기록했어요."
        var done = false
        Mutation.run<ActualActivity?>(invalidate = listOf(DataKey.activities), success = { msg }) {
            val r = TimetableAPI.saveActivity(input = input, activityId = activityId)
            done = true
            r
        }
        sheetTrack(
            "activity_form.result",
            GD.result(done) + mapOf("activity_action" to if (activityId != null) "update" else "create"),
        )
        if (done) close()
        return done
    }

    suspend fun deleteActivity(activityId: Int): Boolean {
        val r = Mutation.run(invalidate = listOf(DataKey.activities), successMessage = "기록을 지웠어요.") {
            TimetableAPI.deleteActivity(activityId = activityId)
        }
        sheetTrack("activity_form.result", GD.result() + mapOf("activity_action" to "delete"))
        if (r != null) close()
        return r != null
    }

    companion object {
        /** 화면이 사라져도 끝까지 보내야 하는 저장 (범위 고르기) */
        private val persistScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        /** 실제로 한 시간 구간들 — date 0시 기준 분 (나눠서 안 했으면 실제 시작~끝 한 구간) */
        fun actualRanges(t: TimeBlockTask, date: String): List<TimetableLayout.MinuteRange> {
            val segs = t.actualSegments
            if (!segs.isNullOrEmpty()) {
                return segs.map {
                    TimetableLayout.MinuteRange(start = KST.minutesFrom(date, it.start), end = KST.minutesFrom(date, it.end))
                }
            }
            val s = t.actualStartAt
            val e = t.actualEndAt
            if (s != null && e != null) {
                return listOf(TimetableLayout.MinuteRange(start = KST.minutesFrom(date, s), end = KST.minutesFrom(date, e)))
            }
            return emptyList()
        }

        // MARK: - 미리보기용 샘플

        /** 네트워크 없이 그려지는 보드 (@Preview 전용) */
        fun sample(start: String, end: String, withProposal: Boolean = false): TimetableBoardModel {
            val m = TimetableBoardModel()
            m.offline = true
            m.rangeStart = start
            m.rangeEnd = end
            m.rawDays = TimetableSampleData.days(from = start, to = end)
            m.activities = TimetableSampleData.activities(on = KST.today())
            m.loadedOnce = true
            val t = m.rawDays.flatMap { it.unscheduledTasks }.firstOrNull()
            if (withProposal && t != null) {
                val d = KST.today()
                m.proposals = listOf(
                    TimetableLayout.Proposal(
                        taskId = t.taskId, reason = "집중이 잘 되는 시간이에요.",
                        proposedStart = "${d}T16:00:00", proposedEnd = "${d}T17:00:00",
                        start = "${d}T16:00:00", end = "${d}T17:00:00",
                    )
                )
                m.proposalKind = TimetableProposalKind.added
            }
            return m
        }
    }
}
