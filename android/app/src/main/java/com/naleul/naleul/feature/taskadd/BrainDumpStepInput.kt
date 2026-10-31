// feature/taskadd/BrainDumpStepInput.kt
//
// iOS Features/TaskAdd/BrainDumpStepInput.swift
// 웹 원본: features/brain-dump/ui/StepInput.tsx
//
// 1단계 · Task 입력 (Brain dump)
//  - 한 줄에 Task 하나. 키보드의 Enter 를 누를 때마다 목록에 추가돼요.
//  - 여러 줄을 붙여 넣으면 줄마다 나눠서 추가해요.
//  - Free 한도: 목록 개수가 남은 개수에 닿으면 입력창을 막고 안내해요.
//
// iOS · Android 에서 바꾼 점
//  - 웹은 input 의 Enter 키를 잡지만, 앱은 여러 줄 입력칸에 줄바꿈("\n")이 들어오는 순간을
//    "Enter" 로 봐요. 그래서 붙여 넣기(여러 줄)와 Enter 키를 같은 코드로 처리할 수 있어요.
//  - 날짜 칩("날짜 선택")을 누르면 머티리얼 달력 창이 떠요 (iOS 는 달력 시트).
//  - 시간 input → 시각 선택 창. 비어 있을 땐 "--:--" 를 눌러 채워요.
//  - 걸리는 시간 select → 칩을 누르면 메뉴가 떠요.
//  - iOS @Binding lines → lines + onLinesChange 두 개로 받아요.

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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun BrainDumpStepInput(
    lines: List<BrainDumpLine>,
    onLinesChange: (List<BrainDumpLine>) -> Unit,
    quota: TaskQuota?,
    quotaLoading: Boolean,
    today: String,
    loading: Boolean,
    onNext: () -> Unit,
    /** "Pro 알아보기" (멤버십 화면으로) */
    onPro: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    // MARK: 한도 계산 (웹과 같아요)

    /** Free 플랜이면 남은 개수, 아니면 한 번에 20개 */
    val limit: Int = run {
        val q = quota
        val r = q?.remaining
        if (q != null && !q.premium && r != null) minOf(r, BrainDumpConst.maxLines) else BrainDumpConst.maxLines
    }
    val full = lines.size >= limit
    val freeLimited: Boolean = run {
        val q = quota
        val r = q?.remaining
        if (q == null || q.premium || r == null) false else lines.size >= r
    }
    /** 앰플리튜드 — 무료 남은 개수 (Pro 면 null) */
    val quotaRemaining: Int? = quota?.takeIf { !it.premium }?.remaining
    /** limit_hit 의 이유 — 무료 남은 개수가 20보다 적으면 free_quota, 아니면 한 번에 20개(max_lines) */
    val limitReason = if (quotaRemaining != null && quotaRemaining < BrainDumpConst.maxLines) "free_quota" else "max_lines"

    // MARK: 동작

    fun trackLimit(lineCount: Int, dropped: Int) {
        TA.track("task_add_input.limit_hit", mapOf(
            "limit_reason" to limitReason, "quota_remaining" to quotaRemaining,
            "line_count" to lineCount, "dropped_count" to dropped,
        ))
    }

    fun next(method: String) {
        TA.track("task_add_input.next_click", mapOf(
            "line_count" to lines.size,
            "empty_line_count" to lines.count { it.text.trim().isEmpty() },
            "dated_count" to lines.count { it.date != null },
            "timed_count" to lines.count { it.dateType != DateType.DUE && it.startTime != null },
            "duration_count" to lines.count { it.minutes != null },
            "submit_method" to method,
        ))
        onNext()
    }

    fun add(texts: List<String>, method: String) {
        val clean = texts.map { it.trim() }.filter { it.isNotEmpty() }
        if (clean.isEmpty()) return
        val room = maxOf(limit - lines.size, 0)
        val accepted = clean.take(room)
        val added = accepted.map { BrainDumpLine.make(it, today = today) }
        if (added.isNotEmpty()) {
            onLinesChange(lines + added)
        }
        val dropped = clean.size - accepted.size
        val lineCount = lines.size + added.size
        if (added.isNotEmpty()) {
            TA.track("task_add_input.line_add", mapOf(
                "input_method" to method, "added_count" to added.size, "dropped_count" to dropped,
                "line_count" to lineCount,
                "has_date" to added.any { it.date != null },
                "has_parsed_time" to added.any { it.startTime != null },
                "has_parsed_duration" to added.any { it.minutes != null },
            ))
        }
        // 이번에 한도에 닿아 입력칸이 잠겼거나, 붙여 넣은 일부가 빠짐
        if (dropped > 0 || lineCount >= limit) trackLimit(lineCount, dropped)
        if (dropped > 0) {
            Toast.show("${dropped}개는 한도를 넘어서 넣지 않았어요.")
        }
    }

    // 시트를 열자마자 오늘 남은 개수가 0 이라 입력칸이 잠겨 있음
    LaunchedEffect(quotaLoading) {
        if (!quotaLoading && lines.isEmpty() && full) trackLimit(0, 0)
    }

    /** 입력칸에 줄바꿈이 들어오면(= Enter 키 또는 여러 줄 붙여 넣기) 줄마다 나눠서 추가 */
    fun handleDraft(value: String) {
        if (!value.contains("\n")) {
            draft = value.take(200)
            return
        }
        val texts = value.split("\r\n", "\n", "\r").map { it.trim() }.filter { it.isNotEmpty() }
        draft = ""
        if (texts.isEmpty()) {
            // 빈 칸에서 Enter = 다음 단계
            if (lines.isNotEmpty() && !loading) {
                focusManager.clearFocus()
                next("return_empty")
            }
            return
        }
        // 키보드 Enter 는 글 + 줄바꿈 하나, 붙여 넣기는 여러 줄
        add(texts, if (texts.size > 1) "paste" else "enter")
    }

    fun submitDraft() {
        add(listOf(draft), "button")
        draft = ""
    }

    fun update(key: String, patch: (BrainDumpLine) -> BrainDumpLine) {
        val i = lines.indexOfFirst { it.clientKey == key }
        if (i < 0) return
        onLinesChange(lines.toMutableList().also { it[i] = patch(it[i]) })
    }

    fun remove(key: String) {
        val index = lines.indexOfFirst { it.clientKey == key }.coerceAtLeast(0)
        TA.track("task_add_input.line_remove", mapOf("list_position" to index, "line_count" to lines.size - 1))
        onLinesChange(lines.filterNot { it.clientKey == key })
        runCatching { focusRequester.requestFocus() }
    }

    Column(
        modifier
            .fillMaxWidth()
            .card()
            .padding(20.dp),
    ) {
        // MARK: 위쪽 안내 + 남은 개수
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("머릿속에 있는 일을 한 줄씩 적어 주세요", style = NType.h4, color = NC.ink)
            Text(
                "Enter를 누를 때마다 추가돼요. \"저녁 8시 정기 회의\", \"인강 듣기 3시간\"처럼 적으면 시각과 걸리는 시간을 바로 알아채요. 아래 칩으로 고쳐도 돼요.",
                style = pt(14),
                color = NC.ink3,
            )
            val q = quota
            val remaining = q?.remaining
            if (q != null && !q.premium && remaining != null) {
                val text = if (freeLimited) "오늘 추가할 수 있는 만큼 적었어요" else "오늘 ${remaining - lines.size}개 더 추가할 수 있어요"
                Text(
                    text,
                    style = pt(12, PTWeight.semibold),
                    color = if (freeLimited) NC.warningInk else NC.ink2,
                    modifier = Modifier
                        .background(if (freeLimited) NC.warningSoft else NC.subtle, CircleShape)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }

        // MARK: 적은 줄들
        if (lines.isNotEmpty()) {
            Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                lines.forEachIndexed { i, l ->
                    key(l.clientKey) {
                        BrainDumpLineRow(
                            index = i,
                            line = l,
                            today = today,
                            disabled = loading,
                            onChange = { patch -> update(l.clientKey, patch) },
                            onRemove = { remove(l.clientKey) },
                            onTrack = { event, props ->
                                val p = props.toMutableMap()
                                p["list_position"] = i
                                if (event == "task_add_input.line_cleared") p["line_count"] = lines.size
                                TA.track(event, p)
                            },
                        )
                    }
                }
            }
        }

        // MARK: 입력창 (또는 한도 안내)
        Box(Modifier.padding(top = 16.dp)) {
            if (full) {
                val dashColor = NC.lineStrong
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(NC.subtle, RoundedCornerShape(Radius.xxl))
                        .drawBehind {
                            val r = Radius.xxl.toPx()
                            drawRoundRect(
                                color = dashColor,
                                cornerRadius = CornerRadius(r, r),
                                style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                            )
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    SfImage("lock.fill", size = 13.dp, tint = NC.ink3, modifier = Modifier.padding(top = 2.dp))
                    if (freeLimited) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                (if (quota?.remaining == 0) "오늘은 더 추가할 수 없어요. " else "") +
                                    "무료 플랜은 하루 ${quota?.dailyLimit ?: 2}개까지 추가할 수 있어요 ·",
                                style = pt(14),
                                color = NC.ink2,
                            )
                            Text(
                                "Pro 알아보기",
                                style = pt(14, PTWeight.semibold),
                                color = NC.textBrand,
                                modifier = Modifier.clickable(role = Role.Button) {
                                    TA.track("task_add_input.pro_click", mapOf("quota_remaining" to quotaRemaining))
                                    onPro()
                                },
                            )
                        }
                    } else {
                        Text(
                            "한 번에 ${BrainDumpConst.maxLines}개까지 정리할 수 있어요.",
                            style = pt(14),
                            color = NC.ink2,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            } else {
                val shape = RoundedCornerShape(Radius.xxl)
                val canSubmit = draft.trim().isNotEmpty()
                val ink = NC.ink
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(NC.surface, shape)
                        .border(if (focused) 1.5.dp else 1.dp, if (focused) NC.borderFocus else NC.borderSubtle, shape)
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${lines.size + 1}",
                        style = ptMono(14),
                        color = NC.ink4,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(20.dp),
                    )
                    val placeholder = if (lines.isEmpty()) "예) 저녁 8시 정기 회의 · 인강 듣기 3시간" else "다음 할 일 (비워 두고 Enter = 다음 단계)"
                    BasicTextField(
                        value = draft,
                        onValueChange = { handleDraft(it) },
                        enabled = !(loading || quotaLoading),
                        singleLine = false,
                        maxLines = 3,
                        textStyle = pt(15).copy(color = ink),
                        cursorBrush = SolidColor(NC.brand),
                        interactionSource = interaction,
                        // 여러 줄 입력칸이라 Enter 가 줄바꿈("\n")으로 들어와요 → handleDraft 가 줄을 나눠요
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .focusRequester(focusRequester)
                            .semantics { contentDescription = "할 일 입력" },
                        decorationBox = { inner ->
                            Box(Modifier.heightIn(min = 44.dp), contentAlignment = Alignment.CenterStart) {
                                if (draft.isEmpty()) Text(placeholder, style = pt(15), color = NC.ink4, maxLines = 1)
                                inner()
                            }
                        },
                    )
                    Box(
                        Modifier
                            .size(32.dp)
                            .alpha(if (canSubmit) 1f else 0.3f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = canSubmit, role = Role.Button) {
                                submitDraft()
                                runCatching { focusRequester.requestFocus() }
                            }
                            .semantics { contentDescription = "추가" },
                        contentAlignment = Alignment.Center,
                    ) {
                        SfImage("return", size = 14.dp, tint = NC.ink3)
                    }
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
            if (loading) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = NC.ink3, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                    Text("AI가 목표와 연결하고 있어요…", style = pt(14), color = NC.ink3)
                }
            }
            NButton("다음 · 목표 연결", variant = NButtonVariant.ai, icon = "sparkles", loading = loading, enabled = lines.isNotEmpty() && !loading) {
                focusManager.clearFocus()
                next("button")
            }
        }
    }
}

// MARK: - 한 줄 (번호 · 글 · 날짜/시간/걸리는 시간 칩)

@Composable
private fun BrainDumpLineRow(
    index: Int,
    line: BrainDumpLine,
    today: String,
    disabled: Boolean,
    onChange: ((BrainDumpLine) -> BrainDumpLine) -> Unit,
    onRemove: () -> Unit,
    /** 앰플리튜드 (list_position 은 부모가 붙여요) */
    onTrack: (String, EventProps) -> Unit = { _, _ -> },
) {
    // 시작 시각이 이미 있으면(문장에서 읽었으면) 시간 칸을 펼쳐 둬요
    var showTime by remember { mutableStateOf(line.startTime != null) }
    var pickingDate by remember { mutableStateOf(false) }

    val tomorrow = KST.addDays(today, 1)
    /** 오늘 · 내일이 아닌 날을 골랐는지 */
    val customDate: String? = line.date?.takeIf { it != today && it != tomorrow }

    /** 같은 칩을 다시 누르면 날짜를 지워요 */
    fun pick(d: String, kind: String) {
        if (disabled) return
        onTrack("task_add_input.chip_click", mapOf("chip_kind" to kind, "checked" to (line.date != d)))
        onChange { l -> l.copy(date = if (l.date == d) null else d) }
    }

    /** 종료 시각을 처음 채울 땐 시작 + 1시간 */
    val endDefault: String = line.startTime?.let { s -> KST.minutesToTime(minOf(KST.timeToMinutes(s) + 60, 23 * 60 + 50)) } ?: "10:00"

    val shape = RoundedCornerShape(Radius.xxl)
    Column(
        Modifier
            .fillMaxWidth()
            .background(NC.surface, shape)
            .border(1.dp, NC.borderSubtle, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${index + 1}",
                style = ptMono(14),
                color = NC.ink4,
                textAlign = TextAlign.End,
                modifier = Modifier.width(20.dp),
            )
            val ink = NC.ink
            BasicTextField(
                value = line.text,
                onValueChange = { v ->
                    // 이미 추가한 줄을 다 지워 빈 줄이 됨 → 다음 단계로 갈 때 말없이 빠져요
                    if (line.text.isNotBlank() && v.isBlank()) onTrack("task_add_input.line_cleared", emptyMap())
                    onChange { l -> l.copy(text = v.take(200)) }
                },
                enabled = !disabled,
                singleLine = true,
                textStyle = pt(15, PTWeight.medium).copy(color = ink),
                cursorBrush = SolidColor(NC.brand),
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "${index + 1}번 할 일" },
            )
            NIconButton("xmark", size = 14.dp, color = NC.ink4, label = "${index + 1}번 삭제", enabled = !disabled, action = onRemove)
        }

        FlowLayout(spacing = 6.dp, modifier = Modifier.padding(start = 28.dp).alpha(if (disabled) 0.6f else 1f)) {
            NChip("오늘", selected = line.date == today, size = ChipSize.sm) { pick(today, "today") }
            NChip("내일", selected = line.date == tomorrow, size = ChipSize.sm) { pick(tomorrow, "tomorrow") }
            NChip(customDate?.let { KST.formatMonthDay(it) } ?: "날짜 선택", selected = customDate != null, size = ChipSize.sm) {
                if (!disabled) pickingDate = true
            }
            if (line.date != null) {
                NChip("~까지", selected = line.dateType == DateType.DUE, size = ChipSize.sm) {
                    if (!disabled) {
                        onTrack("task_add_input.chip_click", mapOf("chip_kind" to "due", "checked" to (line.dateType != DateType.DUE)))
                        onChange { l ->
                            l.copy(
                                dateType = if (l.dateType == DateType.DUE) DateType.ON else DateType.DUE,
                                startTime = null,
                                endTime = null,
                            )
                        }
                    }
                }
            }
            if (line.dateType != DateType.DUE) {
                NChip("시간", selected = showTime, size = ChipSize.sm, icon = "clock") {
                    if (!disabled) {
                        onTrack("task_add_input.chip_click", mapOf("chip_kind" to "time", "checked" to !showTime))
                        if (showTime) onChange { l -> l.copy(startTime = null, endTime = null) }
                        showTime = !showTime
                    }
                }
            }
            if (showTime && line.dateType != DateType.DUE) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    BrainDumpTimeField(
                        value = line.startTime,
                        onValueChange = { v -> onChange { l -> l.copy(startTime = v) } },
                        label = "${index + 1}번 시작 시각",
                        defaultValue = "09:00",
                        enabled = !disabled,
                    )
                    Text("~", style = pt(12), color = NC.ink3)
                    BrainDumpTimeField(
                        value = line.endTime,
                        onValueChange = { v -> onChange { l -> l.copy(endTime = v) } },
                        label = "${index + 1}번 종료 시각",
                        defaultValue = endDefault,
                        enabled = !disabled,
                    )
                }
            }
            BrainDumpDurationChip(line = line, index = index, disabled = disabled) { m ->
                onTrack("task_add_input.duration_select", mapOf("duration_minutes" to m))
                onChange { l -> l.copy(minutes = m) }
            }
        }
    }

    if (pickingDate) {
        BrainDumpDatePickSheet(
            initial = line.date ?: today,
            minDate = today,
            onPick = { d ->
                onTrack("task_add_input.chip_click", mapOf("chip_kind" to "date", "checked" to true))
                onChange { l -> l.copy(date = d) }
            },
            onDismiss = { pickingDate = false },
        )
    }
}

// MARK: - 걸리는 시간 칩

/**
 * 시작 시각을 몰라도 "3시간 걸리는 일"이면 자동 배치가 3시간짜리 빈칸을 찾아 넣어요.
 * 시작·종료를 둘 다 고르면 그 차이가 소요 시간이라 여기서는 보여주기만 해요.
 */
@Composable
private fun BrainDumpDurationChip(
    line: BrainDumpLine,
    index: Int,
    disabled: Boolean,
    onChange: (Int?) -> Unit,
) {
    val fixed: Int? = run {
        val s = line.startTime
        val e = line.endTime
        if (line.dateType == DateType.DUE || s == null || e == null) null else BrainDumpConst.span(s, e)
    }
    val options: List<Int> = (BrainDumpConst.durations + listOfNotNull(line.minutes)).toSortedSet().toList()
    val value = fixed ?: line.minutes
    val on = value != null

    val items = listOf(MenuItem(title = "AI가 추정") { onChange(null) }) +
        options.map { m -> MenuItem(title = KST.formatDuration(m)) { onChange(m) } }

    NMenu(
        items = items,
        enabled = !(disabled || fixed != null),
        modifier = Modifier.semantics {
            contentDescription = "${index + 1}번 걸리는 시간" +
                if (fixed != null) " (시작·종료 시각으로 정해져요)" else " (비워 두면 AI가 추정)"
        },
    ) {
        val fg = if (on) NC.white else NC.ink2
        Row(
            Modifier
                .height(28.dp)
                .alpha(if (fixed != null) 0.8f else 1f)
                .background(if (on) NC.brand else NC.surface, CircleShape)
                .border(if (on) 1.5.dp else 1.dp, if (on) NC.borderFocus else NC.borderSubtle, CircleShape)
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SfImage("timer", size = 11.dp, tint = fg)
            Text(value?.let { KST.formatDuration(it) } ?: "걸리는 시간", style = pt(12, PTWeight.medium), color = fg, maxLines = 1)
        }
    }
}

// MARK: - 시각 입력 (웹 <input type="time">) — 2단계 변경 시트에서도 써요

/**
 * "HH:mm" 문자열(없으면 null)을 고르는 칸. 비어 있으면 "--:--" 를 눌러 채우고, x 로 지워요.
 * 값이 있을 때 시각을 누르면 머티리얼 시각 선택 창이 떠요 (iOS DatePicker .hourAndMinute).
 */
@Composable
fun BrainDumpTimeField(
    value: String?,
    onValueChange: (String?) -> Unit,
    label: String,
    /** 처음 누를 때 채울 시각 */
    defaultValue: String = "09:00",
    /** 칸을 넓게 (변경 시트) */
    wide: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var picking by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (wide) Radius.xl else Radius.lg)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        val boxModifier = Modifier
            .then(if (wide) Modifier.weight(1f) else Modifier)
            .height(if (wide) 44.dp else 28.dp)
            .clip(shape)
            .background(NC.surface, shape)
            .border(1.dp, NC.borderSubtle, shape)
        if (value != null) {
            Box(
                boxModifier
                    .clickable(enabled = enabled, role = Role.Button) { picking = true }
                    .padding(horizontal = 10.dp)
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(value.take(5), style = ptMono(if (wide) 15 else 13), color = NC.ink)
            }
            Box(
                Modifier
                    .size(width = 24.dp, height = 28.dp)
                    .clickable(enabled = enabled, role = Role.Button) { onValueChange(null) }
                    .semantics { contentDescription = "$label 지우기" },
                contentAlignment = Alignment.Center,
            ) {
                SfImage("xmark.circle.fill", size = 14.dp, tint = NC.ink4)
            }
        } else {
            Box(
                boxModifier
                    .clickable(enabled = enabled, role = Role.Button) { onValueChange(defaultValue) }
                    .padding(horizontal = 10.dp)
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.CenterStart,
            ) {
                Text("--:--", style = ptMono(if (wide) 15 else 13), color = NC.ink3)
            }
        }
    }
    if (picking && value != null) {
        NTimePickerDialog(
            initialMinutes = KST.timeToMinutes(value),
            title = label,
            onDismiss = { picking = false },
            onPick = { m ->
                onValueChange(KST.minutesToTime(m))
                picking = false
            },
        )
    }
}

// MARK: - 날짜 고르기 (웹 <input type="date" min={today}>)

/** iOS 는 달력 시트, Android 는 머티리얼 날짜 선택 창 */
@Composable
private fun BrainDumpDatePickSheet(
    initial: String,
    minDate: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    NDatePickerDialog(
        initial = maxOf(initial, minDate),
        minDate = minDate,
        onDismiss = onDismiss,
        onPick = { d ->
            onPick(d)
            onDismiss()
        },
    )
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun BrainDumpStepInputPreview() {
    NaleulTheme(dark = false) {
        val today = KST.today()
        var lines by remember {
            mutableStateOf(
                listOf(
                    BrainDumpLine.make("저녁 8시 정기 회의", today = today),
                    BrainDumpLine.make("인강 듣기 3시간", today = today),
                )
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .canvasBackground()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            BrainDumpStepInput(
                lines = lines,
                onLinesChange = { lines = it },
                quota = TaskQuota(premium = false, dailyLimit = 2, usedToday = 0, remaining = 2),
                quotaLoading = false,
                today = today,
                loading = false,
                onNext = {},
                onPro = {},
            )
        }
    }
}
