// feature/taskadd/TaskAddModels.kt
//
// iOS Features/TaskAdd/TaskAddModels.swift
// 웹 원본: features/brain-dump/ui/TaskAddView.tsx (상태 · 요청 흐름), StepInput.tsx (DumpLine · newLine),
//         StepGoals.tsx (MapItem), ItemEditModal.tsx (상수)
//
// Task 추가 시트의 "화면 상태"를 한곳에 모았어요.
//  - TaskAddedInfo   : 캘린더(다른 작업자)에 "이 Task 들을 추가했어요"를 알려 주는 약속 타입
//  - BrainDumpLine   : 1단계에서 적은 한 줄 (날짜 · 시간 칩 포함)
//  - BrainDumpMapItem: 2단계에서 고치는 Task 하나 (확정 요청 Item + AI 가 붙인 연결 이유)
//  - TaskAddViewModel: 3단계 흐름(입력 → 목표 연결 → 결과) 전체 상태와 서버 요청
//
// API 요청 함수 · 응답 모델은 공통 파일 model/BrainDumpModels.kt (BrainDumpAPI) 에 이미 있어요.
//
// Android 에서 바꾼 점
//  - Swift 의 struct(값 타입)는 Kotlin data class 의 val + copy() 로 옮겼어요.
//    (Kotlin 객체는 참조라서 목록 안 값을 직접 바꾸면 화면이 바뀐 걸 모를 수 있어요)

package com.naleul.naleul.feature.taskadd

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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException

// MARK: - 약속(Contract) 타입

/** 추가한 Task 알림 — 캘린더가 그 날짜로 옮기고 새 블록에 NEW 를 붙여요 (웹 TaskAddedInfo) */
data class TaskAddedInfo(
    val taskIds: List<Int>,
    /** 가장 이른 날짜 (시간 미정이면 그날) */
    val date: String? = null,
    /** 저장만 하고 아직 시간을 정하지 않은 Task — 캘린더가 점선 미리보기로 "이대로 진행할까요?"를 물어요 */
    val pendingTaskIds: List<Int>? = null,
) {
    companion object {}
}

// MARK: - 상수 · 문구

object BrainDumpConst {
    /** 백엔드 한 번에 최대 20개 (웹 MAX_LINES) */
    const val maxLines = 20
    /** 임시 목표 최대 개수 (웹 MAX_TEMP_GOALS) */
    const val maxTempGoals = 5
    /** 1단계 "걸리는 시간" 칩 목록 (분) */
    val durations = listOf(15, 30, 45, 60, 90, 120, 150, 180, 240, 300, 360, 480)
    /** 2단계 "예상 소요 시간" 목록 (분) */
    val editMinutes = listOf(10, 15, 20, 30, 45, 60, 90, 120, 150, 180, 240, 300, 360, 480)

    /** 2단계 목록의 중요도 표시 (웹 StepGoals PRIORITY_LABEL) */
    fun priorityLabel(p: TaskPriority?): String = when (p ?: TaskPriority.C) {
        TaskPriority.A -> "가장 중요"
        TaskPriority.B -> "중요"
        TaskPriority.C -> "보통"
        TaskPriority.D -> "가벼움"
        TaskPriority.E -> "여유"
    }

    /** 변경 시트의 중요도 설명 (웹 ItemEditModal PRIORITY_HINT) */
    fun priorityHint(p: TaskPriority): String = when (p) {
        TaskPriority.A -> "가장 중요"
        TaskPriority.B -> "중요"
        TaskPriority.C -> "보통"
        TaskPriority.D -> "가벼움"
        TaskPriority.E -> "여유"
    }

    /** 변경 시트의 소요 시간 표기: 45 → "45분", 90 → "1시간 30분", 120 → "2시간" */
    fun minutesLabel(m: Int): String {
        if (m < 60) return "${m}분"
        if (m % 60 != 0) return "${m / 60}시간 ${m % 60}분"
        return "${m / 60}시간"
    }

    /** "15:00:00" → "15:00" */
    fun toHm(t: String?): String? {
        if (t.isNullOrEmpty()) return null
        return t.take(5)
    }

    /** "HH:mm" 끼리 차이(분). 자정을 넘으면 다음 날로 계산 (웹 DurationChip 의 fixed) */
    fun span(start: String, end: String): Int? {
        val d = ((KST.timeToMinutes(end) - KST.timeToMinutes(start)) % 1440 + 1440) % 1440
        return if (d == 0) null else d
    }
}

// MARK: - 1단계 한 줄

/** 1단계에서 적은 한 줄. 날짜·시간 칩을 고르면 AI 해석보다 우선해요. (웹 DumpLine) */
data class BrainDumpLine(
    val clientKey: String,
    val text: String,
    val date: String? = null,
    /** 날짜를 골랐을 때만 의미: ON = 그날 · DUE = 그날까지 */
    val dateType: DateType = DateType.ON,
    /** "HH:mm" */
    val startTime: String? = null,
    val endTime: String? = null,
    /** 걸리는 시간(분). 비워 두면 AI 가 추정해요 */
    val minutes: Int? = null,
) {
    val id: String get() = clientKey

    companion object {
        /**
         * 새 줄. today 를 주면 문장에서 시각·소요 시간·오늘/내일/모레를 바로 읽어 칩을 채워요 (웹 newLine)
         * 예) "저녁 8시 정기 회의" → 시작 20:00 / "인강 듣기 3시간" → 소요 3시간
         */
        fun make(text: String, today: String?): BrainDumpLine {
            val p = today?.let { LineParser.parse(text = text, today = it) }
            return BrainDumpLine(
                clientKey = BrainDumpAPI.newClientKey(),
                text = text.take(200),
                date = p?.date,
                dateType = DateType.ON,
                startTime = p?.startTime,
                endTime = if (p?.startTime != null) p?.endTime else null,
                minutes = p?.minutes,
            )
        }
    }
}

// MARK: - 2단계 Task

/** 2단계에서 고치는 Task 하나 = 확정 요청 Item + AI 가 붙인 연결 이유 (웹 MapItem) */
data class BrainDumpMapItem(
    val clientKey: String,
    val title: String,
    val dateType: DateType,
    val scheduledDate: String? = null,
    val dueDate: String? = null,
    /** "HH:mm" */
    val startTime: String? = null,
    val endTime: String? = null,
    val estimatedMinutes: Int? = null,
    val priority: TaskPriority? = null,
    val kind: TaskKind? = null,
    val goalCategoryId: Int? = null,
    val generalCategoryId: Int? = null,
    val milestoneId: Int? = null,
    val tempGoalKey: String? = null,
    val reason: String? = null,
    /** 업무형 목표의 주제 (WorkTopic, 2026-10) */
    val workTopicId: Int? = null,
) {
    val id: String get() = clientKey

    /** 확정 요청용 — 날짜 종류에 맞지 않는 값은 비워서 보내요 (웹 goConfirm 과 같은 규칙. reason 은 화면용이라 안 보내요) */
    val confirmItem: ConfirmItem
        get() = ConfirmItem(
            clientKey = clientKey,
            title = title,
            dateType = dateType,
            scheduledDate = if (dateType == DateType.ON) scheduledDate else null,
            dueDate = if (dateType == DateType.DUE) dueDate else null,
            startTime = if (dateType == DateType.ON) startTime else null,
            endTime = if (dateType == DateType.ON && startTime != null) endTime else null,
            estimatedMinutes = estimatedMinutes,
            priority = priority,
            kind = kind,
            goalCategoryId = goalCategoryId,
            generalCategoryId = generalCategoryId,
            milestoneId = milestoneId,
            tempGoalKey = tempGoalKey,
            workTopicId = if (goalCategoryId != null) workTopicId else null,
        )

    companion object {}
}

// MARK: - 화면 상태 (웹 TaskAddView 의 useState 들)

class TaskAddViewModel(mode: TaskAddMode, val today: String = KST.today()) : ScreenModel() {
    // 앰플리튜드용 (화면에는 안 써요)
    val initialMode: TaskAddMode = mode
    private var viewSent = false
    /** AI 가 처음 정리한 목록 — 2단계에서 고친 개수(edited_count)를 세려고 */
    private var parsedItems: List<BrainDumpMapItem> = emptyList()
    /** 직접 추가 폼 · 한 일 기록 입력칸에 글자가 있는지 (방식을 바꾸거나 시트를 닫으면 확인 없이 사라지는 내용) */
    var childHasInput = false
    /** 목표 목록을 못 불러왔는지 (직접 추가의 "목표 없음" 카드가 같은 모양이라 앰플리튜드로 구분해요) */
    var goalsFailed by mutableStateOf(false)

    /** 2단계에서 처음 정리와 달라진 Task 수 */
    val editedCount: Int get() = items.count { item -> parsedItems.firstOrNull { p -> p.clientKey == item.clientKey } != item }

    /** 지금 화면에 적던 내용이 있는지 (task_add.mode_click · panel_close 의 has_input) */
    val hasInput: Boolean get() = if (mode == TaskAddMode.ai) step < 2 && lines.isNotEmpty() else childHasInput

    /** 시트 열림 — 오늘 남은 개수를 받은 뒤(실패해도) 한 번 */
    private fun sendViewOnce() {
        if (viewSent) return
        viewSent = true
        val q = quota
        TA.track("task_add.view", mapOf(
            "initial_mode" to initialMode.rawValue,
            "is_premium" to q?.premium,
            "quota_remaining" to if (q != null && !q.premium) q.remaining else null,
        ))
    }

    /** 할 일을 적거나(ai · manual), 이미 한 일을 기록하거나(log) */
    var mode by mutableStateOf(mode)
    /** 0 입력 · 1 목표 연결 · 2 결과 */
    var step by mutableStateOf(0)
    var lines by mutableStateOf(listOf<BrainDumpLine>())
    var items by mutableStateOf(listOf<BrainDumpMapItem>())
    var tempGoals by mutableStateOf(listOf<TempGoalInput>())
    var aiFailed by mutableStateOf(false)
    var aiLimited by mutableStateOf(false)
    /** clientKey → 겹친 일정 안내 */
    var conflicts by mutableStateOf(mapOf<String, String>())
    var result by mutableStateOf<ConfirmResponse?>(null)

    var quota by mutableStateOf<TaskQuota?>(null)
    var quotaLoading by mutableStateOf(true)
    var goals by mutableStateOf(listOf<GoalCategory>())
    var goalsLoading by mutableStateOf(true)

    var parsing by mutableStateOf(false)
    var confirming by mutableStateOf(false)

    /** 미리보기(@Preview)에서는 서버를 부르지 않아요 */
    var isPreview = false

    /** 진행 중 · 시작 전 목표만 연결 대상으로 (웹 isOngoing) */
    val ongoingGoals: List<GoalCategory> get() = goals.filter { it.goalCategoryStatus.isOngoing }

    /** AI 정리 도중(2단계)에는 다른 방식으로 못 바꿔요 — 적은 내용이 날아가지 않게 */
    fun modeLocked(target: TaskAddMode): Boolean =
        mode == TaskAddMode.ai && target != TaskAddMode.ai && step > 0 && result == null

    // MARK: 불러오기

    /** 오늘 남은 Task 추가 개수 (웹 useTaskQuota) */
    suspend fun loadQuota() {
        if (isPreview) return
        try {
            quota = BrainDumpAPI.quota()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 한도를 못 읽어도 입력은 막지 않아요 (서버가 확정할 때 다시 확인해요)
            if (quotaLoading) TA.track("task_add.quota_load_fail", mapOf("error_status" to TA.status(e)))
        }
        quotaLoading = false
        sendViewOnce()
    }

    suspend fun loadGoals() {
        if (isPreview) return
        try {
            goals = GoalAPI.goals(includeHabit = true)   // 기본 생활 습관으로도 분류 (2026-10)
            goalsFailed = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 목표를 못 읽으면 2단계에서 "목표를 골라 주세요"로 보여요
            goalsFailed = true
        }
        goalsLoading = false
    }

    // MARK: 1단계 → 2단계

    suspend fun goParse() {
        val valid = lines.filter { it.text.trim().isNotEmpty() }
        if (valid.isEmpty() || parsing) return
        val requests = valid.map { l ->
            val hasTime = l.date != null || l.startTime != null
            ParseItemRequest(
                clientKey = l.clientKey,
                text = l.text.trim(),
                date = l.date,
                dateType = if (l.date != null) l.dateType else null,
                startTime = if (hasTime) l.startTime else null,
                endTime = if (hasTime) l.endTime else null,
                estimatedMinutes = l.minutes,
            )
        }
        parsing = true
        val startedAt = System.currentTimeMillis()
        val elapsed = { ((System.currentTimeMillis() - startedAt) / 1000.0).let { Math.round(it).toInt() } }
        try {
            val res = BrainDumpAPI.parse(items = requests)
            if (res == null) {
                TA.track("task_add_input.parse_result", mapOf("result" to "fail", "error_status" to 0, "seconds_elapsed" to elapsed()))
                Toast.error("정리하지 못했어요. 다시 시도해 주세요.")
                return
            }
            val mapped = toMapItems(res)
            TA.track("task_add_input.parse_result", mapOf(
                "result" to "success", "item_count" to mapped.items.size,
                "ai_failed" to res.aiFailed, "ai_limited" to (res.aiLimited ?: false),
                "temp_count" to mapped.tempGoals.size,
                "missing_goal_count" to mapped.items.count { it.goalCategoryId == null && it.tempGoalKey == null },
                "seconds_elapsed" to elapsed(),
            ))
            items = mapped.items
            parsedItems = mapped.items
            tempGoals = mapped.tempGoals
            aiFailed = res.aiFailed
            aiLimited = res.aiLimited ?: false
            conflicts = emptyMap()
            step = 1
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            TA.track("task_add_input.parse_result", mapOf("result" to "fail", "error_status" to TA.status(e), "seconds_elapsed" to elapsed()))
            // Free 한도 초과(403/429)도 여기로 와요 — 백엔드 문구를 그대로 보여 줘요
            val status = (e as? ApiError)?.httpStatus
            if (status == 403 || status == 429) AnalyticsService.logPlanLimitReached(context = "task_per_day")
            Toast.error(errorMessage(e, fallback = "정리하지 못했어요. 다시 시도해 주세요."))
        } finally {
            parsing = false
        }
    }

    /** Swift 튜플 (items:, tempGoals:) 대신 */
    data class MapResult(val items: List<BrainDumpMapItem>, val tempGoals: List<TempGoalInput>) {
        companion object {}
    }

    /** 2단계에서 고친 목록 반영 — 고친 Task 는 겹침 표시를 지워요 */
    fun applyChange(items: List<BrainDumpMapItem>, tempGoals: List<TempGoalInput>) {
        val old = this.items
        this.items = items
        this.tempGoals = tempGoals
        val changed = items.filter { n -> old.firstOrNull { it.clientKey == n.clientKey } != n }
        if (changed.isNotEmpty()) {
            conflicts = conflicts - changed.map { it.clientKey }.toSet()
        }
    }

    // MARK: 2단계 → 3단계

    /** 저장 + 배치. 성공하면 캘린더에 알릴 정보를 돌려줘요 (겹침 · 실패면 null) */
    suspend fun goConfirm(): TaskAddedInfo? {
        if (confirming) return null
        val used = items.mapNotNull { it.tempGoalKey }.toSet()
        TA.track("task_add_goals.confirm_click", mapOf(
            "item_count" to items.size, "temp_count" to used.size,
            "timed_count" to items.count { it.dateType == DateType.ON && it.startTime != null },
            "edited_count" to editedCount,
            "priority_a_count" to items.count { (it.priority ?: TaskPriority.fallback) == TaskPriority.A },
            "work_topic_count" to items.count { it.goalCategoryId != null && it.workTopicId != null },
        ))
        val body = BrainDumpConfirmRequest(
            items = items.map { it.confirmItem },
            tempGoals = tempGoals
                .filter { used.contains(it.tempKey) }
                .map { t -> t.copy(name = t.name.trim()) },
            // 시간을 정하지 않은 Task 는 저장만 → 점선 미리보기로 "이대로 진행할까요?" 물은 뒤 배치
            placeLater = true,
        )
        confirming = true
        try {
            val res = BrainDumpAPI.confirm(body = body)
            if (res == null) {
                TA.track("task_add_goals.confirm_result", mapOf("result" to "fail", "error_status" to 0, "temp_count" to used.size))
                Toast.error("저장하지 못했어요. 다시 시도해 주세요.")
                return null
            }
            if (!res.created) {
                TA.track("task_add_goals.confirm_result", mapOf(
                    "result" to "conflict", "conflict_count" to res.conflicts.size, "temp_count" to used.size,
                ))
                val map = mutableMapOf<String, String>()
                for (c in res.conflicts) {
                    // 한 시각에 2개까지는 같이 둘 수 있어요 — 여기 오는 건 이미 2개가 겹친 시간에 3개째를 넣으려 한 경우
                    val who = c.conflictTitle?.let { "\"$it\"" } ?: "다른 일정 2개"
                    map[c.clientKey] = "이 시간엔 이미 ${who}이(가) 겹쳐 있어요 (${KST.hm(c.start)}–${KST.hm(c.end)}). 시간을 조금 옮겨 주세요."
                }
                conflicts = map
                Toast.error("겹치는 일정이 있어서 저장하지 않았어요.")
                return null
            }
            // 웹 useConfirmBrainDump onSuccess: 캘린더 · 남은 개수 (임시 목표가 생겼으면 목표 목록도)
            val keys = mutableListOf(DataKey.timetable, DataKey.usage)
            if (res.tempGoals.isNotEmpty()) keys.add(DataKey.goals)
            Invalidation.invalidate(keys)

            val count = items.size
            result = res
            step = 2
            val pending = res.pending ?: emptyList()
            TA.track("task_add_goals.confirm_result",
                TA.resultCounts(res) + mapOf("result" to "success", "conflict_count" to res.conflicts.size))
            // 시간 미정이 있으면 시트가 닫히고 캘린더 점선으로 가서 요약은 안 보여요
            if (pending.isEmpty()) TA.track("task_add_result.view", TA.resultCounts(res))
            Toast.success(
                if (pending.isEmpty()) "${count}개 Task를 추가했어요."
                else "${count}개 Task를 추가했어요. 점선 자리를 확인해 주세요."
            )
            // 분석: AI로 정리해서 추가한 Task 개수
            AnalyticsService.logTaskAdded(mode = TaskAddMode.ai.rawValue, count = count)

            val placedDates = (res.placements + res.movedToOtherDays).map { it.date }
            val otherDates = (res.unscheduled + pending).mapNotNull { it.date }
            val allDates = (placedDates + otherDates).sorted()
            return TaskAddedInfo(
                taskIds = res.placements.map { it.taskId } +
                    res.movedToOtherDays.map { it.taskId } +
                    res.unscheduled.map { it.taskId } +
                    pending.map { it.taskId },
                date = allDates.firstOrNull(),
                pendingTaskIds = pending.map { it.taskId },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            TA.track("task_add_goals.confirm_result", mapOf("result" to "fail", "error_status" to TA.status(e), "temp_count" to used.size))
            Toast.error(errorMessage(e, fallback = "저장하지 못했어요. 다시 시도해 주세요."))
            return null
        } finally {
            confirming = false
        }
    }

    /** "더 추가하기" — 처음부터 (남은 개수도 다시 불러와요) */
    fun reset() {
        lines = emptyList()
        items = emptyList()
        tempGoals = emptyList()
        conflicts = emptyMap()
        result = null
        step = 0
        parsedItems = emptyList()
        if (!isPreview) Invalidation.invalidate(DataKey.usage)
    }

    companion object {
        /** AI 결과 → 화면에서 고칠 목록 (웹 toMapItems) */
        fun toMapItems(res: ParseResponse): MapResult {
            val items = res.items.map { i ->
                val existing = i.goal.type == GoalMatchType.EXISTING
                BrainDumpMapItem(
                    clientKey = i.clientKey,
                    title = i.title,
                    dateType = i.dateType,
                    scheduledDate = i.scheduledDate,
                    dueDate = i.dueDate,
                    startTime = BrainDumpConst.toHm(i.startTime),
                    endTime = BrainDumpConst.toHm(i.endTime),
                    estimatedMinutes = i.estimatedMinutes,
                    priority = i.priority ?: TaskPriority.C,
                    kind = i.kind ?: TaskKind.NORMAL,
                    goalCategoryId = if (existing) i.goal.goalCategoryId else null,
                    generalCategoryId = if (existing) i.goal.generalCategoryId else null,
                    milestoneId = if (existing) i.goal.milestoneId else null,
                    tempGoalKey = if (i.goal.type == GoalMatchType.TEMP) i.goal.tempKey else null,
                    reason = i.goal.reason,
                )
            }
            val used = items.mapNotNull { it.tempGoalKey }.toSet()
            val temps = res.tempGoals
                .filter { used.contains(it.tempKey) }
                .map { TempGoalInput(tempKey = it.tempKey, name = it.name, emoji = it.emoji, colorId = null) }
            return MapResult(items, temps)
        }
    }
}

// MARK: - 미리보기용 샘플

object TaskAddSample {
    fun goals(): List<GoalCategory> = listOf(
        GoalCategory(
            goalCategoryId = 1,
            goalCategoryName = "3개월 5kg 감량",
            goalCategoryStatus = GoalStatus.IN_PROGRESS,
            colorCode = "#3D5AFE",
            emoji = "💪",
            generalCategories = listOf(
                SubGoalInfo(generalCategoryId = 11, generalCategoryName = "운동", generalCategoryStatus = GoalStatus.IN_PROGRESS),
                SubGoalInfo(generalCategoryId = 12, generalCategoryName = "식단", generalCategoryStatus = GoalStatus.IN_PROGRESS),
            ),
            milestones = listOf(
                MilestoneInfo(milestoneId = 101, title = "1개월 차 2kg", dueDate = "2026-11-09", status = MilestoneStatus.PENDING),
            ),
        ),
        GoalCategory(
            goalCategoryId = 2,
            goalCategoryName = "정보처리기사 합격",
            goalCategoryStatus = GoalStatus.IN_PROGRESS,
            colorCode = "#12B76A",
            emoji = "📜",
        ),
    )

    fun items(today: String): List<BrainDumpMapItem> = listOf(
        BrainDumpMapItem(
            clientKey = "a", title = "헬스장 가기", dateType = DateType.ON, scheduledDate = today,
            startTime = "19:00", endTime = "20:00", estimatedMinutes = 60, priority = TaskPriority.B, kind = TaskKind.NORMAL,
            goalCategoryId = 1, generalCategoryId = 11, reason = "운동 목표와 관련 있어요",
        ),
        BrainDumpMapItem(
            clientKey = "b", title = "기출 3회분 풀기", dateType = DateType.DUE, dueDate = KST.addDays(today, 3),
            estimatedMinutes = 120, priority = TaskPriority.A, kind = TaskKind.NORMAL, goalCategoryId = 2,
        ),
        BrainDumpMapItem(
            clientKey = "c", title = "이삿짐 박스 사기", dateType = DateType.NONE, estimatedMinutes = 30,
            priority = TaskPriority.C, kind = TaskKind.NORMAL, tempGoalKey = "t1", reason = "관련 목표가 없어 임시 목표로 묶었어요",
        ),
        BrainDumpMapItem(
            clientKey = "d", title = "치과 예약", dateType = DateType.ON, scheduledDate = KST.addDays(today, 1),
            estimatedMinutes = 30, priority = TaskPriority.C, kind = TaskKind.APPOINTMENT,
        ),
    )

    fun result(today: String): ConfirmResponse {
        val tomorrow = KST.addDays(today, 1)
        return ConfirmResponse(
            created = true,
            placements = listOf(
                Placement(
                    taskId = 2000, clientKey = "a", title = "헬스장 가기", date = today,
                    start = "${today}T19:00:00", end = "${today}T20:00:00", reason = null, locked = true,
                ),
                Placement(
                    taskId = 2001, clientKey = "b", title = "기출 3회분 풀기", date = today,
                    start = "${today}T14:00:00", end = "${today}T16:00:00", reason = "마감 전 가장 여유 있는 오후 시간이에요", locked = false,
                ),
            ),
            movedToOtherDays = listOf(
                Placement(
                    taskId = 2002, clientKey = "c", title = "이삿짐 박스 사기", date = tomorrow,
                    start = "${tomorrow}T10:00:00", end = "${tomorrow}T10:30:00",
                    reason = "오늘은 빈 시간이 없어 내일 오전에 놓았어요", locked = false,
                ),
            ),
            unscheduled = listOf(BrainDumpTaskRef(taskId = 2003, clientKey = "d", title = "치과 예약", date = tomorrow)),
            pending = emptyList(),
            conflicts = emptyList(),
            tempGoals = listOf(CreatedTempGoal(tempKey = "t1", goalCategoryId = 9, name = "이사 준비", emoji = "📦", colorCode = null)),
        )
    }

    /** 미리보기용 ViewModel (서버를 부르지 않아요) */
    fun viewModel(mode: TaskAddMode = TaskAddMode.ai, step: Int = 0): TaskAddViewModel {
        val vm = TaskAddViewModel(mode = mode)
        vm.isPreview = true
        vm.quota = TaskQuota(premium = false, dailyLimit = 2, usedToday = 0, remaining = 2)
        vm.quotaLoading = false
        vm.goals = goals()
        vm.goalsLoading = false
        vm.lines = listOf(BrainDumpLine.make("저녁 8시 정기 회의", today = vm.today))
        if (step >= 1) {
            vm.items = items(vm.today)
            vm.tempGoals = listOf(TempGoalInput(tempKey = "t1", name = "이사 준비", emoji = "📦", colorId = null))
            vm.step = 1
        }
        if (step >= 2) {
            vm.result = result(vm.today)
            vm.step = 2
        }
        return vm
    }
}
