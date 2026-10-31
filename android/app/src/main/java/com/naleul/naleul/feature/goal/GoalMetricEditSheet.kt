// feature/goal/GoalMetricEditSheet.kt
//
// 목표 상세 "지금 어디쯤?" → 수치 수정 (2026-10).
// 수치(이름 · 단위 · 처음 값 · 목표 값)와 수치 마감일을 목표 전체 수정과 따로 고쳐요.
// 수치 마감일은 목표 종료일과 따로 움직여요 (비워 두면 목표 종료일을 따라요).

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Composable
fun GoalMetricEditSheet(
    goal: GoalCategory,
    /** progress_section · progress_cta · goal_edit */
    entryPoint: String = "progress_section",
    vm: GoalDetailViewModel = LocalGoalDetailViewModel.current!!,
    onDismiss: () -> Unit = { vm.sheet = null },
) {
    fun numStr(v: Double?): String = v?.let { GoalDetailLogic.trim(it, 6) } ?: ""

    val hadMetric = goal.targetValue != null || !(goal.metricName ?: "").isEmpty()
    var metricName by remember { mutableStateOf(goal.metricName ?: "") }
    var metricUnit by remember { mutableStateOf(goal.metricUnit ?: "") }
    var startValue by remember { mutableStateOf(numStr(goal.startValue)) }
    var targetValue by remember { mutableStateOf(numStr(goal.targetValue)) }
    /** "" = 목표 종료일 따르기 */
    var targetDate by remember { mutableStateOf(goal.metricTargetDate ?: "") }
    var clearMetric by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val nums = listOf(GoalDetailLogic.toNum(startValue), GoalDetailLogic.toNum(targetValue))
    val goalStart = goal.goalCategoryStartDate
    val goalEnd = goal.goalCategoryEndDate
    val effectiveDate = targetDate.ifEmpty { goalEnd ?: "" }

    val error: String? = run {
        if (clearMetric) return@run null
        if (metricName.trim().isEmpty()) return@run "무엇을 재는지 적어 주세요."
        if (nums.any { it == null || it.isNaN() }) return@run "처음 값과 목표 값을 숫자로 입력해 주세요."
        if (targetDate.isNotEmpty() && goalStart != null && targetDate < goalStart) return@run "수치 마감일이 목표 시작일보다 빨라요."
        null
    }
    val changedFields = buildList {
        if (clearMetric) { add("clear"); return@buildList }
        if (metricName.trim() != (goal.metricName ?: "")) add("name")
        if (metricUnit.trim() != (goal.metricUnit ?: "")) add("unit")
        if (nums[0] != goal.startValue) add("start_value")
        if (nums[1] != goal.targetValue) add("target_value")
        if (targetDate != (goal.metricTargetDate ?: "")) add("target_date")
    }
    val isDirtyNow by rememberUpdatedState(changedFields.isNotEmpty())

    LaunchedEffect(Unit) {
        vm.track("goal_detail.metric_edit_open", mapOf(
            "goal_category_id" to goal.goalCategoryId, "entry_point" to entryPoint, "had_metric" to hadMetric,
        ))
    }
    DisposableEffect(Unit) {
        onDispose {
            GD.closeMethod(outcome)?.let { m ->
                vm.track("goal_detail.metric_edit_cancel", mapOf("close_method" to m, "is_dirty" to isDirtyNow))
            }
        }
    }

    fun submit(close: SheetClose) {
        if (error != null || saving) return
        vm.track("goal_detail.metric_edit_save", mapOf(
            "entry_point" to entryPoint,
            "metric_action" to if (clearMetric) "clear" else if (hadMetric) "update" else "add",
            "changed_fields" to changedFields.joinToString(","),
            "own_target_date" to targetDate.isNotEmpty(),
        ))
        val o = mutableMapOf<String, JsonElement>()
        if (clearMetric) {
            o["clearMetric"] = toJsonValue(true)
        } else {
            o["metricName"] = toJsonValue(metricName.trim())
            o["metricUnit"] = toJsonValue(metricUnit.trim())
            o["startValue"] = toJsonValue(nums[0])
            o["targetValue"] = toJsonValue(nums[1])
            if (targetDate.isNotEmpty()) o["metricTargetDate"] = toJsonValue(targetDate)
            else if (goal.metricTargetDate != null) o["clearMetricTargetDate"] = toJsonValue(true)
        }
        val body = JsonObject(o)
        val goalId = goal.goalCategoryId
        saving = true
        scope.launch {
            val ok = Mutation.run(invalidate = listOf(DataKey.goals, DataKey.timetable), successMessage = if (clearMetric) "수치 목표를 지웠어요." else "수치 목표를 수정했어요.") {
                GoalEditAPI.updateGoal(goalId, body)
            }
            saving = false
            vm.track("goal_detail.metric_edit_result", mapOf("result" to (GD.result()["result"])))
            if (ok != null) {
                outcome = "saved"
                close()
            }
        }
    }

    SheetScaffold(
        onDismiss = onDismiss,
        title = if (hadMetric) "수치 수정" else "수치 목표 추가",
        description = "목표 기간과 따로, 수치를 언제까지 이룰지 정할 수 있어요.",
        dismissible = !saving,
    ) { close ->
        GoalSheetForm {
            GoalInlineForm(
                saving = saving,
                valid = error == null && (changedFields.isNotEmpty() || !hadMetric),
                error = error,
                onCancel = {
                    outcome = "cancel_button"
                    close()
                },
                onSubmit = { submit(close) },
            ) {
                if (clearMetric) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(NC.warningSoft, RoundedCornerShape(Radius.xl))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "저장하면 수치 목표와 마일스톤의 수치가 지워져요. 기록한 값은 남아요.",
                            style = pt(13),
                            color = NC.warningInkStrong,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "되돌리기",
                            style = pt(13, PTWeight.semibold).copy(textDecoration = TextDecoration.Underline),
                            color = NC.warningInkStrong,
                            modifier = Modifier.clickable(role = Role.Button) {
                                vm.track("goal_detail.metric_toggle_click", mapOf("metric_action" to "undo", "entry_point" to entryPoint))
                                clearMetric = false
                            },
                        )
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                        GoalFormField("무엇을 재나요?", modifier = Modifier.weight(1f)) {
                            NTextField(metricName, { metricName = it }, placeholder = "체중", maxLength = 30)
                        }
                        GoalFormField("단위", modifier = Modifier.weight(1f)) {
                            NTextField(metricUnit, { metricUnit = it }, placeholder = "kg", maxLength = 10)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                        GoalFormField("처음 값", modifier = Modifier.weight(1f)) {
                            NTextField(startValue, { startValue = it }, placeholder = "80", keyboardType = KeyboardType.Decimal)
                        }
                        GoalFormField("목표 값", modifier = Modifier.weight(1f)) {
                            NTextField(targetValue, { targetValue = it }, placeholder = "72", keyboardType = KeyboardType.Decimal)
                        }
                    }
                    GoalFormField("수치 마감일") {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            NDateField(
                                ymd = targetDate.ifEmpty { null },
                                onYmdChange = { targetDate = it },
                                minDate = goalStart,
                                placeholder = goalEnd?.let { "목표 종료일과 같게 (${KST.dotDate(it)})" } ?: "날짜 선택",
                            )
                            if (targetDate.isNotEmpty()) {
                                Text(
                                    "목표 종료일과 같게 되돌리기",
                                    style = pt(12, PTWeight.semibold),
                                    color = NC.textBrand,
                                    modifier = Modifier.clickable(role = Role.Button) { targetDate = "" },
                                )
                            } else {
                                Text("비워 두면 목표 종료일을 따라요. 목표 기간을 바꿔도 수치 마감일은 따로 움직여요.", style = pt(12), color = NC.ink3)
                            }
                        }
                    }
                    Text(
                        GoalDetailLogic.metricSentence(
                            name = metricName,
                            unit = metricUnit,
                            start = nums[0],
                            target = nums[1],
                            startDate = goalStart,
                            endDate = effectiveDate.ifEmpty { null },
                        ) ?: "처음 값과 목표 값을 넣으면 어떤 계획인지 문장으로 보여드려요.",
                        style = pt(13, PTWeight.medium),
                        color = NC.ink2,
                    )
                    if (hadMetric) {
                        Text(
                            "수치 없이 진행하기",
                            style = pt(13, PTWeight.semibold),
                            color = NC.ink3,
                            modifier = Modifier.clickable(role = Role.Button) {
                                vm.track("goal_detail.metric_toggle_click", mapOf("metric_action" to "clear", "entry_point" to entryPoint))
                                clearMetric = true
                            },
                        )
                    }
                }
            }
        }
    }
}
