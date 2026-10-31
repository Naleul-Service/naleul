// feature/record/RecordQuickLogView.kt
//
// iOS Features/Record/RecordQuickLogView.swift (웹 features/record/ui/RecordQuickLog.tsx)
// 기록형 목표 상세 — "오늘 한 일" 한 줄 기록.
// Task 추가(Brain dump)처럼 문장만 적으면 시각·걸린 시간을 읽어서 바로 저장해요.
//  - "저녁 8시 나를 회의 1시간" → 20:00~21:00
//  - "운동 30분"                → 방금 끝낸 30분
//  - "어제 코드 리뷰 30분"       → 어제
// 목표가 이미 정해져 있으니 AI 없이 바로 저장해요 (그래서 빨라요).
//
// sheetMode = true 면 시트 안에 넣는 모양(카드 테두리 없음 · 바로 키보드 · 저장하면 onDone)이에요.
// 시트 틀(SheetScaffold)은 부르는 쪽이 그려요 (iOS 도 부르는 쪽 .sheet 안에 넣었어요).

package com.naleul.naleul.feature.record

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val recordQuickExamples = listOf("주간 회의 1시간", "오후 2시 기획서 작성 2시간", "어제 코드 리뷰 30분")

@Composable
fun RecordQuickLogView(
    goal: GoalCategory,
    sheetMode: Boolean = false,
    onDone: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    /** 업무형 적기 시트에서 쓸 때만 앰플리튜드 work_compose.log_* 를 보내요 */
    analytics: Boolean = false,
    /** 적던 글이 바뀔 때 (적기 시트가 닫힐 때 is_dirty 를 알려고) */
    onDraftChange: ((String) -> Unit)? = null,
    /** 기록에 성공했을 때 (적기 시트의 added_count) */
    onLogged: (() -> Unit)? = null,
) {
    var text by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val value = text.trim()
    // 적는 동안 바로 읽은 결과 (웹 parseLine 과 같은 규칙)
    val preview: ParsedLine? = if (value.isEmpty()) null else LineParser.parse(value, KST.today())

    fun track(event: String, props: Map<String, Any?> = emptyMap()) {
        if (analytics) WK.track(event, goalId = goal.goalCategoryId, props = props)
    }

    suspend fun submit(method: String) {
        val v = value
        if (v.isEmpty() || saving) return
        val parsed = LineParser.parse(v, KST.today())
        track("work_compose.log_submit", mapOf(
            "text_length" to v.length, "has_parsed_time" to (parsed.startTime != null),
            "has_parsed_duration" to (parsed.minutes != null), "name_from_example" to recordQuickExamples.contains(v),
            "submit_method" to method,
        ))
        saving = true
        try {
            val request = RecordActivityLogRequest(
                items = listOf(
                    RecordActivityLogItem(clientKey = RecordAPI.newClientKey(), text = v, goalCategoryId = goal.goalCategoryId),
                ),
            )
            val r = Mutation.run {
                RecordAPI.logActivities(request)
            }
            track("work_compose.log_result", GD.result(Mutation.lastErrorStatus == null && r != null))
            val res = r ?: return
            onLogged?.invoke()
            val keys = mutableListOf(DataKey.activities, DataKey.record)
            if (res.tempGoals.isNotEmpty()) keys.add(DataKey.goals)
            Invalidation.invalidate(keys)
            text = ""
            // 서버가 정한 시간을 알려줘야 "시간 없이 적으면 어떻게 되지?"가 풀려요
            val a = res.activities.firstOrNull()
            if (a != null) Toast.success("기록했어요 · ${KST.hm(a.startAt)}–${KST.hm(a.endAt)}")
            if (sheetMode) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onDone?.invoke()
            }
        } finally {
            saving = false
        }
    }

    val onTextChange: (String) -> Unit = {
        text = it.take(200)
        onDraftChange?.invoke(text)
    }
    val onSubmit: (String) -> Unit = { m -> scope.launch { submit(m) } }
    val onExample: (String) -> Unit = { ex ->
        track("work_compose.log_example_click", mapOf("example_index" to recordQuickExamples.indexOf(ex).takeIf { it >= 0 }))
        onTextChange(ex)
    }

    if (sheetMode) {
        RecordQuickSheetBody(
            text = text,
            onTextChange = onTextChange,
            value = value,
            preview = preview,
            saving = saving,
            onSubmit = onSubmit,
            modifier = modifier,
            onExample = onExample,
        )
    } else {
        RecordQuickCardBody(
            text = text,
            onTextChange = onTextChange,
            value = value,
            preview = preview,
            saving = saving,
            onSubmit = onSubmit,
            modifier = modifier,
            onExample = onExample,
        )
    }
}

/** 시트 모양: 큰 입력칸 + 읽은 시각 + 예시 + 꽉 찬 [기록하기] */
@Composable
private fun RecordQuickSheetBody(
    text: String,
    onTextChange: (String) -> Unit,
    value: String,
    preview: ParsedLine?,
    saving: Boolean,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
    onExample: (String) -> Unit = onTextChange,
) {
    val focus = remember { FocusRequester() }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val ink = NC.ink
    val ink4 = NC.ink4
    val shape = RoundedCornerShape(Radius.xxl)

    // 시트가 다 올라온 뒤 키보드
    LaunchedEffect(Unit) {
        delay(350)
        runCatching { focus.requestFocus() }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            enabled = !saving,
            textStyle = pt(17).copy(color = ink),
            cursorBrush = SolidColor(NC.brand),
            interactionSource = interaction,
            maxLines = 3,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit("keyboard_done") }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus)
                .background(NC.surface, shape)
                .border(if (focused) 1.5.dp else 1.dp, if (focused) NC.borderFocus else NC.borderSubtle, shape)
                .semantics { contentDescription = "한 일" },
            decorationBox = { inner ->
                Box(Modifier.padding(14.dp)) {
                    if (text.isEmpty()) Text("예) ${recordQuickExamples[0]}", style = pt(17), color = ink4)
                    inner()
                }
            },
        )
        RecordQuickPreviewRow(
            preview = preview,
            value = value,
            onExample = onExample,
            modifier = Modifier.defaultMinSize(minHeight = 24.dp),
        )
        Text("시각이 없으면 방금 끝낸 일로 기록돼요. \"어제\"를 붙이면 어제로 남아요.", style = pt(12), color = NC.ink3)
        NButton(
            "기록하기",
            variant = NButtonVariant.brand,
            size = NButtonSize.lg,
            loading = saving,
            fullWidth = true,
            enabled = value.isNotEmpty(),
        ) { onSubmit("button") }
    }
}

@Composable
private fun RecordQuickCardBody(
    text: String,
    onTextChange: (String) -> Unit,
    value: String,
    preview: ParsedLine?,
    saving: Boolean,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
    onExample: (String) -> Unit = onTextChange,
) {
    val ink = NC.ink
    val ink4 = NC.ink4
    val shape = RoundedCornerShape(Radius.xxl)
    Column(modifier.fillMaxWidth().card().padding(20.dp)) {
        Text("오늘 한 일 기록하기", style = NType.h4, color = NC.ink)
        Text(
            "한 줄로 적으면 시각·걸린 시간을 알아서 읽어요. 시각이 없으면 방금 끝낸 일로 기록돼요.",
            style = pt(13),
            color = NC.ink3,
            modifier = Modifier.padding(top = 4.dp),
        )

        Row(
            Modifier
                .padding(top = 16.dp)
                .fillMaxWidth()
                .background(NC.surface, shape)
                .border(1.dp, NC.borderSubtle, shape)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                enabled = !saving,
                singleLine = true,
                textStyle = pt(15).copy(color = ink),
                cursorBrush = SolidColor(NC.brand),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit("keyboard_done") }),
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .semantics { contentDescription = "한 일" },
                decorationBox = { inner ->
                    Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                        if (text.isEmpty()) Text("예) ${recordQuickExamples[0]}", style = pt(15), color = ink4, maxLines = 1)
                        inner()
                    }
                },
            )
            NButton("기록", size = NButtonSize.sm, icon = "return", loading = saving, enabled = value.isNotEmpty()) { onSubmit("button") }
        }

        RecordQuickPreviewRow(
            preview = preview,
            value = value,
            onExample = onExample,
            modifier = Modifier.padding(top = 8.dp).defaultMinSize(minHeight = 24.dp),
        )
    }
}

/** 읽은 시각 · 걸린 시간 (없으면, 아직 비어 있을 때 예시 칩) */
@Composable
private fun RecordQuickPreviewRow(preview: ParsedLine?, value: String, onExample: (String) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        val p = preview
        if (p != null && (p.startTime != null || p.minutes != null)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                val s = p.startTime
                if (s != null) {
                    RecordQuickChip(icon = "clock", text = s + (p.endTime?.let { "–$it" } ?: ""))
                }
                val m = p.minutes
                if (m != null) {
                    RecordQuickChip(icon = "timer", text = KST.formatDuration(m))
                }
            }
        } else if (value.isEmpty()) {
            FlowLayout(spacing = 6.dp) {
                for (ex in recordQuickExamples) {
                    Text(
                        ex,
                        style = pt(12),
                        color = NC.ink3,
                        maxLines = 1,
                        modifier = Modifier
                            .height(24.dp)
                            .clip(CircleShape)
                            .border(1.dp, NC.borderSubtle, CircleShape)
                            .clickable(role = Role.Button) { onExample(ex) }
                            .wrapContentHeight(Alignment.CenterVertically)
                            .padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordQuickChip(icon: String, text: String) {
    Row(
        Modifier
            .height(24.dp)
            .background(NC.subtle, CircleShape)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SfImage(icon, size = 10.dp, tint = NC.ink2)
        Text(text, style = pt(12, PTWeight.semibold), color = NC.ink2, maxLines = 1)
    }
}

@Preview(showBackground = true)
@Composable
private fun RecordQuickLogViewPreview() {
    NaleulTheme(dark = false) {
        Column(Modifier.canvasBackground().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            RecordQuickLogView(goal = RecordSampleData.goal)
            Box(Modifier.background(NC.surface).padding(16.dp)) {
                RecordQuickLogView(goal = RecordSampleData.goal, sheetMode = true)
            }
        }
    }
}
