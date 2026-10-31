// feature/goalcreation/GoalCreationSlotEditSheet.kt
//
// iOS Features/GoalCreation/GoalCreationSlotEditSheet.swift
// 정리 확인 화면에서 항목 하나 고치기 (목표 · 수치 · 이유 · 기한 · 실천 시간).
// 웹 원본: features/goal-creation/ui/review/SlotEditModal.tsx (Modal → 시트)
//
// 각 항목 폼은 "검증 통과 시 patch, 실패 시 에러 문구" 를 만드는 build() 를 가져요 (웹과 같은 구조).
// 시트로 띄워지는 화면이라 onClose 가 시트 닫기(iOS dismiss)예요. 부르는 쪽은 editing?.let { GoalCreationSlotEditSheet(…) } 만 하면 돼요.

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

private sealed class SlotEditBuildResult {
    data class patch(val value: GoalCreationSlotsPatch) : SlotEditBuildResult()
    data class error(val message: String) : SlotEditBuildResult()
}

/**
 * @param serverError 서버에서 거절된 이유 (400 등)
 * @param onClose 시트 닫기
 */
@Composable
fun GoalCreationSlotEditSheet(
    slot: GoalCreationSlotKey,
    slots: GoalCreationSlots,
    saving: Boolean,
    serverError: String?,
    onClose: () -> Unit,
    onSave: (GoalCreationSlotsPatch) -> Unit,
) {
    var localError by remember(slot) { mutableStateOf<String?>(null) }

    // 목표 · 이유 · 계획
    var goalStatement by remember(slot) { mutableStateOf(slots.goalStatement.value ?: "") }
    var motivation by remember(slot) { mutableStateOf(slots.motivation.value ?: "") }
    var approach by remember(slot) { mutableStateOf(slots.approach.value ?: "") }
    // 수치
    val m = slots.metric.value
    var metricName by remember(slot) { mutableStateOf(m?.name ?: "") }
    var metricUnit by remember(slot) { mutableStateOf(m?.unit ?: "") }
    var metricCurrent by remember(slot) { mutableStateOf(m?.currentValue?.let { DraftPlanUtils.num(it) } ?: "") }
    var metricTarget by remember(slot) { mutableStateOf(m?.targetValue?.let { DraftPlanUtils.num(it) } ?: "") }
    // 기한
    val d = slots.deadline.value
    var endDate by remember(slot) { mutableStateOf(d?.endDate ?: "") }
    var startDate by remember(slot) { mutableStateOf(d?.startDate ?: "") }
    var flexible by remember(slot) { mutableStateOf(d?.isFlexible ?: false) }
    // 실천 시간
    val p = slots.practicePreference.value
    var days by remember(slot) { mutableStateOf(p?.preferredDays ?: emptyList()) }
    var ranges by remember(slot) { mutableStateOf(p?.preferredTimeRanges ?: emptyList()) }
    var unavailable by remember(slot) { mutableStateOf(p?.unavailableNote ?: "") }
    var workStyle by remember(slot) { mutableStateOf(p?.workStyleAnswer ?: "") }

    /** 기한은 내일 ~ 2년 뒤 */
    val today = KST.today()
    val minDate = KST.addDays(today, 1)
    val maxDate = KST.addDays(today, 365 * 2)

    /** 웹 maxLength */
    fun limited(v: String, n: Int): String = GoalCreationForm.prefix(v, n)

    fun build(): SlotEditBuildResult = when (slot) {
        GoalCreationSlotKey.goalStatement -> {
            val v = GoalCreationForm.trim(goalStatement)
            if (v.isEmpty()) SlotEditBuildResult.error("목표를 입력해 주세요.")
            else SlotEditBuildResult.patch(GoalCreationSlotsPatch(goalStatement = v))
        }
        GoalCreationSlotKey.metric -> run {
            val name = GoalCreationForm.trim(metricName)
            if (name.isEmpty()) return@run SlotEditBuildResult.error("무엇을 잴지 이름을 입력해 주세요. (예: 체중)")
            val c = GoalCreationForm.toNumber(metricCurrent)
            val t = GoalCreationForm.toNumber(metricTarget)
            if (c?.isNaN() == true || t?.isNaN() == true) return@run SlotEditBuildResult.error("수치는 숫자로 입력해 주세요.")
            if (t == null) return@run SlotEditBuildResult.error("목표 수치를 입력해 주세요.")
            val unit = GoalCreationForm.trim(metricUnit)
            SlotEditBuildResult.patch(
                GoalCreationSlotsPatch(
                    metric = GoalCreationMetricValue(name = name, currentValue = c, targetValue = t, unit = unit.ifEmpty { null }),
                )
            )
        }
        GoalCreationSlotKey.motivation -> {
            val v = GoalCreationForm.trim(motivation)
            if (v.isEmpty()) SlotEditBuildResult.error("이유를 입력해 주세요.")
            else SlotEditBuildResult.patch(GoalCreationSlotsPatch(motivation = v))
        }
        GoalCreationSlotKey.approach -> {
            val v = GoalCreationForm.trim(approach)
            if (v.isEmpty()) SlotEditBuildResult.error("어떻게 하고 싶은지 적어 주세요. 정한 게 없으면 \"정한 계획 없음\"이라고 적어도 돼요.")
            else SlotEditBuildResult.patch(GoalCreationSlotsPatch(approach = v))
        }
        GoalCreationSlotKey.deadline -> when {
            endDate.isEmpty() -> SlotEditBuildResult.error("날짜를 골라 주세요.")
            endDate < minDate -> SlotEditBuildResult.error("오늘 이후 날짜를 골라 주세요.")
            endDate > maxDate -> SlotEditBuildResult.error("기한은 최대 2년까지 정할 수 있어요.")
            startDate.isNotEmpty() && startDate < today -> SlotEditBuildResult.error("시작일은 오늘부터 고를 수 있어요.")
            startDate.isNotEmpty() && endDate <= startDate -> SlotEditBuildResult.error("마감일은 시작일 뒤여야 해요.")
            else -> SlotEditBuildResult.patch(
                GoalCreationSlotsPatch(
                    deadline = GoalCreationDeadlineValue(endDate = endDate, isFlexible = flexible, startDate = startDate.ifEmpty { null }),
                )
            )
        }
        GoalCreationSlotKey.practicePreference -> run {
            for (r in ranges) {
                if (r.start.isEmpty() || r.end.isEmpty()) return@run SlotEditBuildResult.error("시간대의 시작·끝 시각을 모두 정해 주세요.")
                if (r.start >= r.end) return@run SlotEditBuildResult.error("끝 시각은 시작 시각보다 늦어야 해요.")
            }
            val un = GoalCreationForm.trim(unavailable)
            val ws = GoalCreationForm.trim(workStyle)
            SlotEditBuildResult.patch(
                GoalCreationSlotsPatch(
                    practicePreference = GoalCreationPracticePreference(
                        // 요일 순서를 월→일로 정렬해서 보내요
                        preferredDays = if (days.isEmpty()) null else GoalCreationWeekday.entries.filter { days.contains(it) },
                        preferredTimeRanges = ranges.ifEmpty { null },
                        unavailableNote = un.ifEmpty { null },
                        workStyleAnswer = ws.ifEmpty { null },
                    ),
                )
            )
        }
    }

    fun submit() {
        GCTracker.track(GCEvent.slotEditSave, "slot_key" to slot.rawValue)
        when (val r = build()) {
            is SlotEditBuildResult.error -> {
                // 시트 안 검사에서 막힘 — 서버로 보내지 않아요
                GCTracker.track(GCEvent.slotEditResult, "slot_key" to slot.rawValue, "result" to "invalid", "validation_error" to r.message)
                localError = r.message
            }
            is SlotEditBuildResult.patch -> {
                localError = null
                onSave(r.value)
            }
        }
    }

    SheetScaffold(
        onDismiss = onClose,
        title = slot.editTitle,
        dismissible = !saving,
        fullHeight = true,
        footer = { close ->
            NButton("취소", variant = NButtonVariant.secondary, enabled = !saving) {
                localError = null
                GCTracker.sheetClose = "cancel_button"
                close()
            }
            NButton("저장", loading = saving) { submit() }
        },
    ) {
        when (slot) {
            GoalCreationSlotKey.goalStatement ->
                GoalCreationField("이루고 싶은 목표") {
                    NTextField(
                        goalStatement, { goalStatement = limited(it, 100) },
                        placeholder = "예) 몸무게 10kg 감량",
                        onSubmit = { submit() },
                    )
                }

            GoalCreationSlotKey.metric ->
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        GoalCreationField("무엇을 잴까요?", modifier = Modifier.weight(1f)) {
                            NTextField(metricName, { metricName = it }, placeholder = "체중")
                        }
                        GoalCreationField("단위", modifier = Modifier.width(96.dp)) {
                            NTextField(metricUnit, { metricUnit = it }, placeholder = "kg")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        GoalCreationField("지금", modifier = Modifier.weight(1f)) {
                            NTextField(metricCurrent, { metricCurrent = it }, placeholder = "78", keyboardType = KeyboardType.Decimal)
                        }
                        GoalCreationField("목표", modifier = Modifier.weight(1f)) {
                            NTextField(metricTarget, { metricTarget = it }, placeholder = "68", keyboardType = KeyboardType.Decimal)
                        }
                    }
                }

            GoalCreationSlotKey.motivation ->
                GoalCreationField("왜 이루고 싶나요?", hint = "이유가 구체적일수록 계획 문구에 동기 부여가 잘 담겨요.") {
                    NTextField(
                        motivation, { motivation = limited(it, 300) },
                        placeholder = "예) 건강검진에서 지방간이 나와서",
                        minHeight = 112.dp, singleLine = false,
                    )
                }

            GoalCreationSlotKey.approach ->
                GoalCreationField("어떻게 이루고 싶나요?", hint = "이미 정해 둔 순서 · 교재 · 꼭 할 일을 적으면 그대로 계획의 뼈대가 돼요.") {
                    NTextField(
                        approach, { approach = limited(it, 500) },
                        placeholder = "예) 이론 강의 2주 → 기출 5회독",
                        minHeight = 112.dp, singleLine = false,
                    )
                }

            GoalCreationSlotKey.deadline ->
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    GoalCreationField("언제부터 시작할까요? (비우면 오늘부터)") {
                        GoalCreationDateField(ymd = startDate, onYmdChange = { startDate = it }, min = today, max = maxDate, clearable = true)
                    }
                    GoalCreationField("언제까지 이루고 싶나요?") {
                        GoalCreationDateField(ymd = endDate, onYmdChange = { endDate = it }, min = minDate, max = maxDate)
                    }
                    Row(
                        Modifier.clickable(role = Role.Checkbox) { flexible = !flexible },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SfImage(
                            if (flexible) "checkmark.square.fill" else "square",
                            size = 18.dp,
                            tint = if (flexible) NC.textBrand else NC.ink4,
                        )
                        Text("상황에 따라 조금 늦어져도 괜찮아요", style = pt(14), color = NC.ink)
                    }
                }

            GoalCreationSlotKey.practicePreference ->
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("실천하기 좋은 요일", style = pt(13, PTWeight.semibold), color = NC.ink2)
                        GoalCreationDayChips(days = days, onDaysChange = { days = it })
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("시간대", style = pt(13, PTWeight.semibold), color = NC.ink2)
                        ranges.forEachIndexed { i, r ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GoalCreationTimeField(
                                    hm = r.start,
                                    onHmChange = { v -> ranges = ranges.mapIndexed { j, x -> if (j == i) x.copy(start = v) else x } },
                                    clearable = false,
                                    modifier = Modifier.weight(1f),
                                )
                                Text("~", color = NC.ink3, style = pt(15))
                                GoalCreationTimeField(
                                    hm = r.end,
                                    onHmChange = { v -> ranges = ranges.mapIndexed { j, x -> if (j == i) x.copy(end = v) else x } },
                                    clearable = false,
                                    modifier = Modifier.weight(1f),
                                )
                                NIconButton("xmark", size = 15.dp, label = "시간대 삭제") {
                                    if (i in ranges.indices) ranges = ranges.filterIndexed { j, _ -> j != i }
                                }
                            }
                        }
                        if (ranges.size < 3) {
                            Row(
                                Modifier.clickable(role = Role.Button) {
                                    ranges = ranges + GoalCreationTimeRange(start = "19:00", end = "21:00")
                                },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                SfImage("plus", size = 13.dp, tint = NC.textBrand)
                                Text("시간대 추가", style = pt(14, PTWeight.semibold), color = NC.textBrand)
                            }
                        }
                    }
                    GoalCreationField("안 되는 시간 (선택)") {
                        NTextField(unavailable, { unavailable = limited(it, 100) }, placeholder = "예) 평일 9~18시 회사")
                    }
                    GoalCreationField("평소 일하는 방식 (선택)") {
                        NTextField(workStyle, { workStyle = limited(it, 100) }, placeholder = "예) 미리미리 나눠서 하는 편")
                    }
                }
        }

        val error = localError ?: serverError
        if (error != null) {
            Text(error, style = pt(14, PTWeight.medium), color = NC.danger, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun GoalCreationSlotEditSheetPreview() {
    NaleulTheme(dark = false) {
        // 시트는 미리보기에서 따로 창으로 떠요 (Interactive 모드에서 확인)
        Box(Modifier.fillMaxSize().background(NC.canvas)) {
            GoalCreationSlotEditSheet(
                slot = GoalCreationSlotKey.practicePreference,
                slots = GoalCreationSamples.slots,
                saving = false,
                serverError = null,
                onClose = {},
                onSave = {},
            )
        }
    }
}
