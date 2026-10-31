// feature/home/ReflectionsScreen.kt
//
// iOS Features/Home/ReflectionsScreen.swift
// 회고 모아보기 — 웹 features/reflection/ui/ReflectionsView.tsx (/reflections?goal=).
// 목표 상세 · 홈 · 업무형 보드에서 남긴 회고를 날짜별로 한곳에서 봐요 (GET /home/reflections).
//  - 목표형: 그날 수치 + 기분 + 한 줄 회고 (오늘 기록)
//  - 업무형: 회고 글
// 위의 목표 칩으로 한 목표만, 일간 · 주간 · 월간 탭과 ‹ › 로 기간을 골라 봐요.
//
// 목표 상세 "회고" 탭도 같은 목록을 써요:
//   val feed = rememberScreenModel(goalId) { ReflectionsViewModel(goalId) }
//   ReloadOn(listOf(DataKey.home, DataKey.goals, DataKey.work), id = feed.reloadId) { feed.load() }
//   ReflectionTimelineView(vm = feed, showGoal = false, emptyHint = hint)

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.naleul.naleul.feature.home

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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** 회고 모아보기 기간 (웹 ReflectionPeriod) */
enum class ReflectionPeriod {
    /** 전체 = 기간 없이 최근 기록 (기본) */
    all, day, week, month;

    val rawValue: String get() = name
    val id: String get() = rawValue

    val label: String
        get() = when (this) {
            all -> "전체"
            day -> "일간"
            week -> "주간"
            month -> "월간"
        }

    val currentLabel: String
        get() = when (this) {
            all -> ""
            day -> "오늘"
            week -> "이번 주"
            month -> "이번 달"
        }

    companion object {
        fun fromRaw(raw: String?): ReflectionPeriod? = entries.firstOrNull { it.rawValue == raw }
    }
}

class ReflectionsViewModel(goalId: Int?) : ScreenModel() {
    var goalId by mutableStateOf(goalId)
    /** 전체(기본) · 일간 · 주간 · 월간 */
    @set:JvmName("assignPeriod")
    var period by mutableStateOf(ReflectionPeriod.all)
    /** 이 날짜가 든 기간을 봐요 (‹ › 로 옮겨요) */
    var anchor by mutableStateOf(KST.today())
    var page by mutableStateOf<ReflectionFeedPage?>(null)
    var goals by mutableStateOf<List<GoalCategory>>(emptyList())
    var loading by mutableStateOf(false)
    var failed by mutableStateOf(false)

    // MARK: Amplitude
    /** 이 목록이 쓰인 화면 — reflections(회고 모아보기) · goal_detail(목표 상세 "회고" 탭) */
    var surface = "reflections"
    /** 마지막으로 보낸 reflection_timeline.view 의 기간 · 목표 (같으면 다시 안 보내요) */
    var viewKey by mutableStateOf("")
        private set
    private val scrollSent = mutableSetOf<Int>()
    private var lastErrorStatus: Int? = null
    private var pageviewSent = false
    private var goalsLoaded = false
    private var unmatchedSent: Int? = null

    /** 기간 [from, to] (양 끝 포함) — Swift 튜플 (from:, to:) */
    data class Range(val from: String, val to: String)

    /** (회고가 가장 많은 목표) — Swift 튜플 (title:, days:) */
    data class TopGoal(val title: String, val days: Int, val colorCode: String? = null)

    data class DayGroup(val date: String, val items: List<ReflectionEntry>) {
        val id: String get() = date
    }

    /** 지금 보는 기간 [from, to] (양 끝 포함) */
    val range: Range
        get() = when (period) {
            // 전체: 서버 기본(최근 30일)과 같은 범위 — 요약 · 키에만 써요
            ReflectionPeriod.all -> Range(KST.addDays(KST.today(), -29), KST.today())
            ReflectionPeriod.day -> Range(anchor, anchor)
            ReflectionPeriod.week -> {
                val from = KST.startOfWeek(anchor)
                Range(from, KST.addDays(from, 6))
            }
            ReflectionPeriod.month -> {
                val y = KST.yearOf(anchor)
                val m = KST.monthOf(anchor)
                Range(KST.ymd(y, m, 1), KST.ymd(y, m, KST.lastDayOfMonth(y, m)))
            }
        }

    /** 다시 불러올 때 쓰는 키 (목표 · 기간이 바뀌면 다시) */
    val reloadId: String get() = "${goalId ?: 0}-${period.rawValue}-${range.from}-${range.to}"
    val atLatest: Boolean get() = range.to >= KST.today()

    val title: String
        get() {
            val (from, to) = range
            val today = KST.today()
            return when (period) {
                ReflectionPeriod.all -> "최근 기록"
                ReflectionPeriod.day -> if (from == today) "오늘 · ${KST.formatMonthDay(from)}" else KST.formatMonthDay(from)
                ReflectionPeriod.month -> "${KST.yearOf(from)}년 ${KST.monthOf(from)}월"
                ReflectionPeriod.week -> {
                    val head = if (KST.startOfWeek(today) == from) "이번 주 · " else ""
                    val toText = if (KST.monthOf(to) != KST.monthOf(from)) "${KST.monthOf(to)}월 ${KST.dayOfMonth(to)}일" else "${KST.dayOfMonth(to)}일"
                    "$head${KST.monthOf(from)}월 ${KST.dayOfMonth(from)}일 – $toText"
                }
            }
        }

    /** 이 기간의 날 수 (이번 기간이면 오늘까지) */
    val totalDays: Int
        get() {
            val (from, to) = range
            val end = if (atLatest) minOf(to, KST.today()) else to
            return maxOf(KST.diffDays(from, end) + 1, 1)
        }

    val entries: List<ReflectionEntry> get() = page?.entries ?: emptyList()

    /** 날짜별로 묶기 (이미 최근 날짜 순으로 와요) */
    val groups: List<DayGroup>
        get() {
            val out = mutableListOf<DayGroup>()
            for (e in entries) {
                val last = out.lastOrNull()
                if (last != null && last.date == e.date) {
                    out[out.size - 1] = last.copy(items = last.items + e)
                } else {
                    out.add(DayGroup(date = e.date, items = listOf(e)))
                }
            }
            return out
        }

    /** 이 기간에 가장 많이 돌아본 목표 (목표가 2개 이상일 때) */
    val topGoal: TopGoal?
        get() {
            val byGoal = LinkedHashMap<Int, Pair<String, MutableSet<String>>>()
            val colors = HashMap<Int, String?>()
            for (e in entries) {
                val g = byGoal.getOrPut(e.goalCategoryId) { e.goalTitle to mutableSetOf() }
                g.second.add(e.date)
                colors.getOrPut(e.goalCategoryId) { e.colorCode }
            }
            if (byGoal.size < 2) return null
            val top = byGoal.entries.maxByOrNull { it.value.second.size } ?: return null
            return TopGoal(top.value.first, top.value.second.size, colors[top.key])
        }

    /** 회고를 남길 수 있는 목표 (기본 생활 습관 · 임시 목표 빼고) */
    val filterGoals: List<GoalCategory>
        get() = goals.filter { it.goalMode != GoalMode.HABIT && it.temporary != true }

    suspend fun load() {
        if (page == null) loading = true
        failed = false
        val (from, to) = range
        val loadingPeriod = period
        try {
            coroutineScope {
                val feed = async {
                    // 전체: 기간 없이 (API 기본값) · 그 외: 그 기간만
                    if (loadingPeriod == ReflectionPeriod.all) HomeAPI.reflections(goalId = goalId)
                    else HomeAPI.reflections(before = KST.addDays(to, 1), days = KST.diffDays(from, to) + 1, goalId = goalId)
                }
                // 목표 칩 목록은 처음 한 번만 (실패해도 회고는 보여줘요)
                val list = async {
                    if (goals.isEmpty()) {
                        try { GoalAPI.goals() } catch (e: CancellationException) { throw e } catch (e: Exception) {
                            // [UX] 칩이 '전체'만 남고 안내가 없어요
                            if (surface == "reflections") RF.track("reflections.goals_load_fail", mapOf("error_status" to RF.status(e)))
                            null
                        }
                    } else null
                }
                val result = feed.await()
                list.await()?.let { goals = it; goalsLoaded = true }
                trackPageviewIfNeeded()
                // 불러오는 사이 기간을 또 바꿨으면 버려요
                if (period == loadingPeriod && range.from == from && range.to == to) {
                    page = result
                    result?.let { trackView(it) }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val status = RF.status(e)
            lastErrorStatus = status
            val props = mapOf("surface" to surface, "period" to period.analyticsValue, "period_offset" to offset, "error_status" to status)
            if (page == null) {
                failed = true
                RF.track("reflection_timeline.load_fail", props)
            } else {
                // [UX] 이전 목록이 그대로 남고 안내가 없어요 (기간을 바꾼 직후면 제목만 새 기간)
                RF.track("reflection_timeline.refresh_fail", props)
            }
            trackPageviewIfNeeded()
        }
        loading = false
    }

    // MARK: Amplitude

    /** 오늘이 든 기간에서 몇 기간 떨어졌는지 (지금 0 · 지난주 -1) */
    fun offset(p: ReflectionPeriod, anchor: String): Int {
        val today = KST.today()
        return when (p) {
            ReflectionPeriod.all -> 0
            ReflectionPeriod.day -> KST.diffDays(today, anchor)
            ReflectionPeriod.week -> KST.diffDays(KST.startOfWeek(today), KST.startOfWeek(anchor)) / 7
            ReflectionPeriod.month -> (KST.yearOf(anchor) - KST.yearOf(today)) * 12 + KST.monthOf(anchor) - KST.monthOf(today)
        }
    }
    val offset: Int get() = offset(period, anchor)

    /** 회고 모아보기 첫 진입 — 목표 칩을 불러온 뒤 한 번 (웹과 같이 칩 개수를 같이 보내요) */
    private fun trackPageviewIfNeeded() {
        if (surface != "reflections") return
        if (!pageviewSent) {
            pageviewSent = true
            RF.track("reflections.pageview", mapOf(
                "entry_source" to (RF.pendingEntry ?: "other"),
                "goal_category_id" to goalId,
                "filter_option_count" to (if (goalsLoaded) filterGoals.size else null),
            ))
            RF.pendingEntry = null
        }
        // [UX] 연 목표가 칩에 없음 (임시 · 생활 습관 · 지운 목표)
        val id = goalId
        if (goalsLoaded && id != null && unmatchedSent != id && filterGoals.none { it.goalCategoryId == id }) {
            unmatchedSent = id
            RF.track("reflections.goal_param_unmatched", mapOf("goal_category_id" to id))
        }
    }

    /** 기간 회고를 그린 직후 — 기간 · 목표마다 한 번 (새로고침 · 고친 뒤 다시 불러오기는 빼요) */
    private fun trackView(p: ReflectionFeedPage) {
        val key = "${period.rawValue}:${range.from}:${goalId ?: 0}"
        if (key == viewKey) return
        viewKey = key
        scrollSent.clear()
        val es = p.entries
        RF.track("reflection_timeline.view", mapOf(
            "surface" to surface,
            "goal_category_id" to goalId,
            "period" to period.analyticsValue,
            "period_offset" to offset,
            "entry_count" to es.size,
            "check_in_count" to es.count { it.kind == ReflectionKind.CHECK_IN },
            "retro_count" to es.count { it.kind == ReflectionKind.RETRO },
            "day_count" to es.map { it.date }.toSet().size,
            "elapsed_day_count" to totalDays,
            "is_truncated" to p.hasMore,
        ))
        if (es.isEmpty()) {
            RF.track("reflection_timeline.empty_view", mapOf(
                "surface" to surface, "goal_category_id" to goalId, "period" to period.analyticsValue, "period_offset" to offset,
            ))
        }
    }

    /** 날짜 묶음 카드(위에서 index 번째)가 화면에 절반 이상 들어왔을 때 → 25 · 50 · 75 · 100% 구간마다 한 번 */
    fun sawGroup(index: Int) {
        val n = groups.size
        if (n == 0) return
        val pct = (index + 1) * 100 / n
        for (mark in listOf(25, 50, 75, 100)) {
            if (pct >= mark && scrollSent.add(mark)) {
                RF.track("reflection_timeline.scroll_depth", mapOf(
                    "surface" to surface, "period" to period.analyticsValue, "scroll_percent" to mark, "entry_count" to entries.size,
                ))
            }
        }
    }

    fun trackRetry() {
        RF.track("reflection_timeline.retry_click", mapOf("surface" to surface, "period" to period.analyticsValue, "error_status" to lastErrorStatus))
    }

    fun trackPullRefresh() {
        RF.track("reflections.pull_refresh", mapOf("goal_category_id" to goalId, "period" to period.analyticsValue))
    }

    /** 목표 칩을 누름 ('전체' 0, 목표 칩 1부터) */
    fun selectChip(id: Int?) {
        if (id == goalId) return
        val options = filterGoals
        val index = id?.let { i -> options.indexOfFirst { it.goalCategoryId == i }.takeIf { it >= 0 } }
        RF.track("reflections.goal_filter_select", mapOf(
            "goal_category_id" to id,
            "goal_mode" to index?.let { options[it].analyticsMode },
            "list_position" to (index ?: -1) + 1,
            "filter_option_count" to options.size,
        ))
        select(id)
    }

    /** 목표를 바꾸면 화면의 ReloadOn(id = reloadId)이 다시 불러와요 */
    fun select(id: Int?) {
        if (id == goalId) return
        goalId = id
        page = null
        loading = true
    }

    fun setPeriod(p: ReflectionPeriod) {
        if (p == period) return
        RF.track("reflection_timeline.period_change", mapOf(
            "surface" to surface, "period" to p.analyticsValue, "from_period" to period.analyticsValue,
            "period_offset" to offset(p, anchor),
        ))
        period = p
    }

    /** '오늘 / 이번 주 / 이번 달' */
    fun goToday() {
        val next = KST.today()
        RF.track("reflection_timeline.nav_click", mapOf(
            "surface" to surface, "period" to period.analyticsValue, "date_action" to "today", "period_offset" to offset(period, next),
        ))
        anchor = next
    }

    fun move(n: Int) {
        val next = when (period) {
            ReflectionPeriod.all -> anchor
            ReflectionPeriod.day -> KST.addDays(anchor, n)
            ReflectionPeriod.week -> KST.addDays(anchor, n * 7)
            ReflectionPeriod.month -> KST.addMonths(anchor, n)
        }
        RF.track("reflection_timeline.nav_click", mapOf(
            "surface" to surface, "period" to period.analyticsValue, "date_action" to (if (n < 0) "prev" else "next"),
            "period_offset" to offset(period, next),
        ))
        anchor = next
    }

    companion object {
        /** 미리보기용 (네트워크 없이 샘플) */
        fun preview(empty: Boolean = false): ReflectionsViewModel {
            val m = ReflectionsViewModel(goalId = null)
            m.page = if (empty) {
                ReflectionFeedPage(from = KST.addDays(KST.today(), -6), to = KST.today(), entries = emptyList(), hasMore = false)
            } else {
                ReflectionsSampleData.page(today = KST.today())
            }
            return m
        }
    }
}

/**
 * 회고 모아보기 화면 (쌓이는 화면).
 * previewVM 은 미리보기 전용 — 주면 서버를 부르지 않아요 (iOS init(vm:)).
 */
@Composable
fun ReflectionsScreen(initialGoalId: Int? = null, previewVM: ReflectionsViewModel? = null, asTab: Boolean = false) {
    val autoload = previewVM == null
    val vm = rememberScreenModel(initialGoalId) { previewVM ?: ReflectionsViewModel(goalId = initialGoalId) }
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }

    // 화면이 열릴 때 · 회고를 고치면 다시 불러와요
    if (autoload) {
        TrackScreen("ReflectionsScreen")
        ReloadOn(listOf(DataKey.home, DataKey.goals, DataKey.work), id = vm.reloadId) { vm.load() }
    }

    // 탭(회고)으로 열면 위 뒤로가기 줄 없이 — 공통 헤더 아래에 바로 그려요
    val body: @Composable () -> Unit = {
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                scope.launch {
                    refreshing = true
                    if (autoload) vm.trackPullRefresh()
                    try { if (autoload) vm.load() } finally { refreshing = false }
                }
            },
            modifier = Modifier.fillMaxSize().canvasBackground(),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = PageGutter, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "목표마다 남긴 기록과 회고를 날짜별로 한 번에 봐요. 홈 · 목표 상세 어디서 쓴 것이든 여기 모여요.",
                    style = pt(13), color = NC.ink3,
                )
                ReflectionsFilterChips(vm)
                ReflectionTimelineView(vm = vm)
            }
        }
    }
    if (asTab) body() else PushedScreen(title = "회고 모아보기") { body() }
}

// MARK: 목표 고르기

@Composable
private fun ReflectionsFilterChips(vm: ReflectionsViewModel) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ReflectionsChip(title = "전체", dot = null, selected = vm.goalId == null) { vm.selectChip(null) }
        for (g in vm.filterGoals) {
            ReflectionsChip(
                title = (g.emoji?.let { if (it.isEmpty()) "" else "$it " } ?: "") + g.goalCategoryName,
                dot = goalColor(g.colorCode),
                selected = vm.goalId == g.goalCategoryId,
            ) { vm.selectChip(g.goalCategoryId) }
        }
    }
}

@Composable
private fun ReflectionsChip(title: String, dot: Color?, selected: Boolean, action: () -> Unit) {
    Row(
        Modifier
            .height(34.dp)
            .clip(CircleShape)
            .background(if (selected) NC.actionPrimary else NC.surface, CircleShape)
            .border(1.dp, if (selected) NC.actionPrimary else NC.borderSubtle, CircleShape)
            .clickable(role = Role.Tab, onClick = action)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (dot != null) Box(Modifier.size(8.dp).background(dot, CircleShape))
        Text(title, style = pt(12, PTWeight.semibold), color = if (selected) NC.actionPrimaryFg else NC.ink2, maxLines = 1)
    }
}

/**
 * 기간별 회고 목록 — 일간 · 주간 · 월간 탭 + ‹ › 기간 이동 (웹 ReflectionTimeline).
 * 회고 모아보기와 목표 상세 "회고" 탭이 같이 써요. 스크롤은 감싸는 화면이 해요 (안에 LazyColumn 없음).
 *
 * @param showGoal 줄마다 목표 이름을 보일지 (null 이면 "전체"일 때만)
 * @param emptyHint 비었을 때 안내 문구
 */
@Composable
fun ReflectionTimelineView(
    vm: ReflectionsViewModel,
    showGoal: Boolean? = null,
    emptyHint: String? = null,
    modifier: Modifier = Modifier,
) {
    // 목표 줄은 '전체' 목표 칩이거나 기록이 여러 목표에 걸칠 때만
    val goalShown = showGoal ?: (vm.goalId == null || vm.entries.map { it.goalCategoryId }.distinct().size > 1)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ReflectionPeriodBar(vm)
        ReflectionTimelineContent(vm = vm, goalShown = goalShown, emptyHint = emptyHint)
    }
}

// MARK: 기간 고르기

@Composable
private fun ReflectionPeriodBar(vm: ReflectionsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(NC.bgSubtle, RoundedCornerShape(Radius.segment))
                .padding(3.dp),
        ) {
            for (p in ReflectionPeriod.entries) {
                val on = vm.period == p
                val itemShape = RoundedCornerShape(Radius.segmentItem)
                Box(
                    Modifier
                        .weight(1f)
                        .height(34.dp)
                        .then(if (on) Modifier.segShadow(itemShape).background(NC.bgSurface, itemShape) else Modifier)
                        .clip(itemShape)
                        .clickable(role = Role.Tab) { vm.setPeriod(p) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(p.label, style = NType.labelMD, color = if (on) NC.textPrimary else NC.textSecondary)
                }
            }
        }

        // 날짜 이동 (전체는 기간이 없어 숨겨요): 왼쪽 정렬 ‹ 제목 › + 오늘/이번 주/이번 달 — 내용만큼만 차지해요
        if (vm.period != ReflectionPeriod.all) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NIconButton("chevron.left", size = 16.dp, label = "이전 기간", modifier = Modifier.size(28.dp)) { vm.move(-1) }
                Text(
                    vm.title,
                    style = NType.bodyMDMedium.copy(fontFeatureSettings = "tnum"),
                    color = NC.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 180.dp),
                )
                NIconButton(
                    "chevron.right", size = 16.dp, label = "다음 기간", enabled = !vm.atLatest,
                    modifier = Modifier.size(28.dp).alpha(if (vm.atLatest) 0.3f else 1f),
                ) { vm.move(1) }
                if (!vm.atLatest) {
                    Box(
                        Modifier
                            .padding(start = 4.dp)
                            .defaultMinSize(minHeight = 28.dp)
                            .clip(RoundedCornerShape(Radius.iconBtn))
                            .clickable(role = Role.Button) { vm.goToday() }
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(vm.period.currentLabel, style = NType.labelMD, color = NC.textBrand)
                    }
                }
            }
        }
    }
}

// MARK: 내용

@Composable
private fun ReflectionTimelineContent(vm: ReflectionsViewModel, goalShown: Boolean, emptyHint: String?) {
    val scope = rememberCoroutineScope()
    var opened by remember { mutableStateOf<ReflectionEntry?>(null) }
    opened?.let { entry ->
        key(entry.id) {
            ReflectionDetailSheet(
                entry = entry,
                surface = vm.surface,
                onDismiss = { opened = null },
                onChanged = { scope.launch { vm.load() } },
            )
        }
    }
    when {
        vm.loading -> Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 200.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = NC.ink3, strokeWidth = 2.5.dp)
        }
        vm.failed -> EmptyStateCard(title = "회고를 불러오지 못했어요", icon = "exclamationmark.triangle") {
            NButton("다시 불러오기", variant = NButtonVariant.secondary, size = NButtonSize.sm) {
                vm.trackRetry()
                scope.launch { vm.load() }
            }
        }
        vm.groups.isEmpty() -> EmptyStateCard(
            title = "${when (vm.period) {
                ReflectionPeriod.all -> "최근"
                ReflectionPeriod.day -> "이날"
                ReflectionPeriod.week -> "이번 주"
                ReflectionPeriod.month -> "이 달"
            }}에 남긴 회고가 없어요",
            icon = "books.vertical",
            description = emptyHint ?: "홈의 '오늘 회고'나 목표 상세의 '오늘 기록'에서 하루를 돌아보면 여기 모여요.",
        )
        else -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (vm.period != ReflectionPeriod.day) ReflectionSummary(vm = vm, goalShown = goalShown)
            vm.groups.forEachIndexed { index, g ->
              // 기간 · 목표가 바뀌면 "보였음" 기억을 새로 (스크롤 깊이)
              key(vm.viewKey, g.date) {
                Column(
                    // 스크롤 깊이 (reflection_timeline.scroll_depth)
                    Modifier.onFirstHalfVisible { vm.sawGroup(index) },
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (vm.period != ReflectionPeriod.day) {
                        Row(
                            Modifier.padding(horizontal = 2.dp),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(KST.formatMonthDay(g.date), style = pt(15, PTWeight.bold), color = NC.ink)
                            Text(reflectionRelLabel(g.date), style = pt(12), color = NC.ink3, modifier = Modifier.padding(bottom = 1.dp))
                        }
                    }
                    Column(Modifier.fillMaxWidth().card()) {
                        g.items.forEachIndexed { i, e ->
                            key(e.id) {
                                if (i > 0) HorizontalDivider(color = NC.entryDivider, modifier = Modifier.padding(horizontal = 16.dp))
                                ReflectionEntryRow(entry = e, showGoal = goalShown, surface = vm.surface, onOpen = { opened = it })
                            }
                        }
                    }
                }
              }
            }
        }
    }
}

/** 이 기간 요약 (스탯 카드): 기록한 날 / 전체 날 · 기분별 날 수 · 가장 많이 돌아본 목표 */
@Composable
private fun ReflectionSummary(vm: ReflectionsViewModel, goalShown: Boolean) {
    val checkIns = vm.entries.filter { it.kind == ReflectionKind.CHECK_IN }
    val shape = RoundedCornerShape(Radius.card)
    Column(
        Modifier
            .fillMaxWidth()
            .background(NC.bgSurface, shape)
            .border(1.dp, NC.borderSubtle, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("기록한 날", style = NType.captionMD, color = NC.textSecondary)
                Text(
                    // 전체는 기간이 없어 분모 없이 "N일"
                    if (vm.period == ReflectionPeriod.all) "${vm.groups.size}일" else "${vm.groups.size}일 / ${vm.totalDays}일",
                    style = NType.h3,
                    color = NC.textPrimary,
                )
            }
            if (checkIns.any { it.mood != null }) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    for (m in GoalMoodOption.all) {
                        val n = checkIns.count { it.mood == m.value }
                        Row(
                            Modifier.semantics { contentDescription = "${m.label} ${n}일" },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(m.emoji, fontSize = 20.sp)
                            Text("$n", style = NType.labelLG, color = NC.textPrimary)
                        }
                    }
                }
            }
        }
        val top = vm.topGoal
        if (goalShown && top != null) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("가장 많이 돌아본 목표", style = NType.captionMD, color = NC.textSecondary)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(8.dp).background(goalColor(top.colorCode), CircleShape))
                    Text(top.title, style = NType.bodyMDMedium, color = NC.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Text("· ${top.days}일", style = NType.bodyMD, color = NC.textSecondary)
                }
            }
        }
    }
}

private fun reflectionRelLabel(date: String): String {
    val diff = KST.diffDays(date, KST.today())
    if (diff == 0) return "오늘"
    if (diff == 1) return "어제"
    return "${diff}일 전"
}

/** 회고 한 줄 — 목표 · 수치 · 기분 · 내용 */
@Composable
fun ReflectionEntryRow(
    entry: ReflectionEntry,
    showGoal: Boolean = true,
    surface: String = "reflections",
    /** 줄을 누르면 자세히 보기 (2026-10). null 이면 예전처럼 반응 없음 */
    onOpen: ((ReflectionEntry) -> Unit)? = null,
) {
    val e = entry
    val isRetro = e.kind == ReflectionKind.RETRO
    val mood = GoalMoodOption.of(e.mood)
    val ink = NC.ink
    val ink3 = NC.ink3
    Row(
        Modifier
            .fillMaxWidth()
            // [UX] 줄 본문을 눌러도 반응이 없어요 — 목표 이름은 자기 clickable 이 먼저 받아요
            .then(
                if (onOpen != null) Modifier.clickable(role = Role.Button) {
                    RF.track("reflection_timeline.entry_click", mapOf(
                        "surface" to surface, "entry_kind" to e.kind.name,
                        "days_ago" to KST.diffDays(e.date, KST.today()), "goal_mode" to e.goalMode?.name,
                    ))
                    onOpen(e)
                } else Modifier.clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {
                    RF.track("reflection_timeline.entry_dead_click", mapOf(
                        "surface" to surface, "entry_kind" to e.kind.name,
                        "days_ago" to KST.diffDays(e.date, KST.today()), "goal_mode" to e.goalMode?.name,
                    ))
                }
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(28.dp)
                .background(if (isRetro) NC.subtle else NC.brandSoft, CircleShape)
                .semantics { contentDescription = if (isRetro) "회고 글" else "오늘 기록" },
            contentAlignment = Alignment.Center,
        ) {
            SfImage(if (isRetro) "pencil" else "square.and.pencil", size = 13.dp, tint = if (isRetro) NC.ink2 else NC.textBrand)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // (1) 목표 줄 — 점 8 + 이름 (Label/SM · text/secondary)
            if (showGoal) {
                Row(
                    Modifier.clickable(role = Role.Button) {
                        RF.track("reflection_timeline.goal_link_click", mapOf(
                            "surface" to surface, "goal_category_id" to e.goalCategoryId, "goal_mode" to e.goalMode?.name,
                            "entry_kind" to e.kind.name, "days_ago" to KST.diffDays(e.date, KST.today()),
                        ))
                        AppRouter.openGoal(e.goalCategoryId)
                    },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(8.dp).background(goalColor(e.colorCode), CircleShape))
                    Text(e.goalTitle, style = NType.labelSM, color = NC.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            // (2) 배지 줄 — 수치 → 기분, 한 줄 (넘치면 이름을 줄여요)
            val v = e.value
            val t = e.reviewType
            val reviewLabel = if (isRetro && t != null && t != "DAILY") (if (t == "WEEKLY") "주간 회고" else "월간 회고") else null
            if (v != null || mood != null || reviewLabel != null) {
                val sec = NC.textSecondary
                val pri = NC.textPrimary
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (v != null) {
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = sec, fontWeight = FontWeight.Medium)) { append("${e.metricName ?: "수치"} ") }
                                withStyle(SpanStyle(color = pri, fontWeight = FontWeight.SemiBold)) {
                                    append(GoalDetailLogic.fmtNum(v) + (e.unit ?: ""))
                                }
                            },
                            style = NType.bodySM,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .background(NC.badgeBg, CircleShape)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    if (mood != null) {
                        Text(
                            "${mood.emoji} ${mood.label}",
                            style = NType.bodySMMedium,
                            color = NC.textPrimary,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier
                                .background(NC.badgeBg, CircleShape)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    if (reviewLabel != null) {
                        Text(reviewLabel, style = NType.labelXS, color = NC.textTertiary, maxLines = 1, softWrap = false)
                    }
                }
            }
            // (3) 내용 (배지 줄과 2dp 더 띄워요)
            val note = e.note
            if (!note.isNullOrEmpty()) {
                // iOS .textSelection(.enabled) — 길게 눌러 복사할 수 있게
                if (onOpen != null) {
                    // 목록에선 4줄까지 — 전체는 눌러서 자세히 보기
                    Text(note, style = pt(14).copy(lineHeight = 21.sp), color = NC.ink2, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                } else SelectionContainer(Modifier.padding(top = 2.dp)) {
                    Text(note, style = pt(14).copy(lineHeight = 21.sp), color = NC.ink2)
                }
            } else if (!isRetro) {
                Text("수치만 남겼어요", style = pt(12), color = NC.ink4)
            }
        }
    }
}

/** 회고 글 최대 길이 (웹 · iOS 와 같게) */
private const val REFLECTION_MAX_LENGTH = 1000

/**
 * 회고 자세히 보기 (2026-10) — 날짜 · 목표 · 수치 · 기분 · 전체 내용.
 * 회고 글(RETRO)은 여기서 고치거나 지울 수 있어요. 오늘 기록(CHECK_IN)은 목표 상세 "오늘 기록"에서 고쳐요.
 */
@Composable
private fun ReflectionDetailSheet(
    entry: ReflectionEntry,
    surface: String,
    onDismiss: () -> Unit,
    onChanged: () -> Unit,
) {
    val e = entry
    val isRetro = e.kind == ReflectionKind.RETRO
    val retroId = e.retrospectiveId
    val editable = isRetro && retroId != null
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(e.note ?: "") }
    var busy by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val mood = GoalMoodOption.of(e.mood)
    val baseProps: EventProps = mapOf(
        "surface" to surface, "entry_kind" to e.kind.name,
        "days_ago" to KST.diffDays(e.date, KST.today()), "goal_mode" to e.goalMode?.name,
    )
    LaunchedEffect(Unit) { RF.track("reflection_detail.view", baseProps + ("editable" to editable)) }

    SheetScaffold(
        onDismiss = onDismiss,
        title = if (editing) "회고 고치기" else "${KST.formatMonthDay(e.date)} · ${reflectionRelLabel(e.date)}",
        description = if (isRetro) (when (e.reviewType) { "WEEKLY" -> "주간 회고"; "MONTHLY" -> "월간 회고"; else -> "회고 글" }) else "오늘 기록",
        dismissible = !busy,
        footer = { close ->
            if (editing) {
                NButton("취소", variant = NButtonVariant.secondary, enabled = !busy) {
                    RF.track("reflection_detail.edit_cancel", baseProps)
                    draft = e.note ?: ""
                    editing = false
                }
                NButton("저장", loading = busy, enabled = !busy && draft.isNotBlank() && draft.trim() != (e.note ?: "").trim()) {
                    val id = retroId ?: return@NButton
                    busy = true
                    scope.launch {
                        try {
                            HomeAPI.updateRetro(id, draft.trim(), e.goalCategoryId)
                            RF.track("reflection_detail.edit_save", baseProps + ("content_length" to draft.trim().length))
                            Toast.success("회고를 고쳤어요")
                            onChanged()
                            close()
                        } catch (ex: CancellationException) {
                            throw ex
                        } catch (ex: Exception) {
                            RF.track("reflection_detail.edit_fail", baseProps + ("http_status" to RF.status(ex)))
                            Toast.error("회고를 고치지 못했어요. 잠시 후 다시 시도해 주세요.")
                        }
                        busy = false
                    }
                }
            } else if (editable) {
                NButton("삭제", variant = NButtonVariant.secondary, enabled = !busy) {
                    RF.track("reflection_detail.delete_click", baseProps)
                    confirmDelete = true
                }
                NButton("수정", enabled = !busy) {
                    RF.track("reflection_detail.edit_click", baseProps)
                    editing = true
                }
            } else {
                NButton("목표에서 고치기", variant = NButtonVariant.secondary) {
                    RF.track("reflection_detail.goal_link_click", baseProps + ("goal_category_id" to e.goalCategoryId))
                    close()
                    AppRouter.openGoal(e.goalCategoryId)
                }
                NButton("닫기") { close() }
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(8.dp).background(goalColor(e.colorCode), CircleShape))
                Text(e.goalTitle, style = NType.labelSM, color = NC.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val v = e.value
            if (v != null || mood != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (v != null) {
                        Text(
                            "${e.metricName ?: "수치"} ${GoalDetailLogic.fmtNum(v)}${e.unit ?: ""}",
                            style = NType.bodySMMedium,
                            color = NC.textPrimary,
                            modifier = Modifier.background(NC.badgeBg, CircleShape).padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    if (mood != null) {
                        Text(
                            "${mood.emoji} ${mood.label}",
                            style = NType.bodySMMedium,
                            color = NC.textPrimary,
                            modifier = Modifier.background(NC.badgeBg, CircleShape).padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            if (editing) {
                NTextField(
                    value = draft,
                    onValueChange = { draft = it.take(REFLECTION_MAX_LENGTH) },
                    placeholder = "오늘을 돌아보며 남기고 싶은 말을 적어 주세요",
                    singleLine = false,
                    minHeight = 160.dp,
                    enabled = !busy,
                    maxLength = REFLECTION_MAX_LENGTH,
                )
                Text("${draft.length} / $REFLECTION_MAX_LENGTH", style = pt(12), color = NC.ink4, modifier = Modifier.align(Alignment.End))
            } else {
                val note = e.note
                if (!note.isNullOrEmpty()) {
                    SelectionContainer {
                        Text(note, style = pt(15).copy(lineHeight = 23.sp), color = NC.ink)
                    }
                } else {
                    Text(if (isRetro) "내용이 없어요" else "수치만 남겼어요", style = pt(13), color = NC.ink4)
                }
            }
        }
    }

    ConfirmDialog(
        isPresented = confirmDelete,
        onDismiss = { confirmDelete = false },
        title = "이 회고를 지울까요?",
        message = "지운 회고는 되돌릴 수 없어요.",
        confirmLabel = "삭제",
        tone = ConfirmTone.danger,
        onCancel = { RF.track("reflection_detail.delete_cancel", baseProps) },
    ) {
        val id = retroId ?: return@ConfirmDialog
        busy = true
        scope.launch {
            try {
                HomeAPI.deleteRetro(id)
                RF.track("reflection_detail.delete_confirm", baseProps)
                Toast.success("회고를 지웠어요")
                onChanged()
                onDismiss()
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                RF.track("reflection_detail.delete_fail", baseProps + ("http_status" to RF.status(ex)))
                Toast.error("회고를 지우지 못했어요. 잠시 후 다시 시도해 주세요.")
            }
            busy = false
        }
    }
}

/** 미리보기 샘플 (백엔드와 같은 JSON 을 디코딩해요) */
object ReflectionsSampleData {
    fun page(today: String): ReflectionFeedPage {
        val d = { n: Int -> KST.addDays(today, n) }
        val json = """
        {"from": "${d(-29)}", "to": "$today", "hasMore": true, "entries": [
          {"kind": "CHECK_IN", "date": "$today", "goalCategoryId": 1, "goalName": "건강", "goalEmoji": "🏃", "colorCode": "12B76A",
           "goalMode": "ACHIEVEMENT", "mood": "OK", "note": "점심 마라탕, 저녁은 샐러드", "value": 64.0, "metricName": "체중", "unit": "kg"},
          {"kind": "RETRO", "date": "$today", "goalCategoryId": 2, "goalName": "커리어", "goalEmoji": "💼", "colorCode": "F59E0B",
           "goalMode": "RECORD", "retrospectiveId": 1, "reviewType": "DAILY", "note": "회의가 길어져서 초안을 못 썼다. 내일 오전에 먼저 하기."},
          {"kind": "CHECK_IN", "date": "${d(-1)}", "goalCategoryId": 3, "goalName": "토익 900", "goalEmoji": "📚", "colorCode": "3D5AFE",
           "goalMode": "ACHIEVEMENT", "mood": "GOOD", "note": "LC 파트 2 풀고 오답 정리까지", "value": 835, "metricName": "모의고사 점수", "unit": "점"},
          {"kind": "CHECK_IN", "date": "${d(-1)}", "goalCategoryId": 1, "goalName": "건강", "goalEmoji": "🏃", "colorCode": "12B76A",
           "goalMode": "ACHIEVEMENT", "value": 64.4, "metricName": "체중", "unit": "kg"},
          {"kind": "CHECK_IN", "date": "${d(-3)}", "goalCategoryId": 4, "goalName": "독서", "goalEmoji": "📖", "colorCode": "8B5CF6",
           "goalMode": "ACHIEVEMENT", "mood": "BAD", "note": "야근이라 못 읽음"}
        ]}
        """
        return NJson.decodeFromString(ReflectionFeedPage.serializer(), json)
    }
}

@Preview(showBackground = true, heightDp = 1000)
@Composable
private fun ReflectionsScreenPreview() {
    NaleulTheme(dark = false) {
        ReflectionsScreen(previewVM = ReflectionsViewModel.preview())
    }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun ReflectionsScreenEmptyPreview() {
    NaleulTheme(dark = false) {
        ReflectionsScreen(previewVM = ReflectionsViewModel.preview(empty = true))
    }
}
