// Features/Calendar/TimetableBoardModel.swift
//
// TimeTable 위에서 하는 모든 조작(상세 시트 · 완료 · 시간 변경 · 삭제 · 드래그 · AI 배치 미리보기 · 이월 제안)을
// 한곳에 모은 화면 상태. 캘린더(주간 · 일간 · 월간)와 Task 추가 결과 미리보기가 같은 동작을 쓰도록 분리했어요.
// 웹 원본: features/timetable/ui/useTimetableInteractions.tsx (+ CalendarView.tsx 의 useTimetable · useActivities 조회)
//
// 사용:
//   @State private var board = TimetableBoardModel()
//   .reloadOn([.timetable, .activities], id: rangeKey) { board.setRange(s, e); await board.load(withActivities: true) }
//   .timetableSheets(board)        // 블록을 누르면 뜨는 시트
//   TimetableBottomBars(board: board)  // 범위 고르기 · AI 배치 확인 · 이월 제안 바

import SwiftUI
import UIKit
import Observation

@MainActor
@Observable
final class TimetableBoardModel {
    // MARK: 데이터 (서버에서 받은 그대로)

    private(set) var rawDays: [TimetableDay] = []
    private(set) var activities: [ActualActivity] = []
    private(set) var rangeStart = ""
    private(set) var rangeEnd = ""
    /// 다시 불러오는 중 (처음 불러오기 포함)
    private(set) var isFetching = false
    private(set) var loadedOnce = false
    private(set) var loadError: String?
    /// 불러오기 실패의 HTTP 상태 (calendar.load_fail 의 error_status · 0 = 네트워크)
    private(set) var loadErrorStatus: Int?
    @ObservationIgnored private var fetchSeq = 0
    /// 미리보기(#Preview)용 — 네트워크를 쓰지 않아요
    @ObservationIgnored private var offline = false

    // MARK: 화면 상태

    /// 지금 떠 있는 시트 (블록 상세 · 고정 시간 · 실제로 한 일 · 시간 입력)
    var sheet: TimetableSheet?
    /// 불러온 뒤 열 Task 상세 (알림에서 들어올 때)
    @ObservationIgnored private var openAfterLoadId: Int?
    /// "삭제할까요?"를 띄운 Task (상세 시트 안에서 확인해요)
    var confirmDeleteId: Int?
    /// 드래그로 옮긴 뒤 서버 응답을 기다리는 동안 새 위치에 그려 둘 블록들
    private(set) var pending: [TimetablePendingEntry] = []
    /// 저장은 끝났고 다시 불러오기를 기다리는 임시 표시 (다 불러오면 지워요 — 먼저 지우면 원래 자리가 잠깐 보여서)
    @ObservationIgnored private var settling: Set<UUID> = []
    /// 드래그 직후 "어디까지 바꿀까요?"
    private(set) var ask: TimetableScopeAsk?
    /// 묶음 드래그로 같이 옮길 Task id ("이어진 N개 묶어 옮기기")
    private(set) var group: [Int] = []
    /// 블록을 2초 꾹 눌러 들어간 "묶음 고르기" 모드 — 다른 블록을 누르면 묶음에 넣고 빼요 (DESIGN_SPEC 10.5)
    private(set) var groupSelecting = false
    /// 캘린더 빈 칸을 눌러 띄운 + 메뉴 (그 날짜 · 시각) — CalendarScreen 이 선택지를 보여줘요 (DESIGN_SPEC 11.4)
    var emptySlot: TimetableEmptySlot?
    /// 빈 칸 누르기 = + 메뉴 (캘린더 · 홈 오늘 일정, 2026-10 부터 홈도 같은 메뉴) · 그 밖(Task 추가 결과 격자)은 바로 실제 기록
    var emptyTapOpensMenu: Bool { analyticsSurface == "calendar" || analyticsSurface == "home" }
    /// "AI로 배치하기" 미리보기 제안 (저장 전 점선 블록). nil 이면 미리보기 중 아님
    private(set) var proposals: [TimetableLayout.Proposal]?
    private(set) var proposalUnscheduled = 0
    private(set) var proposalKind: TimetableProposalKind = .fill
    @ObservationIgnored private var resolveHandler: ((Bool, [TimetableLayout.Proposal]) -> Void)?
    /// 끄는 중인 블록
    private(set) var drag: TimetableDragState?
    /// 길게 눌러 "들어 올린" 블록 (아직 움직이기 전) — 이 블록은 커지고 나머지는 흐려져요
    private(set) var liftedOwnerId: String?
    /// 자정을 넘기는 고정 시간 수정 때만 필요 → 고정 시간 시트를 열 때 불러와요
    @ObservationIgnored private var lifePatterns: [LifePattern]?

    // MARK: 진행 중 표시

    private(set) var togglingId: Int?
    private(set) var busyTask = false
    private(set) var deleting = false
    private(set) var busyFixed = false
    private(set) var editSaving = false
    private(set) var fillPreviewing = false
    private(set) var fillApplying = false
    private(set) var carryBusy: TimetableCarryAction?

    /// 이벤트 출처 — home 이면 home.calendar_* 이벤트를 보내요 (캘린더 화면 이벤트는 캘린더 분류에서 붙여요)
    @ObservationIgnored var analyticsSurface = "calendar"

    init() {}

    var isOffline: Bool { offline }

    // MARK: - 이벤트 (홈 일정 격자)

    private var isHome: Bool { analyticsSurface == "home" && !offline }

    private func homeTrack(_ event: String, _ props: EventProps) {
        guard isHome else { return }
        HomeTracker.shared.track(event, props)
    }

    // MARK: - 이벤트 (캘린더 분류 — 웹 useTimetableInteractions 와 같은 이름)

    /// 시트 · 아래 바 이벤트 (홈 · 캘린더 · Task 추가 결과 모두) — surface 를 붙여요
    func sheetTrack(_ event: String, _ props: EventProps = [:]) {
        if offline { return }
        CAL.track(event, ["surface": analyticsSurface].merging(props) { _, new in new })
    }

    /// 격자 클릭 · 끌기 (timetable.*) — 홈은 home.calendar_* 를 따로 보내서 여기선 안 보내요
    func gridTrack(_ event: String, _ props: EventProps = [:]) {
        if analyticsSurface == "home" { return }
        sheetTrack(event, props)
    }

    /// 캘린더 격자 보기 (일간 1일 · 주간 7일). 캘린더가 아니면 nil
    private var gridViewMode: String? {
        guard analyticsSurface == "calendar" else { return nil }
        if rangeStart == rangeEnd { return "day" }
        return KST.diffDays(rangeStart, rangeEnd) == 2 ? "three_day" : "week"
    }

    /// 다른 날로 옮겨진 Task 흔적을 눌렀을 때 (timetable.carried_trace_click)
    func trackCarriedTrace(taskId: Int, traceDate: String, toDate: String) {
        gridTrack("timetable.carried_trace_click", [
            "view_mode": gridViewMode, "task_id": taskId, "is_scheduled": true,
            "day_offset": KST.diffDays(traceDate, toDate),
        ])
    }

    /// drag_source — proposal(점선 제안) · group(묶음) · task · fixed · actual
    private static func gridDragSource(_ src: TimetableDragSource) -> String {
        switch src {
        case let .task(t, _, _, _, group): return t.proposed == true ? "proposal" : (group.isEmpty ? "task" : "group")
        case .fixed: return "fixed"
        case .actual: return "actual"
        }
    }

    /// drag_mode — move · resize_start(위 끝) · resize_end(아래 끝)
    private static func gridDragMode(_ m: TimetableDragMode) -> String {
        switch m {
        case .move: return "move"
        case .resize: return "resize_end"
        case .resizeTop: return "resize_start"
        }
    }

    /// 놓을 수 없던 이유 문구 → cancel_reason (validate 문구와 맞춰요)
    private static func invalidReason(_ message: String) -> String {
        if message.contains("겹쳐 있어요") { return "crowded" }
        if message.contains("기상") || message.contains("취침") { return "sleep_rule" }
        if message.contains("나눠서") { return "actual_overlap" }
        return "invalid"
    }

    /// scope_target — routine · fixed · sleep_wake · sleep_bed
    private static func scopeTarget(_ a: TimetableScopeAsk) -> String {
        switch a.kind {
        case .routine: return "routine"
        case let .fixed(_, edge):
            if edge == .wake { return "sleep_wake" }
            if edge == .bed { return "sleep_bed" }
            return "fixed"
        }
    }

    /// '어디까지 바꿀까요?' 바가 뜰 때
    private func showAsk(_ a: TimetableScopeAsk) {
        ask = a
        sheetTrack("scope_chooser.view", [
            "scope_target": Self.scopeTarget(a),
            "needs_scope": a.options.contains { $0.scope != .DAY && $0.disabledReason == nil },
        ])
    }

    /// 범위를 고르지 않은 채 다른 동작을 해서 "이날만"으로 저장 (other_block_tap · next_drag · leave_page)
    private func autoDay(_ reason: String) {
        guard let a = ask else { return }
        sheetTrack("scope_chooser.auto_day", ["scope_target": Self.scopeTarget(a), "auto_reason": reason])
        chooseScope(.DAY)
    }

    /// 아래 바에서 범위를 직접 고름
    func chooseScopeByUser(_ scope: ChangeScope) {
        guard let a = ask else { return }
        sheetTrack("scope_chooser.choose", ["scope_target": Self.scopeTarget(a), "change_scope": scope.rawValue,
                                            "submit_method": "button"])
        chooseScope(scope)
    }

    /// 아래 바 X — 원래 자리로
    func cancelAskByUser() {
        if let a = ask { sheetTrack("scope_chooser.cancel", ["scope_target": Self.scopeTarget(a), "close_method": "x_button"]) }
        cancelAsk()
    }

    /// 묶음 바 '묶음 풀기'
    func clearGroupByUser() {
        gridTrack("timetable.group_clear", ["group_size": groupIds.count, "close_method": "cancel_button"])
        clearGroup()
    }

    private static func dragProps(_ src: TimetableDragSource) -> EventProps {
        switch src {
        case let .task(t, _, _, _, group):
            return ["drag_source": "task", "task_id": t.taskId, "source_type": t.sourceType.rawValue,
                    "group_size": group.isEmpty ? 1 : group.count]
        case .fixed: return ["drag_source": "fixed"]
        case let .actual(t, _, _, _, _): return ["drag_source": "actual", "task_id": t.taskId]
        }
    }

    private static func modeName(_ m: TimetableDragMode) -> String {
        switch m {
        case .move: return "move"
        case .resize: return "resize"
        case .resizeTop: return "resize_top"
        }
    }

    // MARK: - 불러오기

    /// 보여줄 기간을 바꿔요 (바로 바꿔야 이전 기간 블록이 새 기간에 섞여 보이지 않아요)
    func setRange(_ start: String, _ end: String) {
        if rangeStart != start { rangeStart = start }
        if rangeEnd != end { rangeEnd = end }
    }

    func load(withActivities: Bool) async {
        if offline { return }
        let s = rangeStart
        let e = rangeEnd
        fetchSeq += 1
        let seq = fetchSeq
        isFetching = true
        do {
            let res = try await TimetableAPI.timetable(start: s, end: e)
            var acts: [ActualActivity] = []
            if withActivities {
                // 실제 기록을 못 받아도 TimeTable 은 보여줘요
                acts = (try? await TimetableAPI.activities(start: s, end: e)) ?? []
            }
            // 그사이 기간이 바뀌었으면(주 이동 등) 버려요
            if !Task.isCancelled && s == rangeStart && e == rangeEnd {
                rawDays = res.days
                activities = acts
                loadError = nil
                loadedOnce = true
                finishSettling()
                openPendingTask()
            }
        } catch is CancellationError {
            // 다음 불러오기가 이어받아요
        } catch {
            if s == rangeStart && e == rangeEnd {
                loadError = errorMessage(error)
                loadErrorStatus = CAL.status(error)
                finishSettling()
            }
        }
        if seq == fetchSeq { isFetching = false }
    }

    /// 알림에서 들어오면 — 이미 불러온 기간에 있으면 바로, 아니면 다음 불러오기 뒤에 상세 시트를 열어요
    func openTaskWhenLoaded(_ taskId: Int) {
        openAfterLoadId = taskId
        openPendingTask()
    }

    private func openPendingTask() {
        guard let id = openAfterLoadId else { return }
        for d in rawDays {
            if let t = d.tasks.first(where: { $0.taskId == id }) {
                openAfterLoadId = nil
                sheet = .task(t)
                return
            }
        }
    }

    /// 지금 보는 기간의 서버 데이터 (임시 표시 없음)
    var rangeDays: [TimetableDay] {
        rawDays.filter { $0.date >= rangeStart && $0.date <= rangeEnd }
    }

    /// 화면에 그릴 날짜들: 서버 데이터 + 드래그 임시 위치 + AI 제안(점선)
    var days: [TimetableDay] {
        TimetableLayout.applyProposals(TimetableLayout.applyPending(rangeDays, pending.map { $0.value }), proposals)
    }

    /// 묶음 중 지금도 옮길 수 있는 Task 만 (완료했거나 시간 미정이 된 Task 는 빠져요)
    var groupIds: Set<Int> {
        if group.isEmpty { return [] }
        var live = Set<Int>()
        for d in days {
            for t in d.tasks where TimetableRules.groupable(t) { live.insert(t.taskId) }
        }
        return Set(group.filter { live.contains($0) })
    }

    /// 22시 이월 제안 (아직 확인 안 한 것) — 오늘 이후, 시간순
    var carryProposals: [TimeBlockTask] {
        let today = KST.today()
        var seen = Set<Int>()
        var out: [TimeBlockTask] = []
        for d in days {
            for t in d.tasks + d.unscheduledTasks {
                if t.carryPending != true || t.taskStatus != .TODO || seen.contains(t.taskId) { continue }
                seen.insert(t.taskId)
                let day = TimetableRules.blockDay(t) ?? t.date ?? today
                if day >= today { out.append(t) }
            }
        }
        return out.sorted { a, b in
            (a.plannedStartAt ?? "\(a.date ?? today)T99") < (b.plannedStartAt ?? "\(b.date ?? today)T99")
        }
    }

    var proposing: Bool { proposals != nil }

    /// 확인을 기다리는 블록 — AI 배치 미리보기면 제안들(나머지는 흐리게), 아니면 화면 아래에서 묻고 있는 이월 제안 하나
    var focusIds: Set<Int> {
        if let p = proposals { return Set(p.map { $0.taskId }) }
        if ask == nil, groupIds.isEmpty, let c = carryProposals.first { return [c.taskId] }
        return []
    }

    /// 사용자가 끌어서 고친 제안 수
    var editedProposals: Int {
        (proposals ?? []).filter { $0.start != $0.proposedStart || $0.end != $0.proposedEnd }.count
    }

    /// 데이터가 새로 오면 시트 안의 Task 도 최신 값으로
    func liveTask(_ t: TimeBlockTask) -> TimeBlockTask {
        for d in days {
            if let x = d.tasks.first(where: { $0.taskId == t.taskId }) { return x }
            if let x = d.unscheduledTasks.first(where: { $0.taskId == t.taskId }) { return x }
        }
        return t
    }

    // MARK: 선택 표시

    var selectedTaskId: Int? {
        if case .task(let t)? = sheet { return t.taskId }
        return nil
    }

    var selectedFixedKey: String? {
        if case .fixed(let b)? = sheet { return b.key }
        return nil
    }

    var selectedActivityId: Int? {
        if case .activity(let d)? = sheet { return d.activity?.activityId }
        return nil
    }

    // MARK: - 시트 열기 · 닫기

    func close() {
        sheet = nil
        confirmDeleteId = nil
    }

    /// 시트를 아래로 끌어 닫았을 때
    func sheetDismissed() {
        confirmDeleteId = nil
    }

    /// trackGrid: 격자 블록을 눌렀을 때만 true (홈 할 일 줄은 home.task_row_click 을 따로 보내요)
    func selectTask(_ t: TimeBlockTask, trackGrid: Bool = true) {
        // 묶음 고르기 중에는 어떤 탭도 상세를 열지 않아요 — 묶음에 넣고 빼기만 (DESIGN_SPEC 13.1)
        if groupSelecting {
            toggleGroupMember(t)
            return
        }
        if trackGrid {
            homeTrack(HomeEvent.calendarTaskBlockClick, [
                "view_date": rangeStart, "task_id": t.taskId, "source_type": t.sourceType.rawValue,
                "task_status": t.taskStatus.rawValue, "is_scheduled": t.plannedStartAt != nil,
                "goal_category_id": t.goalCategoryId,
            ])
            gridTrack("timetable.task_block_click", [
                "view_mode": gridViewMode, "task_id": t.taskId, "source_type": t.sourceType.rawValue,
                "task_status": t.taskStatus.rawValue, "is_scheduled": t.plannedStartAt != nil,
                "is_locked": t.locked, "drag_source": t.proposed == true ? "proposal" : "task",
            ])
        }
        // 범위를 고르지 않고 다른 블록을 누르면 앞의 변경은 "이날만"으로 저장해요 (웹: 바깥 클릭)
        if ask != nil { autoDay("other_block_tap") }
        confirmDeleteId = nil
        sheet = .task(t)
    }

    func selectFixed(_ b: FixedBlock) {
        homeTrack(HomeEvent.calendarFixedBlockClick, ["view_date": rangeStart, "pattern_type": b.patternType.rawValue])
        gridTrack("timetable.fixed_block_click", ["pattern_type": b.patternType.rawValue, "is_overridden": b.overridden])
        if ask != nil { autoDay("other_block_tap") }
        sheet = .fixed(b)
        Task { await ensurePatterns() }
    }

    func selectActivity(_ a: ActualActivity) {
        homeTrack(HomeEvent.calendarActivityBlockClick, ["view_date": rangeStart])
        gridTrack("timetable.activity_block_click", ["days_ago": CAL.daysAgo(a.startAt)])
        if ask != nil { autoDay("other_block_tap") }
        confirmDeleteId = nil
        sheet = .activity(TimetableActivityDraft.of(a))
    }

    /// 새 기록 열기 (끝은 지금을 넘지 않게)
    func openActivity(_ draft: TimetableActivityDraft) {
        let now = KST.now()
        let nowMin = draft.date == now.date ? now.minutes : Int.max
        let end = min(draft.end, nowMin)
        let start = min(draft.start, end - 10)
        var d = draft
        d.start = max(start, 0)
        d.end = end
        confirmDeleteId = nil
        sheet = .activity(d)
    }

    /// 빈 칸을 누름 → 그 시각부터 1시간 실제 기록
    func emptyClick(date: String, minutes: Int) {
        homeTrack(HomeEvent.calendarEmptySlotClick, [
            "view_date": date, "date_relation": HomeTracker.relation(date), "slot_minutes": minutes,
        ])
        gridTrack("timetable.empty_slot_click", [
            "view_date": date, "date_relation": CAL.relation(date), "slot_minutes": minutes,
        ])
        if ask != nil { autoDay("other_block_tap") }
        if emptyTapOpensMenu {
            // 지금보다 앞이면 실제 한 일도 고를 수 있어요 (자정 뒤 칸은 다음 날 시각)
            let now = KST.now()
            let past = date < now.date
                || (date == now.date && minutes < now.minutes)
                || (KST.addDays(date, 1) == now.date && minutes < now.minutes + 1440)
            emptySlot = TimetableEmptySlot(date: date, minutes: minutes, past: past)
            return
        }
        openActivity(TimetableActivityDraft(activity: nil, date: date, start: minutes, end: minutes + 60, replaced: nil))
    }

    private func ensurePatterns() async {
        if offline || lifePatterns != nil { return }
        lifePatterns = try? await TimetableAPI.lifePatterns()
    }

    // MARK: - Task 작업

    /// 계획했던 시간에 다른 일을 했어요 → 그 시간으로 실제 기록 (계획 Task 연결)
    func recordInstead(_ t: TimeBlockTask) {
        guard let ps = t.plannedStartAt else { return }
        let day = String(ps.prefix(10))
        let s = KST.minutesFrom(day, ps)
        let e = t.plannedEndAt.map { KST.minutesFrom(day, $0) } ?? s + (t.plannedDurationMinutes ?? 30)
        openActivity(TimetableActivityDraft(activity: nil, date: day, start: s, end: e,
                                            replaced: TimetableReplacedTask(id: t.taskId, name: t.taskName)))
    }

    /// "계획과 다른 시간에 · 나눠서 했어요" — 실제 시각 입력 창 (이미 끝낸 Task 는 남긴 실제 시간부터 고쳐요)
    func completeWithTime(_ t: TimeBlockTask) {
        guard let ps = t.plannedStartAt else { return }
        let hasActual = t.taskStatus == .COMPLETED && t.actualStartAt != nil && t.actualEndAt != nil
        // 실제로 다른 날 했으면 그 날 기준
        let day = String((hasActual ? t.actualStartAt! : ps).prefix(10))
        let s = KST.minutesFrom(day, hasActual ? t.actualStartAt! : ps)
        let e = hasActual
            ? KST.minutesFrom(day, t.actualEndAt!)
            : (t.plannedEndAt.map { KST.minutesFrom(day, $0) } ?? s + (t.plannedDurationMinutes ?? 30))
        confirmDeleteId = nil
        sheet = .timeEdit(TimetableTimeEditing(target: .actual(t),
                                               initial: TimetableTimeValue(date: day, start: s, end: max(e, s + 10))))
    }

    func toggleComplete(_ t: TimeBlockTask, closeOnSuccess: Bool) async {
        if togglingId != nil { return }
        togglingId = t.taskId
        let msg = t.taskStatus == .COMPLETED ? "완료를 취소했어요." : "완료했어요."
        let ok = await Mutation.run(invalidate: [.timetable, .goals], successMessage: msg) {
            try await TimetableAPI.toggleComplete(taskId: t.taskId, currentStatus: t.taskStatus)
        }
        togglingId = nil
        // 상세 시트에서 누른 것만 (목록 · 월간 체크는 각자 이벤트)
        if closeOnSuccess {
            sheetTrack("task_detail.action_result", CAL.result(ok != nil).merging([
                "task_action": t.taskStatus == .COMPLETED ? "uncomplete" : "complete",
            ]) { $1 })
        }
        if ok != nil && closeOnSuccess { close() }
    }

    func unlock(_ t: TimeBlockTask) async {
        busyTask = true
        let ok = await Mutation.run(invalidate: [.timetable], successMessage: "AI 자동 배치에 맡겼어요. 다음 자동 배치 때 빈 시간으로 옮겨질 수 있어요.") {
            try await TimetableAPI.unlockTask(taskId: t.taskId)
        }
        busyTask = false
        sheetTrack("task_detail.action_result", CAL.result(ok != nil).merging(["task_action": "unlock"]) { $1 })
        if ok != nil { close() }
    }

    func unschedule(_ t: TimeBlockTask) async {
        busyTask = true
        let ok = await unscheduleRequest(t.taskId)
        busyTask = false
        sheetTrack("task_detail.action_result", CAL.result(ok).merging(["task_action": "unschedule"]) { $1 })
        if ok { close() }
    }

    /// 상세 시트 "다른 날로" — 내일 / 모레 / 날짜 지정. 같은 시각으로 놓아 보고, 자리가 없으면 그날 시간 미정으로
    ///  - pick: tomorrow · day_after · custom (task_detail.action_result 의 move_option)
    func moveToDate(_ t: TimeBlockTask, date: String, pick: String) async {
        busyTask = true
        let ok = await moveDateRequest(t.taskId, date: date, keepTime: true, hadTime: t.plannedStartAt != nil)
        busyTask = false
        sheetTrack("task_detail.action_result", CAL.result(ok).merging([
            "task_action": "move_date", "move_option": pick, "to_date": date, "kept_time": ok && lastMoveKeptTime,
        ]) { $1 })
        if ok { close() }
    }

    /// 다른 날로 옮기기 (시간 미정 → 다른 날 시간 미정 드래그 · 상세 시트 "다른 날로")
    /// 마지막으로 다른 날로 옮긴 결과가 같은 시각을 지켰는지 (task_detail.action_result 의 kept_time)
    private var lastMoveKeptTime = false

    private func moveDateRequest(_ taskId: Int, date: String, keepTime: Bool, hadTime: Bool = false) async -> Bool {
        lastMoveKeptTime = false
        let r = await Mutation.run(invalidate: [.timetable, .goals], success: { [weak self] (t: TimeBlockTask?) -> String? in
            self?.lastMoveKeptTime = keepTime && hadTime && t?.plannedStartAt != nil
            let label = KST.formatMonthDay(date)
            if keepTime, hadTime, t?.plannedStartAt == nil { return "\(label) 시간 미정으로 옮겼어요. 같은 시간에 자리가 없었어요." }
            return "\(label)로 옮겼어요."
        }) {
            try await TimetableAPI.moveTaskDate(taskId: taskId, date: date, keepTime: keepTime)
        }
        return r != nil
    }

    private func unscheduleRequest(_ taskId: Int) async -> Bool {
        let ok = await Mutation.run(invalidate: [.timetable, .goals], successMessage: "시간 미정으로 옮겼어요.") {
            try await TimetableAPI.unscheduleTask(taskId: taskId)
        }
        return ok != nil
    }

    /// 삭제는 바로 지우지 않고 상세 시트 안에 "삭제할까요?"를 띄워요
    func askDelete(_ t: TimeBlockTask) {
        confirmDeleteId = t.taskId
    }

    func cancelDelete() {
        confirmDeleteId = nil
    }

    func confirmDelete() async {
        guard let id = confirmDeleteId, !deleting else { return }
        deleting = true
        let ok = await Mutation.run(invalidate: [.timetable, .goals], successMessage: "삭제했어요.") {
            try await TimetableAPI.deleteTask(taskId: id)
        }
        deleting = false
        sheetTrack("task_detail.action_result", CAL.result(ok != nil).merging(["task_action": "delete"]) { $1 })
        if ok != nil { close() }
    }

    /// 앞뒤로 바로 이어진 Task 개수 (자기 포함)
    func chainSize(_ t: TimeBlockTask) -> Int {
        TimetableRules.chainOf(t, days: days).count
    }

    /// 이어진 Task 들을 묶음으로 선택 → 하나를 끌면 같이 움직여요
    func selectChain(_ t: TimeBlockTask) {
        group = TimetableRules.chainOf(t, days: days).map { $0.taskId }
        close()
    }

    func clearGroup() {
        group = []
        groupSelecting = false
    }

    /// 블록을 2초 꾹 누름 → 끌기는 그만두고 묶음 고르기 모드 (진동 · 이미 묶음이면 넣기만)
    func enterGroupSelect(_ t: TimeBlockTask, ownerId: String) {
        if drag?.ownerId == ownerId { drag = nil }
        dropLift(ownerId)
        guard TimetableRules.groupable(t) else { return }
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        close()
        groupSelecting = true
        toggleGroup(t, method: "long_press", onlyAdd: true)
    }

    /// 묶음 고르기 모드에서 블록을 누름 → 묶음에 넣거나 빼요
    func toggleGroupMember(_ t: TimeBlockTask) {
        guard TimetableRules.groupable(t) else { return }
        UISelectionFeedbackGenerator().selectionChanged()
        toggleGroup(t, method: "touch_tap", onlyAdd: false)
        if group.isEmpty { groupSelecting = false }
    }

    /// 묶음에 넣기 · 빼기 (웹 useTimetableInteractions toggleGroup 과 같아요)
    ///  - 묶음은 같은 날 Task 끼리만 — 다른 날 Task 를 고르면 새로 시작해요
    ///  - timetable.group_select { task_id, group_action, group_size, select_method } (+ surface)
    ///  - 홈: home.calendar_task_group_select { task_id, group_action, group_size }
    private func toggleGroup(_ t: TimeBlockTask, method: String, onlyAdd: Bool) {
        let date = TimetableRules.blockDay(t)
        let all = days.flatMap { $0.tasks }
        let base = group.filter { id in
            let x = all.first(where: { $0.taskId == id }) ?? t
            return TimetableRules.blockDay(x) == date
        }
        let removing = base.contains(t.taskId)
        if removing && onlyAdd {
            group = base
            return
        }
        let next = removing ? base.filter { $0 != t.taskId } : base + [t.taskId]
        gridTrack("timetable.group_select", [
            "task_id": t.taskId,
            "group_action": removing ? "remove" : "add",
            "group_size": next.count,
            "select_method": method,
        ])
        homeTrack(HomeEvent.calendarTaskGroupSelect, [
            "task_id": t.taskId,
            "group_action": removing ? "remove" : "add",
            "group_size": next.count,
        ])
        group = next
    }

    /// 묶음 고르기 모드에서 빈 칸을 누름 → 모드 끝 (기존 group_clear 이벤트 · close_method = empty_tap)
    func exitGroupSelectByTap() {
        gridTrack("timetable.group_clear", ["group_size": groupIds.count, "close_method": "empty_tap"])
        clearGroup()
    }

    /// 상세 시트에서 그 자리 고치기 저장 결과 (task_detail.field_result)
    func trackFieldResult(_ field: String, ok: Bool) {
        sheetTrack("task_detail.field_result", CAL.result(ok).merging(["edit_field": field]) { $1 })
    }

    /// 상세 시트에서 이름 · 세부 목표 · 마일스톤 · 마감일 고치기 (웹 useUpdateGoalTask)
    func updateTaskContent(goalId: Int, taskId: Int, _ input: GoalTaskInput) async -> Bool {
        let ok = await Mutation.run(invalidate: [.goals, .timetable], successMessage: "Task를 수정했어요.") {
            try await GoalAPI.updateGoalTask(goalId: goalId, taskId: taskId, input)
        }
        return ok != nil
    }

    /// 상세 시트에서 업무형 목표의 세부 목표(로드맵 · WorkTopic) 바꾸기 — nil = 세부 목표 없음 (웹 saveWorkTopic)
    func updateWorkTopic(goalId: Int, taskId: Int, topicId: Int?) async -> Bool {
        var patch = WorkTaskPatch()
        if let topicId { patch.topicId = topicId } else { patch.clearTopic = true }
        let ok = await Mutation.run(invalidate: WorkAPI.changed, successMessage: "Task를 수정했어요.") {
            try await WorkAPI.updateTask(goalId: goalId, taskId: taskId, patch)
        }
        return ok != nil
    }

    /// 상세 시트에서 중요도 바꾸기 — 루틴 · 미션은 그날 Task 만 (웹 useChangePriority)
    func changePriority(taskId: Int, _ priority: TaskPriority) async -> Bool {
        let ok = await Mutation.run(invalidate: [.timetable], successMessage: nil) {
            try await TimetableAPI.changePriority(taskId: taskId, priority: priority)
        }
        return ok != nil
    }

    /// 상세 시트에서 시간 바로 고치기 (루틴이면 범위와 함께)
    func saveTaskTime(_ t: TimeBlockTask, date: String, start: Int, end: Int, scope: ChangeScope) async -> Bool {
        if t.sourceType == .ROUTINE { return await saveRoutine(t, date: date, start: start, end: end, scope: scope) }
        return await reschedule(t, date: date, start: start, end: end)
    }

    // MARK: - 고정 시간 작업

    func editFixedTime(_ b: FixedBlock) {
        let patternEnd = lifePatterns?.first(where: { $0.lifePatternId == b.lifePatternId })?.endTime
        let initial = TimetableTimeValue(date: b.targetDate,
                                         start: KST.minutesFrom(b.targetDate, b.start),
                                         end: TimetableRules.fullFixedEnd(b, days: days, patternEnd: patternEnd))
        confirmDeleteId = nil
        sheet = .timeEdit(TimetableTimeEditing(target: .fixed(b), initial: initial))
    }

    func skipFixed(_ b: FixedBlock) async {
        busyFixed = true
        let ok = await Mutation.run(invalidate: [.timetable], successMessage: "이날만 비웠어요.") {
            try await TimetableAPI.patternOverride(lifePatternId: b.lifePatternId, targetDate: b.targetDate, skipped: true)
        }
        busyFixed = false
        sheetTrack("fixed_detail.result", CAL.result(ok != nil).merging(["fixed_action": "skip"]) { $1 })
        if ok != nil { close() }
    }

    func resetFixed(_ b: FixedBlock) async {
        busyFixed = true
        let ok = await Mutation.run(invalidate: [.timetable], successMessage: "기본 시간으로 되돌렸어요.") {
            try await TimetableAPI.deletePatternOverride(lifePatternId: b.lifePatternId, targetDate: b.targetDate)
        }
        busyFixed = false
        sheetTrack("fixed_detail.result", CAL.result(ok != nil).merging(["fixed_action": "reset"]) { $1 })
        if ok != nil { close() }
    }

    // MARK: - 저장 요청 (범위 · 드래그 · 시간 입력 공통)

    private func reschedule(_ t: TimeBlockTask, date: String, start: Int, end: Int) async -> Bool {
        let ok = await Mutation.run(invalidate: [.timetable, .goals],
                                    success: { (r: TaskScheduleResponse?) -> String? in TimetableAPI.scheduleResultMessage(r) }) {
            try await TimetableAPI.reschedule(taskId: t.taskId,
                                              plannedStartAt: KST.toDateTime(date, start),
                                              plannedEndAt: KST.toDateTime(date, end))
        }
        return ok != nil
    }

    private func rescheduleBatch(_ moves: [TimetableGroupMember]) async -> Bool {
        let items = moves.map {
            TimetableAPI.ScheduleItem(taskId: $0.task.taskId,
                                      plannedStartAt: KST.toDateTime($0.date, $0.start),
                                      plannedEndAt: KST.toDateTime($0.date, $0.end))
        }
        let count = items.count
        let ok = await Mutation.run(invalidate: [.timetable, .goals],
                                    success: { (r: TaskBatchScheduleResponse?) -> String? in
                                        TimetableAPI.batchResultMessage(r, count: count)
                                    }) {
            try await TimetableAPI.rescheduleBatch(items)
        }
        return ok != nil
    }

    private func saveRoutine(_ t: TimeBlockTask, date: String, start: Int, end: Int, scope: ChangeScope) async -> Bool {
        let ok = await Mutation.run(invalidate: [.timetable, .goals],
                                    success: { (r: RoutineScheduleResponse?) -> String? in
                                        TimetableAPI.routineScopeMessage(r, scope: scope)
                                    }) {
            try await TimetableAPI.routineScopedReschedule(taskId: t.taskId,
                                                           plannedStartAt: KST.toDateTime(date, start),
                                                           plannedEndAt: KST.toDateTime(date, end),
                                                           scope: scope)
        }
        return ok != nil
    }

    /// - date: start/end 의 기준 날짜 (끌어놓은 칸). 블록 소속 날짜(targetDate)와 시작 날짜가 다르면
    ///         startDayOffset 으로 알려줘요 — 예: 토요일 소속 수면(00:00)을 금요일 밤 23:30 취침으로 → -1
    private func saveFixed(_ b: FixedBlock, date: String, start: Int, end: Int, scope: ChangeScope) async -> Bool {
        let startDate = KST.addDays(date, Int(floor(Double(start) / 1440)))
        let offset = Int((Double(KST.minutesFrom(b.targetDate, "\(startDate)T00:00:00")) / 1440).rounded())
        let changesDefault = scope == .FOLLOWING || scope == .WEEKDAY
        // FOLLOWING · WEEKDAY 는 기본값이 바뀌니 생활 패턴 목록도 다시 불러와요
        let keys: [DataKey] = changesDefault ? [.timetable, .lifePatterns] : [.timetable]
        let ok = await Mutation.run(invalidate: keys, successMessage: TimetableAPI.patternScopeMessage(scope)) {
            try await TimetableAPI.patternScopedChange(lifePatternId: b.lifePatternId, targetDate: b.targetDate,
                                                       startTime: KST.minutesToTime(start), endTime: KST.minutesToTime(end),
                                                       scope: scope, startDayOffset: offset)
        }
        if changesDefault { lifePatterns = nil }
        return ok != nil
    }

    /// 실제로 한 시간 저장 — 한 구간이면 예전처럼, 여러 구간이면 segments 로
    /// - fromSheet: 실제로 한 시간 시트에서 저장 (actual_time.result) · false 면 끌어 옮긴 실제 블록 (timetable.move_result)
    @discardableResult
    func submitActual(_ t: TimeBlockTask, date: String, segments: [TimetableTimeValue],
                      successMessage: String = "완료했어요. 실제로 한 시간도 남겼어요.", fromSheet: Bool = true) async -> Bool {
        if editSaving || segments.isEmpty { return false }
        editSaving = true
        let r = await Mutation.run(invalidate: [.timetable, .goals, .home], successMessage: successMessage) {
            if segments.count > 1 {
                try await TimetableAPI.completeWithSegments(taskId: t.taskId, segments: segments.map {
                    (start: KST.toDateTime(date, $0.start), end: KST.toDateTime(date, $0.end))
                })
            } else {
                try await TimetableAPI.completeWithActual(taskId: t.taskId,
                                                          actualStartAt: KST.toDateTime(date, segments[0].start),
                                                          actualEndAt: KST.toDateTime(date, segments[0].end))
            }
        }
        editSaving = false
        if fromSheet { sheetTrack("actual_time.result", CAL.result(r != nil)) }
        if r != nil { close() }
        return r != nil
    }

    /// 시간 입력 창 저장 (실제로 한 시간 · 고정 시간)
    func submitEdit(_ editing: TimetableTimeEditing, value v: TimetableTimeValue, scope: ChangeScope) async {
        if editSaving { return }
        editSaving = true
        var ok = false
        switch editing.target {
        case .actual(let t):
            let r = await Mutation.run(invalidate: [.timetable, .goals], successMessage: "완료했어요. 실제로 한 시간도 남겼어요.") {
                try await TimetableAPI.completeWithActual(taskId: t.taskId,
                                                          actualStartAt: KST.toDateTime(v.date, v.start),
                                                          actualEndAt: KST.toDateTime(v.date, v.end))
            }
            ok = r != nil
        case .fixed(let b):
            ok = await saveFixed(b, date: v.date, start: v.start, end: v.end, scope: scope)
            sheetTrack("fixed_detail.result", CAL.result(ok).merging(["fixed_action": "time"]) { $1 })
        }
        editSaving = false
        if ok { close() }
    }

    // MARK: - 임시 표시

    @discardableResult
    private func addPending(_ p: TimetableLayout.Pending) -> UUID {
        let e = TimetablePendingEntry(value: p)
        pending.append(e)
        return e.id
    }

    private func removePending(_ id: UUID) {
        pending.removeAll { $0.id == id }
    }

    /// 저장 끝 → 다시 불러오기가 끝나면 임시 표시를 지워요
    private func settle(_ ids: [UUID]) {
        if offline {
            pending.removeAll { ids.contains($0.id) }
            return
        }
        settling.formUnion(ids)
    }

    private func finishSettling() {
        if settling.isEmpty { return }
        let done = settling
        settling = []
        pending.removeAll { done.contains($0.id) }
    }

    // MARK: - 범위 고르기 (이날만 / 이번 주 / 앞으로)

    func chooseScope(_ scope: ChangeScope) {
        guard let a = ask else { return }
        ask = nil
        Task {
            let ok: Bool
            switch a.kind {
            case .routine(let t):
                ok = await saveRoutine(t, date: a.date, start: a.start, end: a.end, scope: scope)
            case let .fixed(b, _):
                ok = await saveFixed(b, date: a.date, start: a.start, end: a.end, scope: scope)
            }
            sheetTrack("scope_chooser.result", CAL.result(ok).merging([
                "scope_target": Self.scopeTarget(a), "change_scope": scope.rawValue,
            ]) { $1 })
            // 성공 · 실패 모두 TimeTable 을 다시 불러온 뒤 임시 표시를 지워요
            settle([a.pendingId])
        }
    }

    /// 취소하고 원래대로
    func cancelAsk() {
        if let a = ask { removePending(a.pendingId) }
        ask = nil
    }

    /// 범위를 고르지 않고 화면을 떠나면 "이날만"으로 저장해요
    func saveAskOnLeave() {
        if ask != nil { autoDay("leave_page") }
    }

    // MARK: - 드래그 앤 드롭 (웹 useGridDrag + onDrop)

    /// 길게 누르기가 끝난 순간 (손가락은 아직 그대로) — 진동 + 들어 올린 표시
    func lift(ownerId: String) {
        guard drag == nil, liftedOwnerId != ownerId else { return }
        withAnimation(.spring(response: 0.25, dampingFraction: 0.7)) { liftedOwnerId = ownerId }
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
    }

    func beginDrag(source: TimetableDragSource, mode: TimetableDragMode, grab: Double?, ownerId: String) {
        // 범위를 고르지 않고 다음 드래그를 하면 앞의 변경은 "이날만"으로 저장해요
        if ask != nil { autoDay("next_drag") }
        close()
        let alreadyLifted = liftedOwnerId == ownerId
        liftedOwnerId = ownerId
        drag = TimetableDragState(source: source, mode: mode, target: nil, invalid: nil, grab: grab, ownerId: ownerId)
        if !alreadyLifted { UIImpactFeedbackGenerator(style: .medium).impactOccurred() }
    }

    private func dropLift(_ ownerId: String) {
        if liftedOwnerId == ownerId {
            withAnimation(.easeOut(duration: 0.18)) { liftedOwnerId = nil }
        }
    }

    func updateDrag(target: TimetableDropTarget?) {
        guard var st = drag else { return }
        let invalid = target.flatMap { validate(st.source, $0) }
        if st.target == target && st.invalid == invalid { return }
        let moved = st.target != nil
        st.target = target
        st.invalid = invalid
        drag = st
        if moved { UISelectionFeedbackGenerator().selectionChanged() }
    }

    /// 손을 뗌 → 놓을 수 있으면 저장
    func endDrag(ownerId: String) {
        dropLift(ownerId)
        guard let st = drag, st.ownerId == ownerId else { return }
        drag = nil
        guard let target = st.target else {
            gridTrack("timetable.drag_cancel", ["drag_source": Self.gridDragSource(st.source),
                                                "drag_mode": Self.gridDragMode(st.mode), "cancel_reason": "no_target"])
            return
        }
        if let invalid = st.invalid {
            gridTrack("timetable.drag_cancel", [
                "drag_source": Self.gridDragSource(st.source), "drag_mode": Self.gridDragMode(st.mode),
                "cancel_reason": Self.invalidReason(invalid), "validation_error": invalid,
            ])
            var p = Self.dragProps(st.source)
            p["drag_mode"] = Self.modeName(st.mode)
            p["drag_cancel_reason"] = "invalid_drop"
            p["view_date"] = rangeStart
            p["task_id"] = nil; p["source_type"] = nil; p["group_size"] = nil
            if isHome { HomeTracker.shared.system(HomeEvent.calendarDragCancel, p) }
            Toast.error(invalid)
            return
        }
        if TimetableDrag.sameAsSource(st.source, target) { return }
        if isHome {
            var p = Self.dragProps(st.source)
            p["view_date"] = rangeStart
            p["drag_mode"] = Self.modeName(st.mode)
            p["drop_target"] = { () -> String in if case .unscheduled = target { return "unscheduled" }; return "grid" }()
            var needsScope = false
            if case .fixed = st.source { needsScope = true }
            if case let .task(t, _, _, _, group) = st.source, group.isEmpty, t.sourceType == .ROUTINE { needsScope = true }
            p["needs_scope"] = needsScope
            HomeTracker.shared.track(HomeEvent.calendarDragDrop, p)
        } else {
            let dragged = st.source.taskItem
            var needsScope = false
            if case .grid = target, dragged?.proposed != true {
                if case .fixed = st.source { needsScope = true }
                if case let .task(t, _, _, _, group) = st.source, group.isEmpty, t.sourceType == .ROUTINE,
                   case let .grid(d, _, _) = target, TimetableRules.routineDay(t) == d { needsScope = true }
            }
            gridTrack("timetable.drag_drop", [
                "view_mode": gridViewMode, "drag_source": Self.gridDragSource(st.source),
                "drag_mode": Self.gridDragMode(st.mode),
                "drop_target": { () -> String in if case .unscheduled = target { return "unscheduled" }; return "grid" }(),
                "task_id": dragged?.taskId, "source_type": dragged?.sourceType.rawValue,
                "needs_scope": needsScope, "group_size": st.source.group.isEmpty ? nil : st.source.group.count,
            ])
        }
        handleDrop(st.source, target)
    }

    /// 제스처가 끝나지 못하고 취소됨 (전화가 오는 등)
    func cancelDrag(ownerId: String) {
        dropLift(ownerId)
        if let st = drag, st.ownerId == ownerId {
            gridTrack("timetable.drag_cancel", ["drag_source": Self.gridDragSource(st.source),
                                                "drag_mode": Self.gridDragMode(st.mode), "cancel_reason": "gesture_cancelled"])
        }
        if let st = drag, st.ownerId == ownerId, isHome {
            HomeTracker.shared.system(HomeEvent.calendarDragCancel, [
                "drag_source": Self.dragProps(st.source)["drag_source"] ?? nil,
                "drag_mode": Self.modeName(st.mode), "drag_cancel_reason": "gesture_cancelled", "view_date": rangeStart,
            ])
        }
        if drag?.ownerId == ownerId { drag = nil }
    }

    /// 한 시각에 Task 는 2개까지 겹쳐 둘 수 있어요 (백엔드 TaskScheduleService 와 같은 규칙)
    private func validate(_ source: TimetableDragSource, _ target: TimetableDropTarget) -> String? {
        guard case let .grid(date, start, end) = target else { return nil }
        switch source {
        case let .fixed(b, _, _, _):
            return validateSleep(b, date: date, start: start, end: end)
        case let .actual(t, srcDate, _, _, part):
            // 나눠서 한 다른 구간과 겹치면 안 돼요 (붙는 건 괜찮아요)
            let others = Self.actualRanges(t, date: srcDate).enumerated().filter { $0.offset != part }.map { $0.element }
            if others.contains(where: { start < $0.end && $0.start < end }) { return "나눠서 한 다른 구간과 겹쳐요" }
            if end - start > 1440 { return "한 구간은 하루를 넘을 수 없어요" }
            return nil
        case .task:
            let moves = TimetableDrag.groupMoves(source, date: date, start: start, end: end)
            let moving = Set(moves.map { $0.task.taskId })
            let ds = days
            for i in moves.indices {
                let m = moves[i]
                guard let day = ds.first(where: { $0.date == m.date }) else { continue }
                // 같이 옮기는 다른 Task 들의 새 자리도 자리를 차지해요
                var others: [TimetableLayout.MinuteRange] = []
                for j in moves.indices where j != i {
                    others.append(TimetableLayout.MinuteRange(start: moves[j].start, end: moves[j].end))
                }
                if TimetableLayout.crowdedTasks(day, start: m.start, end: m.end, exclude: moving, extra: others) != nil {
                    let who = moves.count > 1 ? "\"\(m.task.taskName)\" 자리에 " : ""
                    return "\(who)이미 Task \(TimetableLayout.maxTasksAtOnce)개가 겹쳐 있어요"
                }
            }
            return nil
        }
    }

    private func validateSleep(_ b: FixedBlock, date: String, start: Int, end: Int) -> String? {
        guard b.patternType == .SLEEP, let day = days.first(where: { $0.date == date }), let s = day.sleep else { return nil }
        if let w = s.wake, w.key == b.key, let bed = s.bed,
           end > KST.minutesFrom(day.date, bed.start) - TimetableRules.minAwakeMinutes {
            return "취침 시간보다 늦게 일어날 수 없어요"
        }
        if let bed = s.bed, bed.key == b.key, let w = s.wake,
           start < KST.minutesFrom(day.date, w.end) + TimetableRules.minAwakeMinutes {
            return "기상 시간보다 일찍 잘 수 없어요"
        }
        return nil
    }

    /// 실제로 한 시간 구간들 — date 0시 기준 분 (나눠서 안 했으면 실제 시작~끝 한 구간)
    static func actualRanges(_ t: TimeBlockTask, date: String) -> [TimetableLayout.MinuteRange] {
        if let segs = t.actualSegments, !segs.isEmpty {
            return segs.map { TimetableLayout.MinuteRange(start: KST.minutesFrom(date, $0.start), end: KST.minutesFrom(date, $0.end)) }
        }
        if let s = t.actualStartAt, let e = t.actualEndAt {
            return [TimetableLayout.MinuteRange(start: KST.minutesFrom(date, s), end: KST.minutesFrom(date, e))]
        }
        return []
    }

    /// 실제 시간 블록을 옮기거나 늘리고 줄였을 때 — 그 구간만 바꾸고 나머지 구간은 그대로 저장해요
    private func dropActual(_ t: TimeBlockTask, date: String, part: Int, start: Int, end: Int) {
        var ranges = Self.actualRanges(t, date: date)
        let moved = TimetableLayout.MinuteRange(start: start, end: end)
        if ranges.indices.contains(part) { ranges[part] = moved } else { ranges = [moved] }
        ranges.sort { $0.start < $1.start }
        let segments = ranges.map { TimetableTimeValue(date: date, start: $0.start, end: $0.end) }
        Task {
            let ok = await submitActual(t, date: date, segments: segments, successMessage: "실제로 한 시간을 바꿨어요.", fromSheet: false)
            gridTrack("timetable.move_result", CAL.result(ok).merging(["drag_source": "actual", "drop_target": "grid"]) { $1 })
        }
    }

    private func handleDrop(_ source: TimetableDragSource, _ target: TimetableDropTarget) {
        if case let .actual(t, _, _, _, part) = source {
            if case let .grid(d, s, e) = target { dropActual(t, date: d, part: part, start: s, end: e) }
            return
        }
        // 미리보기 제안 블록: 저장하지 않고 제안 자리만 바꿔요 (시간 미정 칸으로 끌면 이번 배치에서 빼요)
        if let t = source.taskItem, t.proposed == true {
            var next: [TimetableLayout.Proposal] = []
            for p in proposals ?? [] {
                if p.taskId != t.taskId {
                    next.append(p)
                } else if case let .grid(d, s, e) = target {
                    var q = p
                    q.start = KST.toDateTime(d, s)
                    q.end = KST.toDateTime(d, e)
                    next.append(q)
                }
            }
            proposals = next
            return
        }
        if ask != nil { autoDay("next_drag") }

        // 묶음 드래그: 끈 만큼 묶음 전체를 같이 옮기고 한 번에 저장 (루틴도 이날만 바뀌어요)
        if !source.group.isEmpty, case let .grid(d, s, e) = target {
            let moves = TimetableDrag.groupMoves(source, date: d, start: s, end: e)
            let ids = moves.map { addPending(.task(taskId: $0.task.taskId, date: $0.date, start: $0.start, end: $0.end)) }
            Task {
                let ok = await rescheduleBatch(moves)
                gridTrack("timetable.move_result", CAL.result(ok).merging([
                    "drag_source": "group", "drop_target": "grid", "group_size": moves.count,
                ]) { $1 })
                settle(ids)
            }
            return
        }

        let p: TimetableLayout.Pending
        switch (source, target) {
        case let (.task(t, _, _, _, _), .grid(d, s, e)):
            p = .task(taskId: t.taskId, date: d, start: s, end: e)
        case let (.task(t, _, _, _, _), .unscheduled):
            p = .task(taskId: t.taskId, date: nil, start: 0, end: 0)
        case let (.fixed(b, _, _, _), .grid(d, s, e)):
            p = .fixed(key: b.key, date: d, start: s, end: e)
        default:
            return
        }
        let pid = addPending(p)

        // 루틴을 같은 날 안에서 옮겼거나, 고정 시간을 옮겼으면 → 범위를 물어봐요
        if let t = source.taskItem, case let .grid(d, s, e) = target,
           t.sourceType == .ROUTINE, TimetableRules.routineDay(t) == d {
            showAsk(TimetableScopeAsk(kind: .routine(t), date: d, start: s, end: e, pendingId: pid))
            return
        }
        if case let .fixed(b, _, srcStart, _) = source, case let .grid(d, s, e) = target {
            let edge: TimetableLayout.SleepEdge? = b.patternType == .SLEEP ? (s != srcStart ? .bed : .wake) : nil
            showAsk(TimetableScopeAsk(kind: .fixed(b, edge: edge), date: d, start: s, end: e, pendingId: pid))
            return
        }

        guard let t = source.taskItem else { return }
        Task {
            let ok: Bool
            let dropTarget: String
            if case let .grid(d, s, e) = target {
                ok = await reschedule(t, date: d, start: s, end: e)
                dropTarget = "grid"
            } else if case let .unscheduled(d) = target, d != source.date {
                // 다른 날 "시간 미정" 칸 → 그날 시간 미정으로 (시간 미정 → 시간 미정도 돼요)
                ok = await moveDateRequest(t.taskId, date: d, keepTime: false)
                dropTarget = "unscheduled_other_day"
            } else {
                ok = await unscheduleRequest(t.taskId)
                dropTarget = "unscheduled"
            }
            gridTrack("timetable.move_result", CAL.result(ok).merging(["drag_source": "task", "drop_target": dropTarget]) { $1 })
            settle([pid])
        }
    }

    // MARK: - AI로 배치하기: 미리보기 → (끌어서 고치기) → 이대로 진행 / 취소

    func previewFill(_ target: TimetableFillTarget, kind: TimetableProposalKind = .fill,
                     onPreview: ((FillPreviewResponse) -> Void)? = nil,
                     onResolve: ((Bool, [TimetableLayout.Proposal]) -> Void)? = nil,
                     onError: (() -> Void)? = nil) async {
        if offline || fillPreviewing { return }
        fillPreviewing = true
        defer { fillPreviewing = false }
        do {
            let r: FillPreviewResponse
            switch target {
            case let .range(s, e): r = try await TimetableAPI.fillPreview(startDate: s, endDate: e)
            case let .tasks(ids): r = try await TimetableAPI.fillPreview(taskIds: ids)
            }
            resolveHandler = onResolve
            proposalKind = kind
            sheetTrack("fill_proposal.view", [
                "proposal_kind": kind == .added ? "new" : "fill",
                "placed_count": r.proposals.count, "unscheduled_count": r.unscheduled.count,
            ])
            onPreview?(r)
            proposals = r.proposals.map {
                TimetableLayout.Proposal(taskId: $0.taskId, reason: $0.reason, proposedStart: $0.start,
                                         proposedEnd: $0.end, start: $0.start, end: $0.end)
            }
            proposalUnscheduled = r.unscheduled.count
        } catch is CancellationError {
            // 화면을 떠났어요
        } catch {
            Toast.error(errorMessage(error))
            onError?()
        }
    }

    /// 아래 바 '취소' · '아니요' · '닫기'
    func cancelProposalsByUser() {
        sheetTrack("fill_proposal.cancel", [
            "proposal_kind": proposalKind == .added ? "new" : "fill",
            "close_method": "cancel_button", "dragged_count": editedProposals,
        ])
        cancelProposals()
    }

    func cancelProposals() {
        resolveHandler?(false, proposals ?? [])
        resolveHandler = nil
        proposals = nil
    }

    func applyProposalsNow() async {
        guard let ps = proposals, !ps.isEmpty, !fillApplying else { return }
        let kindName = proposalKind == .added ? "new" : "fill"
        sheetTrack("fill_proposal.apply_click", ["proposal_kind": kindName, "placed_count": ps.count,
                                                 "dragged_count": editedProposals])
        fillApplying = true
        let items = ps.map {
            TimetableAPI.FillApplyItem(taskId: $0.taskId, proposedStart: $0.proposedStart, proposedEnd: $0.proposedEnd,
                                       start: $0.start, end: $0.end, reason: $0.reason)
        }
        let r = await Mutation.run(invalidate: [.timetable, .goals],
                                   success: { (res: FillApplyResponse?) -> String? in
                                       guard let res else { return nil }
                                       return "\(res.placed)개 Task를 배치했어요."
                                           + (res.skipped.isEmpty ? "" : " \(res.skipped.count)개는 그사이 자리가 바뀌어 시간 미정으로 남겼어요.")
                                   }) {
            try await TimetableAPI.fillApply(items)
        }
        fillApplying = false
        sheetTrack("fill_proposal.result", CAL.result(r != nil).merging(["proposal_kind": kindName]) { $1 })
        if r != nil {
            resolveHandler?(true, ps)
            resolveHandler = nil
            proposals = nil
        }
    }

    // MARK: - 22시 이월 제안

    func acceptCarry(_ t: TimeBlockTask) async {
        if carryBusy != nil { return }
        carryBusy = .accept
        let r = await Mutation.run(invalidate: [.timetable], successMessage: "옮겼어요.") {
            try await TimetableAPI.acceptCarry(taskId: t.taskId)
        }
        sheetTrack("carry_proposal.result", CAL.result(r != nil).merging(["carry_action": "accept"]) { $1 })
        carryBusy = nil
    }

    func declineCarry(_ t: TimeBlockTask) async {
        if carryBusy != nil { return }
        carryBusy = .decline
        let r = await Mutation.run(invalidate: [.timetable], successMessage: "시간 미정으로 두었어요. 원하는 시간에 끌어다 놓거나 지울 수 있어요.") {
            try await TimetableAPI.declineCarry(taskId: t.taskId)
        }
        sheetTrack("carry_proposal.result", CAL.result(r != nil).merging(["carry_action": "decline"]) { $1 })
        carryBusy = nil
    }

    /// 이월 되돌리기 (2026-10) — 원래 날짜 · 시각으로. thenActual 이면 이어서 실제로 한 시간 입력 ("그때 했어요")
    func revertCarry(taskId: Int, from: String? = nil, thenActual: Bool = false, fromBar: Bool = false) async {
        if carryBusy != nil { return }
        carryBusy = .revert
        let r = await Mutation.run(invalidate: [.timetable], success: { (t: TimeBlockTask?) -> String? in
            if thenActual { return nil }
            return t?.plannedStartAt != nil ? "원래 시간으로 되돌렸어요." : "원래 날로 되돌렸어요. 시간이 차 있어서 시간 미정에 두었어요."
        }) {
            try await TimetableAPI.revertCarry(taskId: taskId, from: from)
        }
        if fromBar {
            sheetTrack("carry_proposal.result", CAL.result(r != nil).merging(["carry_action": "revert"]) { $1 })
        }
        carryBusy = nil
        if thenActual, let t = r ?? nil, t.plannedStartAt != nil { completeWithTime(t) }
    }

    /// 이월 흔적 메뉴에서 고른 것 (timetable.carried_trace_action)
    func trackCarriedAction(taskId: Int, action: String) {
        gridTrack("timetable.carried_trace_action", ["task_id": taskId, "carry_action": action])
    }

    func acceptAllCarry(date: String) async {
        if carryBusy != nil { return }
        carryBusy = .acceptAll
        let r = await Mutation.run(invalidate: [.timetable], success: { (n: Int) -> String? in "\(n)개를 옮겼어요." }) {
            try await TimetableAPI.acceptAllCarry(date: date)
        }
        sheetTrack("carry_proposal.result", CAL.result(r != nil).merging(["carry_action": "accept_all"]) { $1 })
        carryBusy = nil
    }

    // MARK: - 실제로 한 일

    func saveActivity(_ input: ActivityInput, activityId: Int?) async -> Bool {
        let msg = activityId != nil ? "기록을 수정했어요." : "실제로 한 일을 기록했어요."
        let r = await Mutation.run(invalidate: [.activities], successMessage: msg) {
            try await TimetableAPI.saveActivity(input, activityId: activityId)
        }
        sheetTrack("activity_form.result", CAL.result(r != nil).merging([
            "activity_action": activityId != nil ? "update" : "create",
        ]) { $1 })
        if r != nil { close() }
        return r != nil
    }

    func deleteActivity(_ activityId: Int) async -> Bool {
        let r = await Mutation.run(invalidate: [.activities], successMessage: "기록을 지웠어요.") {
            try await TimetableAPI.deleteActivity(activityId: activityId)
        }
        sheetTrack("activity_form.result", CAL.result(r != nil).merging(["activity_action": "delete"]) { $1 })
        if r != nil { close() }
        return r != nil
    }

    // MARK: - 미리보기용 샘플

    /// 네트워크 없이 그려지는 보드 (#Preview 전용)
    static func sample(start: String, end: String, withProposal: Bool = false) -> TimetableBoardModel {
        let m = TimetableBoardModel()
        m.offline = true
        m.rangeStart = start
        m.rangeEnd = end
        m.rawDays = TimetableSampleData.days(from: start, to: end)
        m.activities = TimetableSampleData.activities(on: KST.today())
        m.loadedOnce = true
        if withProposal, let t = m.rawDays.flatMap({ $0.unscheduledTasks }).first {
            let d = KST.today()
            m.proposals = [TimetableLayout.Proposal(taskId: t.taskId, reason: "집중이 잘 되는 시간이에요.",
                                                    proposedStart: "\(d)T16:00:00", proposedEnd: "\(d)T17:00:00",
                                                    start: "\(d)T16:00:00", end: "\(d)T17:00:00")]
            m.proposalKind = .added
        }
        return m
    }
}

/// 캘린더 빈 칸 (+ 메뉴를 띄울 날짜 · 그날 0시 기준 분 · 지난 시각인지)
struct TimetableEmptySlot: Equatable {
    var date: String
    var minutes: Int
    var past: Bool
}
