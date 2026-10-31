// feature/goal/GoalLegacySetupSheet.kt
//
// iOS Features/Goal/GoalLegacySetupSheet.swift · 웹 features/goal/ui/LegacyGoalSetup.tsx (2026-10)
// AI 없이 만든 목표형 목표 → "목표 형태 정하기"
//  - 목표마다 처음 들어오면 한 번 자동으로 떠요 (이 기기에서). 그 뒤로는 머리말의 "목표 형태 정하기" 버튼으로
//  - 고르고(라디오) "확인"을 눌러야 진행돼요. "건너뛰기"는 왼쪽
//     · AI 와 설계 → 목표 만들기(sourceGoalId). 확정하면 이 목표에 계획이 채워지고 지금 하는 루틴 · 세부 목표는 이어서 써요
//     · 업무형 → 한 번 더 확인 → PATCH /goal-categories/{id}/convert-to-record
//     · 수치만 직접 → 수치 수정 시트 (entry_point=legacy_setup)

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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.Role

object GoalLegacy {
    /** AI 없이 만든 목표형 목표인지 (예전 앱 · 직접 만들기). 수치를 정하거나 AI 로 설계하면 해당하지 않아요 */
    fun isLegacy(g: GoalCategory): Boolean =
        !g.isTemporary && g.aiGenerated != true && !g.isRecordGoal && !g.isHabitGoal &&
            g.metricName.isNullOrEmpty() && g.targetValue == null &&
            g.goalCategoryStatus != GoalStatus.COMPLETED && g.goalCategoryStatus != GoalStatus.DELETED

    private const val PREFS = "naleul_prefs"
    private fun seenKey(id: Int) = "naleul.legacyGoalSetup.seen.$id"

    /** 처음 들어왔을 때 한 번만 자동으로 (보면 기록) */
    fun takeAutoOpen(g: GoalCategory): Boolean {
        if (!isLegacy(g)) return false
        val prefs = AppContext.app.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        val key = seenKey(g.goalCategoryId)
        if (prefs.getBoolean(key, false)) return false
        prefs.edit().putBoolean(key, true).apply()
        return true
    }
}

private enum class LegacyChoice(val raw: String) { achievementAi("achievement_ai"), record("record"), achievementManual("achievement_manual") }

@Composable
fun GoalLegacySetupSheet(
    goal: GoalCategory,
    /** auto(처음 들어왔을 때) · button(머리말 버튼) */
    trigger: String,
    vm: GoalDetailViewModel = LocalGoalDetailViewModel.current!!,
    onDismiss: () -> Unit = { vm.sheet = null },
) {
    var recordStep by remember { mutableStateOf(false) }
    var choice by remember { mutableStateOf<LegacyChoice?>(null) }
    var converting by remember { mutableStateOf(false) }
    // later · saved · next (닫힌 이유 — 이벤트용, 없으면 X · 쓸어 닫기)
    var outcome by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        vm.track("goal_detail.legacy_setup_view", mapOf("goal_status" to goal.goalCategoryStatus.name, "trigger" to trigger))
    }
    DisposableEffect(Unit) {
        onDispose {
            GD.closeMethod(outcome)?.let { m ->
                if (recordStep) vm.track("goal_detail.legacy_setup_record_cancel", mapOf("close_method" to m))
                vm.track("goal_detail.legacy_setup_dismiss", mapOf("close_method" to m, "step" to if (recordStep) "record" else "choose"))
            }
        }
    }

    /** 고른 뒤 "확인" — 그때 진행해요 */
    fun confirm(close: SheetClose) {
        val c = choice ?: return
        vm.track("goal_detail.legacy_setup_select", mapOf("choice" to c.raw))
        when (c) {
            LegacyChoice.achievementAi -> {
                outcome = "next"
                close()
                AppRouter.startGoalCreation(sourceGoalId = goal.goalCategoryId)
            }
            LegacyChoice.record -> recordStep = true
            LegacyChoice.achievementManual -> {
                outcome = "next"
                vm.sheet = GoalDetailSheet.editMetric("legacy_setup")
            }
        }
    }

    fun toRecord(close: SheetClose) {
        if (converting) return
        val id = goal.goalCategoryId
        vm.track("goal_detail.legacy_setup_record_confirm")
        converting = true
        scope.launch {
            val ok = Mutation.run(
                invalidate = listOf(DataKey.goals),
                successMessage = "업무형 목표로 바꿨어요.",
                errorFallback = "바꾸지 못했어요. 다시 시도해 주세요.",
            ) { GoalEditAPI.convertToRecord(goalId = id) }
            converting = false
            vm.track("goal_detail.legacy_setup_record_result", GD.result(ok != null))
            if (ok != null) {
                outcome = "saved"
                close()
            }
        }
    }

    SheetScaffold(
        onDismiss = onDismiss,
        title = if (recordStep) "업무형 목표로 바꿀까요?" else "이 목표, 어떤 형태로 이어갈까요?",
        description = if (!recordStep)
            "아직 목표 수치와 계획이 없어요. 목표 성격에 맞게 골라 주시면 화면을 그에 맞게 바꿔 드려요. 지금까지의 Task · 루틴 · 기록은 어느 쪽이든 그대로 남아요."
        else null,
        dismissible = !converting,
        footer = { close ->
            if (!recordStep) {
                // 건너뛰기는 왼쪽 · 확인은 오른쪽 (고른 뒤에만 눌러져요)
                NButton("건너뛰기", variant = NButtonVariant.secondary) {
                    outcome = "later"
                    close()
                }
                NButton("확인", enabled = choice != null) { confirm(close) }
            } else {
                NButton("돌아가기", variant = NButtonVariant.secondary, enabled = !converting) {
                    vm.track("goal_detail.legacy_setup_record_cancel", mapOf("close_method" to "back"))
                    recordStep = false
                }
                NButton("업무형으로 바꾸기", loading = converting) { toRecord(close) }
            }
        },
    ) {
        if (!recordStep) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LegacyOption(
                    on = choice == LegacyChoice.achievementAi, title = "목표형으로 AI와 설계할게요", ai = true,
                    body = "몸무게 · 점수 · 권수처럼 이룰 수치가 있어요. 몇 가지 질문에 답하면 수치 목표와 루틴을 설계해 이 목표에 채워 드려요. 지금 하는 루틴 · 세부 목표는 그대로 이어서 써요.",
                ) { choice = LegacyChoice.achievementAi }
                LegacyOption(
                    on = choice == LegacyChoice.record, title = "업무형으로 바꿀게요", icon = "briefcase",
                    body = "회사 업무 · 사이드 프로젝트처럼 정해진 수치 없이 한 일을 쌓아 가요. 세부 목표별 보드로 관리해요.",
                ) { choice = LegacyChoice.record }
                LegacyOption(
                    on = choice == LegacyChoice.achievementManual, title = "AI 없이 목표 수치만 직접 정할게요", icon = "target", body = null,
                ) { choice = LegacyChoice.achievementManual }
            }
        } else {
            Text(
                "세부 목표별 보드로 보여 드려요. Task · 루틴 · 기록은 그대로 남고, 목표 종료일은 비워져요 (업무형은 세부 목표마다 마감을 정해요).",
                style = pt(14), color = NC.ink2,
            )
        }
    }
}

@Composable
private fun LegacyOption(
    on: Boolean,
    title: String,
    icon: String? = null,
    ai: Boolean = false,
    body: String?,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(Radius.xxl)
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (on) NC.brandSoft.copy(alpha = 0.4f) else NC.surface, shape)
            .border(if (on) 1.5.dp else 1.dp, if (on) NC.brand else NC.line, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (ai) AIBadge(size = AIBadgeSize.sm) else if (icon != null) SfImage(icon, size = 16.dp, tint = NC.textBrand)
                Text(title, style = pt(if (body == null) 14 else 15, PTWeight.semibold), color = NC.ink)
            }
            if (body != null) Text(body, style = pt(13), color = NC.ink3)
        }
        Box(
            Modifier
                .size(20.dp)
                .background(if (on) NC.brand else Color.Transparent, CircleShape)
                .border(1.5.dp, if (on) NC.brand else NC.lineStrong, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (on) SfImage("checkmark", size = 10.dp, tint = Color.White)
        }
    }
}
