// Features/Calendar/TimetableGridView.swift
//
// TimeTable 격자 — 날짜 머리글 · 시간 미정 줄 · 시간 눈금 · 날짜 칸 · 블록 · 드래그.
// 웹 원본: features/timetable/ui/TimeGrid.tsx + useGridDrag.ts
//
// 배치
//  ┌ 날짜 머리글 (고정) ─────────────────┐
//  ├ 시간 미정 줄 (고정, 있을 때만) ──────┤   ← 여기로 끌어 놓으면 "시간 미정"
//  ├ ScrollView ────────────────────────┤
//  │  시간 눈금 | 월 | 화 | … (시간당 56pt) │
//  └────────────────────────────────────┘
//
// 드래그 (웹: 마우스 4px · 터치 0.35초 길게 누르기 → 아이폰은 "길게 눌러 끌기"만)
//  - 블록을 0.35초 길게 누르면(진동) 끌 수 있어요. 10분 단위로 맞춰져요. 주간에서는 옆 날짜 칸으로도 옮겨요.
//  - 블록 아래 끝(높이 26pt 이상) · 위 끝(44pt 이상)을 길게 눌러 끌면 끝 · 시작 시각만 바뀌어요.
//  - 수면 블록은 "☀️ 기상 / 🌙 취침" 띠를 끌어 기상 · 취침 시각을 바꿔요.
//  - 좌표는 머리글~격자 전체를 감싼 공간(TimetableGeo.space) 기준이에요.
//    격자는 스크롤되므로 "격자 맨 위가 지금 이 공간의 어디인지(gridTop)"를 PreferenceKey 로 따라가요.
//  - 끄는 동안에는 스크롤을 잠가요 (손가락 움직임이 스크롤이 아니라 블록 이동이 되게).

import SwiftUI
import UIKit

// MARK: - 격자 치수 · 좌표 → 시각

struct TimetableGridMetrics {
    var width: CGFloat
    var dates: [String]
    /// 화면 시작 · 끝 (그날 0시 기준 분)
    var from: Int
    var to: Int
    var headerHeight: CGFloat
    /// 시간 미정 줄 높이 (없으면 0)
    var unscheduledHeight: CGFloat

    var showUnscheduled: Bool { unscheduledHeight > 0 }
    var dayCount: Int { max(dates.count, 1) }
    var colWidth: CGFloat { max(0, (width - TimetableGeo.labelWidth) / CGFloat(dayCount)) }
    var height: CGFloat { CGFloat(to - from) * TimetableGeo.ppm }

    func columnX(_ i: Int) -> CGFloat { TimetableGeo.labelWidth + CGFloat(i) * colWidth }

    /// 그날 0시 기준 분 → 격자 안 y
    func y(_ minute: Int) -> CGFloat { CGFloat(minute - from) * TimetableGeo.ppm }

    /// 공간 x → 날짜 칸 번호 (칸 밖이면 가장 가까운 칸)
    func columnIndex(x: CGFloat) -> Int {
        guard colWidth > 0, !dates.isEmpty else { return 0 }
        let raw = Int(floor((x - TimetableGeo.labelWidth) / colWidth))
        return min(max(raw, 0), dates.count - 1)
    }

    /// 공간 y → 그날 0시 기준 분 (소수)
    func minute(atY y: CGFloat, gridTop: CGFloat) -> Double {
        Double(from) + Double((y - gridTop) / TimetableGeo.ppm)
    }

    /// 손가락 위치 → 놓을 자리 (웹 useGridDrag compute)
    /// - translationY: 손가락이 처음 누른 곳에서 세로로 움직인 거리 (격자 기준).
    ///   이미 시간이 있는 블록은 "원래 시각 + 움직인 거리"로 계산해요 → 스크롤 위치와 상관없이 손가락을 그대로 따라와요.
    ///   시간 미정 칩처럼 원래 시각이 없을 때만 손가락의 절대 위치(gridTop 기준)를 써요.
    func target(for st: TimetableDragState, at p: CGPoint, translationY: CGFloat?, gridTop: CGFloat) -> TimetableDropTarget? {
        guard !dates.isEmpty else { return nil }
        let src = st.source
        // ① "시간 미정" 줄 위
        let unscheduledTop = headerHeight
        if st.mode == .move, src.canUnschedule, showUnscheduled,
           p.y >= unscheduledTop, p.y <= unscheduledTop + unscheduledHeight {
            return .unscheduled(date: dates[columnIndex(x: p.x)])
        }
        // ② 날짜 칸 (고정 시간 · 크기 조절은 원래 날짜에서만)
        // 실제로 한 시간은 그 날 안에서만 옮겨요 (다른 날로 옮기면 "언제 했는지"가 바뀌어 버려요)
        let sameDay = src.isFixed || src.isActual || st.mode != .move
        var index = columnIndex(x: p.x)
        if sameDay, let i = dates.firstIndex(of: src.date) { index = i }
        let date = dates[index]
        let m = minute(atY: p.y, gridTop: gridTop)
        /// 움직인 거리 → 분
        let dm: Double? = translationY.map { Double($0 / TimetableGeo.ppm) }

        switch st.mode {
        case .resize:
            let start = src.start ?? from
            let raw = dm.map { Double(src.end ?? start) + $0 } ?? m
            let end = min(to, max(start + KST.snapMinutes, TimetableDrag.snap(raw)))
            return .grid(date: date, start: start, end: end)
        case .resizeTop:
            // 끝은 그대로 두고 시작 시각만 움직여요 (최소 10분은 남김)
            let end = src.end ?? to
            let raw = dm.map { Double(src.start ?? end) + $0 } ?? m
            let start = max(from, min(end - KST.snapMinutes, TimetableDrag.snap(raw)))
            return .grid(date: date, start: start, end: end)
        case .move:
            let dur = src.duration
            // 시간 미정 칩: 블록 위쪽 조금 아래를 잡은 것으로
            let grab = st.grab ?? min(Double(dur) / 2, 30)
            // 묶음이면 묶음 전체가 격자 안에 들어오게
            var before = 0
            var after = dur
            if let s = src.start {
                for g in src.group {
                    before = max(before, s - g.start)
                    after = max(after, g.end - s)
                }
            }
            let lo = from + before
            // 시간이 있는 블록: 원래 시작 + 움직인 거리 (잡은 지점이 손가락 아래에 그대로 남아요)
            let raw: Double
            if let s = src.start, let dm {
                raw = Double(s) + dm
            } else {
                raw = m - grab
            }
            let start = min(max(TimetableDrag.snap(raw), lo), max(lo, to - after))
            return .grid(date: date, start: start, end: start + dur)
        }
    }
}

/// 격자 전체에서 한 번만 계산해 칸들에 나눠 주는 표시 상태
struct TimetableGridFlags {
    var selectedTaskId: Int?
    var selectedFixedKey: String?
    var selectedActivityId: Int?
    var focusIds: Set<Int>
    var groupIds: Set<Int>
    /// AI 배치 미리보기 중 — 제안 말고는 흐리게
    var fadeOthers: Bool
    var highlightIds: Set<Int>
    var drag: TimetableDragState?
    /// 길게 눌러 들어 올린 블록 (owner id)
    var liftedOwner: String?

    /// 블록을 들어 올렸거나 끄는 중 → 나머지는 50% 로 흐리게
    var dimOthers: Bool { drag != nil || liftedOwner != nil }

    /// 들어 올렸거나 끄는 게 "실제로 한 시간" 블록인지 (그 블록이 있는 실제 칸 전체를 흐리게 하지 않으려고)
    var actualActive: Bool { drag?.source.isActual == true || liftedOwner?.hasPrefix("a-") == true }

    /// 이 블록(또는 그 손잡이)이 들어 올린 블록인지
    func isLifted(_ owner: String) -> Bool {
        guard let l = liftedOwner else { return false }
        return l == owner || l.hasPrefix(owner + "-")
    }
}

/// 스크롤해도 화면을 다시 그리지 않도록 일반 클래스에 담아 둬요 (드래그 계산 때만 읽어요)
final class TimetableScrollTracker {
    /// iOS 17: PreferenceKey 로 받은 격자 맨 위 위치 (스크롤 뒤 늦게 오거나 안 올 때가 있어요)
    var gridTop: CGFloat = 0
    /// iOS 18+: 스크롤한 거리 (onScrollGeometryChange — 코드로 스크롤해도 정확해요)
    var scrollY: CGFloat?
    /// 끌기 시작할 때의 "시간 미정" 줄 높이 — 끄는 동안 줄이 생기면 격자가 아래로 밀린 만큼 빼 줘요
    var dragStartUnscheduledHeight: CGFloat = 0

    /// 격자 ScrollView 의 실제 UIScrollView (끄는 중 가장자리 자동 스크롤용)
    weak var scrollView: UIScrollView?
    /// 끌기 시작할 때 스크롤 위치 — 자동 스크롤로 내려간 만큼 블록도 같이 내려가요
    var dragStartOffset: CGFloat = 0
    /// 마지막 손가락 정보 (손가락이 멈춰 있어도 자동 스크롤 중에 자리를 다시 계산해요)
    var lastValue: DragGesture.Value?
    var lastMetrics: TimetableGridMetrics?
    var autoScrollTimer: Timer?

    /// 지금 스크롤 위치 (맨 위 = 0)
    var currentOffset: CGFloat? {
        if let sv = scrollView { return sv.contentOffset.y + sv.adjustedContentInset.top }
        return scrollY
    }

    /// 지금 격자 맨 위가 TimetableGeo.space 의 어디인지
    func gridTop(_ m: TimetableGridMetrics) -> CGFloat {
        if let y = currentOffset { return m.headerHeight + m.unscheduledHeight + 1 - y }
        return gridTop
    }

    func stopAutoScroll() {
        autoScrollTimer?.invalidate()
        autoScrollTimer = nil
        lastValue = nil
    }
}

/// SwiftUI ScrollView 안에 투명한 UIView 를 하나 넣어서, 위로 올라가며 진짜 UIScrollView 를 찾아요
private struct TimetableScrollViewFinder: UIViewRepresentable {
    let tracker: TimetableScrollTracker

    func makeUIView(context: Context) -> FinderView {
        let v = FinderView()
        v.isUserInteractionEnabled = false
        v.backgroundColor = .clear
        v.onFind = { [weak tracker] sv in tracker?.scrollView = sv }
        return v
    }

    func updateUIView(_ uiView: FinderView, context: Context) {
        if tracker.scrollView == nil { uiView.find() }
    }

    final class FinderView: UIView {
        var onFind: ((UIScrollView) -> Void)?

        /// 어떤 터치도 받지 않아요
        override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? { nil }

        override func didMoveToWindow() {
            super.didMoveToWindow()
            DispatchQueue.main.async { [weak self] in self?.find() }
        }

        func find() {
            var v = superview
            while let s = v {
                if let sv = s as? UIScrollView {
                    onFind?(sv)
                    return
                }
                v = s.superview
            }
        }
    }
}

/// iOS 18+ 에서 스크롤 거리를 정확히 받아요 (17 에서는 아무것도 안 해요)
private struct TimetableScrollOffsetReader: ViewModifier {
    let tracker: TimetableScrollTracker

    @ViewBuilder
    func body(content: Content) -> some View {
        if #available(iOS 18.0, *) {
            content.onScrollGeometryChange(for: CGFloat.self) { geo in
                geo.contentOffset.y + geo.contentInsets.top
            } action: { _, y in
                tracker.scrollY = y
            }
        } else {
            content
        }
    }
}

private struct TimetableGridTopKey: PreferenceKey {
    static let defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = nextValue()
    }
}

typealias TimetableDragHandler = (TimetableDragSource, TimetableDragMode, String, DragGesture.Value) -> Void

// MARK: - 격자

struct TimetableGridView<Footer: View>: View {
    let board: TimetableBoardModel
    let days: [TimetableDay]
    let activities: [ActualActivity]
    let startHour: Int
    let endHour: Int
    /// 일간: 칸을 "계획 | 실제"로 나눠요
    let planVsActual: Bool
    /// "NEW" 표시할 Task (방금 만든 것)
    let highlightIds: Set<Int>
    /// 주간에서 날짜 머리글 누름 → 일간
    let onDayClick: ((String) -> Void)?
    /// 빈 칸(지난 시간)을 누르면 실제로 한 일 기록
    let emptyClickEnabled: Bool
    let footer: () -> Footer

    @State private var width: CGFloat = 0
    @State private var expanded: Set<String> = []
    @State private var tracker = TimetableScrollTracker()
    @State private var scrolledFor: String?

    init(board: TimetableBoardModel, days: [TimetableDay], activities: [ActualActivity] = [],
         startHour: Int, endHour: Int, planVsActual: Bool = false, highlightIds: Set<Int> = [],
         onDayClick: ((String) -> Void)? = nil, emptyClickEnabled: Bool = false,
         @ViewBuilder footer: @escaping () -> Footer) {
        self.board = board
        self.days = days
        self.activities = activities
        self.startHour = startHour
        self.endHour = endHour
        self.planVsActual = planVsActual
        self.highlightIds = highlightIds
        self.onDayClick = onDayClick
        self.emptyClickEnabled = emptyClickEnabled
        self.footer = footer
    }

    var body: some View {
        let m = metrics
        let flags = makeFlags()
        let drag: TimetableDragHandler = { s, mode, owner, v in dragChanged(s, mode, owner, v, m) }
        let dragEnd: (String) -> Void = { owner in board.endDrag(ownerId: owner) }

        VStack(spacing: 0) {
            TimetableHeaderRow(days: days, metrics: m, planVsActual: planVsActual, onDayClick: onDayClick)
            if m.showUnscheduled {
                TimetableUnscheduledRow(board: board, days: days, metrics: m, flags: flags, expanded: $expanded,
                                        onDrag: drag, onDragEnd: dragEnd)
            }
            Rectangle().fill(Color.borderSoft).frame(height: 1)
            ScrollViewReader { proxy in
                ScrollView {
                    VStack(spacing: 0) {
                        canvas(m, flags, drag: drag, dragEnd: dragEnd)
                            .background(GeometryReader { g in
                                Color.clear.preference(key: TimetableGridTopKey.self,
                                                       value: g.frame(in: TimetableGeo.space).minY)
                            })
                            // 스크롤 영역 찾기용 투명 뷰 — 1pt 짜리로 구석에 두고 터치는 절대 받지 않게 해요
                            // (격자 크기로 깔면 UIKit 이 터치를 이 뷰로 보내서 스크롤 · 블록 누르기가 막힐 수 있어요)
                            .background(alignment: .topLeading) {
                                TimetableScrollViewFinder(tracker: tracker)
                                    .frame(width: 1, height: 1)
                                    .allowsHitTesting(false)
                                    .accessibilityHidden(true)
                            }
                        footer()
                    }
                }
                .scrollDisabled(board.drag != nil)
                .onChange(of: board.drag == nil) { _, ended in
                    // 혹시 SwiftUI 가 다시 켜지 못한 경우를 막으려고 끝나면 직접 켜요
                    if ended {
                        tracker.stopAutoScroll()
                        tracker.scrollView?.isScrollEnabled = true
                    }
                }
                .onPreferenceChange(TimetableGridTopKey.self) { v in tracker.gridTop = v }
                .modifier(TimetableScrollOffsetReader(tracker: tracker))
                .onAppear { scrollToStart(proxy) }
                .onChange(of: rangeKey) { scrollToStart(proxy) }
                .onChange(of: focusKey(flags)) { scrollToFocus(proxy, flags) }
            }
        }
        .coordinateSpace(TimetableGeo.space)
        .background(GeometryReader { g in
            Color.clear
                .onAppear { width = g.size.width }
                .onChange(of: g.size.width) { _, w in width = w }
        })
    }

    // MARK: 치수

    private var metrics: TimetableGridMetrics {
        // Task 를 끌고 있으면 "시간 미정"으로 놓을 수 있게 빈 줄이라도 보여줘요
        let show = days.contains { !$0.unscheduledTasks.isEmpty } || (board.drag?.source.canUnschedule ?? false)
        var uh: CGFloat = 0
        if show {
            var maxShown = 0
            var anyMore = false
            for d in days {
                let n = d.unscheduledTasks.count
                let shown = expanded.contains(d.date) ? n : min(n, TimetableGeo.unscheduledVisible)
                maxShown = max(maxShown, shown)
                if shown < n { anyMore = true }
            }
            let natural = 10 + CGFloat(maxShown) * (TimetableGeo.chipHeight + 4) + (anyMore ? 18 : 0)
            uh = min(TimetableGeo.maxUnscheduledHeight, max(40, natural))
        }
        let header: CGFloat = days.count == 1 ? (planVsActual ? TimetableGeo.dayHeaderHeight : 52) : TimetableGeo.weekHeaderHeight
        return TimetableGridMetrics(width: width, dates: days.map { $0.date }, from: startHour * 60, to: endHour * 60,
                                    headerHeight: header, unscheduledHeight: uh)
    }

    private func makeFlags() -> TimetableGridFlags {
        TimetableGridFlags(selectedTaskId: board.selectedTaskId,
                           selectedFixedKey: board.selectedFixedKey,
                           selectedActivityId: board.selectedActivityId,
                           focusIds: board.focusIds,
                           groupIds: board.groupIds,
                           fadeOthers: board.proposing,
                           highlightIds: highlightIds,
                           drag: board.drag,
                           liftedOwner: board.liftedOwnerId)
    }

    private func canvas(_ m: TimetableGridMetrics, _ flags: TimetableGridFlags,
                        drag: @escaping TimetableDragHandler, dragEnd: @escaping (String) -> Void) -> some View {
        ZStack(alignment: .topLeading) {
            TimetableHourBackground(startHour: startHour, endHour: endHour, width: m.width)
            ForEach(days.indices, id: \.self) { i in
                TimetableDayColumn(board: board, day: days[i], metrics: m, flags: flags, activities: activities,
                                   single: days.count == 1, planVsActual: planVsActual,
                                   emptyClickEnabled: emptyClickEnabled, onDrag: drag, onDragEnd: dragEnd)
                    .frame(width: m.colWidth, height: m.height)
                    .offset(x: m.columnX(i))
            }
            // 지금 시각 글자 (왼쪽 시간 눈금 자리)
            TimetableNowGutterLabel(dates: m.dates, from: m.from, to: m.to)
        }
        .frame(width: m.width, height: m.height, alignment: .topLeading)
    }

    // MARK: 드래그

    private func dragChanged(_ source: TimetableDragSource, _ mode: TimetableDragMode, _ owner: String,
                             _ v: DragGesture.Value, _ m: TimetableGridMetrics) {
        let top = tracker.gridTop(m)
        if board.drag?.ownerId != owner {
            tracker.dragStartUnscheduledHeight = m.unscheduledHeight
            tracker.dragStartOffset = tracker.currentOffset ?? 0
            // 잡은 지점: 블록 위쪽에서 몇 분 아래를 잡았는지 (시간 미정 칩을 격자로 끌 때 씀)
            var grab: Double? = nil
            if mode == .move, let s = source.start {
                grab = max(0, m.minute(atY: v.startLocation.y, gridTop: top) - Double(s))
            }
            board.beginDrag(source: source, mode: mode, grab: grab, ownerId: owner)
        }
        tracker.lastValue = v
        tracker.lastMetrics = m
        Self.updateTarget(board: board, tracker: tracker, v: v, m: m)
        startAutoScroll()
    }

    /// 손가락 위치 + 스크롤 위치로 놓을 자리 계산
    private static func updateTarget(board: TimetableBoardModel, tracker: TimetableScrollTracker,
                                     v: DragGesture.Value, m: TimetableGridMetrics) {
        guard let st = board.drag else { return }
        // 끌기 시작하면서 "시간 미정" 줄이 새로 생기면 격자가 그만큼 아래로 밀려요 → 손가락과 어긋나지 않게 빼 줘요
        let pushed = m.unscheduledHeight - tracker.dragStartUnscheduledHeight
        // 자동 스크롤로 내려간 만큼 블록도 같이 내려가요
        let scrolled = (tracker.currentOffset ?? tracker.dragStartOffset) - tracker.dragStartOffset
        let dy: CGFloat? = st.source.start != nil ? v.translation.height - pushed + scrolled : nil
        board.updateDrag(target: m.target(for: st, at: v.location, translationY: dy, gridTop: tracker.gridTop(m)))
    }

    // MARK: 끄는 중 가장자리 자동 스크롤

    /// 손가락이 격자 위 · 아래 가장자리(70pt)에 가까울수록 빠르게 스크롤해요
    private func startAutoScroll() {
        guard tracker.autoScrollTimer == nil else { return }
        let board = self.board
        let tracker = self.tracker
        let timer = Timer(timeInterval: 1.0 / 60.0, repeats: true) { _ in
            MainActor.assumeIsolated {
                Self.autoScrollStep(board: board, tracker: tracker)
            }
        }
        // 손가락으로 누르고 있는 동안(tracking 모드)에도 돌게 .common 에 넣어요
        RunLoop.main.add(timer, forMode: .common)
        tracker.autoScrollTimer = timer
    }

    private static func autoScrollStep(board: TimetableBoardModel, tracker: TimetableScrollTracker) {
        guard board.drag != nil, let sv = tracker.scrollView, let v = tracker.lastValue, let m = tracker.lastMetrics else {
            tracker.stopAutoScroll()
            return
        }
        let visTop = m.headerHeight + m.unscheduledHeight + 1
        let visBottom = visTop + sv.bounds.height - sv.adjustedContentInset.bottom
        let edge: CGFloat = 70
        let maxSpeed: CGFloat = 14
        let y = v.location.y
        var speed: CGFloat = 0
        if y > visBottom - edge {
            speed = min((y - (visBottom - edge)) / edge, 1.4) * maxSpeed
        } else if y >= visTop && y < visTop + edge {
            // "시간 미정" 줄 위에서는 스크롤하지 않아요 (거기에 놓으려는 거라서)
            speed = -min((visTop + edge - y) / edge, 1.4) * maxSpeed
        }
        guard speed != 0 else { return }
        let minY = -sv.adjustedContentInset.top
        let maxY = max(minY, sv.contentSize.height - sv.bounds.height + sv.adjustedContentInset.bottom)
        let next = min(max(sv.contentOffset.y + speed, minY), maxY)
        guard abs(next - sv.contentOffset.y) > 0.1 else { return }
        sv.setContentOffset(CGPoint(x: sv.contentOffset.x, y: next), animated: false)
        updateTarget(board: board, tracker: tracker, v: v, m: m)
    }

    // MARK: 스크롤

    private var rangeKey: String { "\(days.first?.date ?? "")_\(days.count)" }

    /// 처음 보거나 기간이 바뀌면 (DESIGN_SPEC 9.6):
    ///  - 오늘이 보이면 지금 시각이 화면 위에서 1/3 쯤 오게
    ///  - 아니면 첫 블록(없으면 08:00) 이 위에 오게
    private func scrollToStart(_ proxy: ScrollViewProxy) {
        guard !days.isEmpty, scrolledFor != rangeKey else { return }
        scrolledFor = rangeKey
        let now = KST.now()
        let hasToday = days.contains { $0.date == now.date }
        if hasToday {
            let hour = min(max(now.minutes / 60, startHour), endHour - 1)
            Task { @MainActor in proxy.scrollTo("tt-hour-\(hour)", anchor: UnitPoint(x: 0.5, y: 0.33)) }
        } else {
            var first: Int?
            for d in days {
                for t in d.tasks {
                    if let r = TimetableLayout.shownMinutes(d.date, t) { first = min(first ?? r.start, r.start) }
                }
            }
            let target = first ?? 8 * 60
            let hour = min(max(target / 60, startHour), endHour - 1)
            Task { @MainActor in proxy.scrollTo("tt-hour-\(hour)", anchor: .top) }
        }
    }

    private func focusKey(_ flags: TimetableGridFlags) -> String {
        if flags.focusIds.isEmpty { return "" }
        return flags.focusIds.sorted().map { String($0) }.joined(separator: ",") + "|" + rangeKey
    }

    /// 확인을 기다리는 블록이 생기면 그 블록이 보이게 스크롤
    private func scrollToFocus(_ proxy: ScrollViewProxy, _ flags: TimetableGridFlags) {
        if flags.focusIds.isEmpty { return }
        var best: Int?
        for d in days {
            for t in d.tasks where flags.focusIds.contains(t.taskId) {
                if let r = TimetableLayout.shownMinutes(d.date, t) { best = min(best ?? r.start, r.start) }
            }
        }
        guard let b = best else { return }
        let hour = min(max(Int(floor(Double(b) / 60)) - 1, startHour), endHour - 1)
        Task { @MainActor in
            withAnimation(.easeInOut(duration: 0.3)) { proxy.scrollTo("tt-hour-\(hour)", anchor: .top) }
        }
    }
}

extension TimetableGridView where Footer == EmptyView {
    init(board: TimetableBoardModel, days: [TimetableDay], activities: [ActualActivity] = [],
         startHour: Int, endHour: Int, planVsActual: Bool = false, highlightIds: Set<Int> = [],
         onDayClick: ((String) -> Void)? = nil, emptyClickEnabled: Bool = false) {
        self.init(board: board, days: days, activities: activities, startHour: startHour, endHour: endHour,
                  planVsActual: planVsActual, highlightIds: highlightIds, onDayClick: onDayClick,
                  emptyClickEnabled: emptyClickEnabled, footer: { EmptyView() })
    }
}

// MARK: - 길게 눌러 끌기 + 탭 (블록 공통 제스처)

/// 길게 누르면 끌기, 짧게 누르면 탭. 끄는 중이 아니면 바깥 ScrollView 는 평소처럼 스크롤돼요.
///
/// 탭은 `.onTapGesture` 로 따로 받아요.
/// 예전엔 `길게 누르기→끌기` 제스처에 `.exclusively(before: TapGesture())` 로 탭을 붙였는데,
/// 이 방식은 "길게 누르기가 실패했다"는 판정이 나야만 탭이 불려요. ScrollView 안에서는 손을 떼는 순간
/// 길게 누르기가 실패가 아니라 취소로 끝나는 경우가 있어서 탭이 아예 안 불렸어요 → 블록을 눌러도 상세가 안 떴어요.
/// 이제 탭은 탭대로, 길게 누르기→끌기는 함께(simultaneous) 받고, 들어 올린 뒤 손을 뗀 건 탭으로 치지 않아요.
private struct TimetableBlockGesture: ViewModifier {
    let enabled: Bool
    let holdSeconds: Double
    let onTap: () -> Void
    let onChanged: (DragGesture.Value) -> Void
    let onEnded: () -> Void
    let onCancelled: () -> Void
    /// 길게 누르기가 끝난 순간 (아직 손가락을 움직이기 전) — 블록을 "들어 올려" 보여줘요
    let onLift: () -> Void
    /// 끄는 중인지 — 제스처가 취소되면(전화 등) false 로 돌아가서 정리할 수 있게
    @GestureState private var pressing = false
    /// 이번 터치에서 들어 올렸는지 — 들어 올렸다 놓은 건 탭(상세 열기)으로 치지 않아요
    @State private var liftedAt: Date?
    /// 움직이지 않고 2초(누르기 포함 · DESIGN_SPEC 13.1) 꾹 누르면 — 묶음 고르기 (nil 이면 안 해요)
    let onHoldLong: (() -> Void)?
    /// 들어 올린 뒤 손가락이 움직인 가장 먼 거리
    @State private var maxMove: CGFloat = 0
    /// 이번 터치에서 묶음 고르기로 넘어갔는지 — 그러면 끌기 이벤트를 더 보내지 않아요
    @State private var holdTriggered = false
    @State private var holdTask: Task<Void, Never>?
    /// 손을 뗀 시각 — 꾹 누르기(들어 올리기 · 묶음 고르기)로 끝난 터치가 탭으로도 불리지 않게
    @State private var releasedAt: Date?

    init(enabled: Bool, holdSeconds: Double = TimetableGeo.holdSeconds, onTap: @escaping () -> Void,
         onChanged: @escaping (DragGesture.Value) -> Void, onEnded: @escaping () -> Void,
         onCancelled: @escaping () -> Void, onLift: @escaping () -> Void = {},
         onHoldLong: (() -> Void)? = nil) {
        self.enabled = enabled
        self.holdSeconds = holdSeconds
        self.onTap = onTap
        self.onChanged = onChanged
        self.onEnded = onEnded
        self.onCancelled = onCancelled
        self.onLift = onLift
        self.onHoldLong = onHoldLong
    }

    @ViewBuilder
    func body(content: Content) -> some View {
        if enabled {
            content
                .contentShape(Rectangle())
                .onTapGesture { tapIfNotLifted() }
                .simultaneousGesture(gesture)
                .onChange(of: pressing) { _, isPressing in
                    if isPressing {
                        liftedAt = Date()
                        maxMove = 0
                        holdTriggered = false
                        onLift()
                        startHoldTimer()
                        return
                    }
                    holdTask?.cancel()
                    holdTask = nil
                    releasedAt = Date()
                    // 묶음 고르기로 넘어간 터치는 끌기가 이미 정리됐어요
                    if holdTriggered {
                        holdTriggered = false
                        return
                    }
                    // 정상 종료면 onEnded 가 먼저 정리해서 아무 일도 안 일어나요
                    let cancel = onCancelled
                    Task { @MainActor in cancel() }
                }
        } else {
            content
                .contentShape(Rectangle())
                .onTapGesture { onTap() }
        }
    }

    /// 들어 올린 뒤 (2초 − 누르기 시간) 동안 거의 움직이지 않으면 묶음 고르기
    private func startHoldTimer() {
        holdTask?.cancel()
        guard let onHoldLong else { return }
        let wait = max(0.5, TimetableGeo.groupHoldSeconds - holdSeconds)
        holdTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: UInt64(wait * 1_000_000_000))
            guard !Task.isCancelled, pressing, maxMove < 10 else { return }
            holdTriggered = true
            onHoldLong()
        }
    }

    private func tapIfNotLifted() {
        // 들어 올렸던 터치(오래 누른 것 포함)는 탭이 아니에요 — 묶음 고르기로 넘어간 뒤 손을 떼도 상세가 안 열려요
        let recentRelease = releasedAt.map { Date().timeIntervalSince($0) < 0.4 } ?? false
        if recentRelease || (liftedAt.map { Date().timeIntervalSince($0) < TimetableGeo.groupHoldSeconds + 1 } ?? false) {
            liftedAt = nil
            releasedAt = nil
            return
        }
        liftedAt = nil
        onTap()
    }

    private var gesture: some Gesture {
        let changed = onChanged
        let ended = onEnded
        return LongPressGesture(minimumDuration: holdSeconds)
            .sequenced(before: DragGesture(minimumDistance: 0, coordinateSpace: TimetableGeo.space))
            .updating($pressing) { value, state, _ in
                if case .second(true, _) = value { state = true }
            }
            .onChanged { value in
                if case .second(true, let d?) = value {
                    maxMove = max(maxMove, hypot(d.translation.width, d.translation.height))
                    if !holdTriggered { changed(d) }
                }
            }
            .onEnded { value in
                if case .second(true, _) = value, !holdTriggered { ended() }
            }
    }
}

// MARK: - 날짜 머리글

private struct TimetableHeaderRow: View {
    let days: [TimetableDay]
    let metrics: TimetableGridMetrics
    let planVsActual: Bool
    let onDayClick: ((String) -> Void)?

    var body: some View {
        let today = KST.today()
        let single = days.count == 1
        HStack(spacing: 0) {
            Color.clear.frame(width: TimetableGeo.labelWidth)
            ForEach(days) { d in
                Group {
                    if single {
                        TimetableDayHeaderCell(day: d, today: today, colWidth: metrics.colWidth, planVsActual: planVsActual)
                    } else {
                        TimetableWeekHeaderCell(day: d, today: today, onDayClick: onDayClick)
                    }
                }
                .frame(width: metrics.colWidth, height: metrics.headerHeight)
                .overlay(alignment: .leading) { Rectangle().fill(Color.borderSoft).frame(width: 0.5) }
            }
        }
        .frame(width: metrics.width, height: metrics.headerHeight, alignment: .leading)
        .background(Color.surface)
    }
}

private struct TimetableWeekHeaderCell: View {
    let day: TimetableDay
    let today: String
    let onDayClick: ((String) -> Void)?

    var body: some View {
        if let onDayClick {
            let date = day.date
            Button { onDayClick(date) } label: { content }
                .buttonStyle(.plain)
                .accessibilityLabel("\(day.date) 일간 보기")
        } else {
            content
        }
    }

    private var content: some View {
        let wd = KST.weekdayIndex(day.date)
        let isToday = day.date == today
        let numberColor: Color = isToday ? .calendarTodayFg : (wd == 6 ? .calendarHoliday : (wd == 5 ? .calendarSaturday : .textPrimary))
        return VStack(spacing: 2) {
            Text(KST.weekdayLabel[wd])
                .font(.pt(11, .semibold))
                .foregroundStyle(isToday ? Color.textPrimary : Color.textTertiary)
            Text("\(KST.dayOfMonth(day.date))")
                .font(.pt(14, .bold))
                .foregroundStyle(numberColor)
                .frame(width: 26, height: 26)
                .background(isToday ? Color.calendarToday : Color.clear, in: Circle())
            Text(statText)
                .font(.ptMono(9))
                .foregroundStyle(Color.ink3)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .contentShape(Rectangle())
    }

    /// 좁은 칸이라 짧게: 아직 오지 않은 날은 개수, 지난 날 · 오늘은 실행률
    private var statText: String {
        let s = day.stats
        if s.total == 0 { return "–" }
        if day.date > today { return "\(s.total)개" }
        return "\(s.rate ?? 0)%"
    }
}

private struct TimetableDayHeaderCell: View {
    let day: TimetableDay
    let today: String
    let colWidth: CGFloat
    let planVsActual: Bool

    var body: some View {
        let wd = KST.weekdayIndex(day.date)
        let isToday = day.date == today
        let numberColor: Color = isToday ? .calendarTodayFg : (wd == 6 ? .calendarHoliday : (wd == 5 ? .calendarSaturday : .textPrimary))
        VStack(spacing: 4) {
            HStack(spacing: 8) {
                Text("\(KST.dayOfMonth(day.date))")
                    .font(.pt(15, .bold))
                    .foregroundStyle(numberColor)
                    .frame(width: 32, height: 32)
                    .background(isToday ? Color.calendarToday : Color.clear, in: Circle())
                VStack(alignment: .leading, spacing: 1) {
                    Text("\(KST.weekdayLabel[wd])요일")
                        .font(.pt(12, .semibold))
                        .foregroundStyle(isToday ? Color.textPrimary : Color.textTertiary)
                    Text(statText).font(.pt(11)).foregroundStyle(Color.ink3).lineLimit(1)
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 10)
            // 일간 "계획 | 실제" 칸 이름 — 아래 격자와 같은 50% · 50% (DESIGN_SPEC 9.4)
            if planVsActual {
                HStack(spacing: 0) {
                    Text("📋 계획한 일")
                        .padding(.leading, 10)
                        .frame(width: colWidth * 0.5, alignment: .leading)
                    Text("✅ 실제로 한 일")
                        .padding(.leading, 6)
                        .frame(width: colWidth * 0.5, alignment: .leading)
                        .overlay(alignment: .leading) { Rectangle().fill(Color.borderSoft).frame(width: 1) }
                }
                .font(.pt(11, .semibold))
                .foregroundStyle(Color.ink3)
                .lineLimit(1)
            }
        }
    }

    /// 아직 오지 않은 날은 실행률 대신 개수
    private var statText: String {
        let s = day.stats
        if s.total == 0 { return "Task 없음" }
        if day.date > today { return "Task \(s.total)개" }
        return "실행률 \(s.rate ?? 0)%"
    }
}

// MARK: - 시간 미정 줄

private struct TimetableUnscheduledRow: View {
    let board: TimetableBoardModel
    let days: [TimetableDay]
    let metrics: TimetableGridMetrics
    let flags: TimetableGridFlags
    @Binding var expanded: Set<String>
    let onDrag: TimetableDragHandler
    let onDragEnd: (String) -> Void

    var body: some View {
        HStack(spacing: 0) {
            Text("시간\n미정")
                .font(.pt(10))
                .foregroundStyle(Color.ink3)
                .multilineTextAlignment(.center)
                .frame(width: TimetableGeo.labelWidth)
            ForEach(days) { d in
                TimetableUnscheduledCell(board: board, day: d, flags: flags, compact: days.count > 1,
                                         expanded: expanded.contains(d.date),
                                         onExpand: { expanded.insert(d.date) },
                                         onDrag: onDrag, onDragEnd: onDragEnd)
                    .frame(width: metrics.colWidth, height: metrics.unscheduledHeight)
                    .overlay(alignment: .leading) { Rectangle().fill(Color.borderSoft).frame(width: 0.5) }
            }
        }
        .frame(width: metrics.width, height: metrics.unscheduledHeight, alignment: .leading)
        .background(Color.surface)
        .overlay(alignment: .top) { Rectangle().fill(Color.borderSoft).frame(height: 0.5) }
    }
}

private struct TimetableUnscheduledCell: View {
    let board: TimetableBoardModel
    let day: TimetableDay
    let flags: TimetableGridFlags
    let compact: Bool
    let expanded: Bool
    let onExpand: () -> Void
    let onDrag: TimetableDragHandler
    let onDragEnd: (String) -> Void

    var body: some View {
        let tasks = day.unscheduledTasks
        let shown = expanded ? tasks : Array(tasks.prefix(TimetableGeo.unscheduledVisible))
        let more = tasks.count - shown.count
        let target = isTarget
        ScrollView(.vertical, showsIndicators: false) {
            VStack(alignment: .leading, spacing: 4) {
                if target {
                    Text("여기에 놓으면 시간 미정")
                        .font(.pt(compact ? 9 : 11, .semibold))
                        .foregroundStyle(Color.textBrand)
                        .lineLimit(2)
                }
                ForEach(shown) { t in chip(t) }
                if more > 0 {
                    Button("+\(more)개 더보기") {
                        board.gridTrack("timetable.unscheduled_more_click", ["view_date": day.date, "remaining_count": more])
                        onExpand()
                    }
                        .font(.pt(compact ? 9 : 11))
                        .foregroundStyle(Color.ink3)
                        .buttonStyle(.plain)
                }
            }
            .padding(compact ? 3 : 5)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .scrollDisabled(flags.drag != nil || !expanded)
        .background(target ? Color.brandSoft : Color.surface)
        .overlay {
            if target {
                Rectangle().stroke(Color.brand, style: StrokeStyle(lineWidth: 2, dash: [4, 3])).padding(1)
            }
        }
    }

    private var isTarget: Bool {
        guard case .unscheduled(let d)? = flags.drag?.target, let src = flags.drag?.source else { return false }
        // 시간 미정 칩을 제 칸 위에 그대로 두면 바뀌는 게 없어요
        if src.start == nil && src.date == d { return false }
        return d == day.date
    }

    private func chip(_ t: TimeBlockTask) -> some View {
        let style = TimetableTaskStyle(t)
        let owner = "u-\(t.taskId)"
        let source = TimetableDragSource.task(t, date: day.date, start: nil, end: nil, group: [])
        let dragging = flags.drag?.draggingTaskIds.contains(t.taskId) ?? false
        let selected = flags.selectedTaskId == t.taskId
        return Text(TimetableRules.title(t.emoji, t.taskName))
            .font(.pt(compact ? 9.5 : 11, .semibold))
            .foregroundStyle(style.titleColor)
            .strikethrough(style.done)
            .lineLimit(1)
            .padding(.leading, compact ? 6 : 10)
            .padding(.trailing, compact ? 3 : 7)
            .frame(maxWidth: .infinity, minHeight: TimetableGeo.chipHeight, maxHeight: TimetableGeo.chipHeight, alignment: .leading)
            .background(TimetableTaskBackground(style: style))
            // 왼쪽은 각지게 — 왼쪽 목표 색 막대가 위아래 끝까지 곧게 (TimeBlock 과 같게, 2026-10)
            .clipShape(TimetableShapes.block(radius: Radius.chipSm, squareLeading: true))
            .overlay {
                if selected { TimetableShapes.block(radius: Radius.chipSm, squareLeading: true).strokeBorder(Color.borderFocus, lineWidth: 2) }
            }
            .opacity(dragging ? 0.3 : ((flags.dimOthers && !flags.isLifted(owner)) ? 0.5 : (style.done ? 0.55 : 1)))
            .scaleEffect(flags.isLifted(owner) && !dragging ? 1.06 : 1)
            .shadow(color: .black.opacity(flags.isLifted(owner) && !dragging ? 0.25 : 0), radius: 8, y: 4)
            .modifier(TimetableBlockGesture(
                enabled: TimetableRules.canDragTask(t),
                onTap: { board.selectTask(t) },
                onChanged: { v in onDrag(source, .move, owner, v) },
                onEnded: { onDragEnd(owner) },
                onCancelled: { board.cancelDrag(ownerId: owner) },
                onLift: { board.lift(ownerId: owner) }
            ))
    }
}

// MARK: - 시간 눈금 (가로줄 + 왼쪽 시각)

private struct TimetableHourBackground: View {
    let startHour: Int
    let endHour: Int
    let width: CGFloat

    var body: some View {
        VStack(spacing: 0) {
            ForEach(startHour..<max(endHour, startHour + 1), id: \.self) { h in
                ZStack(alignment: .topLeading) {
                    Rectangle()
                        .fill(Color.borderSoft)
                        .frame(height: 1)
                        .padding(.leading, TimetableGeo.labelWidth)
                    // 24시 이후는 다음 날 새벽 (취침이 자정 뒤일 때)
                    if h != startHour {
                        Text(String(format: "%02d:00", h % 24))
                            .font(.ptMono(10))
                            .foregroundStyle(Color.ink4)
                            .frame(width: TimetableGeo.labelWidth - 6, alignment: .trailing)
                            .offset(y: -7)
                    }
                }
                .frame(width: width, height: TimetableGeo.hourHeight, alignment: .topLeading)
                .id("tt-hour-\(h)")
            }
        }
        .allowsHitTesting(false)
    }
}

// MARK: - 날짜 칸 하나

/// 실제 기록과 Task 를 같이 놓을 때 열을 다시 나누려고 한 타입으로 묶어요
private enum TimetableMixedItem {
    case task(TimeBlockTask)
    case activity(ActualActivity)
}

private enum TimetableMixedLayout {
    static func convert<A, B>(_ p: TimetableLayout.Placed<A>, _ item: B) -> TimetableLayout.Placed<B> {
        TimetableLayout.Placed(item: item, top: p.top, bottom: p.bottom, start: p.start, end: p.end,
                               clippedTop: p.clippedTop, clippedBottom: p.clippedBottom, col: p.col, cols: p.cols, part: p.part)
    }

    /// Task 와 실제 기록을 같이 놓고 겹치는 것끼리만 나란히 (col / cols 를 다시 정해요)
    static func layout(_ tasks: [TimetableLayout.Placed<TimeBlockTask>],
                       _ acts: [TimetableLayout.Placed<ActualActivity>])
        -> (tasks: [TimetableLayout.Placed<TimeBlockTask>], acts: [TimetableLayout.Placed<ActualActivity>]) {
        var mixed: [TimetableLayout.Placed<TimetableMixedItem>] = []
        for p in tasks { mixed.append(convert(p, TimetableMixedItem.task(p.item))) }
        for p in acts { mixed.append(convert(p, TimetableMixedItem.activity(p.item))) }
        let laid = TimetableLayout.layoutColumns(mixed)
        var outTasks: [TimetableLayout.Placed<TimeBlockTask>] = []
        var outActs: [TimetableLayout.Placed<ActualActivity>] = []
        for p in laid {
            switch p.item {
            case .task(let t): outTasks.append(convert(p, t))
            case .activity(let a): outActs.append(convert(p, a))
            }
        }
        return (outTasks, outActs)
    }
}

/// 칸 하나에 그릴 블록 위치 (웹 TimeGrid 날짜 칸의 계산 부분)
private struct TimetableColumnLayout {
    var tasks: [TimetableLayout.Placed<TimeBlockTask>]
    var doneTasks: [TimetableLayout.Placed<TimeBlockTask>]
    var acts: [TimetableLayout.Placed<ActualActivity>]
    /// 칸을 "계획 50% | 실제 50%"로 나누는지
    var split: Bool
    /// 일간 계획 | 실제 비교
    var compare: Bool

    init(day: TimetableDay, activities: [ActualActivity], from: Int, to: Int, single: Bool, planVsActual: Bool) {
        let compare = single && planVsActual
        let acts0 = TimetableLayout.placeActivities(day.date, activities, from: from, to: to)
        // 일간: 실제 기록이 있으면 나눠서 나란히 / 주간: 칸이 좁아서 한 열에 같이 그려요 (겹칠 때만 나란히)
        let split = compare || (single && !acts0.isEmpty)
        var tasks = TimetableLayout.placeTasks(day, from: from, to: to, basis: compare ? .planned : .shown)
        var done: [TimetableLayout.Placed<TimeBlockTask>] = compare ? TimetableLayout.placeTasks(day, from: from, to: to, basis: .done) : []
        var acts = acts0
        if compare {
            // 실제 칸에는 완료한 Task(실제 시각)와 실제로 한 일 기록을 같이, 겹치면 나란히
            let r = TimetableMixedLayout.layout(done, acts0)
            done = r.tasks
            acts = r.acts
        } else if !split && !acts0.isEmpty {
            let r = TimetableMixedLayout.layout(tasks, acts0)
            tasks = r.tasks
            acts = r.acts
        }
        self.tasks = tasks
        self.doneTasks = done
        self.acts = acts
        self.split = split
        self.compare = compare
    }
}

private struct TimetableGhostInfo {
    var title: String
    var style: TimetableTaskStyle?
    var start: Int
    var end: Int
    var invalid: String?
}

private struct TimetableDayColumn: View {
    let board: TimetableBoardModel
    let day: TimetableDay
    let metrics: TimetableGridMetrics
    let flags: TimetableGridFlags
    let activities: [ActualActivity]
    let single: Bool
    let planVsActual: Bool
    let emptyClickEnabled: Bool
    let onDrag: TimetableDragHandler
    let onDragEnd: (String) -> Void

    var body: some View {
        let layout = TimetableColumnLayout(day: day, activities: activities, from: metrics.from, to: metrics.to,
                                           single: single, planVsActual: planVsActual)
        let w = metrics.colWidth
        let planW = layout.split ? w * 0.5 : w
        let fade = flags.fadeOthers && !flags.focusIds.isEmpty
        ZStack(alignment: .topLeading) {
            columnBackground
            sleepLayer(fade: fade)
            fixedLayer(fade: fade)
            habitLayer(areaW: planW)
            carriedLayer(areaW: planW, fade: fade)
            taskLayer(layout.tasks, areaX: 0, areaW: planW, planned: layout.compare, interactive: true)
            if !layout.split && !layout.acts.isEmpty {
                activityLayer(layout.acts, areaX: 0, areaW: w)
                    .opacity(flags.dimOthers ? 0.5 : (fade ? 0.55 : 1))
            }
            if layout.split {
                splitLayer(layout, x: planW, w: w - planW)
                    .opacity((flags.dimOthers && !flags.actualActive) ? 0.5 : (fade ? 0.55 : 1))
            }
            ghostLayer
            TimetableNowLine(date: day.date, from: metrics.from, to: metrics.to)
        }
        .frame(width: w, height: metrics.height, alignment: .topLeading)
    }

    // MARK: 다른 날로 옮겨진 Task 의 흔적 (2026-10)

    /// 못 해서 다른 날로 옮겨진 Task 를 원래 자리에 흐린 점선 "* 이름 → 날짜"로 남겨요. 끌 수 없고, 누르면 옮겨 간 날로
    @ViewBuilder
    private func carriedLayer(areaW: CGFloat, fade: Bool) -> some View {
        let items = day.carriedOut
        if !items.isEmpty {
            ForEach(items) { c in
                let top = max(KST.minutesFrom(day.date, c.start), metrics.from)
                let bottom = min(max(KST.minutesFrom(day.date, c.end), top + 20), metrics.to)
                if bottom > top {
                    carriedBlock(c, top: top, bottom: bottom, areaW: areaW)
                        .opacity(flags.dimOthers || fade ? 0.35 : 1)
                }
            }
        }
    }

    private func carriedBlock(_ c: CarriedOutBlock, top: Int, bottom: Int, areaW: CGFloat) -> some View {
        let r = rect(top: top, bottom: bottom, col: 0, cols: 1, areaX: 0, areaW: areaW)
        let color = Color.goal(c.goalColorCode)
        let toLabel = "\(KST.monthOf(c.toDate))/\(KST.dayOfMonth(c.toDate))"
        // 누르면 메뉴 (2026-10): 그때 했어요(되돌리고 실제 시간 입력) · 원래 시간으로 되돌리기 · 옮긴 곳 보기
        return Menu {
            Button {
                board.trackCarriedAction(taskId: c.taskId, action: "done_then")
                Task { await board.revertCarry(taskId: c.taskId, from: day.date, thenActual: true) }
            } label: { Label("그때 했어요", systemImage: "checkmark") }
            Button {
                board.trackCarriedAction(taskId: c.taskId, action: "revert")
                Task { await board.revertCarry(taskId: c.taskId, from: day.date) }
            } label: { Label("원래 시간으로 되돌리기", systemImage: "arrow.uturn.backward") }
            Button {
                board.trackCarriedAction(taskId: c.taskId, action: "go")
                AppRouter.shared.openCalendar(date: c.toDate)
            } label: { Label("옮긴 곳 보기 (\(toLabel))", systemImage: "chevron.right") }
        } label: {
            VStack(alignment: .leading, spacing: 1) {
                Text("* " + TimetableRules.title(c.emoji, c.taskName))
                    .font(.pt(single ? 12 : 9.5, .semibold))
                    .strikethrough(true, color: Color.ink4)
                    .foregroundStyle(Color.ink3)
                    .lineLimit(1)
                if r.height >= 30 {
                    Text("→ \(toLabel)로 옮김")
                        .font(.pt(single ? 11 : 9))
                        .foregroundStyle(Color.ink4)
                        .lineLimit(1)
                }
            }
            .padding(.horizontal, single ? 8 : 4)
            .padding(.vertical, 3)
            .frame(width: r.width, height: r.height, alignment: .topLeading)
            .background(color.opacity(0.06), in: RoundedRectangle(cornerRadius: Radius.chipSm))
            .overlay(RoundedRectangle(cornerRadius: Radius.chipSm)
                .strokeBorder(color.opacity(0.45), style: StrokeStyle(lineWidth: 1, dash: [4, 3])))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .simultaneousGesture(TapGesture().onEnded {
            board.trackCarriedTrace(taskId: c.taskId, traceDate: day.date, toDate: c.toDate)
        })
        .accessibilityLabel("\(c.taskName), 못 해서 \(toLabel)로 옮겼어요. 그때 했다고 남기거나 되돌릴 수 있어요")
        .offset(x: r.minX, y: r.minY)
    }

    // MARK: 배경 · 빈 칸 누르기

    @ViewBuilder
    private var columnBackground: some View {
        let isToday = day.date == KST.today()
        let base = Rectangle()
            .fill(isToday ? Color.brandSoft.opacity(0.3) : Color.clear)
            .overlay(alignment: .leading) { Rectangle().fill(Color.borderSoft).frame(width: 0.5) }
            .frame(width: metrics.colWidth, height: metrics.height)
            .contentShape(Rectangle())
        let board = self.board
        // 캘린더는 앞날 · 앞 시간도 눌러서 + 메뉴 (DESIGN_SPEC 11.4) · 홈은 지난 시간만 실제 기록
        if emptyClickEnabled && (board.emptyTapOpensMenu || day.date <= KST.today()) {
            base.onTapGesture { location in
                if board.groupSelecting { board.exitGroupSelectByTap() } else { tapEmpty(location) }
            }
        } else {
            // 빈 칸 누르기가 없는 칸도 묶음 고르기 모드면 눌러서 끝낼 수 있어요
            base.onTapGesture {
                if board.groupSelecting { board.exitGroupSelectByTap() }
            }
        }
    }

    /// 블록이 없는 빈 곳을 누르면 그 시각(30분 단위)으로 실제 기록 열기 — 지난 시간만
    private func tapEmpty(_ loc: CGPoint) {
        let raw = Double(metrics.from) + Double(loc.y / TimetableGeo.ppm)
        let minutes = Int(floor(raw / 30)) * 30
        if !board.emptyTapOpensMenu {
            let now = KST.now()
            if day.date > now.date { return }
            // 자정 뒤 칸(24:00~)은 다음 날 시각
            var nowOnDay: Int? = nil
            if day.date == now.date { nowOnDay = now.minutes } else if KST.addDays(day.date, 1) == now.date { nowOnDay = now.minutes + 1440 }
            if let n = nowOnDay, minutes >= n { return }
        }
        board.emptyClick(date: day.date, minutes: minutes)
    }

    // MARK: 위치

    private func rect(top: Int, bottom: Int, col: Int, cols: Int, areaX: CGFloat, areaW: CGFloat) -> CGRect {
        let y = metrics.y(top)
        let h = max(CGFloat(bottom - top) * TimetableGeo.ppm - 2, 16)
        let w = areaW / CGFloat(max(cols, 1))
        return CGRect(x: areaX + CGFloat(col) * w + 1, y: y, width: max(w - 2, 4), height: h)
    }

    private func blockGesture(enabled: Bool, owner: String, source: TimetableDragSource, mode: TimetableDragMode,
                         hold: Double = TimetableGeo.holdSeconds, onTap: @escaping () -> Void,
                         onHoldLong: (() -> Void)? = nil) -> TimetableBlockGesture {
        let drag = onDrag
        let end = onDragEnd
        let b = board
        return TimetableBlockGesture(enabled: enabled, holdSeconds: hold, onTap: onTap,
                                     onChanged: { v in drag(source, mode, owner, v) },
                                     onEnded: { end(owner) },
                                     onCancelled: { b.cancelDrag(ownerId: owner) },
                                     onLift: { b.lift(ownerId: owner) },
                                     onHoldLong: onHoldLong)
    }

    /// 블록 가장자리 손잡이 (길게 눌러 끌면 길이 조절, 짧게 누르면 상세)
    private func handle(owner: String, source: TimetableDragSource, mode: TimetableDragMode,
                        onTap: @escaping () -> Void) -> some View {
        Color.clear
            .frame(maxWidth: .infinity)
            .frame(height: 12)
            .contentShape(Rectangle())
            .modifier(blockGesture(enabled: true, owner: owner, source: source, mode: mode, hold: 0.25, onTap: onTap))
    }

    // MARK: 수면

    @ViewBuilder
    private func sleepLayer(fade: Bool) -> some View {
        let placed = TimetableLayout.placeSleep(day, from: metrics.from, to: metrics.to)
        ForEach(placed.indices, id: \.self) { i in
            sleepBlock(placed[i], fade: fade)
        }
    }

    private func sleepBlock(_ ps: TimetableLayout.PlacedSleep, fade: Bool) -> some View {
        let p = ps.placed
        let b = p.item
        let key = b.key
        let wake = ps.edge == .wake
        let h = max(CGFloat(p.bottom - p.top) * TimetableGeo.ppm, 16)
        let owner = "s-\(key)-\(ps.edge.rawValue)-\(day.date)"
        let source = TimetableDragSource.fixed(b, date: day.date, start: p.start, end: p.end)
        let dimmed = flags.drag?.draggingFixedKey == key || fade || (flags.dimOthers && !flags.isLifted(owner))
        let board = self.board
        return ZStack(alignment: wake ? .bottom : .top) {
            TimetableSleepBlockView(selected: flags.selectedFixedKey == key, dimmed: dimmed)
                .contentShape(Rectangle())
                .onTapGesture { board.selectFixed(b) }
            // 아침 블록은 아래 가장자리(= 기상), 밤 블록은 위 가장자리(= 취침)를 끌어서 시간을 바꿔요
            TimetableSleepEdgeLabel(edge: ps.edge, minutes: wake ? p.end : p.start, compact: !single)
                .frame(height: min(22, h))
                .modifier(blockGesture(enabled: true, owner: owner, source: source, mode: wake ? .resize : .resizeTop,
                                  hold: 0.25, onTap: { board.selectFixed(b) }))
        }
        .frame(width: metrics.colWidth, height: h)
        .offset(y: metrics.y(p.top))
    }

    // MARK: 고정 시간

    @ViewBuilder
    private func fixedLayer(fade: Bool) -> some View {
        let placed = TimetableLayout.placeFixed(day, from: metrics.from, to: metrics.to)
        ForEach(placed, id: \.item.key) { p in
            fixedBlock(p, fade: fade)
        }
    }

    private func fixedBlock(_ p: TimetableLayout.Placed<FixedBlock>, fade: Bool) -> some View {
        let b = p.item
        let r = rect(top: p.top, bottom: p.bottom, col: 0, cols: 1, areaX: 0, areaW: metrics.colWidth)
        let canDrag = TimetableRules.canDragFixed(b, date: day.date)
        let owner = "f-\(b.key)-\(day.date)"
        let source = TimetableDragSource.fixed(b, date: day.date, start: p.start, end: p.end)
        let dragging = flags.drag?.draggingFixedKey == b.key
        let lifted = flags.isLifted(owner) && !dragging
        let dimmed = dragging || fade || (flags.dimOthers && !flags.isLifted(owner))
        let board = self.board
        let showHandle = canDrag && !p.clippedBottom && r.height >= 26
        return TimetableFixedBlockView(placed: p, heightPx: r.height, compact: !single,
                                       selected: flags.selectedFixedKey == b.key, dimmed: dimmed)
            .modifier(blockGesture(enabled: canDrag, owner: owner, source: source, mode: .move, onTap: { board.selectFixed(b) }))
            .overlay(alignment: .bottom) {
                if showHandle {
                    handle(owner: owner + "-r", source: source, mode: .resize, onTap: { board.selectFixed(b) })
                }
            }
            .frame(width: r.width, height: r.height)
            .scaleEffect(lifted ? 1.05 : 1)
            .shadow(color: .black.opacity(lifted ? 0.22 : 0), radius: 10, y: 5)
            .zIndex(lifted ? 10 : 0)
            .offset(x: r.minX, y: r.minY)
    }

    // MARK: Task

    @ViewBuilder
    private func taskLayer(_ tasks: [TimetableLayout.Placed<TimeBlockTask>], areaX: CGFloat, areaW: CGFloat,
                           planned: Bool, interactive: Bool) -> some View {
        ForEach(tasks, id: \.uid) { p in
            taskBlock(p, all: tasks, areaX: areaX, areaW: areaW, planned: planned, interactive: interactive)
        }
    }

    private func taskBlock(_ p: TimetableLayout.Placed<TimeBlockTask>, all: [TimetableLayout.Placed<TimeBlockTask>],
                           areaX: CGFloat, areaW: CGFloat, planned: Bool, interactive: Bool) -> some View {
        let t = p.item
        let r = rect(top: p.top, bottom: p.bottom, col: p.col, cols: p.cols, areaX: areaX, areaW: areaW)
        // 완료한 Task 가 실제 시각에 그려진 블록(일간 "실제" 칸 · 주간)은 실제로 한 시간을 끌어서 바꿔요
        let actualDrag = !planned && TimetableRules.canDragActual(t)
        let canDrag = (interactive && TimetableRules.canDragTask(t)) || actualDrag
        // 묶음 표시: 2개 이상 묶였거나, 묶음 고르기 모드면 1개라도 (점선 테두리)
        let grouped = interactive && flags.groupIds.contains(t.taskId) && (flags.groupIds.count > 1 || board.groupSelecting)
        // 묶음에 든 Task 를 옮기면(크기 조절 말고) 같은 날의 나머지 묶음도 같이 움직여요
        var members: [TimetableGroupMember] = []
        if grouped {
            for q in all where q.item.taskId != t.taskId && flags.groupIds.contains(q.item.taskId) && TimetableRules.canDragTask(q.item) {
                members.append(TimetableGroupMember(task: q.item, date: day.date, start: q.start, end: q.end))
            }
        }
        // 일간 비교에서는 같은 Task 가 계획 칸 · 실제 칸에 둘 다 있어서 owner 를 나눠요
        let owner = "\(actualDrag ? "a" : "t")-\(t.taskId)-\(p.part)-\(day.date)"
        let actualSource = TimetableDragSource.actual(t, date: day.date, start: p.start, end: p.end, part: p.part)
        // 점심 앞뒤로 나눠 넣은 Task 는 어느 조각을 끌어도 전체 길이 한 덩어리로 옮겨요
        let taskEnd = TimetableRules.splitLength(t).map { p.start + $0 } ?? p.end
        let moveSource = actualDrag ? actualSource : TimetableDragSource.task(t, date: day.date, start: p.start, end: taskEnd, group: members)
        let resizeSource = actualDrag ? actualSource : TimetableDragSource.task(t, date: day.date, start: p.start, end: p.end, group: [])
        // 나눠 넣은 조각은 길이 조절 손잡이를 숨겨요 (한 조각 끝을 끌면 전체 길이가 어긋나요 — 옮기면 한 덩어리가 돼요)
        let splitPiece = !actualDrag && TimetableRules.splitLength(t) != nil
        let dragging = actualDrag
            ? flags.drag?.draggingActualUid == p.uid
            : interactive && (flags.drag?.draggingTaskIds.contains(t.taskId) ?? false)
        let fadedOut = (interactive || actualDrag) && (
            (flags.fadeOthers && !flags.focusIds.isEmpty && !flags.focusIds.contains(t.taskId))
            // 다른 블록을 들어 올렸거나 끄는 중이면 50%
            || (flags.dimOthers && !flags.isLifted(owner))
        )
        // 길게 눌러 들어 올린 순간 (움직이기 전): 살짝 커지고 그림자
        let lifted = (interactive || actualDrag) && flags.isLifted(owner) && !dragging
        let showTop = canDrag && !splitPiece && !p.clippedTop && r.height >= 44
        let showBottom = canDrag && !splitPiece && !p.clippedBottom && r.height >= 26
        let board = self.board
        // 2초 꾹 누르기 → 묶음 고르기 (계획 Task 만 · 고정 시간 · 실제 기록은 안 돼요)
        var holdLong: (() -> Void)? = nil
        if interactive && !actualDrag && TimetableRules.groupable(t) {
            holdLong = { board.enterGroupSelect(t, ownerId: owner) }
        }
        return TimetableTaskBlockView(placed: p, heightPx: r.height, compact: !single, planned: planned,
                                      selected: flags.selectedTaskId == t.taskId, dimmed: dragging, faded: fadedOut,
                                      focused: interactive && flags.focusIds.contains(t.taskId),
                                      isNew: interactive && flags.highlightIds.contains(t.taskId), grouped: grouped)
            .modifier(blockGesture(enabled: canDrag, owner: owner, source: moveSource, mode: .move,
                                   onTap: {
                                       // 묶음 고르기 모드: 누르면 묶음에 넣고 빼요 / 아니면 상세
                                       if board.groupSelecting && interactive {
                                           board.toggleGroupMember(t)
                                       } else {
                                           board.selectTask(t)
                                       }
                                   },
                                   onHoldLong: holdLong))
            .overlay(alignment: .top) {
                if showTop {
                    handle(owner: owner + "-rt", source: resizeSource, mode: .resizeTop, onTap: { board.selectTask(t) })
                }
            }
            .overlay(alignment: .bottom) {
                if showBottom {
                    handle(owner: owner + "-rb", source: resizeSource, mode: .resize, onTap: { board.selectTask(t) })
                }
            }
            .frame(width: r.width, height: r.height)
            .scaleEffect(lifted ? 1.06 : 1)
            .shadow(color: .black.opacity(lifted ? 0.28 : 0), radius: 12, y: 6)
            .zIndex(lifted ? 10 : 0)
            .offset(x: r.minX, y: r.minY)
    }

    // MARK: 시간대 습관 (V56)

    /// 시간대를 정한 습관 — 연한 초록 점선 블록, 누르면 체크 · 체크 풀기 (Task 블록 아래 층)
    private func habitLayer(areaW: CGFloat) -> some View {
        let placed = TimetableLayout.placeHabits(day, from: metrics.from, to: metrics.to)
        let date = day.date
        return ZStack(alignment: .topLeading) {
            ForEach(placed, id: \.item.habitId) { p in
                let r = rect(top: p.top, bottom: p.bottom, col: 0, cols: 1, areaX: 0, areaW: areaW)
                TimetableHabitBlockView(habit: p.item, compact: !single, date: date, onTap: {
                    board.gridTrack("timetable.habit_block_toggle", [
                        "habit_id": p.item.habitId, "check_action": p.item.checked ? "uncomplete" : "complete",
                        "date_relation": CAL.relation(date),
                    ])
                })
                    .frame(width: r.width, height: r.height)
                    .offset(x: r.minX, y: r.minY)
            }
        }
    }

    // MARK: 실제로 한 일

    private func activityLayer(_ acts: [TimetableLayout.Placed<ActualActivity>], areaX: CGFloat, areaW: CGFloat) -> some View {
        let board = self.board
        return ZStack(alignment: .topLeading) {
            ForEach(acts, id: \.item.activityId) { p in
                let r = rect(top: p.top, bottom: p.bottom, col: p.col, cols: p.cols, areaX: areaX, areaW: areaW)
                TimetableActivityBlockView(placed: p, heightPx: r.height, compact: !single,
                                           selected: flags.selectedActivityId == p.item.activityId)
                    .contentShape(Rectangle())
                    .onTapGesture { board.selectActivity(p.item) }
                    .frame(width: r.width, height: r.height)
                    .offset(x: r.minX, y: r.minY)
            }
        }
    }

    /// 일간 오른쪽 40% "실제" 칸: 완료한 Task(실제 시각) + 실제로 한 일
    private func splitLayer(_ layout: TimetableColumnLayout, x: CGFloat, w: CGFloat) -> some View {
        let height = metrics.height
        return ZStack(alignment: .topLeading) {
            Path { path in
                path.move(to: CGPoint(x: x, y: 0))
                path.addLine(to: CGPoint(x: x, y: height))
            }
            .stroke(Color.line, style: StrokeStyle(lineWidth: 1, dash: [3, 3]))
            .allowsHitTesting(false)
            taskLayer(layout.doneTasks, areaX: x, areaW: w, planned: false, interactive: false)
            activityLayer(layout.acts, areaX: x, areaW: w)
        }
    }

    // MARK: 드래그 미리보기

    private var ghost: TimetableGhostInfo? {
        guard let st = flags.drag, case let .grid(d, s, e)? = st.target, d == day.date else { return nil }
        switch st.source {
        case let .task(t, _, _, _, _):
            return TimetableGhostInfo(title: TimetableRules.title(t.emoji, t.taskName), style: TimetableTaskStyle(t),
                                      start: s, end: e, invalid: st.invalid)
        case let .fixed(b, _, _, _):
            return TimetableGhostInfo(title: b.title, style: nil, start: s, end: e, invalid: st.invalid)
        case let .actual(t, _, _, _, _):
            return TimetableGhostInfo(title: TimetableRules.title(t.emoji, t.taskName) + " · 실제", style: TimetableTaskStyle(t),
                                      start: s, end: e, invalid: st.invalid)
        }
    }

    /// 묶음 드래그 중 같이 움직이는 나머지 Task 들의 새 자리
    private var groupGhosts: [TimetableGroupMember] {
        guard let st = flags.drag, !st.source.group.isEmpty, case let .grid(d, s, e)? = st.target else { return [] }
        return Array(TimetableDrag.groupMoves(st.source, date: d, start: s, end: e).dropFirst()).filter { $0.date == day.date }
    }

    /// 끄는 블록이 원래 그려진 가로 자리 (칸 안의 x · 폭) — 겹쳐서 반 폭이면 끄는 동안에도 반 폭 (DESIGN_SPEC 9.7)
    private func ghostFrame(_ src: TimetableDragSource) -> (x: CGFloat, w: CGFloat) {
        let full: (x: CGFloat, w: CGFloat) = (1, max(metrics.colWidth - 2, 4))
        guard let srcDay = board.days.first(where: { $0.date == src.date }) else { return full }
        let lay = TimetableColumnLayout(day: srcDay, activities: activities, from: metrics.from, to: metrics.to,
                                        single: single, planVsActual: planVsActual)
        let w = metrics.colWidth
        let planW = lay.split ? w * 0.5 : w
        switch src {
        case let .task(t, _, _, _, _):
            if let p = lay.tasks.first(where: { $0.item.taskId == t.taskId }) {
                let r = rect(top: p.top, bottom: p.bottom, col: p.col, cols: p.cols, areaX: 0, areaW: planW)
                return (r.minX, r.width)
            }
        case let .actual(t, _, _, _, part):
            let list = lay.split ? lay.doneTasks : lay.tasks
            let areaX: CGFloat = lay.split ? planW : 0
            let areaW: CGFloat = lay.split ? w - planW : planW
            if let p = list.first(where: { $0.item.taskId == t.taskId && $0.part == part }) {
                let r = rect(top: p.top, bottom: p.bottom, col: p.col, cols: p.cols, areaX: areaX, areaW: areaW)
                return (r.minX, r.width)
            }
        case .fixed:
            break
        }
        return full
    }

    @ViewBuilder
    private var ghostLayer: some View {
        if let g = ghost, let src = flags.drag?.source {
            let h = max(CGFloat(g.end - g.start) * TimetableGeo.ppm - 2, 18)
            let gf = ghostFrame(src)
            TimetableDragGhostView(title: g.title, taskStyle: g.style, start: g.start, end: g.end,
                                   invalid: g.invalid, heightPx: h)
                .frame(width: gf.w, height: h)
                // 손가락으로 들고 있는 느낌 — 살짝 크게 + 진한 그림자
                .scaleEffect(1.05)
                .shadow(color: .black.opacity(0.18), radius: 16, y: 10)
                .zIndex(20)
                .offset(x: gf.x, y: metrics.y(g.start))
        }
        let invalid = flags.drag?.invalid
        ForEach(groupGhosts, id: \.task.taskId) { m in
            let h = max(CGFloat(m.end - m.start) * TimetableGeo.ppm - 2, 14)
            TimetableDragGhostView(title: TimetableRules.title(m.task.emoji, m.task.taskName),
                                   taskStyle: TimetableTaskStyle(m.task), start: m.start, end: m.end,
                                   invalid: nil, heightPx: h)
                .overlay {
                    if invalid != nil { RoundedRectangle(cornerRadius: Radius.event).stroke(Color.statusErrorFg, lineWidth: 2) }
                }
                .opacity(0.9)
                .frame(width: max(metrics.colWidth - 2, 4), height: h)
                .offset(x: 1, y: metrics.y(m.start))
        }
    }
}

// MARK: - 지금 시각 선

/// 새벽 00:30 이고 어제 칸이 01:00 까지 이어지면 어제 칸 맨 아래(24:30)에 그려요
private struct TimetableNowLine: View {
    let date: String
    let from: Int
    let to: Int

    var body: some View {
        TimelineView(.everyMinute) { ctx in
            let now = KST.now(ctx.date)
            if let m = minute(now) {
                let y = CGFloat(m - from) * TimetableGeo.ppm
                // 지금 시각 선: 1.5pt action/primary + 왼쪽 끝 8pt 점 (DESIGN_SPEC 9.6)
                ZStack(alignment: .topLeading) {
                    Rectangle().fill(Color.actionPrimary).frame(height: 1.5).offset(y: y - 0.75)
                    Circle().fill(Color.actionPrimary).frame(width: 8, height: 8).offset(x: -4, y: y - 4)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private func minute(_ now: (date: String, minutes: Int)) -> Int? {
        if date == now.date && now.minutes >= from && now.minutes <= to { return now.minutes }
        if KST.addDays(date, 1) == now.date && now.minutes + 1440 <= to { return now.minutes + 1440 }
        return nil
    }
}

/// 지금 시각 글자 — 왼쪽 시간 눈금 자리에 Caption/SM · primary (보이는 날 중 오늘이 있을 때만)
private struct TimetableNowGutterLabel: View {
    let dates: [String]
    let from: Int
    let to: Int

    var body: some View {
        TimelineView(.everyMinute) { ctx in
            let now = KST.now(ctx.date)
            if let m = minute(now) {
                let y = CGFloat(m - from) * TimetableGeo.ppm
                Text(KST.formatMinutes(now.minutes))
                    .font(.captionSM)
                    .monospacedDigit()
                    .foregroundStyle(Color.actionPrimary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                    .frame(width: TimetableGeo.labelWidth - 4, height: 14, alignment: .trailing)
                    .background(Color.bgSurface)
                    .offset(y: y - 7)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private func minute(_ now: (date: String, minutes: Int)) -> Int? {
        if dates.contains(now.date) && now.minutes >= from && now.minutes <= to { return now.minutes }
        if dates.contains(KST.addDays(now.date, -1)) && now.minutes + 1440 <= to { return now.minutes + 1440 }
        return nil
    }
}

#Preview("주간") {
    let start = KST.startOfWeek(KST.today())
    let board = TimetableBoardModel.sample(start: start, end: KST.addDays(start, 6))
    let days = board.days
    let hours = TimetableLayout.visibleHours(days, activities: board.activities)
    return TimetableGridView(board: board, days: days, activities: board.activities,
                             startHour: hours.startHour, endHour: hours.endHour,
                             highlightIds: [2001], emptyClickEnabled: true)
        .padding(.horizontal, 16)
        .canvasBackground()
}

#Preview("일간 계획 | 실제") {
    let d = KST.today()
    let board = TimetableBoardModel.sample(start: d, end: d)
    let days = board.days
    let hours = TimetableLayout.visibleHours(days, activities: board.activities)
    return TimetableGridView(board: board, days: days, activities: board.activities,
                             startHour: hours.startHour, endHour: hours.endHour,
                             planVsActual: true, emptyClickEnabled: true)
        .padding(.horizontal, 16)
        .canvasBackground()
}

// MARK: - 시간대 습관 블록

struct TimetableHabitBlockView: View {
    let habit: HabitBlock
    var compact = false
    let date: String
    /// 누를 때 (timetable.habit_block_toggle)
    var onTap: (() -> Void)? = nil
    @State private var busy = false

    private let green = Color.statusSuccessFg

    var body: some View {
        Button {
            onTap?()
            Task { await toggle() }
        } label: {
            HStack(alignment: .top, spacing: 3) {
                NSymbol(habit.checked ? "checkmark.circle.fill" : "circle", size: compact ? 9 : 11, weight: .semibold)
                Text((habit.emoji.map { "\($0) " } ?? "") + habit.name)
                    .font(.pt(compact ? 10 : 11, .semibold))
                    .strikethrough(habit.checked)
                    .lineLimit(2)
                Spacer(minLength: 0)
            }
            .foregroundStyle(habit.checked ? Color.success : green)
            .padding(.horizontal, 5)
            .padding(.vertical, 3)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .background((habit.checked ? Color.successSoft : green.opacity(0.06)),
                        in: RoundedRectangle(cornerRadius: 6))
            .overlay(RoundedRectangle(cornerRadius: 6)
                .strokeBorder(green.opacity(0.4), style: StrokeStyle(lineWidth: 1, dash: [3, 2])))
        }
        .buttonStyle(.plain)
        .disabled(busy)
        .accessibilityLabel("\(habit.name) \(habit.checked ? "체크 풀기" : "체크하기")")
    }

    @MainActor
    private func toggle() async {
        busy = true
        defer { busy = false }
        _ = await Mutation.run(invalidate: [.timetable, .habits, .home]) {
            if habit.checked {
                try await API.send("DELETE", "/v1/habits/\(habit.habitId)/checks/\(date)")
            } else {
                try await API.send("PUT", "/v1/habits/\(habit.habitId)/checks/\(date)")
            }
        }
    }
}

#Preview("습관 블록") {
    VStack(spacing: 8) {
        TimetableHabitBlockView(habit: HabitBlock(habitId: 1, name: "샤워하기", emoji: "🚿",
                                                 start: "2026-10-13T20:00:00", end: "2026-10-13T20:30:00", checked: false),
                                date: "2026-10-13")
            .frame(width: 200, height: 40)
        TimetableHabitBlockView(habit: HabitBlock(habitId: 2, name: "물 2L", emoji: "💧",
                                                 start: "2026-10-13T08:00:00", end: "2026-10-13T08:20:00", checked: true),
                                compact: true, date: "2026-10-13")
            .frame(width: 60, height: 30)
    }
    .padding()
}
