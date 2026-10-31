// feature/habit/HabitFormSheet.kt
//
// iOS Features/Habit/HabitFormSheet.swift
// 웹 features/habit/ui/HabitForm.tsx (+ HabitView 의 수정 · 쉬기 · 지우기) — 습관 추가 · 수정 시트.
// 반복 요일을 7일 모두 고르면 매일(repeatDays: [])로 보내요. 알림 시각은 선택.
// 수정할 때 알림을 끄면 clearRemindTime 을 보내요 (HabitInput.hadRemind).
//
// 시트를 직접 그려요: if (…) HabitFormSheet(habit = h, onDone = { sheet = null })
// onDone 은 iOS 처럼 "끝났으니 닫아 주세요" 신호예요 (아래로 끌어 닫아도 불려요).

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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun HabitFormSheet(
    /** null 이면 새로 추가 */
    habit: Habit?,
    /** 지금 있는 습관 이름들 (같은 이름 추가 감지 · habit_count 용) */
    existingNames: List<String> = emptyList(),
    onDone: () -> Unit,
    /** 새로 추가했을 때 (목표 추가 화면처럼 "보러 가기" 토스트를 직접 띄울 때만 — 넘기면 기본 성공 토스트는 생략) */
    onCreated: ((String) -> Unit)? = null,
) {
    var emoji by remember { mutableStateOf(habit?.emoji ?: "") }
    var name by remember { mutableStateOf(habit?.name ?: "") }
    // 매일이면 화면에선 7일 모두 켠 상태로 보여줘요 ("매일" 칩이 켜지게)
    var days by remember {
        mutableStateOf(
            if (habit != null && !habit.daily && habit.repeatDays.isNotEmpty()) habit.repeatDays
            else JavaDayOfWeek.entries.toList(),
        )
    }
    val initRemind = remember { habit?.remindTime?.take(5) ?: "" }
    var remindOn by remember { mutableStateOf(initRemind.isNotEmpty()) }
    /** "HH:mm" */
    var remind by remember { mutableStateOf(if (initRemind.isEmpty()) "08:00" else initRemind) }
    /** 하는 시간대 (선택) — 켜면 캘린더에 블록으로 */
    val initStart = remember { habit?.startTime?.take(5) ?: "" }
    val initEnd = remember { habit?.endTime?.take(5) ?: "" }
    var timeOn by remember { mutableStateOf(initStart.isNotEmpty() && initEnd.isNotEmpty()) }
    var startTime by remember { mutableStateOf(if (initStart.isEmpty()) "20:00" else initStart) }
    var endTime by remember { mutableStateOf(if (initEnd.isEmpty()) "20:30" else initEnd) }
    var saving by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    /** 어떻게 닫혔는지 (cancel_button · saved · deleted) — 비어 있으면 X · 쓸어 닫기 */
    var outcome by remember { mutableStateOf<String?>(null) }
    val isPreview = LocalInspectionMode.current
    fun track(event: String, props: EventProps) {
        if (!isPreview) GD.track(event, props)
    }
    fun trackResult(action: String) {
        track("habit.result", GD.result() + mapOf("habit_action" to action))
    }

    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val trimmedName = name.trim()
    val error: String? = when {
        days.isEmpty() -> "요일을 하나 이상 골라 주세요."
        timeOn && endTime <= startTime -> "끝나는 시간이 시작보다 뒤여야 해요."
        else -> null
    }
    val valid = trimmedName.isNotEmpty() && error == null

    LaunchedEffect(error) {
        if (error != null) track("habit.edit_error", mapOf("is_new" to (habit == null), "validation_error" to error))
    }
    DisposableEffect(Unit) {
        onDispose {
            GD.closeMethod(outcome)?.let { m ->
                track("habit.edit_cancel", mapOf("is_new" to (habit == null), "close_method" to m))
            }
        }
    }

    // MARK: 저장

    fun submit(close: () -> Unit, method: String) {
        if (!valid || saving) return
        track("habit.save_click", mapOf(
            "is_new" to (habit == null), "habit_id" to habit?.habitId, "repeat_day_count" to days.size,
            "has_time" to timeOn, "has_remind" to remindOn, "submit_method" to method,
        ))
        if (habit == null && existingNames.contains(trimmedName)) {
            track("habit.duplicate_add", mapOf("habit_count" to existingNames.size))
        }
        val input = HabitInput(
            name = trimmedName,
            emoji = emoji.trim(),
            repeatDays = if (days.size == 7) emptyList() else days,
            remindTime = if (remindOn) remind else null,
            hadRemind = habit?.remindTime != null,
            startTime = if (timeOn) startTime else null,
            endTime = if (timeOn) endTime else null,
            hadTimeRange = habit?.hasTimeRange ?: false,
        )
        val habitId = habit?.habitId
        val createdName = trimmedName
        saving = true
        scope.launch {
            // 응답 본문이 비어도 성공은 성공 (iOS Habit?? 와 같게 Unit 으로 받아요)
            val ok: Unit? = if (habitId != null) {
                Mutation.run(
                    invalidate = listOf(DataKey.habits, DataKey.home, DataKey.timetable),
                    successMessage = "습관을 수정했어요.",
                ) {
                    HabitAPI.update(habitId = habitId, input = input)
                    Unit
                }
            } else {
                Mutation.run(
                    invalidate = listOf(DataKey.habits, DataKey.home, DataKey.timetable),
                    successMessage = if (onCreated != null) null else "'$createdName' 습관을 추가했어요.",
                ) {
                    HabitAPI.create(input = input)
                    Unit
                }
            }
            saving = false
            trackResult(if (habitId == null) "create" else "update")
            if (ok != null) {
                outcome = "saved"
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                close()
                if (habitId == null) onCreated?.invoke(createdName)
            }
        }
    }

    suspend fun setPaused(h: Habit, pause: Boolean, close: () -> Unit) {
        if (saving) return
        saving = true
        val id = h.habitId
        val ok: Unit? = if (pause) {
            Mutation.run(
                invalidate = listOf(DataKey.habits, DataKey.home, DataKey.timetable),
                successMessage = "잠시 쉬어요. 쉬는 동안은 안 한 날로 세지 않아요.",
            ) {
                HabitAPI.pause(habitId = id)
            }
        } else {
            Mutation.run(
                invalidate = listOf(DataKey.habits, DataKey.home, DataKey.timetable),
                successMessage = "다시 시작했어요.",
            ) {
                HabitAPI.resume(habitId = id)
            }
        }
        saving = false
        trackResult(if (pause) "pause" else "resume")
        if (ok != null) {
            outcome = "saved"
            close()
        }
    }

    suspend fun remove(close: () -> Unit) {
        val h = habit ?: return
        if (saving) return
        saving = true
        val id = h.habitId
        val ok: Unit? = Mutation.run(
            invalidate = listOf(DataKey.habits, DataKey.home, DataKey.timetable),
            successMessage = "습관을 지웠어요.",
        ) {
            HabitAPI.delete(habitId = id)
        }
        saving = false
        trackResult("delete")
        if (ok != null) {
            outcome = "deleted"
            close()
        }
    }

    SheetScaffold(
        onDismiss = onDone,
        title = if (habit == null) "습관 추가" else "습관 수정",
        description = if (habit == null) "했는지만 체크하는 생활 습관이에요. 마감 · 수치가 없어요." else null,
        dismissible = !saving,
        fullHeight = true,
        footer = { close ->
            NButton("취소", variant = NButtonVariant.ghost, enabled = !saving) {
                outcome = "cancel_button"
                close()
            }
            NButton(if (habit == null) "습관 추가" else "저장", loading = saving, enabled = valid) { submit(close, "button") }
        },
    ) { close ->
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // 이모지 · 이름
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("이모지", style = pt(12, PTWeight.medium), color = NC.ink3)
                    EmojiField(emoji = emoji, onEmojiChange = { emoji = it }, placeholder = "✅")
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("습관", style = pt(12, PTWeight.medium), color = NC.ink3)
                    NTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = "예: 영양제 먹기",
                        minHeight = 52.dp,
                        maxLength = 30,
                        onSubmit = { submit(close, "keyboard_done") },
                    )
                }
            }

            // 요일
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("언제 하나요?", style = pt(12, PTWeight.medium), color = NC.ink3)
                GoalDaysPicker(value = days, onValueChange = { days = it })
                if (error != null) {
                    FieldMessage(text = error, isError = true)
                } else {
                    Text(HabitFormat.daysLabel(daily = days.size == 7, days = days), style = pt(12), color = NC.ink3)
                }
            }

            // 하는 시간 (선택)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SfImage("calendar.badge.clock", size = 14.dp, tint = if (timeOn) NC.textBrand else NC.ink3)
                    Text("하는 시간 (선택)", style = pt(14, PTWeight.semibold), color = NC.ink)
                    Spacer(Modifier.weight(1f))
                    NSwitch(isOn = timeOn, onIsOnChange = { timeOn = it }, label = "하는 시간")
                }
                if (timeOn) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NTimeField(
                            minutes = KST.timeToMinutes(startTime),
                            onMinutesChange = { startTime = KST.minutesToTime(it) },
                            label = "시작",
                            modifier = Modifier.width(110.dp),
                        )
                        Text("~", style = pt(15), color = NC.ink3)
                        NTimeField(
                            minutes = KST.timeToMinutes(endTime),
                            onMinutesChange = { endTime = KST.minutesToTime(it) },
                            label = "끝",
                            modifier = Modifier.width(110.dp),
                        )
                    }
                }
                Text(
                    "정하면 캘린더에 그 시간이 블록으로 보이고, 자동 배치가 그 시간은 비워 둬요. (예: 샤워 20:00 ~ 20:30)",
                    style = pt(12),
                    color = NC.ink4,
                )
            }

            // 알림 (선택)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SfImage(if (remindOn) "bell" else "bell.slash", size = 14.dp, tint = if (remindOn) NC.textBrand else NC.ink3)
                    Text("알림 (선택)", style = pt(14, PTWeight.semibold), color = NC.ink)
                    Spacer(Modifier.weight(1f))
                    NSwitch(isOn = remindOn, onIsOnChange = { remindOn = it }, label = "알림")
                }
                if (remindOn) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("알림 시각", style = pt(13), color = NC.ink2)
                        Spacer(Modifier.weight(1f))
                        NTimeField(
                            minutes = KST.timeToMinutes(remind),
                            onMinutesChange = { remind = KST.minutesToTime(it) },
                            label = "알림 시각",
                            modifier = Modifier.width(110.dp),
                        )
                    }
                }
                Text("그 시각까지 체크하지 않았을 때만 앱으로 알려드려요.", style = pt(12), color = NC.ink4)
            }

            // 수정할 때만: 쉬기 · 다시 시작 · 지우기
            if (habit != null) {
                val h = habit
                Column(Modifier.alpha(if (saving) 0.4f else 1f)) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(NC.line))
                    Row(
                        Modifier.padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (h.isPaused) {
                            NButton("다시 시작", variant = NButtonVariant.secondary, size = NButtonSize.sm, icon = "play.fill", enabled = !saving) {
                                track("habit.pause_toggle", mapOf("habit_id" to h.habitId, "pause_action" to "resume", "streak" to h.streak))
                                scope.launch { setPaused(h, false, close) }
                            }
                        } else {
                            NButton("쉬기", variant = NButtonVariant.secondary, size = NButtonSize.sm, icon = "pause.fill", enabled = !saving) {
                                track("habit.pause_toggle", mapOf("habit_id" to h.habitId, "pause_action" to "pause", "streak" to h.streak))
                                scope.launch { setPaused(h, true, close) }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Row(
                            Modifier
                                .height(32.dp)
                                .clickable(enabled = !saving, role = Role.Button) {
                                    track("habit.delete_click", mapOf("habit_id" to h.habitId, "streak" to h.streak))
                                    confirmDelete = true
                                }
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            SfImage("trash", size = 12.dp, tint = NC.danger)
                            Text("습관 지우기", style = pt(13, PTWeight.semibold), color = NC.danger)
                        }
                    }
                }
            }
        }
        ConfirmDialog(
            isPresented = confirmDelete,
            onDismiss = { confirmDelete = false },
            title = "'${habit?.name ?: ""}' 습관을 지울까요?",
            message = "지금까지 체크한 기록도 화면에서 사라져요. 잠깐 쉬려면 '쉬기'를 써 주세요.",
            confirmLabel = "지우기",
            tone = ConfirmTone.danger,
            onCancel = { track("habit.delete_cancel", mapOf("habit_id" to habit?.habitId)) },
        ) {
            track("habit.delete_confirm", mapOf("habit_id" to habit?.habitId, "streak" to habit?.streak))
            scope.launch { remove(close) }
        }
    }
}

// MARK: - 미리보기

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun HabitFormSheetAddPreview() {
    NaleulTheme(dark = false) {
        HabitFormSheet(habit = null, onDone = {})
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun HabitFormSheetEditPreview() {
    NaleulTheme(dark = false) {
        HabitFormSheet(habit = HabitPreviewData.habits[2], onDone = {})
    }
}
