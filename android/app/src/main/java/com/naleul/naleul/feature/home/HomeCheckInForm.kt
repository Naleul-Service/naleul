// feature/home/HomeCheckInForm.kt
//
// iOS Features/Home/HomeCheckInForm.swift
// 홈 "오늘 회고" 시트의 목표형 칸 — 웹 features/home/ui/HomeCheckInForm.tsx.
// 목표 상세 "오늘 기록"(GoalDailyCheckInCard)과 같은 기록이에요 (PUT /goal-categories/{id}/check-in).
//  - 수치 목표면 그날 수치 (예: 64kg)
//  - 어땠는지 (잘했어요 · 그럭저럭 · 아쉬워요)
//  - 한 줄 회고
// 이미 남긴 기록이 있으면 채워진 채로 열리고, 저장하면 덮어써요. 업무형은 이 칸 대신 회고 글을 써요.

package com.naleul.naleul.feature.home

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** 받침에 맞는 조사: 체중은 / 점수는 */
private fun checkInJosa(word: String, withFinal: String, without: String): String {
    val last = word.lastOrNull() ?: return word + without
    val c = last.code
    if (c < 0xAC00 || c > 0xD7A3) return word + without
    return word + (if ((c - 0xAC00) % 28 != 0) withFinal else without)
}

@Composable
fun HomeCheckInForm(
    goal: HomeGoalToday,
    checkIn: HomeCheckIn,
    date: String,
    onSaved: () -> Unit = {},
    onSkip: () -> Unit = {},
) {
    // 목표 · 날짜가 바뀌면 처음 값으로 다시 채워요 (iOS 는 부모가 .id(...) 로 새로 만들어요)
    var value by remember(goal.goalCategoryId, date) {
        mutableStateOf(checkIn.value?.let { GoalDetailLogic.fmtNum(it) } ?: "")
    }
    var mood by remember(goal.goalCategoryId, date) { mutableStateOf(checkIn.mood) }
    var note by remember(goal.goalCategoryId, date) { mutableStateOf(checkIn.note ?: "") }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val had = checkIn.written
    val name = checkIn.metricName?.takeIf { it.isNotEmpty() } ?: "수치"

    val trimmedValue = value.trim()
    /** 비었으면 null, 숫자가 아니면 NaN */
    val valueNum: Double? = if (trimmedValue.isEmpty()) null else (trimmedValue.toDoubleOrNull() ?: Double.NaN)
    val valueOk = valueNum?.isFinite() ?: true
    val trimmedNote = note.trim()
    // 처음 쓰는 날은 하나라도 채워야 해요 (이미 남긴 기록은 비워서 지울 수도 있어요)
    val canSave = valueOk && !saving &&
        ((checkIn.hasMetric && valueNum != null) || mood != null || trimmedNote.isNotEmpty() || had)

    fun submit() {
        if (!canSave) return
        HomeTracker.track(
            HomeEvent.checkinSaveClick,
            "goal_category_id" to goal.goalCategoryId, "has_metric" to checkIn.hasMetric,
            "has_value" to (checkIn.hasMetric && valueNum != null), "mood" to mood?.name,
            "note_length" to trimmedNote.length, "is_edit" to had, "submit_method" to "button",
            "view_date" to date, "date_relation" to HomeTracker.relation(date),
        )
        val v: Double? = if (checkIn.hasMetric) valueNum else null
        val n: String? = trimmedNote.ifEmpty { null }
        val md = mood
        val id = goal.goalCategoryId
        val day = date
        focusManager.clearFocus()
        saving = true
        scope.launch {
            val r = Mutation.run(
                invalidate = listOf(DataKey.home, DataKey.goals),
                successMessage = "오늘 기록을 남겼어요.",
                errorFallback = "기록하지 못했어요. 다시 시도해 주세요.",
            ) {
                HomeAPI.checkIn(goalId = id, date = day, value = v, mood = md, note = n)
            }
            HomeTracker.system(
                HomeEvent.retroSaveResult,
                "retro_action" to "checkin", "result" to if (r == null) "fail" else "success", "goal_category_id" to id,
            )
            saving = false
            if (r != null) onSaved()
        }
    }

    val brand = NC.brand
    Column(
        Modifier
            .fillMaxWidth()
            .background(NC.brandSoft.copy(alpha = 0.3f), RoundedCornerShape(Radius.xl))
            .border(1.dp, NC.borderSubtle, RoundedCornerShape(Radius.xl))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (checkIn.hasMetric) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${checkInJosa(name, "은", "는")} 얼마예요?", style = pt(13, PTWeight.semibold), color = NC.ink2)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NTextField(
                        value = value,
                        onValueChange = { v -> value = v.filter { "0123456789.-".contains(it) } },
                        placeholder = checkIn.currentValue?.let { GoalDetailLogic.fmtNum(it) } ?: "",
                        invalid = !valueOk,
                        keyboardType = KeyboardType.Decimal,
                        textStyle = pt(17, PTWeight.bold),
                        modifier = Modifier
                            .widthIn(max = 180.dp)
                            .semantics { contentDescription = "${goal.goalName} $name 값" },
                    )
                    val u = checkIn.unit
                    if (!u.isNullOrEmpty()) Text(u, style = pt(13), color = NC.ink3)
                }
                if (!valueOk) {
                    Text("숫자로 입력해 주세요", style = pt(12), color = NC.danger)
                    LaunchedEffect(Unit) {
                        HomeTracker.system(HomeEvent.checkinValueInvalid, "goal_category_id" to goal.goalCategoryId)
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("어땠어요?", style = pt(13, PTWeight.semibold), color = NC.ink2)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (o in GoalMoodOption.all) {
                    val on = mood == o.value
                    Row(
                        Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(CircleShape)
                            .background(if (on) NC.brand else NC.surface, CircleShape)
                            .border(if (on) 1.5.dp else 1.dp, if (on) NC.borderFocus else NC.borderSubtle, CircleShape)
                            .clickable(role = Role.RadioButton) {
                                HomeTracker.track(HomeEvent.checkinMoodSelect, "mood" to o.value.name, "mood_action" to if (on) "clear" else "select")
                                mood = if (on) null else o.value
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                    ) {
                        Text(o.emoji, style = pt(13))
                        Text(o.label, style = pt(13, PTWeight.semibold), color = if (on) Color.White else NC.ink2, maxLines = 1)
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("무엇을 했나요? (한 줄 회고)", style = pt(13, PTWeight.semibold), color = NC.ink2)
            NTextField(
                value = note,
                onValueChange = { v ->
                    // 처음 글자를 칠 때 한 번 (home.retro_input_start)
                    if (note.isEmpty() && v.isNotEmpty() && checkIn.note.isNullOrEmpty()) {
                        HomeTracker.track(
                            HomeEvent.retroInputStart,
                            "goal_category_id" to goal.goalCategoryId, "form_type" to "checkin", "goal_mode" to goal.goalMode?.name,
                        )
                    }
                    note = v
                },
                placeholder = goal.prompt,
                minHeight = 88.dp,
                singleLine = false,
                maxLength = 500,
                modifier = Modifier.semantics { contentDescription = "${goal.goalName} 한 줄 회고" },
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NButton("건너뛰기", variant = NButtonVariant.ghost, size = NButtonSize.sm) { onSkip() }
            Spacer(Modifier.weight(1f))
            val fg = nButtonContentColor(NButtonVariant.primary, canSave)
            Row(
                Modifier.nButton(variant = NButtonVariant.primary, size = NButtonSize.sm, enabled = canSave) { submit() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (saving) CircularProgressIndicator(color = fg, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                Text(if (had) "고치고 다음" else "저장하고 다음", style = pt(13, PTWeight.semibold), color = fg)
                SfImage("arrow.right", size = 12.dp, tint = fg)
            }
        }
        Text("목표 상세의 '오늘 기록'과 같은 기록이에요.", style = pt(11), color = NC.ink4)
    }
}

@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun HomeCheckInFormPreview() {
    NaleulTheme(dark = false) {
        val home = HomeSampleData.homeToday(KST.today())
        Column(
            Modifier.canvasBackground().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 수치 목표 · 이미 남김 · 수치 없는 목표
            for (id in listOf(3, 1, 4)) {
                val g = home.goals.first { it.goalCategoryId == id }
                HomeCheckInForm(goal = g, checkIn = g.checkIn ?: HomeCheckIn(), date = KST.today())
            }
        }
    }
}
