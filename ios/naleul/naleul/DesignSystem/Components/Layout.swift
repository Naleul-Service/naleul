// DesignSystem/Components/Layout.swift
//
// 화면 공통 틀: 페이지 제목 · 로딩 · 빈 화면 · 에러 · 줄바꿈 배치(FlowLayout) · 진행 막대.
//
//   PageHeader(title: "목표", description: "…") { NButton("추가") {} }
//   LoadState(phase: vm.phase) { data in ... } — 로딩/에러 처리를 한 번에
//   FlowLayout(spacing: 8) { ForEach(...) { Chip(...) } }

import SwiftUI
import UIKit

// MARK: - 페이지 제목 (웹 PageHeader)

struct PageHeader<Actions: View>: View {
    var breadcrumb: String? = nil
    let title: String
    var description: String? = nil
    @ViewBuilder var actions: () -> Actions

    init(breadcrumb: String? = nil, title: String, description: String? = nil,
         @ViewBuilder actions: @escaping () -> Actions) {
        self.breadcrumb = breadcrumb
        self.title = title
        self.description = description
        self.actions = actions
    }

    var body: some View {
        HStack(alignment: .top, spacing: 16) {
            VStack(alignment: .leading, spacing: 4) {
                if let breadcrumb { Text(breadcrumb).font(.captionMD).foregroundStyle(Color.textTertiary) }
                Text(title).textStyle(.h2).foregroundStyle(Color.textPrimary)
                if let description {
                    Text(description).textStyle(.bodyMD).foregroundStyle(Color.textSecondary).padding(.top, 2)
                }
            }
            Spacer(minLength: 0)
            HStack(spacing: 8) { actions() }
        }
    }
}

extension PageHeader where Actions == EmptyView {
    init(breadcrumb: String? = nil, title: String, description: String? = nil) {
        self.init(breadcrumb: breadcrumb, title: title, description: description) { EmptyView() }
    }
}

// MARK: - 불러오기 상태

/// 화면 데이터 상태. ViewModel 에서 var phase: LoadPhase<GoalCategory> = .loading 처럼 써요
enum LoadPhase<Value> {
    case loading
    case loaded(Value)
    case failed(String)

    var value: Value? {
        if case .loaded(let v) = self { return v }
        return nil
    }
    var isLoading: Bool {
        if case .loading = self { return true }
        return false
    }
}

/// phase 에 따라 로딩 · 에러(다시 시도) · 내용을 보여줘요
struct LoadState<Value, Content: View>: View {
    let phase: LoadPhase<Value>
    var retry: (() async -> Void)? = nil
    @ViewBuilder var content: (Value) -> Content

    init(phase: LoadPhase<Value>, retry: (() async -> Void)? = nil, @ViewBuilder content: @escaping (Value) -> Content) {
        self.phase = phase
        self.retry = retry
        self.content = content
    }

    var body: some View {
        switch phase {
        case .loading:
            LoadingView()
        case .failed(let message):
            ErrorView(message: message, retry: retry)
        case .loaded(let value):
            content(value)
        }
    }
}

struct LoadingView: View {
    var minHeight: CGFloat = 240
    var body: some View {
        ProgressView()
            .tint(Color.textTertiary)
            .frame(maxWidth: .infinity, minHeight: minHeight)
    }
}

struct ErrorView: View {
    let message: String
    var retry: (() async -> Void)? = nil

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: "exclamationmark.circle")
                .font(.system(size: 22))
                .foregroundStyle(Color.textSecondary)
                .frame(width: 48, height: 48)
                .background(Color.bgSubtle, in: Circle())
            Text(message).textStyle(.bodyMD).foregroundStyle(Color.textSecondary).multilineTextAlignment(.center)
            if let retry {
                NButton("다시 시도", variant: .outline, size: .sm) { Task { await retry() } }
            }
        }
        .padding(24)
        .frame(maxWidth: .infinity, minHeight: 240)
    }
}

/// EmptyState (카드 없이): padding 32 · 간격 12 · 가운데 · 아이콘 원 48(bg/subtle, 아이콘 24)
/// 제목 Body/LG·Medium · 설명 Body/SM text/secondary · 버튼(선택)은 Outline SM 권장
struct EmptyStateView<Action: View>: View {
    var icon: String? = nil
    let title: String
    var description: String? = nil
    @ViewBuilder var action: () -> Action

    init(icon: String? = nil, title: String, description: String? = nil, @ViewBuilder action: @escaping () -> Action) {
        self.icon = icon
        self.title = title
        self.description = description
        self.action = action
    }

    var body: some View {
        VStack(spacing: 12) {
            if let icon {
                NSymbol(icon, size: 20)
                    .foregroundStyle(Color.textSecondary)
                    .frame(width: 48, height: 48)
                    .background(Color.bgSubtle, in: Circle())
            }
            VStack(spacing: 4) {
                Text(title).textStyle(.bodyLGMedium).foregroundStyle(Color.textPrimary).multilineTextAlignment(.center)
                if let description {
                    Text(description).textStyle(.bodySM).foregroundStyle(Color.textSecondary).multilineTextAlignment(.center)
                }
            }
            action()
        }
        .padding(32)
        .frame(maxWidth: .infinity)
    }
}

extension EmptyStateView where Action == EmptyView {
    init(icon: String? = nil, title: String, description: String? = nil) {
        self.init(icon: icon, title: title, description: description) { EmptyView() }
    }
}

/// 비어 있을 때 카드 (EmptyState + Card)
struct EmptyStateCard<Action: View>: View {
    var icon: String? = nil
    let title: String
    var description: String? = nil
    @ViewBuilder var action: () -> Action

    init(icon: String? = nil, title: String, description: String? = nil, @ViewBuilder action: @escaping () -> Action) {
        self.icon = icon
        self.title = title
        self.description = description
        self.action = action
    }

    var body: some View {
        EmptyStateView(icon: icon, title: title, description: description) { action() }
            .frame(maxWidth: .infinity, minHeight: 220)
            .card()
    }
}

extension EmptyStateCard where Action == EmptyView {
    init(icon: String? = nil, title: String, description: String? = nil) {
        self.init(icon: icon, title: title, description: description) { EmptyView() }
    }
}

// MARK: - 진행 막대 (SM h4 · MD h6 · track progress/track · fill progress/fill 밝은 민트)

struct ProgressBar: View {
    /// 0~1 (넘어가면 잘라요)
    let value: Double
    var color: Color = .progressFill
    var track: Color = .progressTrack
    var height: CGFloat = 6

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(track)
                Capsule().fill(color)
                    .frame(width: geo.size.width * min(max(value, 0), 1))
            }
        }
        .frame(height: height)
        .animation(.easeOut(duration: 0.25), value: value)
    }
}

// MARK: - 줄바꿈 배치 (웹 flex-wrap gap-2)

struct FlowLayout: Layout {
    var spacing: CGFloat = 8
    var lineSpacing: CGFloat? = nil

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let rows = arrange(proposal: proposal, subviews: subviews)
        let width = proposal.width ?? rows.map(\.width).max() ?? 0
        let height = rows.reduce(0) { $0 + $1.height } + CGFloat(max(rows.count - 1, 0)) * (lineSpacing ?? spacing)
        return CGSize(width: width, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let rows = arrange(proposal: ProposedViewSize(width: bounds.width, height: nil), subviews: subviews)
        var y = bounds.minY
        for row in rows {
            var x = bounds.minX
            for i in row.indices {
                let size = subviews[i].sizeThatFits(.unspecified)
                subviews[i].place(at: CGPoint(x: x, y: y + (row.height - size.height) / 2), proposal: ProposedViewSize(size))
                x += size.width + spacing
            }
            y += row.height + (lineSpacing ?? spacing)
        }
    }

    private struct Row {
        var indices: [Int] = []
        var width: CGFloat = 0
        var height: CGFloat = 0
    }

    private func arrange(proposal: ProposedViewSize, subviews: Subviews) -> [Row] {
        let maxWidth = proposal.width ?? .infinity
        var rows: [Row] = []
        var current = Row()
        for i in subviews.indices {
            let size = subviews[i].sizeThatFits(.unspecified)
            let extra = current.indices.isEmpty ? size.width : current.width + spacing + size.width
            if extra > maxWidth, !current.indices.isEmpty {
                rows.append(current)
                current = Row(indices: [i], width: size.width, height: size.height)
            } else {
                current.indices.append(i)
                current.width = extra
                current.height = max(current.height, size.height)
            }
        }
        if !current.indices.isEmpty { rows.append(current) }
        return rows
    }
}

// MARK: - 섹션 제목 (카드 밖 작은 회색 제목)

struct SectionTitle: View {
    let title: String
    var trailing: String? = nil

    init(_ title: String, trailing: String? = nil) {
        self.title = title
        self.trailing = trailing
    }

    var body: some View {
        HStack {
            Text(title).font(.labelMD).tracking(-0.26).foregroundStyle(Color.textSecondary)
            Spacer(minLength: 0)
            if let trailing { Text(trailing).font(.captionMD).foregroundStyle(Color.textTertiary) }
        }
    }
}

// MARK: - 화면 배경

extension View {
    /// 탭 화면 공통 배경 (bg-canvas)
    func canvasBackground() -> some View {
        background(Color.bgCanvas.ignoresSafeArea())
    }

    /// 페이지 좌우 여백 (모든 최상위 화면 공통 20pt)
    func pagePadding() -> some View {
        padding(.horizontal, PageLayout.horizontalPadding)
    }
}

/// 페이지 공통 치수
enum PageLayout {
    /// 페이지 좌우 여백
    static let horizontalPadding: CGFloat = 20
    /// 위 공통 헤더 높이 (MobileHeader)
    static let headerHeight: CGFloat = 48
}

// MARK: - 아래 탭바 (TabBar · mobile)
// bg/surface · 위 1px border/subtle · 기본 글자/아이콘 text/tertiary · 선택 text/brand · 라벨 Label/SM(12 SemiBold)
// 시스템 TabView 를 그대로 쓰고 UITabBarAppearance 로 색 · 글꼴만 맞춰요 (아이콘 상자 배경은 시스템 탭바에선 못 그려요)

enum NTabBarAppearance {
    @MainActor static func apply() {
        let normal = UIColor(hexString: "#7D8898")   // text/tertiary (라이트 · 다크 같음)
        let selected = UIColor { $0.userInterfaceStyle == .dark ? UIColor(hexString: "#A3D2E8") : UIColor(hexString: "#0D6B89") }
        let font = UIFont(name: "PretendardVariable-SemiBold", size: 11) ?? .systemFont(ofSize: 11, weight: .semibold)

        let appearance = UITabBarAppearance()
        appearance.configureWithOpaqueBackground()
        appearance.backgroundColor = UIColor { $0.userInterfaceStyle == .dark ? UIColor(hexString: "#1C232C") : UIColor(hexString: "#FFFFFF") }
        appearance.shadowColor = UIColor { $0.userInterfaceStyle == .dark ? UIColor(hexString: "#2F3845") : UIColor(hexString: "#D8DFE8") }
        for item in [appearance.stackedLayoutAppearance, appearance.inlineLayoutAppearance, appearance.compactInlineLayoutAppearance] {
            item.normal.iconColor = normal
            item.normal.titleTextAttributes = [.foregroundColor: normal, .font: font]
            item.selected.iconColor = selected
            item.selected.titleTextAttributes = [.foregroundColor: selected, .font: font]
        }
        UITabBar.appearance().standardAppearance = appearance
        UITabBar.appearance().scrollEdgeAppearance = appearance
    }
}

#Preview {
    ScrollView {
        VStack(alignment: .leading, spacing: 16) {
            PageHeader(title: "목표", description: "AI와 함께 세운 목표를 관리해요") {
                NButton("추가", size: .sm, icon: "plus") {}
            }
            ProgressBar(value: 0.62)
            FlowLayout {
                ForEach(["운동", "공부", "독서", "건강", "커리어", "취미"], id: \.self) { Chip($0) {} }
            }
            EmptyStateCard(icon: "target", title: "아직 목표가 없어요", description: "AI와 대화하며 첫 목표를 세워 보세요") {
                NButton("목표 만들기", variant: .outline, size: .sm) {}
            }
            ErrorView(message: "불러오지 못했어요.") {}
        }
        .padding(16)
    }
    .canvasBackground()
}

// MARK: - 제목 있는 카드 (웹 pattern/ui/Section.tsx)

/// Card + 제목(17 bold) + 오른쪽 보조. 나의 패턴 · 목표 상세 섹션에서 써요
struct SectionCard<Aside: View, Content: View>: View {
    let title: String
    @ViewBuilder var aside: () -> Aside
    @ViewBuilder var content: () -> Content

    init(_ title: String, @ViewBuilder aside: @escaping () -> Aside, @ViewBuilder content: @escaping () -> Content) {
        self.title = title
        self.aside = aside
        self.content = content
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(spacing: 12) {
                Text(title).textStyle(.h4).foregroundStyle(Color.textPrimary)
                Spacer(minLength: 0)
                aside().font(.captionMD).foregroundStyle(Color.textSecondary)
            }
            content()
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card()
    }
}

extension SectionCard where Aside == EmptyView {
    init(_ title: String, @ViewBuilder content: @escaping () -> Content) {
        self.init(title, aside: { EmptyView() }, content: content)
    }
}

/// 기록이 부족할 때 (웹 NotEnough)
struct NotEnoughView: View {
    var text = "기록이 쌓이면 보여드려요"
    var body: some View {
        Text(text)
            .textStyle(.bodyMD)
            .foregroundStyle(Color.textTertiary)
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity, minHeight: 120)
    }
}
