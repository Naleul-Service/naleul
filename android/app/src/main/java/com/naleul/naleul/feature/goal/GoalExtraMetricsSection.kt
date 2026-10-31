// feature/goal/GoalExtraMetricsSection.kt
//
// iOS Features/Goal/GoalExtraMetricsSection.swift
// 웹 features/goal/ui/progress/ExtraMetricsSection.tsx — 목표형 추가 그래프 (V52).
// 메인 수치("지금 어디쯤?") 말고 같이 보고 싶은 수치를 더 그려요 (최대 5개).
// 예) SQLD: 메인 "모의고사 점수 40 → 70점" + 추가 "기출 회독 0 → 5회"
// 그래프마다 오늘 기대값("계획대로라면 오늘까지 2회")을 보여줘서 오늘 어디까지 해야 하는지 바로 알 수 있어요.
//
// 응답이 진행 데이터(GoalProgress) 전체라 vm.progress 에 그대로 넣어 바로 다시 그려요.

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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlin.math.roundToInt

// MARK: - API

object GoalExtraMetricAPI {
    /** 최대 개수 */
    const val maxCount = 5

    suspend fun create(goalId: Int, name: String, unit: String?, startValue: Double, targetValue: Double): GoalProgress? =
        API.post<GoalProgress>(
            "/v1/goal-categories/$goalId/metrics",
            jsonCompact(
                "name" to name,
                "unit" to unit,
                "startValue" to startValue,
                "targetValue" to targetValue,
            ),
        )

    suspend fun update(
        goalId: Int,
        metricId: Int,
        name: String?,
        unit: String?,
        startValue: Double?,
        targetValue: Double?,
    ): GoalProgress? =
        API.patch<GoalProgress>(
            "/v1/goal-categories/$goalId/metrics/$metricId",
            jsonCompact(
                "name" to name,
                "unit" to unit,
                "startValue" to startValue,
                "targetValue" to targetValue,
            ),
        )

    suspend fun delete(goalId: Int, metricId: Int): GoalProgress? =
        API.delete<GoalProgress>("/v1/goal-categories/$goalId/metrics/$metricId")

    /** 기록 (date 가 null 이면 오늘). 같은 날 다시 보내면 고쳐요 */
    suspend fun record(goalId: Int, metricId: Int, date: String? = null, value: Double, memo: String? = null): GoalProgress? =
        API.put<GoalProgress>(
            "/v1/goal-categories/$goalId/metrics/$metricId/logs",
            jsonCompact(
                "date" to date,
                "value" to value,
                "memo" to memo,
            ),
        )

    suspend fun deleteLog(goalId: Int, metricId: Int, date: String): GoalProgress? =
        API.delete<GoalProgress>("/v1/goal-categories/$goalId/metrics/$metricId/logs/$date")
}

/** 그래프 시트: 새로 만들기 · 고치기 */
sealed class GoalExtraMetricFormTarget {
    object create : GoalExtraMetricFormTarget()
    data class edit(override val metric: ExtraMetric) : GoalExtraMetricFormTarget()

    val id: String
        get() = when (this) {
            is create -> "new"
            is edit -> "metric:${metric.metricId}"
        }

    open val metric: ExtraMetric? get() = null

    companion object {}
}

// MARK: - 섹션

@Composable
fun GoalExtraMetricsSection(
    goal: GoalCategory,
    vm: GoalDetailViewModel = LocalGoalDetailViewModel.current!!,
    modifier: Modifier = Modifier,
) {
    var form by remember { mutableStateOf<GoalExtraMetricFormTarget?>(null) }
    var deleteTarget by remember { mutableStateOf<ExtraMetric?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 임시 목표는 기간 · 계획선이 없어서 숨겨요
    val p = vm.progress
    if (goal.isTemporary || p == null) return

    val metrics = (p.extraMetrics ?: emptyList()).sortedBy { it.sortOrder }
    val readOnly = goal.goalCategoryStatus == GoalStatus.COMPLETED

    SectionCard(
        title = "함께 보는 그래프",
        modifier = modifier,
        aside = { if (metrics.isNotEmpty()) Text("${metrics.size} / ${GoalExtraMetricAPI.maxCount}", style = pt(13), color = NC.ink3) },
    ) {
        if (metrics.isEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                SfImage("chart.xyaxis.line", size = 14.dp, tint = NC.ink3, modifier = Modifier.padding(top = 2.dp))
                Text(
                    "메인 수치 말고도 매일 보고 싶은 숫자가 있다면 그래프를 더 만들어 보세요. 예) 기출 회독 0 → 5회, 공부 시간 0 → 60시간",
                    style = pt(13).copy(lineHeight = 19.sp),
                    color = NC.ink3,
                )
            }
        }
        Column {
            metrics.forEachIndexed { i, metric ->
                if (i > 0) {
                    Box(Modifier.padding(vertical = 20.dp).fillMaxWidth().height(1.dp).background(NC.line))
                }
                key(metric.metricId) {
                    GoalExtraMetricCard(
                        goalId = goal.goalCategoryId,
                        p = p,
                        metric = metric,
                        readOnly = readOnly,
                        vm = vm,
                        onEdit = {
                            vm.track("goal_detail.extra_metric_edit_click", mapOf("metric_id" to metric.metricId))
                            form = GoalExtraMetricFormTarget.edit(metric)
                        },
                        onDelete = {
                            vm.track("goal_detail.extra_metric_delete_click", mapOf(
                                "metric_id" to metric.metricId, "log_count" to metric.progress.logs.size,
                            ))
                            deleteTarget = metric
                            confirmDelete = true
                        },
                    )
                }
            }
        }
        if (!readOnly && metrics.size < GoalExtraMetricAPI.maxCount) {
            GoalAddButton("그래프 추가") {
                vm.track("goal_detail.extra_metric_add_click", mapOf("metric_count" to metrics.size))
                form = GoalExtraMetricFormTarget.create
            }
        }
    }

    form?.let { target ->
        key(target.id) {
            GoalExtraMetricFormSheet(
                goalId = goal.goalCategoryId,
                metric = target.metric,
                onSaved = { vm.progress = it },
                onDone = { form = null },
            )
        }
    }

    ConfirmDialog(
        isPresented = confirmDelete,
        onDismiss = { confirmDelete = false },
        title = "'${deleteTarget?.progress?.name ?: ""}' 그래프를 지울까요?",
        message = "이 그래프에 남긴 기록도 함께 사라져요.",
        confirmLabel = "지우기",
        tone = ConfirmTone.danger,
        onCancel = {
            vm.track("goal_detail.extra_metric_delete_cancel", mapOf("metric_id" to deleteTarget?.metricId))
            deleteTarget = null
        },
    ) {
        val m = deleteTarget
        if (m != null) {
            vm.track("goal_detail.extra_metric_delete_confirm", mapOf("metric_id" to m.metricId, "log_count" to m.progress.logs.size))
            scope.launch {
                val goalId = goal.goalCategoryId
                val metricId = m.metricId
                val next = Mutation.run(successMessage = "그래프를 지웠어요.") {
                    GoalExtraMetricAPI.delete(goalId = goalId, metricId = metricId)
                }
                vm.track("goal_detail.extra_metric_result", GD.result() + mapOf("metric_action" to "delete"))
                if (next != null) vm.progress = next
                deleteTarget = null
            }
        }
    }
}

// MARK: - 그래프 하나

@Composable
private fun GoalExtraMetricCard(
    goalId: Int,
    p: GoalProgress,
    metric: ExtraMetric,
    readOnly: Boolean,
    vm: GoalDetailViewModel,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val L = GoalDetailLogic
    val m = metric.progress
    var value by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }
    var removingDate by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current

    val num: Double? = value.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()?.takeIf { it.isFinite() }
    val unitText = m.unit?.takeIf { it.isNotEmpty() }
    val titleText = (m.name ?: "수치").let { name -> if (unitText != null) "$name ($unitText)" else name }

    fun record() {
        val v = num ?: return
        if (saving) return
        val metricId = metric.metricId
        vm.track("goal_detail.extra_metric_record_submit", mapOf("metric_id" to metricId, "is_overwrite" to (m.lastLoggedDate == p.today)))
        saving = true
        scope.launch {
            val r = Mutation.run(successMessage = "기록했어요.") {
                // 응답이 비어도 성공인지 알 수 있게 Pair 로 감싸요
                Pair(GoalExtraMetricAPI.record(goalId = goalId, metricId = metricId, value = v), true)
            }
            saving = false
            vm.track("goal_detail.extra_metric_result", GD.result() + mapOf("metric_action" to "record"))
            if (r != null) {
                r.first?.let { vm.progress = it }
                value = ""
                focusManager.clearFocus()
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    fun removeLog(date: String) {
        if (removingDate != null) return
        val metricId = metric.metricId
        vm.track("goal_detail.extra_metric_log_delete_click", mapOf("metric_id" to metricId, "days_ago" to GD.daysAgo(date)))
        removingDate = date
        scope.launch {
            val next = Mutation.run(successMessage = "기록을 지웠어요.") {
                GoalExtraMetricAPI.deleteLog(goalId = goalId, metricId = metricId, date = date)
            }
            removingDate = null
            vm.track("goal_detail.extra_metric_result", GD.result() + mapOf("metric_action" to "log_delete"))
            if (next != null) vm.progress = next
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // ── 이름 · 상태 · 메뉴 ──
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                titleText,
                style = pt(15, PTWeight.bold),
                color = NC.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            GoalProgressStatusChip(status = m.status)
            if (!readOnly) {
                NMenu(
                    items = listOf(
                        MenuItem("그래프 수정", "pencil") { onEdit() },
                        MenuItem("그래프 삭제", "trash", destructive = true) { onDelete() },
                    ),
                    modifier = Modifier.semantics { contentDescription = "${m.name ?: "그래프"} 더보기" },
                ) {
                    Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                        SfImage("ellipsis", size = 15.dp, tint = NC.ink3)
                    }
                }
            }
        }

        // ── 지금 / 목표 막대 ──
        val percent = m.progressPercent ?: (goalExtraPos(m, m.currentValue) * 100)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                Text(L.withUnit(m.currentValue, m.unit), style = ptMono(20, PTWeight.bold), color = NC.ink)
                Text(
                    "/ 목표 ${L.withUnit(m.targetValue, m.unit)}",
                    style = pt(13),
                    color = NC.ink3,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
                Spacer(Modifier.weight(1f))
                Text("${percent.coerceIn(0.0, 999.0).roundToInt()}%", style = ptMono(13, PTWeight.semibold), color = NC.ink2)
            }
            ProgressBar(
                value = goalExtraPos(m, m.currentValue),
                color = if (m.status == ProgressStatus.ACHIEVED) NC.success else NC.progressFill,
                height = 8.dp,
            )
            Text("시작 ${L.withUnit(m.startValue, m.unit)}", style = pt(12), color = NC.ink4)
        }

        // ── "계획대로라면 오늘까지 2회 · 지금 1회" / 한 줄 해석 ──
        val e = m.expectedValue
        if (e != null && m.status != ProgressStatus.ACHIEVED) {
            val rounded = Math.round(e * 10) / 10.0
            val brand = NC.brand
            val ink2 = NC.ink2
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = brand)) { append("계획대로라면 오늘까지 ${L.withUnit(rounded, m.unit)}") }
                    if (m.currentValue != e) {
                        withStyle(SpanStyle(color = ink2, fontWeight = FontWeight.Medium)) {
                            append(" · 지금 ${L.withUnit(m.currentValue, m.unit)}")
                        }
                    }
                },
                style = pt(13, PTWeight.semibold),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NC.brandSoft, RoundedCornerShape(Radius.xl))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        } else {
            val lines = goalExtraInsightLines(p, m)
            if (lines.isNotEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(NC.subtle, RoundedCornerShape(Radius.xl))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    for (line in lines) Text(line, style = pt(13).copy(lineHeight = 19.sp), color = NC.ink)
                }
            }
        }

        // ── 오늘 기록 ──
        if (!readOnly) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("오늘 기록", style = pt(12, PTWeight.medium), color = NC.ink3)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NTextField(
                            value = value,
                            onValueChange = { value = it },
                            placeholder = if (m.loggedToday) "오늘 기록을 고쳐요" else "오늘 ${m.name ?: "값"}",
                            keyboardType = KeyboardType.Decimal,
                            textStyle = pt(15, PTWeight.semibold),
                            onSubmit = { record() },
                            modifier = Modifier.weight(1f),
                        )
                        if (unitText != null) Text(unitText, style = pt(13), color = NC.ink3)
                    }
                    NButton("저장", size = NButtonSize.sm, loading = saving, enabled = num != null) { record() }
                }
            }
        }

        // ── 그래프 ──
        if (m.planLine.size > 1 || m.logs.isNotEmpty()) {
            Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GoalMetricChart(p = p, m = m, height = 180, onInteract = { vm.trackChartInteract("extra_metric", metric.metricId) })
                GoalChartLegend(items = listOf("계획" to GoalChartSeries.Kind.plan, "내 기록" to GoalChartSeries.Kind.actual))
            }
        }

        // ── 지난 기록 ──
        if (m.logs.isNotEmpty()) {
            val rows = m.logs.reversed()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (showLogs) "기록 접기" else "기록 ${rows.size}개 보기",
                    style = pt(12, PTWeight.semibold),
                    color = NC.ink3,
                    modifier = Modifier.clickable(role = Role.Button) {
                        showLogs = !showLogs
                        vm.track("goal_detail.extra_metric_logs_toggle", mapOf("metric_id" to metric.metricId, "expanded" to showLogs))
                    },
                )
                if (showLogs) {
                    Column {
                        rows.forEachIndexed { i, l ->
                            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(NC.line))
                            Row(
                                Modifier.padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(L.md(l.date), style = pt(13), color = NC.ink3, modifier = Modifier.width(70.dp))
                                Text(L.withUnit(l.value, m.unit), style = ptMono(13, PTWeight.bold), color = NC.ink)
                                Spacer(Modifier.weight(1f))
                                if (!readOnly) {
                                    if (removingDate == l.date) {
                                        CircularProgressIndicator(color = NC.ink3, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                                    } else {
                                        NIconButton("xmark", size = 12.dp, color = NC.ink4, label = "${L.md(l.date)} 기록 지우기") {
                                            removeLog(l.date)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 시작 → 목표 사이 위치 (0~1) */
private fun goalExtraPos(m: MetricProgress, v: Double): Double {
    val span = m.targetValue - m.startValue
    if (span == 0.0) return 1.0
    return ((v - m.startValue) / span).coerceIn(0.0, 1.0)
}

private fun goalExtraInsightLines(p: GoalProgress, m: MetricProgress): List<String> {
    val L = GoalDetailLogic
    val lines = mutableListOf<String>()
    val projected = m.projectedDate
    if (m.status == ProgressStatus.ACHIEVED) {
        lines.add("목표 ${L.withUnit(m.targetValue, m.unit)}에 도달했어요. 정말 잘했어요!")
    } else if (projected != null) {
        lines.add("최근 기록 추세라면 ${L.md(projected)}쯤 목표에 닿아요${goalProjectedTail(p.endDate, projected)}.")
    } else if (m.logs.isEmpty()) {
        lines.add("아직 기록이 없어요. 오늘 값을 남기면 그래프가 그려져요.")
    }
    return lines
}

// MARK: - 그래프 만들기 · 고치기 시트

/** metric 이 null 이면 새로 만들기. 저장하면 onSaved(새 진행 데이터) 후 onDone 으로 닫혀요 */
@Composable
fun GoalExtraMetricFormSheet(
    goalId: Int,
    metric: ExtraMetric?,
    onSaved: (GoalProgress) -> Unit,
    onDone: () -> Unit,
) {
    val initial = metric?.progress
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var unit by remember { mutableStateOf(initial?.unit ?: "") }
    var start by remember { mutableStateOf(initial?.let { GoalDetailLogic.fmtNum(it.startValue) } ?: "") }
    var target by remember { mutableStateOf(initial?.let { GoalDetailLogic.fmtNum(it.targetValue) } ?: "") }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var outcome by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            GD.closeMethod(outcome)?.let { m ->
                GD.track("goal_detail.extra_metric_cancel", mapOf("goal_category_id" to goalId),
                    mapOf("is_new" to (metric == null), "close_method" to m))
            }
        }
    }

    val trimmedName = name.trim()
    val startNum = GoalDetailLogic.toNum(start)
    val targetNum = GoalDetailLogic.toNum(target)
    val error: String? = run {
        val s = startNum ?: return@run null
        val t = targetNum ?: return@run null
        if (s.isNaN() || t.isNaN()) return@run "숫자로 입력해 주세요."
        if (s == t) return@run "시작값과 목표값을 다르게 정해 주세요."
        null
    }
    val valid = trimmedName.isNotEmpty() && startNum != null && targetNum != null && error == null

    fun submit(close: SheetClose) {
        val s = startNum ?: return
        val t = targetNum ?: return
        if (!valid || saving) return
        val n = trimmedName
        val u = unit.trim()
        val metricId = metric?.metricId
        GD.track("goal_detail.extra_metric_save", mapOf("goal_category_id" to goalId), mapOf("is_new" to (metricId == null)))
        saving = true
        scope.launch {
            // 응답이 비어도 성공인지 알 수 있게 Pair 로 감싸요
            val r = if (metricId != null) {
                Mutation.run(successMessage = "그래프를 고쳤어요.") {
                    Pair(
                        GoalExtraMetricAPI.update(goalId = goalId, metricId = metricId, name = n, unit = u, startValue = s, targetValue = t),
                        true,
                    )
                }
            } else {
                Mutation.run(successMessage = "그래프를 추가했어요.") {
                    Pair(
                        GoalExtraMetricAPI.create(goalId = goalId, name = n, unit = u.ifEmpty { null }, startValue = s, targetValue = t),
                        true,
                    )
                }
            }
            saving = false
            GD.track("goal_detail.extra_metric_result", mapOf("goal_category_id" to goalId),
                GD.result() + mapOf("metric_action" to if (metricId == null) "create" else "update"))
            if (r != null) {
                outcome = "saved"
                r.first?.let { onSaved(it) }
                close()
            }
        }
    }

    SheetScaffold(
        onDismiss = onDone,
        title = if (metric == null) "그래프 추가" else "그래프 수정",
        dismissible = !saving,
    ) { close ->
        GoalSheetForm {
            GoalInlineForm(
                submitLabel = if (metric == null) "그래프 추가" else "저장",
                saving = saving,
                valid = valid,
                error = error,
                onCancel = {
                    outcome = "cancel_button"
                    close()
                },
                onSubmit = { submit(close) },
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    GoalFormField("무엇을 잴까요?", modifier = Modifier.weight(1f)) {
                        NTextField(name, { name = it }, placeholder = "예: 기출 회독", maxLength = 30)
                    }
                    GoalFormField("단위", modifier = Modifier.width(88.dp)) {
                        NTextField(unit, { unit = it }, placeholder = "회", maxLength = 10)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    GoalFormField("시작값", modifier = Modifier.weight(1f)) {
                        // 음수도 넣을 수 있게 숫자+기호 키보드 (iOS .numbersAndPunctuation)
                        NTextField(start, { start = it }, placeholder = "0", keyboardType = KeyboardType.Text)
                    }
                    GoalFormField("목표값", modifier = Modifier.weight(1f)) {
                        NTextField(target, { target = it }, placeholder = "5", keyboardType = KeyboardType.Text)
                    }
                }
                Text("목표 기간 동안 시작값에서 목표값까지 곧게 가는 계획선이 그려져요.", style = pt(12), color = NC.ink3)
            }
        }
    }
}

// MARK: - 미리보기

private object GoalExtraMetricPreviewData {
    val progress: GoalProgress
        get() {
            val p = GoalPreviewData.progress
            val today = p.today
            val start = p.startDate ?: KST.addDays(today, -30)
            val end = p.endDate ?: KST.addDays(today, 60)
            val json = """
            [
              {"metricId": 1, "sortOrder": 0, "progress": {
                "name": "기출 회독", "unit": "회", "startValue": 0, "targetValue": 5, "currentValue": 1,
                "expectedValue": 1.7, "progressPercent": 20, "status": "BEHIND",
                "lastLoggedDate": "${KST.addDays(today, -2)}", "loggedToday": false,
                "planLine": [{"date": "$start", "value": 0, "label": "시작"}, {"date": "$end", "value": 5, "label": "목표"}],
                "logs": [{"date": "${KST.addDays(today, -12)}", "value": 0.5}, {"date": "${KST.addDays(today, -2)}", "value": 1}]
              }},
              {"metricId": 2, "sortOrder": 1, "progress": {
                "name": "공부 시간", "unit": "시간", "startValue": 0, "targetValue": 60, "currentValue": 62,
                "progressPercent": 103, "status": "ACHIEVED", "loggedToday": true, "lastLoggedDate": "$today",
                "planLine": [{"date": "$start", "value": 0}, {"date": "$end", "value": 60}],
                "logs": [{"date": "${KST.addDays(today, -20)}", "value": 20}, {"date": "$today", "value": 62}]
              }}
            ]
            """.trimIndent()
            // 미리보기 전용이라 실패하면 바로 알 수 있게 그대로 던져요
            val extras = NJson.decodeFromString(ListSerializer(ExtraMetric.serializer()), json)
            return p.copy(extraMetrics = extras)
        }
}

@Preview(showBackground = true, heightDp = 1400, name = "추가 그래프")
@Composable
private fun GoalExtraMetricsSectionPreview() {
    NaleulTheme(dark = false) {
        val vm = remember { GoalDetailViewModel.preview(progress = GoalExtraMetricPreviewData.progress) }
        CompositionLocalProvider(LocalGoalDetailViewModel provides vm) {
            Column(Modifier.canvasBackground().verticalScroll(rememberScrollState()).padding(16.dp)) {
                GoalExtraMetricsSection(goal = GoalPreviewData.goal)
            }
        }
    }
}

@Preview(showBackground = true, name = "그래프 없음")
@Composable
private fun GoalExtraMetricsSectionEmptyPreview() {
    NaleulTheme(dark = false) {
        val vm = remember { GoalDetailViewModel.preview() }
        CompositionLocalProvider(LocalGoalDetailViewModel provides vm) {
            Column(Modifier.canvasBackground().padding(16.dp)) {
                GoalExtraMetricsSection(goal = GoalPreviewData.goal)
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 500, name = "그래프 추가 시트")
@Composable
private fun GoalExtraMetricFormSheetPreview() {
    NaleulTheme(dark = false) {
        GoalExtraMetricFormSheet(goalId = 1, metric = null, onSaved = {}, onDone = {})
    }
}
