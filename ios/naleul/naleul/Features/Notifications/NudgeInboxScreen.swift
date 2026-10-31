// Features/Notifications/NudgeInboxScreen.swift
//
// 알림함 — 실천 도우미 알림과 서버에서 온 푸시를 한곳에 모아 봐요 (홈 오른쪽 위 종 → 이 화면).
//  - 오늘 · 어제 · 이번 주 · 이전으로 묶어요
//  - 누르면 알림에 적힌 곳으로 이동 (하루 마무리 → 오늘 회고, 루틴 실천 → 그날 캘린더 …)
//  - 왼쪽으로 밀어서 지우기, 위의 "모두 읽음"
//  - 들어오면 새로 온 알림은 "안 읽음"으로 보이고, 화면을 떠날 때 지금까지 온 알림을 모두 읽음 처리해요
//  - "'○○' 할 시간이에요"(Task 시작 알림)는 목록에 넣지 않아요 (NudgeEntry.isHiddenFromInbox)
//  - 전체 · 읽지 않음 · 실천 도우미 필터는 뺐어요 (Task 시작 알림이 빠져서 목록이 짧아짐)
//  - 맨 위에 "다음 알림"을 보여줘서 언제 무슨 말을 걸지 미리 알 수 있어요

import SwiftUI
import UIKit
import UserNotifications

struct NudgeInboxScreen: View {
    @State private var center: NudgeCenter
    /// 이 화면이 스택에 들어온 뒤 한 번이라도 보였는지 (다른 탭에 갔다 돌아오면 pageview 를 다시 안 보내요)
    @State private var didEnter = false
    /// 지금 화면에 보이는지 (다른 탭에 가 있는 동안 앱이 뒤로 가도 exit 를 보내지 않게)
    @State private var visible = false
    @Environment(\.openURL) private var openURL
    @Environment(AppRouter.self) private var router
    @Environment(\.scenePhase) private var scenePhase
    private var tracker: InboxTracker { .shared }

    init() { _center = State(initialValue: NudgeCenter.shared) }

    /// 미리보기용
    init(center: NudgeCenter) { _center = State(initialValue: center) }

    private var shown: [NudgeEntry] { center.entries }

    var body: some View {
        List {
            Section {
                header
                    .listRowInsets(EdgeInsets(top: 8, leading: 16, bottom: 4, trailing: 16))
                    .listRowBackground(Color.clear)
                    .listRowSeparator(.hidden)
            }
            if shown.isEmpty {
                empty
                    .listRowBackground(Color.clear)
                    .listRowSeparator(.hidden)
            } else {
                ForEach(groups, id: \.title) { g in
                    Section {
                        ForEach(g.items) { e in
                            NudgeInboxRow(entry: e)
                                .contentShape(Rectangle())
                                .onTapGesture { openItem(e, section: g.title) }
                                .listRowInsets(EdgeInsets(top: 4, leading: 16, bottom: 4, trailing: 16))
                                .listRowBackground(Color.clear)
                                .listRowSeparator(.hidden)
                                .swipeActions(edge: .trailing) {
                                    Button(role: .destructive) {
                                        tracker.track(NotiEvent.itemDelete, [
                                            "notification_kind": e.analyticsKind, "is_read": e.read,
                                            "action_method": "swipe", "section": InboxSectionKey.of(g.title),
                                        ])
                                        center.delete(e.id)
                                    } label: {
                                        Label("지우기", systemImage: "trash")
                                    }
                                    if !e.read {
                                        Button {
                                            tracker.track(NotiEvent.itemMarkRead, ["notification_kind": e.analyticsKind, "action_method": "swipe"])
                                            center.markRead(e.id)
                                        } label: {
                                            Label("읽음", systemImage: "envelope.open")
                                        }
                                        .tint(Color.brand)
                                    }
                                }
                        }
                    } header: {
                        Text(g.title)
                            .font(.pt(13, .semibold))
                            .foregroundStyle(Color.ink3)
                            .textCase(nil)
                    }
                }
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .canvasBackground()
        .navigationTitle("알림")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    Button {
                        tracker.track(NotiEvent.markAllReadClick, ["unread_count": center.unreadCount])
                        center.markAllRead()
                    } label: { Label("모두 읽음", systemImage: "checkmark.circle") }
                        .disabled(center.unreadCount == 0)
                    Button {
                        tracker.track(NotiEvent.settingsClick, [
                            "enabled_group_count": NudgeGroup.enabledCount, "push_authorized": center.isAuthorized,
                        ])
                        tracker.markNavigating()
                        router.openSettings(.notifications)
                    } label: {
                        Label("알림 설정", systemImage: "gearshape")
                    }
                    Divider().overlay(Color.borderSoft)
                    Button(role: .destructive) {
                        tracker.track(NotiEvent.deleteAllClick, ["total_count": center.entries.count, "unread_count": center.unreadCount])
                        center.deleteAll()
                    } label: { Label("모두 지우기", systemImage: "trash") }
                        .disabled(center.entries.isEmpty)
                } label: {
                    Image(systemName: "ellipsis.circle").foregroundStyle(Color.ink2)
                }
                .accessibilityLabel("알림함 메뉴")
            }
        }
        .refreshable {
            let before = center.entries.count
            await center.refresh(force: true, trigger: "pull_refresh")
            tracker.track(NotiEvent.pullRefresh, [
                "added_count": max(0, center.entries.count - before), "unread_count": center.unreadCount,
            ])
        }
        .task { await center.refresh(trigger: "inbox_open") }
        .onAppear {
            tracker.enter(fresh: !didEnter, center: center)
            didEnter = true
            visible = true
            Analytics.setScreen("NudgeInboxScreen")
        }
        .onDisappear {
            visible = false
            // 나갈 때 지금까지 온 알림을 모두 읽음으로 (보는 동안에는 안 읽은 알림이 그대로 표시돼요)
            let autoRead = center.markAllRead()
            tracker.exit(unread: center.unreadCount, autoReadCount: autoRead)
        }
        .onChange(of: scenePhase) { _, phase in
            // 알림함을 보다가 앱이 뒤로 가면 떠난 걸로, 다시 오면 시간만 다시 재요
            guard visible else { return }
            if phase == .background { tracker.exit("background", unread: center.unreadCount) }
            if phase == .active { tracker.enter(fresh: false, center: center) }
        }
    }

    // MARK: 동작

    /// 알림을 눌러 적힌 곳으로 — 앞에서 몇 번째였는지 · 이미 읽었던 건지도 같이
    private func openItem(_ e: NudgeEntry, section: String) {
        tracker.track(NotiEvent.itemClick, [
            "notification_kind": e.analyticsKind,
            "routine_kind": e.analyticsRoutineKind,
            "nudge_group": e.kind.group?.rawValue,
            "destination": e.analyticsDestination,
            "is_read": e.read,
            "section": InboxSectionKey.of(section),
            "list_position": (shown.firstIndex(where: { $0.id == e.id }) ?? 0) + 1,
            "minutes_since_sent": e.minutesSinceSent,
        ])
        tracker.markNavigating()
        center.open(e)
    }

    // MARK: 맨 위 (권한 · 다음 알림)

    @ViewBuilder private var header: some View {
        VStack(alignment: .leading, spacing: 12) {
            if center.authorization == .denied {
                HStack(alignment: .top, spacing: 10) {
                    Image(systemName: "bell.slash").foregroundStyle(Color.warningInk)
                    VStack(alignment: .leading, spacing: 6) {
                        Text("알림이 꺼져 있어요").font(.pt(14, .semibold)).foregroundStyle(Color.ink)
                        Text("iPhone 설정에서 나를 앱 알림을 켜야 실천 도우미가 말을 걸 수 있어요.")
                            .font(.pt(13)).foregroundStyle(Color.ink2)
                            .fixedSize(horizontal: false, vertical: true)
                        Button("설정 열기") {
                            tracker.track(NotiEvent.openSettingsClick, ["planned_count": center.planned.count])
                            tracker.awaitingSettingsReturn = true
                            if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
                        }
                        .font(.pt(13, .semibold))
                        .foregroundStyle(Color.textBrand)
                        .buttonStyle(.plain)
                    }
                }
                .padding(14)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.warningSoft, in: RoundedRectangle(cornerRadius: Radius.xxl))
                .onAppear { tracker.bannerOnce(planned: center.planned.count) }
            }
            if let next = center.planned.first {
                NudgeNextCard(entry: next)
                    .contentShape(Rectangle())
                    // [UX] 눌러도 아무 일도 없어요 — 얼마나 누르는지 보고 이동(설정 · 미리 보기)을 붙일지 정해요
                    .onTapGesture {
                        tracker.track(NotiEvent.nextCardClick, [
                            "notification_kind": next.analyticsKind, "nudge_group": next.kind.group?.rawValue,
                            "dead_target": "next_card",
                        ])
                    }
            }
        }
    }

    private var empty: some View {
        VStack(spacing: 10) {
            NSymbol("bell", size: 30).foregroundStyle(Color.ink4)
            Text("아직 받은 알림이 없어요")
                .font(.pt(15, .semibold)).foregroundStyle(Color.ink2)
            Text("하루가 끝나면 돌아보기를, 루틴을 놓친 순간엔 실천률과 함께 알려드릴게요.")
                .font(.pt(13)).foregroundStyle(Color.ink3)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 48)
        .onAppear { tracker.emptyOnce(total: center.entries.count, authorized: center.isAuthorized) }
    }

    // MARK: 날짜 묶음

    private struct InboxSection {
        let title: String
        let items: [NudgeEntry]
    }

    private var groups: [InboxSection] {
        let today = KST.today()
        let weekStart = KST.startOfWeek(today)
        var buckets: [(String, [NudgeEntry])] = [("오늘", []), ("어제", []), ("이번 주", []), ("이전", [])]
        for e in shown {
            let d = KST.ymd(from: e.date)
            let i = d == today ? 0 : (d == KST.addDays(today, -1) ? 1 : (d >= weekStart ? 2 : 3))
            buckets[i].1.append(e)
        }
        return buckets.filter { !$0.1.isEmpty }.map { InboxSection(title: $0.0, items: $0.1) }
    }
}

// MARK: - 한 줄

struct NudgeInboxRow: View {
    let entry: NudgeEntry

    private var tint: Color {
        switch entry.kind.group {
        case .morning?: return .warning
        case .evening?: return .brand
        case .motivation?: return .danger
        case .report?: return .success
        case .comeback?: return .brand
        case nil: return .ink3
        }
    }

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            NSymbol(entry.kind.icon, size: 15, weight: .semibold)
                .foregroundStyle(tint)
                .frame(width: 36, height: 36)
                .background(tint.opacity(0.12), in: Circle())
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 6) {
                    Text(entry.kind.label).font(.pt(12, .medium)).foregroundStyle(Color.ink3)
                    Text("·").font(.pt(12)).foregroundStyle(Color.ink4)
                    Text(NudgeFormat.time(entry.date)).font(.pt(12)).foregroundStyle(Color.ink3)
                    Spacer(minLength: 0)
                    if !entry.read {
                        Circle().fill(Color.brand).frame(width: 8, height: 8).accessibilityLabel("읽지 않음")
                    }
                }
                Text(entry.title)
                    .font(.pt(15, entry.read ? .medium : .bold))
                    .foregroundStyle(entry.read ? Color.ink2 : Color.ink)
                    .fixedSize(horizontal: false, vertical: true)
                if !entry.body.isEmpty {
                    Text(entry.body)
                        .font(.pt(13))
                        .foregroundStyle(Color.ink3)
                        .lineSpacing(2)
                        .lineLimit(4)
                        .fixedSize(horizontal: false, vertical: true)
                }
                if let r = entry.nudgeRoute {
                    HStack(spacing: 3) {
                        Text(r.actionLabel)
                        NSymbol("chevron.right", size: 10, weight: .semibold)
                    }
                    .font(.pt(12, .semibold))
                    .foregroundStyle(Color.textBrand)
                    .padding(.top, 2)
                }
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(entry.read ? Color.surface : Color.brandSoft.opacity(0.45), in: RoundedRectangle(cornerRadius: Radius.xxl))
        .overlay(RoundedRectangle(cornerRadius: Radius.xxl).stroke(Color.line, lineWidth: 1))
    }
}

/// "다음 알림" — 언제 무슨 말을 걸지 미리 보여줘요
private struct NudgeNextCard: View {
    let entry: NudgeEntry

    var body: some View {
        HStack(spacing: 10) {
            NSymbol("clock", size: 13, weight: .semibold).foregroundStyle(Color.ink3)
            VStack(alignment: .leading, spacing: 2) {
                Text("다음 알림 · \(NudgeFormat.when(entry.date))")
                    .font(.pt(12, .medium)).foregroundStyle(Color.ink3)
                Text(entry.title).font(.pt(14, .semibold)).foregroundStyle(Color.ink).lineLimit(1)
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(Color.subtle, in: RoundedRectangle(cornerRadius: Radius.xl))
    }
}

enum NudgeFormat {
    private static func clock(_ minutes: Int) -> String {
        let h = minutes / 60, m = minutes % 60
        let ampm = h < 12 ? "오전" : "오후"
        let h12 = h % 12 == 0 ? 12 : h % 12
        return m == 0 ? "\(ampm) \(h12)시" : "\(ampm) \(h12):\(m < 10 ? "0" : "")\(m)"
    }

    /// 알림함 시각 — 오늘이면 "오후 9:10", 아니면 "10월 3일 오후 9:10"
    static func time(_ date: Date) -> String {
        let n = KST.now(date)
        if n.date == KST.today() { return clock(n.minutes) }
        return "\(KST.formatMonthDay(n.date)) \(clock(n.minutes))"
    }

    /// 다음 알림 — "오늘 오후 9:10" · "내일 오전 8:20" · "10월 17일 오후 8시"
    static func when(_ date: Date) -> String {
        let n = KST.now(date)
        let today = KST.today()
        let day = n.date == today ? "오늘" : (n.date == KST.addDays(today, 1) ? "내일" : KST.formatMonthDay(n.date))
        return "\(day) \(clock(n.minutes))"
    }
}

// MARK: - 미리보기

enum NudgePreviewData {
    static var entries: [NudgeEntry] {
        let now = Date()
        return [
            NudgeEntry(id: "p1", kind: .dayWrapUp, title: "오늘 일정이 끝났어요 🌙",
                       body: "3분만 오늘을 돌아볼까요? 한 일은 체크하고 못 한 이유는 한 줄로 남겨 봐요. 체크한 기록으로 나의 패턴이 잘 지키는 시간을 찾아요.",
                       date: now.addingTimeInterval(-600), route: NudgeRoute.homeRetro.raw),
            NudgeEntry(id: "p6", kind: .routineNudge, title: "어제 이 시간에 했어요",
                       body: "어제 10:00에 '📖 독서'를 했어요 · 연속 7일 기록을 지켜봐요",
                       date: now.addingTimeInterval(-1_800), route: NudgeRoute.calendar(date: KST.today()).raw,
                       serverType: "ROUTINE_NUDGE", routineKind: "YESTERDAY_TIME"),
            NudgeEntry(id: "p2", kind: .server, title: "곧 시작해요", body: "10분 뒤 '영어 단어' 시작이에요.",
                       date: now.addingTimeInterval(-3_600), route: nil),
            NudgeEntry(id: "p3", kind: .countdown, title: "'📝 SQLD 합격' 해방까지 3일",
                       body: "끝이 보여요. 남은 사흘은 꼭 해야 할 것만 해도 충분해요.",
                       date: now.addingTimeInterval(-86_400), route: NudgeRoute.goal(2).raw, read: true),
            NudgeEntry(id: "p4", kind: .why, title: "15일 전의 내가 보낸 말 💌",
                       body: "“여름 전에 10km 를 쉬지 않고 뛰고 싶어서” — '🏃 러닝'을(를) 시작한 이유예요.",
                       date: now.addingTimeInterval(-3 * 86_400), route: NudgeRoute.goal(3).raw, read: true),
            NudgeEntry(id: "p5", kind: .comeback, title: "완벽하지 않아도 괜찮아요",
                       body: "다시 시작하면 돼요. 오늘은 10분짜리 하나만 해볼까요?",
                       date: now.addingTimeInterval(-9 * 86_400), route: NudgeRoute.calendar(date: KST.today()).raw, read: true),
        ]
    }

    static var planned: [NudgeEntry] {
        [NudgeEntry(id: "n1", kind: .firstStep, title: "오늘은 이것 하나만 먼저 👣",
                    body: "09:00 '영어 단어' — 나머지 3개는 이것부터 끝내고 봐요.", date: Date().addingTimeInterval(5_400),
                    route: NudgeRoute.calendar(date: KST.addDays(KST.today(), 1)).raw)]
    }
}

#Preview("알림함") {
    NavigationStack {
        NudgeInboxScreen(center: NudgeCenter(preview: NudgePreviewData.entries, planned: NudgePreviewData.planned))
    }
    .environment(AppRouter.shared)
}

#Preview("알림함 — 비어 있음") {
    NavigationStack {
        NudgeInboxScreen(center: NudgeCenter(preview: []))
    }
    .environment(AppRouter.shared)
}

#Preview("한 줄") {
    VStack(spacing: 8) {
        ForEach(NudgePreviewData.entries.prefix(3)) { NudgeInboxRow(entry: $0) }
    }
    .padding(16)
    .canvasBackground()
}
