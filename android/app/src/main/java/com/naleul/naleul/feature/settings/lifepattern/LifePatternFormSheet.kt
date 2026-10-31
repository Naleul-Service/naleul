// feature/settings/lifepattern/LifePatternFormSheet.kt
//
// iOS Features/Settings/LifePattern/LifePatternFormSheet.swift
// 웹 원본: features/life-pattern/ui/LifePatternFormModal.tsx
//
// 고정 시간 추가·수정 시트. 제목 · 이모지 · 시작/종료 시각 · 반복 요일을 받아요.
// 추가할 때는 제목 아래 추천(+이동시간 +수면시간 +외출준비 …)을 누르면 한 번에 채워져요.
// 웹 <input type="time"> 은 머티리얼 시각 선택 창(NTimePickerDialog)으로 바꿨어요.
// 부르는 쪽이 key(요청 id) { } 로 감싸서, 시트를 열 때마다 초기값으로 새로 그려져요 (iOS .sheet(item:)).

package com.naleul.naleul.feature.settings

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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** 폼 입력값 (웹 LifePatternFormValue). 바꿀 때는 copy() */
data class LifePatternFormValue(
    val patternType: LifePatternType,
    val title: String,
    val emoji: String,
    /** "HH:mm" */
    val startTime: String,
    val endTime: String,
    val days: List<JavaDayOfWeek>,
) {
    companion object {
        val empty = LifePatternFormValue(
            patternType = LifePatternType.CUSTOM, title = "", emoji = "",
            startTime = "09:00", endTime = "10:00", days = LifePatternRules.allDays,
        )

        fun from(preset: LifePatternPreset): LifePatternFormValue = LifePatternFormValue(
            patternType = preset.patternType, title = preset.title, emoji = preset.emoji,
            startTime = preset.startTime, endTime = preset.endTime, days = preset.days,
        )

        fun from(pattern: LifePattern): LifePatternFormValue = LifePatternFormValue(
            patternType = pattern.patternType, title = pattern.title, emoji = pattern.emoji ?: "",
            startTime = LifePatternRules.toHm(pattern.startTime), endTime = LifePatternRules.toHm(pattern.endTime),
            days = pattern.days.ifEmpty { LifePatternRules.allDays },
        )
    }
}

/** 요일별 시간 한 칸 (일반 고정 시간). 파일 밖에서 안 써요 */
private data class LifePatternFormDayTimeEntry(
    val start: String,
    val end: String,
    val offset: Int,
)

/**
 * 고정 시간 추가 · 수정 시트
 * @param editing 수정할 패턴. null 이면 추가
 * @param initial 추가할 때 처음 채울 값 (추천 칩에서 열었을 때)
 * @param patterns 이미 있는 패턴 (수면·점심·저녁 중복 추천 막기)
 * @param onSubmit 저장. 성공하면 true → 시트가 닫혀요
 * @param onEditExisting 추천을 눌렀는데 같은 종류(수면 등)가 이미 있으면 그걸 수정하러 가요
 * @param onDismiss 시트가 닫힐 때
 */
@Composable
fun LifePatternFormSheet(
    editing: LifePattern?,
    initial: LifePatternFormValue?,
    patterns: List<LifePattern>,
    onSubmit: suspend (LifePatternInput) -> Boolean,
    onEditExisting: (LifePattern) -> Unit,
    onDismiss: () -> Unit,
) {
    val hasDayTimes = !(editing?.dayTimes ?: emptyList()).isEmpty()
    var form by remember { mutableStateOf(if (editing != null) LifePatternFormValue.from(pattern = editing) else (initial ?: LifePatternFormValue.empty)) }
    var perDay by remember { mutableStateOf(hasDayTimes) }
    var dayMap by remember {
        mutableStateOf(
            (editing?.dayTimes ?: emptyList()).associate { t ->
                t.day to LifePatternFormDayTimeEntry(
                    start = LifePatternRules.toHm(t.startTime), end = LifePatternRules.toHm(t.endTime), offset = t.startDayOffset,
                )
            }
        )
    }
    /** 수면은 "밤" 단위 (월요일 밤 취침 → 화요일 아침 기상). 비어 있으면 모든 밤이 기본 시간 */
    var nights by remember {
        mutableStateOf(
            if (editing != null && hasDayTimes) LifePatternRules.sleepNights(LifePatternTimes(editing)) else emptyList()
        )
    }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // MARK: 계산 (웹과 같은 규칙)

    val isEdit = editing != null
    val isSleep = form.patternType == LifePatternType.SLEEP
    val defaultNight = LifePatternSleepNight(bed = form.startTime, wake = form.endTime)

    /** 기본 취침이 자정 이후면 다음 날 소속이라 그 요일 기준으로 봐요 */
    val nightShift = LifePatternRules.nightShift(form.startTime)
    fun hasNight(w: Int): Boolean = form.days.contains(LifePatternRules.allDays[(w + nightShift) % 7])
    fun nightAt(w: Int): LifePatternSleepNight? {
        if (!hasNight(w)) return null
        val stored: LifePatternSleepNight? = if (w < nights.size) nights[w] else null
        return stored ?: defaultNight
    }
    val allNights: List<LifePatternSleepNight?> = (0 until 7).map { nightAt(it) }
    val sleepConflict = if (perDay && isSleep) LifePatternRules.sleepConflictDay(allNights) else -1

    fun dayTimeOf(d: JavaDayOfWeek): LifePatternFormDayTimeEntry =
        dayMap[d] ?: LifePatternFormDayTimeEntry(start = form.startTime, end = form.endTime, offset = 0)
    fun isCustomDay(d: JavaDayOfWeek): Boolean {
        val t = dayTimeOf(d)
        return t.offset != 0 || t.start != form.startTime || t.end != form.endTime
    }
    val genericDayTimes: List<LifePatternDayTime> = form.days.filter { isCustomDay(it) }.map { d ->
        val t = dayTimeOf(d)
        LifePatternDayTime(day = d, startTime = t.start, endTime = t.end, startDayOffset = t.offset)
    }
    val badGenericDay: JavaDayOfWeek? =
        if (perDay && !isSleep) form.days.firstOrNull { dayTimeOf(it).start == dayTimeOf(it).end } else null

    // 시간 계산: 종료가 시작보다 이르면 다음 날까지 (수면 23:00 ~ 07:00)
    val startMin = KST.timeToMinutes(form.startTime)
    val rawEndMin = KST.timeToMinutes(form.endTime)
    val timeValid = startMin != rawEndMin
    val crosses = timeValid && rawEndMin < startMin
    val durationMin = (if (crosses) rawEndMin + 1440 else rawEndMin) - startMin

    val trimmedTitle = form.title.trim()
    val valid = trimmedTitle.isNotEmpty() && trimmedTitle.length <= 30 && timeValid && form.days.isNotEmpty() &&
        sleepConflict < 0 && badGenericDay == null

    /** 추천: 추가할 때만, 입력한 제목과 맞는 것 */
    val suggestions: List<LifePatternPreset> = if (isEdit) emptyList() else LifePatternRules.matchPresets(form.title)

    // MARK: Amplitude
    val preview = androidx.compose.ui.platform.LocalInspectionMode.current
    /** "saved" · "next"(다른 고정 시간 수정으로 넘어감) 면 닫혀도 form_close 안 보냄 · "cancel_button" */
    var outcome by remember { mutableStateOf<String?>(null) }
    val openedForm = remember { form }
    DisposableEffect(Unit) {
        onDispose {
            GD.closeMethod(outcome)?.let { m ->
                ST.track("life_pattern.form_close", mapOf(
                    "is_new" to !isEdit, "close_method" to m, "is_dirty" to (form != openedForm || perDay != hasDayTimes),
                ), preview)
            }
        }
    }
    // [UX] 입력 오류 문구가 보일 때 (같은 오류는 한 번만 — 바뀌면 다시)
    val validationError: String? = when {
        !timeValid -> "time_same"
        form.days.isEmpty() -> "no_days"
        sleepConflict >= 0 -> "sleep_conflict"
        badGenericDay != null -> "day_time_same"
        else -> null
    }
    LaunchedEffect(validationError) {
        if (validationError != null) ST.track("life_pattern.form_error_view", mapOf(
            "validation_error" to validationError, "pattern_type" to form.patternType.rawValue, "is_new" to !isEdit,
        ), preview)
    }

    // MARK: 동작

    fun setNight(w: Int, bed: String? = null, wake: String? = null) {
        val next: MutableList<LifePatternSleepNight?> = (0 until 7).map { i -> if (i < nights.size) nights[i] else null }.toMutableList()
        var cur = next[w] ?: defaultNight
        if (bed != null) cur = cur.copy(bed = bed)
        if (wake != null) cur = cur.copy(wake = wake)
        next[w] = cur
        nights = next
    }

    fun setDayTime(d: JavaDayOfWeek, start: String? = null, end: String? = null) {
        var t = dayTimeOf(d)
        if (start != null) t = t.copy(start = start)
        if (end != null) t = t.copy(end = end)
        dayMap = dayMap + (d to t)
    }

    fun applyPreset(p: LifePatternPreset) {
        val existing = LifePatternRules.existingOfType(patterns, p)
        ST.track("life_pattern.form_suggestion_click", mapOf("pattern_type" to p.patternType.rawValue, "is_new" to (existing == null)), preview)
        if (existing != null) {
            outcome = "next"
            onEditExisting(existing)
            return
        }
        form = LifePatternFormValue.from(preset = p)
    }

    /**
     * 제목을 직접 바꾸면 수면·점심·저녁 추천에서 벗어난 것으로 보고 일반 고정 일정으로 저장해요
     * (수정할 때는 종류를 바꿀 수 없어요)
     */
    fun onTitleChange(v: String) {
        val keep = isEdit || v.trim() == LifePatternRules.typeLabel(form.patternType)
        form = if (keep) form.copy(title = v) else form.copy(title = v, patternType = LifePatternType.CUSTOM)
    }

    fun toggleDay(d: JavaDayOfWeek) {
        form = if (form.days.contains(d)) {
            form.copy(days = form.days.filter { it != d })
        } else {
            val current = form.days
            form.copy(days = LifePatternRules.allDays.filter { it == d || current.contains(it) })
        }
    }

    fun isSet(target: List<JavaDayOfWeek>): Boolean = LifePatternRules.sameSet(form.days, target)

    fun submit(close: SheetClose) {
        if (!valid || saving) return
        val emoji = form.emoji.trim()
        val dayTimes: List<LifePatternDayTime> = if (perDay) {
            if (isSleep) {
                LifePatternRules.nightsToDayTimes(defaultStart = form.startTime, defaultEnd = form.endTime, nights = allNights)
            } else {
                genericDayTimes
            }
        } else {
            // 요일별을 끄면 [] → 모든 요일이 기본 시간으로 돌아가요
            emptyList()
        }
        val input = LifePatternInput(
            patternType = form.patternType,
            title = trimmedTitle,
            emoji = emoji.ifEmpty { null },
            startTime = form.startTime,
            endTime = form.endTime,
            // 7개 다 고르면 매일 (백엔드는 빈 배열도 매일로 봐요)
            days = if (form.days.size == 7) emptyList() else form.days,
            dayTimes = dayTimes,
        )
        ST.track("life_pattern.form_save_click", mapOf(
            "is_new" to !isEdit, "pattern_type" to form.patternType.rawValue, "repeat_day_count" to form.days.size,
            "per_day_time" to perDay, "crosses_midnight" to crosses, "duration_minutes" to durationMin,
        ), preview)
        saving = true
        scope.launch {
            val ok = onSubmit(input)
            saving = false
            if (ok) {
                outcome = "saved"
                close()
            }
        }
    }

    // MARK: 화면

    SheetScaffold(
        onDismiss = onDismiss,
        title = if (isEdit) "고정 시간 수정" else "고정 시간 추가",
        description = if (isEdit) "저장하면 오늘부터 모든 날짜의 기본 시간이 바뀌어요."
            else "매주 반복되는 시간을 등록하면 이 시간은 비워두고 Task를 배치해요.",
        dismissible = !saving,
        fullHeight = true,
        footer = { close ->
            NButton("취소", variant = NButtonVariant.secondary, enabled = !saving) {
                outcome = "cancel_button"
                close()
            }
            NButton(if (isEdit) "저장" else "추가", loading = saving, enabled = valid) { submit(close) }
        },
    ) { _ ->
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            // MARK: 제목 + 이모지 + 추천
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FieldLabel("이모지", modifier = Modifier.width(52.dp))
                        EmojiField(emoji = form.emoji, onEmojiChange = { form = form.copy(emoji = it) })
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FieldLabel("제목")
                        NTextField(
                            value = form.title,
                            onValueChange = { onTitleChange(it.take(30)) },
                            placeholder = "예: 출근, 운동, 외출준비",
                            minHeight = 52.dp,
                        )
                    }
                }

                if (suggestions.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            SfImage("sparkles", size = 11.dp, tint = NC.ink3)
                            Text("추천", style = pt(12, PTWeight.medium), color = NC.ink3)
                        }
                        FlowLayout(spacing = 6.dp) {
                            for (p in suggestions) {
                                val existing = LifePatternRules.existingOfType(patterns, p)
                                NChip(
                                    LifePatternRules.presetChipTitle(p, existing = existing),
                                    selected = form.title == p.title && form.patternType == p.patternType,
                                    size = ChipSize.sm,
                                ) { applyPreset(p) }
                            }
                        }
                    }
                }

                if (form.patternType != LifePatternType.CUSTOM) {
                    val ink2 = NC.ink2
                    Text(
                        buildAnnotatedString {
                            append("종류: ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = ink2)) {
                                append(LifePatternRules.typeLabel(form.patternType))
                            }
                            if (form.patternType == LifePatternType.SLEEP) {
                                append(" · 수면 시간은 하루의 시작(기상)과 끝(취침) 기준으로도 쓰여요.")
                            }
                        },
                        style = pt(12),
                        color = NC.ink3,
                    )
                }
            }

            // MARK: 시간
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FieldLabel(if (isSleep) (if (perDay) "기본 취침" else "취침 시간") else (if (perDay) "기본 시작 시간" else "시작 시간"))
                        LifePatternTimeField(time = form.startTime, onTimeChange = { form = form.copy(startTime = it) })
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FieldLabel(if (isSleep) (if (perDay) "기본 기상" else "기상 시간") else (if (perDay) "기본 종료 시간" else "종료 시간"))
                        LifePatternTimeField(time = form.endTime, onTimeChange = { form = form.copy(endTime = it) })
                    }
                }
                FieldMessage(
                    text = if (timeValid) KST.formatDuration(durationMin) + (if (crosses) " · 다음 날까지 이어져요" else "")
                    else "시작과 종료 시간을 다르게 입력해 주세요.",
                    isError = !timeValid,
                )
            }

            // MARK: 반복 요일
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("반복 요일", style = pt(13, PTWeight.semibold), color = NC.ink2)
                    Spacer(Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        NChip("매일", selected = isSet(LifePatternRules.allDays), size = ChipSize.sm) { form = form.copy(days = LifePatternRules.allDays) }
                        NChip("평일", selected = isSet(LifePatternRules.weekdays), size = ChipSize.sm) { form = form.copy(days = LifePatternRules.weekdays) }
                        NChip("주말", selected = isSet(LifePatternRules.weekend), size = ChipSize.sm) { form = form.copy(days = LifePatternRules.weekend) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (d in LifePatternRules.allDays) {
                        LifePatternDayToggle(
                            label = d.label,
                            selected = form.days.contains(d),
                            weekend = d.index >= 5,
                            modifier = Modifier.weight(1f),
                        ) { toggleDay(d) }
                    }
                }
                if (form.days.isEmpty()) {
                    FieldMessage(text = "요일을 하나 이상 골라 주세요.", isError = true)
                }
            }

            // MARK: 요일별 시간 — 예: 주말에는 늦게 일어나기
            if (timeValid && form.days.isNotEmpty()) {
                if (perDay) {
                    val perDayMessage = when {
                        sleepConflict >= 0 -> "${LifePatternRules.allDays[sleepConflict].label}요일 기상이 그날 취침보다 늦어요."
                        badGenericDay != null -> "${badGenericDay.label}요일 시작과 종료를 다르게 입력해 주세요."
                        isSleep -> "파란 요일만 기본과 달라요. 예: 토·일 기상을 늦추면 주말 아침이 그만큼 비워져요."
                        else -> "파란 요일만 기본 시간(${form.startTime}~${form.endTime})과 달라요."
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(NC.subtle.copy(alpha = 0.6f), RoundedCornerShape(Radius.xl))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("요일별 시간", style = pt(13, PTWeight.semibold), color = NC.ink)
                            Spacer(Modifier.weight(1f))
                            Text(
                                "모든 요일 같은 시간으로",
                                style = pt(12, PTWeight.semibold).copy(textDecoration = TextDecoration.Underline),
                                color = NC.ink3,
                                modifier = Modifier.clickable(role = Role.Button) {
                                    ST.track("life_pattern.form_per_day_toggle", mapOf("expanded" to false, "pattern_type" to form.patternType.rawValue), preview)
                                    perDay = false
                                    dayMap = emptyMap()
                                    nights = emptyList()
                                },
                            )
                        }

                        if (isSleep) {
                            // 수면 한 줄: 전날 밤 → 이날 아침 기상 · 이날 밤 취침
                            for (w in 0 until 7) {
                                val d = LifePatternRules.allDays[w]
                                val prev = (w + 6) % 7
                                val morning = nightAt(prev)
                                val night = nightAt(w)
                                val custom = (morning != null && morning.wake != defaultNight.wake) ||
                                    (night != null && night.bed != defaultNight.bed)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    LifePatternDayBadge(label = d.label, highlighted = custom, conflict = sleepConflict == w)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text("☀️ 기상", style = pt(11), color = NC.ink3)
                                        if (morning != null) {
                                            LifePatternTimeField(time = morning.wake, onTimeChange = { setNight(prev, wake = it) }, compact = true)
                                        } else {
                                            Box(Modifier.height(34.dp), contentAlignment = Alignment.CenterStart) {
                                                Text("—", style = pt(13), color = NC.ink4)
                                            }
                                        }
                                    }
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text("🌙 취침", style = pt(11), color = NC.ink3)
                                        if (night != null) {
                                            LifePatternTimeField(time = night.bed, onTimeChange = { setNight(w, bed = it) }, compact = true)
                                        } else {
                                            Box(Modifier.height(34.dp), contentAlignment = Alignment.CenterStart) {
                                                Text("—", style = pt(13), color = NC.ink4)
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // 일반 고정 시간 한 줄
                            for (d in LifePatternRules.allDays.filter { form.days.contains(it) }) {
                                val t = dayTimeOf(d)
                                val custom = isCustomDay(d)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    LifePatternDayBadge(label = d.label, highlighted = custom, conflict = false)
                                    LifePatternTimeField(
                                        time = t.start, onTimeChange = { setDayTime(d, start = it) }, compact = true,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text("~", style = pt(13), color = NC.ink4)
                                    LifePatternTimeField(
                                        time = t.end, onTimeChange = { setDayTime(d, end = it) }, compact = true,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        "기본으로",
                                        style = pt(12, PTWeight.semibold),
                                        color = NC.ink3,
                                        modifier = Modifier
                                            .alpha(if (custom) 1f else 0f)
                                            .clickable(enabled = custom, role = Role.Button) { dayMap = dayMap - d },
                                    )
                                }
                            }
                        }

                        FieldMessage(text = perDayMessage, isError = sleepConflict >= 0 || badGenericDay != null)
                    }
                } else {
                    Text(
                        "+ 요일마다 시간을 다르게 하기" + (if (isSleep) " (예: 주말엔 늦게 일어나기)" else ""),
                        style = pt(13, PTWeight.semibold),
                        color = NC.textBrand,
                        modifier = Modifier.clickable(role = Role.Button) {
                            ST.track("life_pattern.form_per_day_toggle", mapOf("expanded" to true, "pattern_type" to form.patternType.rawValue), preview)
                            perDay = true
                            // 처음 펼치면 지금 기본 시간으로 채워요 (이미 요일별 시간이 있던 패턴은 그대로)
                            if ((editing?.dayTimes ?: emptyList()).isEmpty()) {
                                nights = emptyList()
                                dayMap = emptyMap()
                            }
                        },
                    )
                }
            }
        }
    }
}

// MARK: - 작은 조각들

/**
 * "HH:mm" 문자열 ↔ 시각 선택. 누르면 머티리얼 시각 창이 떠요 (iOS DatePicker(.hourAndMinute))
 * 시각 창은 기기 시간대와 상관없이 "벽시계 값"만 주고받아서 한국 시간 변환이 필요 없어요.
 */
@Composable
fun LifePatternTimeField(
    time: String,
    onTimeChange: (String) -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    PickerFieldBox(
        text = KST.minutesToTime(KST.timeToMinutes(time)),
        icon = if (compact) null else "clock",
        modifier = modifier.fillMaxWidth(),
    ) { open = true }
    if (open) {
        NTimePickerDialog(
            initialMinutes = KST.timeToMinutes(time),
            onDismiss = { open = false },
            onPick = { m ->
                onTimeChange(KST.minutesToTime(m))
                open = false
            },
        )
    }
}

/** 요일 버튼 (월 화 수 …) — 7개가 한 줄에 같은 폭으로 */
@Composable
private fun LifePatternDayToggle(
    label: String,
    selected: Boolean,
    weekend: Boolean,
    modifier: Modifier = Modifier,
    action: () -> Unit,
) {
    Box(
        modifier
            .height(36.dp)
            .clip(CircleShape)
            .background(if (selected) NC.actionPrimary else NC.bgSunken, CircleShape)
            .border(1.dp, if (selected) NC.actionPrimary else NC.borderSubtle, CircleShape)
            .clickable(role = Role.Checkbox) { action() }
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = NType.bodyMDMedium,
            color = if (selected) NC.actionPrimaryFg else (if (weekend) NC.textTertiary else NC.textSecondary),
        )
    }
}

/** 요일별 시간 줄 앞의 요일 칸 (기본과 다르면 파랑, 겹치면 빨간 테두리) */
@Composable
private fun LifePatternDayBadge(label: String, highlighted: Boolean, conflict: Boolean) {
    val shape = RoundedCornerShape(Radius.lg)
    Box(
        Modifier
            .size(32.dp)
            .background(if (highlighted) NC.brand else NC.surface, shape)
            .then(if (conflict) Modifier.border(2.dp, NC.danger, shape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = pt(13, PTWeight.bold), color = if (highlighted) Color.White else NC.ink2)
    }
}

// MARK: - 미리보기

@Preview(showBackground = true, name = "추가")
@Composable
private fun LifePatternFormSheetAddPreview() {
    NaleulTheme(dark = false) {
        LifePatternFormSheet(
            editing = null, initial = null, patterns = LifePatternSamples.patterns,
            onSubmit = { true }, onEditExisting = {}, onDismiss = {},
        )
    }
}

@Preview(showBackground = true, name = "수면 수정 (요일별)")
@Composable
private fun LifePatternFormSheetSleepPreview() {
    NaleulTheme(dark = false) {
        LifePatternFormSheet(
            editing = LifePatternSamples.patterns.firstOrNull(), initial = null, patterns = LifePatternSamples.patterns,
            onSubmit = { true }, onEditExisting = {}, onDismiss = {},
        )
    }
}
