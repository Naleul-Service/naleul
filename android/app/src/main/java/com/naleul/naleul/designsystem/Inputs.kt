// designsystem/Inputs.kt
//
// iOS DesignSystem/Components/Inputs.swift — 입력칸 · 스위치 · 세그먼트 · 필드 라벨 + 날짜/시각 고르기 · 메뉴.
//
//   NTextField(name, { name = it }, placeholder = "목표 이름")           // iOS TextField(...).inputStyle()
//   NTextField(memo, { memo = it }, minHeight = 96.dp, singleLine = false) // iOS TextEditor(...).inputStyle(minHeight: 96)
//   FieldLabel("시작 시간", hint = "10분 단위")
//   NSwitch(isOn = enabled, onIsOnChange = { enabled = it }, label = "알림")
//   SegmentPicker(selection = view, onSelectionChange = { view = it }, options = listOf("day" to "일", "week" to "주"))
//   NDateField(ymd = date, onYmdChange = { date = it })                  // iOS DatePicker(displayedComponents: .date)
//   NTimeField(minutes = start, onMinutesChange = { start = it })        // iOS DatePicker(displayedComponents: .hourAndMinute)
//   NMenuPicker(selection = goalId, options = listOf(null to "연결 안 함") + goals.map { it.id to it.title }, onSelect = { goalId = it })
//   NMenu(items = listOf(MenuItem("수정", "pencil") { edit() }, MenuItem("삭제", "trash", destructive = true) { delete() })) { SfImage("ellipsis") }

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.naleul.naleul.designsystem

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naleul.naleul.core.util.KST

// MARK: - 입력칸 (Component_ver.2: h44 radius 10 bg/surface 1px border/subtle px14 Input 14 · focus border/focus)

@Composable
fun NTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    minHeight: Dp = 44.dp,
    invalid: Boolean = false,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = if (singleLine) ImeAction.Done else ImeAction.Default,
    onSubmit: (() -> Unit)? = null,
    maxLength: Int? = null,
    textStyle: TextStyle = NType.input,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(Radius.input)
    val strokeColor = when {
        invalid -> NC.statusErrorFg
        focused -> NC.borderFocus
        else -> NC.borderSubtle
    }
    val ink = if (enabled) NC.textPrimary else NC.disabledFg
    val ink4 = NC.textPlaceholder
    BasicTextField(
        value = value,
        onValueChange = { v -> onValueChange(if (maxLength != null) v.take(maxLength) else v) },
        enabled = enabled,
        singleLine = singleLine,
        textStyle = textStyle.copy(color = ink),
        cursorBrush = SolidColor(NC.borderFocus),
        interactionSource = interaction,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onAny = { onSubmit?.invoke() }),
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = minHeight)
            .background(if (enabled) NC.bgSurface else NC.disabledBg, shape)
            .border(if (focused || invalid) 1.5.dp else 1.dp, strokeColor, shape),
        decorationBox = { inner ->
            Box(
                Modifier.padding(horizontal = 14.dp, vertical = 10.dp).defaultMinSize(minHeight = minHeight - 20.dp),
                contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
            ) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(placeholder, style = textStyle.copy(fontWeight = NType.inputPlaceholder.fontWeight), color = ink4)
                }
                inner()
            }
        },
    )
}

/** 입력칸 위 라벨 (+ 오른쪽 보조 글) */
@Composable
fun FieldLabel(title: String, hint: String? = null, required: Boolean = false, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = NType.labelLG, color = NC.textPrimary)
        if (required) Text("*", style = NType.labelLG, color = NC.statusErrorFg)
        Spacer(Modifier.weight(1f))
        if (hint != null) Text(hint, style = NType.captionMD, color = NC.textTertiary)
    }
}

/** 입력칸 아래 도움말/오류 문구 */
@Composable
fun FieldMessage(text: String, isError: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        text,
        style = if (isError) NType.captionMD.copy(fontWeight = NType.labelXS.fontWeight) else NType.captionMD,
        color = if (isError) NC.statusErrorFg else NC.textTertiary,
        modifier = modifier,
    )
}

// MARK: - 토글 (Component_ver.2 Toggle 44×26 · on action/primary · off border/subtle · 손잡이 20 bg/surface)

@Composable
fun NSwitch(isOn: Boolean, onIsOnChange: (Boolean) -> Unit, label: String = "", disabled: Boolean = false, modifier: Modifier = Modifier) {
    val track = when {
        disabled -> NC.disabledBg
        isOn -> NC.actionPrimary
        else -> NC.borderSubtle
    }
    val knobX by animateDpAsState(if (isOn) 21.dp else 3.dp, tween(160), label = "toggle")
    val knob = NC.bgSurface
    Box(
        modifier
            .size(width = 44.dp, height = 26.dp)
            .clip(CircleShape)
            .background(track, CircleShape)
            .toggleable(value = isOn, enabled = !disabled, role = Role.Switch, onValueChange = onIsOnChange)
            .semantics { if (label.isNotEmpty()) contentDescription = label },
    ) {
        Box(
            Modifier
                .offset(x = knobX, y = 3.dp)
                .size(20.dp)
                .shadow(1.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.12f))
                .background(knob, CircleShape),
        )
    }
}

/** 별칭 (스펙 이름) */
@Composable
fun NToggle(isOn: Boolean, onIsOnChange: (Boolean) -> Unit, label: String = "", disabled: Boolean = false, modifier: Modifier = Modifier) =
    NSwitch(isOn, onIsOnChange, label, disabled, modifier)

// MARK: - 체크박스 (20×20 r6 · off 1.5px border/control · on action/primary + 흰 체크 14)

@Composable
fun NCheckbox(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, enabled: Boolean = true, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Radius.checkbox)
    val fill = when {
        !enabled -> NC.disabledBg
        checked -> NC.actionPrimary
        else -> Color.Transparent
    }
    Box(
        modifier
            .size(20.dp)
            .clip(shape)
            .background(fill, shape)
            .then(if (!checked) Modifier.border(1.5.dp, if (enabled) NC.borderControl else NC.disabledFg, shape) else Modifier)
            .then(
                if (onCheckedChange != null) Modifier.toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange)
                else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) NIcon.check.Icon(size = 14.dp, tint = if (enabled) NC.actionPrimaryFg else NC.disabledFg)
    }
}

// MARK: - 라디오 (20×20 · off 1.5px border/control · on 2px primary 링 + 8px 점)

@Composable
fun NRadio(selected: Boolean, onClick: (() -> Unit)?, enabled: Boolean = true, modifier: Modifier = Modifier) {
    val ring = when {
        !enabled -> NC.disabledFg
        selected -> NC.actionPrimary
        else -> NC.borderControl
    }
    Box(
        modifier
            .size(20.dp)
            .clip(CircleShape)
            .border(if (selected) 2.dp else 1.5.dp, ring, CircleShape)
            .then(if (onClick != null) Modifier.selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(8.dp).background(ring, CircleShape))
    }
}

// MARK: - 요일 칩 (44×36 radius full · on primary + 흰 글자 · off bg/canvas + text/secondary · Body/MD·Medium)

@Composable
fun DayChip(label: String, selected: Boolean, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .size(width = 44.dp, height = 36.dp)
            .clip(CircleShape)
            .background(
                when {
                    !enabled -> NC.disabledBg
                    selected -> NC.actionPrimary
                    else -> NC.bgSunken
                },
                CircleShape,
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = NType.bodyMDMedium,
            color = when {
                !enabled -> NC.disabledFg
                selected -> NC.actionPrimaryFg
                else -> NC.textSecondary
            },
            maxLines = 1,
        )
    }
}

// MARK: - 세그먼트 (SegmentedTabs / ViewSwitch: bg/subtle r12 p3 · 선택 칸 bg/surface r10 + 그림자 · Label/MD)

@Composable
fun <T> SegmentPicker(
    selection: T,
    onSelectionChange: (T) -> Unit,
    options: List<Pair<T, String>>,
    icons: Map<T, String> = emptyMap(),
    fullWidth: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .background(NC.bgSubtle, RoundedCornerShape(Radius.segment))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for ((value, label) in options) {
            val on = value == selection
            val itemShape = RoundedCornerShape(Radius.segmentItem)
            val fg = if (on) NC.textPrimary else NC.textSecondary
            Row(
                modifier = Modifier
                    .then(if (fullWidth) Modifier.weight(1f) else Modifier)
                    .height(32.dp)
                    .then(if (on) Modifier.segShadow(itemShape).background(NC.bgSurface, itemShape) else Modifier)
                    .clip(itemShape)
                    .clickable(role = Role.Tab) { onSelectionChange(value) }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            ) {
                icons[value]?.let { SfImage(it, size = 14.dp, tint = fg) }
                Text(label, style = NType.labelMD, color = fg, maxLines = 1)
            }
        }
    }
}

/** 스펙 이름 (SegmentedTabs) — SegmentPicker 와 같아요 */
@Composable
fun <T> SegmentedTabs(
    selection: T,
    onSelectionChange: (T) -> Unit,
    options: List<Pair<T, String>>,
    fullWidth: Boolean = false,
    modifier: Modifier = Modifier,
) = SegmentPicker(selection, onSelectionChange, options, fullWidth = fullWidth, modifier = modifier)

// MARK: - 이모지 한 글자 입력칸

@Composable
fun EmojiField(emoji: String, onEmojiChange: (String) -> Unit, placeholder: String = "🙂", modifier: Modifier = Modifier) {
    BasicTextField(
        value = emoji,
        // 마지막 한 글자(이모지)만 — 이모지는 여러 코드 단위라 글자 경계를 BreakIterator 로 찾아요
        onValueChange = { onEmojiChange(lastGrapheme(it)) },
        singleLine = true,
        textStyle = TextStyle(fontSize = 22.sp, textAlign = TextAlign.Center, color = NC.textPrimary),
        modifier = modifier
            .size(52.dp)
            .background(NC.bgSubtle, RoundedCornerShape(Radius.input)),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.Center) {
                if (emoji.isEmpty()) Text(placeholder, style = TextStyle(fontSize = 22.sp), color = NC.textPlaceholder)
                inner()
            }
        },
    )
}

/** 문자열의 마지막 "글자" (이모지 한 개처럼 여러 코드 단위로 된 글자도 통째로) */
fun lastGrapheme(s: String): String {
    if (s.isEmpty()) return ""
    val it = java.text.BreakIterator.getCharacterInstance()
    it.setText(s)
    val end = it.last()
    val start = it.previous()
    return if (start == java.text.BreakIterator.DONE) s else s.substring(start, end)
}

// MARK: - 날짜 고르기 ("YYYY-MM-DD" ↔ 머티리얼 날짜 선택 창)

/** 누르면 달력 창이 떠요. minDate/maxDate 는 "YYYY-MM-DD" (그 날 포함) */
@Composable
fun NDateField(
    ymd: String?,
    onYmdChange: (String) -> Unit,
    minDate: String? = null,
    maxDate: String? = null,
    placeholder: String = "날짜 선택",
    enabled: Boolean = true,
    format: (String) -> String = { KST.dotDate(it) + " (" + KST.weekdayLabel[KST.weekdayIndex(it)] + ")" },
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    PickerFieldBox(
        text = if (ymd.isNullOrEmpty()) placeholder else format(ymd),
        isPlaceholder = ymd.isNullOrEmpty(),
        icon = "calendar",
        enabled = enabled,
        modifier = modifier,
    ) { open = true }
    if (open) {
        NDatePickerDialog(
            initial = ymd ?: KST.today(),
            minDate = minDate,
            maxDate = maxDate,
            onDismiss = { open = false },
            onPick = { onYmdChange(it); open = false },
        )
    }
}

/** 날짜 선택 창만 (버튼 모양은 직접 만들 때) */
@Composable
fun NDatePickerDialog(
    initial: String,
    minDate: String? = null,
    maxDate: String? = null,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val minMillis = minDate?.let { KST.utcMillis(it) }
    val maxMillis = maxDate?.let { KST.utcMillis(it) }
    // 머티리얼 달력은 1900~2100년 밖의 처음 날짜를 받으면 죽어요 (예: "9999-12-31", 빈 값) → 그땐 오늘로
    val safeInitial = if (KST.isYmd(initial) && KST.yearOf(initial) in 1900..2100) initial else KST.today()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = KST.utcMillis(safeInitial),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                (minMillis == null || utcTimeMillis >= minMillis) && (maxMillis == null || utcTimeMillis <= maxMillis)
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onPick(KST.ymdFromUtcMillis(it)) } ?: onDismiss() }) {
                Text("확인", style = NType.labelLG, color = NC.textBrand)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소", style = NType.bodyMDMedium, color = NC.textSecondary) }
        },
        colors = DatePickerDefaults.colors(
            containerColor = NC.bgSurface,
            selectedDayContainerColor = NC.actionPrimary,
            selectedDayContentColor = NC.actionPrimaryFg,
            todayDateBorderColor = NC.calendarToday,
            todayContentColor = NC.textBrand,
        ),
    ) {
        DatePicker(state = state, showModeToggle = false)
    }
}

// MARK: - 시각 고르기 (그날 0시부터 몇 분 ↔ 머티리얼 시각 선택 창)

/** 누르면 시각 창이 떠요. minutes 가 null 이면 "--:--" 로 보여요 */
@Composable
fun NTimeField(
    minutes: Int?,
    onMinutesChange: (Int) -> Unit,
    label: String = "",
    placeholder: String = "--:--",
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    PickerFieldBox(
        text = minutes?.let { KST.formatMinutes(it) } ?: placeholder,
        isPlaceholder = minutes == null,
        icon = "clock",
        enabled = enabled,
        modifier = modifier,
    ) { open = true }
    if (open) {
        NTimePickerDialog(
            initialMinutes = minutes ?: (9 * 60),
            title = label,
            onDismiss = { open = false },
            onPick = { onMinutesChange(it); open = false },
        )
    }
}

/** 시각 선택 창만 */
@Composable
fun NTimePickerDialog(initialMinutes: Int, title: String = "", onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val m = ((initialMinutes % 1440) + 1440) % 1440
    val state = rememberTimePickerState(initialHour = m / 60, initialMinute = m % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NC.bgSurface,
        shape = RoundedCornerShape(Radius.dialog),
        title = if (title.isNotEmpty()) ({ Text(title, style = NType.h3, color = NC.textPrimary) }) else null,
        text = {
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(
                    selectorColor = NC.actionPrimary,
                    timeSelectorSelectedContainerColor = NC.actionSecondaryBg,
                    timeSelectorSelectedContentColor = NC.textBrand,
                    clockDialColor = NC.bgSubtle,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) {
                Text("확인", style = NType.labelLG, color = NC.textBrand)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소", style = NType.bodyMDMedium, color = NC.textSecondary) } },
    )
}

/** 날짜 · 시각 · 메뉴 고르기 칸 공통 모양 */
@Composable
fun PickerFieldBox(
    text: String,
    isPlaceholder: Boolean = false,
    icon: String? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(Radius.input)
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .background(if (enabled) NC.bgSurface else NC.disabledBg, shape)
            .border(1.dp, NC.borderSubtle, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text,
            style = NType.bodyMD.copy(fontFeatureSettings = "tnum"),
            color = when {
                !enabled -> NC.disabledFg
                isPlaceholder -> NC.textPlaceholder
                else -> NC.textPrimary
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (icon != null) SfImage(icon, size = 16.dp, tint = NC.textTertiary)
    }
}

// MARK: - 메뉴에서 하나 고르기 (iOS Picker(...).pickerStyle(.menu))

@Composable
fun <T> NMenuPicker(
    selection: T,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
    placeholder: String = "선택",
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.first == selection }?.second
    Box(modifier) {
        PickerFieldBox(
            text = current ?: placeholder,
            isPlaceholder = current == null,
            icon = "chevron.up.chevron.down",
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) { open = true }
        NDropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = NC.bgElevated, shape = RoundedCornerShape(Radius.xl)) {
            for ((value, label) in options) {
                NDropdownMenuItem(
                    text = { Text(label, style = NType.bodyMD, color = NC.textPrimary) },
                    trailingIcon = if (value == selection) ({ NIcon.check.Icon(size = 16.dp, tint = NC.textBrand) }) else null,
                    onClick = { open = false; onSelect(value) },
                )
            }
        }
    }
}

// MARK: - 버튼을 누르면 뜨는 메뉴 (iOS Menu { Button … } label: { … })

data class MenuItem(
    val title: String,
    val icon: String? = null,
    val destructive: Boolean = false,
    val enabled: Boolean = true,
    val action: () -> Unit,
)

@Composable
fun NMenu(
    items: List<MenuItem>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: @Composable BoxScope.() -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Box(
            Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = enabled, role = Role.Button) { open = true },
            contentAlignment = Alignment.Center,
        ) { label() }
        NDropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = NC.bgElevated, shape = RoundedCornerShape(Radius.xl)) {
            for (item in items) {
                val color = if (item.destructive) NC.statusErrorFg else NC.textPrimary
                NDropdownMenuItem(
                    text = { Text(item.title, style = NType.bodyMD, color = color) },
                    leadingIcon = if (item.icon != null) ({ SfImage(item.icon, size = 16.dp, tint = color) }) else null,
                    enabled = item.enabled,
                    onClick = { open = false; item.action() },
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun InputsPreview() {
    NaleulTheme(dark = false) {
        var text by remember { mutableStateOf("") }
        var on by remember { mutableStateOf(true) }
        var view by remember { mutableStateOf("week") }
        var date by remember { mutableStateOf("2026-11-18") }
        var time by remember { mutableStateOf<Int?>(570) }
        var emoji by remember { mutableStateOf("🏃") }
        Column(Modifier.background(NC.canvas).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FieldLabel("목표 이름", hint = "20자 이내", required = true)
            NTextField(text, { text = it }, placeholder = "예: 3개월 동안 5kg 감량")
            FieldMessage("이름을 입력해 주세요.", isError = true)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("알림", style = pt(15)); Spacer(Modifier.weight(1f)); NSwitch(on, { on = it }, label = "알림")
            }
            SegmentPicker(view, { view = it }, options = listOf("day" to "일", "week" to "주", "month" to "월"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NDateField(date, { date = it }, modifier = Modifier.width(180.dp))
                NTimeField(time, { time = it }, modifier = Modifier.width(110.dp))
            }
            EmojiField(emoji, { emoji = it })
        }
    }
}
