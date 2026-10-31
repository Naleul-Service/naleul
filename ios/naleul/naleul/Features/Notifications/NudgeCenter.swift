// Features/Notifications/NudgeCenter.swift
//
// 알림 예약 · 알림함 저장 · 알림을 눌렀을 때 이동을 한 곳에서 해요.
//
//   NudgeCenter.shared.refresh()        // 앱이 앞으로 올 때 (RootView) — 받은 알림 모으기 + 다시 예약
//   NudgeCenter.shared.handleTap(...)   // 알림을 눌렀을 때 (AppDelegate)
//   NudgeCenter.shared.entries          // 알림함 목록 (최신순)
//
// 알림함은 이 기기에만 저장돼요 (UserDefaults). 서버 푸시도 iOS 알림 센터에 남아 있는 것을 모아서 같이 보여줘요.
// 예약한 로컬 알림은 울릴 시각이 지나면 알림함으로 옮겨요 (알림 센터에서 지웠어도 알림함에는 남아요).

import Foundation
import Observation
import UserNotifications

@MainActor
@Observable
final class NudgeCenter {
    static let shared = NudgeCenter()

    /// 알림함 (최신순)
    private(set) var entries: [NudgeEntry] = []
    /// 아직 안 울린 예약 (설정 화면에서 "다음 알림" 보여주기용)
    private(set) var planned: [NudgeEntry] = []
    /// 알림 권한
    private(set) var authorization: UNAuthorizationStatus = .notDetermined

    var unreadCount: Int { entries.filter { !$0.read }.count }
    /// 알림이 실제로 울릴 수 있는지 (임시 허용 포함)
    var isAuthorized: Bool { [.authorized, .provisional, .ephemeral].contains(authorization) }

    private let inboxKey = "nudge.inbox.v1"
    private let plannedKey = "nudge.planned.v1"
    private let maxEntries = 200
    private let keepDays = 60
    private var refreshing = false
    private var lastRefresh: Date?
    private let isPreview: Bool

    private init() {
        isPreview = false
        // 예전 버전에서 모아 둔 Task 시작 알림은 목록에서 빼요
        entries = load(inboxKey).filter { !$0.isHiddenFromInbox }
        planned = load(plannedKey)
    }

    /// 미리보기 · 테스트용 (저장 · 예약 안 해요)
    init(preview entries: [NudgeEntry], planned: [NudgeEntry] = []) {
        isPreview = true
        self.entries = entries
        self.planned = planned
    }

    // MARK: - 앱이 앞으로 올 때

    /// 받은 알림 모으기 + 앞으로 사흘치 다시 예약. 30초 안에 또 부르면 건너뛰어요
    /// trigger: app_foreground · login · inbox_open · pull_refresh · settings_toggle (nudge.schedule_result 의 refresh_trigger)
    func refresh(force: Bool = false, trigger: String = "app_foreground") async {
        guard !isPreview else { return }
        // 권한은 30초 제한과 상관없이 매번 다시 읽어요
        // (설정 앱에서 알림을 켜고 금방 돌아와도 '알림이 꺼져 있어요'가 남아 있지 않게)
        await updateAuthorization()
        guard !refreshing else { return }
        if !force, let l = lastRefresh, Date().timeIntervalSince(l) < 30 { return }
        refreshing = true
        defer { refreshing = false; lastRefresh = Date() }

        collectDue()
        await harvestDelivered()

        var props: EventProps = [
            "refresh_trigger": trigger,
            "enabled_group_count": NudgeGroup.enabledCount,
            "push_authorized": isAuthorized,
        ]
        // 둘러보기 · 로그아웃이면 예약하지 않아요
        guard TokenManager.shared.accessToken != nil, !TokenManager.shared.isGuest else {
            await clearPending()
            props["result"] = "skipped_guest"
            props["scheduled_count"] = 0
            Analytics.track(NotiEvent.scheduleResult, props)
            return
        }
        let snap = await makeSnapshot()
        guard let snapshot = snap.snapshot else {
            // 계산에 필요한 일정 · 목표를 못 받았어요 — 지난 예약은 그대로 둬요
            props["result"] = "fail"
            props["fail_reason"] = snap.failReason
            props["scheduled_count"] = planned.count
            Analytics.track(NotiEvent.scheduleResult, props)
            return
        }
        let list = NudgePlanner.plan(snapshot)
        await schedule(list)
        props["result"] = "success"
        props["scheduled_count"] = planned.count
        props["dropped_count"] = max(0, list.count - planned.count)
        Analytics.track(NotiEvent.scheduleResult, props)
    }

    /// 휴대폰 알림 권한을 다시 읽어요. 지난번과 달라졌으면 nudge.permission_changed,
    /// 알림함 '설정 열기'로 다녀온 직후면 notification_inbox.permission_return
    private func updateAuthorization() async {
        authorization = await UNUserNotificationCenter.current().notificationSettings().authorizationStatus
        guard authorization != .notDetermined else { return }
        let now = isAuthorized
        let key = "nudge.lastAuthorized"
        if let before = UserDefaults.standard.object(forKey: key) as? Bool, before != now {
            Analytics.track(NotiEvent.permissionChanged, ["push_authorized": now, "enabled_group_count": NudgeGroup.enabledCount])
        }
        UserDefaults.standard.set(now, forKey: key)
        if InboxTracker.shared.awaitingSettingsReturn {
            InboxTracker.shared.awaitingSettingsReturn = false
            InboxTracker.shared.system(NotiEvent.permissionReturn, ["push_authorized": now])
        }
    }

    private func makeSnapshot() async -> (snapshot: NudgeSnapshot?, failReason: String?) {
        let today = KST.today()
        let weekStart = KST.startOfWeek(today)
        do {
            async let next = TimetableAPI.timetable(start: today, end: KST.addDays(today, 2))
            async let week = TimetableAPI.timetable(start: weekStart, end: today)
            async let goals = GoalAPI.goals()
            let (n, w, g) = try await (next, week, goals)
            let enabled = Set(NudgeGroup.allCases.filter { $0.isEnabled })
            return (NudgeSnapshot(now: Date(), days: n.days, week: w.days, goals: g, enabled: enabled), nil)
        } catch {
            #if DEBUG
            print("⚠️ 실천 도우미 알림 계산 실패:", error)
            #endif
            return (nil, error is URLError ? "network" : "api")
        }
    }

    // MARK: - 예약

    private func schedule(_ list: [NudgeEntry]) async {
        let center = UNUserNotificationCenter.current()
        await clearPending()
        // iOS 는 한 앱에 예약 64개까지 — 넉넉히 50개까지만
        for e in list.prefix(50) {
            let content = UNMutableNotificationContent()
            content.title = e.title
            content.body = e.body
            content.sound = .default
            content.threadIdentifier = "nudge.\(e.kind.rawValue)"
            content.userInfo = ["kind": e.kind.rawValue, "route": e.route ?? "", "nudge": true]
            let comps = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: e.date)
            let trigger = UNCalendarNotificationTrigger(dateMatching: comps, repeats: false)
            try? await center.add(UNNotificationRequest(identifier: e.id, content: content, trigger: trigger))
        }
        planned = Array(list.prefix(50))
        save(planned, plannedKey)
    }

    /// 예약해 둔 실천 도우미 알림만 지워요 (서버 푸시 · 다른 알림은 그대로)
    private func clearPending() async {
        let center = UNUserNotificationCenter.current()
        let ids = await center.pendingNotificationRequests().map(\.identifier).filter { $0.hasPrefix("nudge.") }
        center.removePendingNotificationRequests(withIdentifiers: ids)
        planned = []
        save(planned, plannedKey)
    }

    /// 설정에서 묶음을 켜고 끄면 바로 다시 예약
    func setEnabled(_ group: NudgeGroup, _ on: Bool) {
        group.isEnabled = on
        Task { await refresh(force: true, trigger: "settings_toggle") }
    }

    /// 로그아웃 — 예약 · 알림함을 비워요 (다른 계정에 앞 사람 알림이 보이지 않게)
    func reset() {
        entries = []
        save(entries, inboxKey)
        Task { await clearPending() }
    }

    // MARK: - 알림함에 모으기

    /// 울릴 시각이 지난 예약 → 알림함
    private func collectDue() {
        let now = Date()
        let due = planned.filter { $0.date <= now }
        guard !due.isEmpty else { return }
        for e in due {
            // 울릴 시각이 지났다 = 휴대폰에 떴다 (권한이 꺼져 있었으면 실제로는 안 보였을 수 있어요)
            Analytics.track(NotiEvent.delivered, [
                "notification_kind": e.analyticsKind,
                "nudge_group": e.kind.group?.rawValue,
                "minutes_since_sent": e.minutesSinceSent,
            ])
            insert(e)
        }
        planned.removeAll { $0.date <= now }
        save(planned, plannedKey)
    }

    /// iOS 알림 센터에 남아 있는 알림(서버 푸시 포함)을 알림함으로
    private func harvestDelivered() async {
        let delivered = await UNUserNotificationCenter.current().deliveredNotifications()
        for n in delivered { insert(Self.entry(from: n)) }
    }

    /// 앱을 보고 있을 때 온 알림 (AppDelegate willPresent)
    func record(_ e: NudgeEntry) {
        guard !isPreview else { return }
        insert(e)
    }

    private func insert(_ e: NudgeEntry) {
        if e.isHiddenFromInbox { return }
        if entries.contains(where: { $0.id == e.id }) { return }
        entries.append(e)
        trimAndSave()
    }

    nonisolated static func entry(from n: UNNotification) -> NudgeEntry {
        let c = n.request.content
        let type = c.userInfo["type"] as? String
        // 서버 루틴 실천 알림 — data 의 kind 는 YESTERDAY_TIME · RATE_RESCHEDULE (로컬 NudgeKind 가 아니에요)
        if type == "ROUTINE_NUDGE" {
            return routineEntry(id: n.request.identifier, title: c.title, body: c.body, date: n.date, info: c.userInfo)
        }
        let kind = NudgeKind(rawValue: c.userInfo["kind"] as? String ?? "") ?? .server
        let route = c.userInfo["route"] as? String
        return NudgeEntry(id: n.request.identifier, kind: kind,
                          title: c.title.isEmpty ? "나를" : c.title, body: c.body,
                          date: n.date, route: (route?.isEmpty ?? true) ? nil : route,
                          serverType: (type?.isEmpty ?? true) ? nil : type)
    }

    /// ROUTINE_NUDGE → 알림함 한 건. 어제 한 시간 알림은 그날 캘린더로, 실천률 알림은 그 Task 상세까지 열어요
    nonisolated static func routineEntry(id: String, title: String, body: String, date: Date,
                                         info: [AnyHashable: Any]) -> NudgeEntry {
        let rk = info["kind"] as? String
        let rawDate = info["date"] as? String ?? ""
        let day = KST.isYmd(rawDate) ? rawDate : KST.today()
        let taskId = (info["taskId"] as? String).flatMap(Int.init) ?? (info["taskId"] as? Int)
        let route: NudgeRoute
        if rk == "RATE_RESCHEDULE", let taskId { route = .calendarTask(date: day, taskId: taskId) }
        else { route = .calendar(date: day) }
        return NudgeEntry(id: id, kind: .routineNudge, title: title.isEmpty ? "나를" : title, body: body,
                          date: date, route: route.raw, serverType: "ROUTINE_NUDGE", routineKind: rk)
    }

    // MARK: - 읽음 · 지우기

    func markRead(_ id: String) {
        guard let i = entries.firstIndex(where: { $0.id == id }), !entries[i].read else { return }
        entries[i].read = true
        trimAndSave()
    }

    /// 모두 읽음 — 읽음으로 바꾼 개수를 돌려줘요
    @discardableResult
    func markAllRead() -> Int {
        let n = unreadCount
        guard n > 0 else { return 0 }
        for i in entries.indices { entries[i].read = true }
        trimAndSave()
        return n
    }

    func delete(_ id: String) {
        entries.removeAll { $0.id == id }
        trimAndSave()
        UNUserNotificationCenter.current().removeDeliveredNotifications(withIdentifiers: [id])
    }

    func deleteAll() {
        let ids = entries.map(\.id)
        entries = []
        trimAndSave()
        UNUserNotificationCenter.current().removeDeliveredNotifications(withIdentifiers: ids)
    }

    // MARK: - 눌렀을 때 이동

    /// 알림 배너를 눌렀을 때 — 알림함에 넣고 읽음 처리 후 이동
    func handleTap(_ e: NudgeEntry) {
        insert(e)
        markRead(e.id)
        open(e)
    }

    /// 알림함에서 눌렀을 때도 같은 이동
    func open(_ e: NudgeEntry) {
        markRead(e.id)
        let router = AppRouter.shared
        switch e.nudgeRoute {
        case .home?: router.openHome()
        case .homeRetro?: router.openHome(retro: true)
        case .calendar(let d)?: router.openCalendar(view: .day, date: d)
        case .calendarTask(let d, let id)?: router.openCalendar(view: .day, date: d, taskId: id)
        case .taskAdd?: router.openTaskAdd(mode: .manual)
        case .goal(let id)?: router.openGoal(id)
        case .reflections?:
            RF.pendingEntry = "nudge"
            router.openReflections()
        case .patterns?:
            PT.pendingEntry = "nudge"
            router.openPatterns()
        case .calendarAi(let p)?: router.openCalendarAi(p.isEmpty ? nil : p)
        case nil:
            // 서버 푸시는 대부분 일정 알림이라 오늘 캘린더로 (예전과 같아요)
            router.openCalendar(view: .day, date: KST.today())
        }
    }

    // MARK: - 저장

    private func trimAndSave() {
        let cutoff = Date().addingTimeInterval(-Double(keepDays) * 86_400)
        entries = entries.filter { $0.date >= cutoff }.sorted { $0.date > $1.date }
        if entries.count > maxEntries { entries = Array(entries.prefix(maxEntries)) }
        save(entries, inboxKey)
    }

    private func load(_ key: String) -> [NudgeEntry] {
        guard let data = UserDefaults.standard.data(forKey: key) else { return [] }
        return (try? JSONDecoder().decode([NudgeEntry].self, from: data)) ?? []
    }

    private func save(_ list: [NudgeEntry], _ key: String) {
        guard !isPreview else { return }
        UserDefaults.standard.set(try? JSONEncoder().encode(list), forKey: key)
    }
}
