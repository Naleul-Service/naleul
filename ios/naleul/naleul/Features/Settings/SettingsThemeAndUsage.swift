// Features/Settings/SettingsThemeAndUsage.swift
//
// 웹 원본: features/theme/ThemePicker.tsx (설정 > 화면 모드)
//          features/usage/ui/UsageCard.tsx (설정 > 오늘 사용량)
//
// 화면 모드는 @AppStorage(ThemePref.storageKey) 에 저장만 하면 RootView 가 바로 적용해요.

import SwiftUI
import Observation

// MARK: - 화면 모드

struct SettingsThemePicker: View {
    @AppStorage(ThemePref.storageKey) private var themeRaw = ThemePref.system.rawValue

    init() {}

    private var selection: Binding<ThemePref> {
        Binding(
            get: { ThemePref(rawValue: themeRaw) ?? .system },
            set: {
                if $0.rawValue != themeRaw { ST.track("settings.theme_change", ["theme_pref": $0.rawValue]) }
                themeRaw = $0.rawValue
            }
        )
    }

    private var options: [(value: ThemePref, label: String)] {
        ThemePref.allCases.map { (value: $0, label: $0.label) }
    }

    private var icons: [ThemePref: String] {
        var d: [ThemePref: String] = [:]
        for t in ThemePref.allCases { d[t] = t.icon }
        return d
    }

    var body: some View {
        // 웹은 한 줄(제목 | 버튼)이지만 아이폰 폭에 맞게 위아래로 놓았어요
        VStack(alignment: .leading, spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text("화면 모드").font(.pt(15, .bold)).foregroundStyle(Color.ink)
                Text("시스템을 고르면 기기 설정(라이트·다크)을 따라가요").font(.pt(13)).foregroundStyle(Color.ink3)
            }
            SegmentPicker(selection: selection, options: options, icons: icons, fullWidth: true)
                .accessibilityLabel("화면 모드")
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 16)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

// MARK: - 오늘 사용량

@MainActor
@Observable
final class SettingsUsageViewModel {
    var usage: AiUsage?
    var isPending = true
    private let isSample: Bool

    init(sample: AiUsage? = nil) {
        isSample = sample != nil
        if let sample {
            usage = sample
            isPending = false
        }
    }

    func load() async {
        if isSample { return }
        do {
            let u = try await UsageAPI.usage()
            usage = u
            // [UX] 한도를 다 쓴 기능이 있어도 카드 안에 Pro 로 가는 버튼이 없어요
            ST.track("settings.usage_view", [
                "is_premium": u?.pro ?? ST.isPremium,
                "exhausted_keys": (u?.items ?? []).filter { $0.limit != nil && ($0.remaining ?? 0) == 0 }.map(\.key).joined(separator: ","),
                "result": "success",
            ])
        } catch is CancellationError {
            return
        } catch {
            // 웹처럼 실패하면 빈 목록으로 (설정 화면 전체를 막지 않아요)
            ST.track("settings.usage_view", ["is_premium": ST.isPremium, "result": "fail"])
        }
        isPending = false
    }
}

/// 하루 횟수가 정해진 기능 (AI 목표 만들기 · AI 계획 초안 · Task 추가)
struct SettingsUsageCard: View {
    @State private var vm: SettingsUsageViewModel

    init() { _vm = State(initialValue: SettingsUsageViewModel()) }

    /// 미리보기용
    init(sample: AiUsage) { _vm = State(initialValue: SettingsUsageViewModel(sample: sample)) }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 8) {
                Text("오늘 사용량").font(.pt(16, .semibold)).foregroundStyle(Color.ink)
                if let usage = vm.usage {
                    Text(usage.pro ? "Pro 한도" : "무료 한도")
                        .font(.pt(11, .semibold))
                        .foregroundStyle(Color.ink2)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 2)
                        .background(Color.subtle, in: Capsule())
                }
                Spacer(minLength: 0)
            }
            Text("매일 0시(한국 시간)에 다시 채워져요")
                .font(.pt(12))
                .foregroundStyle(Color.ink3)
                .padding(.top, 2)

            if vm.isPending {
                ProgressView().tint(Color.ink3).frame(maxWidth: .infinity, minHeight: 72)
            } else {
                VStack(alignment: .leading, spacing: 14) {
                    ForEach(vm.usage?.items ?? []) { item in
                        SettingsUsageRow(item: item)
                    }
                }
                .padding(.top, 14)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.surface, in: RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.line, lineWidth: 1))
        .reloadOn([.usage]) { await vm.load() }
    }
}

private struct SettingsUsageRow: View {
    let item: UsageItem

    private var ratio: Double {
        guard let limit = item.limit, limit > 0 else { return 0 }
        return min(Double(item.used) / Double(limit), 1)
    }
    private var barColor: Color {
        ratio >= 1 ? Color.danger : (ratio >= 0.7 ? Color.warning : Color.brand)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(item.label).font(.pt(14, .semibold)).foregroundStyle(Color.ink)
                    if !item.description.isEmpty {
                        Text(item.description).font(.pt(12)).foregroundStyle(Color.ink3).lineLimit(2)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                remaining
            }
            if item.limit != nil {
                ProgressBar(value: ratio, color: barColor, height: 6)
            }
        }
    }

    @ViewBuilder private var remaining: some View {
        if let limit = item.limit {
            let left = item.remaining ?? 0
            (Text("\(left)").font(.ptMono(13, .bold)).foregroundColor(left == 0 ? Color.danger : Color.ink)
             + Text(" / \(limit)회 남음").font(.ptMono(13)).foregroundColor(Color.ink3))
                .fixedSize()
        } else {
            Text("제한 없음").font(.pt(13)).foregroundStyle(Color.ink3)
        }
    }
}

#Preview {
    ScrollView {
        VStack(spacing: 16) {
            SettingsThemePicker().card()
            SettingsUsageCard(sample: AiUsage(pro: false, resetsAt: "2026-10-10T00:00:00", items: [
                UsageItem(key: "GOAL_SESSION", label: "AI 목표 만들기", description: "목표를 대화로 설계해요", used: 2, limit: 3, remaining: 1),
                UsageItem(key: "GOAL_DRAFT", label: "AI 계획 초안", description: "목표마다 계획 초안을 만들어요", used: 3, limit: 3, remaining: 0),
                UsageItem(key: "TASK_PER_DAY", label: "Task 추가", description: "하루에 추가할 수 있는 Task", used: 1, limit: nil, remaining: nil),
            ], session: nil))
        }
        .padding(16)
    }
    .canvasBackground()
}
