// feature/pattern/PatternScreen.kt
//
// iOS Features/Pattern/PatternScreen.swift
// 웹 app/(main)/patterns/page.tsx · features/pattern/ui/PatternView.tsx · ShareButton.tsx
// "나의 패턴" 탭 루트 화면.
//
//  - 기간: 최근 4주 / 전체 (웹 ?period= 대신 화면 상태)
//  - 기간을 바꾸는 동안에도 이전 리포트를 흐리게 보여줘요 (웹 placeholderData)
//  - 탭에 들어올 때마다 다시 물어봐요 (웹 staleTime 0 — 방금 완료한 일이 바로 반영되게)
//  - "리포트 공유": 웹은 화면을 PNG 로 내려받지만, Android 는 시트에 그린 리포트를 이미지로 떠서(GraphicsLayer)
//    사진 보관함(Pictures/Naleul)에 저장한 뒤 공유 창으로 넘겨요
//
// 모바일 배치
//  ┌ 나의 패턴            [↻] [공유]  ┐
//  │ [ 최근 4주 | 전체 ]  (꽉 찬 폭)   │
//  │ 10.01 – 10.28 · 업데이트 13:42    │
//  ├ AI 실행 스타일 (검은 카드)         ┤
//  ├ 섹션 칩 (위에 붙음): 요약 · 시간대 · 요일 · 흐름 · 시간 사용 · 블록 · 루틴 · 습관 ┤
//  └ 제안 → 섹션 카드들                 ┘
// 칩을 누르면 그 섹션으로 스크롤하고, 스크롤하면 지금 보고 있는 섹션 칩이 켜져요.
// (iOS LazyVStack(pinnedViews: .sectionHeaders) → LazyColumn 의 stickyHeader)

package com.naleul.naleul.feature.pattern

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

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// MARK: - 상태

class PatternViewModel(preview: PatternReport? = null) : ScreenModel() {
    var period by mutableStateOf(PatternPeriod.RECENT_4W)
    /** 마지막으로 받은 리포트 (기간을 바꾸는 동안에는 이전 기간 리포트가 남아 있어요) */
    var report by mutableStateOf<PatternReport?>(preview)
    var isFetching by mutableStateOf(false)
    var loadError by mutableStateOf<String?>(null)
    var recalculating by mutableStateOf(false)
    private val isPreview: Boolean = preview != null

    // MARK: Amplitude
    /** report_view 를 보낸 기간 (같은 기간이면 새로고침 · 다시 계산에 다시 안 보내요) */
    private var reportedPeriod: PatternPeriod? = null
    private var suggestionKey: String? = null
    private var lastErrorStatus: Int? = null
    private val scrollSent = mutableSetOf<Int>()

    suspend fun load() {
        if (isPreview) return
        isFetching = true
        val asked = period
        try {
            val r = PatternAPI.report(period)
            report = r
            loadError = null
            if (r != null && asked == period) trackReport(r)
        } catch (e: CancellationException) {
            // 탭을 떠나면 취소돼요 — 다음에 들어올 때 다시 불러요
            throw e
        } catch (e: Exception) {
            loadError = errorMessage(e)
            lastErrorStatus = PT.status(e)
            PT.track("pattern.load_fail", mapOf("period" to period.name, "error_status" to lastErrorStatus))
        } finally {
            isFetching = false
        }
    }

    /** 리포트를 그린 직후 — 기간마다 한 번 (+ 잠김 · 제안 바) */
    private fun trackReport(r: PatternReport) {
        val p = period.name
        val items = if (r.evaluatedCount > 0) r.suggestion?.items.orEmpty() else emptyList()
        if (reportedPeriod != period) {
            reportedPeriod = period
            scrollSent.clear()
            val styleReady = !r.style?.title.isNullOrEmpty()
            PT.track("pattern.report_view", mapOf(
                "period" to p,
                "range_days" to r.range.days,
                "evaluated_count" to r.evaluatedCount,
                "style_ready" to styleReady,
                "ai_status" to r.aiStatus.name,
                "empty_sections" to PT.emptySections(r),
                "has_suggestion" to items.isNotEmpty(),
                "minutes_since_update" to PT.minutesSince(r.generatedAt),
            ))
            if (!styleReady) {
                // [UX] 잠긴 스타일 카드 — 얼마나 남은 상태에서 이탈하는지
                PT.track("pattern.locked_view", mapOf(
                    "period" to p, "evaluated_count" to r.evaluatedCount,
                    "remaining_tasks" to r.style?.progress?.remainingTasks, "remaining_days" to r.style?.progress?.remainingDays,
                ))
            }
        }
        // 제안 바 — 기간이나 항목이 바뀌면 다시
        val key = if (items.isEmpty()) null else "$p|" + items.joinToString(",") { it.key }
        if (key != null && key != suggestionKey) {
            PT.track("pattern.suggestion_view", mapOf(
                "period" to p, "item_count" to items.size,
                "suggestion_types" to PT.suggestionTypes(items), "expected_gain" to r.suggestion?.expectedGain,
            ))
        }
        suggestionKey = key
    }

    /** 스크롤 깊이 25 · 50 · 75 · 100% — 기간마다 한 번 (리포트가 보일 때만) */
    fun scrolled(percent: Float) {
        val r = report ?: return
        if (loadError != null || r.evaluatedCount <= 0) return
        for (mark in listOf(25, 50, 75, 100)) {
            if (percent >= mark - 1 && scrollSent.add(mark)) {
                PT.track("pattern.scroll_depth", mapOf(
                    "scroll_percent" to mark, "period" to period.name, "has_suggestion" to r.suggestion?.items.orEmpty().isNotEmpty(),
                ))
            }
        }
    }

    fun trackRetry() = PT.track("pattern.retry_click", mapOf("period" to period.name, "error_status" to lastErrorStatus))

    fun trackPullRefresh() = PT.track("pattern.pull_refresh", mapOf(
        "period" to period.name, "minutes_since_update" to report?.let { PT.minutesSince(it.generatedAt) },
    ))

    /** 기간 세그먼트 */
    fun changePeriod(p: PatternPeriod) {
        if (p == period) return
        if (!isPreview) PT.track("pattern.period_change", mapOf("period" to p.name, "from_period" to period.name))
        PT.period = p
        period = p
    }

    /** 탭이 보일 때마다 (다른 탭에 갔다 오면 다시) */
    fun trackPageview() {
        if (isPreview) return
        PT.period = period
        PT.resetVisit()
        PT.track("pattern.pageview", mapOf("period" to period.name, "entry_source" to (PT.pendingEntry ?: "tab_bar")))
        PT.pendingEntry = null
    }

    /** "지금 다시 계산" — 캐시를 무시하고 새로 계산 (AI 문장 다듬기는 하루 한 번) */
    suspend fun recalculate() {
        if (isPreview) return
        recalculating = true
        PT.track("pattern.recalc_click", mapOf("period" to period.name, "minutes_since_update" to report?.let { PT.minutesSince(it.generatedAt) }))
        val prevVersion = report?.dataVersion
        try {
            val r = PatternAPI.report(period, refresh = true)
            if (r != null) report = r
            loadError = null
            val nextVersion = r?.dataVersion
            PT.track("pattern.recalc_result", mapOf(
                "period" to period.name, "result" to "success",
                "data_changed" to if (prevVersion != null && nextVersion != null) prevVersion != nextVersion else null,
            ))
            Toast.success("최신 기록으로 다시 계산했어요. (AI 문장 다듬기는 하루 한 번)")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PT.track("pattern.recalc_result", mapOf("period" to period.name, "result" to "fail", "error_status" to PT.status(e)))
            Toast.error(errorMessage(e, fallback = "다시 계산하지 못했어요."))
        } finally {
            recalculating = false
        }
    }
}

// MARK: - 섹션

enum class PatternSection {
    summary, heatmap, weekday, weekly, usage, block, routines, habits;

    val id: String get() = name
    val rawValue: String get() = name

    val label: String
        get() = when (this) {
            summary -> "요약"
            heatmap -> "시간대"
            weekday -> "요일"
            weekly -> "흐름"
            usage -> "시간 사용"
            block -> "블록"
            routines -> "루틴"
            habits -> "습관"
        }

    val icon: String
        get() = when (this) {
            summary -> "square.grid.2x2"
            heatmap -> "clock"
            weekday -> "calendar"
            weekly -> "chart.line.uptrend.xyaxis"
            usage -> "hourglass"
            block -> "checkmark.rectangle.stack"
            routines -> "repeat"
            habits -> "list.bullet.clipboard"
        }

    companion object {}
}

private object PatternGeo {
    /** 위에 붙는 칩 바 높이 — 섹션으로 스크롤할 때 칩 바 아래에 오도록 */
    const val navHeight = 52
    const val navKey = "_nav"

    /** LazyColumn 안 순서: 0 머리말 · 1 스타일 카드 · 2 칩 바 · 3 제안 · 4… 섹션들 */
    fun sectionIndex(s: PatternSection): Int = 4 + s.ordinal
}

// MARK: - 화면

/** 나의 패턴 탭 루트. preview 를 넘기면 네트워크 없이 그려요 (미리보기용) */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PatternScreen(preview: PatternReport? = null) {
    val vm = rememberScreenModel { PatternViewModel(preview) }
    var shareReport by remember { mutableStateOf<PatternReport?>(null) }
    var current by remember { mutableStateOf(PatternSection.summary) }
    /** 칩을 눌러 스크롤하는 동안에는 스크롤 위치로 칩을 바꾸지 않아요 (깜빡임 방지) */
    var jumping by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    // 탭이 보일 때마다 pageview (다른 탭에 갔다 오면 이 화면이 다시 그려져요)
    LaunchedEffect(Unit) { vm.trackPageview() }

    // 기록이 바뀌는 곳(캘린더 · 실제 한 일 · 목표)이 낡으면 같이 다시 물어봐요
    ReloadOn(listOf(DataKey.pattern, DataKey.timetable, DataKey.activities, DataKey.goals), id = vm.period) { vm.load() }

    val report = vm.report
    val shown: PatternReport? = report?.takeIf { vm.loadError == null && it.evaluatedCount > 0 }
    val fade by animateFloatAsState(if (vm.isFetching) 0.7f else 1f, tween(150), label = "pattern-fetching")

    val navHeightPx = with(density) { PatternGeo.navHeight.dp.toPx() }
    val lineExtraPx = with(density) { 40.dp.toPx() }
    val jumpOffsetPx = with(density) { (PatternGeo.navHeight + 12).dp.toPx() }

    // 칩 바 바로 아래(약간 여유)를 지난 마지막 섹션 = 지금 보는 섹션
    LaunchedEffect(listState, shown != null) {
        if (shown == null) return@LaunchedEffect
        snapshotFlow {
            val visible = listState.layoutInfo.visibleItemsInfo
            val nav = visible.firstOrNull { it.key == PatternGeo.navKey }
            val line: Float = (nav?.let { maxOf(it.offset, 0) + it.size }?.toFloat() ?: navHeightPx) + lineExtraPx
            var passed = PatternSection.summary
            for (s in PatternSection.entries) {
                val idx = PatternGeo.sectionIndex(s)
                val item = visible.firstOrNull { it.index == idx }
                val top: Float = when {
                    item != null -> item.offset.toFloat()
                    idx < listState.firstVisibleItemIndex -> Float.NEGATIVE_INFINITY
                    else -> Float.POSITIVE_INFINITY
                }
                if (top <= line) passed = s
            }
            passed
        }.collect { next ->
            if (!jumping && next != current) current = next
        }
    }

    // 스크롤 깊이 — LazyColumn 은 전체 높이를 몰라서 보이는 항목 평균 높이로 어림해요 (마지막 항목이 다 보이면 100)
    LaunchedEffect(listState, shown != null) {
        if (shown == null) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            val visible = info.visibleItemsInfo
            val total = info.totalItemsCount
            if (visible.isEmpty() || total == 0) return@snapshotFlow 0f
            val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
            val last = visible.last()
            if (last.index == total - 1 && last.offset + last.size <= info.viewportEndOffset) return@snapshotFlow 100f
            val avg = visible.sumOf { it.size }.toFloat() / visible.size
            val content = avg * total
            val offset = listState.firstVisibleItemIndex * avg + listState.firstVisibleItemScrollOffset
            if (content - viewport <= 0f) 0f else offset / (content - viewport) * 100f
        }.collect { vm.scrolled(it) }
    }

    fun jump(s: PatternSection) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        PT.track("pattern.section_nav_click", mapOf("section" to s.analyticsKey, "period" to vm.period.name))
        jumping = true
        current = s
        scope.launch {
            try {
                listState.animateScrollToItem(PatternGeo.sectionIndex(s))
                // 섹션이 칩 바 바로 아래에 오도록 칩 바 높이만큼 되돌려요
                listState.animateScrollBy(-jumpOffsetPx)
            } finally {
                jumping = false
            }
        }
    }

    Column(Modifier.fillMaxSize().canvasBackground().statusBarsPadding()) {
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                vm.trackPullRefresh()
                vm.scope.launch {
                    refreshing = true
                    try {
                        vm.load()
                    } finally {
                        refreshing = false
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                item(key = "header") {
                    PatternHeader(vm = vm, onShare = {
                        PT.track("pattern.share_click", mapOf(
                            "period" to vm.period.name, "evaluated_count" to it.evaluatedCount, "style_ready" to !it.style?.title.isNullOrEmpty(),
                        ))
                        shareReport = it
                    })
                }
                if (shown != null) {
                    item(key = "style") {
                        PatternStyleCard(style = shown.style, modifier = Modifier.padding(horizontal = PageGutter).padding(bottom = 12.dp))
                    }
                    stickyHeader(key = PatternGeo.navKey) {
                        PatternSectionNav(current = current, onTap = { jump(it) })
                    }
                    item(key = "suggestion") {
                        // 바로 해 볼 수 있는 제안은 맨 위에
                        PatternSuggestionBarView(
                            suggestion = shown.suggestion,
                            modifier = Modifier.padding(horizontal = PageGutter).padding(top = 12.dp).alpha(fade),
                        )
                    }
                    for (s in PatternSection.entries) {
                        item(key = "pattern-${s.rawValue}") {
                            Box(Modifier.padding(horizontal = PageGutter).padding(top = 16.dp).alpha(fade)) {
                                PatternSectionContent(section = s, r = shown)
                            }
                        }
                    }
                    item(key = "footer") {
                        // 숫자는 기록이 바뀔 때마다 바로, AI 문장 다듬기는 하루 한 번 (AI 비용 때문에)
                        Text(
                            if (shown.aiStatus == PatternAiStatus.AI) {
                                "문장은 AI가 다듬었고, 숫자는 실제 기록으로 계산했어요."
                            } else {
                                "숫자는 실제 기록으로 바로 계산했어요. AI 문장 다듬기는 하루 한 번 해요."
                            },
                            style = pt(12),
                            color = NC.ink4,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(start = PageGutter, end = PageGutter, top = 20.dp, bottom = 32.dp),
                        )
                    }
                } else {
                    item(key = "content") {
                        Box(Modifier.padding(horizontal = PageGutter).padding(bottom = 32.dp)) {
                            PatternScreenContent(vm = vm)
                        }
                    }
                }
            }
        }
    }

    shareReport?.let { r ->
        PatternShareSheet(report = r, onDismiss = { shareReport = null })
    }
}

// MARK: 머리말

@Composable
private fun PatternHeader(vm: PatternViewModel, onShare: (PatternReport) -> Unit) {
    val r = vm.report
    Column(
        Modifier.fillMaxWidth().padding(horizontal = PageGutter).padding(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("나의 패턴", style = NType.h2, color = NC.textPrimary)
            Spacer(Modifier.weight(1f))
            if (r != null) {
                // 머리말이 스크롤로 사라져도 계산이 끊기지 않게 화면(vm) 범위에서 돌려요
                PatternRecalcButton(recalculating = vm.recalculating) { vm.scope.launch { vm.recalculate() } }
            }
            if (r != null && r.evaluatedCount > 0) {
                NIconButton("square.and.arrow.up", size = 17.dp, color = NC.ink2, label = "리포트 공유") { onShare(r) }
            }
        }
        SegmentPicker(
            selection = vm.period,
            onSelectionChange = { vm.changePeriod(it) },
            options = PatternPeriod.entries.map { it to it.label },
            fullWidth = true,
            modifier = Modifier.semantics { contentDescription = "기간" },
        )
        if (r != null) {
            // 언제 계산한 결과인지 — 방금 한 일이 반영됐는지 알 수 있게
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(PatternFormat.rangeLabel(r), style = pt(12), color = NC.ink3)
                Text(
                    "업데이트 ${PatternFormat.updatedLabel(r.generatedAt)} · 오늘 완료한 일까지 반영 · 당겨서 새로고침",
                    style = pt(12),
                    color = NC.ink3,
                )
            }
        }
    }
}

/** "지금 다시 계산" — 아이콘만 (돌아가는 동안 회전) */
@Composable
private fun PatternRecalcButton(recalculating: Boolean, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "pattern-recalc")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing)),
        label = "pattern-recalc-angle",
    )
    Box(
        Modifier
            .size(36.dp)
            .alpha(if (recalculating) 0.5f else 1f)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = !recalculating, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "지금 다시 계산" },
        contentAlignment = Alignment.Center,
    ) {
        SfImage("arrow.clockwise", size = 16.dp, tint = NC.ink2, modifier = Modifier.rotate(if (recalculating) angle else 0f))
    }
}

// MARK: 섹션들

@Composable
private fun PatternSectionContent(section: PatternSection, r: PatternReport) {
    when (section) {
        PatternSection.summary -> PatternSummaryTiles(summary = r.summary)
        PatternSection.heatmap -> PatternHeatmapCard(heatmap = r.heatmap)
        PatternSection.weekday -> PatternWeekdayBars(weekday = r.weekday)
        PatternSection.weekly -> PatternWeeklyTrend(weekly = r.weekly)
        PatternSection.usage -> PatternTimeUsageCard(usage = r.timeUsage)
        PatternSection.block -> PatternBlockKeepingCard(block = r.blockKeeping)
        PatternSection.routines -> PatternRoutineLists(routines = r.routines)
        PatternSection.habits -> PatternTaskHabitsCard(habits = r.taskHabits)
    }
}

// MARK: 기록 없음 · 오류 · 불러오는 중

@Composable
private fun PatternScreenContent(vm: PatternViewModel) {
    val message = vm.loadError
    val r = vm.report
    when {
        message != null -> PatternErrorCard(message = message) {
            vm.trackRetry()
            vm.scope.launch { vm.load() }
        }
        r != null -> PatternEmptyState(r = r, period = vm.period)
        else -> LoadingView(minHeight = 420.dp)
    }
}

@Composable
private fun PatternErrorCard(message: String, retry: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 280.dp)
            .card()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        Text("패턴을 불러오지 못했어요", style = NType.h4, color = NC.ink)
        Text(message, style = pt(14), color = NC.ink3, textAlign = TextAlign.Center)
        NButton("다시 시도", variant = NButtonVariant.secondary, modifier = Modifier.padding(top = 10.dp)) { retry() }
    }
}

/** 기록이 하나도 없어도 빈 화면 대신 "얼마나 하면 열리는지"를 보여줘요 */
@Composable
private fun PatternEmptyState(r: PatternReport, period: PatternPeriod) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PatternStyleCard(style = r.style)
        Column(
            Modifier.fillMaxWidth().card().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Task를 완료하면 바로 패턴이 쌓이기 시작해요. 시간대별 실행률·요일별 패턴은 기록이 조금 더 모이면 열려요.",
                style = pt(14),
                color = NC.ink2,
            )
            NButton("캘린더로 가기", size = NButtonSize.md, fullWidth = true) {
                PT.track("pattern.empty_cta_click", mapOf("period" to period.name))
                AppRouter.openCalendar()
            }
        }
    }
}

// MARK: - 섹션 칩 바 (위에 붙음 · 가로 스크롤)

@Composable
private fun PatternSectionNav(current: PatternSection, onTap: (PatternSection) -> Unit) {
    val rowState = rememberLazyListState()
    // 켜진 칩이 보이게 가로로 따라가요
    LaunchedEffect(current) {
        rowState.animateScrollToItem(maxOf(0, current.ordinal - 1))
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(PatternGeo.navHeight.dp)
            .background(NC.canvas.copy(alpha = 0.96f)),
    ) {
        LazyRow(
            state = rowState,
            modifier = Modifier.align(Alignment.CenterStart).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(PatternSection.entries, key = { it.rawValue }) { s ->
                val on = s == current
                val fg = if (on) NC.actionPrimaryFg else NC.ink2
                Row(
                    Modifier
                        .height(34.dp)
                        .clip(CircleShape)
                        .background(if (on) NC.actionPrimary else NC.surface, CircleShape)
                        .border(1.dp, if (on) Color.Transparent else NC.borderSubtle, CircleShape)
                        .clickable(role = Role.Tab) { onTap(s) }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    SfImage(s.icon, size = 11.dp, tint = fg)
                    Text(s.label, style = pt(13, if (on) PTWeight.bold else PTWeight.medium), color = fg, maxLines = 1)
                }
            }
        }
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(0.5.dp).background(NC.line))
    }
}

// MARK: - 리포트 카드 묶음 (공유 이미지가 써요)

@Composable
fun PatternReportBody(report: PatternReport, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PatternStyleCard(style = report.style)
        PatternSummaryTiles(summary = report.summary)
        PatternHeatmapCard(heatmap = report.heatmap)
        PatternWeekdayBars(weekday = report.weekday)
        PatternWeeklyTrend(weekly = report.weekly)
        PatternTimeUsageCard(usage = report.timeUsage)
        PatternBlockKeepingCard(block = report.blockKeeping)
        PatternRoutineLists(routines = report.routines)
        PatternTaskHabitsCard(habits = report.taskHabits)
    }
}

/** 공유 이미지 내용: 제목 · 기간 문구 + 카드들, 가장자리에 여백 (웹 PAD 32) */
@Composable
private fun PatternShareContent(report: PatternReport, modifier: Modifier = Modifier) {
    Column(
        modifier.background(NC.canvas).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("나의 패턴", style = NType.h2, color = NC.textPrimary)
            Text(PatternFormat.rangeLabel(report), style = pt(14), color = NC.ink3)
        }
        PatternReportBody(report = report)
    }
}

/**
 * 리포트를 미리 보여주고 [공유하기]를 누르면 그 그림을 이미지로 떠서 공유 창(메신저 · 저장 등)으로 넘겨요.
 * iOS 는 ImageRenderer 로 먼저 이미지를 만든 뒤 시트에 띄웠지만,
 * Compose 는 화면에 그려진 것만 이미지로 뜰 수 있어서 시트 안에 리포트를 그리고 그걸 떠요 (rememberGraphicsLayer).
 */
@Composable
private fun PatternShareSheet(report: PatternReport, onDismiss: () -> Unit) {
    val layer = rememberGraphicsLayer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val line = NC.line
    val shape = RoundedCornerShape(Radius.xl)

    SheetScaffold(
        onDismiss = onDismiss,
        title = "리포트 공유",
        description = "이미지로 저장하거나 공유할 수 있어요.",
        fullHeight = true,
        footer = { _ ->
            NButton("공유하기", icon = "square.and.arrow.up", loading = busy) {
                scope.launch {
                    busy = true
                    try {
                        val bitmap = layer.toImageBitmap().asAndroidBitmap()
                        val error = patternShareImage(context, bitmap, "naleul-pattern-${report.range.end}.png")
                        PT.track("pattern.share_result", mapOf("period" to PT.period.name, "result" to if (error == null) "success" else "fail"))
                        if (error != null) Toast.error(error)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        PT.track("pattern.share_result", mapOf("period" to PT.period.name, "result" to "fail"))
                        Toast.error("이미지를 만들지 못했어요. 다시 시도해 주세요.")
                    } finally {
                        busy = false
                    }
                }
            }
        },
    ) { _ ->
        PatternShareContent(
            report = report,
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(1.dp, line, shape)
                // 이 아래 내용을 레이어에 기록해 두고 그대로 그려요 → 나중에 toImageBitmap() 으로 이미지가 돼요
                .drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                },
        )
    }
}

/**
 * 이미지를 사진 보관함(Pictures/Naleul)에 PNG 로 저장하고 공유 창을 띄워요. 실패하면 안내 문구, 성공하면 null.
 * (따로 파일 공유 설정(FileProvider) 없이 MediaStore 주소로 넘겨요 — Android 10 이상)
 */
private suspend fun patternShareImage(context: Context, bitmap: Bitmap, fileName: String): String? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return "Android 10 이상에서 이미지로 공유할 수 있어요."
    val uri = withContext(Dispatchers.IO) {
        // 하드웨어 비트맵은 바로 압축하지 못할 수 있어서 일반 비트맵으로 바꿔요
        val soft = if (bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Naleul")
        }
        val resolver = context.contentResolver
        val u = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext null
        val written = resolver.openOutputStream(u)?.use { soft.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: false
        if (written) u else null
    } ?: return "이미지를 만들지 못했어요. 다시 시도해 주세요."
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, "리포트 공유").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    context.startActivity(chooser)
    Toast.success("사진 보관함에도 저장했어요.")
    return null
}

@Preview(showBackground = true, heightDp = 1600)
@Composable
private fun PatternScreenPreview() {
    NaleulTheme(dark = false) {
        PatternScreen(preview = PatternSampleData.report)
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun PatternScreenEmptyPreview() {
    NaleulTheme(dark = false) {
        PatternScreen(preview = PatternSampleData.emptyReport)
    }
}
