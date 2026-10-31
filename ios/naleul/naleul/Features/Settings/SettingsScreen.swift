// Features/Settings/SettingsScreen.swift
//
// 웹 원본: app/(main)/settings/page.tsx (설정 탭 루트)
//          features/account/ui/LogoutButton.tsx
//
// 위 헤더 톱니 버튼으로 지금 탭 위에 쌓여요 (AppRoute.settingsHome). 하위 화면은 NavigationLink(value: AppRoute.settings(...))
// 로 열고, 실제 화면은 SettingsRouteView 가 골라요.
// 웹의 "운영 대시보드(DEV)" 버튼은 iOS 에 옮기지 않았어요.

import SwiftUI

struct SettingsScreen: View {
    @Environment(UserStore.self) private var userStore

    init() {}

    var body: some View {
        SettingsContent(
            name: displayName,
            role: userStore.user?.userRole ?? .free,
            usageSample: nil,
            adminViewPlan: userStore.user?.adminViewPlan
        )
        // 멤버십 결제 · 닉네임 변경 뒤(.account) 서버의 최신 이름 · 플랜으로 맞춰요
        .reloadOn([.account]) { await refreshUser() }
    }

    private var displayName: String {
        let n = userStore.user?.userName ?? ""
        return n.isEmpty ? "나를 사용자" : n
    }

    @MainActor
    private func refreshUser() async {
        guard let userId = userStore.user?.userId else { return }
        guard let info = try? await UserAPI.fetchUser(userId: userId) else { return }
        userStore.updateUserName(info.nickname)
        userStore.updateAdminViewPlan(info.adminViewPlan)
        userStore.updateUserRole(UserRole(rawValue: info.userRole) ?? .free)
    }
}

/// 설정 화면 내용 (미리보기에서 사용자 · 사용량을 직접 넣을 수 있게 따로 뒀어요)
///
/// 모바일 배치 (아이폰 기본 설정 앱처럼 묶음 목록)
///  ┌ 설정 (큰 제목)                              ┐
///  │ [아바타] 유림  PRO                    ›     │  ← 누르면 닉네임 수정 시트
///  │ (무료면) Pro로 더 많이 · 업그레이드 배너       │
///  │ 오늘 사용량                                  │
///  │ 일정   : 기본 생활 패턴 · 외부 캘린더 가져오기 │
///  │ 앱     : 알림 · 위젯 · 화면 모드 · 멤버십      │
///  │ 정보   : 약관 · 앱 버전                       │
///  │ 계정   : 로그아웃 · 탈퇴하기                   │
///  └─────────────────────────────────────────────┘
struct SettingsContent: View {
    let name: String
    let role: UserRole
    /// 미리보기용 사용량 (nil 이면 서버에서 불러와요)
    let usageSample: AiUsage?

    @State private var logoutOpen = false

    // private @State 가 있으면 자동 생성 init 이 private 이 되어 SettingsScreen · 미리보기에서 못 불러서 직접 만들어요
    /// 운영진이 보고 있는 플랜 — role 이 그대로여도 플랜을 바꾸면 이 화면을 다시 그리게 입력으로 받아요
    let adminViewPlan: String?

    init(name: String, role: UserRole, usageSample: AiUsage?, adminViewPlan: String? = nil) {
        self.name = name
        self.role = role
        self.usageSample = usageSample
        self.adminViewPlan = adminViewPlan
    }

    private var appVersion: String {
        let v = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "-"
        let b = Bundle.main.infoDictionary?["CFBundleVersion"] as? String
        return b.map { "\(v) (\($0))" } ?? v
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                if role == .guest {
                    GuestBanner(surface: "settings")
                } else {
                    SettingsProfileSummaryCard(name: name, role: role)
                }

                // 운영진이 Free 화면으로 볼 때도 실제 Free 사용자처럼 보여요
                if role == .free || (role == .admin && !role.isPaid) {
                    SettingsUpgradeBanner()
                }

                // 하루 횟수가 정해진 기능 (AI 목표 만들기 · AI 계획 초안 · Task 추가)
                if let usageSample {
                    SettingsUsageCard(sample: usageSample)
                } else {
                    SettingsUsageCard()
                }

                SettingsGroup("일정") {
                    SettingsLinkRow(route: .lifePattern, icon: "clock.fill", tint: GoalPalette.blue.tone.accent,
                                    title: "기본 생활 패턴", subtitle: "수면 · 식사 · 이동처럼 매주 반복되는 시간")
                    SettingsDivider()
                    SettingsLinkRow(route: .calendarImport, icon: "calendar.badge.plus", tint: GoalPalette.coral.tone.accent,
                                    title: "외부 캘린더 가져오기", subtitle: "일정을 골라 Task로 가져와요")
                    SettingsDivider()
                    SettingsAutoCarryRow()
                }

                SettingsGroup("앱") {
                    SettingsLinkRow(route: .notifications, icon: "bell.fill", tint: GoalPalette.amber.tone.accent,
                                    title: "알림", subtitle: nil)
                    SettingsDivider()
                    SettingsLinkRow(route: .widget, icon: "square.grid.2x2.fill", tint: GoalPalette.blue.tone.accent,
                                    title: "위젯", subtitle: "홈 화면 위젯 스타일")
                    SettingsDivider()
                    SettingsThemeRow()
                    SettingsDivider()
                    SettingsLinkRow(route: .membership, icon: "crown.fill", tint: GoalPalette.purple.tone.accent,
                                    title: "멤버십", subtitle: nil,
                                    value: role.isPaid ? "PRO" : "무료")
                }

                // 운영진만 — 결제와 상관없이 Free · Pro 화면과 횟수 제한을 바꿔 확인해요 (서버에도 적용)
                if role == .admin {
                    SettingsGroup("운영진") {
                        SettingsAdminPlanRow()
                    }
                }

                SettingsGroup("정보") {
                    SettingsLinkRow(route: .terms, icon: "doc.text.fill", tint: GoalPalette.slate.tone.accent,
                                    title: "약관 동의", subtitle: nil)
                    SettingsDivider()
                    SettingsValueRow(icon: "info.circle.fill", tint: Color.textTertiary,
                                     title: "앱 버전", value: appVersion)
                }

                SettingsGroup("계정") {
                    Button {
                        Analytics.track(AnalyticsEvent.settingsLogoutClick, ["is_guest": role == .guest])
                        logoutOpen = true
                    } label: {
                        SettingsRowLabel(icon: "rectangle.portrait.and.arrow.right", tint: Color.textSecondary,
                                         title: role == .guest ? "둘러보기 끝내기" : "로그아웃", subtitle: nil, chevron: false)
                    }
                    .buttonStyle(SettingsRowButtonStyle())
                }

                if role != .guest {
                    // 예전엔 TapGesture 가 링크 안쪽 글자에 붙어 있어서 탭을 가로채 화면이 안 넘어갔어요
                    // → 다른 줄(SettingsLinkRow)처럼 링크 바깥에 simultaneousGesture 를 붙여요
                    NavigationLink(value: AppRoute.settings(.withdraw)) {
                        Text("탈퇴하기")
                            .font(.pt(13, .medium))
                            .foregroundStyle(Color.ink3)
                            .underline()
                            .frame(maxWidth: .infinity, minHeight: 44)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .simultaneousGesture(TapGesture().onEnded {
                        ST.track("settings.menu_click", ["destination": "withdraw", "is_premium": role.isPaid])
                    })
                }
            }
            .pagePadding()
            .padding(.top, 8)
            .padding(.bottom, 24)
        }
        .canvasBackground()
        // 위 헤더 톱니로 지금 탭 위에 쌓이는 화면 — 뒤로 버튼 있는 내비게이션 바
        .navigationTitle("설정")
        .navigationBarTitleDisplayMode(.inline)
        // 탭이 보일 때마다 (다른 탭에 갔다 오면 다시)
        .onAppear {
            ST.track("settings.pageview", [
                "is_premium": role.isPaid,
                "theme_pref": UserDefaults.standard.string(forKey: ThemePref.storageKey) ?? ThemePref.system.rawValue,
            ])
        }
        // 확인창 (웹 ConfirmDialog) — 취소도 이벤트로 남기려고 confirmDialog 대신 직접 alert 를 써요
        .alert(role == .guest ? "둘러보기를 끝내고 로그인 화면으로 갈까요?" : "정말 로그아웃 하시겠어요?",
               isPresented: $logoutOpen) {
            Button("취소", role: .cancel) {
                Analytics.track(AnalyticsEvent.settingsLogoutCancelClick, ["is_guest": role == .guest])
            }
            Button(role == .guest ? "끝내기" : "로그아웃", role: .destructive) {
                Analytics.track(AnalyticsEvent.settingsLogoutConfirmClick, ["is_guest": role == .guest])
                AuthService.shared.logout(reason: .logout)
            }
        }
    }
}

// MARK: - 묶음 · 줄

/// 회색 작은 제목 + 흰 카드 묶음 (아이폰 설정 앱의 inset grouped)
struct SettingsGroup<Content: View>: View {
    let title: String
    @ViewBuilder var content: () -> Content

    init(_ title: String, @ViewBuilder content: @escaping () -> Content) {
        self.title = title
        self.content = content
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.pt(13, .semibold))
                .foregroundStyle(Color.ink3)
                .padding(.leading, 16)
            VStack(spacing: 0) { content() }
                .background(Color.surface, in: RoundedRectangle(cornerRadius: 16))
                .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.line, lineWidth: 1))
                .clipShape(RoundedRectangle(cornerRadius: 16))
        }
    }
}

/// 아이콘 뒤부터 시작하는 구분선
struct SettingsDivider: View {
    var body: some View {
        Rectangle().fill(Color.borderSoft).frame(height: 0.5).padding(.leading, 60)
    }
}

/// 줄 모양: [색 아이콘] 제목 (부제) … 값 ›
struct SettingsRowLabel: View {
    let icon: String
    let tint: Color
    let title: String
    let subtitle: String?
    var value: String? = nil
    var chevron = true

    var body: some View {
        HStack(spacing: 14) {
            NSymbol(icon, size: 14, weight: .semibold)
                .foregroundStyle(.white)
                .frame(width: 30, height: 30)
                .background(tint, in: RoundedRectangle(cornerRadius: 8))
            VStack(alignment: .leading, spacing: 1) {
                Text(title).font(.pt(15, .medium)).foregroundStyle(Color.ink)
                if let subtitle {
                    Text(subtitle).font(.pt(12)).foregroundStyle(Color.ink3).lineLimit(1)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            if let value {
                Text(value).font(.pt(14)).foregroundStyle(Color.ink3).lineLimit(1)
            }
            if chevron {
                NSymbol("chevron.right", size: 13, weight: .semibold)
                    .foregroundStyle(Color.ink4)
            }
        }
        .padding(.horizontal, 16)
        .frame(minHeight: subtitle == nil ? 52 : 60)
        .contentShape(Rectangle())
    }
}

/// 하위 화면으로 가는 줄
struct SettingsLinkRow: View {
    let route: SettingsRoute
    let icon: String
    let tint: Color
    let title: String
    let subtitle: String?
    var value: String? = nil

    /// settings.menu_click 의 destination
    private var destination: String {
        switch route {
        case .lifePattern: return "life_pattern"
        case .notifications: return "notifications"
        case .calendarImport: return "calendar_import"
        case .membership: return "membership"
        case .terms, .termsDetail: return "terms"
        case .withdraw: return "withdraw"
        case .widget: return "widget"
        }
    }

    var body: some View {
        NavigationLink(value: AppRoute.settings(route)) {
            SettingsRowLabel(icon: icon, tint: tint, title: title, subtitle: subtitle, value: value)
        }
        .simultaneousGesture(TapGesture().onEnded {
            ST.track("settings.menu_click", ["destination": destination, "is_premium": ST.isPremium])
            if route == .membership { ST.membershipEntry = "settings_row" }
            if route == .lifePattern { ST.lifePatternEntry = "settings" }
        })
        .buttonStyle(SettingsRowButtonStyle())
    }
}

/// 값만 보여주는 줄 (앱 버전)
struct SettingsValueRow: View {
    let icon: String
    let tint: Color
    let title: String
    let value: String

    var body: some View {
        SettingsRowLabel(icon: icon, tint: tint, title: title, subtitle: nil, value: value, chevron: false)
    }
}

/// 화면 모드 줄 — 오른쪽 메뉴로 시스템 / 라이트 / 다크
struct SettingsThemeRow: View {
    @AppStorage(ThemePref.storageKey) private var themeRaw = ThemePref.system.rawValue

    init() {}

    private var current: ThemePref { ThemePref(rawValue: themeRaw) ?? .system }

    var body: some View {
        Menu {
            Picker("화면 모드", selection: Binding(get: { current }, set: {
                if $0 != current { ST.track("settings.theme_change", ["theme_pref": $0.rawValue]) }
                themeRaw = $0.rawValue
            })) {
                ForEach(ThemePref.allCases) { t in
                    Label(t.label, systemImage: t.icon).tag(t)
                }
            }
        } label: {
            HStack(spacing: 14) {
                Image(systemName: "circle.lefthalf.filled")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Color.textPrimary)
                    .frame(width: 30, height: 30)
                    .background(Color.bgSubtle, in: RoundedRectangle(cornerRadius: Radius.nav))
                Text("화면 모드").font(.pt(15, .medium)).foregroundStyle(Color.ink)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Text(current.label).font(.pt(14)).foregroundStyle(Color.ink3)
                Image(systemName: "chevron.up.chevron.down")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(Color.ink4)
            }
            .padding(.horizontal, 16)
            .frame(minHeight: 52)
            .contentShape(Rectangle())
        }
        .buttonStyle(SettingsRowButtonStyle())
        .accessibilityLabel("화면 모드, \(current.label)")
    }
}

/// 운영진 플랜 바꿔 보기 (2026-10-24) — PATCH /v1/admin/view-plan
/// 서버의 isCurrentlyPremium 이 이 값을 따르므로 하루 횟수 제한 · Pro 전용 잠금도 실제 Free / Pro 사용자와 같아져요.
struct SettingsAdminPlanRow: View {
    @Environment(UserStore.self) private var userStore
    /// UserRole.saveAdminViewPlan 과 같은 키 — 바뀌면 이 줄이 바로 다시 그려져요
    @AppStorage("admin_view_plan") private var storedPlan: String?
    @State private var saving = false

    init() {}

    private var current: String { storedPlan == "FREE" ? "FREE" : "PRO" }

    var body: some View {
        Menu {
            Picker("보고 있는 플랜", selection: Binding(get: { current }, set: { change(to: $0) })) {
                Text("Free").tag("FREE")
                Text("Pro").tag("PRO")
            }
        } label: {
            HStack(spacing: 14) {
                Image(systemName: "eye.fill")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Color.textPrimary)
                    .frame(width: 30, height: 30)
                    .background(Color.bgSubtle, in: RoundedRectangle(cornerRadius: Radius.nav))
                VStack(alignment: .leading, spacing: 2) {
                    Text("플랜 바꿔 보기").font(.pt(15, .medium)).foregroundStyle(Color.ink)
                    Text("결제와 상관없이 Free · Pro 화면과 횟수 제한을 확인해요")
                        .font(.pt(12)).foregroundStyle(Color.ink3)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                if saving {
                    ProgressView().controlSize(.small)
                } else {
                    Text(current == "FREE" ? "Free" : "Pro").font(.pt(14)).foregroundStyle(Color.ink3)
                }
                Image(systemName: "chevron.up.chevron.down")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(Color.ink4)
            }
            .padding(.horizontal, 16)
            .frame(minHeight: 60)
            .contentShape(Rectangle())
        }
        .buttonStyle(SettingsRowButtonStyle())
        .disabled(saving)
        .accessibilityLabel("플랜 바꿔 보기, \(current == "FREE" ? "Free" : "Pro")")
    }

    private func change(to next: String) {
        let from = current
        guard next != from, !saving else { return }
        saving = true
        Task { @MainActor in
            defer { saving = false }
            do {
                let r: AdminViewPlanResponse? = try await API.patch("/v1/admin/view-plan", body: ["plan": .string(next)])
                userStore.updateAdminViewPlan(r?.plan ?? next)
                ST.track("settings.admin_view_plan_change", ["from_plan": from, "to_plan": next, "result": "success"])
                Toast.show(next == "PRO" ? "이제 Pro 화면으로 보여요." : "이제 Free 화면으로 보여요. 횟수 제한도 Free 와 같아요.")
                Invalidation.shared.invalidate(.account, .usage)
            } catch {
                ST.track("settings.admin_view_plan_change", ["from_plan": from, "to_plan": next, "result": "fail"])
                Toast.show("바꾸지 못했어요. 다시 시도해 주세요.")
            }
        }
    }
}

private struct AdminViewPlanResponse: Decodable {
    let plan: String
    let premium: Bool
}

/// 무료 사용자에게만: Pro 업그레이드 배너
struct SettingsUpgradeBanner: View {
    var body: some View {
        NavigationLink(value: AppRoute.settings(.membership)) {
            HStack(spacing: 14) {
                NSymbol("crown.fill", size: 18, weight: .semibold)
                    .foregroundStyle(.white)
                    .frame(width: 42, height: 42)
                    .background(Color.white.opacity(0.18), in: RoundedRectangle(cornerRadius: 12))
                VStack(alignment: .leading, spacing: 2) {
                    Text("Pro로 더 많이").font(.pt(16, .semibold)).foregroundStyle(.white)
                    Text("AI 목표 · 계획 초안을 넉넉하게, 광고 없이")
                        .font(.pt(12))
                        .foregroundStyle(Color.white.opacity(0.85))
                        .lineLimit(2)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                NSymbol("chevron.right", size: 13, weight: .bold)
                    .foregroundStyle(Color.white.opacity(0.8))
            }
            .padding(16)
            .background(
                Color.aiGradient,
                in: RoundedRectangle(cornerRadius: Radius.card, style: .continuous)
            )
            .contentShape(RoundedRectangle(cornerRadius: Radius.card))
        }
        .buttonStyle(.plain)
        .simultaneousGesture(TapGesture().onEnded {
            ST.track("settings.menu_click", ["destination": "membership_banner", "is_premium": false])
            ST.membershipEntry = "settings_banner"
        })
    }
}

/// 설정 목록 줄을 눌렀을 때 살짝 회색 (웹 hover:bg-subtle/60)
struct SettingsRowButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .background(configuration.isPressed ? Color.subtle.opacity(0.6) : Color.clear)
    }
}

#Preview("무료") {
    NavigationStack {
        SettingsContent(name: "유림", role: .free, usageSample: AiUsage(pro: false, resetsAt: "2026-10-10T00:00:00", items: [
            UsageItem(key: "GOAL_SESSION", label: "AI 목표 만들기", description: "목표를 대화로 설계해요", used: 2, limit: 3, remaining: 1),
        ], session: nil))
    }
}

#Preview("Pro") {
    NavigationStack {
        SettingsContent(
            name: "유림",
            role: .pro,
            usageSample: AiUsage(pro: true, resetsAt: "2026-10-10T00:00:00", items: [
                UsageItem(key: "GOAL_SESSION", label: "AI 목표 만들기", description: "목표를 대화로 설계해요", used: 2, limit: 10, remaining: 8),
                UsageItem(key: "TASK_PER_DAY", label: "Task 추가", description: "하루에 추가할 수 있는 Task", used: 4, limit: nil, remaining: nil),
            ], session: nil)
        )
    }
}

// MARK: - 못 한 일 자동 이월 (2026-10, 웹 설정 AutoCarryToggle)

struct SettingsAutoCarryRow: View {
    private var setting: AutoCarrySetting { AutoCarrySetting.shared }

    var body: some View {
        HStack(spacing: 0) {
            SettingsRowLabel(icon: "arrow.uturn.forward", tint: GoalPalette.green.tone.accent,
                             title: "못 한 일 자동 이월", subtitle: setting.help, chevron: false)
            NSwitch(isOn: setting.binding(surface: "settings"), label: "못 한 일 자동 이월", disabled: setting.saving)
                .padding(.trailing, 16)
        }
        .task { if !setting.loaded { await setting.load() } }
    }
}
