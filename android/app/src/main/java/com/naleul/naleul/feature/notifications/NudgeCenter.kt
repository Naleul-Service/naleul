// feature/notifications/NudgeCenter.kt
//
// iOS Features/Notifications/NudgeCenter.swift
// 알림 예약 · 알림함 저장 · 알림을 눌렀을 때 이동을 한 곳에서 해요.
//
//   NudgeCenter.refresh()              // 앱이 앞으로 올 때 — 받은 알림 모으기 + 다시 예약 (suspend)
//   NudgeCenter.handleIntent(intent)   // 알림을 눌러 앱(MainActivity)이 열렸을 때
//   NudgeCenter.handleTap(entry)       // 알림을 눌렀을 때 (handleIntent 가 불러요)
//   NudgeCenter.entries                // 알림함 목록 (최신순)
//
// 알림함은 이 기기에만 저장돼요 (SharedPreferences, iOS UserDefaults 와 같은 키 "nudge.inbox.v1").
// 서버 푸시도 상단 알림 영역에 남아 있는 것을 모아서 같이 보여줘요.
// 예약한 로컬 알림은 울릴 시각이 지나면 알림함으로 옮겨요 (알림 영역에서 지웠어도 알림함에는 남아요).
//
// Android 에서 달라지는 것
//  - iOS UNCalendarNotificationTrigger(정해진 시각에 울리기) → AlarmManager.setAndAllowWhileIdle 로 시각을 예약하고,
//    그 시각에 NudgeAlarmReceiver 가 깨어나서 알림을 띄워요. "정확한 시각" 권한이 필요 없는 방식이라
//    배터리 절약 중에는 몇 분 늦게 울릴 수 있어요 (실천 도우미는 몇 분 늦어도 괜찮아요).
//  - 어떤 알람을 예약했는지는 iOS 처럼 "nudge.planned.v1" 에 적어 두고, 다시 예약할 때 그 목록으로 지워요.
//  - 휴대폰을 껐다 켜면 Android 는 예약한 알람을 잊어요 → 다음에 앱을 열 때(refresh) 다시 예약돼요.

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

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.naleul.naleul.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer

/** 알림 권한 상태 (iOS UNAuthorizationStatus 중 쓰는 것만) */
enum class NudgeAuthorization {
    notDetermined, denied, authorized;

    companion object {}
}

object NudgeCenter {
    private const val TAG = "NudgeCenter"
    private const val inboxKey = "nudge.inbox.v1"
    private const val plannedKey = "nudge.planned.v1"
    private const val maxEntries = 200
    private const val keepDays = 60

    /** 알람 · 알림 Intent 를 구분하는 action */
    const val ACTION_ALARM = "com.naleul.naleul.NUDGE_ALARM"

    /** 알림함 (최신순) */
    var entries: List<NudgeEntry> by mutableStateOf(emptyList())
        private set

    /** 아직 안 울린 예약 (설정 화면에서 "다음 알림" 보여주기용) */
    var planned: List<NudgeEntry> by mutableStateOf(emptyList())
        private set

    /** 알림 권한 */
    var authorization: NudgeAuthorization by mutableStateOf(NudgeAuthorization.notDetermined)
        private set

    val unreadCount: Int get() = entries.count { !it.read }
    /** 알림이 실제로 울릴 수 있는지 */
    val isAuthorized: Boolean get() = authorization == NudgeAuthorization.authorized

    private var refreshing = false
    private var lastRefresh: Long? = null
    private val mainScope = MainScope()
    /** 저장할 때 쓰는 JSON 변환기 (init 보다 먼저 만들어야 해요 — object 는 위에서부터 차례로 초기화돼요) */
    private val listSerializer = ListSerializer(NudgeEntry.serializer())

    init {
        // 앱 Context 가 없으면(미리보기) 빈 알림함으로 시작해요
        runCatching {
            // 예전 버전에서 모아 둔 Task 시작 알림은 목록에서 빼요
            entries = load(inboxKey).filter { !it.isHiddenFromInbox }
            planned = load(plannedKey)
        }
    }

    private fun context(): Context? = runCatching { AppContext.app as Context }.getOrNull()

    // MARK: - 앱이 앞으로 올 때

    /**
     * 받은 알림 모으기 + 앞으로 사흘치 다시 예약. 30초 안에 또 부르면 건너뛰어요 (메인 스레드에서 불러요)
     * trigger: app_foreground · login · inbox_open · pull_refresh · settings_toggle (nudge.schedule_result 의 refresh_trigger)
     */
    suspend fun refresh(force: Boolean = false, trigger: String = "app_foreground") {
        val ctx = context() ?: return
        // 권한은 30초 제한과 상관없이 매번 다시 읽어요
        // (설정에서 알림을 켜고 금방 돌아와도 '알림이 꺼져 있어요'가 남아 있지 않게)
        updateAuthorization(ctx)
        if (refreshing) return
        val l = lastRefresh
        if (!force && l != null && System.currentTimeMillis() - l < 30_000L) return
        refreshing = true
        try {
            collectDue()
            harvestDelivered(ctx)

            val base = arrayOf<Pair<String, Any?>>(
                "refresh_trigger" to trigger,
                "enabled_group_count" to NudgeGroup.enabledCount,
                "push_authorized" to isAuthorized,
            )
            // 둘러보기 · 로그아웃이면 예약하지 않아요
            if (TokenManager.accessToken == null || TokenManager.isGuest) {
                clearPending(ctx)
                AnalyticsService.track(NotiEvent.scheduleResult, *base, "result" to "skipped_guest", "scheduled_count" to 0)
                return
            }
            val (snapshot, failReason) = makeSnapshot()
            if (snapshot == null) {
                // 계산에 필요한 일정 · 목표를 못 받았어요 — 지난 예약은 그대로 둬요
                AnalyticsService.track(
                    NotiEvent.scheduleResult, *base,
                    "result" to "fail", "fail_reason" to failReason, "scheduled_count" to planned.size,
                )
                return
            }
            val list = NudgePlanner.plan(snapshot)
            schedule(ctx, list)
            AnalyticsService.track(
                NotiEvent.scheduleResult, *base,
                "result" to "success", "scheduled_count" to planned.size, "dropped_count" to maxOf(0, list.size - planned.size),
            )
        } finally {
            refreshing = false
            lastRefresh = System.currentTimeMillis()
        }
    }

    /**
     * 휴대폰 알림 권한을 다시 읽어요. 지난번과 달라졌으면 nudge.permission_changed,
     * 알림함 '설정 열기'로 다녀온 직후면 notification_inbox.permission_return
     */
    private fun updateAuthorization(ctx: Context) {
        authorization = if (NotificationChannels.canPost(ctx)) NudgeAuthorization.authorized else NudgeAuthorization.denied
        val now = isAuthorized
        val key = "nudge.lastAuthorized"
        runCatching {
            val prefs = NudgePrefs.prefs()
            if (prefs.contains(key) && prefs.getBoolean(key, now) != now) {
                AnalyticsService.track(NotiEvent.permissionChanged, "push_authorized" to now, "enabled_group_count" to NudgeGroup.enabledCount)
            }
            prefs.edit().putBoolean(key, now).apply()
        }
        if (InboxTracker.awaitingSettingsReturn) {
            InboxTracker.awaitingSettingsReturn = false
            InboxTracker.system(NotiEvent.permissionReturn, "push_authorized" to now)
        }
    }

    private suspend fun makeSnapshot(): Pair<NudgeSnapshot?, String?> {
        val today = KST.today()
        val weekStart = KST.startOfWeek(today)
        return try {
            coroutineScope {
                val next = async { TimetableAPI.timetable(today, KST.addDays(today, 2)) }
                val week = async { TimetableAPI.timetable(weekStart, today) }
                val goals = async { GoalAPI.goals() }
                val n = next.await()
                val w = week.await()
                val g = goals.await()
                val enabled = NudgeGroup.entries.filter { it.isEnabled }.toSet()
                NudgeSnapshot(now = System.currentTimeMillis(), days = n.days, week = w.days, goals = g, enabled = enabled) to null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "⚠️ 실천 도우미 알림 계산 실패: ${e.message}")
            null to (if (e is java.io.IOException) "network" else "api")
        }
    }

    // MARK: - 예약

    /** 알람을 깨울 PendingIntent (같은 id 면 같은 Intent 로 보여서 다시 만들면 덮어써요) */
    private fun alarmIntent(ctx: Context, e: NudgeEntry, flags: Int): PendingIntent? {
        val intent = Intent(ctx, NudgeAlarmReceiver::class.java).apply {
            action = ACTION_ALARM
            // Intent 는 extras 가 아니라 action · data 로 구분돼서, 알림마다 다른 data 를 붙여요
            data = Uri.parse("naleul-nudge://alarm/" + Uri.encode(e.id))
            putExtras(NotificationChannels.extrasOf(e))
        }
        return PendingIntent.getBroadcast(ctx, e.id.hashCode(), intent, flags or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun schedule(ctx: Context, list: List<NudgeEntry>) {
        clearPending(ctx)
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        // iOS 는 예약 64개까지라 50개로 맞췄어요 (Android 도 앱당 알람 수 제한이 있어서 그대로)
        val chosen = list.take(50)
        for (e in chosen) {
            val pi = alarmIntent(ctx, e, PendingIntent.FLAG_UPDATE_CURRENT) ?: continue
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, e.date, pi)
            } catch (err: Exception) {
                if (BuildConfig.DEBUG) Log.w(TAG, "알람 예약 실패: ${err.message}")
            }
        }
        planned = chosen
        save(planned, plannedKey)
    }

    /** 예약해 둔 실천 도우미 알람만 지워요 (서버 푸시 · 다른 알림은 그대로) */
    private fun clearPending(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        for (e in planned) {
            val pi = alarmIntent(ctx, e, PendingIntent.FLAG_NO_CREATE) ?: continue
            am?.cancel(pi)
            pi.cancel()
        }
        planned = emptyList()
        save(planned, plannedKey)
    }

    /** 설정에서 묶음을 켜고 끄면 바로 다시 예약 */
    fun setEnabled(group: NudgeGroup, on: Boolean) {
        group.isEnabled = on
        mainScope.launch { refresh(force = true, trigger = "settings_toggle") }
    }

    /** 로그아웃 — 예약 · 알림함을 비워요 (다른 계정에 앞 사람 알림이 보이지 않게) */
    fun reset() {
        entries = emptyList()
        save(entries, inboxKey)
        context()?.let { clearPending(it) }
    }

    // MARK: - 알림함에 모으기

    /** 울릴 시각이 지난 예약 → 알림함 */
    private fun collectDue() {
        val now = System.currentTimeMillis()
        val due = planned.filter { it.date <= now }
        if (due.isEmpty()) return
        for (e in due) {
            // 울릴 시각이 지났다 = 휴대폰에 떴다 (권한이 꺼져 있었으면 실제로는 안 보였을 수 있어요)
            AnalyticsService.track(
                NotiEvent.delivered,
                "notification_kind" to e.analyticsKind, "nudge_group" to e.kind.group?.rawValue,
                "minutes_since_sent" to e.minutesSinceSent,
            )
            insert(e)
        }
        planned = planned.filter { it.date > now }
        save(planned, plannedKey)
    }

    /** 상단 알림 영역에 남아 있는 알림(서버 푸시 포함)을 알림함으로 */
    private fun harvestDelivered(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val active = runCatching { nm.activeNotifications.toList() }.getOrDefault(emptyList())
        for (sbn in active) {
            if (sbn.packageName != ctx.packageName) continue
            val ex = sbn.notification.extras ?: continue
            val ours = ex.getString(NotificationChannels.EXTRA_ID)
            if (ours != null) {
                insert(entryFromBundle(ex) ?: continue)
            } else {
                // 앱이 꺼져 있을 때 Firebase 가 대신 띄운 서버 푸시
                val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
                val body = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
                if (title.isEmpty() && body.isEmpty()) continue
                if (similarServerEntry(title, body) != null) continue
                insert(
                    NudgeEntry(
                        id = "push.${sbn.tag ?: ""}.${sbn.id}.${sbn.postTime}",
                        kind = NudgeKind.server,
                        title = title.ifEmpty { "나를" },
                        body = body,
                        date = sbn.postTime,
                    )
                )
            }
        }
    }

    /** 앱을 보고 있을 때 온 알림 · 울린 실천 도우미 (iOS AppDelegate willPresent) — 메인 스레드에서 불러요 */
    fun record(e: NudgeEntry) {
        insert(e)
    }

    private fun insert(e: NudgeEntry) {
        if (e.isHiddenFromInbox) return
        if (entries.any { it.id == e.id }) return
        entries = entries + e
        trimAndSave()
    }

    /** 같은 내용의 서버 푸시가 이미 알림함에 있는지 (Firebase 가 대신 띄운 알림은 id 를 알 수 없어서 내용으로 찾아요) */
    private fun similarServerEntry(title: String, body: String): NudgeEntry? =
        entries.firstOrNull { it.kind == NudgeKind.server && it.title == title.ifEmpty { "나를" } && it.body == body }

    /** 우리가 띄운 알림의 extras → 알림 한 건 */
    fun entryFromBundle(ex: android.os.Bundle): NudgeEntry? {
        val id = ex.getString(NotificationChannels.EXTRA_ID) ?: return null
        val route = ex.getString(NotificationChannels.EXTRA_ROUTE)
        return NudgeEntry(
            id = id,
            kind = NudgeKind.fromRaw(ex.getString(NotificationChannels.EXTRA_KIND)) ?: NudgeKind.server,
            title = ex.getString(NotificationChannels.EXTRA_TITLE).let { if (it.isNullOrEmpty()) "나를" else it },
            body = ex.getString(NotificationChannels.EXTRA_BODY).orEmpty(),
            date = ex.getLong(NotificationChannels.EXTRA_DATE, System.currentTimeMillis()),
            route = if (route.isNullOrEmpty()) null else route,
            serverType = ex.getString(NotificationChannels.EXTRA_TYPE)?.ifEmpty { null },
            routineKind = ex.getString(NotificationChannels.EXTRA_ROUTINE_KIND)?.ifEmpty { null },
        )
    }

    // MARK: - 읽음 · 지우기

    fun markRead(id: String) {
        val e = entries.firstOrNull { it.id == id } ?: return
        if (e.read) return
        entries = entries.map { if (it.id == id) it.copy(read = true) else it }
        trimAndSave()
    }

    /** 모두 읽음 — 읽음으로 바꾼 개수를 돌려줘요 */
    fun markAllRead(): Int {
        val n = entries.count { !it.read }
        if (n == 0) return 0
        entries = entries.map { it.copy(read = true) }
        trimAndSave()
        return n
    }

    fun delete(id: String) {
        entries = entries.filter { it.id != id }
        trimAndSave()
        context()?.let { ctx -> runCatching { androidx.core.app.NotificationManagerCompat.from(ctx).cancel(id, 0) } }
    }

    fun deleteAll() {
        val ids = entries.map { it.id }
        entries = emptyList()
        trimAndSave()
        context()?.let { ctx ->
            val nm = androidx.core.app.NotificationManagerCompat.from(ctx)
            for (id in ids) runCatching { nm.cancel(id, 0) }
        }
    }

    // MARK: - 눌렀을 때 이동

    /**
     * 알림을 눌러 앱이 열렸을 때 (MainActivity onCreate · onNewIntent 에서 불러요).
     *  - 우리가 띄운 알림(실천 도우미 · 앱을 보고 있을 때 온 푸시): extras 에 "nudge.id" 가 있어요
     *  - 앱이 꺼져 있을 때 Firebase 가 띄운 서버 푸시: extras 에 data 값(type, targetId …)과 "google.message_id" 가 있어요
     * 같은 Intent 를 두 번 처리하지 않도록 처리한 뒤 값을 지워요.
     */
    fun handleIntent(intent: Intent) {
        val ex = intent.extras ?: return
        val entry: NudgeEntry
        val type: String
        if (ex.containsKey(NotificationChannels.EXTRA_ID)) {
            entry = entryFromBundle(ex) ?: return
            type = ex.getString(NotificationChannels.EXTRA_TYPE) ?: entry.kind.rawValue
            intent.removeExtra(NotificationChannels.EXTRA_ID)
        } else if (ex.containsKey("google.message_id") || ex.containsKey("type")) {
            type = ex.getString("type") ?: ex.getString("kind") ?: ""
            val title = ex.getString("title") ?: "나를"
            val body = ex.getString("body") ?: ex.getString("message") ?: ""
            val route = ex.getString("route")
            val messageId = ex.getString("google.message_id") ?: "${System.currentTimeMillis()}"
            val sent = runCatching { ex.get("google.sent_time")?.toString()?.toLong() }.getOrNull()
            // 알림 영역에서 이미 모은 같은 알림이 있으면 그것을 써요
            entry = if (type == "ROUTINE_NUDGE") {
                // 루틴 실천 알림 — 앱이 꺼져 있을 때 Firebase 가 띄운 것
                entries.firstOrNull { it.kind == NudgeKind.routineNudge && it.title == title.ifEmpty { "나를" } && it.body == body }
                    ?: NudgeEntry.routine("push.$messageId", title, body, sent ?: System.currentTimeMillis()) { ex.getString(it) }
            } else similarServerEntry(title, body) ?: NudgeEntry(
                id = "push.$messageId",
                kind = NudgeKind.fromRaw(ex.getString("kind")) ?: NudgeKind.server,
                title = title,
                body = body,
                date = sent ?: System.currentTimeMillis(),
                route = if (route.isNullOrEmpty()) null else route,
                serverType = type.ifEmpty { null },
            )
            intent.removeExtra("google.message_id")
            intent.removeExtra("type")
        } else {
            return
        }
        trackPushOpen(entry, type)
        handleTap(entry)
    }

    /** push.open (+ 로그인 전이면 push.open_blocked). 앱을 새로 켠 경우 아직 사용자 복원 전이라 저장된 토큰으로 판단해요 */
    private fun trackPushOpen(entry: NudgeEntry, type: String) {
        val kind = if (entry.kind != NudgeKind.server) entry.analyticsKind else type.ifEmpty { "server" }
        val destination = entry.route?.takeIf { it.isNotEmpty() }?.substringBefore(':') ?: "calendar_today"
        val hasSession = TokenManager.accessToken != null
        // 이어서 오는 app.open 의 open_source
        AnalyticsService.pendingOpenSource = if (entry.kind == NudgeKind.server) "push" else "nudge"
        AnalyticsService.track(
            AnalyticsEvent.pushOpen,
            "notification_kind" to kind,
            "routine_kind" to entry.analyticsRoutineKind,
            "destination" to destination,
            "launch_type" to AnalyticsService.launchTypeNow,
            "is_logged_in" to hasSession,
            "minutes_since_sent" to ((System.currentTimeMillis() - entry.date) / 60_000).toInt().coerceAtLeast(0),
        )
        if (!hasSession || TokenManager.isGuest) {
            AnalyticsService.track(AnalyticsEvent.pushOpenBlocked, "notification_kind" to kind, "destination" to destination)
        }
    }

    /** 알림 배너를 눌렀을 때 — 알림함에 넣고 읽음 처리 후 이동 */
    fun handleTap(e: NudgeEntry) {
        insert(e)
        markRead(e.id)
        open(e)
    }

    /** 알림함에서 눌렀을 때도 같은 이동 */
    fun open(e: NudgeEntry) {
        markRead(e.id)
        when (val r = e.nudgeRoute) {
            is NudgeRoute.home -> AppRouter.openHome()
            is NudgeRoute.homeRetro -> AppRouter.openHome(retro = true)
            is NudgeRoute.calendar -> AppRouter.openCalendar(view = CalendarViewMode.day, date = r.date)
            is NudgeRoute.calendarTask -> AppRouter.openCalendar(view = CalendarViewMode.day, date = r.date, taskId = r.taskId)
            is NudgeRoute.taskAdd -> AppRouter.openTaskAdd(mode = TaskAddMode.manual)
            is NudgeRoute.goal -> AppRouter.openGoal(r.id)
            is NudgeRoute.reflections -> {
                RF.pendingEntry = "nudge"
                AppRouter.openReflections()
            }
            is NudgeRoute.patterns -> {
                PT.pendingEntry = "nudge"
                AppRouter.openPatterns()
            }
            is NudgeRoute.calendarAi -> AppRouter.openCalendarAi(prompt = r.prompt.ifEmpty { null })
            // 서버 푸시는 대부분 일정 알림이라 오늘 캘린더로 (예전과 같아요)
            null -> AppRouter.openCalendar(view = CalendarViewMode.day, date = KST.today())
        }
    }

    // MARK: - 저장

    private fun trimAndSave() {
        val cutoff = System.currentTimeMillis() - keepDays.toLong() * 86_400_000L
        var list = entries.filter { it.date >= cutoff }.sortedByDescending { it.date }
        if (list.size > maxEntries) list = list.take(maxEntries)
        entries = list
        save(entries, inboxKey)
    }

    private fun load(key: String): List<NudgeEntry> {
        val text = runCatching { NudgePrefs.prefs().getString(key, null) }.getOrNull() ?: return emptyList()
        // 알림함 LazyColumn 키(entry-id)가 겹치면 죽어서, 혹시 저장된 목록에 같은 id 가 있으면 하나만 남겨요
        return runCatching { NJson.decodeFromString(listSerializer, text).distinctBy { it.id } }.getOrDefault(emptyList())
    }

    private fun save(list: List<NudgeEntry>, key: String) {
        runCatching {
            NudgePrefs.prefs().edit().putString(key, NJson.encodeToString(listSerializer, list)).apply()
        }
    }
}
