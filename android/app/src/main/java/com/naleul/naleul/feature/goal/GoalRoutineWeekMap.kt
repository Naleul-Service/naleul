// Features/Goal/GoalRoutineWeekMap.kt
//
// iOS GoalRoutineWeekMap.swift — 루틴 폼 안의 "내 루틴 한눈에 보기" — 웹 features/goal/ui/RoutineWeekMap.tsx.
// 루틴을 만들 때 캘린더에 다녀오지 않아도 다른 목표의 루틴이 언제 있는지 보여요.
//  - 위: 같은 요일 · 시간에 겹치는 루틴이 있으면 바로 경고 (+ 캘린더에서 AI로 한 번에 정리)
//  - 펼치면: 월~일 주간표에 모든 목표의 루틴(목표 색)과 지금 만드는 루틴(점선)을 같이 그려요.
//    빈 곳을 누르면 그 요일 · 시각으로 정해져요.

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.ceil
import kotlinx.serialization.Serializable

/** GET /routines/all — 내 모든 목표의 루틴 (목표 색 포함) */
@Serializable
data class AllRoutine(
    val routineId: Int,
    val routineName: String,
    val goalCategoryId: Int,
    val goalCategoryName: String,
    val goalCategoryColorCode: String? = null,
    val repeatStartDate: String? = null,
    val repeatEndDate: String? = null,
    val repeatStartTime: String? = null,
    val repeatEndTime: String? = null,
    val routineStatus: String,
    val repeatDays: List<RepeatDay>,
) {
    val id: Int get() = routineId

    @Serializable
    data class RepeatDay(
        val dayOfWeek: JavaDayOfWeek,
        val startTime: String? = null,
        val endTime: String? = null,
    ) {
        companion object {}
    }

    /** 지금 반복 중인(또는 곧 시작할) 루틴만 */
    fun isLive(today: String): Boolean =
        (routineStatus == "NOT_STARTED" || routineStatus == "IN_PROGRESS") && (repeatEndDate?.let { it >= today } ?: true)

    companion object {}
}

object RoutineAPI {
    suspend fun all(): List<AllRoutine> = API.get<List<AllRoutine>>("/v1/routines/all") ?: emptyList()
}

/** 만들고 있는 루틴 (폼 값) */
data class GoalRoutineDraft(
    val routineId: Int?,
    val name: String,
    val days: List<JavaDayOfWeek>,
    /** 요일별 실제 시간 ("HH:mm", 없으면 빈 문자열) — iOS 튜플 (start:, end:) */
    val timeOf: (JavaDayOfWeek) -> Times,
) {
    data class Times(val start: String, val end: String) {
        companion object {}
    }

    companion object {}
}

object GoalRoutineMap {
    data class Slot(
        val id: String,
        val routineId: Int?,
        val name: String,
        val goalName: String,
        val color: String?,
        val day: JavaDayOfWeek,
        /** 그날 0시 기준 분. 자정을 넘기면 end > 1440 */
        val start: Int,
        val end: Int,
    ) {
        companion object {}
    }

    data class Conflict(
        val id: Int,
        val name: String,
        val goalName: String,
        val days: List<JavaDayOfWeek>,
        val time: String,
    ) {
        companion object {}
    }

    fun range(s: String, e: String): Pair<Int, Int> {
        val a = KST.timeToMinutes(s)
        var b = KST.timeToMinutes(e)
        if (b <= a) b += 1440
        return a to b
    }

    fun slots(r: AllRoutine): List<Slot> = r.repeatDays.mapNotNull { d ->
        val st = d.startTime ?: r.repeatStartTime
        val et = d.endTime ?: r.repeatEndTime
        if (st == null || et == null) return@mapNotNull null
        val (a, b) = range(st, et)
        Slot(
            id = "${r.routineId}-${d.dayOfWeek.name}", routineId = r.routineId, name = r.routineName,
            goalName = r.goalCategoryName, color = r.goalCategoryColorCode, day = d.dayOfWeek, start = a, end = b,
        )
    }

    fun existing(all: List<AllRoutine>, draft: GoalRoutineDraft, today: String = KST.today()): List<Slot> =
        all.filter { it.routineId != draft.routineId && it.isLive(today) }.flatMap { slots(it) }

    fun mine(draft: GoalRoutineDraft): List<Slot> = draft.days.mapNotNull { d ->
        val t = draft.timeOf(d)
        if (t.start.isEmpty() || t.end.isEmpty()) return@mapNotNull null
        val (a, b) = range(t.start, t.end)
        Slot(
            id = "new-${d.name}", routineId = null, name = if (draft.name.isEmpty()) "새 루틴" else draft.name,
            goalName = "", color = null, day = d, start = a, end = b,
        )
    }

    /** 만들고 있는 루틴과 같은 요일 · 시간에 겹치는 기존 루틴 */
    fun conflicts(all: List<AllRoutine>, draft: GoalRoutineDraft): List<Conflict> {
        val ex = existing(all, draft)
        // 순서를 지키는 묶음 (LinkedHashMap 은 넣은 순서를 기억해요)
        val hits = LinkedHashMap<Int, Conflict>()
        for (m in mine(draft)) {
            for (s in ex) {
                if (!(s.day == m.day && s.start < m.end && m.start < s.end)) continue
                val rid = s.routineId ?: continue
                val cur = hits[rid] ?: Conflict(
                    id = rid, name = s.name, goalName = s.goalName, days = emptyList(),
                    time = "${KST.minutesToTime(s.start)}–${KST.minutesToTime(s.end)}",
                )
                hits[rid] = if (cur.days.contains(m.day)) cur else cur.copy(days = cur.days + m.day)
            }
        }
        return hits.values.toList()
    }
}

private val goalMapHourHeight = 16.dp
private val goalMapLabelWidth = 24.dp

/**
 * @param onPick 빈 곳을 눌렀을 때 — 그 요일을 넣고 시작 시각을 그 시각(30분 단위)으로
 * @param onDismiss 이 지도가 들어 있는 시트를 닫기 (iOS @Environment(\.dismiss)) — "캘린더에서 AI로 정리하기" 전에 불러요
 */
@Composable
fun GoalRoutineWeekMap(
    draft: GoalRoutineDraft,
    onPick: (JavaDayOfWeek, Int) -> Unit,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    /** 새 루틴 입력인지 (앰플리튜드 is_new) */
    isNew: Boolean = false,
    /** 겹치는 루틴 수가 바뀔 때 (저장 이벤트의 has_conflict 용) */
    onConflictCount: ((Int) -> Unit)? = null,
) {
    var all by remember { mutableStateOf<List<AllRoutine>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf(false) }
    var conflictSeen by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val vm = LocalGoalDetailViewModel.current

    LaunchedEffect(Unit) {
        all = try {
            RoutineAPI.all()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
        loaded = true
    }

    val conflicts = GoalRoutineMap.conflicts(all, draft)
    val ex = GoalRoutineMap.existing(all, draft)
    val liveCount = ex.mapNotNull { it.routineId }.toSet().size

    // 겹침이 처음 보일 때 한 번 (입력 시트당)
    LaunchedEffect(conflicts.size) {
        val n = conflicts.size
        onConflictCount?.invoke(n)
        if (n > 0 && !conflictSeen) {
            conflictSeen = true
            vm?.track("goal_detail.routine_conflict_view", mapOf("conflict_count" to n, "is_new" to isNew))
        }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (conflicts.isNotEmpty()) GoalRoutineWeekMapWarning(draft, conflicts, onDismiss) {
            vm?.track("goal_detail.routine_conflict_ai_click", mapOf("conflict_count" to conflicts.size, "is_new" to isNew))
        }
        Row(
            Modifier
                .heightIn(min = 32.dp)
                .clickable(role = Role.Button) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    vm?.track("goal_detail.routine_weekmap_toggle", mapOf("expanded" to !open, "routine_count" to liveCount))
                    open = !open
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SfImage("calendar", size = 13.dp, tint = NC.ink2)
            Text("내 루틴 한눈에 보기", style = pt(13, PTWeight.semibold), color = NC.ink2)
            if (liveCount > 0) Text("(${liveCount}개)", style = pt(13), color = NC.ink3)
            SfImage("chevron.down", size = 11.dp, tint = NC.ink2, modifier = Modifier.rotate(if (open) 180f else 0f))
        }
        if (open) {
            if (loaded) {
                GoalRoutineWeekMapGrid(draft = draft, ex = ex, onPick = { d, m ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onPick(d, m)
                })
            } else {
                Box(Modifier.fillMaxWidth().height(160.dp).background(NC.subtle, RoundedCornerShape(12.dp)))
            }
        }
    }
}

@Composable
private fun GoalRoutineWeekMapWarning(
    draft: GoalRoutineDraft,
    conflicts: List<GoalRoutineMap.Conflict>,
    onDismiss: (() -> Unit)?,
    onAiClick: () -> Unit = {},
) {
    val command = conflicts.joinToString(", ") { "'${it.name}'" } +
        " 루틴과 '${if (draft.name.isEmpty()) "새 루틴" else draft.name}' 시간이 겹쳐. 겹치지 않게 정리해줘"
    val ink = NC.ink
    val ink2 = NC.ink2
    val ink3 = NC.ink3
    Column(
        Modifier.fillMaxWidth().background(NC.warningSoft, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SfImage("exclamationmark.triangle.fill", size = 13.dp, tint = NC.warning)
            Text("같은 시간에 이미 루틴이 있어요", style = pt(13, PTWeight.semibold), color = NC.ink)
        }
        for (c in conflicts) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = ink)) { append(c.name) }
                    withStyle(SpanStyle(color = ink3)) { append(" (${c.goalName}) · ") }
                    withStyle(SpanStyle(color = ink2)) { append("${c.days.joinToString("·") { it.label }} ${c.time}") }
                },
                style = pt(13),
            )
        }
        Text(
            "그대로 둬도 괜찮아요 (같은 시간에 둘 다 보여요). 많이 겹치면 캘린더에서 AI로 한 번에 정리할 수 있어요.",
            style = pt(12),
            color = NC.ink3,
        )
        Row(
            Modifier
                .heightIn(min = 28.dp)
                .clickable(role = Role.Button) {
                    onAiClick()
                    onDismiss?.invoke()
                    AppRouter.openCalendarAi(command)
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AIBadge(AIBadgeSize.sm)
            Text("캘린더에서 AI로 정리하기", style = pt(12, PTWeight.semibold), color = NC.textBrand)
        }
    }
}

/** 월~일 주간표 (펼쳤을 때) */
@Composable
private fun GoalRoutineWeekMapGrid(
    draft: GoalRoutineDraft,
    ex: List<GoalRoutineMap.Slot>,
    onPick: (JavaDayOfWeek, Int) -> Unit,
) {
    val mine = GoalRoutineMap.mine(draft)
    val everything = ex + mine
    val lo = minOf(6 * 60, everything.minOfOrNull { it.start } ?: (6 * 60))
    val hi = maxOf(24 * 60, everything.maxOfOrNull { minOf(it.end, 1440 + 6 * 60) } ?: (24 * 60))
    val from = lo / 60 * 60
    val to = ceil(hi.toDouble() / 60).toInt() * 60
    val height = goalMapHourHeight * ((to - from).toFloat() / 60f)
    fun y(m: Int) = goalMapHourHeight * ((m - from).toFloat() / 60f)
    fun overlaps(a: GoalRoutineMap.Slot, b: GoalRoutineMap.Slot): Boolean =
        a.day == b.day && a.start < b.end && b.start < a.end

    // 누를 때마다 최신 onPick 을 부르도록 (pointerInput 은 키가 바뀔 때만 새로 만들어져요)
    val pick by rememberUpdatedState(onPick)
    val subtle = NC.subtle
    val brand = NC.brand
    val brandSoft = NC.brandSoft

    Column(
        Modifier
            .fillMaxWidth()
            .background(NC.surface, RoundedCornerShape(12.dp))
            .border(1.dp, NC.borderSubtle, RoundedCornerShape(12.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Spacer(Modifier.width(goalMapLabelWidth))
            for (d in JavaDayOfWeek.entries) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(d.label, style = pt(11, PTWeight.bold), color = if (draft.days.contains(d)) NC.textBrand else NC.ink3)
                }
            }
        }
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            // 시각 눈금 (3시간마다)
            Box(Modifier.width(goalMapLabelWidth).height(height)) {
                for (m in from..to step 180) {
                    Text(
                        String.format(Locale.US, "%02d", (m / 60) % 24),
                        style = ptMono(9),
                        color = NC.ink4,
                        modifier = Modifier.align(Alignment.TopEnd).offset(y = y(m) - 6.dp),
                    )
                }
            }
            for (d in JavaDayOfWeek.entries) {
                BoxWithConstraints(
                    Modifier
                        .weight(1f)
                        .height(height)
                        .background(subtle.copy(alpha = 0.6f), RoundedCornerShape(5.dp))
                        .semantics { contentDescription = "${d.label}요일 시간 고르기" }
                        .pointerInput(d, from) {
                            detectTapGestures { loc ->
                                val m = from.toDouble() + (loc.y / goalMapHourHeight.toPx()).toDouble() * 60
                                val snapped = maxOf(0, minOf(1410, (m / 30).toInt() * 30))
                                pick(d, snapped)
                            }
                        },
                ) {
                    val w = maxWidth
                    for (s in ex.filter { it.day == d }) {
                        val half = mine.any { overlaps(it, s) }
                        val h = maxOf(y(minOf(s.end, to)) - y(s.start), 5.dp)
                        Box(
                            Modifier
                                .offset(x = 1.dp, y = y(s.start))
                                .size(width = if (half) w / 2 - 2.dp else w - 2.dp, height = h)
                                .background(goalColor(s.color).copy(alpha = 0.85f), RoundedCornerShape(3.dp)),
                        )
                    }
                    for (s in mine.filter { it.day == d }) {
                        val half = ex.any { overlaps(it, s) }
                        val h = maxOf(y(minOf(s.end, to)) - y(s.start), 5.dp)
                        Box(
                            Modifier
                                .offset(x = if (half) w / 2 else 1.dp, y = y(s.start))
                                .size(width = if (half) w / 2 - 1.dp else w - 2.dp, height = h)
                                .background(brandSoft.copy(alpha = 0.8f), RoundedCornerShape(3.dp))
                                .drawBehind {
                                    // 점선 테두리 = 지금 만드는 루틴
                                    val r = 3.dp.toPx()
                                    drawRoundRect(
                                        color = brand,
                                        cornerRadius = CornerRadius(r, r),
                                        style = Stroke(
                                            width = 1.5.dp.toPx(),
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())),
                                        ),
                                    )
                                },
                        )
                    }
                }
            }
        }
        Text(
            "색 칸 = 다른 루틴 · 점선 = 지금 만드는 루틴 · 빈 곳을 누르면 그 요일 · 시각으로 정해져요",
            style = pt(11),
            color = NC.ink3,
        )
    }
}

@Preview(showBackground = true, heightDp = 600)
@Composable
private fun GoalRoutineWeekMapPreview() {
    NaleulTheme(dark = false) {
        val draft = GoalRoutineDraft(
            routineId = null, name = "SQL 문제 풀기", days = listOf(JavaDayOfWeek.TUESDAY, JavaDayOfWeek.THURSDAY),
            timeOf = { GoalRoutineDraft.Times("16:30", "17:30") },
        )
        Column(Modifier.fillMaxSize().canvasBackground().verticalScroll(rememberScrollState()).padding(16.dp)) {
            GoalRoutineWeekMap(draft = draft, onPick = { _, _ -> })
        }
    }
}
