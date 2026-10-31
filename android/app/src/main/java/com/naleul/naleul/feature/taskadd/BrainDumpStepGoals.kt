// feature/taskadd/BrainDumpStepGoals.kt
//
// iOS Features/TaskAdd/BrainDumpStepGoals.swift
// 웹 원본: features/brain-dump/ui/StepGoals.tsx
//
// 2단계 · 목표 연결
//  - AI 결과를 목표별로 묶어서 보여줘요. 관련 목표가 없으면 임시 목표.
//  - "변경"으로 목표·날짜·시간·소요 시간·중요도를 고쳐요 (BrainDumpItemEditSheet).
//  - 모든 Task 가 목표에 연결돼야 "TimeBlock 만들기"를 누를 수 있어요 (백엔드 BRAIN_DUMP_GOAL_REQUIRED).

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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun BrainDumpStepGoals(
    items: List<BrainDumpMapItem>,
    tempGoals: List<TempGoalInput>,
    goals: List<GoalCategory>,
    aiFailed: Boolean,
    /** 오늘 AI 정리 횟수를 다 씀 (Free 하루 3번) */
    aiLimited: Boolean = false,
    /** clientKey → 겹친 일정 안내 (확정 시 conflicts) */
    conflicts: Map<String, String>,
    today: String,
    loading: Boolean,
    onChange: (List<BrainDumpMapItem>, List<TempGoalInput>) -> Unit,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf<BrainDumpMapItem?>(null) }

    // MARK: 목표별 묶음

    val groups: List<BrainDumpGoalGroup> = run {
        val out = mutableListOf<BrainDumpGoalGroup>()
        val index = mutableMapOf<String, Int>()
        for (it in items) {
            var g = BrainDumpGoalGroup(key = "none", title = "목표를 골라 주세요", emoji = null, color = "#B8BCC4", tempKey = null)
            val gid = it.goalCategoryId
            val tk = it.tempGoalKey
            if (gid != null) {
                val goal = goals.firstOrNull { g2 -> g2.goalCategoryId == gid }
                g = BrainDumpGoalGroup(
                    key = "g$gid", title = goal?.goalCategoryName ?: "목표", emoji = goal?.emoji,
                    color = hexOf(goal?.colorCode), tempKey = null,
                )
            } else if (tk != null) {
                val t = tempGoals.firstOrNull { t2 -> t2.tempKey == tk }
                g = BrainDumpGoalGroup(key = "t:$tk", title = t?.name ?: "임시 목표", emoji = t?.emoji, color = "#F59E0B", tempKey = tk)
            }
            val i = index[g.key]
            if (i != null) {
                out[i] = out[i].copy(items = out[i].items + it)
            } else {
                index[g.key] = out.size
                out.add(g.copy(items = listOf(it)))
            }
        }
        // 목표가 없는 묶음은 맨 위에 (먼저 고르도록)
        out.filter { it.key == "none" } + out.filter { it.key != "none" }
    }

    val missing = items.count { it.goalCategoryId == null && it.tempGoalKey == null }
    val blankTemp = tempGoals.any { it.name.trim().isEmpty() }
    val nextTempKey: String = run {
        val maxN = tempGoals.fold(0) { m, t -> maxOf(m, t.tempKey.drop(1).toIntOrNull() ?: 0) }
        "t${maxN + 1}"
    }

    // MARK: 동작

    fun renameTemp(tempKey: String, name: String) {
        val next = tempGoals.map { t -> if (t.tempKey != tempKey) t else t.copy(name = name.take(30)) }
        onChange(items, next)
    }

    /** 행에서 바로 중요도 고르기 — "변경" 시트를 열지 않아도 돼요 (웹 StepGoals selectPriority) */
    fun selectPriority(item: BrainDumpMapItem, p: TaskPriority) {
        TA.track("task_add_goals.priority_select", mapOf(
            "list_position" to items.indexOfFirst { x -> x.clientKey == item.clientKey }.coerceAtLeast(0),
            "priority" to p.rawValue,
            "prev_priority" to (item.priority ?: TaskPriority.fallback).rawValue,
        ))
        onChange(items.map { if (it.clientKey == item.clientKey) it.copy(priority = p) else it }, tempGoals)
    }

    /** 업무형 목표의 주제를 줄에서 바로 고르기 — 고른 주제는 업무 보드에서 그 주제 아래에 보여요 */
    fun selectTopic(item: BrainDumpMapItem, topicId: Int?, topicCount: Int) {
        TA.track("task_add_goals.topic_select", mapOf(
            "list_position" to items.indexOfFirst { x -> x.clientKey == item.clientKey }.coerceAtLeast(0),
            "has_topic" to (topicId != null),
            "topic_count" to topicCount,
        ))
        onChange(items.map { if (it.clientKey == item.clientKey) it.copy(workTopicId = topicId) else it }, tempGoals)
    }

    fun save(nextIn: BrainDumpMapItem, newTemp: TempGoalInput?) {
        val prev = items.firstOrNull { it.clientKey == nextIn.clientKey }
        // 목표가 바뀌면 주제는 그 목표의 것이 아니라 비워요
        val next = if (prev != null && prev.goalCategoryId != nextIn.goalCategoryId) nextIn.copy(workTopicId = null) else nextIn
        val list = items.map { if (it.clientKey == next.clientKey) next else it }
        val temps = if (newTemp != null) tempGoals + newTemp else tempGoals
        // 아무 Task 도 가리키지 않는 임시 목표는 만들지 않아요
        val used = list.mapNotNull { it.tempGoalKey }.toSet()
        onChange(list, temps.filter { used.contains(it.tempKey) })
        // 시트는 BrainDumpItemEditSheet 가 저장 뒤 스스로 닫아요 (닫히면 onDismiss → editing = null)
    }

    LaunchedEffect(Unit) {
        TA.track("task_add_goals.view", mapOf(
            "item_count" to items.size, "missing_goal_count" to missing,
            "temp_count" to items.mapNotNull { it.tempGoalKey }.toSet().size,
            "ai_failed" to aiFailed, "ai_limited" to aiLimited,
        ))
    }

    Column(
        modifier
            .fillMaxWidth()
            .card()
            .padding(20.dp),
    ) {
        Text("목표에 연결했어요", style = NType.h4, color = NC.ink)
        Text(
            "잘못 연결됐거나 날짜·시간이 다르면 \"변경\"을 눌러 고쳐 주세요. 관련 목표가 없는 일은 임시 목표로 묶었어요.",
            style = pt(14),
            color = NC.ink3,
            modifier = Modifier.padding(top = 4.dp),
        )

        if (aiFailed) {
            BrainDumpNotice(
                text = if (aiLimited)
                    "오늘 AI 정리 횟수를 다 써서 기본 정리로 나눠 두었어요. 목표는 \"변경\"에서 직접 골라 주세요. (내일 0시에 다시 채워져요 · Pro는 하루 30번)"
                else
                    "AI 정리가 잠시 안 돼서, 적은 내용만 나눠 두었어요. 목표는 \"변경\"에서 직접 골라 주세요.",
                danger = false,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        if (conflicts.isNotEmpty()) {
            BrainDumpNotice(
                text = "직접 정한 시간이 다른 일정과 겹쳐서 저장하지 않았어요. 빨갛게 표시된 Task의 시간을 바꿔 주세요.",
                danger = true,
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            for (g in groups) {
                key(g.key) {
                    GroupSection(
                        g = g,
                        tempGoals = tempGoals,
                        goals = goals,
                        conflicts = conflicts,
                        onRename = { tk, v -> renameTemp(tk, v) },
                        onPriority = { item, p -> selectPriority(item, p) },
                        onTopic = { item, id, count -> selectTopic(item, id, count) },
                        onEdit = { item ->
                            TA.track("task_add_goals.edit_open", mapOf(
                                "list_position" to items.indexOfFirst { x -> x.clientKey == item.clientKey }.coerceAtLeast(0),
                                "goal_match" to TA.goalMatch(item.goalCategoryId, item.tempGoalKey),
                                "has_conflict" to (conflicts[item.clientKey] != null),
                            ))
                            editing = item
                        },
                    )
                }
            }
        }

        // MARK: 아래 버튼
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (missing > 0) {
                Text("${missing}개 Task의 목표를 골라 주세요", style = pt(14), color = NC.danger)
            }
            if (blankTemp) {
                Text("임시 목표 이름을 입력해 주세요", style = pt(14), color = NC.danger)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NButton("이전", variant = NButtonVariant.secondary, enabled = !loading, action = onBack)
                NButton(
                    "TimeBlock 만들기", variant = NButtonVariant.ai, icon = "sparkles", loading = loading, enabled = !(missing > 0 || blankTemp || loading),
                    // 흐린 버튼을 누름 (목표 안 고른 Task · 빈 임시 목표 이름)
                    modifier = Modifier.disabledTap(!loading && (missing > 0 || blankTemp)) {
                        TA.track("task_add_goals.confirm_blocked", mapOf(
                            "disabled_reason" to if (missing > 0) "no_goal" else "blank_temp", "missing_goal_count" to missing,
                        ))
                    },
                    action = onConfirm,
                )
            }
        }
    }

    editing?.let { it ->
        BrainDumpItemEditSheet(
            item = it,
            goals = goals,
            tempGoals = tempGoals,
            today = today,
            nextTempKey = nextTempKey,
            canAddTemp = tempGoals.size < BrainDumpConst.maxTempGoals,
            onSave = { next, newTemp -> save(next, newTemp) },
            onDismiss = { editing = null },
        )
    }
}

// MARK: 묶음 하나

@Composable
private fun GroupSection(
    g: BrainDumpGoalGroup,
    tempGoals: List<TempGoalInput>,
    goals: List<GoalCategory>,
    conflicts: Map<String, String>,
    onRename: (String, String) -> Unit,
    onPriority: (BrainDumpMapItem, TaskPriority) -> Unit,
    onTopic: (BrainDumpMapItem, Int?, Int) -> Unit,
    onEdit: (BrainDumpMapItem) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(colorHex(g.color), CircleShape)
            )
            val tk = g.tempKey
            if (tk != null) {
                Row(
                    Modifier.weight(1f, fill = false),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val e = g.emoji
                    if (!e.isNullOrEmpty()) Text(e, style = pt(15))
                    val name = tempGoals.firstOrNull { it.tempKey == tk }?.name ?: ""
                    // 이름을 고치고 벗어날 때 temp_rename
                    var renameStart by remember { mutableStateOf<String?>(null) }
                    val ink = NC.ink
                    BasicTextField(
                        value = name,
                        onValueChange = { v -> onRename(tk, v) },
                        singleLine = true,
                        textStyle = pt(15, PTWeight.bold).copy(color = ink),
                        cursorBrush = SolidColor(NC.brand),
                        modifier = Modifier
                            .widthIn(min = 60.dp, max = 180.dp)
                            .width(IntrinsicSize.Min)
                            .onFocusChanged { f ->
                                if (f.isFocused) {
                                    renameStart = name
                                } else if (renameStart != null) {
                                    if (name != renameStart) TA.track("task_add_goals.temp_rename", mapOf("text_length" to name.trim().length))
                                    renameStart = null
                                }
                            }
                            .semantics { contentDescription = "임시 목표 이름" },
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (name.isEmpty()) Text("임시 목표 이름", style = pt(15, PTWeight.bold), color = NC.ink4, maxLines = 1)
                                inner()
                            }
                        },
                    )
                    SfImage("pencil", size = 12.dp, tint = NC.ink4)
                    NBadge("임시 목표", tone = BadgeTone.warning)
                }
            } else {
                Text(
                    (g.emoji?.let { "$it " } ?: "") + g.title,
                    style = pt(15, PTWeight.bold),
                    color = if (g.key == "none") NC.danger else NC.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            Text("${g.items.size}개", style = pt(12), color = NC.ink4)
            Spacer(Modifier.weight(1f))
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (it in g.items) {
                key(it.clientKey) {
                    BrainDumpItemRow(
                        item = it,
                        goals = goals,
                        conflict = conflicts[it.clientKey],
                        onPriority = { p -> onPriority(it, p) },
                        onTopic = { id, count -> onTopic(it, id, count) },
                    ) { onEdit(it) }
                }
            }
        }
    }
}

/** 목표별 묶음 (파일 안에서만 써요) */
private data class BrainDumpGoalGroup(
    val key: String,
    val title: String,
    val emoji: String?,
    /** "#RRGGBB" */
    val color: String,
    /** 임시 목표 묶음이면 그 tempKey */
    val tempKey: String?,
    val items: List<BrainDumpMapItem> = emptyList(),
) {
    val id: String get() = key

    companion object {}
}

/** 노란 · 빨간 안내 상자 */
@Composable
private fun BrainDumpNotice(text: String, danger: Boolean, modifier: Modifier = Modifier) {
    val fg = if (danger) NC.danger else NC.warningInk
    Row(
        modifier
            .fillMaxWidth()
            .background(if (danger) NC.dangerSoft else NC.warningSoft, RoundedCornerShape(Radius.xl))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        SfImage("exclamationmark.circle", size = 14.dp, tint = fg, modifier = Modifier.padding(top = 1.dp))
        Text(text, style = pt(14), color = fg, modifier = Modifier.weight(1f))
    }
}

// MARK: - Task 한 줄

@Composable
private fun BrainDumpItemRow(
    item: BrainDumpMapItem,
    goals: List<GoalCategory>,
    conflict: String?,
    /** 행에서 바로 중요도 고르기 (2026-10) */
    onPriority: (TaskPriority) -> Unit,
    /** 업무형 목표면 주제를 바로 고르기 (topicId, 고를 수 있는 주제 수) */
    onTopic: (Int?, Int) -> Unit,
    onEdit: () -> Unit,
) {
    var pickPriority by remember { mutableStateOf(false) }
    val priority = item.priority ?: TaskPriority.fallback
    val goal: GoalCategory? = item.goalCategoryId?.let { gid -> goals.firstOrNull { it.goalCategoryId == gid } }
    val sub: SubGoalInfo? = goal?.generalCategories?.firstOrNull { it.generalCategoryId == item.generalCategoryId }
    val milestone: MilestoneInfo? = goal?.milestones?.firstOrNull { it.milestoneId == item.milestoneId }
    val dateLabel: String = when {
        item.dateType == DateType.DUE && item.dueDate != null -> "${KST.formatMonthDay(item.dueDate)}까지"
        item.dateType == DateType.ON && item.scheduledDate != null -> KST.formatMonthDay(item.scheduledDate)
        else -> "날짜 없음"
    }
    val timeLabel: String? = item.startTime?.let { s ->
        val end = item.endTime?.let { "–${it.take(5)}" } ?: ""
        "${s.take(5)}$end"
    }

    val shape = RoundedCornerShape(Radius.xxl)
    val metaColor = NC.ink3
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (conflict != null) NC.dangerSoft.copy(alpha = 0.4f) else NC.surface, shape)
            .border(1.dp, if (conflict != null) NC.danger else NC.borderSubtle, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.title, style = pt(15, PTWeight.semibold), color = NC.ink)
            FlowLayout(spacing = 10.dp, lineSpacing = 4.dp, modifier = Modifier.padding(top = 4.dp)) {
                ItemMeta("calendar.badge.clock", dateLabel)
                if (timeLabel != null) ItemMeta("clock", timeLabel)
                Text("예상 ${KST.formatDuration(item.estimatedMinutes ?: 30)}", style = pt(12), color = metaColor)
                PriorityToggle(priority = priority, open = pickPriority) { pickPriority = !pickPriority }
                if (item.kind == TaskKind.APPOINTMENT) NBadge("약속", tone = BadgeTone.warning)
                if (sub != null) Text("› ${sub.generalCategoryName}", style = pt(12), color = metaColor)
                if (milestone != null) Text("🏁 ${milestone.title}", style = pt(12), color = metaColor)
            }
            if (goal != null && goal.isWorkGoal) {
                BrainDumpWorkTopicPicker(
                    goalId = goal.goalCategoryId,
                    value = item.workTopicId,
                    modifier = Modifier.padding(top = 8.dp),
                    onChange = onTopic,
                )
            }
            if (pickPriority) {
                PriorityChips(value = priority, modifier = Modifier.padding(top = 8.dp)) { p ->
                    pickPriority = false
                    if (p != priority) onPriority(p)
                }
            }

            val reason = item.reason
            if (!reason.isNullOrEmpty() && conflict == null) {
                Row(
                    Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    AIBadge(AIBadgeSize.sm)
                    Text(reason, style = pt(12), color = NC.ink3)
                }
            }
            if (conflict != null) {
                Text(conflict, style = pt(12, PTWeight.semibold), color = NC.danger, modifier = Modifier.padding(top = 6.dp))
            }
        }
        NButton(
            "변경",
            variant = NButtonVariant.secondary,
            size = NButtonSize.sm,
            modifier = Modifier.semantics { contentDescription = "${item.title} 변경" },
            action = onEdit,
        )
    }
}

@Composable
private fun ItemMeta(icon: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        SfImage(icon, size = 11.dp, tint = NC.ink3)
        Text(text, style = pt(12), color = NC.ink3)
    }
}

@Preview(showBackground = true, heightDp = 1000)
@Composable
private fun BrainDumpStepGoalsPreview() {
    NaleulTheme(dark = false) {
        val today = KST.today()
        Column(
            Modifier
                .fillMaxSize()
                .canvasBackground()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            BrainDumpStepGoals(
                items = TaskAddSample.items(today),
                tempGoals = listOf(TempGoalInput(tempKey = "t1", name = "이사 준비", emoji = "📦", colorId = null)),
                goals = TaskAddSample.goals(),
                aiFailed = false,
                conflicts = mapOf("a" to "이 시간엔 이미 \"팀 회의\"이(가) 겹쳐 있어요 (19:00–20:00). 시간을 조금 옮겨 주세요."),
                today = today,
                loading = false,
                onChange = { _, _ -> },
                onBack = {},
                onConfirm = {},
            )
        }
    }
}

/** 2단계 행의 중요도 라벨 — 누르면 A~E 칩이 펼쳐져요 */
@Composable
private fun PriorityToggle(priority: TaskPriority, open: Boolean, onClick: () -> Unit) {
    val top = priority.isTop
    Row(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "중요도 ${priority.rawValue} ${priority.label} 바꾸기" },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PriorityMark(priority = priority, color = NC.textBrand, size = 14.dp)
        Text(
            BrainDumpConst.priorityLabel(priority),
            style = pt(12, if (top) PTWeight.semibold else PTWeight.regular),
            color = if (top) NC.textBrand else NC.ink3,
        )
        SfImage("chevron.down", size = 9.dp, tint = NC.ink3, modifier = Modifier.rotate(if (open) 180f else 0f))
    }
}

/** 업무형 목표의 주제 고르기 (웹 StepGoals WorkTopicPicker) — 끝내지 않은 주제만, 비우면 "주제 없음" */
@Composable
private fun BrainDumpWorkTopicPicker(
    goalId: Int,
    value: Int?,
    modifier: Modifier = Modifier,
    onChange: (Int?, Int) -> Unit,
) {
    var topics by remember(goalId) { mutableStateOf<List<WorkTopic>?>(null) }
    LaunchedEffect(goalId) {
        topics = try {
            WorkAPI.board(goalId = goalId)?.topics ?: emptyList()
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (_: Exception) {
            emptyList()
        }
    }
    val choices = (topics ?: return).filter { it.topicId != null && (!it.completed || it.topicId == value) }
    if (choices.isEmpty()) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            SfImage("briefcase", size = 12.dp, tint = NC.ink3)
            Text("세부 목표 없음 · 목표 상세의 로드맵에서 세부 목표를 만들면 여기서 고를 수 있어요", style = pt(12), color = NC.ink3)
        }
        return
    }
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SfImage("briefcase", size = 12.dp, tint = NC.ink3)
        Text("세부 목표", style = pt(12), color = NC.ink3)
        NMenuPicker(
            selection = value,
            options = listOf<Pair<Int?, String>>(null to "선택 안 함 (기타 할 일)") +
                choices.map { t -> t.topicId to listOfNotNull(t.emoji, t.name).joinToString(" ") },
            onSelect = { id -> onChange(id, choices.size) },
            placeholder = "선택 안 함 (기타 할 일)",
            modifier = Modifier.widthIn(max = 220.dp).semantics { contentDescription = "세부 목표" },
        )
    }
}
