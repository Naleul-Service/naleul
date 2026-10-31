// Features/TaskAdd/TaskAddModels.swift
//
// 웹 원본: features/brain-dump/ui/TaskAddView.tsx (상태 · 요청 흐름), StepInput.tsx (DumpLine · newLine),
//         StepGoals.tsx (MapItem), ItemEditModal.tsx (상수)
//
// Task 추가 시트의 "화면 상태"를 한곳에 모았어요.
//  - TaskAddedInfo   : 캘린더(다른 작업자)에 "이 Task 들을 추가했어요"를 알려 주는 약속 타입
//  - BrainDumpLine   : 1단계에서 적은 한 줄 (날짜 · 시간 칩 포함)
//  - BrainDumpMapItem: 2단계에서 고치는 Task 하나 (확정 요청 Item + AI 가 붙인 연결 이유)
//  - TaskAddViewModel: 3단계 흐름(입력 → 목표 연결 → 결과) 전체 상태와 서버 요청
//
// API 요청 함수 · 응답 모델은 공통 파일 Models/BrainDumpModels.swift (BrainDumpAPI) 에 이미 있어요.

import Foundation
import Observation

// MARK: - 약속(Contract) 타입

/// 추가한 Task 알림 — 캘린더가 그 날짜로 옮기고 새 블록에 NEW 를 붙여요 (웹 TaskAddedInfo)
struct TaskAddedInfo {
    var taskIds: [Int]
    /// 가장 이른 날짜 (시간 미정이면 그날)
    var date: String?
    /// 저장만 하고 아직 시간을 정하지 않은 Task — 캘린더가 점선 미리보기로 "이대로 진행할까요?"를 물어요
    var pendingTaskIds: [Int]?
}

// MARK: - 상수 · 문구

enum BrainDumpConst {
    /// 백엔드 한 번에 최대 20개 (웹 MAX_LINES)
    static let maxLines = 20
    /// 임시 목표 최대 개수 (웹 MAX_TEMP_GOALS)
    static let maxTempGoals = 5
    /// 1단계 "걸리는 시간" 칩 목록 (분)
    static let durations = [15, 30, 45, 60, 90, 120, 150, 180, 240, 300, 360, 480]
    /// 2단계 "예상 소요 시간" 목록 (분)
    static let editMinutes = [10, 15, 20, 30, 45, 60, 90, 120, 150, 180, 240, 300, 360, 480]

    /// 2단계 목록의 중요도 표시 (웹 StepGoals PRIORITY_LABEL)
    static func priorityLabel(_ p: TaskPriority?) -> String {
        switch p ?? .C {
        case .A: return "가장 중요"
        case .B: return "중요"
        case .C: return "보통"
        case .D: return "가벼움"
        case .E: return "여유"
        }
    }

    /// 변경 시트의 중요도 설명 (웹 ItemEditModal PRIORITY_HINT)
    static func priorityHint(_ p: TaskPriority) -> String {
        switch p {
        case .A: return "가장 중요"
        case .B: return "중요"
        case .C: return "보통"
        case .D: return "가벼움"
        case .E: return "여유"
        }
    }

    /// 변경 시트의 소요 시간 표기: 45 → "45분", 90 → "1시간 30분", 120 → "2시간"
    static func minutesLabel(_ m: Int) -> String {
        if m < 60 { return "\(m)분" }
        if m % 60 != 0 { return "\(m / 60)시간 \(m % 60)분" }
        return "\(m / 60)시간"
    }

    /// "15:00:00" → "15:00"
    static func toHm(_ t: String?) -> String? {
        guard let t, !t.isEmpty else { return nil }
        return String(t.prefix(5))
    }

    /// "HH:mm" 끼리 차이(분). 자정을 넘으면 다음 날로 계산 (웹 DurationChip 의 fixed)
    static func span(_ start: String, _ end: String) -> Int? {
        let d = ((KST.timeToMinutes(end) - KST.timeToMinutes(start)) % 1440 + 1440) % 1440
        return d == 0 ? nil : d
    }
}

// MARK: - 1단계 한 줄

/// 1단계에서 적은 한 줄. 날짜·시간 칩을 고르면 AI 해석보다 우선해요. (웹 DumpLine)
struct BrainDumpLine: Hashable, Identifiable {
    var id: String { clientKey }

    var clientKey: String
    var text: String
    var date: String?
    /// 날짜를 골랐을 때만 의미: ON = 그날 · DUE = 그날까지
    var dateType: DateType = .ON
    /// "HH:mm"
    var startTime: String?
    var endTime: String?
    /// 걸리는 시간(분). 비워 두면 AI 가 추정해요
    var minutes: Int?

    /// 새 줄. today 를 주면 문장에서 시각·소요 시간·오늘/내일/모레를 바로 읽어 칩을 채워요 (웹 newLine)
    /// 예) "저녁 8시 정기 회의" → 시작 20:00 / "인강 듣기 3시간" → 소요 3시간
    static func make(_ text: String, today: String?) -> BrainDumpLine {
        let p = today.map { LineParser.parse(text, today: $0) }
        return BrainDumpLine(
            clientKey: BrainDumpAPI.newClientKey(),
            text: String(text.prefix(200)),
            date: p?.date,
            dateType: .ON,
            startTime: p?.startTime,
            endTime: p?.startTime != nil ? p?.endTime : nil,
            minutes: p?.minutes
        )
    }
}

// MARK: - 2단계 Task

/// 2단계에서 고치는 Task 하나 = 확정 요청 Item + AI 가 붙인 연결 이유 (웹 MapItem)
struct BrainDumpMapItem: Hashable, Identifiable {
    var id: String { clientKey }

    var clientKey: String
    var title: String
    var dateType: DateType
    var scheduledDate: String?
    var dueDate: String?
    /// "HH:mm"
    var startTime: String?
    var endTime: String?
    var estimatedMinutes: Int?
    var priority: TaskPriority?
    var kind: TaskKind?
    var goalCategoryId: Int?
    var generalCategoryId: Int?
    var milestoneId: Int?
    var tempGoalKey: String?
    var reason: String?
    /// 업무형 목표의 주제 (WorkTopic, 2026-10)
    var workTopicId: Int? = nil

    /// 확정 요청용 — 날짜 종류에 맞지 않는 값은 비워서 보내요 (웹 goConfirm 과 같은 규칙. reason 은 화면용이라 안 보내요)
    var confirmItem: ConfirmItem {
        ConfirmItem(
            clientKey: clientKey,
            title: title,
            dateType: dateType,
            scheduledDate: dateType == .ON ? scheduledDate : nil,
            dueDate: dateType == .DUE ? dueDate : nil,
            startTime: dateType == .ON ? startTime : nil,
            endTime: (dateType == .ON && startTime != nil) ? endTime : nil,
            estimatedMinutes: estimatedMinutes,
            priority: priority,
            kind: kind,
            goalCategoryId: goalCategoryId,
            generalCategoryId: generalCategoryId,
            milestoneId: milestoneId,
            tempGoalKey: tempGoalKey,
            workTopicId: goalCategoryId != nil ? workTopicId : nil
        )
    }
}

// MARK: - 화면 상태 (웹 TaskAddView 의 useState 들)

@MainActor
@Observable
final class TaskAddViewModel {
    /// 할 일을 적거나(ai · manual), 이미 한 일을 기록하거나(log)
    var mode: TaskAddMode
    /// 0 입력 · 1 목표 연결 · 2 결과
    var step = 0
    var lines: [BrainDumpLine] = []
    var items: [BrainDumpMapItem] = []
    var tempGoals: [TempGoalInput] = []
    var aiFailed = false
    var aiLimited = false
    /// clientKey → 겹친 일정 안내
    var conflicts: [String: String] = [:]
    var result: ConfirmResponse?

    var quota: TaskQuota?
    var quotaLoading = true
    var goals: [GoalCategory] = []
    var goalsLoading = true
    /// 목표 목록을 못 불러왔는지 (직접 추가의 "목표 없음" 카드가 같은 모양이라 앰플리튜드로 구분해요)
    var goalsFailed = false

    var parsing = false
    var confirming = false

    let today: String
    /// 미리보기(#Preview)에서는 서버를 부르지 않아요
    @ObservationIgnored var isPreview = false

    // 앰플리튜드용 (화면에는 안 써요)
    @ObservationIgnored let initialMode: TaskAddMode
    @ObservationIgnored private var viewSent = false
    /// AI 가 처음 정리한 목록 — 2단계에서 고친 개수(edited_count)를 세려고
    @ObservationIgnored private var parsedItems: [BrainDumpMapItem] = []
    /// 직접 추가 폼 · 한 일 기록 입력칸에 글자가 있는지 (방식을 바꾸거나 시트를 닫으면 확인 없이 사라지는 내용)
    @ObservationIgnored var childHasInput = false

    init(mode: TaskAddMode, today: String = KST.today()) {
        self.mode = mode
        self.initialMode = mode
        self.today = today
    }

    /// 2단계에서 처음 정리와 달라진 Task 수
    var editedCount: Int {
        items.filter { it in parsedItems.first(where: { $0.clientKey == it.clientKey }) != it }.count
    }

    /// 지금 화면에 적던 내용이 있는지 (task_add.mode_click · panel_close 의 has_input)
    var hasInput: Bool {
        if mode == .ai { return step < 2 && !lines.isEmpty }
        return childHasInput
    }

    /// 시트 열림 — 오늘 남은 개수를 받은 뒤(실패해도) 한 번
    private func sendViewOnce() {
        guard !viewSent else { return }
        viewSent = true
        TA.track("task_add.view", [
            "initial_mode": initialMode.rawValue,
            "is_premium": quota?.premium,
            "quota_remaining": (quota?.premium == false) ? quota?.remaining : nil,
        ])
    }

    /// 진행 중 · 시작 전 목표만 연결 대상으로 (웹 isOngoing)
    var ongoingGoals: [GoalCategory] { goals.filter { $0.goalCategoryStatus.isOngoing } }

    /// AI 정리 도중(2단계)에는 다른 방식으로 못 바꿔요 — 적은 내용이 날아가지 않게
    func modeLocked(_ target: TaskAddMode) -> Bool {
        mode == .ai && target != .ai && step > 0 && result == nil
    }

    // MARK: 불러오기

    /// 오늘 남은 Task 추가 개수 (웹 useTaskQuota)
    func loadQuota() async {
        if isPreview { return }
        do {
            quota = try await BrainDumpAPI.quota()
        } catch is CancellationError {
            return
        } catch {
            // 한도를 못 읽어도 입력은 막지 않아요 (서버가 확정할 때 다시 확인해요)
            if quotaLoading { TA.track("task_add.quota_load_fail", ["error_status": TA.status(error)]) }
        }
        quotaLoading = false
        sendViewOnce()
    }

    func loadGoals() async {
        if isPreview { return }
        do {
            goals = try await GoalAPI.goals(includeHabit: true)   // 기본 생활 습관으로도 분류 (2026-10)
            goalsFailed = false
        } catch is CancellationError {
            return
        } catch {
            // 목표를 못 읽으면 2단계에서 "목표를 골라 주세요"로 보여요
            goalsFailed = true
        }
        goalsLoading = false
    }

    // MARK: 1단계 → 2단계

    func goParse() async {
        let valid = lines.filter { !$0.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
        guard !valid.isEmpty, !parsing else { return }
        let requests = valid.map { l -> ParseItemRequest in
            let hasTime = l.date != nil || l.startTime != nil
            return ParseItemRequest(
                clientKey: l.clientKey,
                text: l.text.trimmingCharacters(in: .whitespacesAndNewlines),
                date: l.date,
                dateType: l.date != nil ? l.dateType : nil,
                startTime: hasTime ? l.startTime : nil,
                endTime: hasTime ? l.endTime : nil,
                estimatedMinutes: l.minutes
            )
        }
        parsing = true
        defer { parsing = false }
        let startedAt = Date()
        let elapsed = { Int(Date().timeIntervalSince(startedAt).rounded()) }
        do {
            guard let res = try await BrainDumpAPI.parse(requests) else {
                TA.track("task_add_input.parse_result", ["result": "fail", "error_status": 0, "seconds_elapsed": elapsed()])
                Toast.error("정리하지 못했어요. 다시 시도해 주세요.")
                return
            }
            let mapped = Self.toMapItems(res)
            TA.track("task_add_input.parse_result", [
                "result": "success", "item_count": mapped.items.count,
                "ai_failed": res.aiFailed, "ai_limited": res.aiLimited ?? false,
                "temp_count": mapped.tempGoals.count,
                "missing_goal_count": mapped.items.filter { $0.goalCategoryId == nil && $0.tempGoalKey == nil }.count,
                "seconds_elapsed": elapsed(),
            ])
            items = mapped.items
            parsedItems = mapped.items
            tempGoals = mapped.tempGoals
            aiFailed = res.aiFailed
            aiLimited = res.aiLimited ?? false
            conflicts = [:]
            step = 1
        } catch is CancellationError {
            return
        } catch {
            TA.track("task_add_input.parse_result", ["result": "fail", "error_status": TA.status(error), "seconds_elapsed": elapsed()])
            // Free 한도 초과(403/429)도 여기로 와요 — 백엔드 문구를 그대로 보여 줘요
            Toast.error(errorMessage(error, fallback: "정리하지 못했어요. 다시 시도해 주세요."))
        }
    }

    /// AI 결과 → 화면에서 고칠 목록 (웹 toMapItems)
    static func toMapItems(_ res: ParseResponse) -> (items: [BrainDumpMapItem], tempGoals: [TempGoalInput]) {
        let items: [BrainDumpMapItem] = res.items.map { i in
            let existing = i.goal.type == .EXISTING
            return BrainDumpMapItem(
                clientKey: i.clientKey,
                title: i.title,
                dateType: i.dateType,
                scheduledDate: i.scheduledDate,
                dueDate: i.dueDate,
                startTime: BrainDumpConst.toHm(i.startTime),
                endTime: BrainDumpConst.toHm(i.endTime),
                estimatedMinutes: i.estimatedMinutes,
                priority: i.priority ?? .C,
                kind: i.kind ?? .NORMAL,
                goalCategoryId: existing ? i.goal.goalCategoryId : nil,
                generalCategoryId: existing ? i.goal.generalCategoryId : nil,
                milestoneId: existing ? i.goal.milestoneId : nil,
                tempGoalKey: i.goal.type == .TEMP ? i.goal.tempKey : nil,
                reason: i.goal.reason
            )
        }
        let used = Set(items.compactMap(\.tempGoalKey))
        let temps = res.tempGoals
            .filter { used.contains($0.tempKey) }
            .map { TempGoalInput(tempKey: $0.tempKey, name: $0.name, emoji: $0.emoji, colorId: nil) }
        return (items, temps)
    }

    /// 2단계에서 고친 목록 반영 — 고친 Task 는 겹침 표시를 지워요
    func applyChange(items next: [BrainDumpMapItem], tempGoals nextTemps: [TempGoalInput]) {
        let old = items
        items = next
        tempGoals = nextTemps
        let changed = next.filter { n in old.first(where: { $0.clientKey == n.clientKey }) != n }
        for n in changed { conflicts.removeValue(forKey: n.clientKey) }
    }

    // MARK: 2단계 → 3단계

    /// 저장 + 배치. 성공하면 캘린더에 알릴 정보를 돌려줘요 (겹침 · 실패면 nil)
    func goConfirm() async -> TaskAddedInfo? {
        guard !confirming else { return nil }
        let used = Set(items.compactMap(\.tempGoalKey))
        TA.track("task_add_goals.confirm_click", [
            "item_count": items.count, "temp_count": used.count,
            "timed_count": items.filter { $0.dateType == .ON && $0.startTime != nil }.count,
            "edited_count": editedCount,
            "priority_a_count": items.filter { ($0.priority ?? .fallback) == .A }.count,
            "work_topic_count": items.filter { $0.goalCategoryId != nil && $0.workTopicId != nil }.count,
        ])
        let body = BrainDumpConfirmRequest(
            items: items.map(\.confirmItem),
            tempGoals: tempGoals
                .filter { used.contains($0.tempKey) }
                .map { t -> TempGoalInput in
                    var trimmed = t
                    trimmed.name = t.name.trimmingCharacters(in: .whitespacesAndNewlines)
                    return trimmed
                },
            // 시간을 정하지 않은 Task 는 저장만 → 점선 미리보기로 "이대로 진행할까요?" 물은 뒤 배치
            placeLater: true
        )
        confirming = true
        defer { confirming = false }
        do {
            guard let res = try await BrainDumpAPI.confirm(body) else {
                TA.track("task_add_goals.confirm_result", ["result": "fail", "error_status": 0, "temp_count": used.count])
                Toast.error("저장하지 못했어요. 다시 시도해 주세요.")
                return nil
            }
            if !res.created {
                TA.track("task_add_goals.confirm_result", ["result": "conflict", "conflict_count": res.conflicts.count,
                                                           "temp_count": used.count])
                var map: [String: String] = [:]
                for c in res.conflicts {
                    // 한 시각에 2개까지는 같이 둘 수 있어요 — 여기 오는 건 이미 2개가 겹친 시간에 3개째를 넣으려 한 경우
                    let who = c.conflictTitle.map { "\"\($0)\"" } ?? "다른 일정 2개"
                    map[c.clientKey] = "이 시간엔 이미 \(who)이(가) 겹쳐 있어요 (\(KST.hm(c.start))–\(KST.hm(c.end))). 시간을 조금 옮겨 주세요."
                }
                conflicts = map
                Toast.error("겹치는 일정이 있어서 저장하지 않았어요.")
                return nil
            }
            // 웹 useConfirmBrainDump onSuccess: 캘린더 · 남은 개수 (임시 목표가 생겼으면 목표 목록도)
            var keys: [DataKey] = [.timetable, .usage]
            if !res.tempGoals.isEmpty { keys.append(.goals) }
            Invalidation.shared.invalidate(keys)

            let count = items.count
            result = res
            step = 2
            let pending = res.pending ?? []
            TA.track("task_add_goals.confirm_result",
                     TA.resultCounts(res).merging(["result": "success", "conflict_count": res.conflicts.count]) { $1 })
            // 시간 미정이 있으면 시트가 닫히고 캘린더 점선으로 가서 요약은 안 보여요
            if pending.isEmpty { TA.track("task_add_result.view", TA.resultCounts(res)) }
            Toast.success(pending.isEmpty
                          ? "\(count)개 Task를 추가했어요."
                          : "\(count)개 Task를 추가했어요. 점선 자리를 확인해 주세요.")

            let placedDates = (res.placements + res.movedToOtherDays).map(\.date)
            let otherDates = (res.unscheduled + pending).compactMap(\.date)
            let allDates = (placedDates + otherDates).sorted()
            return TaskAddedInfo(
                taskIds: res.placements.map(\.taskId)
                    + res.movedToOtherDays.map(\.taskId)
                    + res.unscheduled.map(\.taskId)
                    + pending.map(\.taskId),
                date: allDates.first,
                pendingTaskIds: pending.map(\.taskId)
            )
        } catch is CancellationError {
            return nil
        } catch {
            TA.track("task_add_goals.confirm_result", ["result": "fail", "error_status": TA.status(error), "temp_count": used.count])
            Toast.error(errorMessage(error, fallback: "저장하지 못했어요. 다시 시도해 주세요."))
            return nil
        }
    }

    /// "더 추가하기" — 처음부터 (남은 개수도 다시 불러와요)
    func reset() {
        lines = []
        items = []
        tempGoals = []
        conflicts = [:]
        result = nil
        step = 0
        parsedItems = []
        if !isPreview { Invalidation.shared.invalidate(.usage) }
    }
}

// MARK: - 미리보기용 샘플

@MainActor
enum TaskAddSample {
    static func goals() -> [GoalCategory] {
        let json = """
        [
          {"goalCategoryId": 1, "goalCategoryName": "3개월 5kg 감량", "goalCategoryStatus": "IN_PROGRESS",
           "colorCode": "#3D5AFE", "emoji": "💪",
           "generalCategories": [
             {"generalCategoryId": 11, "generalCategoryName": "운동", "generalCategoryStatus": "IN_PROGRESS", "routines": []},
             {"generalCategoryId": 12, "generalCategoryName": "식단", "generalCategoryStatus": "IN_PROGRESS", "routines": []}
           ],
           "milestones": [
             {"milestoneId": 101, "title": "1개월 차 2kg", "dueDate": "2026-11-09", "status": "PENDING"}
           ]},
          {"goalCategoryId": 2, "goalCategoryName": "정보처리기사 합격", "goalCategoryStatus": "IN_PROGRESS",
           "colorCode": "#12B76A", "emoji": "📜", "generalCategories": []}
        ]
        """
        return (try? JSONDecoder().decode([GoalCategory].self, from: Data(json.utf8))) ?? []
    }

    static func items(today: String) -> [BrainDumpMapItem] {
        [
            BrainDumpMapItem(clientKey: "a", title: "헬스장 가기", dateType: .ON, scheduledDate: today,
                             startTime: "19:00", endTime: "20:00", estimatedMinutes: 60, priority: .B, kind: .NORMAL,
                             goalCategoryId: 1, generalCategoryId: 11, reason: "운동 목표와 관련 있어요"),
            BrainDumpMapItem(clientKey: "b", title: "기출 3회분 풀기", dateType: .DUE, dueDate: KST.addDays(today, 3),
                             estimatedMinutes: 120, priority: .A, kind: .NORMAL, goalCategoryId: 2),
            BrainDumpMapItem(clientKey: "c", title: "이삿짐 박스 사기", dateType: .NONE, estimatedMinutes: 30,
                             priority: .C, kind: .NORMAL, tempGoalKey: "t1", reason: "관련 목표가 없어 임시 목표로 묶었어요"),
            BrainDumpMapItem(clientKey: "d", title: "치과 예약", dateType: .ON, scheduledDate: KST.addDays(today, 1),
                             estimatedMinutes: 30, priority: .C, kind: .APPOINTMENT),
        ]
    }

    static func result(today: String) -> ConfirmResponse {
        ConfirmResponse(
            created: true,
            placements: [
                Placement(taskId: 2000, clientKey: "a", title: "헬스장 가기", date: today,
                          start: "\(today)T19:00:00", end: "\(today)T20:00:00", reason: nil, locked: true),
                Placement(taskId: 2001, clientKey: "b", title: "기출 3회분 풀기", date: today,
                          start: "\(today)T14:00:00", end: "\(today)T16:00:00", reason: "마감 전 가장 여유 있는 오후 시간이에요", locked: false),
            ],
            movedToOtherDays: [
                Placement(taskId: 2002, clientKey: "c", title: "이삿짐 박스 사기", date: KST.addDays(today, 1),
                          start: "\(KST.addDays(today, 1))T10:00:00", end: "\(KST.addDays(today, 1))T10:30:00",
                          reason: "오늘은 빈 시간이 없어 내일 오전에 놓았어요", locked: false),
            ],
            unscheduled: [BrainDumpTaskRef(taskId: 2003, clientKey: "d", title: "치과 예약", date: KST.addDays(today, 1))],
            pending: [],
            conflicts: [],
            tempGoals: [CreatedTempGoal(tempKey: "t1", goalCategoryId: 9, name: "이사 준비", emoji: "📦", colorCode: nil)]
        )
    }

    /// 미리보기용 ViewModel (서버를 부르지 않아요)
    static func viewModel(mode: TaskAddMode = .ai, step: Int = 0) -> TaskAddViewModel {
        let vm = TaskAddViewModel(mode: mode)
        vm.isPreview = true
        vm.quota = TaskQuota(premium: false, dailyLimit: 2, usedToday: 0, remaining: 2)
        vm.quotaLoading = false
        vm.goals = goals()
        vm.goalsLoading = false
        vm.lines = [BrainDumpLine.make("저녁 8시 정기 회의", today: vm.today)]
        if step >= 1 {
            vm.items = items(today: vm.today)
            vm.tempGoals = [TempGoalInput(tempKey: "t1", name: "이사 준비", emoji: "📦", colorId: nil)]
            vm.step = 1
        }
        if step >= 2 {
            vm.result = result(today: vm.today)
            vm.step = 2
        }
        return vm
    }
}
