// feature/goal/GoalAiEditBar.kt
//
// iOS Features/Goal/GoalAiEditBar.swift
// 웹 features/goal/ui/GoalAiEditBar.tsx — 목표 상세 "말로 고치기".
// 트리 구조(영역 → 루틴 → 점검 시점)나 수치 계산 방식을 몰라도 한 문장으로 고칠 수 있게 해요.
//  1) 문장을 보내면 AI 가 "바뀔 항목 목록"을 만들어요 — 이때는 아무것도 안 바뀌어요
//  2) 바뀌기 전/후를 보고 원하는 것만 골라 [적용]
// 실행 중인 목표라 바로 바꾸지 않고 꼭 확인을 거쳐요.

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement

private const val GoalAiEditMaxLength = 200

/** '최근 기록 보고 계획 조정받기'가 채우는 문장 (is_prefilled 판단용) — GoalDailyCheckInCard 와 같은 문장 */
private const val GoalAiEditPrefill = "최근 기록과 회고를 보고 계획을 조정해줘"

private val GoalAiEditExamples = listOf(
    "최근 기록 보고 계획 조정해줘",
    "운동은 주 2회로 줄여줘",
    "종료일 2주 늦춰줘",
    "아침 7시에 스트레칭 10분 추가해줘",
)

/** 말로 고치기 입력 줄. autoFocus 면 시트로 열 때 바로 키보드를 올려요 */
@Composable
fun GoalAiEditBar(
    goal: GoalCategory,
    autoFocus: Boolean = false,
    vm: GoalDetailViewModel = LocalGoalDetailViewModel.current!!,
    modifier: Modifier = Modifier,
) {
    var asking by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    // 시트로 열었을 때 어떻게 닫혔는지 (next = 미리보기 시트로 넘어감)
    var outcome by remember { mutableStateOf<String?>(null) }
    if (autoFocus) {
        DisposableEffect(Unit) {
            onDispose {
                // 보내지 않고 X · 쓸어 닫기 · 뒤로 가기
                GD.closeMethod(outcome)?.let { m ->
                    vm.track("goal_detail.ai_edit_sheet_close", mapOf("close_method" to m, "text_length" to vm.aiEditText.length))
                }
            }
        }
    }

    fun submit(method: String) {
        val value = vm.aiEditText.trim()
        if (value.isEmpty() || asking) return
        vm.track("goal_detail.ai_edit_submit", mapOf(
            "text_length" to value.length, "is_prefilled" to (value == GoalAiEditPrefill), "submit_method" to method,
        ))
        val id = goal.goalCategoryId
        asking = true
        focusManager.clearFocus()
        scope.launch {
            val res = Mutation.run(successMessage = null) {
                GoalEditAPI.editPreview(goalId = id, instruction = value)
            }
            asking = false
            vm.track("goal_detail.ai_edit_preview_result", GD.result() + mapOf("change_count" to res?.changes?.size))
            if (res != null) {
                outcome = "next"
                vm.sheet = GoalDetailSheet.aiPreview(res)
            }
        }
    }

    // "오늘 기록" 카드의 "최근 기록 보고 계획 조정받기" → 문장을 채우고 이 칸으로 커서 이동 (처음엔 안 움직여요)
    var lastTick by remember { mutableIntStateOf(vm.aiFocusTick) }
    LaunchedEffect(vm.aiFocusTick) {
        if (vm.aiFocusTick != lastTick) {
            lastTick = vm.aiFocusTick
            runCatching { focus.requestFocus() }
        }
    }
    LaunchedEffect(Unit) {
        if (!autoFocus) return@LaunchedEffect
        // 시트가 다 올라온 뒤에 키보드를 올려야 애니메이션이 겹치지 않아요
        delay(350)
        runCatching { focus.requestFocus() }
    }

    val shape = RoundedCornerShape(Radius.card)
    val ink = NC.ink
    Column(
        modifier
            .fillMaxWidth()
            .aiBorder(shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.heightIn(min = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AIBadge(AIBadgeSize.sm)
            BasicTextField(
                value = vm.aiEditText,
                onValueChange = { vm.aiEditText = it.take(GoalAiEditMaxLength) },
                enabled = !asking,
                singleLine = true,
                textStyle = pt(15).copy(color = ink),
                cursorBrush = SolidColor(NC.brand),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit("keyboard_send") }),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focus)
                    .semantics { contentDescription = "말로 고치기" },
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (vm.aiEditText.isEmpty()) {
                            Text(
                                "바꾸고 싶은 걸 말로 적어 보세요. 예) 운동은 주 2회로 줄여줘",
                                style = pt(15),
                                color = NC.ink4,
                                maxLines = 1,
                            )
                        }
                        inner()
                    }
                },
            )
            NButton(
                "고치기",
                variant = NButtonVariant.ai,
                size = NButtonSize.sm,
                icon = "return",
                loading = asking,
                enabled = vm.aiEditText.trim().isNotEmpty(),
            ) { submit("button") }
        }

        if (vm.aiEditText.isEmpty()) {
            FlowLayout(spacing = 6.dp, modifier = Modifier.padding(start = 28.dp)) {
                for (ex in GoalAiEditExamples) {
                    Text(
                        ex,
                        style = pt(12),
                        color = NC.ink3,
                        modifier = Modifier
                            .clip(CircleShape)
                            .border(1.dp, NC.borderSubtle, CircleShape)
                            .clickable(role = Role.Button) {
                                vm.track("goal_detail.ai_edit_example_click", mapOf("example_index" to GoalAiEditExamples.indexOf(ex)))
                                vm.aiEditText = ex
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

// MARK: - 말로 고치기 시트 (✨ 버튼 · … 메뉴에서 열어요)

@Composable
fun GoalAiEditSheet(
    goal: GoalCategory,
    onDismiss: () -> Unit,
) {
    SheetScaffold(
        onDismiss = onDismiss,
        title = "말로 고치기",
        description = "바꾸고 싶은 걸 한 문장으로 적어 주세요. 바로 바꾸지 않고, 바뀔 내용을 먼저 보여드려요.",
    ) {
        GoalAiEditBar(goal = goal, autoFocus = true)
    }
}

// MARK: - 미리보기 시트 ("이렇게 바꿀까요?")

@Composable
fun GoalAiEditPreviewSheet(
    goalId: Int,
    preview: GoalEditPreview,
    vm: GoalDetailViewModel = LocalGoalDetailViewModel.current!!,
    onDismiss: () -> Unit = { vm.sheet = null },
) {
    val picked = remember { mutableStateListOf<Boolean>().apply { addAll(preview.changes.map { true }) } }
    var applying by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // 어떻게 닫혔는지 (cancel_button · retry_button · saved, 없으면 X · 쓸어 닫기)
    var outcome by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            GD.closeMethod(outcome)?.let { m ->
                vm.track("goal_detail.ai_edit_cancel", mapOf("close_method" to m, "change_count" to preview.changes.size))
            }
        }
    }

    val chosen: List<JsonElement> = preview.changes.indices
        .filter { it < picked.size && picked[it] }
        .map { preview.changes[it].op }

    fun tone(action: String): BadgeTone = when (action) {
        "추가" -> BadgeTone.brand
        "삭제" -> BadgeTone.danger
        else -> BadgeTone.neutral
    }

    fun apply(close: SheetClose) {
        val ops = chosen
        if (ops.isEmpty() || applying) return
        val id = goalId
        vm.track("goal_detail.ai_edit_apply_click", mapOf("selected_count" to ops.size, "change_count" to preview.changes.size))
        applying = true
        scope.launch {
            val ok = Mutation.run(
                invalidate = listOf(DataKey.goals, DataKey.timetable),
                successMessage = "${ops.size}개 변경을 적용했어요.",
            ) {
                GoalEditAPI.editApply(goalId = id, ops = ops)
            }
            applying = false
            vm.track("goal_detail.ai_edit_apply_result", GD.result() + mapOf("selected_count" to ops.size))
            if (ok != null) {
                outcome = "saved"
                vm.aiEditText = ""
                close()
            }
        }
    }

    SheetScaffold(
        onDismiss = onDismiss,
        title = "이렇게 바꿀까요?",
        description = preview.summary.ifEmpty { null },
        dismissible = !applying,
        fullHeight = true,
        footer = { close ->
            if (preview.changes.isEmpty()) {
                NButton("다시 적기", variant = NButtonVariant.secondary) {
                    outcome = "retry_button"
                    close()
                }
            } else {
                NButton("취소", variant = NButtonVariant.secondary, enabled = !applying) {
                    outcome = "cancel_button"
                    close()
                }
                NButton("${chosen.size}개 적용", loading = applying, enabled = chosen.isNotEmpty()) { apply(close) }
            }
        },
    ) {
        if (preview.changes.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "아직 아무것도 바뀌지 않았어요. 적용할 항목만 체크해 주세요.",
                    style = pt(13),
                    color = NC.ink3,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                preview.changes.forEachIndexed { i, c ->
                    val on = i < picked.size && picked[i]
                    val shape = RoundedCornerShape(Radius.xxl)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .alpha(if (on) 1f else 0.6f)
                            .clip(shape)
                            .background(if (on) NC.surface else NC.bgSunken, shape)
                            .border(if (on) 1.5.dp else 1.dp, if (on) NC.borderFocus else NC.borderSubtle, shape)
                            .clickable(enabled = !applying, role = Role.Checkbox) {
                                if (i < picked.size) {
                                    vm.track("goal_detail.ai_edit_item_toggle", mapOf("change_action" to c.action, "checked" to !picked[i]))
                                    picked[i] = !picked[i]
                                }
                            }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        SfImage(
                            if (on) "checkmark.square.fill" else "square",
                            size = 18.dp,
                            tint = if (on) NC.ink else NC.ink4,
                            modifier = Modifier.padding(top = 1.dp),
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                NBadge(c.action, tone = tone(c.action))
                                Text(c.target, style = pt(14, PTWeight.semibold), color = NC.ink)
                            }
                            val b = c.before
                            if (!b.isNullOrEmpty()) {
                                Text(b, style = pt(13).copy(textDecoration = TextDecoration.LineThrough), color = NC.ink3)
                            }
                            val a = c.after
                            if (!a.isNullOrEmpty()) {
                                Text(a, style = pt(14), color = NC.ink)
                            }
                        }
                    }
                }
            }
        }
    }
}

// MARK: - 미리보기

@Preview(showBackground = true, name = "말로 고치기")
@Composable
private fun GoalAiEditBarPreview() {
    NaleulTheme(dark = false) {
        val vm = remember { GoalDetailViewModel.preview() }
        CompositionLocalProvider(LocalGoalDetailViewModel provides vm) {
            Box(Modifier.canvasBackground().padding(16.dp)) {
                GoalAiEditBar(goal = GoalPreviewData.goal)
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 500, name = "말로 고치기 시트")
@Composable
private fun GoalAiEditSheetPreview() {
    NaleulTheme(dark = false) {
        val vm = remember { GoalDetailViewModel.preview() }
        CompositionLocalProvider(LocalGoalDetailViewModel provides vm) {
            GoalAiEditSheet(goal = GoalPreviewData.goal, onDismiss = {})
        }
    }
}

@Preview(showBackground = true, heightDp = 800, name = "변경 미리보기")
@Composable
private fun GoalAiEditPreviewSheetPreview() {
    val preview = GoalEditPreview(
        summary = "운동 루틴을 주 2회로 줄이고 종료일을 2주 늦출게요.",
        changes = listOf(
            GoalEditChange(
                op = json("type" to "UPDATE_ROUTINE", "targetId" to 32),
                action = "변경",
                target = "루틴 '하체 근력 30분'",
                before = "화·목·토",
                after = "화·목",
            ),
            GoalEditChange(op = json("type" to "UPDATE_GOAL"), action = "변경", target = "목표", before = "12월 7일까지", after = "12월 21일까지"),
            GoalEditChange(op = json("type" to "ADD_ROUTINE"), action = "추가", target = "루틴 '아침 스트레칭 10분'", before = null, after = "매일 07:00"),
        ),
    )
    NaleulTheme(dark = false) {
        val vm = remember { GoalDetailViewModel.preview() }
        CompositionLocalProvider(LocalGoalDetailViewModel provides vm) {
            GoalAiEditPreviewSheet(goalId = 1, preview = preview, onDismiss = {})
        }
    }
}
