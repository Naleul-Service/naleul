// feature/work/WorkBoardTab.kt
//
// iOS Features/Work/WorkBoardTab.swift
// 웹 features/work/ui/WorkBoardView.tsx — 업무형 목표의 "보드" 탭 (모바일 한 줄 배치)
//  1. 다음 마감 · 진행 요약 (+ 공유)
//  2. 적기 — 할 일 · 한 일 · 회고 (시트)
//  3. 반복 루틴 (아래에 있으면 잘 안 보여서 위로)
//  4. 로드맵 — 주제를 마감 순서대로, 점 사이에 그 주제의 남은 할 일
//  5. 보드 (시작 전 · 진행 중 · 완료)
//  6. 업무 일지 · 7. 메모
// GoalDetailScreen 의 탭 영역(세로 스크롤 · 간격 14 · 좌우 16) 안에 들어가요.
// ⚠️ 바깥이 이미 세로 스크롤이라 여기서는 LazyColumn 을 안 써요 (Column + for).

package com.naleul.naleul.feature.work

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID

/** 적기 시트 열기 (탭 · 주제) */
data class WorkComposeRequest(
    var tab: WorkComposeTab,
    var topicId: Int? = null,
    val id: String = UUID.randomUUID().toString(),
) {
    companion object {}
}

/** 주제 시트 — 새로 만들기 / 고치기 (iOS enum WorkTopicSheetTarget { case create, case edit(WorkTopic) }) */
sealed class WorkTopicSheetTarget {
    object create : WorkTopicSheetTarget()
    data class edit(val topic: WorkTopic) : WorkTopicSheetTarget()

    val id: String
        get() = when (this) {
            is create -> "create"
            is edit -> "edit-${topic.id}"
        }

    companion object {}
}

/**
 * 업무형 목표의 보드 탭.
 * @param previewModel 미리보기 · 테스트용 (네트워크 안 씀). iOS init(goal:previewModel:)
 * @param detail 목표 상세 화면 모델 — 적기 시트의 "모두 보기"가 회고 탭으로 가게 (iOS 는 @Environment 로 받았어요)
 */
@Composable
fun WorkBoardTab(
    goal: GoalCategory,
    previewModel: WorkBoardModel? = null,
    detail: GoalDetailViewModel? = null,
    modifier: Modifier = Modifier,
    /** 목표 상세 맨 위 요약 카드와 같이 쓰는 모델 (스펙 12.1) — 주면 요약 · 불러오기는 그쪽이 맡아요 */
    sharedModel: WorkBoardModel? = null,
) {
    val ownModel = rememberScreenModel(goal.goalCategoryId) {
        previewModel ?: WorkBoardModel(goalId = goal.goalCategoryId, routineCount = goal.analyticsRoutineCount)
    }
    val model = sharedModel ?: ownModel
    val summaryHere = sharedModel == null
    var compose by remember { mutableStateOf<WorkComposeRequest?>(null) }
    var openedTask by remember { mutableStateOf<WorkTask?>(null) }
    /** 열린 Task 시트가 어디서 열렸는지 (roadmap · board) — 앰플리튜드 surface */
    var openedSurface by remember { mutableStateOf("board") }
    var topicTarget by remember { mutableStateOf<WorkTopicSheetTarget?>(null) }
    var sharing by remember { mutableStateOf(false) }

    val gid = goal.goalCategoryId

    fun track(event: String, props: EventProps = emptyMap()) {
        if (model.isPreview) return
        WK.track(event, gid, props)
    }
    fun openCompose(tab: WorkComposeTab, topicId: Int?, entry: String) {
        track("work_compose.open", mapOf("compose_tab" to tab.name, "entry_point" to entry, "topic_id" to topicId))
        compose = WorkComposeRequest(tab = tab, topicId = topicId)
    }
    fun openTask(t: WorkTask, surface: String) {
        track("work_task.open", WK.taskProps(t, surface) + mapOf("task_status" to t.status.name))
        openedSurface = surface
        openedTask = t
    }

    if (summaryHere) {
        ReloadOn(listOf(DataKey.work, DataKey.goals, DataKey.timetable), id = goal.goalCategoryId) { model.load() }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 1. 요약 (목표 상세에서는 맨 위 카드로 옮겼어요)
        if (summaryHere) when (val phase = model.phase) {
            is LoadPhase.Loading -> LoadingView(minHeight = 200.dp)
            is LoadPhase.Failed -> ErrorView(
                message = phase.message,
                retry = {
                    track("work_board.retry_click", mapOf("error_status" to model.loadErrorStatus))
                    model.load()
                },
                modifier = Modifier.card(),
            )
            is LoadPhase.Loaded -> WorkSummaryCard(
                board = phase.value,
                colorCode = goal.colorCode,
                onShare = {
                    track("work_board.share_click", mapOf("open_task_count" to phase.value.summary.remaining))
                    sharing = true
                },
                onPillTap = { kind -> track("work_board.pill_dead_click", mapOf("pill_kind" to kind)) },
            )
        }
        // 2. 적기 카드는 빼고 (스펙 12.2) — 떠 있는 '적기' 버튼이 같은 일을 해요
        // 3. 반복 루틴
        GoalRoutinesSection(goal = goal)
        // 4 · 5. 로드맵 · 보드
        val board = model.board
        if (board != null) {
            WorkRoadmapSection(
                board = board,
                model = model,
                onOpenTask = { t -> openTask(t, "roadmap") },
                onAddTask = { topicId ->
                    track("work_roadmap.add_task_click", mapOf("topic_id" to topicId, "compose_tab" to "task"))
                    openCompose(WorkComposeTab.task, topicId = topicId, entry = "roadmap")
                },
                onEditTopic = { t ->
                    track("work_roadmap.topic_edit_open", mapOf("topic_id" to t.topicId))
                    topicTarget = WorkTopicSheetTarget.edit(t)
                },
                onAddTopic = {
                    track("work_roadmap.add_topic_click", mapOf("topic_count" to board.summary.topicsTotal))
                    topicTarget = WorkTopicSheetTarget.create
                },
            )
            WorkKanbanSection(board = board, model = model, onOpenTask = { t -> openTask(t, "board") })
        }
        // 6. 업무 일지
        if (model.isPreview) {
            RecordJournalView(goalId = gid, goalName = goal.goalCategoryName, preview = RecordSampleData.journal)
        } else {
            RecordJournalView(goalId = gid, goalName = goal.goalCategoryName)
        }
        // 7. 메모
        if (goal.motive != null || goal.aiNote != null) {
            GoalNotesCard(goal = goal)
        }
    }

    // MARK: 시트들 (값이 있으면 떠요)

    compose?.let { req ->
        key(req.id) {
            WorkComposeSheet(
                goal = goal,
                initialTab = req.tab,
                topicId = req.topicId,
                board = model.board,
                isPreview = model.isPreview,
                detail = detail,
                onDismiss = { compose = null },
            )
        }
    }
    openedTask?.let { t ->
        key(t.taskId) {
            WorkTaskSheet(
                goalId = gid,
                task = t,
                topics = model.board?.topics ?: emptyList(),
                surface = openedSurface,
                isPreview = model.isPreview,
                onDismiss = { openedTask = null },
            )
        }
    }
    topicTarget?.let { target ->
        key(target.id) {
            when (target) {
                is WorkTopicSheetTarget.create ->
                    WorkTopicSheet(
                        goalId = gid, topic = null, topicCount = model.board?.summary?.topicsTotal ?: 0,
                        isPreview = model.isPreview, onDismiss = { topicTarget = null },
                    )
                is WorkTopicSheetTarget.edit ->
                    WorkTopicSheet(goalId = gid, topic = target.topic, isPreview = model.isPreview, onDismiss = { topicTarget = null })
            }
        }
    }
    if (sharing) {
        WorkShareSheet(
            goalId = gid,
            previewText = if (model.isPreview) WorkSampleData.shareText else null,
            onDismiss = { sharing = false },
        )
    }
}

// MARK: - 목표 상세 맨 위 요약 카드 (스펙 13.8: 깔끔하게 다시 — 12.1 배치를 바꿔요)

@Composable
fun WorkGoalSummaryCard(
    goal: GoalCategory,
    model: WorkBoardModel,
    modifier: Modifier = Modifier,
) {
    val gid = goal.goalCategoryId
    var sharing by remember { mutableStateOf(false) }
    fun track(event: String, props: EventProps = emptyMap()) {
        if (model.isPreview) return
        WK.track(event, gid, props)
    }
    ReloadOn(listOf(DataKey.work, DataKey.goals, DataKey.timetable), id = gid) { model.load() }
    val tone = goalTone(goal.colorCode)
    val board = (model.phase as? LoadPhase.Loaded)?.value
    val titleText = goal.emoji?.takeIf { it.isNotEmpty() }?.let { "$it ${goal.goalCategoryName}" } ?: goal.goalCategoryName
    val startYmd = board?.summary?.startDate ?: goal.goalCategoryStartDate
    Column(
        modifier
            .fillMaxWidth()
            .background(tone.bg, RoundedCornerShape(16.dp))
            .padding(20.dp),
    ) {
        // 1줄: 이모지 + 제목 (H3, 2줄까지) · 오른쪽 공유 아이콘 버튼
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                titleText, style = NType.h3, color = NC.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(top = 6.dp),
            )
            if (board != null) {
                NIconButton("square.and.arrow.up", size = 20.dp, color = NC.textSecondary, label = "공유하기") {
                    track("work_board.share_click", mapOf("open_task_count" to board.summary.remaining))
                    sharing = true
                }
            }
        }
        // 2줄: 상태 · 업무형 태그 + "2026.10.06 시작 · 마감은 주제마다"
        FlowLayout(spacing = 6.dp, modifier = Modifier.padding(top = 4.dp)) {
            NTag(
                goal.goalCategoryStatus.label,
                tone = if (goal.goalCategoryStatus == GoalStatus.COMPLETED) BadgeTone.success else BadgeTone.brand,
            )
            if (goal.isTemporary) NTag("임시 목표")
            NTag("업무형")
            val meta = listOfNotNull(startYmd?.let { "${it.replace("-", ".")} 시작" }, "마감은 세부 목표마다").joinToString(" · ")
            Box(Modifier.height(24.dp), contentAlignment = Alignment.CenterStart) {
                Text(meta, style = NType.captionMD, color = NC.textSecondary, maxLines = 1)
            }
        }
        Box(Modifier.padding(vertical = 16.dp).fillMaxWidth().height(1.dp).background(NC.borderSoft))
        when (val phase = model.phase) {
            is LoadPhase.Loading -> LoadingView(minHeight = 120.dp)
            is LoadPhase.Failed -> ErrorView(
                message = phase.message,
                retry = {
                    track("work_board.retry_click", mapOf("error_status" to model.loadErrorStatus))
                    model.load()
                },
            )
            is LoadPhase.Loaded -> WorkGoalSummaryBody(phase.value.summary, tone)
        }
    }
    if (sharing) {
        WorkShareSheet(
            goalId = gid,
            previewText = if (model.isPreview) WorkSampleData.shareText else null,
            onDismiss = { sharing = false },
        )
    }
}

/** 숫자 3칸 (다음 마감 · 진행 · 남은 Task) + Task 완료 막대 + 한 줄 설명 */
@Composable
private fun WorkGoalSummaryBody(s: WorkSummary, tone: GoalTone) {
    val next = s.nextTopic
    val nextDue = next?.dueDate
    val overdue = (next?.dDay ?: 0) < 0
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            WorkGoalStat("다음 마감", Modifier.weight(1f)) {
                if (next != null && nextDue != null) {
                    Text(next.name, style = NType.labelMD, color = NC.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(WorkFormat.dDay(next.dDay) ?: "", style = NType.h2, color = if (overdue) NC.statusErrorFg else tone.fg)
                } else {
                    Text("없음", style = NType.h2, color = NC.textTertiary)
                }
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(NC.borderSoft))
            WorkGoalStat("진행", Modifier.weight(1f)) {
                Text("${s.done}/${s.total}", style = NType.h2.copy(fontFeatureSettings = "tnum"), color = NC.textPrimary)
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(NC.borderSoft))
            WorkGoalStat("남은 Task", Modifier.weight(1f)) {
                Text("${s.remaining}개", style = NType.h2.copy(fontFeatureSettings = "tnum"), color = NC.textPrimary)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Task 완료", style = NType.labelMD, color = NC.textPrimary)
                Spacer(Modifier.weight(1f))
                Text("${s.progressPercent ?: 0}%", style = NType.labelMD, color = NC.textPrimary)
            }
            ProgressBar(value = (s.progressPercent ?: 0).toDouble() / 100, color = tone.accent, track = NC.progressTrack, height = 6.dp)
            val pace: String? = next?.remainingPerDay?.takeIf { it > 0 && (next.dDay ?: -1) >= 0 }?.let { "마감까지 ${WorkFormat.pace(it)}" }
            val caption = listOfNotNull(
                if (s.topicsTotal > 0) "끝낸 세부 목표 ${s.topicsDone}/${s.topicsTotal}" else null,
                nextDue?.let { WorkFormat.fullDate(it).replace(" (", "(") + "까지" },
                pace,
            ).joinToString(" · ")
            if (caption.isNotEmpty()) Text(caption, style = NType.captionMD, color = NC.textSecondary)
        }
    }
}

@Composable
private fun WorkGoalStat(label: String, modifier: Modifier = Modifier, value: @Composable () -> Unit) {
    Column(modifier.padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = NType.captionMD, color = NC.textSecondary)
        value()
    }
}

// MARK: - 1. 다음 마감 · 진행 요약 (웹 SummaryHero)

private enum class WorkPillTone { normal, danger, warning }

@Composable
private fun WorkSummaryCard(board: WorkBoard, onShare: () -> Unit, onPillTap: (String) -> Unit = {}, colorCode: String? = null) {
    val s = board.summary
    // NowCard 스타일 (Figma 3022:14866): 목표 색 tint 바탕 + textPrimary/Secondary (다크 모드에서도 맞게)
    val tone = goalTone(colorCode)
    Column(
        Modifier
            .fillMaxWidth()
            .background(tone.bg, RoundedCornerShape(Radius.card))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) { WorkSummaryNextBlock(s) }
            // 공유 버튼 (Secondary SM)
            NButton(
                "공유", variant = NButtonVariant.secondary, size = NButtonSize.sm, icon = "square.and.arrow.up",
                modifier = Modifier.semantics { contentDescription = "공유하기" },
            ) { onShare() }
        }
        WorkSummaryProgressBlock(s, accent = tone.accent)
        WorkSummaryPills(board, onPillTap)
    }
}

@Composable
private fun WorkSummaryNextBlock(s: WorkSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            SfImage("flag", size = 12.dp, tint = NC.textSecondary)
            Text("다음 마감", style = pt(13), color = NC.textSecondary)
        }
        val next = s.nextTopic
        val due = next?.dueDate
        if (next != null && due != null) {
            val overdue = (next.dDay ?: 0) < 0
            Text(
                next.title,
                style = NType.labelLG,
                color = NC.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                WorkFormat.dDay(next.dDay) ?: "",
                style = pt(28, PTWeight.bold),
                color = if (overdue) NC.statusErrorFg else NC.textPrimary,
            )
            Text(
                WorkFormat.fullDate(due) + "까지" + (if (next.remaining > 0) " · 남은 ${next.remaining}개" else ""),
                style = pt(14),
                color = NC.textSecondary,
            )
        } else {
            val emptyTitle = when {
                s.topicsTotal == 0 -> "아직 세부 목표가 없어요"
                s.topicsDone == s.topicsTotal -> "모든 세부 목표를 끝냈어요"
                else -> "마감 정한 세부 목표가 없어요"
            }
            val emptyHint = when {
                s.topicsTotal == 0 -> "로드맵에 세부 목표를 만들고 마감을 정해 보세요"
                s.topicsDone == s.topicsTotal -> "수고했어요! 다음 세부 목표를 이어서 만들어도 돼요"
                else -> "세부 목표에 마감을 정하면 하루에 몇 개씩 해야 하는지 알려드려요"
            }
            Text(emptyTitle, style = pt(24, PTWeight.bold), color = NC.textPrimary)
            Text(emptyHint, style = pt(13), color = NC.textSecondary)
        }
    }
}

@Composable
private fun WorkSummaryProgressBlock(s: WorkSummary, accent: Color = NC.progressFill) {
    val topicPercent: Int? = if (s.topicsTotal > 0)
        Math.round(s.topicsDone.toDouble() / s.topicsTotal.toDouble() * 100).toInt()
    else null
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("진행", style = pt(13), color = NC.textSecondary)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${s.done}", style = NType.h2.copy(fontFeatureSettings = "tnum"), color = NC.textPrimary, modifier = Modifier.alignByBaseline())
            Text(
                "/ ${s.total}개 완료",
                style = pt(17, PTWeight.semibold),
                color = NC.textSecondary,
                modifier = Modifier.alignByBaseline(),
            )
        }
        Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            WorkSummaryMeter(label = "Task 완료", value = s.progressPercent, right = null, accent = accent)
            WorkSummaryMeter(label = "끝낸 세부 목표", value = topicPercent, right = "${s.topicsDone} / ${s.topicsTotal}", accent = accent)
        }
    }
}

@Composable
private fun WorkSummaryPills(board: WorkBoard, onPillTap: (String) -> Unit) {
    val s = board.summary
    val next = s.nextTopic
    val overdueTopics = board.timeline.count { it.status == WorkTopicStatus.OVERDUE }
    val pace: String? = run {
        val n = next ?: return@run null
        val p = n.remainingPerDay ?: return@run null
        if (p <= 0 || (n.dDay ?: -1) < 0) return@run null
        "${n.name} 마감까지 ${WorkFormat.pace(p)}"
    }
    FlowLayout(spacing = 8.dp) {
        WorkSummaryPill("남은 Task ${s.remaining}개", WorkPillTone.normal) { onPillTap("remaining") }
        if (s.inProgress > 0) WorkSummaryPill("진행 중 ${s.inProgress}개", WorkPillTone.normal) { onPillTap("in_progress") }
        if (pace != null) WorkSummaryPill(pace, WorkPillTone.normal) { onPillTap("pace") }
        if (overdueTopics > 0) WorkSummaryPill("마감 지난 세부 목표 ${overdueTopics}개", WorkPillTone.danger) { onPillTap("overdue_topic") }
        if (s.overdue > 0) WorkSummaryPill("마감 지난 Task ${s.overdue}개", WorkPillTone.danger) { onPillTap("overdue_task") }
        if (s.dueThisWeek > 0) WorkSummaryPill("이번 주 마감 ${s.dueThisWeek}개", WorkPillTone.warning) { onPillTap("due_this_week") }
    }
}

@Composable
private fun WorkSummaryPill(text: String, tone: WorkPillTone, onTap: () -> Unit = {}) {
    val (bg, fg) = when (tone) {
        WorkPillTone.normal -> NC.bgSurface.copy(alpha = 0.7f) to NC.textSecondary
        WorkPillTone.danger -> NC.statusErrorBg to NC.statusErrorFg
        WorkPillTone.warning -> NC.statusWarningBg to NC.statusWarningFg
    }
    Box(
        Modifier
            .height(28.dp)
            .clip(CircleShape)
            .background(bg, CircleShape)
            // 지금은 눌러도 아무 동작이 없어요 — 앰플리튜드 pill_dead_click 만 (물결 표시 없이)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onTap() }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = pt(13, PTWeight.semibold), color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun WorkSummaryMeter(label: String, value: Int?, right: String?, accent: Color = NC.progressFill) {
    if (value == null) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = pt(12), color = NC.textSecondary)
            Spacer(Modifier.weight(1f))
            Text(right ?: "$value%", style = ptMono(12, PTWeight.semibold), color = NC.textPrimary)
        }
        ProgressBar(value = value.toDouble() / 100, color = accent, track = NC.bgSurface.copy(alpha = 0.7f), height = 6.dp)
    }
}

// MARK: - 2. 적기 (할 일 · 한 일 · 회고)

@Composable
private fun WorkComposeEntryRow(onPick: (WorkComposeTab) -> Unit) {
    val dashColor = NC.brand.copy(alpha = 0.35f)
    val shape = RoundedCornerShape(Radius.card)
    Column(
        Modifier
            .fillMaxWidth()
            .background(NC.surface, shape)
            .workDashedBorder(dashColor, 1.5.dp, 18.dp, dash = 5f, gap = 4f)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(36.dp).background(NC.brandSoft, CircleShape), contentAlignment = Alignment.Center) {
                SfImage("square.and.pencil", size = 15.dp, tint = NC.textBrand)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("적기", style = pt(15, PTWeight.bold), color = NC.ink)
                Text(
                    "할 일 · 실제로 한 일 · 회고를 한곳에서 적어요",
                    style = pt(12),
                    color = NC.ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (tab in WorkComposeTab.entries) {
                val tabShape = RoundedCornerShape(Radius.xl)
                Column(
                    Modifier
                        .weight(1f)
                        .height(56.dp)
                        .clip(tabShape)
                        .background(NC.brandSoft, tabShape)
                        .clickable(role = Role.Button) { onPick(tab) }
                        .semantics { contentDescription = "${tab.label} 적기" },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                ) {
                    SfImage(tab.icon, size = 15.dp, tint = NC.textBrand)
                    Text(tab.label, style = pt(13, PTWeight.semibold), color = NC.textBrand)
                }
            }
        }
    }
}

// MARK: - 미리보기
// (반복 루틴 · 업무 일지 · 메모는 goal · record 기능의 컴포저블이라 그쪽 미리보기 데이터를 써요)

@Preview(showBackground = true, heightDp = 2000)
@Composable
private fun WorkBoardTabPreview() {
    NaleulTheme(dark = false) {
        val m = remember { WorkBoardModel.preview(WorkSampleData.board) }
        Column(Modifier.canvasBackground().verticalScroll(rememberScrollState()).padding(16.dp)) {
            WorkBoardTab(goal = GoalPreviewData.recordGoal, previewModel = m)
        }
    }
}

@Preview(showBackground = true, heightDp = 2000)
@Composable
private fun WorkBoardTabOverduePreview() {
    NaleulTheme(dark = false) {
        val m = remember { WorkBoardModel.preview(WorkSampleData.overdueBoard) }
        Column(Modifier.canvasBackground().verticalScroll(rememberScrollState()).padding(16.dp)) {
            WorkBoardTab(goal = GoalPreviewData.recordGoal, previewModel = m)
        }
    }
}

@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun WorkBoardTabEmptyPreview() {
    NaleulTheme(dark = false) {
        val m = remember { WorkBoardModel.preview(WorkSampleData.emptyBoard) }
        Column(Modifier.canvasBackground().verticalScroll(rememberScrollState()).padding(16.dp)) {
            WorkBoardTab(goal = GoalPreviewData.recordGoal, previewModel = m)
        }
    }
}

/** 요약 카드만 (다른 기능 없이 볼 수 있게) */
@Preview(showBackground = true)
@Composable
private fun WorkSummaryCardPreview() {
    NaleulTheme(dark = false) {
        Column(Modifier.canvasBackground().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            WorkSummaryCard(board = WorkSampleData.board, onShare = {})
            WorkSummaryCard(board = WorkSampleData.emptyBoard, onShare = {})
            WorkComposeEntryRow(onPick = {})
        }
    }
}
