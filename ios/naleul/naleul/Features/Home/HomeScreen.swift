// Features/Home/HomeScreen.swift
//
// 홈 탭 — 오늘 해야 할 것만 한곳에. 웹 원본: features/home/ui/HomeView.tsx
//  1. 오늘 요약 (얼마나 했는지 · 지금/다음 할 일 · 마감 · 오늘 회고 버튼)
//  2. 오늘 일정 (캘린더 일간과 같은 계획 | 실제 격자 — 블록을 누르면 캘린더와 같은 상세 시트)
//  3. 할 일 · 루틴과 생활 습관 (바로 체크)
//  4. 다가오는 일정 (6일) · 마감 로드맵
// 웹은 넓은 화면에서 칸을 나누지만 아이폰은 한 줄로 카드를 쌓아요.
// 요약 카드 아래에 "나의 패턴"과 같은 섹션 칩 바(위에 붙음)를 둬요:
//   오늘 일정 · 할 일 · 루틴/습관 · 다가오는 일정 · 마감 로드맵
//   칩을 누르면 그 카드로 스크롤하고, 스크롤하면 지금 보고 있는 카드 칩이 켜져요.
// 오늘 회고는 요약 카드의 "오늘 회고" → 시트 (HomeRetroSheet)

import SwiftUI
import UIKit

// MARK: - 데이터

@MainActor
@Observable
final class HomeViewModel {
    /// days[0] = 오늘, 나머지 = 앞으로 6일
    private(set) var days: [TimetableDay] = []
    private(set) var habitHome: HabitHome?
    /// /home/today — 아직 배포 전인 서버(404)면 nil + homeFailed
    private(set) var home: HomeToday?
    private(set) var homeFailed = false
    private(set) var loadedOnce = false
    private(set) var loadError: String?
    private(set) var isFetching = false
    private(set) var togglingTaskId: Int?
    @ObservationIgnored private var offline = false

    init() {}

    var today: TimetableDay? { days.first }
    var upcoming: [TimetableDay] { Array(days.dropFirst()) }

    /// 오늘의 루틴이 아닌 Task (시간순, 시간 미정은 뒤)
    var tasks: [TimeBlockTask] {
        guard let d = today else { return [] }
        return HomeDay.tasksOfDay(d).filter { !HomeDay.isRoutine($0) }
            .sorted { HomeDay.startMinutes(d.date, $0) < HomeDay.startMinutes(d.date, $1) }
    }

    /// 오늘의 루틴 Task
    var routines: [TimeBlockTask] {
        guard let d = today else { return [] }
        return HomeDay.tasksOfDay(d).filter { HomeDay.isRoutine($0) }
            .sorted { HomeDay.startMinutes(d.date, $0) < HomeDay.startMinutes(d.date, $1) }
    }

    /// 오늘 체크할 생활 습관
    var todayHabits: [Habit] {
        (habitHome?.habits ?? []).filter { $0.status == .ACTIVE && $0.scheduled }
    }

    /// 아직 회고를 안 쓴, 오늘 할 일이 있던 목표 수
    var retroLeft: Int {
        (home?.goals ?? []).filter { $0.activeToday && !$0.hasRecord }.count
    }

    /// - day: 보고 있는 날 (날짜 ‹ › 로 고른 날)
    /// - realToday: 진짜 오늘. 습관은 앞날 체크가 안 돼서 앞날이면 숨겨요
    func load(today day: String, realToday: String) async {
        if offline { return }
        isFetching = true
        defer { isFetching = false }
        let isToday = day == realToday
        let isFuture = day > realToday
        async let week = TimetableAPI.timetable(start: day, end: KST.addDays(day, 6))
        async let habits = Self.habits(on: day, isToday: isToday, isFuture: isFuture)
        async let homeToday = HomeAPI.today(date: isToday ? nil : day)
        if isFuture { habitHome = nil }

        // 다시 불러오다 실패하면 이전 데이터를 그대로 보여줘요
        do {
            let r = try await week
            days = r.days
            loadError = nil
            loadedOnce = true
        } catch is CancellationError {
        } catch {
            loadError = errorMessage(error)
            loadedOnce = true
        }
        do {
            if let h = try await habits { habitHome = h }
        } catch is CancellationError {
        } catch {
            HomeTracker.shared.system(HomeEvent.sectionLoadFail, [
                "failed_section": "habits", "error_status": (error as? APIError)?.httpStatus, "view_date": day,
            ])
        }
        do {
            let h = try await homeToday
            if let h {
                home = h
                homeFailed = false
            } else if home == nil {
                homeFailed = true
            }
        } catch is CancellationError {
        } catch {
            if home == nil { homeFailed = true }
            HomeTracker.shared.system(HomeEvent.sectionLoadFail, [
                "failed_section": "deadlines", "error_status": (error as? APIError)?.httpStatus, "view_date": day,
            ])
        }
        HomeTracker.shared.lastLoadedAt = Date()
    }

    /// 앞날은 습관 체크가 안 돼서 불러오지 않아요
    private static func habits(on day: String, isToday: Bool, isFuture: Bool) async throws -> HabitHome? {
        if isFuture { return nil }
        return try await HabitAPI.home(date: isToday ? nil : day)
    }

    // MARK: 체크

    /// Task 완료 ↔ 완료 취소 (끝나면 캘린더 · 목표 · 홈이 같이 다시 불러와져요)
    func toggleTask(_ t: TimeBlockTask) async {
        guard togglingTaskId == nil, t.sourceType != .MISSION else { return }
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        togglingTaskId = t.taskId
        let current = t.taskStatus
        setStatus(t.taskId, current == .COMPLETED ? .TODO : .COMPLETED)
        let ok = await Mutation.run(invalidate: [.timetable, .goals, .home, .record]) {
            try await TimetableAPI.toggleComplete(taskId: t.taskId, currentStatus: current)
        }
        if ok == nil { setStatus(t.taskId, current) }
        HomeTracker.shared.system(HomeEvent.checkResult, [
            "item_type": HomeDay.isRoutine(t) ? "routine" : "task",
            "check_action": HomeTracker.checkAction(current == .COMPLETED),
            "result": ok == nil ? "fail" : "success",
        ])
        togglingTaskId = nil
    }

    /// 생활 습관 체크 (바로 바뀌고, 실패하면 되돌려요)
    func toggleHabit(_ h: Habit, day: String) async {
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        let next = !h.checked
        setHabit(h.habitId, checked: next)
        let ok = await Mutation.run(invalidate: [.habits, .home]) {
            try await HabitAPI.setChecked(habitId: h.habitId, day: day, checked: next)
        }
        if ok == nil { setHabit(h.habitId, checked: !next) }
        HomeTracker.shared.system(HomeEvent.checkResult, [
            "item_type": "habit", "check_action": next ? "complete" : "uncomplete", "result": ok == nil ? "fail" : "success",
        ])
    }

    private func setStatus(_ taskId: Int, _ status: TaskStatus) {
        for i in days.indices {
            for j in days[i].tasks.indices where days[i].tasks[j].taskId == taskId {
                days[i].tasks[j].taskStatus = status
            }
            for j in days[i].unscheduledTasks.indices where days[i].unscheduledTasks[j].taskId == taskId {
                days[i].unscheduledTasks[j].taskStatus = status
            }
        }
    }

    private func setHabit(_ habitId: Int, checked: Bool) {
        guard var hh = habitHome, let i = hh.habits.firstIndex(where: { $0.habitId == habitId }) else { return }
        if hh.habits[i].checked == checked { return }
        hh.habits[i].checked = checked
        hh.doneCount = max(0, hh.doneCount + (checked ? 1 : -1))
        habitHome = hh
    }

    // MARK: 미리보기

    static func preview(homeFailed: Bool = false, empty: Bool = false) -> HomeViewModel {
        let m = HomeViewModel()
        m.offline = true
        let today = KST.today()
        m.days = empty ? [] : TimetableSampleData.days(from: today, to: KST.addDays(today, 6))
        m.habitHome = empty ? nil : HomeSampleData.habitHome(today: today)
        m.home = homeFailed ? nil : HomeSampleData.homeToday(today: today)
        m.homeFailed = homeFailed
        m.loadedOnce = true
        return m
    }
}

// MARK: - 화면

struct HomeScreen: View {
    @Environment(AppRouter.self) private var router

    @State private var vm: HomeViewModel
    @State private var board: TimetableBoardModel
    @State private var today: String = KST.today()
    /// 날짜 ‹ › 로 고른 날 (nil = 오늘을 따라가요 — 자정이 지나면 저절로 다음 날)
    @State private var picked: String? = nil
    private var viewDate: String { picked ?? today }
    private var isToday: Bool { viewDate == today }
    @State private var nowMinutes: Int = KST.now().minutes
    @State private var retroOpen = false
    /// 섹션 칩 바 — 지금 보고 있는 카드
    @State private var currentSection: HomeSection = .calendar
    /// 칩을 눌러 스크롤하는 동안에는 스크롤 위치로 칩을 바꾸지 않아요 (깜빡임 방지)
    @State private var jumping = false
    /// 회고 시트를 연 경로 — button · push(하루 마무리 알림)
    @State private var retroEntry = "button"
    /// 오늘 일정의 "추가" · 빈 칸 메뉴에서 연 Task 추가 시트 — 캘린더 탭으로 옮기지 않고 홈 위에 띄워요 (2026-10)
    @State private var taskAdd: TaskAddRequest?
    /// 방금 추가한 Task — 오늘 일정에 NEW 로 표시
    @State private var newIds: Set<Int> = []
    @Environment(\.scenePhase) private var scenePhase
    private let isPreview: Bool

    init() {
        _vm = State(initialValue: HomeViewModel())
        let b = TimetableBoardModel()
        b.analyticsSurface = "home"
        _board = State(initialValue: b)
        isPreview = false
    }

    /// 미리보기용 (네트워크 없이 샘플 데이터)
    init(previewVM: HomeViewModel) {
        let d = KST.today()
        _vm = State(initialValue: previewVM)
        _board = State(initialValue: TimetableBoardModel.sample(start: d, end: d))
        isPreview = true
    }

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 0, pinnedViews: [.sectionHeaders]) {
                    if showsSections {
                        top
                            .pagePadding()
                            .padding(.top, 8)
                            .padding(.bottom, 12)
                        Section {
                            sectionCards
                                .pagePadding()
                                .padding(.top, 12)
                                .padding(.bottom, 24)
                        } header: {
                            HomeSectionNav(current: currentSection) { s in jump(to: s, proxy) }
                                .background(GeometryReader { g in
                                    Color.clear.preference(key: HomeSectionTopKey.self,
                                                           value: [HomeGeo.navKey: g.frame(in: .named("homeScroll")).maxY])
                                })
                        }
                    } else {
                        content
                            .pagePadding()
                            .padding(.top, 8)
                            .padding(.bottom, 24)
                    }
                }
                // 가장 깊이 내린 스크롤 % (home.page_exit)
                .onGeometryChange(for: CGRect.self) { $0.frame(in: .named("homeScroll")) } action: { f in
                    HomeTracker.shared.scrolled(f)
                }
            }
            .onPreferenceChange(HomeSectionTopKey.self) { tops in
                guard !jumping else { return }
                // 칩 바 바로 아래(약간 여유)를 지난 마지막 카드 = 지금 보는 카드
                let line = (tops[HomeGeo.navKey] ?? HomeGeo.navHeight) + 40
                let passed = HomeSection.allCases.filter { (tops[$0.rawValue] ?? .infinity) <= line }
                let next = passed.last ?? .calendar
                if next != currentSection { currentSection = next }
            }
        }
        .coordinateSpace(.named("homeScroll"))
        // 격자 블록을 끄는 동안에는 홈 스크롤을 잠가요
        .scrollDisabled(board.drag != nil)
        .refreshable {
            HomeTracker.shared.track(HomeEvent.pullRefresh, [
                "view_date": viewDate,
                "minutes_since_update": HomeTracker.shared.lastLoadedAt.map { Int(Date().timeIntervalSince($0) / 60) },
            ])
            await reload()
        }
        .canvasBackground()
        .safeAreaInset(edge: .bottom) {
            TimetableBottomBars(board: board, showFill: false)
        }
        .toolbar(.hidden, for: .navigationBar)
        .reloadOn([.timetable, .home, .habits, .goals, .record, .work, .activities], id: viewDate) {
            await reload()
        }
        .task {
            // 1분마다 "지금" 갱신 (지금 할 일 · 다음 일정까지 남은 시간). 날짜가 바뀌면 다시 불러와요
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 60_000_000_000)
                let n = KST.now()
                nowMinutes = n.minutes
                if n.date != today {
                    HomeTracker.shared.system(HomeEvent.dayRollover, ["from_date": today, "to_date": n.date])
                    today = n.date
                }
            }
        }
        .timetableSheets(board)
        // 오늘 일정 빈 칸을 누르면 캘린더 일간과 같은 + 메뉴 (그 날짜 · 시각을 미리 채워요)
        .timetableEmptySlotMenu(board: board, onTaskAdd: { m, slot in
            HomeTracker.shared.track(HomeEvent.calendarTaskAddClick, [
                "view_date": slot.date, "date_relation": HomeTracker.relation(slot.date),
                "add_mode": m.rawValue, "entry_source": "empty_tap",
            ])
            taskAdd = TaskAddRequest(mode: m, single: true, prefill: TaskAddPrefill(date: slot.date, startMinutes: slot.minutes))
        }, onActivity: { slot in
            HomeTracker.shared.track(HomeEvent.calendarAddActivityClick, [
                "view_date": slot.date, "date_relation": HomeTracker.relation(slot.date), "entry_source": "empty_tap",
            ])
        })
        .background {
            // Task 추가 시트도 블록 시트와 다른 뷰에 붙여요
            Color.clear
                .sheet(item: $taskAdd) { req in
                    TaskAddSheet(initialMode: req.mode, single: req.single, prefill: req.prefill,
                                 onAdded: { info in handleAdded(info) })
                }
        }
        .background {
            // 회고 시트는 블록 시트와 다른 뷰에 붙여서 서로 부딪히지 않게 해요
            Color.clear
                .sheet(isPresented: $retroOpen) {
                    HomeRetroSheet(vm: vm, today: viewDate, entryPoint: retroEntry)
                }
        }
        .onAppear { if !isPreview { HomeTracker.shared.enter() } }
        .onDisappear {
            board.saveAskOnLeave()
            if !isPreview { HomeTracker.shared.exit(router.tab == .home ? "navigate" : "tab_switch", viewDate: viewDate) }
        }
        .onChange(of: scenePhase) { _, phase in
            guard !isPreview, router.tab == .home, router.homePath.isEmpty else { return }
            if phase == .background { HomeTracker.shared.exit("background", viewDate: viewDate) }
            if phase == .active { HomeTracker.shared.enter() }
        }
        // 하루 마무리 알림을 누르고 들어오면 → 오늘로 돌아와서 회고 시트
        .onChange(of: router.homeRetro, initial: true) { _, open in
            guard open else { return }
            router.homeRetro = false
            picked = nil
            retroEntry = "push"
            retroOpen = true
        }
    }

    /// Task 추가 시트에서 추가가 끝났을 때 — 캘린더와 같이 NEW 표시, AI로 정리한 Task 는 점선 미리보기부터
    private func handleAdded(_ info: TaskAddedInfo) {
        newIds.formUnion(info.taskIds)
        guard let pending = info.pendingTaskIds, !pending.isEmpty else { return }
        // 시트가 오늘 일정을 가리면 점선 제안이 안 보여서 닫아요 (task_add.panel_close 의 close_method = next)
        Analytics.sheetCloseHint = "next"
        taskAdd = nil
        Task {
            await board.previewFill(.tasks(pending), kind: .added, onPreview: { r in
                TA.track("task_add_result.proposal_ready", [
                    "surface": "home", "result": "success",
                    "placed_count": r.proposals.count, "unscheduled_count": r.unscheduled.count,
                ])
            }, onResolve: { applied, shown in
                TA.track("task_add_result.proposal_resolve", [
                    "surface": "home", "proposal_applied": applied,
                    "placed_count": applied ? shown.count : 0,
                ])
            })
        }
    }

    @MainActor
    private func reload() async {
        if isPreview { return }
        let d = viewDate
        let real = today
        let model = vm
        let b = board
        b.setRange(d, d)
        async let a: Void = model.load(today: d, realToday: real)
        async let c: Void = b.load(withActivities: true)
        _ = await (a, c)
        trackPageview()
    }

    /// 홈을 다 불러온 직후 (들어올 때마다 한 번)
    private func trackPageview() {
        guard !vm.days.isEmpty else { return }
        let tasks = vm.tasks, routines = vm.routines, habits = vm.todayHabits
        let deadlines = vm.home?.deadlines ?? []
        let done = tasks.filter { $0.isCompleted }.count + routines.filter { $0.isCompleted }.count
            + habits.filter { $0.checked }.count
        let total = tasks.count + routines.count + habits.count
        HomeTracker.shared.pageviewOnce([
            "view_date": viewDate,
            "date_relation": HomeTracker.relation(viewDate),
            "total_count": total,
            "done_count": done,
            "progress_percent": total > 0 ? Int((Double(done) / Double(total) * 100).rounded()) : 0,
            "task_count": tasks.count,
            "routine_count": routines.count,
            "habit_count": habits.count,
            "retro_left_count": vm.retroLeft,
            "due_today_count": deadlines.filter { $0.dDay == 0 }.count,
            "overdue_count": deadlines.filter { $0.overdue }.count,
            "focus_state": focusState,
            "unread_count": NudgeCenter.shared.unreadCount,
        ])
    }

    /// 요약 카드 '지금 할 일 / 다음 일정' 상태 — current · next · none · not_today
    private var focusState: String {
        guard isToday, let d = vm.today else { return "not_today" }
        let open = HomeDay.tasksOfDay(d).filter { t in
            !t.isCompleted && t.plannedStartAt.map { String($0.prefix(10)) == d.date } == true
        }
        if open.contains(where: { HomeDay.startMinutes(d.date, $0) <= nowMinutes && nowMinutes < HomeDay.endMinutes(d.date, $0) }) {
            return "current"
        }
        return open.contains(where: { HomeDay.startMinutes(d.date, $0) > nowMinutes }) ? "next" : "none"
    }

    /// 불러오기 전 · 전부 실패한 때는 칩 바 없이 안내만
    private var showsSections: Bool { !vm.days.isEmpty }

    /// 칩으로 카드 이동
    private func jump(to s: HomeSection, _ proxy: ScrollViewProxy) {
        UISelectionFeedbackGenerator().selectionChanged()
        HomeTracker.shared.track(HomeEvent.sectionNavClick, [
            "section": s.rawValue, "from_section": currentSection.rawValue, "view_date": viewDate,
        ])
        jumping = true
        currentSection = s
        withAnimation(.easeInOut(duration: 0.35)) { proxy.scrollTo("home-\(s.rawValue)", anchor: .top) }
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 500_000_000)
            jumping = false
        }
    }

    /// 카드 위에 보이지 않는 표식을 칩 바 높이만큼 위에 둬요 → 칩으로 스크롤하면 카드가 칩 바 바로 아래에 와요
    private func anchored<C: View>(_ s: HomeSection, @ViewBuilder _ card: () -> C) -> some View {
        card()
            .background(alignment: .top) {
                Color.clear
                    .frame(height: 1)
                    .alignmentGuide(VerticalAlignment.top) { _ in HomeGeo.navHeight + 12 }
                    .id("home-\(s.rawValue)")
                    .allowsHitTesting(false)
            }
            .background(GeometryReader { g in
                Color.clear.preference(key: HomeSectionTopKey.self,
                                       value: [s.rawValue: g.frame(in: .named("homeScroll")).minY])
            })
    }

    @ViewBuilder
    private var content: some View {
        if vm.days.isEmpty && !vm.loadedOnce {
            LoadingView(minHeight: 420)
        } else if vm.days.isEmpty, let error = vm.loadError {
            VStack(spacing: 0) {
                Text(isToday ? "오늘 일정을 불러오지 못했어요" : "이날 일정을 불러오지 못했어요")
                    .font(.pt(16, .semibold))
                    .foregroundStyle(Color.ink)
                    .padding(.top, 24)
                ErrorView(message: error) {
                    HomeTracker.shared.track(HomeEvent.retryClick, ["view_date": viewDate, "failed_section": "all"])
                    await reload()
                }
            }
            .card()
            .onAppear { HomeTracker.shared.system(HomeEvent.loadFail, ["view_date": viewDate]) }
        } else {
            LoadingView(minHeight: 420)
        }
    }

    /// 칩 바 위 — 둘러보기 배너 · 오늘 요약
    private var top: some View {
        let tasks = vm.tasks
        let routines = vm.routines
        let habits = vm.todayHabits
        let deadlines = vm.home?.deadlines ?? []
        return VStack(spacing: 14) {
            // 알림 종은 위 공통 헤더(MainTabView)로 옮겼어요
            if UserStore.shared.isGuest {
                GuestBanner()
            }

            HomeSummaryCard(today: viewDate, nowMinutes: nowMinutes, userName: userName,
                            day: vm.today, tasks: tasks, routines: routines,
                            habitDone: habits.filter { $0.checked }.count, habitTotal: habits.count,
                            dueToday: deadlines.filter { $0.dDay == 0 }.count,
                            overdue: deadlines.filter { $0.overdue }.count,
                            retroLeft: vm.retroLeft,
                            showRetro: vm.home != nil && !vm.homeFailed,
                            onRetro: {
                                HomeTracker.shared.track(HomeEvent.retroButtonClick, [
                                    "view_date": viewDate, "date_relation": HomeTracker.relation(viewDate),
                                    "retro_left_count": vm.retroLeft,
                                ])
                                retroEntry = "button"
                                retroOpen = true
                            },
                            nav: HomeDayNav(isToday: isToday, isPast: viewDate < today,
                                            onPrev: { go(KST.addDays(viewDate, -1), event: HomeEvent.datePrevClick) },
                                            onNext: { go(KST.addDays(viewDate, 1), event: HomeEvent.dateNextClick) },
                                            onToday: { go(today, event: HomeEvent.dateTodayClick) }))
        }
    }

    /// 칩 바 아래 — 오늘 일정 · 할 일 · 루틴/습관 · 다가오는 일정 · 마감 로드맵
    private var sectionCards: some View {
        let tasks = vm.tasks
        let routines = vm.routines
        let habits = vm.todayHabits
        return VStack(spacing: 14) {
            anchored(.calendar) {
            HomeDayCalendarCard(board: board, today: viewDate,
                                onOpenCalendar: {
                                    HomeTracker.shared.track(HomeEvent.calendarLinkClick, ["view_date": viewDate])
                                    router.openCalendar(view: .day, date: viewDate)
                                },
                                dayLabel: isToday ? "오늘" : "이날",
                                newIds: newIds,
                                onTaskAdd: { m in
                                    HomeTracker.shared.track(HomeEvent.calendarTaskAddClick, [
                                        "view_date": viewDate, "date_relation": HomeTracker.relation(viewDate),
                                        "add_mode": m.rawValue, "entry_source": "header_button",
                                    ])
                                    taskAdd = TaskAddRequest(mode: m, single: true)
                                })
            }

            anchored(.tasks) {
            HomeTasksCard(today: viewDate, tasks: tasks, togglingId: vm.togglingTaskId,
                          onToggle: { t in
                              var p = HomeTracker.taskProps(t, viewDate: viewDate)
                              p["check_action"] = HomeTracker.checkAction(t.isCompleted)
                              p["list_section"] = t.isCompleted ? "done" : "todo"
                              p["due_state"] = HomeTracker.dueState(t, today: today)
                              p["is_missed"] = t.missed
                              HomeTracker.shared.track(HomeEvent.taskCheckClick, p)
                              Task { await vm.toggleTask(t) }
                          },
                          onSelect: { t in
                              HomeTracker.shared.track(HomeEvent.taskRowClick, [
                                  "view_date": viewDate, "task_id": t.taskId, "source_type": t.sourceType.rawValue,
                                  "list_section": t.isCompleted ? "done" : "todo",
                              ])
                              board.selectTask(t, trackGrid: false)
                          },
                          onAdd: {
                              HomeTracker.shared.track(HomeEvent.taskAddClick, [
                                  "entry_point": "tasks_card", "view_date": viewDate, "task_count": tasks.count,
                              ])
                              router.openTaskAdd(mode: .manual)
                          },
                          dayLabel: isToday ? "오늘" : "이날")
            }

            anchored(.routines) {
            HomeRoutinesCard(today: viewDate, routines: routines, habits: habits, habitHome: vm.habitHome,
                             togglingId: vm.togglingTaskId,
                             onToggleTask: { t in
                                 HomeTracker.shared.track(HomeEvent.routineCheckClick, [
                                     "view_date": viewDate, "date_relation": HomeTracker.relation(viewDate),
                                     "task_id": t.taskId, "check_action": HomeTracker.checkAction(t.isCompleted),
                                     "goal_category_id": t.goalCategoryId,
                                 ])
                                 Task { await vm.toggleTask(t) }
                             },
                             onSelectTask: { t in
                                 HomeTracker.shared.track(HomeEvent.taskRowClick, [
                                     "view_date": viewDate, "task_id": t.taskId, "source_type": t.sourceType.rawValue,
                                     "list_section": "routine",
                                 ])
                                 board.selectTask(t, trackGrid: false)
                             },
                             onToggleHabit: { h in
                                 HomeTracker.shared.track(HomeEvent.habitCheckClick, [
                                     "view_date": viewDate, "date_relation": HomeTracker.relation(viewDate),
                                     "habit_id": h.habitId, "check_action": HomeTracker.checkAction(h.checked),
                                     "streak": h.streak,
                                 ])
                                 Task { await vm.toggleHabit(h, day: viewDate) }
                             },
                             onOpenHabits: {
                                 if let id = vm.habitHome?.goalCategoryId { router.openGoal(id) } else { router.openGoalList() }
                             },
                             dayLabel: isToday ? "오늘" : "이날")
            }

            anchored(.upcoming) {
            HomeUpcomingCard(days: vm.upcoming, today: viewDate,
                             onOpenDay: { d in router.openCalendar(view: .day, date: d) })
            }

            anchored(.roadmap) {
            HomeRoadmapCard(deadlines: vm.home?.deadlines, failed: vm.homeFailed, today: viewDate,
                            onOpenGoal: { id in router.openGoal(id) },
                            onOpenDay: { d in router.openCalendar(view: .day, date: d) })
            }

            // Free · 둘러보기만 (Pro 는 광고 없음)
            AdBannerSlot(role: UserStore.shared.user?.userRole, adminViewPlan: UserStore.shared.user?.adminViewPlan)
        }
    }

    /// 날짜 이동 — 오늘로 오면 다시 "오늘 따라가기"
    private func go(_ d: String, event: String) {
        var p: EventProps = ["from_date": viewDate, "to_date": d]
        if event != HomeEvent.dateTodayClick { p["date_relation"] = HomeTracker.relation(d) }
        HomeTracker.shared.track(event, p)
        UISelectionFeedbackGenerator().selectionChanged()
        picked = d == today ? nil : d
    }

    private var userName: String {
        let n = UserStore.shared.user?.userName.trimmingCharacters(in: .whitespaces) ?? ""
        return n.isEmpty ? "나를 사용자" : n
    }
}

// MARK: - 섹션 칩 바 (나의 패턴 PatternSectionNav 와 같은 모양 · 위에 붙음 · 가로 스크롤)

enum HomeSection: String, CaseIterable, Identifiable {
    case calendar, tasks, routines, upcoming, roadmap
    var id: String { rawValue }

    var label: String {
        switch self {
        case .calendar: return "오늘 일정"
        case .tasks: return "할 일"
        case .routines: return "루틴/습관"
        case .upcoming: return "다가오는 일정"
        case .roadmap: return "마감 로드맵"
        }
    }

    var icon: String {
        switch self {
        case .calendar: return "calendar"
        case .tasks: return "checklist"
        case .routines: return "repeat"
        case .upcoming: return "calendar.badge.clock"
        case .roadmap: return "flag.checkered"
        }
    }
}

enum HomeGeo {
    /// 위에 붙는 칩 바 높이 — 카드로 스크롤할 때 칩 바 아래에 오도록
    static let navHeight: CGFloat = 52
    static let navKey = "_nav"
}

/// 카드마다 "스크롤 영역 맨 위에서 얼마나 떨어졌는지"
struct HomeSectionTopKey: PreferenceKey {
    static let defaultValue: [String: CGFloat] = [:]
    static func reduce(value: inout [String: CGFloat], nextValue: () -> [String: CGFloat]) {
        value.merge(nextValue(), uniquingKeysWith: { $1 })
    }
}

struct HomeSectionNav: View {
    let current: HomeSection
    let onTap: (HomeSection) -> Void

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(HomeSection.allCases) { s in
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
        .frame(height: HomeGeo.navHeight)
        .background(Color.canvas.opacity(0.96))
        .overlay(alignment: .bottom) { Rectangle().fill(Color.borderSoft).frame(height: 0.5) }
        .animation(.easeOut(duration: 0.15), value: current)
    }
}

// MARK: - 오늘 일정 (캘린더 일간 격자)

/// 웹 HomeDayCalendar — 캘린더 화면의 일간 보기(계획 | 실제)를 그대로 넣었어요.
/// 블록을 눌러 상세 · 완료, 길게 눌러 끌어 시간 바꾸기까지 똑같이 돼요.
/// 추가도 캘린더 일간과 같아요 (2026-10): 머리글 "추가" 메뉴 · 빈 칸을 누르면 AI로 자동 Task 추가 · 계획 할 일 추가 · 실제 한 일 추가.
struct HomeDayCalendarCard: View {
    let board: TimetableBoardModel
    let today: String
    let onOpenCalendar: () -> Void
    /// 홈에서 다른 날을 보고 있으면 "이날"
    var dayLabel: String = "오늘"
    /// 방금 추가한 Task (NEW 표시)
    var newIds: Set<Int> = []
    /// 머리글 "추가" 메뉴의 AI로 자동 Task 추가 · 계획 할 일 추가 (없으면 실제 한 일만)
    var onTaskAdd: ((TaskAddMode) -> Void)? = nil

    /// 홈 전체가 스크롤되니 격자는 고정 높이 안에서 따로 스크롤해요
    static let gridHeight: CGFloat = 440

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 4) {
                Text("\(dayLabel) 일정").font(.pt(16, .semibold)).foregroundStyle(Color.ink)
                if board.isFetching && board.loadedOnce {
                    ProgressView().controlSize(.mini).tint(Color.ink4)
                }
                Spacer(minLength: 0)
                // 캘린더 + 버튼과 같은 메뉴 — AI로 자동 Task 추가 · 계획 할 일 추가 · 실제 한 일 추가 (2026-10)
                Menu {
                    TimetableAddMenuItems(onTaskAdd: { m in onTaskAdd?(m) }, onActivity: openActivityNow)
                } label: {
                    Label("추가", systemImage: "plus")
                        .font(.pt(13, .semibold))
                        .foregroundStyle(Color.ink3)
                        .padding(.horizontal, 8)
                        .frame(height: 32)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("추가")
                Button(action: onOpenCalendar) {
                    Label("캘린더", systemImage: "calendar")
                        .font(.pt(13, .semibold))
                        .foregroundStyle(Color.ink3)
                        .padding(.horizontal, 8)
                        .frame(height: 32)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
            .padding(.leading, 16)
            .padding(.trailing, 8)
            .padding(.vertical, 10)

            grid
                .frame(height: HomeDayCalendarCard.gridHeight)
        }
        .background(Color.surface)
        .clipShape(RoundedRectangle(cornerRadius: Radius.card))
        .card()
    }

    @ViewBuilder
    private var grid: some View {
        let days = board.days
        if days.isEmpty && (board.isFetching || !board.loadedOnce) {
            LoadingView(minHeight: HomeDayCalendarCard.gridHeight)
        } else if days.isEmpty {
            VStack(spacing: 8) {
                Text("TimeTable을 불러오지 못했어요").font(.pt(15, .bold)).foregroundStyle(Color.ink)
                if let e = board.loadError {
                    Text(e).font(.pt(13)).foregroundStyle(Color.ink3).multilineTextAlignment(.center)
                }
                NButton("다시 시도", variant: .secondary, size: .sm) {
                    if board.analyticsSurface == "home" {
                        HomeTracker.shared.track(HomeEvent.retryClick, ["view_date": today, "failed_section": "timetable"])
                    }
                    Task { await board.load(withActivities: true) }
                }
                .padding(.top, 4)
            }
            .padding(24)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .onAppear {
                if board.analyticsSurface == "home" {
                    HomeTracker.shared.system(HomeEvent.sectionLoadFail, ["failed_section": "timetable", "view_date": today])
                }
            }
        } else {
            let hours = TimetableLayout.visibleHours(days, activities: board.activities)
            TimetableGridView(board: board, days: days, activities: board.activities,
                              startHour: hours.startHour, endHour: hours.endHour,
                              planVsActual: true, highlightIds: newIds, emptyClickEnabled: true) {
                EmptyView()
            }
        }
    }

    /// "한 일" — 오늘 최근 1시간으로 열어요
    private func openActivityNow() {
        if board.analyticsSurface == "home" {
            HomeTracker.shared.track(HomeEvent.calendarAddActivityClick, [
                "view_date": today, "date_relation": HomeTracker.relation(today), "entry_source": "header_button",
            ])
        }
        let endMin = (KST.now().minutes / 10) * 10
        board.openActivity(TimetableActivityDraft(activity: nil, date: today, start: max(endMin - 60, 0),
                                                  end: endMin, replaced: nil))
    }
}

// MARK: - 미리보기

#Preview("홈") {
    NavigationStack {
        HomeScreen(previewVM: .preview())
    }
    .environment(AppRouter.shared)
}

#Preview("홈 — 마감 API 없음") {
    NavigationStack {
        HomeScreen(previewVM: .preview(homeFailed: true))
    }
    .environment(AppRouter.shared)
}

#Preview("오늘 일정 카드") {
    ScrollView {
        HomeDayCalendarCard(board: TimetableBoardModel.sample(start: KST.today(), end: KST.today()),
                            today: KST.today(), onOpenCalendar: {})
            .padding(16)
    }
    .canvasBackground()
    .environment(AppRouter.shared)
}

// MARK: - 맨 위 (알림함)

/// 오른쪽 위 종 — 읽지 않은 알림 수를 빨간 점으로
struct HomeTopBar: View {
    let unread: Int
    let onBell: () -> Void

    var body: some View {
        HStack {
            Spacer(minLength: 0)
            Button(action: onBell) {
                NSymbol(unread > 0 ? "bell.badge" : "bell", size: 19, weight: .medium)
                    .foregroundStyle(Color.ink2)
                    .frame(width: 40, height: 36)
                    .overlay(alignment: .topTrailing) {
                        if unread > 0 {
                            Text(unread > 99 ? "99+" : "\(unread)")
                                .font(.pt(10, .bold))
                                .foregroundStyle(Color.white)
                                .padding(.horizontal, 5)
                                .frame(minWidth: 17, minHeight: 17)
                                .background(Color.actionDanger, in: Capsule())
                                .offset(x: 2, y: -1)
                        }
                    }
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(unread > 0 ? "알림함, 읽지 않은 알림 \(unread)개" : "알림함")
        }
    }
}

#Preview("알림 종") {
    VStack(spacing: 12) {
        HomeTopBar(unread: 3) {}
        HomeTopBar(unread: 0) {}
    }
    .padding(16)
    .canvasBackground()
}
