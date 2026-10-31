// Features/Pattern/PatternScreen.swift
//
// 웹 app/(main)/patterns/page.tsx · features/pattern/ui/PatternView.tsx · ShareButton.tsx
// "나의 패턴" 탭 루트 화면.
//
//  - 기간: 최근 4주 / 전체 (웹 ?period= 대신 화면 상태)
//  - 기간을 바꾸는 동안에도 이전 리포트를 흐리게 보여줘요 (웹 placeholderData)
//  - 탭에 들어올 때마다 다시 물어봐요 (웹 staleTime 0 — 방금 완료한 일이 바로 반영되게)
//  - "리포트 공유": 웹은 화면을 PNG 로 내려받지만, iOS 는 ImageRenderer 로 이미지를 그려 공유 시트로 넘겨요

import SwiftUI
import UIKit

// MARK: - 상태

@MainActor
@Observable
final class PatternViewModel {
    var period: PatternPeriod = .RECENT_4W
    /// 마지막으로 받은 리포트 (기간을 바꾸는 동안에는 이전 기간 리포트가 남아 있어요)
    var report: PatternReport?
    var isFetching = false
    var loadError: String?
    var recalculating = false
    private let isPreview: Bool

    // MARK: Amplitude
    /// report_view 를 보낸 기간 (같은 기간이면 새로고침 · 다시 계산에 다시 안 보내요)
    private var reportedPeriod: PatternPeriod?
    private var suggestionKey: String?
    private var lastErrorStatus: Int?
    private var scrollSent: Set<Int> = []

    init(preview: PatternReport? = nil) {
        report = preview
        isPreview = preview != nil
    }

    func load() async {
        if isPreview { return }
        isFetching = true
        defer { isFetching = false }
        let asked = period
        do {
            let r = try await PatternAPI.report(period)
            report = r
            loadError = nil
            if let r, asked == period { trackReport(r) }
        } catch is CancellationError {
            // 탭을 떠나면 취소돼요 — 다음에 들어올 때 다시 불러요
        } catch {
            loadError = errorMessage(error)
            lastErrorStatus = PT.status(error)
            PT.track("pattern.load_fail", ["period": period.rawValue, "error_status": lastErrorStatus])
        }
    }

    /// "지금 다시 계산" — 캐시를 무시하고 새로 계산 (AI 문장 다듬기는 하루 한 번)
    func recalculate() async {
        if isPreview { return }
        recalculating = true
        defer { recalculating = false }
        PT.track("pattern.recalc_click", ["period": period.rawValue, "minutes_since_update": report.flatMap { PT.minutesSince($0.generatedAt) }])
        let prevVersion = report?.dataVersion
        do {
            let next = try await PatternAPI.report(period, refresh: true)
            if let r = next { report = r }
            loadError = nil
            let nextVersion = next?.dataVersion
            PT.track("pattern.recalc_result", [
                "period": period.rawValue, "result": "success",
                "data_changed": (prevVersion != nil && nextVersion != nil) ? prevVersion != nextVersion : nil,
            ])
            Toast.success("최신 기록으로 다시 계산했어요. (AI 문장 다듬기는 하루 한 번)")
        } catch is CancellationError {
        } catch {
            PT.track("pattern.recalc_result", ["period": period.rawValue, "result": "fail", "error_status": PT.status(error)])
            Toast.error(errorMessage(error, fallback: "다시 계산하지 못했어요."))
        }
    }

    // MARK: Amplitude

    /// 리포트를 그린 직후 — 기간마다 한 번 (+ 잠김 · 제안 바)
    private func trackReport(_ r: PatternReport) {
        let p = period.rawValue
        let items = r.evaluatedCount > 0 ? (r.suggestion?.items ?? []) : []
        if reportedPeriod != period {
            reportedPeriod = period
            scrollSent = []
            let styleReady = !(r.style?.title ?? "").isEmpty
            PT.track("pattern.report_view", [
                "period": p,
                "range_days": r.range.days,
                "evaluated_count": r.evaluatedCount,
                "style_ready": styleReady,
                "ai_status": r.aiStatus.rawValue,
                "empty_sections": PT.emptySections(r),
                "has_suggestion": !items.isEmpty,
                "minutes_since_update": PT.minutesSince(r.generatedAt),
            ])
            if !styleReady {
                // [UX] 잠긴 스타일 카드 — 얼마나 남은 상태에서 이탈하는지
                PT.track("pattern.locked_view", [
                    "period": p, "evaluated_count": r.evaluatedCount,
                    "remaining_tasks": r.style?.progress?.remainingTasks, "remaining_days": r.style?.progress?.remainingDays,
                ])
            }
        }
        // 제안 바 — 기간이나 항목이 바뀌면 다시
        let key = items.isEmpty ? nil : "\(p)|" + items.map(\.key).joined(separator: ",")
        if let key, key != suggestionKey, let s = r.suggestion {
            PT.track("pattern.suggestion_view", [
                "period": p, "item_count": items.count,
                "suggestion_types": PT.suggestionTypes(items), "expected_gain": s.expectedGain,
            ])
        }
        suggestionKey = key
    }

    /// 스크롤 깊이 25 · 50 · 75 · 100% — 기간마다 한 번 (리포트가 보일 때만)
    func scrolled(offset: CGFloat, content: CGFloat, viewport: CGFloat) {
        guard let r = report, loadError == nil, r.evaluatedCount > 0 else { return }
        let max = content - viewport
        guard max > 0 else { return }
        let pct = offset / max * 100
        for mark in [25, 50, 75, 100] where pct >= CGFloat(mark - 1) && !scrollSent.contains(mark) {
            scrollSent.insert(mark)
            PT.track("pattern.scroll_depth", [
                "scroll_percent": mark, "period": period.rawValue, "has_suggestion": !(r.suggestion?.items ?? []).isEmpty,
            ])
        }
    }

    func trackRetry() {
        PT.track("pattern.retry_click", ["period": period.rawValue, "error_status": lastErrorStatus])
    }

    func trackPullRefresh() {
        PT.track("pattern.pull_refresh", ["period": period.rawValue, "minutes_since_update": report.flatMap { PT.minutesSince($0.generatedAt) }])
    }
}

/// 공유 시트에 넘길 이미지
private struct PatternShareImage: Identifiable {
    let id = UUID()
    let image: UIImage
    let fileName: String
}

// MARK: - 화면
//
// 모바일 배치
//  ┌ 나의 패턴            [↻] [공유]  ┐
//  │ [ 최근 4주 | 전체 ]  (꽉 찬 폭)   │
//  │ 10.01 – 10.28 · 업데이트 13:42    │
//  ├ AI 실행 스타일 (검은 카드)         ┤
//  ├ 섹션 칩 (위에 붙음): 요약 · 시간대 · 요일 · 흐름 · 시간 사용 · 블록 · 루틴 · 습관 ┤
//  └ 제안 → 섹션 카드들                 ┘
// 칩을 누르면 그 섹션으로 스크롤하고, 스크롤하면 지금 보고 있는 섹션 칩이 켜져요.

enum PatternSection: String, CaseIterable, Identifiable {
    case summary, heatmap, weekday, weekly, usage, block, routines, habits
    var id: String { rawValue }

    var label: String {
        switch self {
        case .summary: return "요약"
        case .heatmap: return "시간대"
        case .weekday: return "요일"
        case .weekly: return "흐름"
        case .usage: return "시간 사용"
        case .block: return "블록"
        case .routines: return "루틴"
        case .habits: return "습관"
        }
    }

    var icon: String {
        switch self {
        case .summary: return "square.grid.2x2"
        case .heatmap: return "clock"
        case .weekday: return "calendar"
        case .weekly: return "chart.line.uptrend.xyaxis"
        case .usage: return "hourglass"
        case .block: return "checkmark.rectangle.stack"
        case .routines: return "repeat"
        case .habits: return "list.bullet.clipboard"
        }
    }
}

private enum PatternGeo {
    static let space = "pattern-scroll"
    /// 위에 붙는 칩 바 높이 — 섹션으로 스크롤할 때 칩 바 아래에 오도록
    static let navHeight: CGFloat = 52
    static let navKey = "_nav"
    /// 스크롤 깊이용 — 내용 맨 위 · 내용 높이 · 보이는 높이
    static let contentTopKey = "_contentTop"
    static let contentHeightKey = "_contentHeight"
    static let viewportKey = "_viewport"
}

/// 섹션마다 "스크롤 영역 맨 위에서 얼마나 떨어졌는지"
private struct PatternSectionTopKey: PreferenceKey {
    static let defaultValue: [String: CGFloat] = [:]
    static func reduce(value: inout [String: CGFloat], nextValue: () -> [String: CGFloat]) {
        value.merge(nextValue(), uniquingKeysWith: { $1 })
    }
}

struct PatternScreen: View {
    @State private var vm: PatternViewModel
    @State private var shareItem: PatternShareImage?
    @State private var current: PatternSection = .summary
    /// 칩을 눌러 스크롤하는 동안에는 스크롤 위치로 칩을 바꾸지 않아요 (깜빡임 방지)
    @State private var jumping = false
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.displayScale) private var displayScale

    init() {
        _vm = State(initialValue: PatternViewModel())
    }

    /// 미리보기용 (네트워크 없이)
    init(preview report: PatternReport) {
        _vm = State(initialValue: PatternViewModel(preview: report))
    }

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 0, pinnedViews: [.sectionHeaders]) {
                    header
                        .pagePadding()
                        .padding(.top, 8)
                        .padding(.bottom, 16)
                    if let r = vm.report, vm.loadError == nil, r.evaluatedCount > 0 {
                        PatternStyleCard(style: r.style)
                            .pagePadding()
                            .padding(.bottom, 12)
                        Section {
                            sections(r)
                                .pagePadding()
                                .padding(.top, 12)
                                .padding(.bottom, 32)
                                .opacity(vm.isFetching ? 0.7 : 1)
                                .animation(.easeOut(duration: 0.15), value: vm.isFetching)
                        } header: {
                            PatternSectionNav(current: current) { s in jump(to: s, proxy) }
                                .background(GeometryReader { g in
                                    Color.clear.preference(key: PatternSectionTopKey.self,
                                                           value: [PatternGeo.navKey: g.frame(in: .named(PatternGeo.space)).maxY])
                                })
                        }
                    } else {
                        content
                            .pagePadding()
                            .padding(.bottom, 32)
                    }
                }
                .background(GeometryReader { g in
                    let f = g.frame(in: .named(PatternGeo.space))
                    Color.clear.preference(key: PatternSectionTopKey.self,
                                           value: [PatternGeo.contentTopKey: f.minY, PatternGeo.contentHeightKey: f.height])
                })
            }
            .coordinateSpace(name: PatternGeo.space)
            // 스크롤 영역 높이 (스크롤 깊이 계산)
            .background(GeometryReader { g in
                Color.clear.preference(key: PatternSectionTopKey.self, value: [PatternGeo.viewportKey: g.size.height])
            })
            .onPreferenceChange(PatternSectionTopKey.self) { tops in
                if let minY = tops[PatternGeo.contentTopKey], let h = tops[PatternGeo.contentHeightKey], let vh = tops[PatternGeo.viewportKey] {
                    vm.scrolled(offset: -minY, content: h, viewport: vh)
                }
                guard !jumping else { return }
                // 칩 바 바로 아래(약간 여유)를 지난 마지막 섹션 = 지금 보는 섹션
                let line = (tops[PatternGeo.navKey] ?? PatternGeo.navHeight) + 40
                let passed = PatternSection.allCases.filter { (tops[$0.rawValue] ?? .infinity) <= line }
                let next = passed.last ?? .summary
                if next != current { current = next }
            }
        }
        .canvasBackground()
        .toolbar(.hidden, for: .navigationBar)
        // 기록이 바뀌는 곳(캘린더 · 실제 한 일 · 목표)이 낡으면 같이 다시 물어봐요
        .reloadOn([.pattern, .timetable, .activities, .goals], id: vm.period) { await vm.load() }
        .refreshable {
            vm.trackPullRefresh()
            await vm.load()
        }
        // 탭이 보일 때마다 (다른 탭에 갔다 오면 다시)
        .onAppear {
            PT.period = vm.period
            PT.resetVisit()
            PT.track("pattern.pageview", ["period": vm.period.rawValue, "entry_source": PT.pendingEntry ?? "tab_bar"])
            PT.pendingEntry = nil
        }
        .onChange(of: vm.period) { old, new in
            PT.period = new
            PT.track("pattern.period_change", ["period": new.rawValue, "from_period": old.rawValue])
        }
        .sheet(item: $shareItem) { item in
            PatternShareSheet(item: item)
        }
    }

    private func jump(to s: PatternSection, _ proxy: ScrollViewProxy) {
        UISelectionFeedbackGenerator().selectionChanged()
        PT.track("pattern.section_nav_click", ["section": s.analyticsKey, "period": vm.period.rawValue])
        jumping = true
        current = s
        withAnimation(.easeInOut(duration: 0.35)) { proxy.scrollTo("pattern-\(s.rawValue)", anchor: .top) }
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 500_000_000)
            jumping = false
        }
    }

    // MARK: 머리말

    private var header: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .center, spacing: 4) {
                Text("나의 패턴").font(.pt(20, .semibold)).foregroundStyle(Color.ink)
                Spacer(minLength: 0)
                if vm.report != nil {
                    recalcButton
                }
                if let r = vm.report, r.evaluatedCount > 0 {
                    IconButton(systemName: "square.and.arrow.up", size: 17, color: .ink2, label: "리포트 공유") {
                        share(r)
                    }
                }
            }
            SegmentPicker(selection: $vm.period,
                          options: PatternPeriod.allCases.map { (value: $0, label: $0.label) },
                          fullWidth: true)
                .accessibilityLabel("기간")
            if let r = vm.report {
                // 언제 계산한 결과인지 — 방금 한 일이 반영됐는지 알 수 있게
                VStack(alignment: .leading, spacing: 2) {
                    Text(PatternFormat.rangeLabel(r))
                    Text("업데이트 \(PatternFormat.updatedLabel(r.generatedAt)) · 오늘 완료한 일까지 반영 · 당겨서 새로고침")
                }
                .font(.pt(12))
                .foregroundStyle(Color.ink3)
            }
        }
    }

    /// "지금 다시 계산" — 아이콘만 (돌아가는 동안 회전)
    private var recalcButton: some View {
        Button {
            Task { await vm.recalculate() }
        } label: {
            Image(systemName: "arrow.clockwise")
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(Color.ink2)
                .rotationEffect(.degrees(vm.recalculating ? 360 : 0))
                .animation(vm.recalculating ? .linear(duration: 0.8).repeatForever(autoreverses: false) : .default,
                           value: vm.recalculating)
                .frame(width: 36, height: 36)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(vm.recalculating)
        .opacity(vm.recalculating ? 0.5 : 1)
        .accessibilityLabel("지금 다시 계산")
    }

    // MARK: 섹션들

    private func sections(_ r: PatternReport) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            // 바로 해 볼 수 있는 제안은 맨 위에
            PatternSuggestionBarView(suggestion: r.suggestion)
            anchored(.summary) { PatternSummaryTiles(summary: r.summary) }
            anchored(.heatmap) { PatternHeatmapCard(heatmap: r.heatmap) }
            anchored(.weekday) { PatternWeekdayBars(weekday: r.weekday) }
            anchored(.weekly) { PatternWeeklyTrend(weekly: r.weekly) }
            anchored(.usage) { PatternTimeUsageCard(usage: r.timeUsage) }
            anchored(.block) { PatternBlockKeepingCard(block: r.blockKeeping) }
            anchored(.routines) { PatternRoutineLists(routines: r.routines) }
            anchored(.habits) { PatternTaskHabitsCard(habits: r.taskHabits) }
            // 숫자는 기록이 바뀔 때마다 바로, AI 문장 다듬기는 하루 한 번 (AI 비용 때문에)
            Text(r.aiStatus == .AI
                 ? "문장은 AI가 다듬었고, 숫자는 실제 기록으로 계산했어요."
                 : "숫자는 실제 기록으로 바로 계산했어요. AI 문장 다듬기는 하루 한 번 해요.")
                .font(.pt(12))
                .foregroundStyle(Color.ink4)
                .frame(maxWidth: .infinity, alignment: .center)
                .multilineTextAlignment(.center)
                .padding(.top, 4)
        }
    }

    /// 섹션 위에 보이지 않는 표식을 칩 바 높이만큼 위에 둬요 → 칩으로 스크롤하면 섹션이 칩 바 바로 아래에 와요
    private func anchored<C: View>(_ s: PatternSection, @ViewBuilder _ content: () -> C) -> some View {
        content()
            .background(alignment: .top) {
                // alignmentGuide 는 실제 자리(레이아웃)를 옮겨서 scrollTo 가 이 위치를 기준으로 맞춰요 (offset 은 보이는 자리만 옮겨요)
                Color.clear
                    .frame(height: 1)
                    .alignmentGuide(VerticalAlignment.top) { _ in PatternGeo.navHeight + 12 }
                    .id("pattern-\(s.rawValue)")
                    .allowsHitTesting(false)
            }
            .background(GeometryReader { g in
                Color.clear.preference(key: PatternSectionTopKey.self,
                                       value: [s.rawValue: g.frame(in: .named(PatternGeo.space)).minY])
            })
    }

    // MARK: 기록 없음 · 오류 · 불러오는 중

    @ViewBuilder
    private var content: some View {
        if let message = vm.loadError {
            errorCard(message)
        } else if let r = vm.report {
            emptyState(r)
        } else {
            LoadingView(minHeight: 420)
        }
    }

    private func errorCard(_ message: String) -> some View {
        VStack(spacing: 6) {
            Text("패턴을 불러오지 못했어요").font(.pt(16, .semibold)).foregroundStyle(Color.ink)
            Text(message).font(.pt(14)).foregroundStyle(Color.ink3).multilineTextAlignment(.center)
            NButton("다시 시도", variant: .secondary) {
                vm.trackRetry()
                Task { await vm.load() }
            }
                .padding(.top, 10)
        }
        .padding(32)
        .frame(maxWidth: .infinity, minHeight: 280)
        .card()
    }

    /// 기록이 하나도 없어도 빈 화면 대신 "얼마나 하면 열리는지"를 보여줘요
    private func emptyState(_ r: PatternReport) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            PatternStyleCard(style: r.style)
            VStack(alignment: .leading, spacing: 12) {
                Text("Task를 완료하면 바로 패턴이 쌓이기 시작해요. 시간대별 실행률·요일별 패턴은 기록이 조금 더 모이면 열려요.")
                    .font(.pt(14))
                    .foregroundStyle(Color.ink2)
                    .fixedSize(horizontal: false, vertical: true)
                NButton("캘린더로 가기", size: .md, fullWidth: true) {
                    PT.track("pattern.empty_cta_click", ["period": vm.period.rawValue])
                    AppRouter.shared.openCalendar()
                }
            }
            .padding(20)
            .frame(maxWidth: .infinity, alignment: .leading)
            .card()
        }
    }

    // MARK: 공유

    /// 리포트 영역(머리말 + 카드들)을 이미지로 그려요. 기간 선택 · 버튼 · 제안 바는 빼요 (웹 data-share-exclude)
    @MainActor
    private func share(_ r: PatternReport) {
        PT.track("pattern.share_click", [
            "period": vm.period.rawValue, "evaluated_count": r.evaluatedCount, "style_ready": !(r.style?.title ?? "").isEmpty,
        ])
        let content = PatternShareContent(report: r)
            .environment(\.colorScheme, colorScheme)
        let renderer = ImageRenderer(content: content)
        renderer.scale = displayScale
        renderer.proposedSize = ProposedViewSize(width: PatternShareContent.width, height: nil)
        if let image = renderer.uiImage {
            shareItem = PatternShareImage(image: image, fileName: "naleul-pattern-\(r.range.end).png")
            PT.track("pattern.share_result", ["period": vm.period.rawValue, "result": "success"])
        } else {
            PT.track("pattern.share_result", ["period": vm.period.rawValue, "result": "fail"])
            Toast.error("이미지를 만들지 못했어요. 다시 시도해 주세요.")
        }
    }
}

// MARK: - 섹션 칩 바 (위에 붙음 · 가로 스크롤)

private struct PatternSectionNav: View {
    let current: PatternSection
    let onTap: (PatternSection) -> Void

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(PatternSection.allCases) { s in
                        let on = s == current
                        Button { onTap(s) } label: {
                            HStack(spacing: 5) {
                                NSymbol(s.icon, size: 11, weight: .semibold)
                                Text(s.label)
                            }
                            .font(.pt(13, on ? .bold : .medium))
                            .foregroundStyle(on ? Color.actionPrimaryFg : Color.ink2)
                            .padding(.horizontal, 12)
                            .frame(height: 34)
                            .background(on ? Color.actionPrimary : Color.surface, in: Capsule())
                            .overlay(Capsule().stroke(on ? Color.clear : Color.line, lineWidth: 1))
                            .contentShape(Capsule())
                        }
                        .buttonStyle(.plain)
                        .id(s.rawValue)
                        .accessibilityAddTraits(on ? .isSelected : [])
                    }
                }
                .padding(.horizontal, 16)
            }
            .onChange(of: current) { _, c in
                withAnimation(.easeOut(duration: 0.2)) { proxy.scrollTo(c.rawValue, anchor: .center) }
            }
        }
        .frame(height: PatternGeo.navHeight)
        .background(Color.canvas.opacity(0.96))
        .overlay(alignment: .bottom) { Rectangle().fill(Color.borderSoft).frame(height: 0.5) }
        .animation(.easeOut(duration: 0.15), value: current)
    }
}

// MARK: - 리포트 카드 묶음 (공유 이미지가 써요)

struct PatternReportBody: View {
    let report: PatternReport

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            PatternStyleCard(style: report.style)
            PatternSummaryTiles(summary: report.summary)
            PatternHeatmapCard(heatmap: report.heatmap)
            PatternWeekdayBars(weekday: report.weekday)
            PatternWeeklyTrend(weekly: report.weekly)
            PatternTimeUsageCard(usage: report.timeUsage)
            PatternBlockKeepingCard(block: report.blockKeeping)
            PatternRoutineLists(routines: report.routines)
            PatternTaskHabitsCard(habits: report.taskHabits)
        }
    }
}

/// 공유 이미지 내용: 제목 · 기간 문구 + 카드들, 가장자리에 여백 (웹 PAD 32)
private struct PatternShareContent: View {
    static let width: CGFloat = 430
    let report: PatternReport

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            VStack(alignment: .leading, spacing: 4) {
                Text("나의 패턴").font(.pt(20, .semibold)).foregroundStyle(Color.ink)
                Text(PatternFormat.rangeLabel(report)).font(.pt(14)).foregroundStyle(Color.ink3)
            }
            PatternReportBody(report: report)
        }
        .padding(24)
        .frame(width: Self.width)
        .background(Color.canvas)
    }
}

/// 만든 이미지를 미리 보여주고 공유 시트(사진 저장 · 메신저 등)로 넘겨요
private struct PatternShareSheet: View {
    let item: PatternShareImage

    var body: some View {
        let image = Image(uiImage: item.image)
        SheetScaffold(title: "리포트 공유", description: "이미지로 저장하거나 공유할 수 있어요.", detents: [.large]) {
            image
                .resizable()
                .scaledToFit()
                .clipShape(RoundedRectangle(cornerRadius: Radius.xl))
                .overlay(RoundedRectangle(cornerRadius: Radius.xl).stroke(Color.line, lineWidth: 1))
        } footer: {
            ShareLink(item: image, preview: SharePreview(item.fileName, image: image)) {
                HStack(spacing: 8) {
                    NSymbol("square.and.arrow.up", size: 14, weight: .semibold)
                    Text("공유하기")
                }
            }
            .buttonStyle(.n(.primary, .md))
        }
    }
}

#Preview("리포트") {
    NavigationStack {
        PatternScreen(preview: PatternSampleData.report)
    }
}

#Preview("기록 없음") {
    NavigationStack {
        PatternScreen(preview: PatternSampleData.emptyReport)
    }
}
