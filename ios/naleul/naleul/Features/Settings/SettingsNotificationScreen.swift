// Features/Settings/SettingsNotificationScreen.swift
//
// 웹 원본: app/(main)/settings/notifications/page.tsx
//          features/notification-settings/ui/NotificationSettingsView.tsx
//          features/notification-settings/api.ts
//
// Task 푸시 알림 on/off (미션 알림 설정은 미션 기능 종료로 제외). FREE 플랜이면 스위치 대신 자물쇠 (예전 iOS 와 같은 규칙: PRO·ADMIN 만 사용).
// 스위치는 먼저 바꿔서 바로 반응하고(낙관적 업데이트), 서버가 실패하면 원래 값으로 되돌려요.

import SwiftUI
import Observation

// MARK: - 모델 · API

/// GET /v1/notification-settings 응답 (백엔드 NotificationSettingsResponse — 모두 boolean 이라 항상 와요)
struct SettingsNotificationSettings: Codable, Hashable {
    var missionAlertEnabled: Bool
    var missionCertificationEnabled: Bool
    var wishlistEnabled: Bool
    var missionProgressEnabled: Bool
    var missionEditEnabled: Bool
    var badgeEnabled: Bool
    var pokeEnabled: Bool
    var taskAlertEnabled: Bool
}

enum SettingsNotificationAPI {
    private static let base = "/v1/notification-settings"

    static func fetch() async throws -> SettingsNotificationSettings? {
        try await API.get(base)
    }

    /// Task 알림은 카테고리가 아니라 별도 엔드포인트(/task-alert)로 바꿔요
    static func updateTaskAlert(enabled: Bool) async throws -> SettingsNotificationSettings? {
        try await API.patch("\(base)/task-alert", body: ["enabled": .bool(enabled)])
    }

    /// 미션 알림 카테고리용 (지금 화면에서는 안 써요)
    static func updateCategory(_ category: String, enabled: Bool) async throws -> SettingsNotificationSettings? {
        try await API.patch(base, body: ["category": .string(category), "enabled": .bool(enabled)])
    }
}

// MARK: - 화면 상태

@MainActor
@Observable
final class SettingsNotificationViewModel {
    var phase: LoadPhase<SettingsNotificationSettings> = .loading
    private let isSample: Bool
    // MARK: Amplitude
    private var pageviewSent = false
    private(set) var lastErrorStatus: Int?

    init(sample: SettingsNotificationSettings? = nil) {
        isSample = sample != nil
        if let sample { phase = .loaded(sample) }
    }

    func load() async {
        if isSample { return }
        if phase.value == nil { phase = .loading }
        do {
            if let s = try await SettingsNotificationAPI.fetch() {
                phase = .loaded(s)
                if !pageviewSent {
                    pageviewSent = true
                    ST.track("notification_settings.pageview", ["is_premium": ST.isPremium, "is_guest": UserStore.shared.isGuest])
                }
            } else {
                phase = .failed("설정을 불러오지 못했어요.")
                lastErrorStatus = 0
                ST.track("notification_settings.load_fail", ["error_status": 0])
            }
        } catch is CancellationError {
        } catch {
            phase = .failed("설정을 불러오지 못했어요.")
            lastErrorStatus = ST.status(error)
            ST.track("notification_settings.load_fail", ["error_status": lastErrorStatus])
        }
    }

    /// 토글 하나 바꾸기. category 가 nil 이면 Task 알림
    func toggle(category: String?, field: WritableKeyPath<SettingsNotificationSettings, Bool>, enabled: Bool) {
        guard var current = phase.value else { return }
        let prev = current
        current[keyPath: field] = enabled
        let kind = category ?? "TASK_ALERT"
        ST.track("notification_settings.toggle", ["notification_kind": kind, "checked": enabled])
        phase = .loaded(current)   // 먼저 바꿔서 바로 반응
        if isSample { return }
        Task {
            do {
                let res: SettingsNotificationSettings?
                if let category {
                    res = try await SettingsNotificationAPI.updateCategory(category, enabled: enabled)
                } else {
                    res = try await SettingsNotificationAPI.updateTaskAlert(enabled: enabled)
                }
                if let res { phase = .loaded(res) }
                ST.track("notification_settings.toggle_result", ["notification_kind": kind, "checked": enabled, "result": "success"])
            } catch {
                ST.track("notification_settings.toggle_result", [
                    "notification_kind": kind, "checked": enabled, "result": "fail", "error_status": ST.status(error),
                ])
                phase = .loaded(prev)
                Toast.error("설정 변경에 실패했어요. 다시 시도해 주세요.")
            }
        }
    }
}

// MARK: - 화면

struct SettingsNotificationScreen: View {
    @Environment(UserStore.self) private var userStore
    @State private var vm: SettingsNotificationViewModel
    /// 미리보기에서 잠김 상태를 강제로 보여줄 때
    private let lockedOverride: Bool?

    init() {
        _vm = State(initialValue: SettingsNotificationViewModel())
        lockedOverride = nil
    }

    /// 미리보기용
    init(sample: SettingsNotificationSettings, locked: Bool) {
        _vm = State(initialValue: SettingsNotificationViewModel(sample: sample))
        lockedOverride = locked
    }

    /// BM(2026-10): 알림은 Free 도 쓸 수 있어요 (AI 만 횟수 제한). 둘러보기만 잠금 (같은 계정을 모두 같이 써서)
    private var locked: Bool {
        if let lockedOverride { return lockedOverride }
        return userStore.isGuest
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("여기서 바꾼 설정은 나를 앱의 푸시 알림에 적용돼요.")
                    .font(.pt(14))
                    .foregroundStyle(Color.ink3)

                // 이 기기에서 울리는 실천 도우미 알림 (하루 마무리 · 아침 첫걸음 · 시작한 이유 …)
                NudgeSettingsCard(locked: locked)

                switch vm.phase {
                case .loading:
                    LoadingView(minHeight: 192)
                case .failed:
                    VStack(spacing: 16) {
                        Text("설정을 불러오지 못했어요.").font(.pt(15)).foregroundStyle(Color.ink2)
                        NButton("다시 시도", variant: .secondary) {
                            ST.track("notification_settings.retry_click", ["error_status": vm.lastErrorStatus])
                            Task { await vm.load() }
                        }
                    }
                    .padding(32)
                    .frame(maxWidth: .infinity)
                    .card()
                case .loaded(let data):
                    content(data)
                }
            }
            .padding(16)
        }
        .canvasBackground()
        .navigationTitle("알림 설정")
        .navigationBarTitleDisplayMode(.inline)
        .reloadOn([.notificationSettings]) { await vm.load() }
    }

    @ViewBuilder
    private func content(_ data: SettingsNotificationSettings) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            NotificationSectionTitle(title: "Task 알림 설정", locked: locked)
            NotificationToggleRow(
                kind: "TASK_ALERT",
                title: "Task 알림",
                description: "개인이 설정한 Task 푸시 알림을 받을 수 있어요.",
                isOn: data.taskAlertEnabled,
                locked: locked
            ) { enabled in
                vm.toggle(category: nil, field: \.taskAlertEnabled, enabled: enabled)
            }
        }
        .card()

        // 미션 알림 설정은 미션 기능 종료로 뺐어요 (서버 값은 그대로 두고 화면에서만 숨겨요)

        if locked {
            VStack(alignment: .leading, spacing: 12) {
                Text("로그인하면 푸시 알림을 설정할 수 있어요.")
                    .font(.pt(13))
                    .foregroundStyle(Color.ink3)
                // iOS 에서는 바로 멤버십 화면으로 갈 수 있게 버튼을 하나 더 뒀어요
                NavigationLink(value: AppRoute.settings(.membership)) {
                    HStack(spacing: 6) {
                        NSymbol("crown", size: 13, weight: .semibold)
                        Text("멤버십 보기")
                    }
                    .simultaneousGesture(TapGesture().onEnded {
                        // [UX] '로그인하면…' 문구인데 멤버십으로 가요
                        ST.track("notification_settings.locked_click", ["destination": "membership"])
                        ST.membershipEntry = "notification_settings"
                    })
                }
                .buttonStyle(.n(.brand, .sm))
            }
            .padding(.horizontal, 4)
        }
    }
}

private struct NotificationSectionTitle: View {
    let title: String
    let locked: Bool

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 8) {
                Text(title).font(.pt(16, .semibold)).foregroundStyle(Color.ink)
                if locked { Badge("PRO", tone: .brand) }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 16)
            Rectangle().fill(Color.borderSoft).frame(height: 1)
        }
    }
}

private struct NotificationToggleRow: View {
    /// notification_kind (TASK_ALERT · MISSION_ALERT …)
    var kind: String = "TASK_ALERT"
    let title: String
    let description: String
    let isOn: Bool
    let locked: Bool
    let onChange: (Bool) -> Void

    private var text: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title).font(.pt(15, .semibold)).foregroundStyle(locked ? Color.ink4 : Color.ink)
            Text(description).font(.pt(13)).foregroundStyle(locked ? Color.ink4 : Color.ink3)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    var body: some View {
        if locked {
            // 잠긴 상태 — 누르면 토글 대신 안내
            Button {
                ST.track("notification_settings.locked_click", ["notification_kind": kind, "destination": "login_prompt"])
                LoginPrompt.shared.show(reason: "notification_settings")
            } label: {
                HStack(spacing: 16) {
                    text
                    NSymbol("lock", size: 15).foregroundStyle(Color.ink4)
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 16)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        } else {
            HStack(spacing: 16) {
                text
                NSwitch(isOn: Binding(get: { isOn }, set: { onChange($0) }), label: title)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 16)
        }
    }
}

private let sampleNotificationSettings = SettingsNotificationSettings(
    missionAlertEnabled: true, missionCertificationEnabled: true, wishlistEnabled: false,
    missionProgressEnabled: true, missionEditEnabled: false, badgeEnabled: false,
    pokeEnabled: true, taskAlertEnabled: true
)

#Preview("PRO") {
    NavigationStack {
        SettingsNotificationScreen(sample: sampleNotificationSettings, locked: false)
    }
    .environment(UserStore.shared)
    .environment(AppRouter.shared)
}

#Preview("FREE (잠김)") {
    NavigationStack {
        SettingsNotificationScreen(sample: sampleNotificationSettings, locked: true)
    }
    .environment(UserStore.shared)
    .environment(AppRouter.shared)
}
