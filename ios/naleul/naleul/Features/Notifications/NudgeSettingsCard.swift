// Features/Notifications/NudgeSettingsCard.swift
//
// 설정 > 알림 설정 맨 위 "실천 도우미 알림" — 묶음별로 켜고 꺼요 (이 기기에만 저장).
// 바꾸면 바로 다시 예약해요. 알림함으로 가는 줄도 여기 있어요.

import SwiftUI

struct NudgeSettingsCard: View {
    let locked: Bool
    @Environment(AppRouter.self) private var router
    @State private var on: Set<NudgeGroup> = Set(NudgeGroup.allCases.filter { $0.isEnabled })
    private let center: NudgeCenter

    init(locked: Bool, center: NudgeCenter = .shared) {
        self.locked = locked
        self.center = center
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 8) {
                    Text("실천 도우미 알림").font(.pt(16, .semibold)).foregroundStyle(Color.ink)
                    Spacer(minLength: 0)
                }
                Text("계획만 세우고 끝나지 않게, 필요한 순간에 한마디씩 걸어요. 이 기기에서만 울려요.")
                    .font(.pt(13))
                    .foregroundStyle(Color.ink3)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 16)
            Rectangle().fill(Color.borderSoft).frame(height: 1)

            ForEach(Array(NudgeGroup.allCases.enumerated()), id: \.element) { i, g in
                if i > 0 { Rectangle().fill(Color.borderSoft).frame(height: 1) }
                row(g)
            }

            Rectangle().fill(Color.borderSoft).frame(height: 1)
            Button {
                Analytics.track(NotiEvent.nudgeInboxClick, ["unread_count": center.unreadCount, "planned_count": center.planned.count])
                InboxTracker.shared.pendingEntry = "settings"
                router.openNotifications()
            } label: {
                HStack(spacing: 8) {
                    Image(systemName: "tray.full").font(.system(size: 14))
                    Text("알림함 보기")
                    if center.unreadCount > 0 {
                        Text("\(center.unreadCount)")
                            .font(.pt(11, .bold))
                            .foregroundStyle(Color.white)
                            .padding(.horizontal, 6)
                            .frame(minHeight: 18)
                            .background(Color.actionDanger, in: Capsule())
                    }
                    Spacer(minLength: 0)
                    if let next = center.planned.first {
                        Text("다음 · \(NudgeFormat.when(next.date))").font(.pt(12)).foregroundStyle(Color.ink3)
                    }
                    NSymbol("chevron.right", size: 12, weight: .semibold).foregroundStyle(Color.ink4)
                }
                .font(.pt(15, .semibold))
                .foregroundStyle(Color.ink)
                .padding(.horizontal, 20)
                .padding(.vertical, 14)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
        .card()
    }

    @ViewBuilder
    private func row(_ g: NudgeGroup) -> some View {
        let text = VStack(alignment: .leading, spacing: 2) {
            Text(g.title).font(.pt(15, .semibold)).foregroundStyle(locked ? Color.ink4 : Color.ink)
            Text(g.description).font(.pt(13)).foregroundStyle(locked ? Color.ink4 : Color.ink3)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)

        if locked {
            Button {
                Analytics.track(NotiEvent.nudgeLockedClick, ["nudge_group": g.rawValue])
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
                NSwitch(isOn: Binding(get: { on.contains(g) }, set: { v in
                    if v { on.insert(g) } else { on.remove(g) }
                    // 권한이 꺼져 있는데 켜면 예약만 되고 울리지 않아요 → push_authorized 로 같이 봐요
                    Analytics.track(NotiEvent.nudgeToggle, [
                        "nudge_group": g.rawValue, "is_on": v,
                        "enabled_group_count": on.count, "push_authorized": center.isAuthorized,
                    ])
                    center.setEnabled(g, v)
                }), label: g.title)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 16)
        }
    }
}

#Preview("실천 도우미 알림") {
    ScrollView {
        VStack(spacing: 16) {
            NudgeSettingsCard(locked: false,
                              center: NudgeCenter(preview: NudgePreviewData.entries, planned: NudgePreviewData.planned))
            NudgeSettingsCard(locked: true, center: NudgeCenter(preview: []))
        }
        .padding(16)
    }
    .canvasBackground()
    .environment(AppRouter.shared)
}
