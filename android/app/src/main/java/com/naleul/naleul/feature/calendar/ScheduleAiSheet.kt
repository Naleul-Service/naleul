// feature/calendar/ScheduleAiSheet.kt
//
// iOS Features/Calendar/ScheduleAiSheet.swift 를 옮긴 파일.
// 캘린더 "AI로 일정 조정" — 웹 features/timetable/ui/ScheduleAiBar.tsx.
// 여러 목표의 루틴 · 일정을 한 문장으로 한꺼번에 옮겨요.
//  1) 문장을 보내면 AI 가 "옮길 항목 목록"을 만들어요 (보고 있는 주/날의 일정 + 모든 루틴 기준) — 이때는 아무것도 안 바뀌어요
//  2) 전/후 · 이유를 보고 항목마다 시간 · 날짜 · 요일 · 길이를 고치거나 빼고 [적용] (2026-10, 웹 오른쪽 패널과 같게)
// 루틴 변경은 "앞으로 계속", Task 변경은 그날만 바뀌어요.
// 루틴 폼의 "캘린더에서 AI로 정리하기"로 열면 문장이 채워져 있어요 (AppRouter.openCalendarAi).
//
// ScheduleAiRequest(시트를 여는 요청)는 app/AppRouter.kt 에 있어요.

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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

// MARK: - API (POST /timetable/ai-edit/preview · /apply)

@Serializable
data class ScheduleEditOp(
    val type: String,
    val targetId: Int,
    val days: List<String>? = null,
    val startTime: String? = null,
    val durationMinutes: Int? = null,
    val date: String? = null,
    /** AI 가 이렇게 옮기는 이유 (2026-10) — 항목 아래에 보여줘요 */
    val reason: String? = null,
) {
    companion object {}
}

@Serializable
data class ScheduleEditChange(
    val op: ScheduleEditOp,
    /** ROUTINE · TASK */
    val kind: String,
    val target: String,
    val goal: String? = null,
    val colorCode: String? = null,
    val before: String,
    val after: String,
) {
    companion object {}
}

@Serializable
data class ScheduleEditPreview(
    val summary: String,
    val changes: List<ScheduleEditChange> = emptyList(),
    /** 지금 겹쳐 있는 루틴 — "화 16:30–17:00 '기출 풀기' · 'SQL 문제'" */
    val overlaps: List<String> = emptyList(),
) {
    companion object {}
}

@Serializable
data class ScheduleEditApplyResult(
    val applied: Int,
    val skipped: List<String> = emptyList(),
) {
    companion object {}
}

@Serializable
private data class ScheduleEditPreviewBody(val instruction: String, val startDate: String, val endDate: String)

@Serializable
private data class ScheduleEditApplyBody(val ops: List<ScheduleEditOp>, val startDate: String, val endDate: String)

object ScheduleEditAPI {
    suspend fun preview(instruction: String, start: String, end: String): ScheduleEditPreview? =
        API.request<ScheduleEditPreview>(
            "POST", "/v1/timetable/ai-edit/preview",
            JSONValueUtil.from(ScheduleEditPreviewBody(instruction = instruction, startDate = start, endDate = end)),
        )

    suspend fun apply(ops: List<ScheduleEditOp>, start: String, end: String): ScheduleEditApplyResult? =
        API.request<ScheduleEditApplyResult>(
            "POST", "/v1/timetable/ai-edit/apply",
            JSONValueUtil.from(ScheduleEditApplyBody(ops = ops, startDate = start, endDate = end)),
        )
}

// MARK: - 시트

/** ScheduleAiSheet 의 static 값 */
object ScheduleAi {
    const val maxLength = 300
    val examples = listOf("겹치는 루틴 정리해줘", "이번 주 운동 루틴은 저녁 8시로 옮겨줘", "내일 할 일을 오후로 몰아줘")
}

@Composable
fun ScheduleAiSheet(
    /** 보고 있는 기간 (최대 7일) */
    start: String,
    end: String,
    prompt: String? = null,
    preview: ScheduleEditPreview? = null,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf((prompt ?: "").take(ScheduleAi.maxLength)) }
    var asking by remember { mutableStateOf(false) }
    var applying by remember { mutableStateOf(false) }
    var currentPreview by remember { mutableStateOf(preview) }
    val picked = remember { mutableStateListOf<Boolean>().apply { addAll(preview?.changes?.map { true } ?: emptyList()) } }
    /** 항목마다 고친 값 (처음엔 AI 제안 그대로) */
    val ops = remember { mutableStateListOf<ScheduleEditOp>().apply { addAll(preview?.changes?.map { it.op } ?: emptyList()) } }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    /** 루틴 폼 겹침 안내에서 채워 온 문장 (calendar_ai.submit 의 is_prefilled) */
    val prefilled = remember { prompt?.take(ScheduleAi.maxLength) }
    /** 적용해서 닫히면 "saved" (닫기 이벤트 안 보냄) */
    var outcome by remember { mutableStateOf<String?>(null) }
    /** 미리보기(@Preview)에서는 보내지 않아요 */
    val inspection = LocalInspectionMode.current
    fun calTrack(event: String, props: EventProps = emptyMap()) {
        if (!inspection) CAL.track(event, props)
    }

    // 시트가 닫힐 때 — 미리보기가 떠 있었으면 cancel, 적던 중이면 close
    DisposableEffect(Unit) {
        onDispose {
            GD.closeMethod(outcome)?.let { m ->
                val cp = currentPreview
                if (cp != null) {
                    calTrack("calendar_ai.cancel", mapOf("close_method" to m, "change_count" to cp.changes.size))
                } else {
                    calTrack("calendar_ai.close", mapOf("close_method" to m, "text_length" to text.trim().length))
                }
            }
        }
    }

    /** '다시 적기' — 미리보기만 닫고 적은 문장은 남겨요 */
    fun rewrite() {
        calTrack("calendar_ai.cancel", mapOf("close_method" to "rewrite", "change_count" to (currentPreview?.changes?.size ?: 0)))
        currentPreview = null
    }

    val chosen: List<ScheduleEditOp> = ops.filterIndexed { i, _ -> i < picked.size && picked[i] }

    suspend fun ask(value: String) {
        val v = value.trim()
        if (v.isEmpty() || asking) return
        focusManager.clearFocus()
        asking = true
        try {
            val p = ScheduleEditAPI.preview(v, start = start, end = end)
            if (p != null) {
                calTrack(
                    "calendar_ai.preview_result",
                    mapOf("result" to "success", "change_count" to p.changes.size, "overlap_count" to p.overlaps.size),
                )
                text = v
                currentPreview = p
                picked.clear()
                picked.addAll(p.changes.map { true })
                ops.clear()
                ops.addAll(p.changes.map { it.op })
            } else {
                // [UX] 빈 응답이면 화면에 아무 변화가 없어요
                calTrack("calendar_ai.preview_result", mapOf("result" to "fail", "error_status" to 0))
            }
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            calTrack("calendar_ai.preview_result", mapOf("result" to "fail", "error_status" to CAL.status(e)))
            Toast.error(errorMessage(e, fallback = "AI가 잠시 응답하지 않아요. 잠시 후 다시 시도해주세요."))
        } finally {
            asking = false
        }
    }

    suspend fun apply(close: () -> Unit) {
        val selected = chosen
        if (selected.isEmpty() || applying) return
        val edited = ops.indices.count { i ->
            val orig = currentPreview?.changes?.getOrNull(i)?.op
            i < picked.size && picked[i] && orig != null && orig != ops[i]
        }
        calTrack(
            "calendar_ai.apply_click",
            mapOf("selected_count" to selected.size, "change_count" to (currentPreview?.changes?.size ?: 0), "edited_count" to edited),
        )
        applying = true
        var done = false
        val result = Mutation.run<ScheduleEditApplyResult?>(
            invalidate = listOf(DataKey.timetable, DataKey.goals, DataKey.home, DataKey.record),
        ) {
            val r = ScheduleEditAPI.apply(selected, start = start, end = end)
            done = true
            r
        }
        applying = false
        if (!done) {
            calTrack("calendar_ai.apply_result", GD.result(false))
            return
        }
        val applied = result?.applied ?: 0
        val skipped = result?.skipped ?: emptyList()
        calTrack("calendar_ai.apply_result", mapOf("result" to "success", "applied_count" to applied, "skipped_count" to skipped.size))
        if (applied > 0) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            Toast.success("${applied}개를 옮겼어요." + (if (skipped.isEmpty()) "" else " ${skipped.size}개는 못 옮겼어요."))
            outcome = "saved"
            close()
        } else {
            Toast.error(skipped.firstOrNull() ?: "옮긴 일정이 없어요.")
        }
    }

    val p = currentPreview
    SheetScaffold(
        onDismiss = onDismiss,
        title = if (p == null) "AI로 일정 옮기기" else "이렇게 옮길까요?",
        description = p?.summary ?: "이미 있는 루틴 · 일정을 말로 한꺼번에 옮겨요. 말한 시간이 가장 먼저예요. 바꾸기 전에 꼭 확인을 거쳐요.",
        dismissible = !applying,
        fullHeight = true,
        footer = { close ->
            if (p != null) {
                if (p.changes.isEmpty()) {
                    NButton("다시 적기", variant = NButtonVariant.secondary, fullWidth = true, modifier = Modifier.weight(1f)) { rewrite() }
                } else {
                    NButton("다시 적기", variant = NButtonVariant.secondary, enabled = !applying) { rewrite() }
                    NButton(
                        "${chosen.size}개 적용", loading = applying, fullWidth = true, enabled = chosen.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) { scope.launch { apply(close) } }
                }
            } else {
                NButton(
                    "조정하기", variant = NButtonVariant.ai, icon = "sparkles", loading = asking, fullWidth = true,
                    enabled = text.trim().isNotEmpty(), modifier = Modifier.weight(1f),
                ) {
                    val v = text.trim()
                    if (v.isNotEmpty() && !asking) {
                        calTrack(
                            "calendar_ai.submit",
                            mapOf(
                                "view_mode" to if (start == end) "day" else "week", "text_length" to v.length,
                                "is_prefilled" to (prefilled != null && text == prefilled), "submit_method" to "button",
                            ),
                        )
                    }
                    scope.launch { ask(text) }
                }
            }
        },
    ) {
        if (p != null) {
            ScheduleAiPreviewList(
                p = p, picked = picked, asking = asking, ops = ops, today = KST.today(),
                onEdit = { i, field, op ->
                    if (i in ops.indices) {
                        calTrack("calendar_ai.item_edit", mapOf("change_kind" to p.changes[i].kind, "edit_field" to field))
                        ops[i] = op
                    }
                },
                onToggle = { i ->
                    if (i in picked.indices) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        calTrack("calendar_ai.item_toggle", mapOf("change_kind" to p.changes[i].kind, "checked" to !picked[i]))
                        picked[i] = !picked[i]
                    }
                },
                onAskOverlap = {
                    calTrack("calendar_ai.overlap_fix_click", mapOf("overlap_count" to p.overlaps.size))
                    scope.launch { ask("겹치는 루틴 정리해줘") }
                },
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NTextField(
                    value = text, onValueChange = { text = it },
                    placeholder = "예) 겹치는 루틴 정리해줘 · 운동 루틴은 전부 저녁 8시로",
                    minHeight = 96.dp, singleLine = false, maxLength = ScheduleAi.maxLength,
                    modifier = Modifier.focusRequester(focus).semantics { contentDescription = "AI로 일정 옮기기" },
                )
                FlowLayout(spacing = 6.dp) {
                    ScheduleAi.examples.forEachIndexed { i, ex ->
                        Box(
                            Modifier
                                .height(30.dp)
                                .clip(CircleShape)
                                .border(1.dp, NC.borderSubtle, CircleShape)
                                .clickable(role = Role.Button) {
                                    calTrack("calendar_ai.example_click", mapOf("example_index" to i))
                                    text = ex
                                }
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(ex, style = pt(12), color = NC.ink2)
                        }
                    }
                }
                Text(
                    "${KST.formatRange(start, end)} 일정과 모든 루틴을 보고 바꿀 항목을 골라 줘요. 루틴은 앞으로 계속, 하루짜리 일정은 그날만 바뀌어요.",
                    style = pt(12),
                    color = NC.ink3,
                )
            }
            LaunchedEffect(Unit) {
                if (text.isEmpty()) runCatching { focus.requestFocus() }
            }
        }
    }
}

@Composable
private fun ScheduleAiPreviewList(
    p: ScheduleEditPreview,
    picked: List<Boolean>,
    asking: Boolean,
    ops: List<ScheduleEditOp>,
    today: String,
    onEdit: (Int, String, ScheduleEditOp) -> Unit,
    onToggle: (Int) -> Unit,
    onAskOverlap: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (p.changes.isNotEmpty()) {
            Text("아직 아무것도 바뀌지 않았어요. 시간을 고치거나 뺄 항목은 여기서 바꾸고 적용해 주세요.", style = pt(13), color = NC.ink3)
            p.changes.forEachIndexed { i, c ->
                val on = i < picked.size && picked[i]
                ScheduleAiChangeRow(c, on = on) { onToggle(i) }
                if (on && i < ops.size) ScheduleAiOpEditor(ops[i], today) { field, op -> onEdit(i, field, op) }
            }
        }
        if (p.overlaps.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(NC.warningSoft, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    SfImage("exclamationmark.triangle.fill", size = 13.dp, tint = NC.warning)
                    Text("지금 겹쳐 있는 루틴 ${p.overlaps.size}", style = pt(13, PTWeight.semibold), color = NC.ink)
                }
                for (o in p.overlaps) {
                    Text(o, style = pt(13), color = NC.ink2)
                }
                if (p.changes.isEmpty()) {
                    Text(
                        "겹치는 루틴 정리해 달라고 하기",
                        style = pt(12, PTWeight.semibold),
                        color = NC.textBrand,
                        modifier = Modifier
                            .defaultMinSize(minHeight = 28.dp)
                            .alpha(if (asking) 0.4f else 1f)
                            .clickable(enabled = !asking, role = Role.Button) { onAskOverlap() }
                            .padding(vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduleAiChangeRow(c: ScheduleEditChange, on: Boolean, toggle: () -> Unit) {
    val shape = RoundedCornerShape(Radius.card)
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (on) 1f else 0.6f)
            // 고른 AI 제안 = AI 그라데이션 테두리 (스펙 6B) · 안 고른 것 = 평범한 테두리
            .then(if (on) Modifier.aiBorder(shape, glass = false) else Modifier.clip(shape).background(NC.bgSunken, shape).border(1.dp, NC.borderSubtle, shape))
            .clickable(role = Role.Checkbox, onClick = toggle)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // "square" 는 SfIcon 에 없어서 빈 네모를 직접 그려요
        NCheckbox(checked = on, onCheckedChange = null)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (c.kind == "ROUTINE") SfImage("repeat", size = 11.dp, tint = NC.textBrand)
                Text(c.target, style = pt(14, PTWeight.semibold), color = NC.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            val goal = c.goal
            if (goal != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(goalColor(c.colorCode), CircleShape))
                    Text(goal, style = pt(12), color = NC.ink3)
                }
            }
            Text(c.before, style = pt(13).copy(textDecoration = TextDecoration.LineThrough), color = NC.ink3)
            Text(c.after, style = pt(14), color = NC.ink)
        }
    }
}

private val scheduleAiSamplePreview = ScheduleEditPreview(
    summary = "화·목 16시에 '기출 풀기'와 겹치던 'SQL 문제 풀기'를 19:00으로 옮길게요.",
    changes = listOf(
        ScheduleEditChange(
            op = ScheduleEditOp(type = "MOVE_ROUTINE", targetId = 82, startTime = "19:00"), kind = "ROUTINE",
            target = "루틴 'SQL 문제 풀기'", goal = "SQLD 합격", colorCode = "12B76A",
            before = "화·목 16:30–17:30", after = "화·목 19:00–20:00 · 앞으로 계속",
        ),
        ScheduleEditChange(
            op = ScheduleEditOp(type = "MOVE_TASK", targetId = 505, startTime = "19:30"), kind = "TASK",
            target = "Task '습관 체크 API 연결'", goal = "나를 앱 v2 출시", colorCode = "3D5AFE",
            before = "10/14(수) 16:00–18:00", after = "10/14(수) 19:30–21:30",
        ),
    ),
    overlaps = listOf("화·목 16:30–17:00 '기출 풀기' · 'SQL 문제 풀기'"),
)

@Preview(showBackground = true)
@Composable
private fun ScheduleAiSheetComposePreview() {
    NaleulTheme(dark = false) {
        val s = KST.startOfWeek(KST.today())
        ScheduleAiSheet(start = s, end = KST.addDays(s, 6), prompt = "겹치는 루틴 정리해줘", onDismiss = { })
    }
}

@Preview(showBackground = true)
@Composable
private fun ScheduleAiSheetPreviewListPreview() {
    NaleulTheme(dark = false) {
        ScheduleAiSheet(start = KST.today(), end = KST.today(), preview = scheduleAiSamplePreview, onDismiss = { })
    }
}

// MARK: - 항목 고치기 (2026-10, 웹 ScheduleAiPanel · iOS opEditor)

private val SA_DAY_CODES = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
private val SA_DAY_LABELS = listOf("월", "화", "수", "목", "금", "토", "일")
private val SA_LENGTHS = listOf(10, 15, 20, 30, 40, 45, 60, 90, 120, 150, 180, 240)

private fun hhmmToMinutes(t: String?): Int? {
    val p = t?.split(":")?.mapNotNull { it.toIntOrNull() } ?: return null
    return if (p.isEmpty()) null else p[0] * 60 + (p.getOrNull(1) ?: 0)
}

@Composable
private fun ScheduleAiOpEditor(o: ScheduleEditOp, today: String, onEdit: (String, ScheduleEditOp) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val reason = o.reason
        if (!reason.isNullOrBlank()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SfImage("sparkles", size = 11.dp, tint = NC.textBrand, modifier = Modifier.padding(top = 2.dp))
                Text(reason, style = pt(12), color = NC.ink2)
            }
        }
        if (o.type == "MOVE_TASK") {
            NDateField(
                ymd = o.date ?: today,
                onYmdChange = { onEdit("date", o.copy(date = it)) },
                minDate = today,
                maxDate = KST.addDays(today, 60),
            )
        } else {
            val days = o.days
            if (days != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SA_DAY_CODES.forEachIndexed { k, code ->
                        DayChip(SA_DAY_LABELS[k], selected = code in days, modifier = Modifier.weight(1f)) {
                            val next = if (code in days) days - code else days + code
                            if (next.isNotEmpty()) onEdit("days", o.copy(days = next))
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            NTimeField(
                minutes = hhmmToMinutes(o.startTime),
                onMinutesChange = { m ->
                    val r = (m / 10) * 10
                    onEdit("start", o.copy(startTime = "%02d:%02d".format(r / 60, r % 60)))
                },
                label = "시작",
                enabled = !(o.type == "MOVE_ROUTINE" && o.startTime == null),
                modifier = Modifier.weight(1f),
            )
            var open by remember { mutableStateOf(false) }
            Box(Modifier.weight(1f)) {
                Text(
                    "길이 " + (o.durationMinutes?.let { KST.formatDuration(it) } ?: "그대로"),
                    style = pt(13), color = NC.ink2,
                    modifier = Modifier.clickable(role = Role.Button) { open = true }.padding(vertical = 10.dp),
                )
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    DropdownMenuItem(text = { Text("그대로") }, onClick = { open = false; onEdit("duration", o.copy(durationMinutes = null)) })
                    for (m in SA_LENGTHS) {
                        DropdownMenuItem(
                            text = { Text(KST.formatDuration(m)) },
                            onClick = { open = false; onEdit("duration", o.copy(durationMinutes = m)) },
                        )
                    }
                }
            }
        }
    }
}
