// Features/Work/WorkModels.swift
//
// 웹 features/work/api.ts · format.ts — 업무형 목표 보드 (백엔드 V52 · V53 · /api/v1/goal-categories/{id}/work-*)
//
// 업무형은 목표 전체 마감이 없고 주제마다 마감이 있어요 (V53) → 주제를 마감 순서대로 잇는 세로 로드맵.
// Task 는 주제로 묶고, 시작 전 · 진행 중 · 완료 보드로 옮겨요. 날짜를 안 정한 Task 는 "할 일 목록"에 쌓여요.
//
// 보드를 바꾸면 [.work, .goals, .timetable, .record, .home] 을 다시 불러와요 (WorkAPI.changed).

import Foundation

enum WorkColumn: String, Codable, FallbackDecodable, Hashable, CaseIterable {
    case NOT_STARTED, IN_PROGRESS, DONE
    static let fallback: WorkColumn = .NOT_STARTED

    var label: String {
        switch self {
        case .NOT_STARTED: return "시작 전"
        case .IN_PROGRESS: return "진행 중"
        case .DONE: return "완료"
        }
    }

    var icon: String {
        switch self {
        case .NOT_STARTED: return "circle.dashed"
        case .IN_PROGRESS: return "circle.lefthalf.filled"
        case .DONE: return "checkmark.circle.fill"
        }
    }
}

struct WorkTask: Codable, Hashable, Identifiable {
    var id: Int { taskId }

    var taskId: Int
    var taskName: String
    var emoji: String?
    var topicId: Int?
    var topicName: String?
    var status: WorkColumn
    /// 하기로 한 날 (nil = 할 일 목록)
    var date: String?
    /// "HH:mm" — 시간이 정해졌을 때만
    var startTime: String?
    var endTime: String?
    var dueDate: String?
    /// 마감까지 남은 날 (오늘 0, 지났으면 음수)
    var dueInDays: Int?
    var overdue: Bool
    var memo: String?
    /// 반복 루틴의 그날 Task — 보드 칸에는 안 나와요
    var routine: Bool
    var completedAt: String?

    var isDone: Bool { status == .DONE }

    enum CodingKeys: String, CodingKey {
        case taskId, taskName, emoji, topicId, topicName, status, date, startTime, endTime
        case dueDate, dueInDays, overdue, memo, routine, completedAt
    }
}

extension WorkTask {
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        taskId = try c.decode(Int.self, forKey: .taskId)
        taskName = try c.decodeIfPresent(String.self, forKey: .taskName) ?? ""
        emoji = try c.decodeIfPresent(String.self, forKey: .emoji)
        topicId = try c.decodeIfPresent(Int.self, forKey: .topicId)
        topicName = try c.decodeIfPresent(String.self, forKey: .topicName)
        status = try c.decodeIfPresent(WorkColumn.self, forKey: .status) ?? .NOT_STARTED
        date = try c.decodeIfPresent(String.self, forKey: .date)
        startTime = try c.decodeIfPresent(String.self, forKey: .startTime)
        endTime = try c.decodeIfPresent(String.self, forKey: .endTime)
        dueDate = try c.decodeIfPresent(String.self, forKey: .dueDate)
        dueInDays = try c.decodeIfPresent(Int.self, forKey: .dueInDays)
        overdue = try c.decodeIfPresent(Bool.self, forKey: .overdue) ?? false
        memo = try c.decodeIfPresent(String.self, forKey: .memo)
        routine = try c.decodeIfPresent(Bool.self, forKey: .routine) ?? false
        completedAt = try c.decodeIfPresent(String.self, forKey: .completedAt)
    }
}

/// DONE 끝남 · OVERDUE 마감 지남 · NEXT 다음 마감 · UPCOMING 그 뒤 · NO_DUE 마감 없음
enum WorkTopicStatus: String, Codable, FallbackDecodable, Hashable {
    case DONE, OVERDUE, NEXT, UPCOMING, NO_DUE
    static let fallback: WorkTopicStatus = .NO_DUE
}

/// 주제 — 업무형은 목표 전체 마감 대신 주제마다 마감이 있어요 (V53)
struct WorkTopic: Codable, Hashable, Identifiable {
    var id: String { topicId.map(String.init) ?? "none" }

    /// nil = "주제 없음" (보드 필터에만)
    var topicId: Int?
    var name: String
    var emoji: String?
    var sortOrder: Int
    var dueDate: String?
    /// 마감까지 남은 날 (오늘 0, 지났으면 음수)
    var dDay: Int?
    var status: WorkTopicStatus
    /// 사용자가 "끝냈어요"로 표시했는지
    var completed: Bool
    var total: Int
    var done: Int
    var remaining: Int
    var progressPercent: Int?
    /// 마감까지 하루에 끝내야 하는 Task 수
    var remainingPerDay: Double?

    var isDone: Bool { status == .DONE }
    var title: String { (emoji.map { $0.isEmpty ? "" : "\($0) " } ?? "") + name }
}

struct WorkSummary: Codable, Hashable {
    var startDate: String?
    var total: Int
    var notStarted: Int
    var inProgress: Int
    var done: Int
    var remaining: Int
    var overdue: Int
    var dueThisWeek: Int
    var backlog: Int
    var progressPercent: Int?
    /// 끝난 주제 수 / 전체 주제 수
    var topicsDone: Int
    var topicsTotal: Int
    /// 아직 안 끝난 주제 중 마감이 가장 가까운 것 (지난 것 포함). 없으면 nil
    var nextTopic: WorkTopic?
}

struct WorkRetro: Codable, Hashable, Identifiable {
    var id: Int { retrospectiveId }
    var retrospectiveId: Int
    var reviewDate: String
    var reviewType: String?
    var content: String
}

struct WorkBoardColumns: Codable, Hashable {
    var notStarted: [WorkTask]
    var inProgress: [WorkTask]
    var done: [WorkTask]
    var doneTotal: Int

    func tasks(_ c: WorkColumn) -> [WorkTask] {
        switch c {
        case .NOT_STARTED: return notStarted
        case .IN_PROGRESS: return inProgress
        case .DONE: return done
        }
    }
}

struct WorkArchiveDay: Codable, Hashable, Identifiable {
    var id: String { date }
    var date: String
    var count: Int
    var tasks: [WorkTask]
}

/// GET /goal-categories/{id}/work-board
struct WorkBoard: Codable, Hashable {
    var goalCategoryId: Int
    var goalName: String
    var emoji: String?
    var description: String?
    var today: String
    var summary: WorkSummary
    /// 주제를 마감 순서대로 (마감 없는 주제는 맨 뒤) — 세로 로드맵
    var timeline: [WorkTopic]
    var todayTasks: [WorkTask]
    var doneToday: [WorkTask]
    var upcoming: [WorkTask]
    /// 보드 필터용 주제 (맨 끝에 "주제 없음")
    var topics: [WorkTopic]
    var board: WorkBoardColumns
    var backlog: [WorkTask]
    var archive: [WorkArchiveDay]
    var retrospectives: [WorkRetro]

    /// 주제가 있는 것만 (주제 고르기용)
    var realTopics: [WorkTopic] { topics.filter { $0.topicId != nil } }

    /// 주제별 남은 할 일 (진행 중 먼저) · 끝낸 일 — 로드맵 점 사이에 보여줘요
    func openTasks(topicId: Int?) -> [WorkTask] {
        (board.inProgress + board.notStarted).filter { $0.topicId == topicId }
    }

    func doneTasks(topicId: Int?) -> [WorkTask] {
        board.done.filter { $0.topicId == topicId }
    }
}

/// 공유 범위 — 오늘 · 남은 일 · 전체
enum WorkShareScope: String, CaseIterable, Hashable {
    case TODAY, REMAINING, ALL

    var label: String {
        switch self {
        case .TODAY: return "오늘"
        case .REMAINING: return "남은 일"
        case .ALL: return "전체"
        }
    }
}

/// Task 수정 — 보낼 것만 (nil 은 안 보내요). clear* 는 지우기
struct WorkTaskPatch: Hashable {
    var taskName: String?
    var topicId: Int?
    var clearTopic: Bool?
    var date: String?
    var clearDate: Bool?
    var dueDate: String?
    var clearDueDate: Bool?
    var memo: String?
    var status: WorkColumn?

    var json: JSONValue {
        .compact([
            "taskName": taskName.map { JSONValue.string($0) },
            "topicId": topicId.map { JSONValue.int($0) },
            "clearTopic": clearTopic.map { JSONValue.bool($0) },
            "date": date.map { JSONValue.string($0) },
            "clearDate": clearDate.map { JSONValue.bool($0) },
            "dueDate": dueDate.map { JSONValue.string($0) },
            "clearDueDate": clearDueDate.map { JSONValue.bool($0) },
            "memo": memo.map { JSONValue.string($0) },
            "status": status.map { JSONValue.string($0.rawValue) },
        ])
    }
}

/// 주제 수정 — 보낼 것만
struct WorkTopicPatch: Hashable {
    var name: String?
    var emoji: String?
    var dueDate: String?
    var clearDueDate: Bool?
    /// true 끝냈어요 · false 다시 열기
    var completed: Bool?

    var json: JSONValue {
        .compact([
            "name": name.map { JSONValue.string($0) },
            "emoji": emoji.map { JSONValue.string($0) },
            "dueDate": dueDate.map { JSONValue.string($0) },
            "clearDueDate": clearDueDate.map { JSONValue.bool($0) },
            "completed": completed.map { JSONValue.bool($0) },
        ])
    }
}

enum WorkAPI {
    /// 보드를 바꾸면 같이 다시 불러올 것들
    static let changed: [DataKey] = [.work, .goals, .timetable, .record, .home]

    static func board(goalId: Int, archiveDays: Int = 30) async throws -> WorkBoard? {
        try await API.get("/v1/goal-categories/\(goalId)/work-board?archiveDays=\(archiveDays)")
    }

    /// 공유 텍스트 (카톡 · 슬랙에 붙여 넣기)
    static func shareText(goalId: Int, scope: WorkShareScope) async throws -> String {
        struct R: Decodable { var text: String }
        let r: R? = try await API.get("/v1/goal-categories/\(goalId)/work-board/share-text?scope=\(scope.rawValue)")
        return r?.text ?? ""
    }

    // MARK: Task

    /// 한 개 추가
    static func createTask(goalId: Int, taskName: String, topicId: Int?, date: String?, dueDate: String?) async throws -> WorkTask? {
        try await API.post("/v1/goal-categories/\(goalId)/work-tasks", body: [
            "taskName": .string(taskName),
            "topicId": .of(topicId),
            "date": .of(date),
            "dueDate": .of(dueDate),
        ])
    }

    /// 여러 줄을 한 번에 (한 줄 = Task 하나)
    static func createTasks(goalId: Int, text: String, topicId: Int?, date: String?, dueDate: String?) async throws -> [WorkTask] {
        let r: [WorkTask]? = try await API.post("/v1/goal-categories/\(goalId)/work-tasks/bulk", body: [
            "text": .string(text),
            "topicId": .of(topicId),
            "date": .of(date),
            "dueDate": .of(dueDate),
        ])
        return r ?? []
    }

    static func updateTask(goalId: Int, taskId: Int, _ patch: WorkTaskPatch) async throws -> WorkTask? {
        try await API.patch("/v1/goal-categories/\(goalId)/work-tasks/\(taskId)", body: patch.json)
    }

    /// 보드 칸 옮기기 (시작 전 · 진행 중 · 완료)
    static func move(goalId: Int, taskId: Int, to: WorkColumn) async throws {
        try await API.send("PATCH", "/v1/goal-categories/\(goalId)/work-tasks/\(taskId)/status",
                           body: ["status": .string(to.rawValue)])
    }

    static func deleteTask(taskId: Int) async throws {
        try await API.send("DELETE", "/v1/tasks/\(taskId)")
    }

    // MARK: 주제

    static func createTopic(goalId: Int, name: String, dueDate: String?) async throws {
        try await API.send("POST", "/v1/goal-categories/\(goalId)/work-topics",
                           body: ["name": .string(name), "dueDate": .of(dueDate)])
    }

    static func updateTopic(goalId: Int, topicId: Int, _ patch: WorkTopicPatch) async throws {
        try await API.send("PATCH", "/v1/goal-categories/\(goalId)/work-topics/\(topicId)", body: patch.json)
    }

    /// 들어 있던 Task 는 "주제 없음"으로 옮겨져요
    static func deleteTopic(goalId: Int, topicId: Int) async throws {
        try await API.send("DELETE", "/v1/goal-categories/\(goalId)/work-topics/\(topicId)")
    }

    // MARK: 회고 (기존 /v1/retrospectives)

    static func createRetro(goalId: Int, content: String, date: String? = nil) async throws {
        try await API.send("POST", "/v1/retrospectives", body: .compact([
            "reviewType": .string("DAILY"),
            "reviewDate": date.map { JSONValue.string($0) },
            "content": .string(content),
            "goalCategoryId": .int(goalId),
        ]))
    }

    static func updateRetro(retrospectiveId: Int, content: String) async throws {
        try await API.send("PATCH", "/v1/retrospectives/\(retrospectiveId)", body: ["content": .string(content)])
    }

    static func deleteRetro(retrospectiveId: Int) async throws {
        try await API.send("DELETE", "/v1/retrospectives/\(retrospectiveId)")
    }
}

/// 웹 work/format.ts
enum WorkFormat {
    /// "오늘" · "내일" · "어제" · "10.14 (화)" (해가 다르면 "2027.01.03 (일)")
    static func dayLabel(_ ymd: String, today: String) -> String {
        if ymd == today { return "오늘" }
        if ymd == KST.addDays(today, 1) { return "내일" }
        if ymd == KST.addDays(today, -1) { return "어제" }
        let p = KST.parts(ymd)
        let md = String(format: "%02d.%02d", p.m, p.d)
        let date = p.y == KST.yearOf(today) ? md : "\(p.y).\(md)"
        return "\(date) (\(KST.weekdayLabel[KST.weekdayIndex(ymd)]))"
    }

    /// Task 마감 — "D-3" · "오늘 마감" · "2일 지남" (완료면 nil)
    static func dueLabel(_ t: WorkTask) -> String? {
        guard let d = t.dueInDays, t.status != .DONE else { return nil }
        if d == 0 { return "오늘 마감" }
        return d > 0 ? "D-\(d)" : "\(-d)일 지남"
    }

    /// 주제 마감 — "D-3" · "오늘 마감" · "2일 지남"
    static func topicDue(_ dDay: Int?) -> String? {
        guard let d = dDay else { return nil }
        if d == 0 { return "오늘 마감" }
        return d > 0 ? "D-\(d)" : "\(-d)일 지남"
    }

    /// "D-12" · "D-DAY" · "D+3"
    static func dDay(_ d: Int?) -> String? {
        guard let d else { return nil }
        if d == 0 { return "D-DAY" }
        return d > 0 ? "D-\(d)" : "D+\(-d)"
    }

    /// "10/16(금)"
    static func shortDate(_ ymd: String) -> String {
        "\(KST.monthOf(ymd))/\(KST.dayOfMonth(ymd))(\(KST.weekdayLabel[KST.weekdayIndex(ymd)]))"
    }

    /// "2026.11.14 (금)"
    static func fullDate(_ ymd: String) -> String {
        "\(ymd.replacingOccurrences(of: "-", with: ".")) (\(KST.weekdayLabel[KST.weekdayIndex(ymd)]))"
    }

    /// 마감까지 속도 — 하루 1개 이상이면 "하루 2개씩", 적으면 "3일에 1개씩"
    static func pace(_ perDay: Double) -> String {
        // 0 이하 · 무한대면 Int 변환에서 죽지 않게
        guard perDay.isFinite, perDay > 0 else { return "" }
        if perDay >= 1 {
            let v = (perDay * 10).rounded() / 10
            let s = v == v.rounded() ? String(Int(v)) : String(v)
            return "하루 \(s)개씩"
        }
        let days = min((1 / perDay).rounded(), 9999)
        return "\(max(Int(days), 2))일에 1개씩"
    }
}
