// Features/Goal/GoalFormParts.kt
//
// iOS GoalFormParts.swift — 웹 features/goal/edit/inline.tsx 의 입력 부품들 (InlineForm · InlineConfirm · Field · RowActions · AddButton · DaysPicker).
//
// 웹은 "그 자리에서 바로 고치기"(항목 자리가 입력창으로 바뀜, 한 번에 하나만 열림)였어요.
// 휴대폰은 화면이 좁아서 입력창은 시트(SheetScaffold)로 띄워요. 시트는 한 번에 하나만 뜨니까
// "한 번에 하나만 열기" 규칙이 저절로 지켜져요. 삭제 확인은 ConfirmDialog 로 바꿨어요.
// 웹 Enter 저장 / Esc 취소 → 키보드의 완료 키로 저장, 취소 버튼 · 아래로 끌어 닫기로 취소.

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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

// MARK: - 시트 안에서는 테두리 없는 모양으로

/** true 면 GoalInlineForm 이 테두리·배경 없이 그려져요 (시트 안에 넣을 때. iOS EnvironmentValues.goalFormPlain) */
val LocalGoalFormPlain = compositionLocalOf { false }

/** 시트 안에 GoalInlineForm 을 넣을 때 (테두리 없이). iOS .goalSheetForm() */
@Composable
fun GoalSheetForm(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalGoalFormPlain provides true) { content() }
}

// MARK: - 입력 폼 틀 (웹 InlineForm)

/**
 * 입력칸들 + 오류 문구 + [취소] [저장] 버튼.
 * valid 가 false 면 저장 버튼이 꺼지고, saving 중엔 스피너가 돌아요.
 */
@Composable
fun GoalInlineForm(
    submitLabel: String = "저장",
    saving: Boolean = false,
    valid: Boolean = true,
    error: String? = null,
    onCancel: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    plain: Boolean = LocalGoalFormPlain.current,
    extra: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(Radius.xxl)
    val frame = if (plain) {
        Modifier
    } else {
        Modifier
            .background(NC.brandSoft.copy(alpha = 0.4f), shape)
            .border(1.dp, NC.borderSubtle, shape)
            .padding(14.dp)
    }
    Column(
        modifier.fillMaxWidth().then(frame),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        content()
        if (error != null) {
            Text(error, style = pt(12), color = NC.danger)
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            extra()
            Spacer(Modifier.weight(1f))
            NButton("취소", variant = NButtonVariant.ghost, size = NButtonSize.sm, enabled = !saving) { onCancel() }
            NButton(submitLabel, size = NButtonSize.sm, loading = saving, enabled = valid) {
                if (valid && !saving) onSubmit()
            }
        }
    }
}

// MARK: - 라벨 + 입력칸 (웹 Field)

@Composable
fun GoalFormField(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = pt(12, PTWeight.medium), color = NC.ink3)
        content()
    }
}

// MARK: - 날짜 · 시간 입력 (웹 <input type="date|time">)

/** 비어 있을 때 보이는 "날짜 선택" · "시간 선택" 버튼 */
@Composable
private fun GoalEmptyPickButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Radius.lg)
    Box(
        Modifier
            .height(36.dp)
            .clip(shape)
            .background(NC.subtle, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = pt(14, PTWeight.medium), color = NC.ink3, maxLines = 1)
    }
}

/** "YYYY-MM-DD" 문자열과 주고받는 날짜 칸. optional 이면 비울 수 있어요 ("" = 없음) */
@Composable
fun GoalDateInput(
    value: String,
    onValueChange: (String) -> Unit,
    optional: Boolean = false,
    disabled: Boolean = false,
    /** 이 날짜까지만 고를 수 있어요 (예: 지난 기록 고치기는 오늘까지) */
    maxDate: String? = null,
    placeholder: String = "날짜 선택",
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (value.isEmpty()) {
            GoalEmptyPickButton(placeholder, enabled = !disabled) { onValueChange(KST.today()) }
        } else {
            NDateField(
                ymd = value,
                onYmdChange = onValueChange,
                maxDate = maxDate,
                enabled = !disabled,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (optional && !disabled) {
                NIconButton("xmark.circle.fill", size = 14.dp, color = NC.ink4, label = "날짜 지우기") { onValueChange("") }
            }
        }
    }
}

/** "HH:mm" 문자열과 주고받는 시간 칸 ("" = 비움 → 자동 배치) */
@Composable
fun GoalTimeInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "시간 선택",
    /** 비어 있을 때 누르면 넣을 시간 */
    suggested: String = "09:00",
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (value.isEmpty()) {
            GoalEmptyPickButton(placeholder) { onValueChange(suggested) }
        } else {
            NTimeField(
                minutes = KST.timeToMinutes(value),
                onMinutesChange = { onValueChange(KST.minutesToTime(it)) },
                modifier = Modifier.weight(1f, fill = false),
            )
            NIconButton("xmark.circle.fill", size = 14.dp, color = NC.ink4, label = "시간 지우기") { onValueChange("") }
        }
    }
}

// MARK: - 고르기 (웹 <select>)

@Composable
fun <Value> GoalSelectMenu(
    selection: Value,
    onSelectionChange: (Value) -> Unit,
    options: List<Pair<Value, String>>,
    modifier: Modifier = Modifier,
) {
    NMenuPicker(
        selection = selection,
        options = options,
        onSelect = onSelectionChange,
        modifier = modifier.fillMaxWidth(),
    )
}

// MARK: - 글자 수 제한 (웹 maxLength)

/** iOS .goalMaxLength($text, max) — 입력칸 onValueChange 안에서 goalMaxLength(it, 50) 처럼 써요 (NTextField 는 maxLength 인자도 있어요) */
fun goalMaxLength(text: String, max: Int): String = if (text.length > max) text.take(max) else text

// MARK: - 줄 끝 아이콘 버튼 (웹 RowActions — 휴대폰은 마우스가 없어서 항상 보여요)

@Composable
fun GoalRowActions(
    label: String,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(modifier) {
        if (onEdit != null) {
            NIconButton("pencil", size = 13.dp, color = NC.ink3, label = "$label 수정") { onEdit() }
        }
        if (onDelete != null) {
            NIconButton("trash", size = 13.dp, color = NC.ink3, label = "$label 삭제") { onDelete() }
        }
    }
}

// MARK: - "+ 추가" 점선 버튼 (웹 AddButton)

@Composable
fun GoalAddButton(title: String, modifier: Modifier = Modifier, action: () -> Unit) {
    val stroke = NC.lineStrong
    val shape = RoundedCornerShape(Radius.xxl)
    Row(
        modifier
            .fillMaxWidth()
            .height(42.dp)
            .drawBehind {
                // 점선 테두리 (웹 border-dashed)
                val r = Radius.xxl.toPx()
                drawRoundRect(
                    color = stroke,
                    cornerRadius = CornerRadius(r, r),
                    style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                )
            }
            .clip(shape)
            .clickable(role = Role.Button, onClick = action),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        SfImage("plus", size = 14.dp, tint = NC.ink3)
        Text(title, style = pt(14, PTWeight.semibold), color = NC.ink3)
    }
}

// MARK: - 요일 고르기 (웹 DaysPicker)

private val goalAllDays: List<JavaDayOfWeek> get() = JavaDayOfWeek.entries.toList()
private val goalWeekdays: List<JavaDayOfWeek> get() = JavaDayOfWeek.entries.take(5)
private val goalWeekend: List<JavaDayOfWeek> get() = JavaDayOfWeek.entries.takeLast(2)

@Composable
fun GoalDaysPicker(value: List<JavaDayOfWeek>, onValueChange: (List<JavaDayOfWeek>) -> Unit, modifier: Modifier = Modifier) {
    fun isSet(t: List<JavaDayOfWeek>): Boolean = value.size == t.size && t.all { value.contains(it) }

    fun toggle(d: JavaDayOfWeek) {
        if (value.contains(d)) {
            onValueChange(value.filter { it != d })
        } else {
            // 요일 순서를 지켜요
            onValueChange(goalAllDays.filter { it == d || value.contains(it) })
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (d in goalAllDays) {
                NChip(d.label, selected = value.contains(d), size = ChipSize.sm) { toggle(d) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            NChip("매일", selected = isSet(goalAllDays), size = ChipSize.sm) { onValueChange(goalAllDays) }
            NChip("평일", selected = isSet(goalWeekdays), size = ChipSize.sm) { onValueChange(goalWeekdays) }
            NChip("주말", selected = isSet(goalWeekend), size = ChipSize.sm) { onValueChange(goalWeekend) }
        }
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun GoalFormPartsPreview() {
    NaleulTheme(dark = false) {
        var date by remember { mutableStateOf("2026-10-09") }
        var due by remember { mutableStateOf("") }
        var time by remember { mutableStateOf("") }
        var days by remember { mutableStateOf(listOf(JavaDayOfWeek.MONDAY, JavaDayOfWeek.WEDNESDAY)) }
        var pick by remember { mutableStateOf<Int?>(null) }
        Column(
            Modifier.fillMaxSize().canvasBackground().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            GoalInlineForm(
                submitLabel = "추가",
                error = "날짜를 입력해 주세요.",
                onCancel = {},
                onSubmit = {},
                extra = { Text("알림 켜짐", style = pt(13, PTWeight.semibold), color = NC.textBrand) },
            ) {
                GoalFormField("날짜") { GoalDateInput(value = date, onValueChange = { date = it }) }
                GoalFormField("마감일") { GoalDateInput(value = due, onValueChange = { due = it }, optional = true) }
                GoalFormField("시작 (선택)") { GoalTimeInput(value = time, onValueChange = { time = it }) }
                GoalFormField("반복 요일") { GoalDaysPicker(value = days, onValueChange = { days = it }) }
                GoalFormField("세부 목표 (선택)") {
                    GoalSelectMenu(selection = pick, onSelectionChange = { pick = it }, options = listOf(null to "세부 목표 없음", 1 to "식단 관리"))
                }
            }
            GoalAddButton("루틴 추가") {}
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("아침 스트레칭", style = pt(15))
                Spacer(Modifier.weight(1f))
                GoalRowActions(label = "루틴", onEdit = {}, onDelete = {})
            }
        }
    }
}
