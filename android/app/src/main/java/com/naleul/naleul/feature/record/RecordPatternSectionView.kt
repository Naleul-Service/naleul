// feature/record/RecordPatternSectionView.kt
//
// iOS Features/Record/RecordPatternSectionView.swift
// 웹 features/record/ui/pattern/RecordPatternSection.tsx · RecordSummaryTiles.tsx
// 기록형 목표 상세 — "이 목표에 대한 나의 패턴" (이 목표의 완료한 Task 기준).
//
// 기록형은 기한도 목표 수치도 없어서 달성률(%)을 낼 수 없어요.
// 대신 완료한 Task 가 "언제·얼마나" 쌓였는지와 루틴별로 한 것을 숫자로 보여주고,
// 성장은 "그 전 4주보다 늘어난 것"과 "줄지 않는 누적"으로 보여줘요.
// 웹의 넓은 화면 2단 배치는 휴대폰에서 세로로 쌓았어요.

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

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// MARK: - 상태

class RecordPatternViewModel(preview: RecordPattern? = null) : ScreenModel() {
    var phase by mutableStateOf<LoadPhase<RecordPattern>>(if (preview != null) LoadPhase.Loaded(preview) else LoadPhase.Loading)
        private set
    private var goalId: Int? = null
    private val isPreview: Boolean = preview != null
    /** 통계 탭을 연 뒤 처음 그렸는지 (record_stats.view 는 탭을 열 때마다 한 번) */
    private var viewSent = false

    suspend fun load(goalId: Int) {
        if (isPreview) return
        if (this.goalId != goalId) {
            this.goalId = goalId
            phase = LoadPhase.Loading
        }
        try {
            val p = RecordAPI.pattern(goalId = goalId)
            if (p != null) {
                phase = LoadPhase.Loaded(p)
                if (!viewSent) {
                    viewSent = true
                    WK.track("record_stats.view", goalId = goalId, props = mapOf(
                        "stats_state" to if (p.summary.totalCount == 0) "empty" else "ok",
                        "completed_task_total" to p.summary.totalCount, "routine_count" to p.routines.items.size,
                    ))
                }
            } else {
                phase = LoadPhase.Failed("이 목표의 패턴을 불러오지 못했어요.")
                WK.track("record_stats.load_fail", goalId = goalId, props = mapOf("error_status" to 404))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 이미 보여주던 패턴이 있으면 그대로 둬요
            if (phase.value == null) {
                phase = LoadPhase.Failed(errorMessage(e))
                WK.track("record_stats.load_fail", goalId = goalId, props = mapOf("error_status" to ((e as? ApiError)?.httpStatus ?: 0)))
            }
        }
    }

    suspend fun retry() {
        val id = goalId ?: return
        phase = LoadPhase.Loading
        load(goalId = id)
    }
}

// MARK: - 화면

/** preview 를 넘기면 네트워크 없이 그려요 (미리보기용) */
@Composable
fun RecordPatternSectionView(goalId: Int, preview: RecordPattern? = null, modifier: Modifier = Modifier) {
    val vm = rememberScreenModel { RecordPatternViewModel(preview) }
    // 기록을 남기거나 지우면(record) · Task 완료를 바꾸면(timetable · goals) 같이 다시 불러와요
    ReloadOn(listOf(DataKey.record, DataKey.timetable, DataKey.goals), id = goalId) { vm.load(goalId = goalId) }

    Box(modifier) {
        when (val phase = vm.phase) {
            is LoadPhase.Loading -> LoadingView(minHeight = 192.dp)
            is LoadPhase.Failed -> {
                Row(
                    Modifier.fillMaxWidth().card().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("이 목표의 패턴을 불러오지 못했어요.", style = pt(14), color = NC.ink2, modifier = Modifier.weight(1f))
                    NButton("다시 시도", variant = NButtonVariant.secondary, size = NButtonSize.sm) {
                        if (preview == null) WK.track("record_stats.retry_click", goalId = goalId)
                        vm.scope.launch { vm.retry() }
                    }
                }
            }
            is LoadPhase.Loaded -> CompositionLocalProvider(LocalRecordStatsGoalId provides (if (preview == null) goalId else null)) {
                RecordPatternContent(data = phase.value, goalId = if (preview == null) goalId else null)
            }
        }
    }
}

/** 불러온 패턴 → 카드들 */
@Composable
private fun RecordPatternContent(data: RecordPattern, goalId: Int? = null) {
    val hasRoutines = data.routines.items.isNotEmpty()
    /** 카드가 화면에 절반 이상 처음 보일 때 (탭을 열 때마다 카드당 1회) */
    fun Modifier.seen(card: String): Modifier =
        if (goalId == null) this
        else onFirstHalfVisible { WK.track("record_stats.card_view", goalId = goalId, props = mapOf("stats_card" to card)) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (data.summary.totalCount == 0) {
            // 완료한 Task 가 아직 없으면 빈 그래프 대신 안내 한 줄 (루틴이 있으면 루틴 카드는 보여줘요)
            Column(
                Modifier.fillMaxWidth().card().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("아직 완료한 Task가 없어요", style = pt(16, PTWeight.bold), color = NC.ink)
                Text(
                    "이 목표의 Task를 완료하면 주차별로 완료한 Task, 완료의 리듬, 주로 하는 때가 여기에 보여요.",
                    style = pt(14).copy(lineHeight = 20.sp),
                    color = NC.ink3,
                )
            }
        } else {
            RecordSummaryTiles(summary = data.summary, modifier = Modifier.seen("summary"))
            RecordWeeklyCard(weeks = data.rhythm.weeks, modifier = Modifier.seen("weekly"))
            RecordRhythmCard(rhythm = data.rhythm, modifier = Modifier.seen("rhythm"))
            RecordWhenCard(`when` = data.`when`, modifier = Modifier.seen("when"))
        }
        if (hasRoutines) {
            RecordRoutineCard(routines = data.routines, modifier = Modifier.seen("routine"))
        }
    }
}

// MARK: - 요약 4칸

/**
 * 완료한 Task 기준. 기한·목표 수치가 없으니 "쌓인 양"과 "그 전 4주보다 늘어난 것"만 보여줘요.
 * 줄었을 때는 빨간색 대신 그냥 최근 값을 보여줘요 (기록형은 쉬어가는 주도 자연스러워요).
 */
@Composable
fun RecordSummaryTiles(summary: RecordPatternSummary, modifier: Modifier = Modifier) {
    val s = summary
    val r = s.recent
    val p = s.previous
    val moreCount = if (p.count > 0 && r.count > p.count) r.count - p.count else 0
    val moreDays = if (p.activeDays > 0 && r.activeDays > p.activeDays) r.activeDays - p.activeDays else 0
    val moreMinutes = if (p.minutes > 0 && r.minutes > p.minutes) r.minutes - p.minutes else 0

    val countSub = if (moreCount > 0) "▲ 최근 4주 ${r.count}개 · ${moreCount}개 더" else "최근 4주 ${r.count}개"
    val daysSub = if (moreDays > 0) "▲ 최근 4주 ${r.activeDays}일 · ${moreDays}일 더" else "최근 4주 ${r.activeDays}일"
    val minutesSub = when {
        s.totalMinutes == 0 -> "Task에 시간을 정하면 쌓여요"
        moreMinutes > 0 -> "▲ 최근 4주 ${KST.formatDuration(moreMinutes)} 더"
        else -> "최근 4주 ${KST.formatDuration(r.minutes)}"
    }
    val streakSub = when {
        s.thisWeekDone -> "이번 주도 완료했어요"
        s.weekStreak > 0 -> "이번 주에 하나 끝내면 ${s.weekStreak + 1}주째예요"
        else -> "이번 주에 첫 칸을 채워보세요"
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PatternTile(
                label = "완료한 Task", value = "${s.totalCount}개", sub = countSub,
                tone = if (moreCount > 0) PatternTileTone.up else null, valueSize = 28,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            PatternTile(
                label = "완료한 날", value = "${s.activeDays}일", sub = daysSub,
                tone = if (moreDays > 0) PatternTileTone.up else null, valueSize = 28,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PatternTile(
                label = "쌓인 시간", value = if (s.totalMinutes > 0) KST.formatDuration(s.totalMinutes) else "–",
                sub = minutesSub, tone = if (moreMinutes > 0) PatternTileTone.up else null, valueSize = 28,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            PatternTile(
                label = "연속 완료", value = (if (s.weekStreak > 0) "🔥 " else "") + "${s.weekStreak}주",
                sub = streakSub, tone = if (s.thisWeekDone && s.weekStreak >= 2) PatternTileTone.up else null, valueSize = 28,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 2200)
@Composable
private fun RecordPatternSectionViewPreview() {
    NaleulTheme(dark = false) {
        Column(Modifier.canvasBackground().verticalScroll(rememberScrollState()).padding(16.dp)) {
            RecordPatternSectionView(goalId = 7, preview = RecordSampleData.pattern)
        }
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun RecordPatternSectionViewEmptyPreview() {
    NaleulTheme(dark = false) {
        Column(Modifier.canvasBackground().verticalScroll(rememberScrollState()).padding(16.dp)) {
            RecordPatternSectionView(goalId = 7, preview = RecordSampleData.emptyPattern)
        }
    }
}
