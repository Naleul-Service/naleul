// feature/goal/GoalDailyCheckInCard.kt
//
// iOS Features/Goal/GoalDailyCheckInCard.swift
// 웹 features/goal/ui/progress/DailyCheckInCard.tsx — 목표 상세 맨 위 "오늘 기록".
//  - 수치 목표면 오늘 수치 (예: 64kg)
//  - 오늘 어땠는지 (잘했어요 · 그럭저럭 · 아쉬워요)
//  - 무엇을 했는지 한 줄 회고 (예: "점심에 마라탕 먹음")
// 남긴 기록은 "말로 고치기" AI 가 계획을 조정할 때 근거로 써요.

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** 그날 느낌 선택지 (웹 MOODS) — 홈 회고 · 회고 모아보기에서도 써요 */
data class GoalMoodOption(
    val value: ReflectionMood,
    val emoji: String,
    val label: String,
) {
    val id: ReflectionMood get() = value

    companion object {
        val all: List<GoalMoodOption> = listOf(
            GoalMoodOption(value = ReflectionMood.GOOD, emoji = "😊", label = "잘했어요"),
            GoalMoodOption(value = ReflectionMood.OK, emoji = "🙂", label = "그럭저럭"),
            GoalMoodOption(value = ReflectionMood.BAD, emoji = "😣", label = "아쉬워요"),
        )

        fun of(m: ReflectionMood?): GoalMoodOption? {
            if (m == null) return null
            return all.firstOrNull { it.value == m }
        }
    }
}

/** 오늘 탭의 작은 요약 — 기록은 오른쪽 아래 ✏️ 버튼(또는 이 카드)을 눌러 시트에서 해요 */
@Composable
fun GoalDailyCheckInCard(
    goal: GoalCategory,
    vm: GoalDetailViewModel = LocalGoalDetailViewModel.current!!,
    modifier: Modifier = Modifier,
) {
    val p = vm.progress ?: return
    val today = GoalCheckInToday(p)
    if (today.value != null || today.reflection != null) {
        GoalCheckInDoneCard(p = p, today = today, vm = vm, modifier = modifier)
    } else {
        GoalCheckInPromptRow(vm = vm, modifier = modifier)
    }
}

/** 아직 안 남겼을 때: 한 줄 안내 (누르면 시트) */
@Composable
private fun GoalCheckInPromptRow(vm: GoalDetailViewModel, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Radius.card)
    val dash = NC.brand.copy(alpha = 0.35f)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(NC.surface, shape)
            // 점선 테두리 (iOS StrokeStyle(dash: [5, 4]))
            .drawBehind {
                val w = 1.5.dp.toPx()
                drawRoundRect(
                    color = dash,
                    topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius(18.dp.toPx()),
                    style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
                )
            }
            .clickable(role = Role.Button) {
                vm.track("goal_detail.checkin_open", mapOf("entry_point" to "prompt_card", "is_edit" to false))
                vm.sheet = GoalDetailSheet.checkIn
            }
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).background(NC.brandSoft, CircleShape), contentAlignment = Alignment.Center) {
            SfImage("square.and.pencil", size = 15.dp, tint = NC.textBrand)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("오늘 어땠나요?", style = pt(15, PTWeight.bold), color = NC.ink)
            Text("기록을 남기면 그래프와 AI 조정에 바로 반영돼요", style = pt(12), color = NC.ink3)
        }
        SfImage("chevron.right", size = 13.dp, tint = NC.ink4)
    }
}

/** 남겼을 때: 오늘 기록 요약 + 수정 · 계획 조정받기 */
@Composable
private fun GoalCheckInDoneCard(p: GoalProgress, today: GoalCheckInToday, vm: GoalDetailViewModel, modifier: Modifier = Modifier) {
    val m = p.metric
    val name = m?.name?.takeIf { it.isNotEmpty() } ?: "수치"
    val mo = GoalMoodOption.of(today.reflection?.mood)
    Column(
        modifier
            .fillMaxWidth()
            .card(radius = 18.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            SfImage("checkmark.circle.fill", size = 15.dp, tint = NC.success)
            Text("오늘 기록", style = pt(15, PTWeight.bold), color = NC.ink)
            Text(GoalDetailLogic.md(p.today), style = pt(13), color = NC.ink3)
            Spacer(Modifier.weight(1f))
            Text(
                "수정",
                style = pt(13, PTWeight.semibold),
                color = NC.ink3,
                modifier = Modifier.clickable(role = Role.Button) {
                    vm.track("goal_detail.checkin_open", mapOf("entry_point" to "edit_button", "is_edit" to true))
                    vm.sheet = GoalDetailSheet.checkIn
                },
            )
        }
        GoalCheckInSummaryChips(name = name, value = today.value, unit = m?.unit, mood = mo, valueSize = 17, spacing = 8)
        val n = today.reflection?.note
        if (!n.isNullOrEmpty()) {
            Text(n, style = pt(14).copy(lineHeight = 20.sp), color = NC.ink2, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        // 기록을 남긴 뒤 — 쌓인 기록으로 계획을 조정받을 수 있다는 걸 알려줘요
        GoalCheckInAdjustLink(vm = vm)
    }
}

/** "체중 64kg" + "😊 잘했어요" 칩 줄 */
@Composable
private fun GoalCheckInSummaryChips(name: String, value: Double?, unit: String?, mood: GoalMoodOption?, valueSize: Int, spacing: Int) {
    if (value == null && mood == null) return
    val ink3 = NC.ink3
    val ink = NC.ink
    FlowLayout(spacing = spacing.dp) {
        if (value != null) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = ink3)) { append("$name ") }
                    withStyle(SpanStyle(color = ink, fontSize = valueSize.sp, fontWeight = FontWeight.Bold)) {
                        append(GoalDetailLogic.fmtNum(value) + (unit ?: ""))
                    }
                },
                style = pt(13),
            )
        }
        if (mood != null) {
            Text(
                "${mood.emoji} ${mood.label}",
                style = pt(13, PTWeight.semibold),
                color = NC.ink,
                modifier = Modifier
                    .background(NC.subtle, CircleShape)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

/** ✨ 최근 기록 보고 계획 조정받기 — 문장을 채우고 말로 고치기를 열어요 */
@Composable
private fun GoalCheckInAdjustLink(vm: GoalDetailViewModel) {
    Row(
        Modifier
            .clickable(role = Role.Button) {
                vm.trackAdjustHint()
                vm.aiEditText = "최근 기록과 회고를 보고 계획을 조정해줘"
                vm.aiFocusTick += 1
            }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AIBadge(AIBadgeSize.sm)
        Text("최근 기록 보고 계획 조정받기", style = pt(12, PTWeight.semibold), color = NC.textBrand)
    }
}

/** ✏️ 버튼으로 올라오는 "오늘 기록" 시트 — 수치 · 기분 · 한 줄 회고 + 최근 기록 */
@Composable
fun GoalCheckInSheet(
    goal: GoalCategory,
    vm: GoalDetailViewModel = LocalGoalDetailViewModel.current!!,
    onDismiss: () -> Unit = { vm.sheet = null },
) {
    val today = vm.progress?.today ?: KST.today()
    SheetScaffold(
        onDismiss = onDismiss,
        title = "오늘 기록 · ${GoalDetailLogic.md(today)}",
        description = "남긴 기록은 그래프와 AI 계획 조정에 바로 반영돼요.",
    ) { close ->
        val p = vm.progress
        if (p != null) {
            GoalCheckInBody(goalId = goal.goalCategoryId, p = p, sheetMode = true, vm = vm, onClose = { close() })
        } else {
            Box(Modifier.fillMaxWidth().heightIn(min = 160.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NC.ink3, strokeWidth = 2.5.dp)
            }
        }
    }
}

/** '최근 기록 보고 계획 조정받기' — 문장을 채운 말로 고치기 시트가 열려요 */
fun GoalDetailViewModel.trackAdjustHint() {
    val g = goal
    val canAi = g != null && !g.isRecordGoal && !g.isHabitGoal && !g.isTemporary && g.goalCategoryStatus != GoalStatus.COMPLETED
    track("goal_detail.checkin_adjust_hint_click", mapOf("has_ai_edit" to canAi))
    track("goal_detail.ai_edit_open", mapOf("entry_point" to "adjust_hint", "is_prefilled" to true))
}

/** 오늘 기록을 이미 남겼는지 (✏️ 버튼에 체크 표시) */
val GoalDetailViewModel.checkedInToday: Boolean
    get() {
        val p = progress ?: return false
        val t = GoalCheckInToday(p)
        return t.value != null || t.reflection != null
    }

/** 진행 데이터에서 오늘 기록 꺼내기 */
private class GoalCheckInToday(p: GoalProgress) {
    val value: Double? = p.metric?.logs?.firstOrNull { it.date == p.today }?.value
    val reflection: GoalReflection? = p.reflections?.firstOrNull { it.date == p.today }

    /** 오늘 기록이 바뀌었는지 알아보는 열쇠 */
    val key: String
        get() {
            val v = value?.toString() ?: ""
            val n = reflection?.note ?: ""
            val mo = reflection?.mood?.rawValue ?: ""
            return "$v|$n|$mo"
        }

    companion object {}
}

/** 받침에 맞는 조사: 체중은 / 점수는 */
private fun goalCheckInJosa(word: String, withFinal: String, without: String): String {
    val last = word.lastOrNull() ?: return word + without
    val code = last.code
    if (code < 0xAC00 || code > 0xD7A3) return word + without
    return word + (if ((code - 0xAC00) % 28 != 0) withFinal else without)
}

/**
 * 입력 화면. sheetMode 면 늘 입력 화면 · 카드 테두리 없음 · 저장하면 onClose 로 시트를 닫아요.
 * (iOS 는 카드로도 썼던 흔적이 있어 카드 모양도 그대로 옮겼어요)
 */
@Composable
private fun GoalCheckInBody(
    goalId: Int,
    p: GoalProgress,
    sheetMode: Boolean = false,
    vm: GoalDetailViewModel,
    onClose: (() -> Unit)? = null,
) {
    val initial = remember { GoalCheckInToday(p) }
    val initialDone = initial.value != null || initial.reflection != null
    var editing by remember { mutableStateOf(sheetMode || !initialDone) }
    var value by remember { mutableStateOf(initial.value?.let { GoalDetailLogic.fmtNum(it) } ?: "") }
    var mood by remember { mutableStateOf(initial.reflection?.mood) }
    var note by remember { mutableStateOf(initial.reflection?.note ?: "") }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val m = p.metric
    val today = GoalCheckInToday(p)
    val done = today.value != null || today.reflection != null
    val name = m?.name?.takeIf { it.isNotEmpty() } ?: "수치"
    val recent = (p.reflections ?: emptyList()).filter { it.date != p.today }.take(3)

    val valueNum: Double? = value.trim().let { t -> if (t.isEmpty()) null else (t.toDoubleOrNull() ?: Double.NaN) }
    val valueOk = valueNum?.isFinite() ?: true
    val hasSomething = (m != null && valueNum != null) || mood != null || note.trim().isNotEmpty() || today.reflection != null
    val canSave = valueOk && hasSomething && !saving

    // ── 앰플리튜드 ── 시트가 어떻게 닫혔는지 (saved · next, 없으면 X · 쓸어 닫기)
    var outcome by remember { mutableStateOf<String?>(null) }
    val initialKey = remember {
        (initial.value?.let { GoalDetailLogic.fmtNum(it) } ?: "") + "|" + (initial.reflection?.mood?.rawValue ?: "") + "|" + (initial.reflection?.note ?: "")
    }
    val isDirtyNow by rememberUpdatedState(value + "|" + (mood?.rawValue ?: "") + "|" + note != initialKey)
    LaunchedEffect(valueOk) {
        if (!valueOk) vm.track("goal_detail.checkin_invalid")
    }
    if (sheetMode) {
        DisposableEffect(Unit) {
            onDispose {
                GD.closeMethod(outcome)?.let { cm ->
                    vm.track("goal_detail.checkin_close", mapOf("close_method" to cm, "is_dirty" to isDirtyNow))
                }
            }
        }
    }

    fun submit() {
        if (!canSave) return
        val v: Double? = if (m != null) valueNum else null
        val trimmed = note.trim()
        val n: String? = trimmed.ifEmpty { null }
        val md = mood
        vm.track("goal_detail.checkin_submit", mapOf(
            "has_value" to (v != null), "mood" to md?.rawValue, "note_length" to trimmed.length, "is_edit" to done,
        ))
        saving = true
        scope.launch {
            val r = Mutation.run(
                invalidate = listOf(DataKey.goals, DataKey.home),
                successMessage = "오늘 기록을 남겼어요.",
                errorFallback = "기록하지 못했어요. 다시 시도해 주세요.",
            ) {
                // 성공 여부를 알 수 있게 응답을 Pair 로 감싸요 (응답이 비어도 성공)
                Pair(GoalEditAPI.checkIn(goalId = goalId, value = v, mood = md, note = n), true)
            }
            saving = false
            vm.track("goal_detail.checkin_result", GD.result())
            if (r != null) {
                outcome = "saved"
                editing = false
                r.first?.let { vm.progress = it }
                if (sheetMode) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClose?.invoke() ?: run { vm.sheet = null }
                }
            }
        }
    }

    // ── 입력 화면 ──
    @Composable
    fun Form() {
        if (m != null) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("오늘 ${goalCheckInJosa(name, "은", "는")} 얼마예요?", style = pt(13, PTWeight.semibold), color = NC.ink2)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    NTextField(
                        value = value,
                        onValueChange = { v -> value = v.filter { "0123456789.-".contains(it) } },
                        placeholder = GoalDetailLogic.fmtNum(m.currentValue),
                        keyboardType = KeyboardType.Decimal,
                        textStyle = pt(17, PTWeight.bold),
                        modifier = Modifier
                            .widthIn(max = 180.dp)
                            .semantics { contentDescription = "$name 값" },
                    )
                    val u = m.unit
                    if (!u.isNullOrEmpty()) {
                        Text(u, style = pt(13), color = NC.ink3)
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("오늘 어땠어요?", style = pt(13, PTWeight.semibold), color = NC.ink2)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (o in GoalMoodOption.all) {
                    val on = mood == o.value
                    Row(
                        Modifier
                            .then(if (sheetMode) Modifier.weight(1f) else Modifier)
                            .height(if (sheetMode) 44.dp else 36.dp)
                            .clip(CircleShape)
                            .background(if (on) NC.brand else NC.surface, CircleShape)
                            .border(if (on) 1.5.dp else 1.dp, if (on) NC.borderFocus else NC.borderSubtle, CircleShape)
                            .clickable(role = Role.RadioButton) {
                                vm.track("goal_detail.checkin_mood_select", mapOf(
                                    "mood" to o.value.rawValue, "mood_action" to if (on) "deselect" else "select",
                                ))
                                mood = if (on) null else o.value
                            }
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(o.emoji, style = pt(13, PTWeight.semibold))
                        Text(o.label, style = pt(13, PTWeight.semibold), color = if (on) Color.White else NC.ink2, maxLines = 1)
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("무엇을 했나요? (한 줄 회고)", style = pt(13, PTWeight.semibold), color = NC.ink2)
            NTextField(
                value = note,
                onValueChange = { note = it },
                placeholder = if (m != null) "예) 점심에 마라탕 먹음, 저녁은 샐러드 · 30분 걸음" else "예) 영어 단어 50개 외움, 저녁 약속 때문에 인강은 못 들음",
                singleLine = false,
                minHeight = if (sheetMode) 88.dp else 64.dp,
                maxLength = 500,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            NButton(
                if (done) "기록 수정하기" else "기록하기",
                variant = NButtonVariant.brand,
                loading = saving,
                fullWidth = sheetMode,
                enabled = canSave,
                modifier = if (sheetMode) Modifier.weight(1f) else Modifier,
            ) { submit() }
            if (done && !sheetMode) {
                NButton("취소", variant = NButtonVariant.ghost) { editing = false }
            }
            if (!valueOk) {
                Text("숫자로 입력해 주세요", style = pt(12), color = NC.danger)
            }
        }
    }

    // ── 최근 기록 ──
    @Composable
    fun RecentList() {
        val logs = m?.logs ?: emptyList()
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth().padding(bottom = 6.dp).height(1.dp).background(NC.line))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("최근 기록", style = pt(12, PTWeight.semibold), color = NC.ink3)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .heightIn(min = 28.dp)
                        .clickable(role = Role.Button) {
                            // 목표 상세 안이라 화면을 새로 열지 않고 "회고" 탭으로 바꿔요
                            vm.track("goal_detail.recent_records_all_click")
                            outcome = "next"
                            if (sheetMode) onClose?.invoke()
                            vm.reflectionsTabRequested = true
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("모두 보기", style = pt(12, PTWeight.semibold), color = NC.textBrand)
                }
            }
            for (r in recent) {
                val v = logs.firstOrNull { it.date == r.date }?.value
                val mo = GoalMoodOption.of(r.mood)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${KST.monthOf(r.date)}/${KST.dayOfMonth(r.date)}", style = pt(13), color = NC.ink3, modifier = Modifier.width(44.dp))
                    if (mo != null) {
                        Text(mo.emoji, style = pt(13), modifier = Modifier.semantics { contentDescription = mo.label })
                    }
                    if (v != null) {
                        Text(GoalDetailLogic.fmtNum(v) + (m?.unit ?: ""), style = pt(13, PTWeight.bold), color = NC.ink)
                    }
                    Text(r.note ?: "", style = pt(13), color = NC.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }

    if (sheetMode) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { Form() }
            if (recent.isNotEmpty()) RecentList()
        }
    } else {
        // 카드 모양 (목표 상세에 바로 넣을 때)
        val shape = RoundedCornerShape(Radius.card)
        Column(
            Modifier
                .fillMaxWidth()
                .background(if (editing) NC.brandSoft.copy(alpha = 0.4f) else NC.surface, shape)
                .border(if (editing) 1.5.dp else 1.dp, if (editing) NC.borderFocus else NC.borderSubtle, shape)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 머리
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).background(NC.brand, CircleShape), contentAlignment = Alignment.Center) {
                    SfImage("square.and.pencil", size = 14.dp, tint = Color.White)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                        Text("오늘 기록", style = pt(16, PTWeight.bold), color = NC.ink)
                        Text(GoalDetailLogic.md(p.today), style = pt(13, PTWeight.medium), color = NC.ink3)
                    }
                    val lead = if (m != null) name + "와(과) 오늘 한 일을 남기면" else "오늘 한 일을 남기면"
                    Text("$lead 그래프와 AI 조정에 바로 반영돼요", style = pt(12), color = NC.ink3)
                }
                if (!editing) {
                    NButton("수정", variant = NButtonVariant.secondary, size = NButtonSize.sm, icon = "pencil") { editing = true }
                }
            }
            if (editing) {
                Form()
            } else {
                // 요약
                val mo = GoalMoodOption.of(today.reflection?.mood)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GoalCheckInSummaryChips(name = name, value = today.value, unit = m?.unit, mood = mo, valueSize = 18, spacing = 12)
                    val n = today.reflection?.note
                    if (!n.isNullOrEmpty()) {
                        Text(n, style = pt(14).copy(lineHeight = 20.sp), color = NC.ink2)
                    }
                    GoalCheckInAdjustLink(vm = vm)
                }
            }
            if (recent.isNotEmpty()) RecentList()
        }
    }
}

// MARK: - 미리보기

@Preview(showBackground = true, name = "요약 카드")
@Composable
private fun GoalDailyCheckInCardPreview() {
    NaleulTheme(dark = false) {
        val vm = remember { GoalDetailViewModel.preview() }
        CompositionLocalProvider(LocalGoalDetailViewModel provides vm) {
            Column(Modifier.canvasBackground().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                GoalDailyCheckInCard(goal = GoalPreviewData.goal)
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 700, name = "기록 시트")
@Composable
private fun GoalCheckInSheetPreview() {
    NaleulTheme(dark = false) {
        val vm = remember { GoalDetailViewModel.preview() }
        CompositionLocalProvider(LocalGoalDetailViewModel provides vm) {
            GoalCheckInSheet(goal = GoalPreviewData.goal, onDismiss = {})
        }
    }
}
