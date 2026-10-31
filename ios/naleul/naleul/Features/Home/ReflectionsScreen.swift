// Features/Home/ReflectionsScreen.swift
//
// 회고 모아보기 — 웹 features/reflection/ui/ReflectionsView.tsx (/reflections?goal=).
// 목표 상세 · 홈 · 업무형 보드에서 남긴 회고를 날짜별로 한곳에서 봐요 (GET /home/reflections).
//  - 목표형: 그날 수치 + 기분 + 한 줄 회고 (오늘 기록)
//  - 업무형: 회고 글
// 위의 목표 칩으로 한 목표만, 일간 · 주간 · 월간 탭과 ‹ › 로 기간을 골라 봐요.

import SwiftUI
import UIKit

/// 회고 모아보기 기간 (웹 ReflectionPeriod)
enum ReflectionPeriod: String, CaseIterable, Identifiable {
    /// 전체 — 기간 없이 최근 회고 (기본)
    case all
    case day, week, month
    var id: String { rawValue }
    var label: String {
        switch self {
        case .all: return "전체"
        case .day: return "일간"
        case .week: return "주간"
        case .month: return "월간"
        }
    }
    var currentLabel: String {
        switch self {
        case .all: return ""
        case .day: return "오늘"
        case .week: return "이번 주"
        case .month: return "이번 달"
        }
    }
}

@MainActor
@Observable
final class ReflectionsViewModel {
    var goalId: Int?
    /// 전체(기본) · 일간 · 주간 · 월간
    var period: ReflectionPeriod = .all
    /// 이 날짜가 든 기간을 봐요 (‹ › 로 옮겨요)
    var anchor: String = KST.today()
    var page: ReflectionFeedPage?
    var goals: [GoalCategory] = []
    var loading = false
    var failed = false

    // MARK: Amplitude
    /// 이 목록이 쓰인 화면 — reflections(회고 모아보기) · goal_detail(목표 상세 "회고" 탭)
    var surface = "reflections"
    /// 마지막으로 보낸 reflection_timeline.view 의 기간 · 목표 (같으면 다시 안 보내요)
    private(set) var viewKey = ""
    private var scrollSent: Set<Int> = []
    private var lastErrorStatus: Int?
    private var pageviewSent = false
    private var goalsLoaded = false
    private var unmatchedSent: Int?

    init(goalId: Int?) { self.goalId = goalId }

    /// 지금 보는 기간 [from, to] (양 끝 포함)
    var range: (from: String, to: String) {
        switch period {
        case .all:
            // 전체는 기간 없이 최근 회고 (API 기본 30일) — 요약 · 다시 불러오기 키용
            let today = KST.today()
            return (KST.addDays(today, -29), today)
        case .day: return (anchor, anchor)
        case .week:
            let from = KST.startOfWeek(anchor)
            return (from, KST.addDays(from, 6))
        case .month:
            let y = KST.yearOf(anchor), m = KST.monthOf(anchor)
            return (KST.ymd(year: y, month: m, day: 1), KST.ymd(year: y, month: m, day: KST.lastDayOfMonth(year: y, month: m)))
        }
    }

    /// 다시 불러올 때 쓰는 키 (목표 · 기간이 바뀌면 다시)
    var reloadId: String { "\(goalId ?? 0)-\(range.from)-\(range.to)" }
    var atLatest: Bool { range.to >= KST.today() }

    var title: String {
        let (from, to) = range
        let today = KST.today()
        switch period {
        case .all: return "최근 회고"
        case .day: return from == today ? "오늘 · \(KST.formatMonthDay(from))" : KST.formatMonthDay(from)
        case .month: return "\(KST.yearOf(from))년 \(KST.monthOf(from))월"
        case .week:
            let head = KST.startOfWeek(today) == from ? "이번 주 · " : ""
            let toText = KST.monthOf(to) != KST.monthOf(from) ? "\(KST.monthOf(to))월 \(KST.dayOfMonth(to))일" : "\(KST.dayOfMonth(to))일"
            return "\(head)\(KST.monthOf(from))월 \(KST.dayOfMonth(from))일 – \(toText)"
        }
    }

    /// 이 기간의 날 수 (이번 기간이면 오늘까지)
    var totalDays: Int {
        let (from, to) = range
        let end = atLatest ? min(to, KST.today()) : to
        return max(KST.diffDays(from, end) + 1, 1)
    }

    var entries: [ReflectionEntry] { page?.entries ?? [] }

    struct DayGroup: Identifiable {
        var id: String { date }
        let date: String
        var items: [ReflectionEntry]
    }

    /// 날짜별로 묶기 (이미 최근 날짜 순으로 와요)
    var groups: [DayGroup] {
        var out: [DayGroup] = []
        for e in entries {
            if let last = out.last, last.date == e.date {
                out[out.count - 1].items.append(e)
            } else {
                out.append(DayGroup(date: e.date, items: [e]))
            }
        }
        return out
    }

    /// 이 기간에 가장 많이 돌아본 목표 (목표가 2개 이상일 때)
    var topGoal: (title: String, days: Int)? {
        var byGoal: [Int: (title: String, days: Set<String>)] = [:]
        for e in entries {
            var g = byGoal[e.goalCategoryId] ?? (e.goalTitle, [])
            g.days.insert(e.date)
            byGoal[e.goalCategoryId] = g
        }
        guard byGoal.count >= 2, let top = byGoal.values.max(by: { $0.days.count < $1.days.count }) else { return nil }
        return (top.title, top.days.count)
    }

    /// 회고를 남길 수 있는 목표 (기본 생활 습관 · 임시 목표 빼고)
    var filterGoals: [GoalCategory] {
        goals.filter { $0.goalMode != .HABIT && $0.temporary != true }
    }

    func load() async {
        if page == nil { loading = true }
        failed = false
        let (from, to) = range
        do {
            // 전체: 기간 없이 (before = nil · 기본 일수) / 일간 · 주간 · 월간: 그 기간만
            let before: String? = period == .all ? nil : KST.addDays(to, 1)
            let days = period == .all ? 30 : KST.diffDays(from, to) + 1
            async let feed = HomeAPI.reflections(before: before, days: days, goalId: goalId)
            async let list = GoalAPI.goals()
            let result = try await feed
            if goals.isEmpty {
                do {
                    goals = try await list
                    goalsLoaded = true
                } catch {
                    // [UX] 칩이 '전체'만 남고 안내가 없어요
                    if surface == "reflections" { RF.track("reflections.goals_load_fail", ["error_status": RF.status(error)]) }
                }
            }
            trackPageviewIfNeeded()
            // 불러오는 사이 기간을 또 바꿨으면 버려요
            if range.from == from && range.to == to {
                page = result
                if let result { trackView(result) }
            }
        } catch {
            let status = RF.status(error)
            lastErrorStatus = status
            let props: EventProps = ["surface": surface, "period": period.analyticsValue, "period_offset": offset, "error_status": status]
            if page == nil {
                failed = true
                RF.track("reflection_timeline.load_fail", props)
            } else {
                // [UX] 이전 목록이 그대로 남고 안내가 없어요 (기간을 바꾼 직후면 제목만 새 기간)
                RF.track("reflection_timeline.refresh_fail", props)
            }
            trackPageviewIfNeeded()
        }
        loading = false
    }

    // MARK: Amplitude

    /// 오늘이 든 기간에서 몇 기간 떨어졌는지 (지금 0 · 지난주 -1)
    func offset(_ p: ReflectionPeriod, anchor a: String) -> Int {
        let today = KST.today()
        switch p {
        case .all: return 0
        case .day: return KST.diffDays(today, a)
        case .week: return KST.diffDays(KST.startOfWeek(today), KST.startOfWeek(a)) / 7
        case .month: return (KST.yearOf(a) - KST.yearOf(today)) * 12 + KST.monthOf(a) - KST.monthOf(today)
        }
    }
    var offset: Int { offset(period, anchor: anchor) }

    /// 회고 모아보기 첫 진입 — 목표 칩을 불러온 뒤 한 번 (웹과 같이 칩 개수를 같이 보내요)
    private func trackPageviewIfNeeded() {
        guard surface == "reflections" else { return }
        if !pageviewSent {
            pageviewSent = true
            RF.track("reflections.pageview", [
                "entry_source": RF.pendingEntry ?? "other",
                "goal_category_id": goalId,
                "filter_option_count": goalsLoaded ? filterGoals.count : nil,
            ])
            RF.pendingEntry = nil
        }
        // [UX] 연 목표가 칩에 없음 (임시 · 생활 습관 · 지운 목표)
        if goalsLoaded, let id = goalId, unmatchedSent != id, !filterGoals.contains(where: { $0.goalCategoryId == id }) {
            unmatchedSent = id
            RF.track("reflections.goal_param_unmatched", ["goal_category_id": id])
        }
    }

    /// 기간 회고를 그린 직후 — 기간 · 목표마다 한 번 (새로고침 · 고친 뒤 다시 불러오기는 빼요)
    private func trackView(_ p: ReflectionFeedPage) {
        let key = "\(period.rawValue):\(range.from):\(goalId ?? 0)"
        guard key != viewKey else { return }
        viewKey = key
        scrollSent = []
        let es = p.entries
        RF.track("reflection_timeline.view", [
            "surface": surface,
            "goal_category_id": goalId,
            "period": period.analyticsValue,
            "period_offset": offset,
            "entry_count": es.count,
            "check_in_count": es.filter { $0.kind == .CHECK_IN }.count,
            "retro_count": es.filter { $0.kind == .RETRO }.count,
            "day_count": Set(es.map(\.date)).count,
            "elapsed_day_count": totalDays,
            "is_truncated": p.hasMore,
        ])
        if es.isEmpty {
            RF.track("reflection_timeline.empty_view", [
                "surface": surface, "goal_category_id": goalId, "period": period.analyticsValue, "period_offset": offset,
            ])
        }
    }

    /// 날짜 묶음 카드(위에서 index 번째)가 화면에 절반 이상 들어왔을 때 → 25 · 50 · 75 · 100% 구간마다 한 번
    func sawGroup(_ index: Int) {
        let n = groups.count
        guard n > 0 else { return }
        let pct = (index + 1) * 100 / n
        for mark in [25, 50, 75, 100] where pct >= mark && !scrollSent.contains(mark) {
            scrollSent.insert(mark)
            RF.track("reflection_timeline.scroll_depth", [
                "surface": surface, "period": period.analyticsValue, "scroll_percent": mark, "entry_count": entries.count,
            ])
        }
    }

    func trackRetry() {
        RF.track("reflection_timeline.retry_click", ["surface": surface, "period": period.analyticsValue, "error_status": lastErrorStatus])
    }

    func trackPullRefresh() {
        RF.track("reflections.pull_refresh", ["goal_category_id": goalId, "period": period.analyticsValue])
    }

    /// 목표 칩을 누름 ('전체' 0, 목표 칩 1부터)
    func selectChip(_ id: Int?) {
        guard id != goalId else { return }
        let options = filterGoals
        let index = id.flatMap { id in options.firstIndex { $0.goalCategoryId == id } }
        RF.track("reflections.goal_filter_select", [
            "goal_category_id": id,
            "goal_mode": index.map { options[$0].analyticsMode },
            "list_position": (index ?? -1) + 1,
            "filter_option_count": options.count,
        ])
        select(id)
    }

    /// 목표를 바꾸면 화면의 reloadOn(id: reloadId)이 다시 불러와요
    func select(_ id: Int?) {
        guard id != goalId else { return }
        UISelectionFeedbackGenerator().selectionChanged()
        goalId = id
        page = nil
        loading = true
    }

    func setPeriod(_ p: ReflectionPeriod) {
        guard p != period else { return }
        UISelectionFeedbackGenerator().selectionChanged()
        RF.track("reflection_timeline.period_change", [
            "surface": surface, "period": p.analyticsValue, "from_period": period.analyticsValue,
            "period_offset": offset(p, anchor: anchor),
        ])
        period = p
    }

    /// '오늘 / 이번 주 / 이번 달'
    func goToday() {
        let next = KST.today()
        RF.track("reflection_timeline.nav_click", [
            "surface": surface, "period": period.analyticsValue, "date_action": "today", "period_offset": offset(period, anchor: next),
        ])
        anchor = next
    }

    func move(_ n: Int) {
        let next: String
        switch period {
        case .all: next = anchor
        case .day: next = KST.addDays(anchor, n)
        case .week: next = KST.addDays(anchor, n * 7)
        case .month: next = KST.addMonths(anchor, n)
        }
        RF.track("reflection_timeline.nav_click", [
            "surface": surface, "period": period.analyticsValue, "date_action": n < 0 ? "prev" : "next",
            "period_offset": offset(period, anchor: next),
        ])
        anchor = next
    }

    #if DEBUG
    static func preview(empty: Bool = false) -> ReflectionsViewModel {
        let m = ReflectionsViewModel(goalId: nil)
        m.page = empty ? ReflectionFeedPage(from: KST.addDays(KST.today(), -6), to: KST.today(), entries: [], hasMore: false)
            : ReflectionsSampleData.page(today: KST.today())
        return m
    }
    #endif
}

struct ReflectionsScreen: View {
    @State private var vm: ReflectionsViewModel
    /// 미리보기에서는 서버를 부르지 않아요
    private let autoload: Bool
    /// 아래 탭(회고)의 첫 화면이면 true — 위 공통 헤더가 있어서 내비게이션 바 대신 페이지 제목을 보여줘요
    private var isTabRoot = false

    init(initialGoalId: Int? = nil, isTabRoot: Bool = false) {
        _vm = State(initialValue: ReflectionsViewModel(goalId: initialGoalId))
        autoload = true
        self.isTabRoot = isTabRoot
    }

    init(vm: ReflectionsViewModel) {
        _vm = State(initialValue: vm)
        autoload = false
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                if isTabRoot {
                    PageHeader(title: "회고")
                }
                Text("목표마다 남긴 기록과 회고를 날짜별로 한 번에 봐요. 홈 · 목표 상세 어디서 쓴 것이든 여기 모여요.")
                    .font(.pt(13))
                    .foregroundStyle(Color.ink3)
                filterChips
                ReflectionTimelineView(vm: vm)
            }
            .pagePadding()
            .padding(.vertical, 16)
        }
        .canvasBackground()
        .navigationTitle("회고 모아보기")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(isTabRoot ? .hidden : .automatic, for: .navigationBar)
        .refreshable {
            vm.trackPullRefresh()
            await vm.load()
        }
        .modifier(AutoReload(enabled: autoload, vm: vm))
    }

    /// 화면이 열릴 때 · 회고를 고치면 다시 불러와요
    private struct AutoReload: ViewModifier {
        let enabled: Bool
        let vm: ReflectionsViewModel
        func body(content: Content) -> some View {
            if enabled {
                content.reloadOn([.home, .goals, .work], id: vm.reloadId) { await vm.load() }
            } else {
                content
            }
        }
    }

    // MARK: 목표 고르기

    private var filterChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                chip(title: "전체", dot: nil, selected: vm.goalId == nil) { vm.selectChip(nil) }
                ForEach(vm.filterGoals) { g in
                    chip(title: (g.emoji.map { $0.isEmpty ? "" : "\($0) " } ?? "") + g.goalCategoryName,
                         dot: Color.goal(g.colorCode), selected: vm.goalId == g.goalCategoryId) {
                        vm.selectChip(g.goalCategoryId)
                    }
                }
            }
        }
        .scrollClipDisabled()
    }

    private func chip(title: String, dot: Color?, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 5) {
                if let dot {
                    Circle().fill(dot).frame(width: 8, height: 8)
                }
                Text(title).lineLimit(1)
            }
            .font(.pt(12, .semibold))
            .foregroundStyle(selected ? Color.actionPrimaryFg : Color.ink2)
            .padding(.horizontal, 12)
            .frame(height: 34)
            .background(selected ? Color.actionPrimary : Color.surface, in: Capsule())
            .overlay(Capsule().stroke(selected ? Color.actionPrimary : Color.borderSubtle, lineWidth: 1))
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

/// 기간별 회고 목록 — 일간 · 주간 · 월간 탭 + ‹ › 기간 이동 (웹 ReflectionTimeline).
/// 회고 모아보기와 목표 상세 "회고" 탭이 같이 써요. 스크롤은 감싸는 화면이 해요.
struct ReflectionTimelineView: View {
    let vm: ReflectionsViewModel
    /// 줄마다 목표 이름을 보일지 (nil 이면 "전체"일 때만)
    var showGoal: Bool? = nil
    /// 비었을 때 안내 문구
    var emptyHint: String? = nil

    private var goalShown: Bool { showGoal ?? (vm.goalId == nil) }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            periodBar
            content
        }
    }

    // MARK: 기간 고르기

    private var periodBar: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 0) {
                ForEach(ReflectionPeriod.allCases) { p in
                    let on = vm.period == p
                    Button { vm.setPeriod(p) } label: {
                        Text(p.label)
                            .font(.pt(13, .semibold))
                            .foregroundStyle(on ? Color.textPrimary : Color.textSecondary)
                            .frame(maxWidth: .infinity)
                            .frame(height: 34)
                            .background {
                                if on {
                                    RoundedRectangle(cornerRadius: Radius.segmentItem, style: .continuous)
                                        .fill(Color.bgSurface)
                                        .segShadow()
                                }
                            }
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(on ? .isSelected : [])
                }
            }
            .padding(3)
            .background(Color.bgSubtle, in: RoundedRectangle(cornerRadius: Radius.segment, style: .continuous))

            // 기간 이동 (전체에서는 숨겨요): 왼쪽 정렬 · 내용 크기만큼 ‹ 10월 12일 – 18일 › 이번 주
            if vm.period != .all {
                HStack(spacing: 2) {
                    navArrow(.chevronLeft, label: "이전 기간") { vm.move(-1) }
                    Text(vm.title)
                        .font(.bodyMDMedium)
                        .tracking(-0.28)
                        .foregroundStyle(Color.textPrimary)
                        .monospacedDigit()
                        .lineLimit(1)
                        .fixedSize()
                    navArrow(.chevronRight, label: "다음 기간") { vm.move(1) }
                        .disabled(vm.atLatest)
                        .opacity(vm.atLatest ? 0.3 : 1)
                    if !vm.atLatest {
                        Button { vm.goToday() } label: {
                            Text(vm.period.currentLabel).font(.labelMD).foregroundStyle(Color.textBrand)
                                .padding(.horizontal, 6).frame(minHeight: 28).contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                    Spacer(minLength: 0)
                }
            }
        }
    }

    /// 기간 이동 화살표 — 16pt 아이콘 · 28pt 버튼
    private func navArrow(_ icon: NIcon, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            icon.image(size: 16)
                .foregroundStyle(Color.textSecondary)
                .frame(width: 28, height: 28)
                .contentShape(Rectangle())
        }
        .buttonStyle(IconButtonStyle())
        .accessibilityLabel(label)
    }

    // MARK: 내용

    @ViewBuilder
    private var content: some View {
        if vm.loading {
            ProgressView().frame(maxWidth: .infinity, minHeight: 200)
        } else if vm.failed {
            EmptyStateCard(icon: "exclamationmark.triangle", title: "회고를 불러오지 못했어요") {
                NButton("다시 불러오기", variant: .secondary, size: .sm) {
                    vm.trackRetry()
                    Task { await vm.load() }
                }
            }
        } else if vm.groups.isEmpty {
            EmptyStateCard(icon: "books.vertical",
                           title: vm.period == .all ? "아직 남긴 회고가 없어요"
                               : "\(vm.period == .day ? "이날" : vm.period == .week ? "이번 주" : "이 달")에 남긴 회고가 없어요",
                           description: emptyHint ?? "홈의 '오늘 회고'나 목표 상세의 '오늘 기록'에서 하루를 돌아보면 여기 모여요.")
        } else {
            if vm.period != .day { summary }
            ForEach(Array(vm.groups.enumerated()), id: \.element.id) { index, g in
                VStack(alignment: .leading, spacing: 8) {
                    if vm.period != .day {
                        HStack(alignment: .firstTextBaseline, spacing: 6) {
                            Text(KST.formatMonthDay(g.date)).font(.pt(15, .bold)).foregroundStyle(Color.ink)
                            Text(relLabel(g.date)).font(.pt(12)).foregroundStyle(Color.ink3)
                        }
                        .padding(.horizontal, 2)
                    }
                    VStack(spacing: 0) {
                        ForEach(Array(g.items.enumerated()), id: \.element.id) { i, e in
                            if i > 0 {
                                // 칸 사이 구분선: 1pt · 좌우 16 들여서
                                Rectangle().fill(Color.listDivider).frame(height: 1).padding(.horizontal, 16)
                            }
                            ReflectionEntryRow(entry: e, showGoal: goalShown, surface: vm.surface)
                        }
                    }
                    .card()
                }
                // 스크롤 깊이 (reflection_timeline.scroll_depth)
                .onFirstHalfVisible { vm.sawGroup(index) }
                // 기간 · 목표가 바뀌면 "보였음" 기억을 새로
                .id("\(vm.viewKey)|\(g.date)")
            }
        }
    }

    /// 이 기간 요약 카드: 기록한 날 (/ 전체 날) · 기분별 날 수 · 가장 많이 돌아본 목표
    private var summary: some View {
        let checkIns = vm.entries.filter { $0.kind == .CHECK_IN }
        return VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .bottom, spacing: 16) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("기록한 날").font(.captionMD).foregroundStyle(Color.textSecondary)
                    Text(vm.period == .all ? "\(vm.groups.count)일" : "\(vm.groups.count)일 / \(vm.totalDays)일")
                        .textStyle(.h3)
                        .foregroundStyle(Color.textPrimary)
                        .monospacedDigit()
                }
                Spacer(minLength: 0)
                if checkIns.contains(where: { $0.mood != nil }) {
                    HStack(spacing: 12) {
                        ForEach(GoalMoodOption.all) { m in
                            let n = checkIns.filter { $0.mood == m.value }.count
                            HStack(spacing: 4) {
                                Text(m.emoji).font(.system(size: 20))
                                Text("\(n)").font(.labelLG).monospacedDigit().foregroundStyle(Color.textPrimary)
                            }
                            .accessibilityElement(children: .combine)
                            .accessibilityLabel("\(m.label) \(n)일")
                        }
                    }
                }
            }
            if goalShown, let top = vm.topGoal {
                Rectangle().fill(Color.listDivider).frame(height: 1)
                VStack(alignment: .leading, spacing: 4) {
                    Text("가장 많이 돌아본 목표").font(.captionMD).foregroundStyle(Color.textSecondary)
                    HStack(spacing: 6) {
                        if let color = topGoalColor(top.title) {
                            Circle().fill(color).frame(width: 8, height: 8)
                        }
                        Text(top.title).font(.bodyMDMedium).foregroundStyle(Color.textPrimary).lineLimit(1)
                        Text("· \(top.days)일").font(.bodyMD).foregroundStyle(Color.textSecondary)
                    }
                }
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.bgSurface, in: RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).strokeBorder(Color.borderSubtle, lineWidth: 1))
    }

    /// 가장 많이 돌아본 목표의 색 (같은 이름의 회고에서)
    private func topGoalColor(_ title: String) -> Color? {
        vm.entries.first(where: { $0.goalTitle == title }).map { Color.goal($0.colorCode) }
    }

    private func relLabel(_ date: String) -> String {
        let diff = KST.diffDays(date, KST.today())
        if diff == 0 { return "오늘" }
        if diff == 1 { return "어제" }
        return "\(diff)일 전"
    }
}

/// 회고 한 줄 — 목표 · 수치 · 기분 · 내용
struct ReflectionEntryRow: View {
    let entry: ReflectionEntry
    var showGoal = true
    /// Amplitude surface (reflections · goal_detail)
    var surface = "reflections"

    @Environment(AppRouter.self) private var router
    /// 눌러서 여는 상세 시트 (수정 · 삭제)
    @State private var detailOpen = false

    var body: some View {
        let e = entry
        let isRetro = e.kind == .RETRO
        let mood = GoalMoodOption.of(e.mood)
        HStack(alignment: .top, spacing: 10) {
            NSymbol(isRetro ? "pencil" : "square.and.pencil", size: 12, weight: .semibold)
                .foregroundStyle(isRetro ? Color.ink2 : Color.textBrand)
                .frame(width: 28, height: 28)
                .background(isRetro ? Color.subtle : Color.brandSoft, in: Circle())
                .accessibilityLabel(isRetro ? "회고 글" : "오늘 기록")
            VStack(alignment: .leading, spacing: 8) {
                // ① 목표 줄 (전체 보기일 때만): 8pt 점 + 목표 이름 · Label/SM · text/secondary
                if showGoal {
                    Button {
                        RF.track("reflection_timeline.goal_link_click", [
                            "surface": surface, "goal_category_id": e.goalCategoryId, "goal_mode": e.goalMode?.rawValue,
                            "entry_kind": e.kind.rawValue, "days_ago": KST.diffDays(e.date, KST.today()),
                        ])
                        router.openGoal(e.goalCategoryId)
                    } label: {
                        HStack(spacing: 6) {
                            Circle().fill(Color.goal(e.colorCode)).frame(width: 8, height: 8)
                            Text(e.goalTitle).font(.labelSM).foregroundStyle(Color.textSecondary).lineLimit(1)
                        }
                    }
                    .buttonStyle(.plain)
                }
                // ② 배지 줄 (수치 → 기분) — 늘 한 줄, 길면 수치 이름을 줄여요
                if e.value != nil || mood != nil || (isRetro && e.reviewType != nil && e.reviewType != "DAILY") {
                    HStack(spacing: 6) {
                        if let v = e.value {
                            HStack(spacing: 4) {
                                Text(e.metricName ?? "수치")
                                    .font(.pt(13, .medium))
                                    .foregroundStyle(Color.textSecondary)
                                    .lineLimit(1)
                                    .truncationMode(.tail)
                                Text(GoalDetailLogic.fmtNum(v) + (e.unit ?? ""))
                                    .font(.pt(13, .semibold))
                                    .foregroundStyle(Color.textPrimary)
                                    .lineLimit(1)
                                    .fixedSize()
                            }
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(Color.badgeBg, in: Capsule())
                            .layoutPriority(0)
                        }
                        if let mood {
                            Text("\(mood.emoji) \(mood.label)")
                                .font(.pt(13, .medium))
                                .foregroundStyle(Color.textPrimary)
                                .lineLimit(1)
                                .fixedSize()
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .background(Color.badgeBg, in: Capsule())
                                .layoutPriority(1)
                        }
                        if isRetro, let t = e.reviewType, t != "DAILY" {
                            Text(t == "WEEKLY" ? "주간 회고" : "월간 회고")
                                .font(.labelXS)
                                .foregroundStyle(Color.textTertiary)
                                .fixedSize()
                                .layoutPriority(1)
                        }
                    }
                }
                if let note = e.note, !note.isEmpty {
                    Text(note)
                        .font(.pt(14))
                        .foregroundStyle(Color.ink2)
                        .lineSpacing(3)
                        .fixedSize(horizontal: false, vertical: true)
                        .textSelection(.enabled)
                } else if !isRetro {
                    Text("수치만 남겼어요").font(.pt(12)).foregroundStyle(Color.ink4)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .contentShape(Rectangle())
        // 줄을 누르면 상세 시트 (전체 내용 · 수정 · 삭제) — 목표 이름 버튼은 버튼이 먼저 받아요
        .onTapGesture {
            RF.track("reflection_timeline.entry_click", [
                "surface": surface, "entry_kind": entry.kind.rawValue, "goal_category_id": entry.goalCategoryId,
                "days_ago": KST.diffDays(entry.date, KST.today()), "goal_mode": entry.goalMode?.rawValue,
            ])
            detailOpen = true
        }
        .sheet(isPresented: $detailOpen) {
            ReflectionDetailSheet(entry: entry, surface: surface)
        }
    }
}

#if DEBUG
enum ReflectionsSampleData {
    static func page(today: String) -> ReflectionFeedPage {
        let d = { (n: Int) -> String in KST.addDays(today, n) }
        let json = """
        {"from": "\(d(-29))", "to": "\(today)", "hasMore": true, "entries": [
          {"kind": "CHECK_IN", "date": "\(today)", "goalCategoryId": 1, "goalName": "건강", "goalEmoji": "🏃", "colorCode": "12B76A",
           "goalMode": "ACHIEVEMENT", "mood": "OK", "note": "점심 마라탕, 저녁은 샐러드", "value": 64.0, "metricName": "체중", "unit": "kg"},
          {"kind": "RETRO", "date": "\(today)", "goalCategoryId": 2, "goalName": "커리어", "goalEmoji": "💼", "colorCode": "F59E0B",
           "goalMode": "RECORD", "retrospectiveId": 1, "reviewType": "DAILY", "note": "회의가 길어져서 초안을 못 썼다. 내일 오전에 먼저 하기."},
          {"kind": "CHECK_IN", "date": "\(d(-1))", "goalCategoryId": 3, "goalName": "토익 900", "goalEmoji": "📚", "colorCode": "3D5AFE",
           "goalMode": "ACHIEVEMENT", "mood": "GOOD", "note": "LC 파트 2 풀고 오답 정리까지", "value": 835, "metricName": "모의고사 점수", "unit": "점"},
          {"kind": "CHECK_IN", "date": "\(d(-1))", "goalCategoryId": 1, "goalName": "건강", "goalEmoji": "🏃", "colorCode": "12B76A",
           "goalMode": "ACHIEVEMENT", "value": 64.4, "metricName": "체중", "unit": "kg"},
          {"kind": "CHECK_IN", "date": "\(d(-3))", "goalCategoryId": 4, "goalName": "독서", "goalEmoji": "📖", "colorCode": "8B5CF6",
           "goalMode": "ACHIEVEMENT", "mood": "BAD", "note": "야근이라 못 읽음"}
        ]}
        """
        return try! JSONDecoder().decode(ReflectionFeedPage.self, from: Data(json.utf8))
    }
}

#Preview("회고 모아보기") {
    NavigationStack {
        ReflectionsScreen(vm: .preview())
    }
    .environment(AppRouter.shared)
}

#Preview("회고 모아보기 — 비어 있음") {
    NavigationStack {
        ReflectionsScreen(vm: .preview(empty: true))
    }
    .environment(AppRouter.shared)
}
#endif
