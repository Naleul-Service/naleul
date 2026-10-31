// feature/goalcreation/GoalCreationFormParts.kt
//
// iOS Features/GoalCreation/GoalCreationFormParts.swift
// 목표 만들기 폼에서 같이 쓰는 입력 부품.
// 웹 원본: features/goal-creation/ui/formParts.tsx (Field · inputClass · toNumber)
// 웹 input type="date" / type="time" → 머티리얼 날짜/시각 선택 창 (한국 시간 기준 문자열로 주고받아요)

package com.naleul.naleul.feature.goalcreation

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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/** 라벨 + 입력칸 + 아래 도움말 (웹 Field) */
@Composable
fun GoalCreationField(
    label: String,
    hint: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = pt(13, PTWeight.semibold), color = NC.ink2)
        content()
        if (hint != null) Text(hint, style = pt(12), color = NC.ink3)
    }
}

object GoalCreationForm {
    /** "78" → 78, "" → null(빈칸), "abc" → NaN (숫자가 아님) — 웹 toNumber */
    fun toNumber(v: String): Double? {
        val t = v.trim().replace(",", ".")
        if (t.isEmpty()) return null
        return t.toDoubleOrNull() ?: Double.NaN
    }

    /** 앞뒤 공백 제거 */
    fun trim(v: String): String = v.trim()

    /**
     * Swift String(v.prefix(n)) 와 같아요 — "보이는 글자" n 개까지.
     * Kotlin take(n) 은 UTF-16 단위라 이모지가 반으로 잘릴 수 있어서 글자 경계(BreakIterator)로 잘라요.
     */
    fun prefix(v: String, n: Int): String {
        if (v.length <= n) return v
        val it = java.text.BreakIterator.getCharacterInstance()
        it.setText(v)
        var end = 0
        var count = 0
        while (count < n) {
            val next = it.next()
            if (next == java.text.BreakIterator.DONE) return v
            end = next
            count += 1
        }
        return v.substring(0, end)
    }

    /** Swift v.count — 보이는 글자 수 */
    fun count(v: String): Int {
        if (v.isEmpty()) return 0
        val it = java.text.BreakIterator.getCharacterInstance()
        it.setText(v)
        var n = 0
        while (it.next() != java.text.BreakIterator.DONE) n += 1
        return n
    }
}

/** 입력칸 테두리 공통 모양 (iOS .background(...).overlay(RoundedRectangle.stroke)) */
@Composable
private fun Modifier.goalCreationFieldBox(disabled: Boolean = false): Modifier {
    val shape = RoundedCornerShape(Radius.xl)
    return this
        .defaultMinSize(minHeight = 44.dp)
        .clip(shape)
        .background(if (disabled) NC.subtle else NC.surface, shape)
        .border(1.dp, NC.borderSubtle, shape)
}

/** "YYYY-MM-DD" 날짜 고르기. 비어 있으면 "날짜 선택" 버튼 → 누르면 기본 날짜로 채워요 */
@Composable
fun GoalCreationDateField(
    ymd: String,
    onYmdChange: (String) -> Unit,
    min: String? = null,
    max: String? = null,
    disabled: Boolean = false,
    /** 비울 수 있는 칸(선택 입력)이면 지우기 버튼을 보여줘요 */
    clearable: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .goalCreationFieldBox(disabled)
            .alpha(if (disabled) 0.7f else 1f)
            .padding(start = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (ymd.isEmpty()) {
            Text(
                "날짜 선택",
                style = pt(15),
                color = NC.ink4,
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = !disabled, role = Role.Button) {
                        // 오늘(또는 고를 수 있는 첫날)로 채워요
                        var d = KST.today()
                        if (min != null && d < min) d = min
                        if (max != null && d > max) d = max
                        onYmdChange(d)
                    }
                    .padding(vertical = 12.dp),
            )
        } else {
            Text(
                KST.dotDate(ymd) + " (" + KST.weekdayLabel[KST.weekdayIndex(ymd)] + ")",
                style = ptMono(15),
                color = NC.ink,
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = !disabled, role = Role.Button) { open = true }
                    .padding(vertical = 12.dp),
            )
            if (clearable && !disabled) {
                NIconButton("xmark.circle.fill", size = 14.dp, color = NC.ink4, label = "지우기") { onYmdChange("") }
            }
        }
    }
    if (open) {
        // 고를 수 있는 범위: min ~ max (max 가 min 보다 앞이면 min 하루만)
        val lo = min ?: "2000-01-01"
        val hiRaw = max ?: "2100-12-31"
        NDatePickerDialog(
            initial = ymd,
            minDate = lo,
            maxDate = if (hiRaw < lo) lo else hiRaw,
            onDismiss = { open = false },
            onPick = { onYmdChange(it); open = false },
        )
    }
}

/** "HH:mm" 시각 고르기. 비어 있으면 placeholder 버튼 (선택 입력) */
@Composable
fun GoalCreationTimeField(
    hm: String,
    onHmChange: (String) -> Unit,
    placeholder: String = "시각 선택",
    defaultValue: String = "19:00",
    clearable: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .goalCreationFieldBox()
            .padding(start = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (hm.isEmpty()) {
            Text(
                placeholder,
                style = pt(15),
                color = NC.ink4,
                modifier = Modifier
                    .weight(1f)
                    .clickable(role = Role.Button) { onHmChange(defaultValue) }
                    .padding(vertical = 12.dp),
            )
        } else {
            Text(
                hm,
                style = ptMono(15),
                color = NC.ink,
                modifier = Modifier
                    .weight(1f)
                    .clickable(role = Role.Button) { open = true }
                    .padding(vertical = 12.dp),
            )
            if (clearable) {
                NIconButton("xmark.circle.fill", size = 14.dp, color = NC.ink4, label = "지우기") { onHmChange("") }
            }
        }
    }
    if (open) {
        NTimePickerDialog(
            initialMinutes = KST.timeToMinutes(hm),
            onDismiss = { open = false },
            onPick = { onHmChange(KST.minutesToTime(it)); open = false },
        )
    }
}

/** 요일 칩 줄 (월~일) */
@Composable
fun GoalCreationDayChips(
    days: List<GoalCreationWeekday>,
    onDaysChange: (List<GoalCreationWeekday>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (d in GoalCreationWeekday.entries) {
            val on = days.contains(d)
            Box(
                Modifier
                    .size(width = 40.dp, height = 32.dp)
                    .clip(CircleShape)
                    .background(if (on) NC.actionPrimary else NC.bgSunken, CircleShape)
                    .border(1.dp, if (on) NC.actionPrimary else NC.borderSubtle, CircleShape)
                    .clickable(role = Role.Checkbox) {
                        if (on) onDaysChange(days.filter { it != d }) else onDaysChange(days + d)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(d.label, style = NType.bodySMMedium, color = if (on) NC.actionPrimaryFg else NC.textSecondary, textAlign = TextAlign.Center)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun GoalCreationFormPartsPreview() {
    NaleulTheme(dark = false) {
        var date by remember { mutableStateOf("2026-11-18") }
        var empty by remember { mutableStateOf("") }
        var time by remember { mutableStateOf("19:00") }
        var days by remember { mutableStateOf(listOf(GoalCreationWeekday.MON, GoalCreationWeekday.WED)) }
        var text by remember { mutableStateOf("") }
        Column(Modifier.background(NC.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            GoalCreationField("이루고 싶은 목표", hint = "100자 이내") {
                NTextField(text, { text = it }, placeholder = "예) 몸무게 10kg 감량")
            }
            GoalCreationField("날짜") { GoalCreationDateField(ymd = date, onYmdChange = { date = it }, min = KST.today()) }
            GoalCreationField("실행일 (선택)") { GoalCreationDateField(ymd = empty, onYmdChange = { empty = it }, clearable = true) }
            GoalCreationField("시작 시각 (선택)") { GoalCreationTimeField(hm = time, onHmChange = { time = it }) }
            GoalCreationDayChips(days = days, onDaysChange = { days = it })
        }
    }
}
