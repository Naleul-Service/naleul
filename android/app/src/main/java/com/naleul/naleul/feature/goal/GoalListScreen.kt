@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

// Features/Goal/GoalListScreen.kt
//
// iOS GoalListScreen.swift — 웹 app/(main)/goal/page.tsx + features/goal/ui/GoalListView.tsx — 목표 탭 첫 화면 (내 목표 목록).
// 임시 목표 · 진행 중 · 완료 순서로 보여줘요. 목표가 하나도 없으면 "첫 목표를 만들어 보세요".
//
// 웹 사이드바의 "내 목표" 목록(GoalNavList)은 휴대폰엔 사이드바가 없어서 이 화면이 그 역할을 해요.

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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

class GoalListViewModel : ScreenModel() {
    var phase by mutableStateOf<LoadPhase<List<GoalCategory>>>(LoadPhase.Loading)
    /** 업무형 목표의 연속 완료 주 (목표 id → 값). 아직 못 받았으면 없음 */
    var streaks by mutableStateOf<Map<Int, GoalWorkStreak>>(emptyMap())
    var isPreview = false

    /** 이어서 만들기 카드가 세션을 다 불러왔는지 (pageview 의 has_resume) */
    var hasResume: Boolean? = null
        private set
    private var pageviewSent = false

    /** 목록을 못 불러왔을 때의 HTTP 상태 (0 = 네트워크) */
    var lastErrorStatus: Int? = null

    fun setHasResume(v: Boolean) {
        hasResume = v
        trackPageviewIfReady()
    }

    /** 목록 · 이어서 만들기 카드를 다 불러온 뒤 화면당 한 번 */
    private fun trackPageviewIfReady() {
        val goals = phase.value ?: return
        val resume = hasResume ?: return
        if (pageviewSent || isPreview) return
        pageviewSent = true
        val temps = goals.count { it.isTemporary && it.goalCategoryStatus.isOngoing }
        val ongoing = goals.count { !it.isTemporary && it.goalCategoryStatus.isOngoing }
        val done = goals.count { it.goalCategoryStatus == GoalStatus.COMPLETED }
        GD.track("goal_list.pageview", mapOf(
            "goal_count" to goals.size, "ongoing_count" to ongoing, "temp_count" to temps, "completed_count" to done,
            "has_resume" to resume, "goal_list_state" to if (goals.isEmpty()) "empty" else "ok",
        ))
    }

    suspend fun load() {
        if (isPreview) return
        try {
            val goals = GoalAPI.goals()
            phase = LoadPhase.Loaded(goals)
            trackPageviewIfReady()
            // 업무형 카드의 "연속 완료"는 목표마다 따로 받아요 (웹 useRecordPattern)
            for (g in goals.filter { it.isRecordGoal }) {
                val s = try {
                    GoalEditAPI.workStreak(goalId = g.goalCategoryId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                if (s != null) streaks = streaks + (g.goalCategoryId to s)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (phase.value == null) {
                phase = LoadPhase.Failed(errorMessage(e))
                lastErrorStatus = (e as? ApiError)?.httpStatus ?: 0
                GD.track("goal_list.load_fail", mapOf("error_status" to lastErrorStatus))
            }
        }
    }

    companion object {
        fun preview(goals: List<GoalCategory>): GoalListViewModel {
            val vm = GoalListViewModel()
            vm.isPreview = true
            vm.phase = LoadPhase.Loaded(goals)
            vm.streaks = mapOf(GoalPreviewData.recordGoal.goalCategoryId to GoalPreviewData.workStreak)
            return vm
        }
    }
}

/** 목표 탭 첫 화면. previewVM 은 미리보기용 (iOS init(previewVM:)) */
@Composable
fun GoalListScreen(previewVM: GoalListViewModel? = null) {
    val vm = rememberScreenModel { previewVM ?: GoalListViewModel() }
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }

    ReloadOn(listOf(DataKey.goals, DataKey.record)) { vm.load() }

    // 탭 첫 화면 — 위 제목줄 없이 (iOS .toolbar(.hidden, for: .navigationBar))
    Column(Modifier.fillMaxSize().canvasBackground().statusBarsPadding()) {
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                scope.launch {
                    if (!vm.isPreview) GD.track("goal_list.pull_refresh", mapOf("goal_count" to vm.phase.value?.size))
                    refreshing = true
                    vm.load()
                    refreshing = false
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = PageGutter, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                PageHeader(title = "목표") {
                    NButton("목표 추가", icon = "plus") {
                        if (!vm.isPreview) GD.track("goal_list.add_click", mapOf("goal_count" to vm.phase.value?.size))
                        AppRouter.openGoalAdd()
                    }
                }
                GoalCreationResumeCard(onLoaded = { vm.setHasResume(it) })
                // 기본 생활 습관 — 모든 사용자에게 하나씩 있는 체크 전용 목표 (V52)
                if (vm.isPreview) {
                    HabitSummaryCard(preview = HabitPreviewData.home)
                } else {
                    HabitSummaryCard()
                }
                GoalListContent(vm)
            }
        }
    }
}

@Composable
private fun GoalListContent(vm: GoalListViewModel) {
    val scope = rememberCoroutineScope()
    when (val phase = vm.phase) {
        is LoadPhase.Loading -> {
            // 불러오는 동안 카드 자리만 (iOS .redacted(reason: .placeholder))
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(3) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .background(NC.surface, RoundedCornerShape(Radius.card))
                            .border(1.dp, NC.borderSubtle, RoundedCornerShape(Radius.card)),
                    )
                }
            }
        }
        is LoadPhase.Failed -> {
            Column(
                Modifier.fillMaxWidth().card().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("목표를 불러오지 못했어요", style = pt(15, PTWeight.bold), color = NC.ink)
                Text(phase.message, style = pt(14), color = NC.ink3, textAlign = TextAlign.Center)
                NButton("다시 시도", modifier = Modifier.padding(top = 10.dp)) {
                    GD.track("goal_list.retry_click", mapOf("error_status" to vm.lastErrorStatus))
                    scope.launch {
                        vm.phase = LoadPhase.Loading
                        vm.load()
                    }
                }
            }
        }
        is LoadPhase.Loaded -> {
            val goals = phase.value
            if (goals.isEmpty()) GoalListEmptyCard() else GoalListLists(goals, vm.streaks)
        }
    }
}

@Composable
private fun GoalListEmptyCard() {
    Column(
        Modifier.fillMaxWidth().heightIn(min = 300.dp).card().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        Text("첫 목표를 만들어 보세요", style = NType.h4, color = NC.ink)
        Text(
            "AI와 대화하며 계획까지 설계하거나, 이미 정한 계획이 있다면 직접 만들 수 있어요.",
            style = pt(14),
            color = NC.ink3,
            textAlign = TextAlign.Center,
        )
        Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NButton("AI로 설계하기", variant = NButtonVariant.ai, size = NButtonSize.lg, icon = "sparkles") {
                    GD.track("goal_list.empty_cta_click", mapOf("cta" to "ai"))
                AppRouter.startGoalCreation()
            }
            NButton("직접 만들기", variant = NButtonVariant.secondary, size = NButtonSize.lg) {
                GD.track("goal_list.empty_cta_click", mapOf("cta" to "manual"))
                AppRouter.openGoalAdd(mode = GoalAddMode.manual)
            }
        }
    }
}

@Composable
private fun GoalListLists(goals: List<GoalCategory>, streaks: Map<Int, GoalWorkStreak>) {
    val temps = goals.filter { it.isTemporary && it.goalCategoryStatus.isOngoing }
    val ongoing = goals.filter { !it.isTemporary && it.goalCategoryStatus.isOngoing }
    val done = goals.filter { it.goalCategoryStatus == GoalStatus.COMPLETED }

    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        if (temps.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("임시 목표 ${temps.size}", style = pt(15, PTWeight.bold), color = NC.ink)
                    Text(
                        "AI로 구체화하면 기간·마일스톤·루틴이 생기고, 지금 있는 Task는 그대로 옮겨져요.",
                        style = pt(13),
                        color = NC.ink3,
                    )
                }
                temps.forEachIndexed { i, g -> key(g.goalCategoryId) { GoalTempCard(g, position = i + 1) } }
            }
        }
        if (ongoing.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("진행 중 ${ongoing.size}", style = pt(15, PTWeight.bold), color = NC.ink)
                ongoing.forEachIndexed { i, g ->
                    key(g.goalCategoryId) { GoalListCard(g, streaks[g.goalCategoryId], section = "ongoing", position = i + 1) }
                }
            }
        }
        if (done.isNotEmpty()) {
            Column(Modifier.padding(top = 8.dp).alpha(0.8f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("완료 ${done.size}", style = pt(15, PTWeight.bold), color = NC.ink3)
                done.forEachIndexed { i, g ->
                    key(g.goalCategoryId) { GoalListCard(g, streaks[g.goalCategoryId], section = "completed", position = i + 1) }
                }
            }
        }
    }
}

// MARK: - 임시 목표 카드 (보기 / AI로 구체화하기)

@Composable
private fun GoalTempCard(goal: GoalCategory, position: Int = 1) {
    val subCount = goal.generalCategories.count { it.generalCategoryStatus != GoalStatus.DELETED }
    val warn = NC.warning.copy(alpha = 0.4f)
    Column(
        Modifier
            .fillMaxWidth()
            .background(NC.warningSoft.copy(alpha = 0.4f), RoundedCornerShape(Radius.card))
            .drawBehind {
                // 점선 테두리 (임시라는 느낌)
                val r = 20.dp.toPx()
                drawRoundRect(
                    color = warn,
                    cornerRadius = CornerRadius(r, r),
                    style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
                )
            }
            // [UX] 이름 · 버튼이 아닌 곳(배지 줄 · 여백)을 누름 — 안쪽 버튼이 먼저 탭을 가져가서 빈 곳만 여기로 와요
            .pointerInput(goal.goalCategoryId) {
                detectTapGestures { GD.track("goal_list.temp_card_dead_click", mapOf("goal_category_id" to goal.goalCategoryId)) }
            }
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(10.dp).background(goalColor(goal.colorCode), CircleShape))
            NBadge("임시 목표", tone = BadgeTone.warning)
        }
        Column(
            Modifier.fillMaxWidth().clickable(role = Role.Button) {
                GD.track("goal_list.temp_card_click", mapOf("goal_category_id" to goal.goalCategoryId, "list_position" to position))
                AppRouter.openGoal(goal.goalCategoryId)
            },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                (goal.emoji?.let { "$it " } ?: "") + goal.goalCategoryName,
                style = NType.h4,
                color = NC.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "Task를 적다가 만들어진 목표예요" + (if (subCount > 0) " · 세부 목표 ${subCount}개" else ""),
                style = pt(13),
                color = NC.ink3,
            )
        }
        NButton("AI로 구체화하기", variant = NButtonVariant.ai, size = NButtonSize.sm, icon = "sparkles") {
            GD.track("goal_list.temp_refine_click", mapOf("goal_category_id" to goal.goalCategoryId, "list_position" to position))
            AppRouter.startGoalCreation(sourceGoalId = goal.goalCategoryId)
        }
    }
}

// MARK: - 목표 카드

@Composable
private fun GoalListCard(goal: GoalCategory, streak: GoalWorkStreak?, section: String = "ongoing", position: Int = 1) {
    val color = goalColor(goal.colorCode)
    val period = GoalFormat.periodProgress(goal.goalCategoryStartDate, goal.goalCategoryEndDate)
    val statusTone = when (goal.goalCategoryStatus) {
        GoalStatus.COMPLETED -> BadgeTone.success
        GoalStatus.IN_PROGRESS -> BadgeTone.brand
        else -> BadgeTone.neutral
    }
    val routineCount = goal.generalCategories.sumOf { it.routines.size }
    val footer = if (goal.isRecordGoal) {
        "업무형 · 세부 목표별 로드맵" + (if (routineCount > 0) " · 루틴 ${routineCount}개" else "")
    } else {
        "세부 목표 ${goal.generalCategories.size}개 · 루틴 ${routineCount}개"
    }
    val shape = RoundedCornerShape(Radius.card)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(NC.surface, shape)
            .border(1.dp, NC.borderSubtle, shape)
            .clickable(role = Role.Button) {
                GD.track("goal_list.goal_card_click", goal.analyticsBase, mapOf(
                    "goal_status" to goal.goalCategoryStatus.name, "goal_section" to section, "list_position" to position,
                    "progress_percent" to if (goal.isRecordGoal) null else goal.analyticsProgressPercent,
                    "paused_days" to goal.pausedDays, "d_day" to if (goal.isRecordGoal) null else goal.analyticsDDay,
                ))
                AppRouter.openGoal(goal.goalCategoryId)
            }
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(10.dp).background(color, CircleShape))
            NBadge(goal.goalCategoryStatus.label, tone = statusTone)
            if (goal.isRecordGoal) NBadge("업무형", tone = BadgeTone.neutral)
            goal.pausedDays?.let { paused -> NBadge("멈춤 ${paused}일째", tone = BadgeTone.warning) }
            Spacer(Modifier.weight(1f))
            if (period != null && goal.goalCategoryStatus.isOngoing) {
                Text(GoalFormat.dDayLabel(period.remain), style = pt(12, PTWeight.semibold), color = NC.ink3)
            }
        }
        Text(
            (goal.emoji?.let { "$it " } ?: "") + goal.goalCategoryName,
            style = NType.h4,
            color = NC.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 12.dp),
        )
        val start = goal.goalCategoryStartDate
        if (start != null) {
            // 업무형은 전체 마감이 없어요 (V53 — 마감은 주제마다)
            val end = if (goal.isRecordGoal) "마감은 세부 목표마다" else (goal.goalCategoryEndDate?.let { GoalFormat.formatDot(it) } ?: "계속")
            Text(
                "${GoalFormat.formatDot(start)} – $end",
                style = pt(13),
                color = NC.ink3,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (goal.isRecordGoal) {
            GoalWorkStreakView(streak = streak, color = color)
        } else {
            GoalLifeProgressView(goal = goal, color = color)
        }

        Text(footer, style = pt(12), color = NC.ink3, modifier = Modifier.padding(top = 12.dp))
    }
}

/** 생활형: 수치가 있으면 목표까지 남은 수치, 없으면 기간 경과 */
@Composable
private fun GoalLifeProgressView(goal: GoalCategory, color: Color) {
    fun fmt(v: Double): String = GoalDetailLogic.withUnit(v, goal.metricUnit)
    val start = goal.startValue
    val target = goal.targetValue
    if (start != null && target != null && start != target) {
        val current = goal.currentValue ?: start
        val ratio = ((current - start) / (target - start)).coerceIn(0.0, 1.0)
        val remain = abs(target - current)
        val done = ratio >= 1
        val ink = NC.ink
        Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    buildAnnotatedString {
                        append(goal.metricName?.let { "$it " } ?: "")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = ink)) { append(fmt(current)) }
                        append(" → ${fmt(target)}")
                    },
                    style = pt(12),
                    color = NC.ink3,
                    modifier = Modifier.weight(1f),
                )
                Text("${(ratio * 100).roundToInt()}%", style = ptMono(12, PTWeight.semibold), color = NC.ink)
            }
            ProgressBar(value = ratio, color = color, height = 6.dp)
            Text(
                if (done) "목표 달성 🎉" else "목표까지 ${fmt(remain)} 남았어요",
                style = pt(12, PTWeight.semibold),
                color = if (done) NC.ink else color,
            )
        }
    } else {
        val period = GoalFormat.periodProgress(goal.goalCategoryStartDate, goal.goalCategoryEndDate) ?: return
        Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("기간 경과", style = pt(12), color = NC.ink3)
                Spacer(Modifier.weight(1f))
                Text("${(period.ratio * 100).roundToInt()}%", style = ptMono(12), color = NC.ink3)
            }
            ProgressBar(value = period.ratio, color = color, height = 6.dp)
        }
    }
}

/** 업무형: 완료한 Task 가 있는 주가 몇 주째 이어지는지 + 최근 8주 칸 */
@Composable
private fun GoalWorkStreakView(streak: GoalWorkStreak?, color: Color) {
    if (streak != null) {
        val s = streak
        val weeks = s.weeks.takeLast(8)
        Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("연속 완료", style = pt(12), color = NC.ink3)
                Spacer(Modifier.weight(1f))
                Text(
                    (if (s.weekStreak > 0) "🔥 " else "") + "${s.weekStreak}주",
                    style = ptMono(12, PTWeight.semibold),
                    color = NC.ink,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (w in weeks) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(6.dp)
                            .background(if (w.count > 0) color else NC.subtle, CircleShape),
                    )
                }
            }
            Text(
                if (s.thisWeekDone) {
                    "이번 주도 완료했어요"
                } else if (s.weekStreak > 0) {
                    "이번 주에 하나 끝내면 ${s.weekStreak + 1}주째예요"
                } else {
                    "이번 주에 첫 칸을 채워보세요"
                },
                style = pt(12),
                color = NC.ink3,
            )
        }
    } else {
        Box(
            Modifier
                .padding(top = 20.dp)
                .fillMaxWidth()
                .height(48.dp)
                .background(NC.subtle, RoundedCornerShape(Radius.lg)),
        )
    }
}

@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun GoalListScreenPreview() {
    NaleulTheme(dark = false) {
        GoalListScreen(
            previewVM = GoalListViewModel.preview(
                listOf(GoalPreviewData.tempGoal, GoalPreviewData.goal, GoalPreviewData.recordGoal, GoalPreviewData.doneGoal),
            ),
        )
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun GoalListScreenEmptyPreview() {
    NaleulTheme(dark = false) {
        GoalListScreen(previewVM = GoalListViewModel.preview(emptyList()))
    }
}
