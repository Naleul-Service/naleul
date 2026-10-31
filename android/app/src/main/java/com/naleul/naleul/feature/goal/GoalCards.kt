// Features/Goal/GoalCards.kt
//
// iOS GoalCards.swift — 웹 features/goal/ui/RefineWithAiCard.tsx · PlaceAfterCreateCard.tsx
//  - GoalRefineWithAiCard    : 임시 목표 → "AI로 구체화하기" / "업무형으로 두기"
//  - GoalPlaceAfterCreateCard: AI 목표를 막 만든 직후 "이어서 Task를 TimeTable에 배치할까요?"

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

/** 카드 왼쪽 위 44pt 아이콘 칸 */
@Composable
private fun GoalCardIcon(name: String, background: Color) {
    Box(
        Modifier.size(44.dp).background(background, RoundedCornerShape(Radius.xxl)),
        contentAlignment = Alignment.Center,
    ) {
        SfImage(name, size = 18.dp, tint = Color.White)
    }
}

// MARK: - 임시 목표 → AI 로 구체화하기

/**
 * 임시 목표는 Brain dump 가 대충 만든 그릇이라 기간·마일스톤·루틴이 없어요.
 * 구체화를 확정하면 지금 있는 Task 와 기록은 새 목표로 그대로 옮겨져요.
 */
@Composable
fun GoalRefineWithAiCard(goal: GoalCategory, modifier: Modifier = Modifier) {
    var keeping by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val vm = LocalGoalDetailViewModel.current
    LaunchedEffect(Unit) {
        if (vm != null && vm.once("refine_card")) {
            vm.track("goal_detail.refine_card_view", mapOf("task_count" to vm.tasks?.totalCount))
        }
    }

    val name = goal.emoji.let { e -> if (!e.isNullOrEmpty()) e + " " + goal.goalCategoryName else goal.goalCategoryName }

    fun keep() {
        if (keeping) return
        val id = goal.goalCategoryId
        keeping = true
        scope.launch {
            Mutation.run(invalidate = listOf(DataKey.goals), successMessage = "업무형 목표로 바꿨어요.") {
                GoalEditAPI.keepAsRecord(goalId = id)
            }
            vm?.track("goal_detail.keep_record_result", GD.result())
            keeping = false
        }
    }

    val shape = RoundedCornerShape(Radius.card)
    Column(
        modifier
            .fillMaxWidth()
            .aiBorder(shape, glass = false)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            AIBadge()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("AI로 ‘$name’ 목표를 구체화해 볼까요?", style = NType.h4, color = NC.ink)
                Text(
                    "아직 임시 목표예요. 몇 가지 질문에 답하면 기간·마일스톤·루틴까지 설계해 드려요. 지금 있는 Task와 기록은 그대로 옮겨져요. 회사 업무 · 취업 준비 · 동아리처럼 기한 없이 쌓아 갈 일이라면 업무형으로 두면 돼요.",
                    style = pt(14).copy(lineHeight = 1.5.em),
                    color = NC.ink3,
                )
            }
        }
        FlowLayout(spacing = 8.dp) {
            // 회사 업무처럼 "이룰 것"이 없는 일은 구체화할 필요 없이 기록형으로 두는 게 맞아요
            NButton("업무형으로 두기", variant = NButtonVariant.secondary, icon = "square.and.pencil", loading = keeping) {
                vm?.track("goal_detail.keep_record_click")
                keep()
            }
            NButton("AI로 구체화하기", variant = NButtonVariant.ai, icon = "sparkles") {
                vm?.track("goal_detail.refine_click")
                AppRouter.startGoalCreation(sourceGoalId = goal.goalCategoryId)
            }
        }
    }
}

// MARK: - 목표를 만든 직후: 이번 주 TimeTable 에 배치할까요?

/** 목표를 만든 직후 채울 기간: 오늘부터 7일 */
private const val GOAL_PLACE_DAYS = 7

/**
 * 백엔드는 확정 직후 오늘만 자동으로 채우고 나머지 날은 밤 10시 작업이 하루씩 채워요.
 * 여기서 동의하면 오늘~6일 뒤까지 한 번에 채워서, 목표를 만들자마자 한 주 계획을 볼 수 있어요.
 * (이미 시간이 정해진 블록은 건드리지 않아요 — FILL)
 *
 * @param onDismiss "나중에" · "닫기" (웹: 주소에서 ?created=1 지우기)
 */
@Composable
fun GoalPlaceAfterCreateCard(goal: GoalCategory, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    var filling by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<FillResponse?>(null) }
    val scope = rememberCoroutineScope()
    val vm = LocalGoalDetailViewModel.current
    LaunchedEffect(Unit) {
        if (vm != null && vm.once("place_card")) vm.track("goal_detail.place_card_view")
    }

    fun resultText(r: FillResponse): String {
        val tail = if (r.unscheduledCount > 0) {
            " · ${r.unscheduledCount}개는 빈 시간이 없어 시간 미정으로 남았어요 (밤 10시에 다시 시도해요)"
        } else {
            ""
        }
        return "${r.placedCount}개 Task를 빈 시간에 넣었어요$tail."
    }

    fun fill() {
        if (filling) return
        val today = KST.today()
        val end = KST.addDays(today, GOAL_PLACE_DAYS - 1)
        filling = true
        scope.launch {
            // 웹 useFillTimetable: 끝나면 캘린더 · 목표(관련 Task 시간)를 다시 불러와요
            val r = Mutation.run(invalidate = listOf(DataKey.timetable, DataKey.goals), successMessage = null) {
                TimetableAPI.fill(startDate = today, endDate = end)
            }
            filling = false
            vm?.track("goal_detail.place_fill_result", GD.result() + mapOf(
                "placed_count" to r?.placedCount, "unscheduled_count" to r?.unscheduledCount,
            ))
            if (r != null) {
                TimetableAPI.toastFillResult(r)
                result = r
            }
        }
    }

    val shape = RoundedCornerShape(Radius.card)
    val res = result
    Column(
        modifier
            .fillMaxWidth()
            .background(NC.successSoft.copy(alpha = 0.5f), shape)
            .border(1.dp, NC.borderSubtle, shape)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            GoalCardIcon(if (res != null) "calendar.badge.checkmark" else "sparkles", NC.success)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (res != null) {
                    Text("이번 주 TimeTable에 배치했어요", style = NType.h4, color = NC.ink)
                    Text(resultText(res), style = pt(14), color = NC.ink3)
                } else {
                    Text("목표가 만들어졌어요 🎉 이어서 Task를 TimeTable에 배치할까요?", style = NType.h4, color = NC.ink)
                    Text(
                        "오늘부터 ${GOAL_PLACE_DAYS}일 동안 시간이 정해지지 않은 Task와 루틴을 빈 시간에 넣어요. 직접 정한 일정과 고정 시간은 그대로예요. 나중에 해도 밤 10시마다 다음 날이 자동으로 채워져요.",
                        style = pt(14).copy(lineHeight = 1.5.em),
                        color = NC.ink3,
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (res != null) {
                NButton("닫기", variant = NButtonVariant.ghost) {
                    vm?.track("goal_detail.place_dismiss_click", mapOf("card_state" to "after_fill"))
                    onDismiss()
                }
                NButton("캘린더에서 보기") {
                    vm?.track("goal_detail.place_calendar_click", mapOf("placed_count" to res.placedCount))
                    AppRouter.openCalendar(view = CalendarViewMode.week, date = KST.today())
                }
            } else {
                NButton("나중에", variant = NButtonVariant.ghost, enabled = !filling) {
                    vm?.track("goal_detail.place_dismiss_click", mapOf("card_state" to "before_fill"))
                    onDismiss()
                }
                NButton("AI로 이번 주 배치하기", variant = NButtonVariant.ai, icon = "sparkles", loading = filling) {
                    vm?.track("goal_detail.place_fill_click")
                    fill()
                }
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun GoalCardsPreview() {
    NaleulTheme(dark = false) {
        Column(
            Modifier.fillMaxSize().canvasBackground().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            GoalPlaceAfterCreateCard(goal = GoalPreviewData.goal, onDismiss = {})
            GoalRefineWithAiCard(goal = GoalPreviewData.tempGoal)
        }
    }
}
