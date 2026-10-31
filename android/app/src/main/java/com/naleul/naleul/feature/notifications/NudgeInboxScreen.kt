// feature/notifications/NudgeInboxScreen.kt
//
// iOS Features/Notifications/NudgeInboxScreen.swift
// 알림함 — 실천 도우미 알림과 서버에서 온 푸시를 한곳에 모아 봐요 (홈 오른쪽 위 종 → 이 화면).
//  - 오늘 · 어제 · 이번 주 · 이전으로 묶어요
//  - 누르면 알림에 적힌 곳으로 이동 (하루 마무리 → 오늘 회고, 루틴 실천 → 그날 캘린더 …)
//  - 길게 눌러 지우기 · 읽음 (iOS 는 왼쪽으로 밀기 — Android 에서는 길게 누르는 메뉴가 더 익숙해요), 위의 "모두 읽음"
//  - 맨 위에 "다음 알림"을 보여줘서 언제 무슨 말을 걸지 미리 알 수 있어요

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.naleul.naleul.feature.notifications

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

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import java.util.Date

// 전체 · 읽지 않음 · 실천 도우미 필터는 뺐어요 (Task 시작 알림이 목록에서 빠져서 목록이 짧아짐)
// 들어오면 새 알림은 "안 읽음"으로 보이고, 화면을 떠날 때 지금까지 온 알림을 모두 읽음 처리해요

/** 날짜 묶음 (오늘 · 어제 · 이번 주 · 이전) */
private data class NudgeInboxSection(val title: String, val items: List<NudgeEntry>)

@Composable
fun NudgeInboxScreen() {
    TrackScreen("NudgeInboxScreen")
    // 화면이 열릴 때 받은 알림을 모으고 다시 예약해요 (30초 안이면 건너뛰어요)
    LaunchedEffect(Unit) { NudgeCenter.refresh(trigger = "inbox_open") }

    // 들어옴 · 나감 — 다른 탭에 갔다 돌아오면 pageview 는 다시 안 보내고 시간만 다시 재요
    var didEnter by rememberSaveable { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        InboxTracker.enter(fresh = !didEnter)
        didEnter = true
        var stopped = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> { stopped = true; InboxTracker.exit("background") }
                Lifecycle.Event.ON_START -> if (stopped) { stopped = false; InboxTracker.enter(fresh = false) }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // 나갈 때 지금까지 온 알림을 모두 읽음으로 (보는 동안에는 안 읽은 알림이 그대로 표시돼요)
            val autoRead = NudgeCenter.markAllRead()
            InboxTracker.exit(autoReadCount = autoRead)
        }
    }

    NudgeInboxContent(
        entries = NudgeCenter.entries,
        planned = NudgeCenter.planned,
        authorization = NudgeCenter.authorization,
        onRefresh = {
            val before = NudgeCenter.entries.size
            NudgeCenter.refresh(force = true, trigger = "pull_refresh")
            InboxTracker.track(
                NotiEvent.pullRefresh,
                "added_count" to maxOf(0, NudgeCenter.entries.size - before), "unread_count" to NudgeCenter.unreadCount,
            )
        },
        onOpen = { NudgeCenter.open(it) },
        onMarkRead = { NudgeCenter.markRead(it) },
        onDelete = { NudgeCenter.delete(it) },
        onMarkAllRead = { NudgeCenter.markAllRead() },
        onDeleteAll = { NudgeCenter.deleteAll() },
    )
}

/** 화면 내용 (NudgeCenter 와 떼어 놔서 미리보기에서 샘플로 그릴 수 있어요 — iOS init(center:)) */
@Composable
private fun NudgeInboxContent(
    entries: List<NudgeEntry>,
    planned: List<NudgeEntry>,
    authorization: NudgeAuthorization,
    onRefresh: suspend () -> Unit,
    onOpen: (NudgeEntry) -> Unit,
    onMarkRead: (String) -> Unit,
    onDelete: (String) -> Unit,
    onMarkAllRead: () -> Unit,
    onDeleteAll: () -> Unit,
) {
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val unreadCount = entries.count { !it.read }

    val shown = entries
    val groups = inboxGroups(shown)

    PushedScreen(
        title = "알림",
        actions = {
            NMenu(
                items = listOf(
                    MenuItem(title = "모두 읽음", icon = "checkmark.circle", enabled = unreadCount > 0) {
                        InboxTracker.track(NotiEvent.markAllReadClick, "unread_count" to unreadCount)
                        onMarkAllRead()
                    },
                    MenuItem(title = "알림 설정", icon = "gearshape") {
                        InboxTracker.track(
                            NotiEvent.settingsClick,
                            "enabled_group_count" to NudgeGroup.enabledCount,
                            "push_authorized" to (authorization == NudgeAuthorization.authorized),
                        )
                        InboxTracker.markNavigating()
                        AppRouter.openSettings(SettingsRoute.Notifications)
                    },
                    MenuItem(title = "모두 지우기", icon = "trash", destructive = true, enabled = entries.isNotEmpty()) {
                        InboxTracker.track(NotiEvent.deleteAllClick, "total_count" to entries.size, "unread_count" to unreadCount)
                        onDeleteAll()
                    },
                ),
                modifier = Modifier.semantics { contentDescription = "알림함 메뉴" },
            ) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    SfImage("ellipsis.circle", size = 20.dp, tint = NC.ink2)
                }
            }
        },
    ) {
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                scope.launch {
                    refreshing = true
                    try { onRefresh() } finally { refreshing = false }
                }
            },
            modifier = Modifier.fillMaxSize().canvasBackground(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "header") {
                    InboxHeader(
                        authorization = authorization,
                        next = planned.firstOrNull(),
                    )
                }
                if (shown.isEmpty()) {
                    item(key = "empty") {
                        LaunchedEffect(Unit) { InboxTracker.emptyOnce() }
                        InboxEmpty()
                    }
                } else {
                    for (g in groups) {
                        item(key = "section-${g.title}") {
                            Text(
                                g.title,
                                style = pt(13, PTWeight.semibold),
                                color = NC.ink3,
                                modifier = Modifier.padding(top = 8.dp, bottom = 0.dp),
                            )
                        }
                        for (e in g.items) {
                            item(key = "entry-${e.id}") {
                                InboxRowWithMenu(
                                    entry = e,
                                    onOpen = {
                                        InboxTracker.track(
                                            NotiEvent.itemClick,
                                            "notification_kind" to e.analyticsKind,
                                            "routine_kind" to e.analyticsRoutineKind,
                                            "nudge_group" to e.kind.group?.rawValue,
                                            "destination" to e.analyticsDestination,
                                            "is_read" to e.read,
                                            "section" to inboxSectionKey(g.title),
                                            "list_position" to shown.indexOfFirst { it.id == e.id } + 1,
                                            "minutes_since_sent" to e.minutesSinceSent,
                                        )
                                        InboxTracker.markNavigating()
                                        onOpen(e)
                                    },
                                    onMarkRead = {
                                        InboxTracker.track(NotiEvent.itemMarkRead, "notification_kind" to e.analyticsKind, "action_method" to "long_press")
                                        onMarkRead(e.id)
                                    },
                                    onDelete = {
                                        InboxTracker.track(
                                            NotiEvent.itemDelete,
                                            "notification_kind" to e.analyticsKind, "is_read" to e.read,
                                            "action_method" to "long_press", "section" to inboxSectionKey(g.title),
                                        )
                                        onDelete(e.id)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 한 줄 + 길게 누르면 뜨는 메뉴 (iOS swipeActions: 지우기 · 읽음) */
@Composable
private fun InboxRowWithMenu(entry: NudgeEntry, onOpen: () -> Unit, onMarkRead: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        NudgeInboxRow(
            entry = entry,
            modifier = Modifier
                .clip(RoundedCornerShape(Radius.xxl))
                .combinedClickable(onClick = onOpen, onLongClick = {
                    menu = true
                    // [UX] 길게 누르면 메뉴가 있다는 걸 알고 쓰는지 (메뉴만 열고 닫는지)
                    InboxTracker.track(NotiEvent.itemMenuOpen, "notification_kind" to entry.analyticsKind, "is_read" to entry.read)
                }),
        )
        NDropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = NC.surface) {
            if (!entry.read) {
                NDropdownMenuItem(
                    text = { Text("읽음", style = pt(15), color = NC.ink) },
                    leadingIcon = { SfImage("envelope.open", size = 16.dp, tint = NC.textBrand) },
                    onClick = { menu = false; onMarkRead() },
                )
            }
            NDropdownMenuItem(
                text = { Text("지우기", style = pt(15), color = NC.danger) },
                leadingIcon = { SfImage("trash", size = 16.dp, tint = NC.danger) },
                onClick = { menu = false; onDelete() },
            )
        }
    }
}

// MARK: 맨 위 (권한 · 다음 알림)

@Composable
private fun InboxHeader(
    authorization: NudgeAuthorization,
    next: NudgeEntry?,
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 4.dp)) {
        if (authorization == NudgeAuthorization.denied) {
            LaunchedEffect(Unit) { InboxTracker.bannerOnce() }
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(NC.warningSoft, RoundedCornerShape(Radius.xxl))
                    .padding(14.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SfImage("bell.slash", size = 16.dp, tint = NC.warningInk)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("알림이 꺼져 있어요", style = pt(14, PTWeight.semibold), color = NC.ink)
                    Text(
                        "휴대폰 설정에서 나를 앱 알림을 켜야 실천 도우미가 말을 걸 수 있어요.",
                        style = pt(13), color = NC.ink2,
                    )
                    Text(
                        "설정 열기",
                        style = pt(13, PTWeight.semibold),
                        color = NC.textBrand,
                        modifier = Modifier.clickable {
                            InboxTracker.track(NotiEvent.openSettingsClick, "planned_count" to NudgeCenter.planned.size)
                            InboxTracker.awaitingSettingsReturn = true
                            // iOS UIApplication.openSettingsURLString → 이 앱의 알림 설정 화면
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { context.startActivity(intent) }
                        },
                    )
                }
            }
        }
        if (next != null) {
            NudgeNextCard(
                entry = next,
                // [UX] 눌러도 아무 일도 없어요 — 얼마나 누르는지 보고 이동(설정 · 미리 보기)을 붙일지 정해요
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                ) {
                    InboxTracker.track(
                        NotiEvent.nextCardClick,
                        "notification_kind" to next.analyticsKind, "nudge_group" to next.kind.group?.rawValue,
                        "dead_target" to "next_card",
                    )
                },
            )
        }
    }
}

@Composable
private fun InboxEmpty() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SfImage("bell", size = 30.dp, tint = NC.ink4)
        Text(
            "아직 받은 알림이 없어요",
            style = pt(15, PTWeight.semibold), color = NC.ink2,
        )
        Text(
            "하루가 끝나면 돌아보기를, 루틴을 놓친 순간엔 실천률과 함께 알려드릴게요.",
            style = pt(13), color = NC.ink3, textAlign = TextAlign.Center,
        )
    }
}

// MARK: 날짜 묶음

private fun inboxGroups(shown: List<NudgeEntry>): List<NudgeInboxSection> {
    val today = KST.today()
    val weekStart = KST.startOfWeek(today)
    val titles = listOf("오늘", "어제", "이번 주", "이전")
    val buckets = List(4) { mutableListOf<NudgeEntry>() }
    for (e in shown) {
        val d = KST.ymd(Date(e.date))
        val i = when {
            d == today -> 0
            d == KST.addDays(today, -1) -> 1
            d >= weekStart -> 2
            else -> 3
        }
        buckets[i].add(e)
    }
    return titles.indices.filter { buckets[it].isNotEmpty() }.map { NudgeInboxSection(titles[it], buckets[it]) }
}

// MARK: - 한 줄

@Composable
fun NudgeInboxRow(entry: NudgeEntry, modifier: Modifier = Modifier) {
    val tint = when (entry.kind.group) {
        NudgeGroup.morning -> NC.warning
        NudgeGroup.evening -> NC.brand
        NudgeGroup.motivation -> NC.danger
        NudgeGroup.report -> NC.success
        NudgeGroup.comeback -> NC.brand
        null -> NC.ink3
    }
    val shape = RoundedCornerShape(Radius.xxl)
    Row(
        modifier
            .fillMaxWidth()
            .background(if (entry.read) NC.surface else NC.brandSoft.copy(alpha = 0.45f), shape)
            .border(1.dp, NC.borderSubtle, shape)
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(36.dp).background(tint.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            SfImage(entry.kind.icon, size = 16.dp, tint = tint)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(entry.kind.label, style = pt(12, PTWeight.medium), color = NC.ink3)
                Text("·", style = pt(12), color = NC.ink4)
                Text(NudgeFormat.time(entry.date), style = pt(12), color = NC.ink3)
                Spacer(Modifier.weight(1f))
                if (!entry.read) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(NC.brand, CircleShape)
                            .semantics { contentDescription = "읽지 않음" }
                    )
                }
            }
            Text(
                entry.title,
                style = pt(15, if (entry.read) PTWeight.medium else PTWeight.bold),
                color = if (entry.read) NC.ink2 else NC.ink,
            )
            if (entry.body.isNotEmpty()) {
                Text(
                    entry.body,
                    style = pt(13).copy(lineHeight = 19.sp),
                    color = NC.ink3,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val r = entry.nudgeRoute
            if (r != null) {
                Row(
                    Modifier.padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(r.actionLabel, style = pt(12, PTWeight.semibold), color = NC.textBrand)
                    SfImage("chevron.right", size = 10.dp, tint = NC.textBrand)
                }
            }
        }
    }
}

/** "다음 알림" — 언제 무슨 말을 걸지 미리 보여줘요 */
@Composable
private fun NudgeNextCard(entry: NudgeEntry, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(NC.subtle, RoundedCornerShape(Radius.xl))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SfImage("clock", size = 14.dp, tint = NC.ink3)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("다음 알림 · ${NudgeFormat.`when`(entry.date)}", style = pt(12, PTWeight.medium), color = NC.ink3)
            Text(entry.title, style = pt(14, PTWeight.semibold), color = NC.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

object NudgeFormat {
    private fun clock(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        val ampm = if (h < 12) "오전" else "오후"
        val h12 = if (h % 12 == 0) 12 else h % 12
        return if (m == 0) "$ampm ${h12}시" else "$ampm $h12:${if (m < 10) "0" else ""}$m"
    }

    /** 알림함 시각 — 오늘이면 "오후 9:10", 아니면 "10월 3일 오후 9:10" (date = 밀리초) */
    fun time(date: Long): String {
        val n = KST.now(Date(date))
        if (n.date == KST.today()) return clock(n.minutes)
        return "${KST.formatMonthDay(n.date)} ${clock(n.minutes)}"
    }

    /** 다음 알림 — "오늘 오후 9:10" · "내일 오전 8:20" · "10월 17일 오후 8시" */
    fun `when`(date: Long): String {
        val n = KST.now(Date(date))
        val today = KST.today()
        val day = when (n.date) {
            today -> "오늘"
            KST.addDays(today, 1) -> "내일"
            else -> KST.formatMonthDay(n.date)
        }
        return "$day ${clock(n.minutes)}"
    }
}

// MARK: - 미리보기

object NudgePreviewData {
    val entries: List<NudgeEntry>
        get() {
            val now = System.currentTimeMillis()
            return listOf(
                NudgeEntry(
                    id = "p1", kind = NudgeKind.dayWrapUp, title = "오늘 일정이 끝났어요 🌙",
                    body = "3분만 오늘을 돌아볼까요? 한 일은 체크하고 못 한 이유는 한 줄로 남겨 봐요. 체크한 기록으로 나의 패턴이 잘 지키는 시간을 찾아요.",
                    date = now - 600_000L, route = NudgeRoute.homeRetro.raw,
                ),
                NudgeEntry(
                    id = "p2", kind = NudgeKind.server, title = "곧 시작해요", body = "10분 뒤 '영어 단어' 시작이에요.",
                    date = now - 3_600_000L, route = null,
                ),
                NudgeEntry(
                    id = "p3", kind = NudgeKind.countdown, title = "'📝 SQLD 합격' 해방까지 3일",
                    body = "끝이 보여요. 남은 사흘은 꼭 해야 할 것만 해도 충분해요.",
                    date = now - 86_400_000L, route = NudgeRoute.goal(2).raw, read = true,
                ),
                NudgeEntry(
                    id = "p4", kind = NudgeKind.why, title = "15일 전의 내가 보낸 말 💌",
                    body = "“여름 전에 10km 를 쉬지 않고 뛰고 싶어서” — '🏃 러닝'을(를) 시작한 이유예요.",
                    date = now - 3 * 86_400_000L, route = NudgeRoute.goal(3).raw, read = true,
                ),
                NudgeEntry(
                    id = "p5", kind = NudgeKind.comeback, title = "완벽하지 않아도 괜찮아요",
                    body = "다시 시작하면 돼요. 오늘은 10분짜리 하나만 해볼까요?",
                    date = now - 9 * 86_400_000L, route = NudgeRoute.calendar(date = KST.today()).raw, read = true,
                ),
            )
        }

    val planned: List<NudgeEntry>
        get() = listOf(
            NudgeEntry(
                id = "n1", kind = NudgeKind.firstStep, title = "오늘은 이것 하나만 먼저 👣",
                body = "09:00 '영어 단어' — 나머지 3개는 이것부터 끝내고 봐요.", date = System.currentTimeMillis() + 5_400_000L,
                route = NudgeRoute.calendar(date = KST.addDays(KST.today(), 1)).raw,
            )
        )
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun NudgeInboxScreenPreview() {
    NaleulTheme(dark = false) {
        NudgeInboxContent(
            entries = NudgePreviewData.entries, planned = NudgePreviewData.planned,
            authorization = NudgeAuthorization.denied,
            onRefresh = {}, onOpen = {}, onMarkRead = {}, onDelete = {}, onMarkAllRead = {}, onDeleteAll = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun NudgeInboxScreenEmptyPreview() {
    NaleulTheme(dark = false) {
        NudgeInboxContent(
            entries = emptyList(), planned = emptyList(), authorization = NudgeAuthorization.authorized,
            onRefresh = {}, onOpen = {}, onMarkRead = {}, onDelete = {}, onMarkAllRead = {}, onDeleteAll = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun NudgeInboxRowPreview() {
    NaleulTheme(dark = false) {
        Column(Modifier.canvasBackground().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (e in NudgePreviewData.entries.take(3)) NudgeInboxRow(entry = e)
        }
    }
}
