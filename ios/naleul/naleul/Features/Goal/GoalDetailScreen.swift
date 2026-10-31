// Features/Goal/GoalDetailScreen.swift
//
// 웹 app/(main)/goal/[goalId]/page.tsx + features/goal/ui/GoalDetailView.tsx — 목표 상세.
//
// 웹은 각 영역을 "그 자리에서" 고쳤어요 (EditingContext: 한 번에 입력창 하나).
// 아이폰은 입력창을 시트로 띄우고, 어떤 시트를 띄울지(sheet)·어떤 삭제를 확인할지(confirm)를
// 이 화면의 GoalDetailViewModel 하나가 들고 있어요. 그래서 시트는 늘 하나만 떠요.
//
// 데이터: 목표 · 관련 Task · 진행("지금 어디쯤?") · 루틴 히트맵을 한 번에 불러와서 섹션들에 나눠 줘요
// (웹은 섹션마다 useQuery 를 따로 썼지만 캐시를 공유했어요 — 여기선 ViewModel 이 그 캐시 역할).
// .goals 가 낡으면(Invalidation) 전부 다시 불러와요.

import SwiftUI
import UIKit
import Observation

// MARK: - 어떤 시트를 띄울지

enum GoalDetailSheet: Identifiable, Hashable {
    /// 목표 수정 (이름·기간·색·카테고리)
    case editGoal
    /// 수치 목표만 수정 (무엇을 · 단위 · 처음 · 목표 값 · 수치 마감일) — 2026-10
    case editMetric(entryPoint: String)
    /// 세부 목표 추가(nil) · 수정
    case subGoal(SubGoalInfo?)
    /// 루틴 추가(nil) · 수정
    case routine(RoutineSummary?, subGoalId: Int?)
    /// 루틴을 다른 날에도 했어요 (반복 요일이 아닌 날 하루 추가)
    case routineExtraDay(RoutineSummary)
    /// 마일스톤 추가(nil) · 수정
    case milestone(MilestoneInfo?, isLast: Bool)
    /// Task 추가(nil) · 수정
    case task(TimeBlockTask?)
    /// 지난 수치 기록 고치기
    case metricLog(date: String, value: Double)
    /// 목표 마무리
    case wrapUp
    /// 오늘 기록 (수치 · 기분 · 한 줄 회고)
    case checkIn
    /// 업무형: 오늘 한 일 한 줄 기록
    case workLog
    /// 업무형: 적기 (할 일 · 한 일 · 회고)
    case workCompose
    /// 말로 고치기 (문장 입력)
    case aiEdit
    /// 말로 고치기 미리보기
    case aiPreview(GoalEditPreview)
    /// 목표 형태 정하기 (AI 없이 만든 목표형 목표, 2026-10) — trigger: auto · button
    case legacySetup(trigger: String)

    var id: String {
        switch self {
        case .editGoal: return "goal"
        case .editMetric: return "metric"
        case .subGoal(let s): return "sub:\(s.map { String($0.generalCategoryId) } ?? "new")"
        case .routine(let r, _): return "routine:\(r.map { String($0.routineId) } ?? "new")"
        case .routineExtraDay(let r): return "routine-extra:\(r.routineId)"
        case .milestone(let m, _): return "milestone:\(m.map { String($0.milestoneId) } ?? "new")"
        case .task(let t): return "task:\(t.map { String($0.taskId) } ?? "new")"
        case .metricLog(let d, _): return "metriclog:\(d)"
        case .wrapUp: return "wrapup"
        case .checkIn: return "checkin"
        case .workLog: return "worklog"
        case .workCompose: return "workcompose"
        case .aiEdit: return "aiedit"
        case .aiPreview: return "ai"
        case .legacySetup: return "legacy"
        }
    }
}

/// 어떤 삭제를 확인할지 (웹 InlineConfirm → iOS 알림창)
enum GoalDetailConfirm: Hashable {
    case deleteSubGoal(SubGoalInfo)
    case deleteRoutine(RoutineSummary)
    case deleteMilestone(MilestoneInfo)
    case deleteTask(TimeBlockTask)
    case deleteMetricLog(date: String, value: Double, unit: String?)

    var title: String {
        switch self {
        case .deleteSubGoal(let s): return "'\(s.generalCategoryName)'을 삭제할까요?"
        case .deleteRoutine(let r): return "'\(r.routineName)' 루틴을 삭제할까요?"
        case .deleteMilestone(let m): return "'\(m.title)'을 삭제할까요?"
        case .deleteTask(let t): return "'\(t.taskName)'을 삭제할까요?"
        case .deleteMetricLog(let d, let v, let u):
            return "\(GoalDetailLogic.md(d)) 기록(\(GoalDetailLogic.withUnit(v, u)))을 지울까요?"
        }
    }

    var message: String? {
        switch self {
        case .deleteSubGoal(let s):
            return s.routines.isEmpty ? "이 세부 목표에 연결된 일회성 Task는 남아요." : "루틴 \(s.routines.count)개와 그 루틴의 Task도 함께 삭제돼요."
        case .deleteRoutine: return "이 루틴으로 만들어진 Task도 함께 삭제돼요."
        case .deleteMilestone: return "이 마일스톤에 연결된 Task는 남고 연결만 풀려요."
        case .deleteTask(let t): return t.sourceType == .ROUTINE ? "이 날짜의 루틴 Task 하나만 지워져요. 루틴은 그대로예요." : nil
        case .deleteMetricLog: return "그래프와 현재 값이 바로 바뀌어요."
        }
    }
}

// MARK: - ViewModel

@MainActor
@Observable
final class GoalDetailViewModel {
    let goalId: Int
    var phase: LoadPhase<GoalCategory> = .loading
    var notFound = false
    var tasks: GoalTasksResponse?
    var tasksFailed = false
    var progress: GoalProgress?
    /// 진행 데이터를 한 번이라도 불러왔는지 (불러오는 중 스피너용)
    var progressLoaded = false
    var heatmap: [RoutineHeatmap]?
    var colors: [UserColor] = []

    /// 지금 띄운 시트 (하나만)
    var sheet: GoalDetailSheet?
    /// "모두 보기"(오늘 기록 · 업무형 회고)를 누르면 true → 탭이 "회고"로 바뀌고 다시 false
    var reflectionsTabRequested = false
    /// 지금 확인 중인 삭제 (알림창이 떠 있는 동안만 값이 있어요)
    var confirm: GoalDetailConfirm?
    /// 마지막으로 물어본 삭제 — 알림창이 닫힌 뒤에도 [삭제] 동작에서 읽을 수 있게
    var confirmTarget: GoalDetailConfirm?
    /// 완료 체크 중인 Task
    var togglingTaskId: Int?

    /// "말로 고치기" 입력 (오늘 기록 카드의 "최근 기록 보고 계획 조정받기"가 채워요 — 웹 AI_EDIT_PREFILL_EVENT)
    var aiEditText = ""
    /// 값이 바뀌면 화면이 말로 고치기 시트를 열어요
    var aiFocusTick = 0

    /// 미리보기용 (네트워크 안 씀)
    var isPreview = false

    // MARK: 이벤트
    private var pageviewSent = false
    /// 화면당 한 번만 보내는 노출 이벤트 키
    private var seen: Set<String> = []
    /// 목표를 못 불러왔을 때의 HTTP 상태 (실패 화면 버튼에 붙여요)
    var loadErrorStatus: Int?

    init(goalId: Int) {
        self.goalId = goalId
    }

    var goal: GoalCategory? { phase.value }

    /// goal_category_id 만 (대부분 이벤트의 기본 속성)
    var gid: EventProps { ["goal_category_id": goalId] }
    /// 할 일 목록이 쓰인 화면 (goal_detail · habit)
    var taskSurface: String { goal?.isHabitGoal == true ? "habit" : "goal_detail" }

    /// 화면당 처음 한 번만 true
    func once(_ key: String) -> Bool { isPreview ? false : seen.insert(key).inserted }

    /// 그래프 만져보기 — 그래프마다 화면당 1회
    func trackChartInteract(_ kind: String, metricId: Int? = nil) {
        guard once("chart:\(kind):\(metricId ?? 0)") else { return }
        var p: EventProps = ["chart_kind": kind]
        if let metricId { p["metric_id"] = metricId }
        track("goal_detail.chart_interact", p)
    }

    func track(_ event: String, _ props: EventProps = [:]) {
        guard !isPreview else { return }
        GD.track(event, gid, props)
    }

    func load() async {
        if isPreview { return }
        do {
            if let g = try await GoalAPI.goal(goalId) {
                phase = .loaded(g)
                notFound = false
            } else {
                notFound = true
                phase = .failed("목표를 찾을 수 없어요")
                loadErrorStatus = 404
                track("goal_detail.load_fail", ["error_status": 404])
                return
            }
        } catch is CancellationError {
            return
        } catch {
            if let e = error as? APIError, e.httpStatus == 404 { notFound = true }
            // 이미 보여주던 목표가 있으면 그대로 둬요 (웹 TanStack Query 도 캐시를 계속 보여줘요)
            if phase.value == nil || notFound {
                phase = .failed(errorMessage(error))
                loadErrorStatus = (error as? APIError)?.httpStatus ?? 0
                track("goal_detail.load_fail", ["error_status": loadErrorStatus])
            }
            return
        }

        // 나머지는 같이 불러와요
        let id = goalId
        async let tasksReq = GoalAPI.tasks(goalId: id)
        async let progressReq = GoalAPI.progress(goalId: id)
        async let heatmapReq = GoalAPI.routineHeatmap(goalId: id)

        do {
            tasks = try await tasksReq
            tasksFailed = false
        } catch {
            if tasks == nil && !(error is CancellationError) {
                tasksFailed = true
                track("goal_task_list.load_fail", ["surface": taskSurface, "error_status": (error as? APIError)?.httpStatus ?? 0])
            }
        }
        trackPageviewIfNeeded()
        do {
            progress = try await progressReq
        } catch {
            // 진행 데이터가 없어도 나머지 화면은 보여줘요
        }
        progressLoaded = true
        do {
            heatmap = try await heatmapReq
        } catch {
            if heatmap == nil { heatmap = [] }
        }
        if colors.isEmpty {
            colors = (try? await ColorAPI.userColors()) ?? []
        }
    }

    func reloadTasks() async {
        do {
            tasks = try await GoalAPI.tasks(goalId: goalId)
            tasksFailed = false
        } catch {
            tasksFailed = true
            track("goal_task_list.load_fail", ["surface": taskSurface, "error_status": (error as? APIError)?.httpStatus ?? 0])
        }
    }

    /// 목표 · 할 일을 처음 다 불러왔을 때 한 번 (습관 그릇은 habit.pageview 로 대신)
    private func trackPageviewIfNeeded() {
        guard !pageviewSent, !isPreview, let g = goal else { return }
        pageviewSent = true
        if g.isHabitGoal { return }
        let tab = g.isRecordGoal ? "board" : (g.goalCategoryStatus == .COMPLETED ? "progress" : "today")
        GD.track("goal_detail.pageview", g.analyticsBase, [
            "goal_status": g.goalCategoryStatus.rawValue, "is_temporary": g.isTemporary, "just_created": justCreated,
            "tab": tab, "has_metric": g.analyticsHasMetric, "routine_count": g.analyticsRoutineCount, "d_day": g.analyticsDDay,
        ])
    }

    /// AI 목표 확정 직후로 들어왔는지 (pageview 의 just_created)
    var justCreated = false

    /// 목표 상세에서 완료 체크 / 취소 (웹 useToggleGoalTask)
    func toggleTask(_ t: TimeBlockTask) async {
        guard togglingTaskId == nil else { return }
        togglingTaskId = t.taskId
        let completing = t.taskStatus != .COMPLETED
        track("goal_task_list.toggle_complete", [
            "surface": taskSurface, "task_id": t.taskId, "check_action": completing ? "complete" : "uncomplete",
            "source_type": t.sourceType.rawValue, "is_missed": t.missed,
            "days_from_today": GD.daysFromToday(GoalDetailLogic.dayOf(t)),
        ])
        let msg = t.taskStatus == .COMPLETED ? "완료를 취소했어요." : "완료했어요."
        let r = await Mutation.run(invalidate: [.goals, .timetable, .record], successMessage: msg) {
            try await GoalAPI.toggleTask(taskId: t.taskId, currentStatus: t.taskStatus)
        }
        track("goal_task_list.result", GD.result(r != nil).merging(["surface": taskSurface, "task_action": "toggle"]) { $1 })
        togglingTaskId = nil
    }

    /// 삭제 확인창 띄우기
    func ask(_ c: GoalDetailConfirm) {
        trackConfirm(c, step: "click")
        confirmTarget = c
        confirm = c
    }

    /// 확인창 '취소' (Android 는 바깥 누르기 포함)
    func cancelConfirm() {
        if let c = confirmTarget { trackConfirm(c, step: "cancel") }
    }

    /// 삭제 확인창 단계별 이벤트 (click · confirm · cancel)
    private func trackConfirm(_ c: GoalDetailConfirm, step: String) {
        switch c {
        case .deleteSubGoal(let s):
            track("goal_detail.structure_delete_\(step)", ["item_kind": "sub_goal", "routine_count": step == "cancel" ? nil : s.routines.count])
        case .deleteMilestone:
            track("goal_detail.structure_delete_\(step)", ["item_kind": "milestone"])
        case .deleteRoutine(let r):
            track("goal_detail.routine_delete_\(step)", ["routine_id": r.routineId])
        case .deleteTask(let t):
            GD.track("goal_task_list.delete_\(step)", ["surface": taskSurface, "task_id": t.taskId, "source_type": t.sourceType.rawValue])
        case .deleteMetricLog(let date, _, _):
            track("goal_detail.metric_log_delete_\(step)", step == "cancel" ? [:] : ["days_ago": GD.daysAgo(date)])
        }
    }

    /// 삭제 요청 결과
    private func trackDeleteResult(_ c: GoalDetailConfirm, ok: Bool) {
        let r = GD.result(ok)
        switch c {
        case .deleteSubGoal:
            track("goal_detail.structure_result", r.merging(["item_kind": "sub_goal", "structure_action": "delete"]) { $1 })
        case .deleteMilestone:
            track("goal_detail.structure_result", r.merging(["item_kind": "milestone", "structure_action": "delete"]) { $1 })
        case .deleteRoutine:
            track("goal_detail.routine_result", r.merging(["routine_action": "delete"]) { $1 })
        case .deleteTask:
            track("goal_task_list.result", r.merging(["surface": taskSurface, "task_action": "delete"]) { $1 })
        case .deleteMetricLog:
            track("goal_detail.metric_result", r.merging(["metric_action": "delete"]) { $1 })
        }
    }

    /// 확인창에서 [삭제]를 눌렀을 때
    func perform(_ c: GoalDetailConfirm) async {
        trackConfirm(c, step: "confirm")
        var ok = false
        defer { trackDeleteResult(c, ok: ok) }
        let edit: [DataKey] = [.goals, .timetable]
        switch c {
        case .deleteSubGoal(let s):
            ok = await Mutation.run(invalidate: edit, successMessage: "세부 목표를 삭제했어요.") {
                try await GoalEditAPI.deleteSubGoal(id: s.generalCategoryId)
            } != nil
        case .deleteRoutine(let r):
            ok = await Mutation.run(invalidate: edit, successMessage: "루틴을 삭제했어요.") {
                try await GoalEditAPI.deleteRoutine(id: r.routineId)
            } != nil
        case .deleteMilestone(let m):
            ok = await Mutation.run(invalidate: edit, successMessage: "마일스톤을 삭제했어요.") {
                try await GoalEditAPI.deleteMilestone(id: m.milestoneId)
            } != nil
        case .deleteTask(let t):
            ok = await Mutation.run(invalidate: edit, successMessage: "Task를 삭제했어요.") {
                try await GoalAPI.deleteTask(taskId: t.taskId)
            } != nil
        case .deleteMetricLog(let date, _, _):
            let r = await Mutation.run(invalidate: [.goals], successMessage: "기록을 지웠어요.",
                                       errorFallback: "지우지 못했어요. 다시 시도해 주세요.") {
                try await GoalEditAPI.deleteMetricLog(goalId: goalId, date: date)
            }
            ok = r != nil
            if let r, let p = r { progress = p }
        }
    }

    /// 미리보기용 ViewModel (샘플 데이터)
    static func preview(goal: GoalCategory = GoalPreviewData.goal,
                        progress: GoalProgress? = GoalPreviewData.progress) -> GoalDetailViewModel {
        let vm = GoalDetailViewModel(goalId: goal.goalCategoryId)
        vm.isPreview = true
        vm.phase = .loaded(goal)
        vm.tasks = GoalPreviewData.tasks
        vm.progress = progress
        vm.progressLoaded = true
        vm.heatmap = GoalPreviewData.heatmap
        vm.colors = GoalPreviewData.colors
        return vm
    }
}

// MARK: - 화면
//
// 모바일 배치
//  ┌ 그라데이션 머리 카드: 이름 · 상태 · 기간 · 진행 링 · 경과/남은 기간 ┐   (스크롤로 사라지면 위 제목줄에 이름)
//  ├ 탭 (위에 붙어 있어요): 오늘 · 진행 · 계획   (업무형: 보드 · 통계)        ┤
//  │  오늘: 주간 점검 · 오늘 기록 · 할 일 · 시작한 이유                      │
//  │  진행: 지금 어디쯤?(그래프) · Task 실천률 · 루틴 실천 히트맵 · 결과       │
//  │  계획: 루틴 · 세부 구조 · 마무리                                       │
//  └ 오른쪽 아래 ✨ 말로 고치기 (시트)                                      ┘
// 수정 · 마무리는 오른쪽 위 … 메뉴에서도 열 수 있어요.

@MainActor
struct GoalDetailScreen: View {
    let goalId: Int
    let justCreated: Bool

    @State private var vm: GoalDetailViewModel
    /// 웹 ?created=1 — 닫으면 주소에서 지웠어요. 여기선 이 화면 동안만 기억해요
    @State private var showPlaceCard: Bool
    /// 머리 카드가 스크롤로 가려지면 위 제목줄에 목표 이름을 보여줘요
    @State private var showNavTitle = false
    /// 스크롤 영역 위쪽 안전 영역(제목줄 높이) — 탭 바가 여기에 붙으면 "머리 카드가 가려졌다"고 봐요
    @State private var safeTop: CGFloat = 0
    /// 탭이 화면 위로 지나갔는지 (→ 탭으로 돌아가는 알약)
    @State private var showTabsPill = false
    /// 지금 탭 이름 (알약 글자)
    @State private var currentTabName = ""
    @Environment(AppRouter.self) private var router

    init(goalId: Int, justCreated: Bool) {
        self.goalId = goalId
        self.justCreated = justCreated
        let model = GoalDetailViewModel(goalId: goalId)
        model.justCreated = justCreated
        _vm = State(initialValue: model)
        _showPlaceCard = State(initialValue: justCreated)
    }

    /// 미리보기용
    init(previewVM: GoalDetailViewModel, justCreated: Bool = false) {
        self.goalId = previewVM.goalId
        self.justCreated = justCreated
        _vm = State(initialValue: previewVM)
        _showPlaceCard = State(initialValue: justCreated)
    }

    var body: some View {
        @Bindable var vm = vm

        ScrollViewReader { proxy in
        ScrollView {
            content
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .coordinateSpace(name: GoalDetailGeo.space)
        // 탭이 화면 위로 지나가면 "탭 이름 ▲" 알약 — 누르면 탭이 보이게 (맨 위가 아니라 탭까지 · DESIGN_SPEC 12.4)
        .overlay(alignment: .top) {
            if showTabsPill, !currentTabName.isEmpty {
                Button {
                    withAnimation(.easeInOut(duration: 0.3)) { proxy.scrollTo(GoalDetailGeo.tabsId, anchor: .top) }
                } label: {
                    HStack(spacing: 4) {
                        Text(currentTabName).font(.labelMD)
                        NIcon.chevronUp.image(size: 14)
                    }
                    .foregroundStyle(Color.textPrimary)
                    .padding(.horizontal, 14)
                    .frame(height: 32)
                    .background(Color.bgSurface, in: Capsule())
                    .overlay(Capsule().strokeBorder(Color.borderSubtle, lineWidth: 1))
                    .cardShadow()
                }
                .buttonStyle(.plain)
                .padding(.top, 8)
                .transition(.opacity.combined(with: .move(edge: .top)))
                .accessibilityLabel("\(currentTabName) 탭으로")
            }
        }
        .onPreferenceChange(GoalTabsBottomKey.self) { bottom in
            let hidden = bottom <= safeTop + 1
            if hidden != showTabsPill { showTabsPill = hidden }
        }
        .animation(.easeOut(duration: 0.15), value: showTabsPill)
        .onPreferenceChange(GoalCurrentTabKey.self) { name in currentTabName = name }
        }
        // 제목줄 아래 끝 (화면 좌표) — 스크롤 뷰가 제목줄 밑까지 깔려도 · 아니어도 같은 값 (DESIGN_SPEC 13.4)
        .background(GeometryReader { g in
            let navBottom = g.frame(in: .global).minY + g.safeAreaInsets.top
            Color.clear
                .onAppear { safeTop = navBottom }
                .onChange(of: navBottom) { _, v in safeTop = v }
        })
        .onPreferenceChange(GoalHeroBottomKey.self) { heroBottom in
            // 요약 카드가 위로 다 지나가면 제목줄에 목표 이름 (DESIGN_SPEC 12.4)
            let hidden = heroBottom <= safeTop + 1
            if hidden != showNavTitle { showNavTitle = hidden }
        }
        .overlay(alignment: .bottomTrailing) {
            if let goal = vm.goal, hasFab(goal) {
                VStack(spacing: NFabFace.gap) {
                    // ✨ 말로 고치기 — 공통 FAB (DESIGN_SPEC 13.6)
                    if canAiEdit(goal) {
                        GoalAIFab {
                            vm.track("goal_detail.ai_edit_open", ["entry_point": "fab", "is_prefilled": false])
                            vm.sheet = .aiEdit
                        }
                    }
                    // ✏️ 오늘 기록 (주 버튼 — 매일 누르는 것). 업무형은 "오늘 한 일" 한 줄 기록
                    if canCheckIn(goal) {
                        GoalCheckInFab(done: vm.checkedInToday) {
                            vm.track("goal_detail.checkin_open", ["entry_point": "fab", "is_edit": vm.checkedInToday])
                            vm.sheet = .checkIn
                        }
                    } else if canWorkLog(goal) {
                        // 업무형: 할 일 · 한 일 · 회고를 한곳에서 적기
                        GoalCheckInFab(done: false, label: "적기") {
                            vm.track("work_compose.open", ["compose_tab": "task", "entry_point": "fab"])
                            vm.sheet = .workCompose
                        }
                    }
                }
                .padding(.trailing, 16)
                .padding(.bottom, 16)
            }
        }
        .canvasBackground()
        // 제목은 늘 그대로 두고 가운데 글자만 보였다 숨겼다 해요 — 제목을 바꾸면 제목줄(뒤로 · ⋯)이 다시 그려지며 사라지곤 했어요 (13.4)
        .navigationTitle(vm.goal?.goalCategoryName ?? "목표")
        .navigationBarTitleDisplayMode(.inline)
        // 제목줄은 투명이 아니라 흰 바탕 (DESIGN_SPEC 12.4)
        .toolbarBackground(Color.bgSurface, for: .navigationBar)
        .toolbarBackground(.visible, for: .navigationBar)
        .toolbar {
            ToolbarItem(placement: .principal) {
                Text(vm.goal?.goalCategoryName ?? "목표")
                    .font(.labelLG)
                    .foregroundStyle(Color.textPrimary)
                    .lineLimit(1)
                    .opacity(showNavTitle ? 1 : 0)
                    .animation(.easeOut(duration: 0.15), value: showNavTitle)
                    .accessibilityHidden(!showNavTitle)
            }
            if let goal = vm.goal {
                ToolbarItem(placement: .topBarTrailing) { moreMenu(goal) }
            }
        }
        .reloadOn([.goals, .userColors], id: goalId) {
            await vm.load()
            // AI 없이 만든 목표형 목표 → 처음 들어왔을 때 한 번 "목표 형태 정하기" (그 뒤로는 머리말 버튼)
            if vm.sheet == nil, !showPlaceCard, let g = vm.goal, GoalLegacy.takeAutoOpen(g) {
                vm.sheet = .legacySetup(trigger: "auto")
            }
        }
        .environment(vm)
        // 오늘 기록 카드의 "최근 기록 보고 계획 조정받기" → 문장을 채우고 말로 고치기 시트를 열어요
        .onChange(of: vm.aiFocusTick) { vm.sheet = .aiEdit }
        .sheet(item: $vm.sheet) { s in
            sheetContent(s)
                .environment(vm)
                .environment(router)
        }
        .confirmDialog(
            isPresented: Binding(get: { vm.confirm != nil }, set: { if !$0 { vm.confirm = nil } }),
            title: vm.confirmTarget?.title ?? "",
            message: vm.confirmTarget?.message,
            confirmLabel: "삭제",
            tone: .danger,
            onCancel: { vm.cancelConfirm() }
        ) {
            // 알림창이 닫히며 confirm 이 먼저 비워질 수 있어서, 따로 기억해 둔 confirmTarget 을 써요
            if let c = vm.confirmTarget {
                Task { @MainActor in await vm.perform(c) }
            }
        }
    }

    /// 오늘 기록은 달성형 목표가 끝나기 전까지 (업무형은 업무 일지에서 한 줄 기록)
    private func canCheckIn(_ goal: GoalCategory) -> Bool {
        !goal.isRecordGoal && !goal.isHabitGoal && goal.goalCategoryStatus != .COMPLETED
    }

    /// 업무형: 끝나기 전까지 오늘 한 일 기록
    private func canWorkLog(_ goal: GoalCategory) -> Bool {
        goal.isRecordGoal && goal.goalCategoryStatus != .COMPLETED
    }

    private func hasFab(_ goal: GoalCategory) -> Bool { canCheckIn(goal) || canWorkLog(goal) || canAiEdit(goal) }

    private func canAiEdit(_ goal: GoalCategory) -> Bool {
        !goal.isRecordGoal && !goal.isHabitGoal && !goal.isTemporary && goal.goalCategoryStatus != .COMPLETED
    }

    private func moreMenu(_ goal: GoalCategory) -> some View {
        // 기본 생활 습관 그릇은 모든 사용자에게 하나씩 있는 목표라 마무리하지 않아요 (습관 단위로 쉬거나 지워요)
        let ongoing = !goal.isTemporary && goal.goalCategoryStatus == .IN_PROGRESS && !goal.isRecordGoal && !goal.isHabitGoal
        return Menu {
            Button {
                vm.track("goal_detail.edit_open", ["goal_mode": goal.analyticsMode, "entry_point": "menu"])
                vm.sheet = .editGoal
            } label: { Label("목표 수정", systemImage: "pencil") }
            if canAiEdit(goal) {
                Button {
                    vm.track("goal_detail.ai_edit_open", ["entry_point": "menu", "is_prefilled": false])
                    vm.sheet = .aiEdit
                } label: { Label("말로 고치기", systemImage: "wand.and.stars") }
            }
            if ongoing {
                Divider().overlay(Color.borderSoft)
                Button {
                    vm.track("goal_detail.wrapup_open", ["wrapup_prompt": GoalWrapUpSection.prompt(goal, metricDone: vm.progress?.metric?.status == .ACHIEVED), "entry_point": "menu"])
                    vm.sheet = .wrapUp
                } label: { Label("목표 마무리하기", systemImage: "flag.checkered") }
            }
        } label: {
            Image(systemName: "ellipsis.circle")
                .font(.system(size: 17, weight: .medium))
                .foregroundStyle(Color.ink2)
        }
        .accessibilityLabel("더보기")
    }

    @ViewBuilder private var content: some View {
        switch vm.phase {
        case .loading:
            LoadingView(minHeight: 400).padding(16)
        case .failed(let message):
            GoalDetailErrorCard(notFound: vm.notFound, message: message, errorStatus: vm.loadErrorStatus) {
                vm.phase = .loading
                await vm.load()
            }
            .padding(16)
        case .loaded(let goal):
            if goal.isHabitGoal {
                // 기본 생활 습관 (V52) — 머리 카드 · 탭 대신 습관 체크 · 잔디 · 일회성 할 일
                HabitGoalBody(goal: goal)
                    .padding(.top, 8)
                    .padding(.bottom, 32)
            } else {
                GoalDetailLoaded(goal: goal, showPlaceCard: $showPlaceCard, hasFab: hasFab(goal))
            }
        }
    }

    @ViewBuilder private func sheetContent(_ s: GoalDetailSheet) -> some View {
        if let goal = vm.goal {
            switch s {
            case .editGoal:
                GoalEditFormSheet(goal: goal, colors: vm.colors)
            case .editMetric(let entry):
                GoalMetricEditSheet(goal: goal, entryPoint: entry)
            case .subGoal(let sub):
                GoalSubGoalFormSheet(goal: goal, sub: sub, colors: vm.colors)
            case .routine(let r, let subId):
                GoalRoutineFormSheet(goal: goal, routine: r, subGoalId: subId)
            case .routineExtraDay(let r):
                GoalRoutineExtraDaySheet(routine: r)
            case .milestone(let m, let isLast):
                GoalMilestoneFormSheet(goal: goal, milestone: m, isLast: isLast)
            case .task(let t):
                // 기본 생활 습관의 일회성 Task 는 "할 일"이라고 불러요
                let noun = goal.isHabitGoal ? "할 일" : "Task"
                SheetScaffold(title: t == nil ? "\(noun) 추가" : "\(noun) 수정", detents: [.large]) {
                    GoalTaskFormView(goal: goal, task: t, onDone: { vm.sheet = nil }, analyticsSurface: vm.taskSurface)
                        .goalSheetForm()
                }
            case .metricLog(let date, let value):
                if let m = vm.progress?.metric, let p = vm.progress {
                    GoalMetricLogEditSheet(goalId: goal.goalCategoryId, metric: m, today: p.today, date: date, value: value)
                }
            case .wrapUp:
                GoalWrapUpSheet(goal: goal, metricDone: vm.progress?.metric?.status == .ACHIEVED)
            case .checkIn:
                GoalCheckInSheet(goal: goal)
            case .workLog:
                SheetScaffold(title: "오늘 한 일 기록",
                              description: "한 줄로 적으면 시각·걸린 시간을 알아서 읽어요.",
                              detents: [.medium, .large]) {
                    RecordQuickLogView(goal: goal, sheetMode: true) { vm.sheet = nil }
                }
            case .workCompose:
                WorkComposeSheet(goal: goal)
            case .aiEdit:
                GoalAiEditSheet(goal: goal)
            case .aiPreview(let preview):
                GoalAiEditPreviewSheet(goalId: goal.goalCategoryId, preview: preview)
            case .legacySetup(let trigger):
                GoalLegacySetupSheet(goal: goal, trigger: trigger)
            }
        }
    }
}

enum GoalDetailGeo {
    static let space = "goal-detail-scroll"
    /// 탭 바 자리 (알약을 누르면 여기로 스크롤)
    static let tabsId = "goal-detail-tabs"
}

/// 요약 카드 아래 끝 (스크롤 공간 기준)
struct GoalHeroBottomKey: PreferenceKey {
    static let defaultValue: CGFloat = .infinity
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) { value = min(value, nextValue()) }
}

/// 탭 바 아래 끝 (스크롤 공간 기준)
struct GoalTabsBottomKey: PreferenceKey {
    static let defaultValue: CGFloat = .infinity
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) { value = min(value, nextValue()) }
}

/// 지금 고른 탭 이름
struct GoalCurrentTabKey: PreferenceKey {
    static let defaultValue: String = ""
    static func reduce(value: inout String, nextValue: () -> String) {
        let n = nextValue()
        if !n.isEmpty { value = n }
    }
}

extension View {
    /// 요약 카드 · 탭 바 위치를 목표 상세 화면에 알려요 (제목줄 제목 · 탭 알약)
    func reportGoalHeroBottom() -> some View {
        background(GeometryReader { g in
            Color.clear.preference(key: GoalHeroBottomKey.self, value: g.frame(in: .global).maxY)
        })
    }

    func reportGoalTabs(current: String) -> some View {
        background(GeometryReader { g in
            Color.clear.preference(key: GoalTabsBottomKey.self, value: g.frame(in: .global).maxY)
        })
        .preference(key: GoalCurrentTabKey.self, value: current)
        .id(GoalDetailGeo.tabsId)
    }
}

// MARK: - 불러온 뒤 (머리 카드 + 붙는 탭 + 탭 내용)

private enum GoalDetailTab: Hashable {
    case today, progress, plan
    /// 업무형: 보드(로드맵 · 보드 · 업무 일지 · 루틴) · 통계(나의 패턴 · 루틴 실천)
    case board, stats
    /// 이 목표에 남긴 기록 · 회고를 날짜별로 (두 형태 모두)
    case reflections

    /// tab · from_tab 값
    var analyticsName: String {
        switch self {
        case .today: return "today"
        case .progress: return "progress"
        case .plan: return "plan"
        case .board: return "board"
        case .stats: return "stats"
        case .reflections: return "reflections"
        }
    }
}

@MainActor
private struct GoalDetailLoaded: View {
    let goal: GoalCategory
    @Binding var showPlaceCard: Bool
    let hasFab: Bool
    @State private var tab: GoalDetailTab
    /// 업무형: 맨 위 요약 카드와 보드 탭이 같이 쓰는 모델 (DESIGN_SPEC 12.1)
    @State private var workModel: WorkBoardModel?
    @Environment(GoalDetailViewModel.self) private var vm

    init(goal: GoalCategory, showPlaceCard: Binding<Bool>, hasFab: Bool) {
        self.goal = goal
        _showPlaceCard = showPlaceCard
        self.hasFab = hasFab
        // 목표형 기본은 진행 (끝난 목표도 진행 · DESIGN_SPEC 13.5)
        let initial: GoalDetailTab = goal.isRecordGoal ? .board : .progress
        _tab = State(initialValue: initial)
        _workModel = State(initialValue: goal.isRecordGoal
            ? WorkBoardModel(goalId: goal.goalCategoryId, routineCount: goal.analyticsRoutineCount) : nil)
    }

    private var tabs: [(GoalDetailTab, String)] {
        goal.isRecordGoal
            ? [(.board, "업무관리"), (.stats, "통계"), (.reflections, "회고")]   // 분석 값은 그대로 board (13.9)
            : [(.progress, "진행"), (.plan, "계획"), (.reflections, "회고")]
    }

    private var currentTabName: String { tabs.first(where: { $0.0 == tab })?.1 ?? "" }

    var body: some View {
        // 탭은 위에 붙지 않고 같이 스크롤돼요 (DESIGN_SPEC 12.4)
        // LazyVStack 이 아니라 VStack: 요약 카드 · 탭 바가 화면 밖으로 나가도 위치 값이 사라지지 않게
        // (Lazy 면 화면 밖에서 뷰가 내려가 값이 기본값으로 돌아가고 → 제목 · 알약이 사라졌어요 · 13.4)
        VStack(alignment: .leading, spacing: 0) {
            Group {
                if goal.isRecordGoal, let workModel {
                    WorkGoalSummaryCard(goal: goal, model: workModel)
                } else {
                    GoalDetailHeroCard(goal: goal)
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 8)
            .reportGoalHeroBottom()

            // 목표형: "이 목표를 시작한 이유"는 요약 카드 바로 아래 (DESIGN_SPEC 13.3)
            if !goal.isRecordGoal, goal.motive != nil || goal.aiNote != nil {
                GoalNotesCard(goal: goal)
                    .padding(.horizontal, 16)
                    .padding(.top, 12)
            }
            Color.clear.frame(height: 12)

            GoalDetailTabBar(selection: $tab, items: tabs) { from, to in
                vm.track("goal_detail.tab_click", ["goal_mode": goal.analyticsMode, "tab": to.analyticsName, "from_tab": from.analyticsName])
            }
            .reportGoalTabs(current: currentTabName)

            VStack(alignment: .leading, spacing: 14) {
                tabContent
            }
            .padding(.horizontal, 16)
            .padding(.top, 14)
            // 떠 있는 버튼(최대 2개)에 마지막 카드가 가리지 않게
            .padding(.bottom, hasFab ? 150 : 32)
            .id(tab)
            .transition(.opacity)
        }
        .animation(.easeOut(duration: 0.18), value: tab)
        .onChange(of: vm.reflectionsTabRequested) { _, requested in
            guard requested else { return }
            tab = .reflections
            vm.reflectionsTabRequested = false
        }
    }

    @ViewBuilder private var tabContent: some View {
        switch tab {
        case .today: GoalTodayTab(goal: goal, showPlaceCard: $showPlaceCard)
        case .progress:
            // 안내 카드(만든 직후 배치 · AI 다듬기 · 주간 점검)는 기본 탭인 진행 맨 위에
            GoalTodayTab(goal: goal, showPlaceCard: $showPlaceCard)
            GoalProgressTab(goal: goal)
        case .plan:
            // 마일스톤 → 루틴 → Task (DESIGN_SPEC 13.5)
            GoalPlanTab(goal: goal)
        case .board:
            if let workModel {
                WorkBoardTab(goal: goal, sharedModel: workModel)
            } else {
                WorkBoardTab(goal: goal)
            }
        case .stats: GoalRecordStatsTab(goal: goal)
        case .reflections: GoalReflectionsTab(goal: goal)
        }
    }
}

// MARK: - 탭 바 (밑줄 · 위에 붙음)

@MainActor
struct GoalDetailTabBar<Tab: Hashable>: View {
    @Binding var selection: Tab
    let items: [(Tab, String)]
    /// 다른 탭을 눌렀을 때 (from, to)
    var onPick: ((Tab, Tab) -> Void)? = nil
    @Namespace private var ns

    var body: some View {
        HStack(spacing: 0) {
            ForEach(items.indices, id: \.self) { i in
                let item = items[i]
                let on = item.0 == selection
                Button {
                    guard !on else { return }
                    UISelectionFeedbackGenerator().selectionChanged()
                    onPick?(selection, item.0)
                    selection = item.0
                } label: {
                    VStack(spacing: 8) {
                        Text(item.1)
                            .font(.pt(15, on ? .bold : .medium))
                            .foregroundStyle(on ? Color.ink : Color.ink3)
                        ZStack {
                            Capsule().fill(Color.clear).frame(height: 3)
                            if on {
                                Capsule().fill(Color.ink).frame(height: 3)
                                    .matchedGeometryEffect(id: "tab", in: ns)
                            }
                        }
                        .padding(.horizontal, 18)
                    }
                    .padding(.top, 10)
                    .frame(maxWidth: .infinity)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(on ? .isSelected : [])
            }
        }
        .background(Color.bgCanvas)
        .overlay(alignment: .bottom) { Rectangle().fill(Color.borderSoft).frame(height: 1) }
        .animation(.spring(response: 0.3, dampingFraction: 0.85), value: selection)
    }
}

// MARK: - 탭 내용

/// 안내 카드: 만든 직후 배치 · AI 다듬기 · 주간 점검 (진행 탭 맨 위)
@MainActor
private struct GoalTodayTab: View {
    let goal: GoalCategory
    @Binding var showPlaceCard: Bool
    @Environment(GoalDetailViewModel.self) private var vm

    var body: some View {
        let ongoing = !goal.isTemporary && goal.goalCategoryStatus == .IN_PROGRESS
        if showPlaceCard {
            GoalPlaceAfterCreateCard(goal: goal) { showPlaceCard = false }
        }
        if goal.isTemporary {
            GoalRefineWithAiCard(goal: goal)
        }
        // 주간 점검: 2주 연속 너무 힘들거나 너무 쉬우면 루틴 하루 빼기/더하기 제안
        if ongoing, let adj = vm.progress?.adjustment {
            GoalAdjustmentBanner(goalId: goal.goalCategoryId, adjustment: adj)
        }
        // "오늘 어땠나요?" 카드는 없앴어요 — 떠 있는 오늘 기록 버튼으로 (DESIGN_SPEC 12.7)
        // "시작한 이유"는 요약 카드 아래 (13.3) · Task 목록은 계획 맨 아래 (13.5)
    }
}

/// 진행: 얼마나 왔는지 — 그래프 모음
@MainActor
private struct GoalProgressTab: View {
    let goal: GoalCategory
    @Environment(GoalDetailViewModel.self) private var vm
    /// 전체 · 지금 어디쯤? · Task 실천률 · 루틴 실천 (DESIGN_SPEC 13.7)
    @State private var shown: GoalProgressViewKind = .all

    var body: some View {
        let completed = goal.goalCategoryStatus == .COMPLETED
        if completed && !goal.isTemporary {
            GoalOutcomeSection(goalId: goal.goalCategoryId, outcome: vm.progress?.outcome)
        }
        if let achievement = goal.achievement, !achievement.isEmpty {
            SectionCard("달성 기록") {
                Text(achievement).font(.pt(15)).foregroundStyle(Color.ink).lineSpacing(4)
            }
        }
        if goal.isTemporary {
            // 임시 목표는 수치 그래프가 없어서 예전처럼 실천 카드만
            if let tasks = vm.tasks {
                GoalTaskRateSection(tasks: tasks.tasks)
            }
            GoalRoutineHeatmapSection(routines: vm.heatmap)
        } else {
            GoalProgressViewChips(selection: $shown) { from, to in
                vm.track("goal_detail.progress_view_change", ["view": to.rawValue, "from_view": from.rawValue,
                                                             "goal_mode": goal.analyticsMode])
            }
            switch shown {
            case .all:
                GoalCombinedProgressCard(progress: vm.progress, tasks: vm.tasks?.tasks ?? [], routines: vm.heatmap ?? [])
                // 추가 그래프 (V52) — 대표 수치 말고 같이 볼 수치
                GoalExtraMetricsSection(goal: goal)
            case .position:
                GoalProgressSection(goal: goal)
                GoalExtraMetricsSection(goal: goal)
            case .taskRate:
                if let tasks = vm.tasks, !tasks.tasks.isEmpty {
                    GoalTaskRateSection(tasks: tasks.tasks)
                } else {
                    SectionCard("Task 실천률") { NotEnoughView(text: "날짜가 지난 Task가 생기면 보여드려요") }
                }
            case .routineRate:
                if let h = vm.heatmap, h.allSatisfy({ $0.repeatDays.isEmpty }) {
                    SectionCard("루틴 실천") { NotEnoughView(text: "루틴이 생기면 보여드려요") }
                } else {
                    GoalRoutineHeatmapSection(routines: vm.heatmap)
                }
            }
        }
    }
}

/// 계획: 마일스톤(세부 구조) → 루틴 → Task (DESIGN_SPEC 13.5)
/// 목표 마무리는 오른쪽 위 ⋯ 메뉴로 옮겼어요
@MainActor
private struct GoalPlanTab: View {
    let goal: GoalCategory

    var body: some View {
        GoalStructurePanel(goal: goal)
        GoalRoutinesSection(goal: goal)
        GoalTaskListSection(goal: goal)
    }
}

/// 회고: 이 목표에 남긴 기록(수치 · 기분 · 한 줄 회고) · 회고 글을 날짜별로 (회고 모아보기와 같은 목록)
@MainActor
private struct GoalReflectionsTab: View {
    let goal: GoalCategory
    @State private var feed: ReflectionsViewModel

    init(goal: GoalCategory) {
        self.goal = goal
        let feed = ReflectionsViewModel(goalId: goal.goalCategoryId)
        feed.surface = "goal_detail"
        _feed = State(initialValue: feed)
    }

    private var hint: String {
        goal.isRecordGoal
            ? "보드의 '적기 → 회고'나 홈의 '오늘 회고'에서 남기면 여기 모여요."
            : "오늘 탭의 '오늘 기록'이나 홈의 '오늘 회고'에서 수치 · 기분 · 한 줄 회고를 남기면 여기 모여요."
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            VStack(alignment: .leading, spacing: 4) {
                Text("이 목표의 회고").font(.pt(16, .semibold)).foregroundStyle(Color.ink)
                Text(hint).font(.pt(12)).foregroundStyle(Color.ink3)
            }
            ReflectionTimelineView(vm: feed, showGoal: false, emptyHint: hint)
        }
        .reloadOn([.home, .goals, .work], id: feed.reloadId) { await feed.load() }
    }
}

/// 업무형 통계: 이 목표에 대한 나의 패턴 + 루틴 실천 (보드 탭에 있던 걸 웹처럼 여기로)
@MainActor
private struct GoalRecordStatsTab: View {
    let goal: GoalCategory
    @Environment(GoalDetailViewModel.self) private var vm

    var body: some View {
        RecordPatternSectionView(goalId: goal.goalCategoryId)
        GoalRoutineHeatmapSection(routines: vm.heatmap)
            .onFirstHalfVisible {
                WK.track("record_stats.card_view", goalId: goal.goalCategoryId, ["stats_card": "routine_heatmap"])
            }
    }
}

// MARK: - 머리 카드 (이름 · 상태 · 기간 · 진행 링)

@MainActor
private struct GoalDetailHeroCard: View {
    let goal: GoalCategory
    @Environment(GoalDetailViewModel.self) private var vm

    private struct Summary {
        var ratio: Double
        var caption: String
        var headline: String
    }

    private var period: GoalFormat.Period? {
        GoalFormat.periodProgress(goal.goalCategoryStartDate, goal.goalCategoryEndDate)
    }

    /// 업무형 · 임시 목표는 진행률이 의미 없어서 링 없이 이름만
    private var showsProgress: Bool { !goal.isTemporary && !goal.isRecordGoal }

    /// 수치 목표면 수치 진행률, 아니면 기간 경과율
    private var summary: Summary {
        let unit = goal.metricUnit ?? ""
        if let start = goal.startValue, let target = goal.targetValue {
            let current = goal.currentValue ?? start
            let span = target - start
            let ratio = span == 0 ? 1 : (current - start) / span
            let caption = "목표까지 남은 \(goal.metricName ?? "")".trimmingCharacters(in: .whitespaces)
            return Summary(ratio: ratio, caption: caption, headline: "\(GoalDetailLogic.trim(abs(target - current), 1))\(unit)")
        }
        return Summary(ratio: period?.ratio ?? 0, caption: "기간 경과", headline: period.map { "\($0.elapsed)일째" } ?? "-")
    }

    private var titleText: String {
        if let e = goal.emoji, !e.isEmpty { return e + " " + goal.goalCategoryName }
        return goal.goalCategoryName
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            VStack(alignment: .leading, spacing: 8) {
                Text(titleText)
                    .font(.pt(20, .semibold))
                    .foregroundStyle(Color.textPrimary)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.trailing, 22)
                FlowLayout(spacing: 6) {
                    pill(goal.goalCategoryStatus.label, icon: goal.goalCategoryStatus == .COMPLETED ? "checkmark" : nil)
                    if goal.isTemporary { pill("임시 목표") }
                    if goal.isRecordGoal { pill(recordBadge) }
                    if let kind = goal.goalKindName, goal.goalType != .WORK { pill(kind) }
                    if let start = goal.goalCategoryStartDate { pill(periodLine(start), icon: "calendar") }
                }
            }

            if showsProgress {
                progressRow
                if let period {
                    HStack(spacing: 8) {
                        if goal.targetValue != nil, let sv = goal.startValue {
                            statBox("현재", "\(GoalDetailLogic.trim(goal.currentValue ?? sv, 6))\(goal.metricUnit ?? "")")
                        }
                        statBox("경과", "\(period.elapsed) / \(period.total)일")
                        statBox("남은 기간", GoalFormat.dDayLabel(period.remain))
                    }
                }
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        // NowCard 스타일: 목표 tint 바탕
        .background(Color.goalTone(goal.colorCode).bg, in: RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
        .overlay(alignment: .topTrailing) {
            // 목표 색 표시 (목록 · 캘린더와 같은 색)
            Circle().fill(Color.goal(goal.colorCode))
                .overlay(Circle().stroke(Color.bgSurface, lineWidth: 2))
                .frame(width: 14, height: 14)
                .padding(16)
        }
    }

    private var progressRow: some View {
        let s = summary
        let clamped = min(max(s.ratio, 0), 1)
        return HStack(spacing: 18) {
            ZStack {
                Circle().stroke(Color.bgSurface.opacity(0.7), lineWidth: 9)
                Circle()
                    .trim(from: 0, to: clamped)
                    .stroke(Color.goalTone(goal.colorCode).accent, style: StrokeStyle(lineWidth: 9, lineCap: .round))
                    .rotationEffect(.degrees(-90))
                    .animation(.easeOut(duration: 0.6), value: clamped)
                Text("\(Int((clamped * 100).rounded()))%")
                    .font(.ptMono(20, .bold))
                    .foregroundStyle(Color.textPrimary)
            }
            .frame(width: 88, height: 88)

            VStack(alignment: .leading, spacing: 2) {
                Text(s.caption).font(.pt(13)).foregroundStyle(Color.textSecondary)
                Text(s.headline)
                    .font(.pt(28, .bold))
                    .foregroundStyle(Color.textPrimary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.6)
                if let status = vm.progress?.metric?.status {
                    Text(statusText(status))
                        .font(.pt(12, .semibold))
                        .foregroundStyle(Color.textPrimary)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(Color.bgSurface.opacity(0.7), in: Capsule())
                        .padding(.top, 4)
                }
            }
            Spacer(minLength: 0)
        }
    }

    private func statusText(_ s: ProgressStatus) -> String {
        switch s {
        case .ACHIEVED: return "목표 달성 🎉"
        case .AHEAD: return "계획보다 앞서요"
        case .ON_TRACK: return "계획대로 가는 중"
        case .BEHIND: return "계획보다 조금 늦어요"
        case .NOT_STARTED: return "시작 전"
        case .NO_PLAN: return "종료일 없음"
        }
    }

    private func pill(_ text: String, icon: String? = nil) -> some View {
        HStack(spacing: 4) {
            if let icon { NSymbol(icon, size: 10, weight: .bold) }
            Text(text)
        }
        .font(.pt(12, .semibold))
        .foregroundStyle(Color.textPrimary)
        .padding(.horizontal, 9)
        .frame(height: 24)
        .background(Color.bgSurface.opacity(0.7), in: Capsule())
    }

    private func statBox(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(.pt(11)).foregroundStyle(Color.textSecondary)
            Text(value)
                .font(.ptMono(15, .bold))
                .foregroundStyle(Color.textPrimary)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 9)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.bgSurface.opacity(0.7), in: RoundedRectangle(cornerRadius: Radius.xl))
    }

    /// 업무형은 회사 업무 · 취업 준비 · 동아리처럼 종류가 다양해서 종류를 나누지 않아요
    private var recordBadge: String { "업무형" }

    private func periodLine(_ start: String) -> String {
        // 업무형은 전체 마감이 없어요 (V53 — 마감은 주제마다)
        if goal.isRecordGoal { return "\(GoalFormat.formatDot(start)) 시작 · 마감은 세부 목표마다" }
        let end = goal.goalCategoryEndDate.map { GoalFormat.formatDot($0) } ?? "종료일 미정"
        return "\(GoalFormat.formatDot(start)) – \(end)"
    }
}

// MARK: - ✨ 말로 고치기 떠 있는 버튼

@MainActor
private struct GoalAIFab: View {
    let action: () -> Void

    var body: some View {
        Button {
            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
            action()
        } label: {
            // 공통 FAB (56 · 아이콘 22 = 캘린더 + 버튼 · DESIGN_SPEC 13.6)
            NFabFace(icon: "wand.and.stars", style: .ai)
        }
        .buttonStyle(NFabPressStyle())
        .accessibilityLabel("말로 고치기")
    }
}

// MARK: - ✏️ 오늘 기록 떠 있는 버튼

@MainActor
private struct GoalCheckInFab: View {
    /// 오늘 이미 기록했으면 작은 체크 표시
    let done: Bool
    var label: String? = nil
    let action: () -> Void

    var body: some View {
        Button {
            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
            action()
        } label: {
            NFabFace(icon: "square.and.pencil")
                .overlay(alignment: .topTrailing) {
                    if done {
                        NSymbol("checkmark", size: 10, weight: .heavy)
                            .foregroundStyle(Color.onAccentMint)
                            .frame(width: 20, height: 20)
                            .background(Color.accentMint, in: Circle())
                            .overlay(Circle().stroke(Color.surface, lineWidth: 2))
                            .offset(x: 2, y: -2)
                    }
                }
        }
        .buttonStyle(NFabPressStyle())
        .accessibilityLabel(label ?? (done ? "오늘 기록 수정" : "오늘 기록하기"))
    }
}

// MARK: - 불러오지 못했을 때

@MainActor
private struct GoalDetailErrorCard: View {
    let notFound: Bool
    let message: String
    var errorStatus: Int? = nil
    let retry: () async -> Void
    @Environment(AppRouter.self) private var router

    var body: some View {
        VStack(spacing: 8) {
            Text(notFound ? "목표를 찾을 수 없어요" : "목표를 불러오지 못했어요")
                .font(.pt(16, .semibold))
                .foregroundStyle(Color.ink)
            if !notFound {
                Text(message).font(.pt(14)).foregroundStyle(Color.ink3).multilineTextAlignment(.center)
            }
            HStack(spacing: 8) {
                NButton("목표 목록", variant: .ghost, size: .sm) {
                    GD.track("goal_detail.load_fail_cta_click", ["cta": "goal_list", "error_status": errorStatus])
                    router.openGoalList()
                }
                if !notFound {
                    NButton("다시 시도", size: .sm) {
                        GD.track("goal_detail.load_fail_cta_click", ["cta": "retry", "error_status": errorStatus])
                        Task { @MainActor in await retry() }
                    }
                }
            }
            .padding(.top, 12)
        }
        .padding(32)
        .frame(maxWidth: .infinity)
        .card()
    }
}

#Preview("달성형") {
    NavigationStack {
        GoalDetailScreen(previewVM: .preview(), justCreated: true)
    }
    .environment(AppRouter.shared)
}

#Preview("업무형") {
    NavigationStack {
        GoalDetailScreen(previewVM: .preview(goal: GoalPreviewData.recordGoal))
    }
    .environment(AppRouter.shared)
}

#Preview("임시 목표") {
    NavigationStack {
        GoalDetailScreen(previewVM: .preview(goal: GoalPreviewData.tempGoal, progress: GoalPreviewData.taskOnlyProgress))
    }
    .environment(AppRouter.shared)
}
