// Features/Calendar/CalendarScreen.swift
//
// 캘린더 탭 루트 (일간 · 주간 · 월간).
// 웹 원본: features/timetable/ui/CalendarView.tsx + app/(main)/calendar/page.tsx
//
// 웹 URL(?view=week&date=…&add=ai) ↔ iOS
//  - view · date  → 이 화면의 @State (다른 화면에서는 router.openCalendar(view:date:) → calendarRequest 로 와요)
//  - add=ai       → router.openTaskAdd(mode:) → router.taskAdd 시트 (TaskAddSheet)
//
// 화면 배치 (아이폰 세로 · 모바일 UX)
//  - 위: 날짜 제목 [‹ ›] · [오늘] · 일/주/월 (한 줄). 일간이면 아래에 밀어서 넘기는 요일 띠
//  - 가운데: TimeTable 격자 (화면 폭을 꽉 채워요 — 이 화면의 주인공)
//  - 오른쪽 아래 떠 있는 버튼: ✨ AI 배치 (시간 미정 개수 배지) · ＋ 추가 메뉴 (AI로 정리 · 직접 추가 · 실제로 한 일)
//  - 아래: 범위 고르기 · AI 배치 확인 · 이월 제안 바

import SwiftUI
import UIKit

struct CalendarScreen: View {
    @Environment(AppRouter.self) private var router

    /// 앱을 열면 "오늘 하루"부터 보여줘요 (모바일에서는 주간 7칸이 너무 좁아요)
    @State private var mode: CalendarViewMode = .day
    @State private var date: String = KST.today()
    @State private var board: TimetableBoardModel
    @State private var month: CalendarMonthModel
    /// Task 추가 시트에서 방금 추가한 Task — 캘린더에 NEW 로 표시
    @State private var newIds: Set<Int> = []

    init() {
        _board = State(initialValue: TimetableBoardModel())
        _month = State(initialValue: CalendarMonthModel())
    }

    /// 미리보기용 (네트워크 없이 샘플 데이터)
    init(previewMode: CalendarViewMode) {
        let today = KST.today()
        let start = previewMode == .week ? KST.startOfWeek(today) : today
        let end = previewMode == .week ? KST.addDays(start, 6) : today
        _mode = State(initialValue: previewMode)
        _board = State(initialValue: TimetableBoardModel.sample(start: start, end: end))
        _month = State(initialValue: CalendarMonthModel.sample(for: today))
    }

    // 월간은 아래 패널에 보여줄 "선택한 날" 하루만 불러와요 (달 전체 요약은 MonthView 가 따로)
    private var rangeStart: String { mode == .week ? KST.startOfWeek(date) : date }
    private var rangeEnd: String {
        switch mode {
        case .week: return KST.addDays(rangeStart, 6)
        case .threeDay: return KST.addDays(rangeStart, 2)
        default: return date
        }
    }

    /// 아래에 확인 바(범위 고르기 · AI 배치 확인)가 떠 있으면 떠 있는 버튼은 숨겨요
    private var bottomBarBusy: Bool { board.ask != nil || board.proposing }

    var body: some View {
        @Bindable var router = router
        VStack(spacing: 0) {
            CalendarHeader(mode: mode, date: date, rangeStart: rangeStart, rangeEnd: rangeEnd,
                           fetching: board.isFetching && board.loadedOnce,
                           onMode: { m in
                               if m != mode {
                                   CAL.track("calendar.view_change", ["view_mode": m.rawValue, "from_view": mode.rawValue, "view_date": date])
                               }
                               go(mode: m)
                           },
                           onToday: {
                               let today = KST.today()
                               CAL.track("calendar.date_nav_click", ["view_mode": mode.rawValue, "date_action": "today",
                                                                     "view_date": today, "date_relation": "today", "nav_method": "button"])
                               go(date: today)
                           },
                           onMove: move,
                           onSelectDate: { d in
                               CAL.track("calendar.week_strip_select", ["view_date": d, "date_relation": CAL.relation(d)])
                               go(date: d)
                           })
            content
                .overlay(alignment: .bottomTrailing) {
                    if !bottomBarBusy {
                        CalendarFloatingActions(board: board, mode: mode,
                                                onAdd: { m in
                                                    CAL.track("calendar.task_add_click", ["view_mode": mode.rawValue, "view_date": date,
                                                                                          "add_mode": m.rawValue, "entry_source": "fab"])
                                                    openTaskAdd(m)
                                                },
                                                onActivity: openActivityNow,
                                                onScheduleAi: {
                                                    CAL.track("calendar.schedule_ai_click", ["view_mode": mode.rawValue, "view_date": date])
                                                    router.scheduleAi = ScheduleAiRequest()
                                                })
                            .padding(.trailing, PageLayout.horizontalPadding)
                            .padding(.bottom, 16)
                            .transition(.scale(scale: 0.6).combined(with: .opacity))
                    }
                }
                .animation(.spring(response: 0.3, dampingFraction: 0.8), value: bottomBarBusy)
        }
        // 캘린더는 페이지 전체가 흰 바탕 (DESIGN_SPEC 9.1)
        .background(Color.bgSurface.ignoresSafeArea())
        .safeAreaInset(edge: .bottom) {
            TimetableBottomBars(board: board)
        }
        .toolbar(.hidden, for: .navigationBar)
        .reloadOn([.timetable, .activities], id: "\(rangeStart)_\(rangeEnd)_\(mode.rawValue)") {
            board.setRange(rangeStart, rangeEnd)
            await board.load(withActivities: mode != .month)
        }
        .timetableSheets(board)
        // 빈 칸을 누르면 + 메뉴와 같은 선택지 (그 날짜 · 시각을 미리 채워요 · DESIGN_SPEC 11.4) — 홈 오늘 일정과 같은 메뉴
        .timetableEmptySlotMenu(board: board, onTaskAdd: { m, slot in
            CAL.track("calendar.task_add_click", ["view_mode": mode.rawValue, "view_date": slot.date,
                                                  "add_mode": m.rawValue, "entry_source": "empty_tap"])
            openTaskAdd(m, prefill: TaskAddPrefill(date: slot.date, startMinutes: slot.minutes))
        }, onActivity: { slot in
            CAL.track("calendar.activity_add_click", ["view_mode": mode.rawValue, "date_relation": CAL.relation(slot.date),
                                                      "entry_source": "empty_tap"])
        })
        .background {
            // Task 추가 시트는 블록 시트와 다른 뷰에 붙여서 서로 부딪히지 않게 해요
            Color.clear
                .sheet(item: $router.taskAdd) { req in
                    TaskAddSheet(initialMode: req.mode, single: req.single, prefill: req.prefill,
                                 onAdded: { info in handleAdded(info) })
                }
        }
        .background {
            // AI로 일정 조정 — 보고 있는 주/날 기준 (월간이면 그 날이 든 주)
            Color.clear
                .sheet(item: $router.scheduleAi) { req in
                    let s = mode == .day || mode == .threeDay ? date : KST.startOfWeek(date)
                    let e = mode == .day ? date : (mode == .threeDay ? KST.addDays(s, 2) : KST.addDays(s, 6))
                    ScheduleAiSheet(start: s, end: e, prompt: req.prompt)
                }
        }
        .onAppear {
            applyRequest(router.calendarRequest)
            if !CAL.isPreview {
                CAL.track("calendar.pageview", ["view_mode": mode.rawValue, "view_date": date, "date_relation": CAL.relation(date)])
            }
        }
        .onChange(of: router.calendarRequest) { _, req in applyRequest(req) }
        .onDisappear { board.saveAskOnLeave() }
    }

    @ViewBuilder
    private var content: some View {
        if mode == .month {
            CalendarMonthView(model: month, board: board, date: date,
                              onSelectDate: { d in go(date: d) },
                              onOpenDay: { d in go(mode: .day, date: d) })
        } else {
            CalendarBoardSection(board: board, mode: mode, date: date, newIds: newIds,
                                 onDayClick: { d in go(mode: .day, date: d) })
                // 주간은 날짜 줄 · 격자를 좌우 10 (DESIGN_SPEC 10.3) · 일간은 페이지 여백 20
                .padding(.horizontal, mode == .week || mode == .threeDay ? 10 : PageLayout.horizontalPadding)
        }
    }

    // MARK: 이동

    private func go(mode newMode: CalendarViewMode? = nil, date newDate: String? = nil) {
        board.close()
        withAnimation(.easeOut(duration: 0.18)) {
            if let newMode { mode = newMode }
            if let newDate { date = newDate }
        }
    }

    /// - method: button(‹ ›) · swipe(머리글 · 요일 띠 밀기)
    private func move(_ n: Int, _ method: String) {
        let step = mode == .week ? 7 : (mode == .threeDay ? 3 : 1)
        let next = mode == .month ? KST.addMonths(date, n) : KST.addDays(date, n * step)
        CAL.track("calendar.date_nav_click", ["view_mode": mode.rawValue, "date_action": n < 0 ? "prev" : "next",
                                              "view_date": next, "date_relation": CAL.relation(next), "nav_method": method])
        go(date: next)
    }

    /// 다른 화면에서 온 "이 날짜/보기로" 요청 (웹 /calendar?view=&date=)
    private func applyRequest(_ req: CalendarRequest?) {
        guard let req else { return }
        board.close()
        if let v = req.view { mode = v }
        if let d = req.date, KST.isYmd(d) { date = d }
        if let id = req.taskId { board.openTaskWhenLoaded(id) }
        router.calendarRequest = nil
    }

    /// Task 추가 — 월간이면 일간으로 바꿔서 추가한 블록이 보이게
    /// + 메뉴 · 빈 칸 메뉴 → 그 방식만 보이는 시트 (DESIGN_SPEC 11.3)
    private func openTaskAdd(_ addMode: TaskAddMode, prefill: TaskAddPrefill? = nil) {
        if mode == .month { mode = .day }
        router.openTaskAdd(mode: addMode, single: true, prefill: prefill)
    }

    /// "실제로 한 일" — 오늘(또는 보고 있는 지난 날)의 최근 1시간으로 열어요
    private func openActivityNow() {
        let now = KST.now()
        let today = now.date
        let day = mode == .day && date < today ? date : today
        let endMin = day == today ? (now.minutes / 10) * 10 : 13 * 60
        CAL.track("calendar.activity_add_click", ["view_mode": mode.rawValue, "date_relation": CAL.relation(day),
                                                  "entry_source": "fab"])
        board.openActivity(TimetableActivityDraft(activity: nil, date: day, start: max(endMin - 60, 0), end: endMin, replaced: nil))
    }

    /// Task 추가 시트에서 추가가 끝났을 때 (웹 CalendarView onAdded)
    private func handleAdded(_ info: TaskAddedInfo) {
        newIds.formUnion(info.taskIds)
        let start = rangeStart
        let end = rangeEnd
        let inView: (String) -> Bool = { d in d >= start && d <= end }
        // AI로 정리한 Task 는 바로 놓지 않고 점선으로 먼저 보여 줘요 → 아래 바에서 "이대로 진행할까요?"
        if let pending = info.pendingTaskIds, !pending.isEmpty {
            // 시트가 캘린더를 가리고 있으면 점선 제안이 안 보여서 닫아요 (task_add.panel_close 의 close_method = next)
            Analytics.sheetCloseHint = "next"
            router.taskAdd = nil
            let fallback = info.date
            Task {
                await board.previewFill(.tasks(pending), kind: .added, onPreview: { r in
                    TA.track("task_add_result.proposal_ready", [
                        "surface": "calendar", "result": "success",
                        "placed_count": r.proposals.count, "unscheduled_count": r.unscheduled.count,
                    ])
                    let first = r.proposals.map { $0.date }.sorted().first ?? fallback
                    if let first, !inView(first) { go(date: first) }
                }, onResolve: { applied, shown in
                    // 예 = 그 자리에 저장 · 아니요 = Task 는 저장된 채 시간 미정
                    TA.track("task_add_result.proposal_resolve", [
                        "surface": "calendar", "proposal_applied": applied,
                        "dragged_count": shown.filter { $0.start != $0.proposedStart || $0.end != $0.proposedEnd }.count,
                        "placed_count": applied ? shown.count : 0,
                        "unscheduled_count": applied ? board.proposalUnscheduled : pending.count,
                    ])
                }, onError: {
                    TA.track("task_add_result.proposal_ready", [
                        "surface": "calendar", "result": "fail", "placed_count": 0, "unscheduled_count": pending.count,
                    ])
                })
            }
            return
        }
        // 추가한 날이 지금 보는 기간 밖이면 그 날(주)로 옮겨요
        if let d = info.date, !inView(d) { go(date: d) }
    }
}

// MARK: - 위쪽 (날짜 제목 · 이동 · 보기)

private struct CalendarHeader: View {
    let mode: CalendarViewMode
    let date: String
    let rangeStart: String
    let rangeEnd: String
    let fetching: Bool
    let onMode: (CalendarViewMode) -> Void
    let onToday: () -> Void
    /// (몇 칸, button · swipe)
    let onMove: (Int, String) -> Void
    let onSelectDate: (String) -> Void

    var body: some View {
        // 제목 줄 ↔ 날짜 줄 간격 12 (DESIGN_SPEC 9.2)
        VStack(spacing: 12) {
            HStack(spacing: 0) {
                IconButton(systemName: "chevron.left", size: 13.3, color: .textSecondary, label: "이전 \(unit)") { onMove(-1, "button") }
                Text(title)
                    .font(.pt(18, .semibold))
                    .foregroundStyle(Color.ink)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                    .contentTransition(.numericText())
                    .accessibilityAddTraits(.updatesFrequently)
                IconButton(systemName: "chevron.right", size: 13.3, color: .textSecondary, label: "다음 \(unit)") { onMove(1, "button") }
                if fetching {
                    ProgressView().controlSize(.mini).tint(Color.ink4)
                }
                Spacer(minLength: 6)
                if !showsToday {
                    Button(action: onToday) {
                        Text("오늘")
                            .font(.pt(13, .semibold))
                            .foregroundStyle(Color.textBrand)
                            .padding(.horizontal, 10)
                            .frame(height: 30)
                            .background(Color.brandSoft, in: Capsule())
                    }
                    .buttonStyle(.plain)
                    .padding(.trailing, 6)
                    .transition(.opacity)
                }
                CalendarModeSwitch(mode: mode, onMode: onMode)
            }
            // 페이지 여백 20 (← 버튼은 안쪽 여백이 있어서 조금 덜)
            .padding(.leading, PageLayout.horizontalPadding - 8)
            .padding(.trailing, PageLayout.horizontalPadding)

            if mode == .day {
                CalendarWeekStrip(date: date, onSelect: onSelectDate, onMoveWeek: { n in onMove(n * 7, "swipe") })
                    .pagePadding()
            }
        }
        .padding(.top, 6)
        .padding(.bottom, 8)
        .background(Color.surface)
        .overlay(alignment: .bottom) { Rectangle().fill(Color.borderSoft).frame(height: 0.5) }
        .gesture(swipe)
    }

    /// 머리글을 좌우로 밀면 이전 · 다음 (일간은 요일 띠가 주 단위로 따로 처리해요)
    private var swipe: some Gesture {
        DragGesture(minimumDistance: 24)
            .onEnded { v in
                guard mode != .day, abs(v.translation.width) > abs(v.translation.height) * 1.5,
                      abs(v.translation.width) > 50 else { return }
                onMove(v.translation.width < 0 ? 1 : -1, "swipe")
            }
    }

    private var unit: String { mode == .month ? "달" : (mode == .week ? "주" : (mode == .threeDay ? "3일" : "날")) }

    private var showsToday: Bool {
        let today = KST.today()
        if mode == .month { return KST.yearOf(date) == KST.yearOf(today) && KST.monthOf(date) == KST.monthOf(today) }
        return rangeStart <= today && today <= rangeEnd
    }

    /// 일간 "10월 9일 (목)" · 주간 "10월 5일 – 11일" · 월간 "2026년 10월"
    private var title: String {
        switch mode {
        case .day:
            return KST.formatMonthDay(date)
        case .week, .threeDay:
            let s = rangeStart, e = rangeEnd
            let head = "\(KST.monthOf(s))월 \(KST.dayOfMonth(s))일"
            return KST.monthOf(s) == KST.monthOf(e)
                ? "\(head) – \(KST.dayOfMonth(e))일"
                : "\(head) – \(KST.monthOf(e))월 \(KST.dayOfMonth(e))일"
        case .month:
            return "\(KST.yearOf(date))년 \(KST.monthOf(date))월"
        }
    }
}

/// 일 · 주 · 월 작은 전환 (한 줄 머리글에 들어가게 짧게)
private struct CalendarModeSwitch: View {
    let mode: CalendarViewMode
    let onMode: (CalendarViewMode) -> Void
    @Namespace private var ns

    private static let modes: [CalendarViewMode] = [.day, .threeDay, .week, .month]

    private func label(_ m: CalendarViewMode) -> String {
        switch m {
        case .day: return "일"
        case .threeDay: return "3일"
        case .week: return "주"
        case .month: return "월"
        }
    }

    var body: some View {
        HStack(spacing: 2) {
            ForEach(CalendarModeSwitch.modes, id: \.self) { m in
                let on = m == mode
                Button {
                    UISelectionFeedbackGenerator().selectionChanged()
                    onMode(m)
                } label: {
                    Text(label(m))
                        .font(.pt(13, on ? .bold : .medium))
                        .foregroundStyle(on ? Color.ink : Color.ink3)
                        .frame(width: 34, height: 28)
                        .background {
                            if on {
                                RoundedRectangle(cornerRadius: 8)
                                    .fill(Color.surface)
                                    .cardShadow()
                                    .matchedGeometryEffect(id: "mode", in: ns)
                            }
                        }
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(m == .threeDay ? "3일 보기" : "\(label(m))간 보기")
                .accessibilityAddTraits(on ? .isSelected : [])
            }
        }
        .padding(3)
        .background(Color.subtle, in: RoundedRectangle(cornerRadius: 10))
        .animation(.spring(response: 0.28, dampingFraction: 0.85), value: mode)
    }
}

/// 일간 위 요일 띠 — 날짜를 누르면 그날로, 좌우로 밀면 이전 · 다음 주
private struct CalendarWeekStrip: View {
    let date: String
    let onSelect: (String) -> Void
    let onMoveWeek: (Int) -> Void

    var body: some View {
        let start = KST.startOfWeek(date)
        let today = KST.today()
        HStack(spacing: 0) {
            ForEach(0..<7, id: \.self) { i in
                let d = KST.addDays(start, i)
                cell(d, index: i, today: today)
            }
        }
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 20)
                .onEnded { v in
                    guard abs(v.translation.width) > abs(v.translation.height), abs(v.translation.width) > 40 else { return }
                    onMoveWeek(v.translation.width < 0 ? 1 : -1)
                }
        )
    }

    private func cell(_ d: String, index: Int, today: String) -> some View {
        let selected = d == date
        let isToday = d == today
        let weekdayColor: Color = index == 6 ? .calendarHoliday : (index == 5 ? .calendarSaturday : .textTertiary)
        let numberColor: Color = isToday ? .calendarTodayFg : (selected ? .textBrand : (d < today ? .textTertiary : .textPrimary))
        return Button {
            guard !selected else { return }
            UISelectionFeedbackGenerator().selectionChanged()
            onSelect(d)
        } label: {
            VStack(spacing: 4) {
                Text(KST.weekdayLabel[index])
                    .font(.pt(11, .medium))
                    .foregroundStyle(weekdayColor)
                Text("\(KST.dayOfMonth(d))")
                    .font(.pt(16, selected || isToday ? .bold : .medium))
                    .foregroundStyle(numberColor)
                    .frame(width: 36, height: 36)
                    .background {
                        // 오늘 = primary 원 + 흰 글자 (DESIGN_SPEC 8.5) · 고른 날 = 연한 브랜드 원
                        if isToday {
                            Circle().fill(Color.calendarToday)
                        } else if selected {
                            Circle().fill(Color.actionSecondaryBg)
                        }
                    }
            }
            .frame(maxWidth: .infinity)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(KST.formatMonthDay(d) + (isToday ? ", 오늘" : ""))
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

// MARK: - 떠 있는 버튼 (AI 배치 · 추가)

private struct CalendarFloatingActions: View {
    let board: TimetableBoardModel
    let mode: CalendarViewMode
    let onAdd: (TaskAddMode) -> Void
    let onActivity: () -> Void
    let onScheduleAi: () -> Void

    var body: some View {
        let targets = TimetableRules.unplacedOf(board.rangeDays, today: KST.today())
        let total = targets.reduce(0) { $0 + $1.count }
        VStack(spacing: NFabFace.gap) {
            // ✨ AI 메뉴: AI로 일정 조정 · AI로 TimeTable 자동 배치 (DESIGN_SPEC 11.1)
            Menu {
                Button(action: onScheduleAi) {
                    // 이미 있는 일정을 말로 옮겨요 (2026-10 이름 변경: AI로 일정 조정 → AI로 일정 옮기기)
                    Label("AI로 일정 옮기기", systemImage: "wand.and.stars")
                }
                // 월간은 TimeTable 격자가 없어서 자동 배치를 빼요
                if mode != .month {
                    Button {
                        autoPlace(targets)
                    } label: {
                        // 시간 미정 Task 를 빈 시간에 넣어요 — 넣을 게 없으면 흐리게 (2026-10)
                        Label(total > 0 ? "AI로 빈 시간에 자동 배치 (\(total)개)" : "AI로 빈 시간에 자동 배치", systemImage: "sparkles")
                    }
                    .disabled(board.fillPreviewing || total == 0)
                }
            } label: {
                TimetableAIFabFace(total: total, loading: board.fillPreviewing, alwaysActive: true)
            }
            .menuIndicator(.hidden)
            .accessibilityLabel("AI")
            .accessibilityValue(total > 0 ? "시간 미정 Task \(total)개" : "")

            // + 메뉴: AI로 자동 Task 추가 · 계획 할 일 추가 · 실제 한 일 추가 (구분선 없음 · DESIGN_SPEC 11.2)
            Menu {
                Button { onAdd(.ai) } label: {
                    Label("AI로 자동 Task 추가", systemImage: "sparkles")
                }
                Button { onAdd(.manual) } label: {
                    Label("계획 할 일 추가", systemImage: "square.and.pencil")
                }
                if mode != .month {
                    Button(action: onActivity) {
                        Label("실제 한 일 추가", systemImage: "checkmark.circle")
                    }
                }
                // 못 한 일 자동 이월 켜기/끄기 (2026-10 · 설정 "일정"에도 있어요)
                Divider()
                Toggle(isOn: AutoCarrySetting.shared.binding(surface: "calendar")) {
                    Label("못 한 일 자동 이월", systemImage: "arrow.uturn.forward")
                }
            } label: {
                NFabFace(icon: "plus")
            }
            .task { if !AutoCarrySetting.shared.loaded { await AutoCarrySetting.shared.load() } }
            .menuIndicator(.hidden)
            .accessibilityLabel("추가")
        }
    }

    /// 예전 ✨ 버튼을 눌렀을 때와 같아요 — 시간 미정 Task 가 없으면 안내 · 있으면 점선 미리보기
    private func autoPlace(_ targets: [TimetableUnplaced]) {
        guard let first = targets.first, let last = targets.last else {
            Toast.show("모든 Task가 TimeTable에 배치돼 있어요")
            return
        }
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        CAL.track("calendar.auto_place_click", ["view_mode": mode.rawValue,
                                                "unscheduled_count": targets.reduce(0) { $0 + $1.count },
                                                "day_count": targets.count])
        Task { await board.previewFill(.range(start: first.date, end: last.date)) }
    }
}

// MARK: - 격자 부분 (일간 · 주간)

private struct CalendarBoardSection: View {
    let board: TimetableBoardModel
    let mode: CalendarViewMode
    let date: String
    let newIds: Set<Int>
    let onDayClick: (String) -> Void

    var body: some View {
        let days = board.days
        let today = KST.today()
        Group {
            if days.isEmpty && board.isFetching {
                LoadingView(minHeight: 420)
                    .frame(maxHeight: .infinity, alignment: .top)
            } else if days.isEmpty, let error = board.loadError {
                VStack(spacing: 8) {
                    Text("TimeTable을 불러오지 못했어요").font(.pt(16, .semibold)).foregroundStyle(Color.ink)
                    Text(error).font(.pt(14)).foregroundStyle(Color.ink3).multilineTextAlignment(.center)
                    NButton("다시 시도", variant: .secondary) {
                        CAL.track("calendar.retry_click", ["view_mode": mode.rawValue, "error_status": board.loadErrorStatus])
                        Task { await board.load(withActivities: true) }
                    }
                    .padding(.top, 8)
                }
                .onAppear {
                    CAL.track("calendar.load_fail", ["view_mode": mode.rawValue, "view_date": date, "error_status": board.loadErrorStatus])
                }
                .padding(32)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            } else if days.isEmpty && !board.loadedOnce {
                LoadingView(minHeight: 420)
                    .frame(maxHeight: .infinity, alignment: .top)
            } else {
                grid(days, today: today)
            }
        }
        .background(Color.surface)
    }

    private func grid(_ days: [TimetableDay], today: String) -> some View {
        let hours = TimetableLayout.visibleHours(days, activities: board.activities)
        let isDay = mode == .day
        return TimetableGridView(board: board, days: days, activities: board.activities,
                                 startHour: hours.startHour, endHour: hours.endHour,
                                 planVsActual: isDay, highlightIds: newIds,
                                 onDayClick: isDay ? nil : { d in
                                     CAL.track("calendar.day_header_click", ["view_date": d, "date_relation": CAL.relation(d)])
                                     onDayClick(d)
                                 },
                                 emptyClickEnabled: true) {
            VStack(alignment: .leading, spacing: 14) {
                // 일간: TimeTable(계획 | 실제) 아래에 그날 할 일 목록
                if isDay {
                    CalendarDayTaskList(board: board, day: days.first(where: { $0.date == date }), today: today)
                }
                // 이 기간(하루 · 3일 · 주) 목표별 실천 시간 — 참고용 작은 막대 (2026-10)
                if let first = days.first?.date, let last = days.last?.date {
                    GoalTimeStatsView(start: first, end: last, viewMode: mode.rawValue, periodLabel: periodLabel(first, last, today))
                }
                TimetableLegend()
                Text("블록을 눌러 자세히 보고, 길게 눌러 끌면 시간을 바꿔요. 지난 빈 시간을 누르면 실제로 한 일을 남길 수 있어요.")
                    .font(.pt(12))
                    .foregroundStyle(Color.ink4)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.horizontal, 16)
            .padding(.top, 14)
            // 떠 있는 버튼에 마지막 내용이 가리지 않게
            .padding(.bottom, 150)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .frame(maxHeight: .infinity)
    }

    /// "오늘" · "이날" · "3일" · "이번 주" · "이 주"
    private func periodLabel(_ first: String, _ last: String, _ today: String) -> String {
        switch mode {
        case .day: return first == today ? "오늘" : "이날"
        case .threeDay: return "3일"
        default: return first <= today && today <= last ? "이번 주" : "이 주"
        }
    }
}

#Preview("일간") {
    NavigationStack {
        CalendarScreen(previewMode: .day)
    }
    .environment(AppRouter.shared)
}

#Preview("주간") {
    NavigationStack {
        CalendarScreen(previewMode: .week)
    }
    .environment(AppRouter.shared)
}

#Preview("월간") {
    NavigationStack {
        CalendarScreen(previewMode: .month)
    }
    .environment(AppRouter.shared)
}
