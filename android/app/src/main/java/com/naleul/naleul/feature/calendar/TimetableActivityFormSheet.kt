// feature/calendar/TimetableActivityFormSheet.kt
//
// iOS Features/Calendar/TimetableActivityFormSheet.swift 를 옮긴 파일.
// 실제로 한 일 기록 · 수정 시트 (지난 빈 칸을 누르거나 "실제로 한 일" · "이 시간에 다른 일을 했어요"로 열어요).
// 웹 원본: features/timetable/ui/ActivityForm.tsx
//  - 끝 시각이 시작보다 이르면 다음 날로 봐요 (예: 23:30 ~ 00:30)
//  - 아직 오지 않은 시간은 기록할 수 없어요 · 24시간까지만
//  - 삭제는 같은 자리에서 한 번 더 확인

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun TimetableActivityFormSheet(
    board: TimetableBoardModel,
    draft: TimetableActivityDraft,
    onDismiss: () -> Unit,
) {
    val a = draft.activity
    var title by remember(draft) { mutableStateOf(a?.title ?: "") }
    var emoji by remember(draft) { mutableStateOf(a?.emoji ?: "") }
    var date by remember(draft) { mutableStateOf(draft.date) }
    var start by remember(draft) { mutableStateOf(timetableWrapMinutes(draft.start)) }
    var end by remember(draft) { mutableStateOf(timetableWrapMinutes(draft.end)) }
    var goalId by remember(draft) { mutableStateOf(a?.goalCategoryId) }
    var replaced by remember(draft) { mutableStateOf(draft.replaced) }
    var memo by remember(draft) { mutableStateOf(a?.memo ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var goals by remember { mutableStateOf<List<GoalCategory>>(emptyList()) }
    val scope = rememberCoroutineScope()
    /** "saved" · "deleted" 면 닫혀도 activity_form.close 안 보냄 */
    var outcome by remember { mutableStateOf<String?>(null) }

    /** 처음 연 값과 달라졌는지 (activity_form.close 의 is_dirty) */
    fun isDirty(): Boolean =
        title != (a?.title ?: "") || emoji != (a?.emoji ?: "") || date != draft.date ||
            start != timetableWrapMinutes(draft.start) || end != timetableWrapMinutes(draft.end) ||
            goalId != a?.goalCategoryId || memo != (a?.memo ?: "") || replaced?.id != draft.replaced?.id

    DisposableEffect(draft) {
        onDispose {
            GD.closeMethod(outcome)?.let { m ->
                board.sheetTrack("activity_form.close", mapOf("is_new" to (a == null), "close_method" to m, "is_dirty" to isDirty()))
            }
        }
    }

    LaunchedEffect(Unit) {
        if (board.isOffline) return@LaunchedEffect
        goals = try {
            GoalAPI.goals(includeHabit = true)   // 기본 생활 습관으로도 분류 (2026-10)
        } catch (c: CancellationException) {
            throw c
        } catch (_: Exception) {
            emptyList()
        }
    }

    // MARK: 계산
    val trimmedTitle = title.trim()
    val startMinutes = start
    /** 자정을 넘긴 기록 */
    val endMinutes = if (end <= start) end + 1440 else end
    val startAt = KST.toDateTime(date, startMinutes)
    val endAt = KST.toDateTime(date, endMinutes)
    val error: String? = run {
        if (trimmedTitle.isEmpty()) return@run null
        val now = KST.now()
        if (endAt > KST.toDateTime(now.date, now.minutes)) return@run "아직 오지 않은 시간은 기록할 수 없어요."
        if (endMinutes - startMinutes > 1440) return@run "24시간까지만 기록할 수 있어요."
        null
    }
    val valid = trimmedTitle.isNotEmpty() && error == null
    LaunchedEffect(error) {
        if (error != null) board.sheetTrack("activity_form.error_view", mapOf("validation_error" to error))
    }
    val durationText = run {
        val m = endMinutes - startMinutes
        if (m >= 60) "${m / 60}시간${if (m % 60 > 0) " ${m % 60}분" else ""}" else "${m}분"
    }
    val activeGoals = goals.filter { it.goalCategoryStatus.isOngoing || it.goalCategoryId == goalId }

    // MARK: 저장
    fun submit() {
        if (!valid || saving) return
        saving = true
        val e = emoji.trim()
        val m = memo.trim()
        val input = ActivityInput(
            title = trimmedTitle, emoji = e.ifEmpty { null }, startAt = startAt, endAt = endAt,
            goalCategoryId = goalId, replacedTaskId = replaced?.id, memo = m.ifEmpty { null },
        )
        board.sheetTrack(
            "activity_form.save_click",
            mapOf(
                "is_new" to (draft.activity == null), "duration_minutes" to (endMinutes - startMinutes),
                "crosses_midnight" to (endMinutes > 1440), "has_goal" to (goalId != null),
                "is_habit_goal" to (goals.firstOrNull { it.goalCategoryId == goalId }?.isHabitGoal ?: false), "memo_length" to m.length,
                "is_replacement" to (replaced != null), "days_ago" to CAL.daysAgo(date),
            ),
        )
        val id = draft.activity?.activityId
        scope.launch {
            if (board.saveActivity(input, activityId = id)) outcome = "saved"
            saving = false
        }
    }

    fun remove(activityId: Int) {
        if (deleting) return
        deleting = true
        scope.launch {
            if (board.deleteActivity(activityId)) outcome = "deleted"
            deleting = false
        }
    }

    SheetScaffold(onDismiss = onDismiss, title = if (draft.activity == null) "실제 한 일 추가" else null, dismissible = !saving && !deleting) {
        Column(
            Modifier.fillMaxWidth().semantics { contentDescription = "실제로 한 일" },
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("✓ 실제로 한 일", style = pt(12, PTWeight.bold), color = NC.success)
                Text("계획과 상관없이 그 시간에 실제로 한 일을 남겨요.", style = pt(12), color = NC.ink3)
            }
            replaced?.let { r ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(NC.subtle, RoundedCornerShape(Radius.xl))
                        .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SfImage("link", size = 12.dp, tint = NC.ink3)
                    Text("계획", style = pt(13), color = NC.ink3)
                    Text(r.name, style = pt(13, PTWeight.bold), color = NC.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("대신", style = pt(13), color = NC.ink3)
                    NIconButton("xmark", size = 12.dp, label = "계획 연결 끊기") {
                        board.sheetTrack("activity_form.unlink_click")
                        replaced = null
                    }
                }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.width(60.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TimetableFieldLabel("이모지")
                    NTextField(
                        value = emoji, onValueChange = { emoji = it }, placeholder = "🙂", maxLength = 16,
                        textStyle = pt(15).copy(textAlign = TextAlign.Center),
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TimetableFieldLabel("무엇을 했나요?")
                    NTextField(
                        value = title, onValueChange = { title = it }, placeholder = "예) 친구 만남, 유튜브, 낮잠",
                        maxLength = 60, onSubmit = { submit() },
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TimetableFieldLabel("날짜")
                TimetableDateField(ymd = date, onYmdChange = { date = it }, maxDate = KST.today(), modifier = Modifier.fillMaxWidth())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TimetableFieldLabel("시작")
                    TimetableClockPicker(minutes = start, onMinutesChange = { start = it }, label = "시작")
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TimetableFieldLabel("끝")
                        if (endMinutes >= 1440) Text("(다음 날)", style = pt(12), color = NC.ink4)
                    }
                    TimetableClockPicker(minutes = end, onMinutesChange = { end = it }, label = "끝")
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TimetableFieldLabel("목표 (선택)")
                NMenuPicker(
                    selection = goalId,
                    options = listOf<Pair<Int?, String>>(null to "연결 안 함") +
                        activeGoals.map { g -> g.goalCategoryId to TimetableRules.title(g.emoji, g.goalCategoryName) },
                    onSelect = { goalId = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TimetableFieldLabel("메모 (선택)")
                NTextField(
                    value = memo, onValueChange = { memo = it },
                    placeholder = "왜 계획과 달라졌는지 적어 두면 패턴을 보기 좋아요",
                    minHeight = 64.dp, singleLine = false, maxLength = 200,
                )
            }
            if (error != null) {
                Text(error, style = pt(13, PTWeight.medium), color = NC.danger)
            }
            // 아래 버튼
            val act = draft.activity
            if (confirmDelete && act != null) {
                TimetableInlineConfirm(
                    message = "이 기록을 지울까요?", loading = deleting,
                    onConfirm = {
                        board.sheetTrack("activity_form.delete_confirm", mapOf("days_ago" to CAL.daysAgo(draft.date)))
                        remove(act.activityId)
                    },
                    onCancel = {
                        board.sheetTrack("activity_form.delete_cancel")
                        confirmDelete = false
                    },
                )
            } else {
                Row(
                    Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (act != null) {
                        Row(
                            Modifier
                                .height(32.dp)
                                .clickable(role = Role.Button) {
                                    board.sheetTrack("activity_form.delete_click", mapOf("days_ago" to CAL.daysAgo(draft.date)))
                                    confirmDelete = true
                                }
                                .padding(horizontal = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SfImage("trash", size = 13.dp, tint = NC.danger)
                            Text("삭제", style = pt(13, PTWeight.semibold), color = NC.danger)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(if (valid) durationText else "", style = ptMono(12), color = NC.ink3)
                    NButton(if (act != null) "저장" else "기록", size = NButtonSize.sm, loading = saving, enabled = valid) { submit() }
                }
            }
        }
    }
}

@Composable
private fun TimetableFieldLabel(text: String) {
    Text(text, style = pt(12, PTWeight.medium), color = NC.ink3)
}

@Preview(showBackground = true)
@Composable
private fun TimetableActivityFormSheetPreview() {
    NaleulTheme(dark = false) {
        val d = KST.today()
        val board = remember { TimetableBoardModel.sample(start = d, end = d) }
        val draft = TimetableActivityDraft(
            activity = null, date = d, start = 600, end = 660,
            replaced = TimetableReplacedTask(id = 2001, name = "영어 단어 외우기"),
        )
        TimetableActivityFormSheet(board = board, draft = draft, onDismiss = { })
    }
}
