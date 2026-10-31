// feature/work/WorkRoadmapSection.kt
//
// iOS Features/Work/WorkRoadmapSection.swift
// 웹 WorkBoardView.tsx Roadmap · RoadmapItem · TopicTasks · RoadmapTask · NoTopicItem · TodayMarker
// 로드맵 — 주제를 마감 순서대로 세로 선 위에 놓아요. 끝난 주제는 채운 점, 다음 마감은 강조,
// 마감 지난 주제는 빨간 점, 마감 없는 주제는 점선. 점을 누르면 "끝냈어요".
// 점과 점 사이에는 그 주제의 남은 할 일을 보여줘요.
//
// 이 파일 아래쪽에 work 패키지 공용 도우미(점선 테두리 · 음수 여백)도 있어요.

package com.naleul.naleul.feature.work

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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun WorkRoadmapSection(
    board: WorkBoard,
    model: WorkBoardModel,
    onOpenTask: (WorkTask) -> Unit,
    /** 이 주제(null = 주제 없음)에 할 일 적기 */
    onAddTask: (Int?) -> Unit,
    onEditTopic: (WorkTopic) -> Unit,
    onAddTopic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val s = board.summary

    /** "오늘" 표시는 마감이 오늘 이후인 첫 주제 앞 (마감 없는 주제는 맨 뒤라 그 앞) */
    val todayAt: Int = run {
        val today = board.today
        val i = board.timeline.indexOfFirst { t ->
            val d = t.dueDate
            d == null || d >= today
        }
        if (i < 0) board.timeline.size else i
    }

    SectionCard(
        "로드맵",
        modifier = modifier,
        aside = {
            if (s.topicsTotal > 0) Text("세부 목표 ${s.topicsDone} / ${s.topicsTotal} 끝")
        },
    ) {
        Column(Modifier.fillMaxWidth()) {
            if (board.timeline.isEmpty()) {
                Text(
                    "포트폴리오 · 이력서 제출처럼 큰 단계를 세부 목표로 만들고, 세부 목표마다 마감을 정해 보세요.",
                    style = pt(14),
                    color = NC.ink3,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            } else {
                // 세로 로드맵
                val at = todayAt
                val noTopic = board.openTasks(topicId = null)
                Column(Modifier.fillMaxWidth()) {
                    board.timeline.forEachIndexed { index, t ->
                        if (index == at) WorkRoadmapTodayMarker()
                        key(t.id) {
                            WorkRoadmapTopicRow(
                                topic = t,
                                toggling = model.togglingTopicId != null && model.togglingTopicId == t.topicId,
                                onToggle = { scope.launch { model.toggleTopic(t) } },
                                onEdit = { onEditTopic(t) },
                            ) {
                                WorkRoadmapTopicTasks(
                                    open = board.openTasks(topicId = t.topicId),
                                    done = board.doneTasks(topicId = t.topicId),
                                    today = board.today,
                                    collapsed = t.isDone,
                                    onOpen = onOpenTask,
                                    onToggle = { task -> scope.launch { model.toggle(task, surface = "roadmap") } },
                                    onAdd = { onAddTask(t.topicId) },
                                    topicId = t.topicId,
                                    goalId = board.goalCategoryId,
                                    isPreview = model.isPreview,
                                )
                            }
                        }
                    }
                    if (at == board.timeline.size) WorkRoadmapTodayMarker()
                    if (noTopic.isNotEmpty()) {
                        WorkRoadmapNoTopicRow(count = noTopic.size) {
                            WorkRoadmapTopicTasks(
                                open = noTopic,
                                done = emptyList(),
                                today = board.today,
                                collapsed = false,
                                onOpen = onOpenTask,
                                onToggle = { task -> scope.launch { model.toggle(task, surface = "roadmap") } },
                                onAdd = { onAddTask(null) },
                                topicId = null,
                                goalId = board.goalCategoryId,
                                isPreview = model.isPreview,
                            )
                        }
                    }
                    WorkRoadmapFinishRow(done = board.summary.topicsDone, total = board.summary.topicsTotal)
                }
            }
            // 주제 추가 (점선 버튼)
            val lineStrong = NC.lineStrong
            Row(
                Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(Radius.xl))
                    .workDashedBorder(lineStrong, 1.dp, Radius.xl, dash = 4f, gap = 3f)
                    .clickable(role = Role.Button) { onAddTopic() },
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SfImage("plus", size = 12.dp, tint = NC.ink3)
                Text("세부 목표 추가", style = pt(13, PTWeight.semibold), color = NC.ink3)
            }
            if (board.timeline.isNotEmpty()) {
                Text(
                    "세부 목표 점을 누르면 '끝냈어요'로 표시돼요",
                    style = pt(11),
                    color = NC.ink4,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

// MARK: - 세로 선 한 칸 (점 + 내용, 점 아래로 선이 이어져요)

@Composable
private fun WorkRoadmapRail(
    showsLine: Boolean = true,
    node: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val lineColor = NC.line
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                if (showsLine) {
                    // 점(28) 아래부터 바닥까지 가운데(14)에 2 굵기 선
                    val x = 14.dp.toPx()
                    drawLine(lineColor, start = Offset(x, 28.dp.toPx()), end = Offset(x, size.height), strokeWidth = 2.dp.toPx())
                }
            },
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { node() }
        Column(Modifier.weight(1f).padding(top = 3.dp, bottom = 20.dp)) { content() }
    }
}

// MARK: - 주제 점

@Composable
fun WorkTopicNode(status: WorkTopicStatus, modifier: Modifier = Modifier) {
    val fill = when (status) {
        WorkTopicStatus.DONE -> NC.brand
        WorkTopicStatus.OVERDUE -> NC.dangerSoft
        else -> NC.surface
    }
    val stroke = when (status) {
        WorkTopicStatus.DONE, WorkTopicStatus.NEXT -> NC.brand
        WorkTopicStatus.OVERDUE -> NC.danger
        WorkTopicStatus.UPCOMING, WorkTopicStatus.NO_DUE -> NC.lineStrong
    }
    val halo = NC.brand.copy(alpha = 0.15f)
    Box(modifier.size(28.dp), contentAlignment = Alignment.Center) {
        if (status == WorkTopicStatus.NEXT) {
            // 다음 마감은 바깥에 옅은 고리
            Box(Modifier.requiredSize(36.dp).border(4.dp, halo, CircleShape))
        }
        Box(Modifier.matchParentSize().background(fill, CircleShape))
        if (status == WorkTopicStatus.NO_DUE) {
            WorkDashedCircle(color = stroke, lineWidth = 2.dp, dash = 3f, gap = 3f, modifier = Modifier.matchParentSize())
        } else {
            Box(Modifier.matchParentSize().border(2.dp, stroke, CircleShape))
        }
        if (status == WorkTopicStatus.DONE) {
            SfImage("checkmark", size = 12.dp, tint = Color.White)
        } else if (status == WorkTopicStatus.OVERDUE) {
            SfImage("exclamationmark.triangle", size = 11.dp, tint = NC.danger)
        }
    }
}

/** 점선 동그라미 테두리 (마감 없는 주제 · 주제 없음) */
@Composable
private fun WorkDashedCircle(color: Color, lineWidth: Dp, dash: Float, gap: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = lineWidth.toPx()
        drawCircle(
            color = color,
            radius = size.minDimension / 2 - w / 2,
            style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.dp.toPx(), gap.dp.toPx()))),
        )
    }
}

// MARK: - 주제 한 줄

@Composable
private fun WorkRoadmapTopicRow(
    topic: WorkTopic,
    toggling: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    tasks: @Composable () -> Unit,
) {
    val done = topic.isDone
    /** Task 를 모두 끝내서 "끝"인 주제는 되돌릴 게 없어요 (직접 끝냈다고 표시한 것만 다시 열기) */
    val autoDone = topic.isDone && !topic.completed
    val dueColor = when (topic.status) {
        WorkTopicStatus.OVERDUE -> NC.danger
        WorkTopicStatus.NEXT -> NC.brand
        else -> if ((topic.dDay ?: 99) <= 3) NC.warningInk else NC.ink3
    }
    val a11y = if (autoDone) "${topic.name}: Task를 모두 끝냈어요"
    else "${topic.name} ${if (topic.completed) "다시 열기" else "끝냈어요"}"

    WorkRoadmapRail(
        node = {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    // 자동으로 끝난 주제도 눌리게 두고(반응 없음) 앰플리튜드 topic_locked_click 만 보내요 (모델이 걸러요)
                    .clickable(enabled = !toggling, role = Role.Button) { onToggle() }
                    .semantics { contentDescription = a11y }
                    .alpha(if (toggling) 0.5f else 1f),
            ) {
                WorkTopicNode(status = topic.status)
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    topic.title,
                    style = pt(15, PTWeight.bold).copy(textDecoration = if (done) TextDecoration.LineThrough else null),
                    color = if (done) NC.ink3 else NC.ink,
                    modifier = Modifier.weight(1f),
                )
                NIconButton(
                    "pencil", size = 13.dp, color = NC.ink4, label = "${topic.name} 고치기",
                    modifier = Modifier.workNegativePadding(top = 6.dp, bottom = 6.dp, end = 6.dp),
                ) { onEdit() }
            }
            WorkRoadmapTopicMeta(topic = topic, done = done, dueColor = dueColor)
            if (topic.total > 0) {
                Row(
                    Modifier.padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ProgressBar(
                        value = (topic.progressPercent ?: 0).toDouble() / 100,
                        color = if (done) NC.success else NC.progressFill,
                        height = 6.dp,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${topic.done}/${topic.total}", style = ptMono(11), color = NC.ink3)
                }
            }
            tasks()
        }
    }
}

@Composable
private fun WorkRoadmapTopicMeta(topic: WorkTopic, done: Boolean, dueColor: Color) {
    val due: String? = if (done) null else WorkFormat.topicDue(topic.dDay)
    FlowLayout(spacing = 8.dp, lineSpacing = 2.dp) {
        val d = topic.dueDate
        Text(if (d != null) "~" + WorkFormat.shortDate(d) else "마감 없음", style = pt(12), color = NC.ink3)
        if (due != null) {
            Text(due, style = pt(12, PTWeight.semibold), color = dueColor)
        }
        if (done) {
            Text("끝", style = pt(12, PTWeight.semibold), color = NC.success)
        }
        if (!done && topic.total > 0) {
            Text("남은 ${topic.remaining}개", style = pt(12), color = NC.ink3)
        }
        val p = topic.remainingPerDay
        if (topic.status == WorkTopicStatus.NEXT && p != null && p > 0) {
            Text(WorkFormat.pace(p), style = pt(12), color = NC.ink3)
        }
    }
}

// MARK: - 주제 아래 할 일 (남은 일은 펼치고, 끝낸 일은 접어 둬요)

@Composable
private fun WorkRoadmapTopicTasks(
    open: List<WorkTask>,
    done: List<WorkTask>,
    today: String,
    collapsed: Boolean,
    onOpen: (WorkTask) -> Unit,
    onToggle: (WorkTask) -> Unit,
    onAdd: () -> Unit,
    /** 앰플리튜드용 (null = 주제 없음) */
    topicId: Int? = null,
    goalId: Int = 0,
    isPreview: Boolean = false,
) {
    var showOpen by remember { mutableStateOf(!collapsed) }
    var showDone by remember { mutableStateOf(false) }
    fun track(event: String, props: EventProps) {
        if (!isPreview) WK.track(event, goalId, props)
    }
    val shape = RoundedCornerShape(Radius.xl)

    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (open.isNotEmpty()) {
            if (showOpen) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(NC.subtle.copy(alpha = 0.5f), shape)
                        .border(1.dp, NC.borderSubtle, shape)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    for (t in open) {
                        key(t.taskId) {
                            WorkRoadmapTaskRow(task = t, today = today, onOpen = { onOpen(t) }, onToggle = { onToggle(t) })
                        }
                    }
                }
            } else {
                Text(
                    "남은 할 일 ${open.size}개 보기",
                    style = pt(12, PTWeight.semibold),
                    color = NC.ink3,
                    modifier = Modifier.clickable(role = Role.Button) {
                        track("work_roadmap.open_tasks_expand", mapOf("topic_id" to topicId, "item_count" to open.size))
                        showOpen = true
                    },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.height(24.dp).clickable(role = Role.Button) { onAdd() },
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SfImage("plus", size = 11.dp, tint = NC.ink3)
                Text("할 일", style = pt(12, PTWeight.semibold), color = NC.ink3)
            }
            if (done.isNotEmpty()) {
                Row(
                    Modifier.height(24.dp).clickable(role = Role.Button) {
                    track("work_roadmap.done_tasks_toggle", mapOf(
                        "topic_id" to topicId, "expanded" to !showDone, "item_count" to done.size,
                    ))
                    showDone = !showDone
                },
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("끝낸 일 ${done.size}", style = pt(12, PTWeight.semibold), color = NC.success)
                    SfImage("chevron.down", size = 10.dp, tint = NC.success, modifier = Modifier.rotate(if (showDone) 180f else 0f))
                }
            }
        }
        if (showDone && done.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                for (t in done) {
                    key(t.taskId) {
                        WorkRoadmapTaskRow(task = t, today = today, onOpen = { onOpen(t) }, onToggle = { onToggle(t) })
                    }
                }
            }
        }
    }
}

/** 로드맵 주제 아래 할 일 한 줄 — 체크하면 완료, 이름을 누르면 자세히 */
@Composable
private fun WorkRoadmapTaskRow(task: WorkTask, today: String, onOpen: () -> Unit, onToggle: () -> Unit) {
    val done = task.isDone
    val title = (task.emoji?.let { if (it.isEmpty()) "" else "$it " } ?: "") + task.taskName
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        WorkCheckCircle(done = done, size = 20.dp) { onToggle() }
        Column(
            Modifier.weight(1f).clickable(role = Role.Button) { onOpen() },
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                title,
                style = pt(14, if (done) PTWeight.regular else PTWeight.medium)
                    .copy(textDecoration = if (done) TextDecoration.LineThrough else null),
                color = if (done) NC.ink3 else NC.ink,
            )
            if (!done) {
                WorkTaskMetaRow(task = task, today = today, showTopic = false)
            }
        }
    }
}

/** "주제 없음" 할 일 — 로드맵 맨 끝(🏁 앞)에 점선 점으로 */
@Composable
private fun WorkRoadmapNoTopicRow(count: Int, tasks: @Composable () -> Unit) {
    val lineStrong = NC.lineStrong
    WorkRoadmapRail(
        node = {
            Box(Modifier.size(28.dp)) {
                Box(Modifier.matchParentSize().background(NC.surface, CircleShape))
                WorkDashedCircle(color = lineStrong, lineWidth = 2.dp, dash = 1f, gap = 3f, modifier = Modifier.matchParentSize())
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("세부 목표 없음", style = pt(15, PTWeight.bold), color = NC.ink2)
            Text("아직 세부 목표를 정하지 않은 할 일 ${count}개", style = pt(12), color = NC.ink3)
            tasks()
        }
    }
}

/** 오늘 — 점 + 가로 선 */
@Composable
private fun WorkRoadmapTodayMarker() {
    val lineColor = NC.line
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val x = 14.dp.toPx()
                drawLine(lineColor, start = Offset(x, 0f), end = Offset(x, size.height), strokeWidth = 2.dp.toPx())
            }
            .padding(bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(10.dp).background(NC.ink, CircleShape))
        }
        Text("오늘", style = pt(12, PTWeight.bold), color = NC.ink)
        Box(Modifier.weight(1f).height(1.dp).background(NC.line))
    }
}

/** 🏁 끝 점 */
@Composable
private fun WorkRoadmapFinishRow(done: Int, total: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(28.dp).background(NC.actionPrimary, CircleShape), contentAlignment = Alignment.Center) {
            SfImage("flag.fill", size = 12.dp, tint = NC.actionPrimaryFg)
        }
        Text(
            if (done == total) "모두 끝냈어요" else "${total - done}개 남았어요",
            style = pt(13, PTWeight.bold),
            color = NC.ink,
        )
    }
}

// MARK: - 공용: 체크 동그라미 · Task 메타 (보드에서도 써요)

@Composable
fun WorkCheckCircle(done: Boolean, size: Dp = 22.dp, modifier: Modifier = Modifier, action: () -> Unit) {
    val label = if (done) "완료 취소" else "완료"
    Box(
        modifier
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = action)
            .semantics { contentDescription = label }
            .padding(2.dp)
            .size(size)
            .background(if (done) NC.brand else Color.Transparent, CircleShape)
            .border(1.5.dp, if (done) NC.actionPrimary else NC.borderControl, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            SfImage("checkmark", size = size * 0.5f, tint = Color.White)
        }
    }
}

/** 주제 · 반복 · 진행 중 · 하는 날 · 시각 · 마감 */
@Composable
fun WorkTaskMetaRow(task: WorkTask, today: String, showTopic: Boolean = true, modifier: Modifier = Modifier) {
    val dueColor = if (task.overdue) NC.danger else if ((task.dueInDays ?: 99) <= 2) NC.warningInk else NC.ink3
    FlowLayout(spacing = 8.dp, lineSpacing = 2.dp, modifier = modifier) {
        val name = task.topicName
        if (showTopic && name != null) {
            Text(
                name,
                style = pt(11, PTWeight.medium),
                color = NC.ink2,
                modifier = Modifier
                    .background(NC.subtle, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
        if (task.routine) {
            Text("반복", style = pt(12, PTWeight.semibold), color = NC.textBrand)
        }
        if (task.status == WorkColumn.IN_PROGRESS) {
            Text("진행 중", style = pt(12, PTWeight.semibold), color = NC.textBrand)
        }
        val d = task.date
        if (d != null && d != today) {
            Text(WorkFormat.dayLabel(d, today = today), style = pt(12), color = NC.ink3)
        }
        val st = task.startTime
        if (st != null) {
            Text(st, style = pt(12), color = NC.ink3)
        }
        val due = WorkFormat.dueLabel(task)
        if (due != null) {
            Text(due, style = pt(12, PTWeight.semibold), color = dueColor)
        }
    }
}

// MARK: - work 패키지 공용 도우미

/**
 * 점선 테두리 (iOS .overlay(RoundedRectangle().stroke(style: StrokeStyle(dash: [dash, gap]))))
 * 그리기 블록 안에서는 NC 색을 못 읽어서 color 는 바깥에서 꺼내 넘겨요.
 */
fun Modifier.workDashedBorder(color: Color, width: Dp, radius: Dp, dash: Float, gap: Float): Modifier =
    this.drawBehind {
        val w = width.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(w / 2, w / 2),
            size = Size(size.width - w, size.height - w),
            cornerRadius = CornerRadius(radius.toPx()),
            style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.dp.toPx(), gap.dp.toPx()))),
        )
    }

/** iOS 의 음수 padding (.padding(.vertical, -6)) — 차지하는 자리만 줄이고 그림은 그대로 */
fun Modifier.workNegativePadding(top: Dp = 0.dp, bottom: Dp = 0.dp, start: Dp = 0.dp, end: Dp = 0.dp): Modifier =
    this.layout { measurable, constraints ->
        val t = top.roundToPx()
        val b = bottom.roundToPx()
        val s = start.roundToPx()
        val e = end.roundToPx()
        val loose = constraints.copy(
            minWidth = 0,
            minHeight = 0,
            maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + s + e else constraints.maxWidth,
            maxHeight = if (constraints.hasBoundedHeight) constraints.maxHeight + t + b else constraints.maxHeight,
        )
        val p = measurable.measure(loose)
        val w = (p.width - s - e).coerceAtLeast(0)
        val h = (p.height - t - b).coerceAtLeast(0)
        layout(w, h) { p.place(-s, -t) }
    }

// MARK: - 미리보기

@Preview(showBackground = true, heightDp = 1200)
@Composable
private fun WorkRoadmapSectionPreview() {
    NaleulTheme(dark = false) {
        val board = WorkSampleData.board
        val model = remember { WorkBoardModel.preview(board) }
        Column(Modifier.canvasBackground().verticalScroll(rememberScrollState()).padding(16.dp)) {
            WorkRoadmapSection(
                board = board, model = model,
                onOpenTask = {}, onAddTask = {}, onEditTopic = {}, onAddTopic = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WorkRoadmapSectionEmptyPreview() {
    NaleulTheme(dark = false) {
        val board = WorkSampleData.emptyBoard
        val model = remember { WorkBoardModel.preview(board) }
        Column(Modifier.canvasBackground().padding(16.dp)) {
            WorkRoadmapSection(
                board = board, model = model,
                onOpenTask = {}, onAddTask = {}, onEditTopic = {}, onAddTopic = {},
            )
        }
    }
}
