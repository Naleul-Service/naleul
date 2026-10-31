@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

// Features/Goal/GoalAddScreen.kt
//
// iOS GoalAddScreen.swift — 웹 app/(main)/goal/add/page.tsx + features/goal/ui/GoalAddView.tsx — 목표 추가.
// 목표는 성격에 따라 세 가지 (V52):
//  - 기본 생활 습관: 했는지만 체크하는 습관. 모든 사용자에게 하나씩 이미 있어서 습관 화면으로 보내요 (goal_mode = HABIT)
//  - 업무형: 회사 업무 · 취업 준비 · 동아리 등. 주제마다 마감을 둔 로드맵 + Task 보드 (RecordGoalForm)
//  - 목표형: 수치로 잴 수 있는 목표. AI 가 대화로 "어떻게 이룰지"를 묻고 루틴까지 계획 (AppRouter.startGoalCreation), 계획이 있으면 직접 입력
// initialMode 가 record 면 업무형 폼, manual 이면 생활형 직접 입력 폼을 바로 열어요.

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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val goalAddHabitExamples = listOf("영양제 먹기", "물 2L 마시기", "샤워하기", "집안일 하기", "금연 · 금주", "일기 쓰기")
private val goalAddWorkExamples = listOf("취업 준비", "OO팀 업무", "동아리 활동", "사이드 프로젝트")
private val goalAddLifeExamples = listOf("3개월에 5kg 감량", "SQLD 합격", "토익 900점", "1000만원 모으기", "올해 책 24권")

@Composable
fun GoalAddScreen(initialMode: GoalAddMode? = null) {
    var open by remember { mutableStateOf(initialMode) }
    /** 기본 생활 습관 그릇 (처음 부르면 서버가 만들어요) */
    var habitHome by remember { mutableStateOf<HabitHome?>(null) }
    /** 이 자리에서 습관 추가 시트 (2026-10 — "보러 가기" 대신) */
    var habitSheet by remember { mutableStateOf(false) }

    /** 직접 만들기 시트 열기 (2026-10 — 아래에 펼치던 폼을 BottomSheet 로) */
    fun openForm(mode: GoalAddMode) {
        if (open == mode) return
        GD.track("goal_add.mode_open", mapOf(
            "goal_mode" to if (mode == GoalAddMode.record) "RECORD" else "ACHIEVEMENT", "from_mode" to (open?.name ?: "none"),
            "presentation" to "sheet",
        ))
        open = mode
    }

    /** 직접 만든 뒤: 시트를 닫고 "보러 가기" 토스트 (바로 이동하지 않아요) */
    fun created(goalMode: String, goalId: Int) {
        open = null
        val label = if (goalMode == "RECORD") "업무형 목표를 만들었어요" else "목표를 만들었어요"
        GD.track("goal_add.created_prompt_view", mapOf("goal_mode" to goalMode, "goal_category_id" to goalId))
        Toast.success(label, "보러 가기") {
            GD.track("goal_add.created_prompt_click", mapOf("goal_mode" to goalMode, "goal_category_id" to goalId, "prompt_action" to "go"))
            AppRouter.openGoal(goalId)
        }
    }

    LaunchedEffect(Unit) { GD.track("goal_add.pageview", mapOf("initial_mode" to (initialMode?.name ?: "none"))) }

    ReloadOn(listOf(DataKey.habits)) {
        habitHome = try {
            HabitAPI.home()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    PushedScreen(title = "목표 추가") {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("어떤 성격의 목표인가요?", style = pt(14), color = NC.ink3)

            GoalAddHabitCard(habitHome, onAdd = { habitSheet = true })
            GoalAddWorkCard(selected = false, onOpen = { openForm(GoalAddMode.record) })
            GoalAddLifeCard(
                selected = false,
                onOpen = { openForm(GoalAddMode.manual) },
                onAi = { GD.track("goal_add.ai_click", mapOf("form_open" to (open?.name ?: "none"))) },
            )
        }
    }

    when (open) {
        GoalAddMode.record -> SheetScaffold(
            onDismiss = { open = null },
            title = "업무형 목표 만들기",
            fullHeight = true,
        ) {
            GoalSheetForm {
                RecordGoalForm(onCancel = { open = null }, analytics = true, onCreated = { id -> created("RECORD", id) })
            }
        }
        GoalAddMode.manual -> SheetScaffold(
            onDismiss = { open = null },
            title = "목표형 직접 만들기",
            fullHeight = true,
        ) {
            GoalSheetForm {
                GoalManualForm(onCancel = { open = null }, onCreated = { id -> created("ACHIEVEMENT", id) })
            }
        }
        null -> Unit
    }

    if (habitSheet) {
        val home = habitHome
        HabitFormSheet(
            habit = null,
            existingNames = home?.habits?.map { it.name } ?: emptyList(),
            onDone = { habitSheet = false },
            onCreated = { name ->
                val habitGoalId = home?.goalCategoryId
                GD.track("goal_add.habit_quick_add", mapOf(
                    "from_example" to false, "habit_count" to ((home?.habits?.size ?: 0) + 1), "submit_method" to "button",
                ))
                GD.track("goal_add.created_prompt_view", mapOf("goal_mode" to "HABIT", "goal_category_id" to habitGoalId))
                Toast.success("'$name' 습관을 추가했어요", "보러 가기") {
                    val goalId = habitHome?.goalCategoryId ?: habitGoalId
                    GD.track("goal_add.created_prompt_click", mapOf("goal_mode" to "HABIT", "goal_category_id" to goalId, "prompt_action" to "go"))
                    if (goalId != null) AppRouter.openGoal(goalId)
                }
            },
        )
    }
}

/** 카드 공통 모양 (iOS GoalAddCardStyle) */
@Composable
private fun Modifier.goalAddCard(selected: Boolean): Modifier {
    val shape = RoundedCornerShape(Radius.card)
    return this
        .fillMaxWidth()
        .background(NC.surface, shape)
        .border(if (selected) 1.5.dp else 1.dp, if (selected) NC.borderFocus else NC.borderSubtle, shape)
        .padding(20.dp)
}

/** 카드 왼쪽 위 44pt 아이콘 */
@Composable
private fun GoalAddIcon(name: String, tint: Color, background: Color) {
    Box(Modifier.size(44.dp).background(background, RoundedCornerShape(Radius.xxl)), contentAlignment = Alignment.Center) {
        SfImage(name, size = 18.dp, tint = tint)
    }
}

/** 제목 + 배지 */
@Composable
private fun GoalAddTitle(title: String, badge: String, tone: BadgeTone) {
    FlowLayout(spacing = 8.dp, modifier = Modifier.padding(top = 16.dp)) {
        Text(title, style = NType.h4, color = NC.ink)
        NBadge(badge, tone = tone)
    }
}

@Composable
private fun GoalAddDescription(text: String) {
    Text(text, style = pt(14).copy(lineHeight = 1.5.em), color = NC.ink3, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun GoalAddExamples(items: List<String>) {
    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("이런 걸 담아요", style = pt(12, PTWeight.medium), color = NC.ink3)
        FlowLayout(spacing = 6.dp) {
            for (x in items) {
                Text(
                    x,
                    style = pt(12),
                    color = NC.ink2,
                    modifier = Modifier.background(NC.subtle, CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/** 글자 + 아이콘 링크 버튼 */
@Composable
private fun GoalAddLink(
    text: String,
    color: Color,
    leadingIcon: String? = null,
    trailingIcon: String? = null,
    trailingRotation: Float = 0f,
    weight: PTWeight = PTWeight.semibold,
    size: Int = 14,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { if (selected) contentDescription = "$text (선택됨)" }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (leadingIcon != null) SfImage(leadingIcon, size = (size - 1).dp, tint = color)
        Text(text, style = pt(size, weight), color = color)
        if (trailingIcon != null) SfImage(trailingIcon, size = 13.dp, tint = color, modifier = Modifier.rotate(trailingRotation))
    }
}

// MARK: 기본 생활 습관 (이미 하나씩 있어요 — 만들기 대신 습관 화면으로)

@Composable
private fun GoalAddHabitCard(habitHome: HabitHome?, onAdd: () -> Unit) {
    Column(Modifier.goalAddCard(selected = false)) {
        GoalAddIcon("checklist", tint = NC.success, background = NC.successSoft)
        GoalAddTitle("기본 생활 습관", "체크만", BadgeTone.success)
        GoalAddDescription("매일 챙겨야 하는데 잊기 쉬운 것들이에요. 마감 없이 했는지만 체크하고, 잔디로 쌓여요. 필요 없으면 쉬거나 지우면 돼요.")
        GoalAddExamples(goalAddHabitExamples)
        if (habitHome != null) {
            // 2026-10: 이 자리에서 바로 추가 (습관 화면은 아래 작은 글자로)
            Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GoalAddLink(
                    text = "습관 추가하기",
                    color = NC.success,
                    leadingIcon = "plus",
                    trailingIcon = "arrow.right",
                    onClick = onAdd,
                )
                if (habitHome.habits.isNotEmpty()) {
                    GoalAddLink(
                        text = "습관 ${habitHome.habits.size}개 보러 가기",
                        color = NC.ink3,
                        weight = PTWeight.medium,
                        size = 13,
                    ) {
                        GD.track("goal_add.habit_click", mapOf("habit_count" to habitHome.habits.size))
                        AppRouter.openGoal(habitHome.goalCategoryId)
                    }
                }
            }
        } else {
            Box(
                Modifier
                    .padding(top = 20.dp)
                    .size(width = 128.dp, height = 18.dp)
                    .background(NC.subtle, RoundedCornerShape(4.dp)),
            )
        }
    }
}

// MARK: 업무형

@Composable
private fun GoalAddWorkCard(selected: Boolean, onOpen: () -> Unit) {
    Column(Modifier.goalAddCard(selected = selected)) {
        GoalAddIcon("briefcase", tint = NC.textBrand, background = NC.actionSecondaryBg)
        GoalAddTitle("업무형", "Task 관리", BadgeTone.neutral)
        GoalAddDescription("수치보다 해야 할 일이 많은 일이에요. 취업 준비 · 동아리 · 회사 업무처럼 단계(세부 목표)마다 마감을 정하고 남은 일을 체크해요.")
        GoalAddExamples(goalAddWorkExamples)
        GoalAddLink(
            text = "업무 목표 만들기",
            color = NC.ink,
            trailingIcon = "arrow.right",
            trailingRotation = if (selected) 90f else 0f,
            selected = selected,
            modifier = Modifier.padding(top = 20.dp),
            onClick = onOpen,
        )
    }
}

// MARK: 생활형

@Composable
private fun GoalAddLifeCard(selected: Boolean, onOpen: () -> Unit, onAi: () -> Unit = {}) {
    Column(Modifier.goalAddCard(selected = selected)) {
        GoalAddIcon("leaf", tint = NC.textBrand, background = NC.brandSoft)
        GoalAddTitle("목표형", "수치 · 기한", BadgeTone.brand)
        GoalAddDescription("숫자로 잴 수 있는 목표예요. AI가 지금 상황과 계획을 물어본 뒤, 오늘 어디까지 하면 되는지 그래프와 루틴으로 짜 드려요.")
        GoalAddExamples(goalAddLifeExamples)
        Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GoalAddLink(
                text = "AI와 계획 세우기",
                color = NC.textBrand,
                leadingIcon = "message",
                trailingIcon = "arrow.right",
            ) {
                onAi()
                AppRouter.startGoalCreation()
            }
            GoalAddLink(
                text = "직접 입력하기",
                color = NC.ink3,
                leadingIcon = "pencil.line",
                weight = PTWeight.medium,
                size = 13,
                selected = selected,
                onClick = onOpen,
            )
        }
    }
}

// MARK: - 생활형 직접 만들기 폼 (웹 ManualGoalForm)

@Composable
private fun GoalManualForm(onCancel: () -> Unit, onCreated: ((Int) -> Unit)? = null) {
    var colors by remember { mutableStateOf<List<UserColor>>(emptyList()) }
    var emoji by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var start by remember { mutableStateOf(KST.today()) }
    var end by remember { mutableStateOf(KST.addDays(KST.today(), 90)) }
    var colorId by remember { mutableStateOf<Int?>(null) }
    var motive by remember { mutableStateOf("") }
    var metricName by remember { mutableStateOf("") }
    var metricUnit by remember { mutableStateOf("") }
    var startValue by remember { mutableStateOf("") }
    var targetValue by remember { mutableStateOf("") }
    var showMetric by remember { mutableStateOf(false) }
    var kind by remember { mutableStateOf<GoalKindValue?>(null) }
    var subs by remember { mutableStateOf<List<String>>(emptyList()) }
    var subDraft by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (colors.isEmpty()) {
            colors = try {
                ColorAPI.userColors()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    val effectiveColor: Int? = colorId ?: GoalDetailLogic.colorIdOf(colors, null)
    val nums: List<Double?> = listOf(GoalDetailLogic.toNum(startValue), GoalDetailLogic.toNum(targetValue))
    val trimmedName = name.trim()

    val error: String? = when {
        trimmedName.isEmpty() -> null
        start.isEmpty() || end.isEmpty() -> "기간을 입력해 주세요."
        start > end -> "종료일이 시작일보다 빨라요."
        nums.any { it?.isNaN() == true } -> "수치는 숫자로 입력해 주세요."
        !GoalKind.isComplete(kind) -> "카테고리를 골라 주세요. (기타는 이름 1~10자)"
        else -> null
    }
    val valid = trimmedName.isNotEmpty() && error == null && effectiveColor != null
    val hasInput = trimmedName.isNotEmpty() || emoji.isNotEmpty() || motive.trim().isNotEmpty() ||
        subs.isNotEmpty() || subDraft.isNotEmpty() || kind != null || showMetric

    // [UX] 빨간 문구가 뜰 때 (같은 문구가 이어지면 한 번)
    LaunchedEffect(error) { error?.let { GD.track("goal_add.manual_error", mapOf("validation_error" to it)) } }

    fun addSub() {
        val v = subDraft.trim()
        if (v.isEmpty()) return
        if (!subs.contains(v) && subs.size < 10) subs = subs + v.take(30)
        subDraft = ""
    }

    fun submit() {
        val color = effectiveColor
        if (!valid || saving || color == null) return
        // 입력칸에 적어 두고 추가를 안 누른 것도 넣어요
        var allSubs = subs
        val draft = subDraft.trim()
        if (draft.isNotEmpty() && !subs.contains(draft)) allSubs = allSubs + draft
        val trimmedMotive = motive.trim()
        val trimmedEmoji = emoji.trim()
        val metricOn = showMetric && metricName.trim().isNotEmpty()
        val input = GoalCreateInput(
            goalCategoryName = trimmedName,
            goalCategoryStartDate = start,
            goalCategoryEndDate = end,
            colorId = color,
            motive = if (trimmedMotive.isEmpty()) null else trimmedMotive,
            emoji = if (trimmedEmoji.isEmpty()) null else trimmedEmoji,
            metricName = if (metricOn) metricName.trim() else null,
            metricUnit = if (metricOn) metricUnit.trim() else null,
            startValue = if (metricOn) nums[0] else null,
            targetValue = if (metricOn) nums[1] else null,
            hasMetric = metricOn,
            kind = kind,
            subGoals = allSubs,
        )
        val typedValue = startValue.trim().isNotEmpty() || targetValue.trim().isNotEmpty()
        GD.track("goal_add.manual_submit", mapOf(
            "goal_type" to kind?.goalType?.name, "goal_sub_type" to kind?.goalSubType?.name,
            "period_days" to KST.diffDays(start, end) + 1, "has_metric" to metricOn,
            "metric_dropped" to (showMetric && !metricOn && typedValue),
            "has_motive" to (input.motive != null), "has_emoji" to (input.emoji != null), "color_changed" to (colorId != null),
            "sub_goal_count" to allSubs.size,
        ))
        saving = true
        scope.launch {
            val r = Mutation.run(
                invalidate = listOf(DataKey.goals),
                success = { res: GoalEditAPI.CreatedGoal ->
                    if (res.failed > 0 || onCreated != null) null else "목표를 만들었어요. 루틴·마일스톤·Task도 바로 추가해 보세요."
                },
            ) {
                GoalEditAPI.createGoal(input)
            }
            saving = false
            GD.track("goal_add.create_result", GD.result() + mapOf(
                "goal_mode" to "ACHIEVEMENT", "goal_category_id" to r?.goal?.goalCategoryId,
            ))
            if (r != null) {
                if (r.failed > 0) {
                    Toast.error("목표는 만들었지만 세부 목표 ${r.failed}개를 만들지 못했어요. 상세 화면에서 다시 추가해 주세요.")
                }
                val created = onCreated
                if (created != null) created(r.goal.goalCategoryId) else AppRouter.replaceWithGoal(r.goal.goalCategoryId)
            }
        }
    }

    GoalInlineForm(
        submitLabel = "목표 만들기",
        saving = saving,
        valid = valid,
        error = error,
        onCancel = {
            GD.track("goal_add.manual_cancel", mapOf("has_input" to hasInput))
            onCancel()
        },
        onSubmit = { submit() },
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GoalFormField("이모지", modifier = Modifier.width(72.dp)) {
                NTextField(
                    value = emoji,
                    onValueChange = { emoji = it },
                    placeholder = "🎯",
                    maxLength = 16,
                    textStyle = pt(15).copy(textAlign = TextAlign.Center),
                )
            }
            GoalFormField("목표 이름", modifier = Modifier.weight(1f)) {
                NTextField(value = name, onValueChange = { name = it }, placeholder = "예: 토익 900점 달성", maxLength = 50)
            }
        }
        GoalFormField("카테고리") {
            GoalKindPicker(value = kind, onValueChange = { kind = it }, compact = true)
        }
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GoalFormField("시작일", modifier = Modifier.weight(1f)) { GoalDateInput(value = start, onValueChange = { start = it }) }
            GoalFormField("종료일", modifier = Modifier.weight(1f)) { GoalDateInput(value = end, onValueChange = { end = it }) }
        }
        GoalFormField("색상") {
            ColorPickerRow(colors = colors, selectedId = effectiveColor, onSelectedIdChange = { colorId = it })
        }
        GoalFormField("이 목표를 시작한 이유 (선택)") {
            NTextField(value = motive, onValueChange = { motive = it }, minHeight = 64.dp, singleLine = false, maxLength = 500)
        }

        // 세부 목표: 완료 키로 하나씩
        GoalFormField("세부 목표 (선택 · 완료 키로 추가)") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (subs.isNotEmpty()) {
                    FlowLayout(spacing = 6.dp) {
                        for (s in subs) {
                            Row(
                                Modifier
                                    .height(28.dp)
                                    .background(NC.brandSoft, CircleShape)
                                    .padding(start = 10.dp, end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(s, style = pt(13, PTWeight.semibold), color = NC.textBrand)
                                SfImage(
                                    "xmark",
                                    size = 10.dp,
                                    tint = NC.textBrand,
                                    contentDescription = "$s 빼기",
                                    modifier = Modifier.clickable(role = Role.Button) { subs = subs.filter { it != s } },
                                )
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NTextField(
                        value = subDraft,
                        onValueChange = { subDraft = it },
                        placeholder = if (subs.isEmpty()) "예: LC 연습, 단어 외우기" else "",
                        maxLength = 30,
                        imeAction = ImeAction.Next,
                        onSubmit = { addSub() },
                        modifier = Modifier.weight(1f),
                    )
                    if (subDraft.trim().isNotEmpty()) {
                        NButton("추가", variant = NButtonVariant.secondary, size = NButtonSize.sm) { addSub() }
                    }
                }
            }
        }

        if (showMetric) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GoalFormField("수치 이름", modifier = Modifier.weight(1f)) {
                        NTextField(value = metricName, onValueChange = { metricName = it }, placeholder = "체중", maxLength = 30)
                    }
                    GoalFormField("단위", modifier = Modifier.weight(1f)) {
                        NTextField(value = metricUnit, onValueChange = { metricUnit = it }, placeholder = "kg", maxLength = 10)
                    }
                }
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GoalFormField("시작", modifier = Modifier.weight(1f)) {
                        NTextField(value = startValue, onValueChange = { startValue = it }, placeholder = "80", keyboardType = KeyboardType.Decimal)
                    }
                    GoalFormField("목표", modifier = Modifier.weight(1f)) {
                        NTextField(value = targetValue, onValueChange = { targetValue = it }, placeholder = "72", keyboardType = KeyboardType.Decimal)
                    }
                }
            }
        } else {
            Text(
                "+ 수치 목표 추가 (예: 체중 80 → 72kg)",
                style = pt(13, PTWeight.semibold),
                color = NC.textBrand,
                modifier = Modifier.clickable(role = Role.Button) {
                    GD.track("goal_add.manual_metric_add_click")
                    showMetric = true
                },
            )
        }
        Text("만든 뒤 목표 상세에서 루틴·마일스톤·Task를 바로 추가할 수 있어요.", style = pt(12), color = NC.ink3)
    }
}

@Preview(showBackground = true, heightDp = 1600)
@Composable
private fun GoalAddScreenPreview() {
    NaleulTheme(dark = false) {
        GoalAddScreen(initialMode = null)
    }
}

@Preview(showBackground = true, heightDp = 1200)
@Composable
private fun GoalManualFormPreview() {
    NaleulTheme(dark = false) {
        Column(Modifier.canvasBackground().padding(16.dp)) {
            GoalManualForm(onCancel = {})
        }
    }
}
