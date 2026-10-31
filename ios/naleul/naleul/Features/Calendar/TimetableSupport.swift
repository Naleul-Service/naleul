// Features/Calendar/TimetableSupport.swift
//
// 캘린더(TimeTable) 화면들이 같이 쓰는 작은 규칙 · 타입 모음 (화면 없음).
// 웹 원본:
//   features/timetable/ui/TimeGrid.tsx        — canDragTask, canDragFixed, Selection
//   features/timetable/ui/useGridDrag.ts      — DragSource, DropTarget, groupMoves, canUnschedule
//   features/timetable/ui/useTimetableInteractions.tsx — chainOf, fullFixedEnd, Opened/Editing/ScopeAsk
//   features/timetable/ui/ScopeChooser.tsx    — scopeOptions
//   features/timetable/ui/TaskDetail.tsx      — taskPermissions, currentInput
//   features/timetable/ui/CarryProposalBar.tsx — relDay, eulReul
//   features/timetable/ui/AutoPlaceBar.tsx    — unplacedOf
//   features/timetable/ui/ActivityForm.tsx    — ActivityDraft, draftOf
//
// 왜 따로 두나요? → 격자 · 상세 시트 · 아래 확인 바가 모두 같은 규칙을 써야 해서 한 곳에 모았어요.

import SwiftUI

// MARK: - 격자 크기

enum TimetableGeo {
    /// 1시간 높이 (웹은 60px — 아이폰은 조금 줄였어요)
    static let hourHeight: CGFloat = 56
    /// 1분당 pt
    static let ppm: CGFloat = 56.0 / 60.0
    /// 왼쪽 시간 눈금 폭
    static let labelWidth: CGFloat = 36
    /// 드래그 좌표를 재는 기준 공간 (머리글 · 시간 미정 줄 · 격자를 모두 감싸요)
    static let space = NamedCoordinateSpace.named("timetable-board")
    /// 주간 날짜 머리글 높이
    static let weekHeaderHeight: CGFloat = 58
    /// 일간 날짜 머리글 높이 ("계획 | 실제" 칸 이름 포함)
    static let dayHeaderHeight: CGFloat = 70
    /// 시간 미정 칸에 처음 보여줄 개수 (웹 UNSCHEDULED_VISIBLE)
    static let unscheduledVisible = 3
    static let chipHeight: CGFloat = 22
    /// 시간 미정 줄 최대 높이 (넘으면 칸 안에서 스크롤 — 웹 max-h-32)
    static let maxUnscheduledHeight: CGFloat = 132
    /// 길게 눌러야 끌기 시작 (웹 터치 TOUCH_HOLD_MS = 500) — 짧으면 "대고 잠깐 멈췄다 내리는" 스크롤이 끌기로 잡혀요 (2026-10: 0.35 → 0.5)
    static let holdSeconds: Double = 0.5
    /// 꾹 눌러 묶음 고르기까지 (누르기 포함) — 3초 → 2초 (DESIGN_SPEC 13.1)
    static let groupHoldSeconds: Double = 2.0
}

// MARK: - 규칙

struct TimetableTaskPermissions: Hashable {
    /// 시간 변경 (완료 · 미션은 백엔드 409)
    var editTime: Bool
    /// 이름 · 세부 목표 · 마일스톤 · 마감일: 목표에 연결된 일회성 Task 만
    var editContent: Bool
    var unlock: Bool
    /// 시간 미정으로 (루틴 · 미션은 400)
    var unschedule: Bool
    var delete: Bool
    /// 중요도: 루틴 · 미션도 그날 Task 만 바꿀 수 있어요 (완료한 Task 는 보기만)
    var editPriority: Bool = false
}

struct TimetableUnplaced: Hashable {
    var date: String
    var count: Int
}

enum TimetableRules {
    /// 기상은 취침보다, 취침은 기상보다 30분 이상 떨어져야 해요 (같은 날 칸 기준)
    static let minAwakeMinutes = 30

    /// 드래그로 옮길 수 있는 Task (완료 · 미션은 백엔드에서 409)
    static func canDragTask(_ t: TimeBlockTask) -> Bool {
        t.taskStatus != .COMPLETED && t.sourceType != .MISSION
    }

    /// 자동 배치가 점심 앞뒤로 나눠 넣은 Task 의 전체 길이(분) — 나눠 넣지 않았으면 nil (웹 splitLength)
    static func splitLength(_ t: TimeBlockTask) -> Int? {
        guard t.taskStatus != .COMPLETED, (t.plannedSegments?.count ?? 0) > 1 else { return nil }
        return t.plannedDurationMinutes
    }

    /// 완료한 Task 의 "실제로 한 시간" 블록을 끌어서 옮기거나 늘리고 줄일 수 있는지 (웹 canDragActual)
    static func canDragActual(_ t: TimeBlockTask) -> Bool {
        t.taskStatus == .COMPLETED && t.sourceType != .MISSION && t.plannedStartAt != nil
    }

    /// 드래그로 옮길 수 있는 고정 블록: 그날 안에서 시작하고 끝나는 것만
    /// (자정을 넘기는 수면은 날짜별 조각만 보여서 끌어도 실제 길이를 알 수 없어요 → 상세의 시간 변경으로)
    static func canDragFixed(_ b: FixedBlock, date: String) -> Bool {
        !b.clippedFromPreviousDay && b.targetDate == date && KST.minutesFrom(date, b.end) < 1440
    }

    /// 묶음에 넣을 수 있는 Task: 시간이 정해져 있고 드래그로 옮길 수 있는 것
    static func groupable(_ t: TimeBlockTask) -> Bool {
        t.plannedStartAt != nil && canDragTask(t) && t.proposed != true
    }

    /// 블록 날짜 (계획 시작일)
    static func blockDay(_ t: TimeBlockTask) -> String? {
        t.plannedStartAt.map { String($0.prefix(10)) }
    }

    /// 루틴 Task 가 속한 날짜 (루틴 인스턴스 날짜)
    static func routineDay(_ t: TimeBlockTask) -> String? {
        t.date ?? blockDay(t)
    }

    /// t 와 앞뒤로 바로 이어진 Task 들 (사이 간격 10분 이하). 쪼개 둔 연속 작업을 한 번에 고르려고 써요
    static func chainOf(_ t: TimeBlockTask, days: [TimetableDay]) -> [TimeBlockTask] {
        guard let date = blockDay(t), groupable(t), let day = days.first(where: { $0.date == date }) else { return [t] }
        struct Item {
            var x: TimeBlockTask
            var s: Int
            var e: Int
        }
        var list: [Item] = []
        for x in day.tasks where groupable(x) && blockDay(x) == date {
            let s = KST.minutesFrom(date, x.plannedStartAt ?? "\(date)T00:00:00")
            let e = x.plannedEndAt.map { KST.minutesFrom(date, $0) } ?? s + (x.plannedDurationMinutes ?? 30)
            list.append(Item(x: x, s: s, e: e))
        }
        list.sort { $0.s < $1.s }
        guard let i = list.firstIndex(where: { $0.x.taskId == t.taskId }) else { return [t] }
        var lo = i
        var hi = i
        var end = list[i].e
        while hi + 1 < list.count && list[hi + 1].s - end <= KST.snapMinutes {
            hi += 1
            end = max(end, list[hi].e)
        }
        var start = list[i].s
        while lo - 1 >= 0 && start - list[lo - 1].e <= KST.snapMinutes {
            lo -= 1
            start = min(start, list[lo].s)
        }
        return list[lo...hi].map { $0.x }
    }

    /// 고정 블록이 자정을 넘기면(화면엔 24:00까지만) 다음 날 조각이나 패턴에서 실제 종료 시각을 찾아요
    static func fullFixedEnd(_ block: FixedBlock, days: [TimetableDay], patternEnd: String?) -> Int {
        let end = KST.minutesFrom(block.targetDate, block.end)
        if end < 1440 { return end }
        let pieces = days.flatMap { $0.fixedBlocks }
        if let next = pieces.first(where: {
            $0.clippedFromPreviousDay && $0.lifePatternId == block.lifePatternId && $0.targetDate == block.targetDate
        }) {
            return KST.minutesFrom(block.targetDate, next.end)
        }
        if let pe = patternEnd, !block.overridden { return 1440 + KST.timeToMinutes(pe) }
        return end
    }

    /// 어떤 작업을 할 수 있는지 (백엔드 규칙과 같게)
    static func permissions(_ t: TimeBlockTask) -> TimetableTaskPermissions {
        let done = t.taskStatus == .COMPLETED
        let mission = t.sourceType == .MISSION
        return TimetableTaskPermissions(
            editTime: !done && !mission,
            editContent: t.sourceType == .MANUAL && t.goalCategoryId != nil && t.generalCategoryId != nil,
            unlock: t.locked && !done && !mission,
            unschedule: t.sourceType == .MANUAL && t.plannedStartAt != nil && !done,
            delete: !mission,
            editPriority: !done
        )
    }

    /// 목표 상세 Task 수정 API 에 보낼 "지금 값 그대로"의 폼 (바꿀 항목만 덮어써서 보내요)
    static func currentInput(_ t: TimeBlockTask) -> GoalTaskInput {
        let day = blockDay(t) ?? t.date ?? t.scheduledDate ?? KST.today()
        var startTime: String? = nil
        var endTime: String? = nil
        if let ps = t.plannedStartAt {
            let s = KST.minutesFrom(day, ps)
            let e = t.plannedEndAt.map { KST.minutesFrom(day, $0) } ?? s + (t.plannedDurationMinutes ?? 30)
            startTime = KST.minutesToTime(s)
            endTime = KST.minutesToTime(e)
        }
        var duration: Int? = nil
        if t.plannedStartAt == nil, let d = t.plannedDurationMinutes, d >= 5, d <= 720 { duration = d }
        return GoalTaskInput(
            taskName: t.taskName,
            emoji: t.emoji,
            generalCategoryId: t.generalCategoryId,
            milestoneId: t.milestoneId,
            date: day,
            startTime: startTime,
            endTime: endTime,
            durationMinutes: duration,
            dueDate: t.dueDate
        )
    }

    /// 기준 날짜(base)에서 본 상대 날짜: 오늘 · 어제 · 그제 · 내일 · 10월 8일 (수)
    static func relDay(_ date: String, base: String) -> String {
        if date == base { return "오늘" }
        if date == KST.addDays(base, -1) { return "어제" }
        if date == KST.addDays(base, -2) { return "그제" }
        if date == KST.addDays(base, 1) { return "내일" }
        return KST.formatMonthDay(date)
    }

    /// "을/를" — 받침에 맞게 (웹과 같이 마지막 글자가 한글이 아니면 "을(를)")
    static func eulReul(_ word: String) -> String {
        guard let last = word.unicodeScalars.last else { return "\(word)을(를)" }
        let c = last.value
        if c < 0xAC00 || c > 0xD7A3 { return "\(word)을(를)" }
        return word + ((c - 0xAC00) % 28 != 0 ? "을" : "를")
    }

    /// 배치 대상: 오늘 이후 날짜의 "시간 미정" 미완료 Task (미션은 방장이 시간을 정하므로 제외)
    static func unplacedOf(_ days: [TimetableDay], today: String) -> [TimetableUnplaced] {
        days.filter { $0.date >= today }
            .map { d in
                TimetableUnplaced(date: d.date,
                                  count: d.unscheduledTasks.filter { $0.taskStatus == .TODO && $0.sourceType != .MISSION }.count)
            }
            .filter { $0.count > 0 }
    }

    /// 블록 이름 앞에 이모지
    static func title(_ emoji: String?, _ name: String) -> String {
        if let emoji, !emoji.isEmpty { return "\(emoji) \(name)" }
        return name
    }
}

// MARK: - 적용 범위 (이날만 / 이번 주 / 앞으로)

enum TimetableScopeKind { case routine, fixed }

struct TimetableScopeOption: Hashable, Identifiable {
    var id: String { scope.rawValue }
    var scope: ChangeScope
    var label: String
    var hint: String
    /// 고를 수 없는 이유 (있으면 비활성)
    var disabledReason: String? = nil
}

enum TimetableScope {
    /// 고를 수 있는 범위 (웹 scopeOptions)
    /// - sameDay: 원래 날짜 그대로인지 (루틴을 다른 날로 옮기면 이날만)
    /// - crossesMidnight: 새 시간이 자정을 넘기는지 (지금은 막지 않아요 — 루틴도 23:00~00:00처럼 자정을 넘길 수 있어요. 12시간 넘게는 서버가 막아요)
    /// - weekdayLabel: 고정 시간 "앞으로 ○마다"에 넣을 요일 이름 (있으면 요일별 옵션이 생겨요)
    static func options(kind: TimetableScopeKind, isToday: Bool, sameDay: Bool = true,
                        crossesMidnight: Bool = false, weekdayLabel: String? = nil) -> [TimetableScopeOption] {
        let first = TimetableScopeOption(scope: .DAY, label: isToday ? "오늘만" : "이날만", hint: "이 하루만 바뀌어요")
        if kind == .fixed, let weekdayLabel {
            return [
                first,
                TimetableScopeOption(scope: .WEEK, label: "이번 주", hint: "이날부터 이번 주 일요일까지"),
                TimetableScopeOption(scope: .WEEKDAY, label: "앞으로 \(weekdayLabel)마다",
                                     hint: "이 요일만 기본 시간이 바뀌어요 (지난 기록은 그대로)"),
                TimetableScopeOption(scope: .FOLLOWING, label: "앞으로 매일",
                                     hint: "모든 요일이 이 시간으로 (요일별로 다르게 정한 시간도 맞춰요)"),
            ]
        }
        let otherDay: String? = (kind == .routine && !sameDay) ? "다른 날로 옮길 때는 이날만 바꿀 수 있어요" : nil
        return [
            first,
            TimetableScopeOption(scope: .WEEK, label: "이번 주", hint: "이날부터 이번 주 일요일까지", disabledReason: otherDay),
            TimetableScopeOption(scope: .FOLLOWING, label: "앞으로 계속",
                                 hint: kind == .routine ? "남은 루틴 전부 + 루틴 기본 시간" : "기본 시간이 바뀌어요 (지난 기록은 그대로)",
                                 disabledReason: otherDay),
        ]
    }

    /// 범위 아이콘 (웹 CalendarDays · CalendarRange · Repeat · Infinity)
    static func icon(_ s: ChangeScope) -> String {
        switch s {
        case .DAY: return "calendar"
        case .WEEK: return "calendar.badge.clock"
        case .WEEKDAY: return "repeat"
        case .FOLLOWING: return "infinity"
        }
    }

    /// 고를 수 없게 된 범위를 골라 둔 상태면 이날만으로
    static func effective(_ chosen: ChangeScope, in options: [TimetableScopeOption]?) -> ChangeScope {
        guard let options else { return .DAY }
        return options.contains(where: { $0.scope == chosen && $0.disabledReason == nil }) ? chosen : .DAY
    }
}

// MARK: - 드래그 (웹 useGridDrag)

/// 묶음으로 같이 옮길 Task (끄는 Task 와 같은 날, 시간이 있는 것)
struct TimetableGroupMember: Hashable {
    var task: TimeBlockTask
    var date: String
    var start: Int
    var end: Int
}

/// 끄는 대상. start/end 가 nil 이면 "시간 미정" 칸에서 끌어온 Task
/// actual = 완료한 Task 의 실제로 한 시간 (part = 나눠서 했을 때 몇 번째 구간인지)
enum TimetableDragSource {
    case task(TimeBlockTask, date: String, start: Int?, end: Int?, group: [TimetableGroupMember])
    case fixed(FixedBlock, date: String, start: Int, end: Int)
    case actual(TimeBlockTask, date: String, start: Int, end: Int, part: Int)

    var date: String {
        switch self {
        case let .task(_, date, _, _, _): return date
        case let .fixed(_, date, _, _): return date
        case let .actual(_, date, _, _, _): return date
        }
    }

    var start: Int? {
        switch self {
        case let .task(_, _, start, _, _): return start
        case let .fixed(_, _, start, _): return start
        case let .actual(_, _, start, _, _): return start
        }
    }

    var end: Int? {
        switch self {
        case let .task(_, _, _, end, _): return end
        case let .fixed(_, _, _, end): return end
        case let .actual(_, _, _, end, _): return end
        }
    }

    var isFixed: Bool {
        if case .fixed = self { return true }
        return false
    }

    var isActual: Bool {
        if case .actual = self { return true }
        return false
    }

    /// 계획 Task (실제 시간 블록은 nil — 계획 블록은 그대로 두고 실제 시간만 바뀌어요)
    var taskItem: TimeBlockTask? {
        if case let .task(t, _, _, _, _) = self { return t }
        return nil
    }

    var group: [TimetableGroupMember] {
        if case let .task(_, _, _, _, g) = self { return g }
        return []
    }

    /// 끄는 동안 유지할 길이 (분)
    var duration: Int {
        if let s = start, let e = end { return e - s }
        if let t = taskItem { return t.plannedDurationMinutes ?? 60 }
        return 60
    }

    /// 시간 미정 칸에 놓을 수 있는 Task (백엔드: 루틴 · 미션은 400)
    ///  - 시간이 있는 블록 → 시간 미정으로 (다른 날 칸이면 그날 시간 미정으로)
    ///  - 시간 미정 칩 → 다른 날 시간 미정으로 (2026-10, PATCH /tasks/{id}/date)
    var canUnschedule: Bool {
        guard case let .task(t, _, _, _, g) = self else { return false }
        return g.isEmpty && t.sourceType == .MANUAL && t.taskStatus != .COMPLETED
    }
}

/// move = 옮기기, resize = 아래 끝(끝 시각), resizeTop = 위 끝(시작 시각)
enum TimetableDragMode: Equatable { case move, resize, resizeTop }

enum TimetableDropTarget: Equatable {
    case grid(date: String, start: Int, end: Int)
    case unscheduled(date: String)
}

struct TimetableDragState {
    var source: TimetableDragSource
    var mode: TimetableDragMode
    var target: TimetableDropTarget?
    /// 놓을 수 없는 이유 (있으면 빨간 미리보기, 놓아도 저장 안 함)
    var invalid: String?
    /// 블록 위쪽에서 잡은 지점까지의 분 (놓을 때 같은 지점이 손가락 아래에 오도록)
    var grab: Double?
    /// 어느 블록의 제스처가 시작했는지 (제스처가 취소됐을 때 정리용)
    var ownerId: String

    var draggingTaskIds: Set<Int> {
        guard let t = source.taskItem else { return [] }
        return Set([t.taskId] + source.group.map { $0.task.taskId })
    }

    var draggingFixedKey: String? {
        if case let .fixed(b, _, _, _) = source { return b.key }
        return nil
    }

    /// 끄는 실제 시간 블록 (Placed.uid 와 같은 "taskId-part")
    var draggingActualUid: String? {
        if case let .actual(t, _, _, _, part) = source { return "\(t.taskId)-\(part)" }
        return nil
    }
}

enum TimetableDrag {
    /// 묶음 드래그 결과: 끈 Task 를 포함해 묶음 전체가 놓일 자리 (끈 만큼 같이 움직여요)
    static func groupMoves(_ source: TimetableDragSource, date: String, start: Int, end: Int) -> [TimetableGroupMember] {
        guard case let .task(t, _, s, _, group) = source else { return [] }
        let me = TimetableGroupMember(task: t, date: date, start: start, end: end)
        guard let s, !group.isEmpty else { return [me] }
        let delta = start - s
        return [me] + group.map { TimetableGroupMember(task: $0.task, date: date, start: $0.start + delta, end: $0.end + delta) }
    }

    /// 원래 자리와 같으면 저장하지 않아요
    static func sameAsSource(_ source: TimetableDragSource, _ target: TimetableDropTarget) -> Bool {
        switch target {
        case let .unscheduled(date): return source.start == nil && date == source.date
        case let .grid(date, start, end): return date == source.date && start == source.start && end == source.end
        }
    }

    /// 10분 단위로 맞추기 (소수 분 → 정수 분)
    static func snap(_ minutes: Double) -> Int {
        Int((minutes / Double(KST.snapMinutes)).rounded()) * KST.snapMinutes
    }
}

// MARK: - 시트 · 확인 바 상태 (웹 Opened / Editing / ScopeAsk)

struct TimetableReplacedTask: Hashable {
    var id: Int
    var name: String
}

/// 실제로 한 일 폼을 열 때의 초기값
struct TimetableActivityDraft: Hashable {
    /// 있으면 수정, 없으면 새로 기록
    var activity: ActualActivity?
    var date: String
    /// 그날 0시 기준 분 (끝이 1440 이상이면 다음 날)
    var start: Int
    var end: Int
    /// "이 시간에 다른 일을 했어요"로 열었을 때 원래 계획했던 Task
    var replaced: TimetableReplacedTask?

    /// 수정할 기록 → 폼 초기값 (웹 draftOf)
    static func of(_ a: ActualActivity) -> TimetableActivityDraft {
        let date = String(a.startAt.prefix(10))
        return TimetableActivityDraft(
            activity: a,
            date: date,
            start: KST.minutesFrom(date, a.startAt),
            end: KST.minutesFrom(date, a.endAt),
            replaced: a.replacedTaskId.map { TimetableReplacedTask(id: $0, name: a.replacedTaskName ?? "계획했던 일") }
        )
    }
}

struct TimetableTimeValue: Hashable {
    var date: String
    /// 그 날짜 0시 기준 분. end 가 1440 이상이면 다음 날
    var start: Int
    var end: Int
}

enum TimetableTimeEditTarget: Hashable {
    case fixed(FixedBlock)
    /// 실제로 한 시간 입력 후 완료
    case actual(TimeBlockTask)
}

struct TimetableTimeEditing: Hashable {
    var target: TimetableTimeEditTarget
    var initial: TimetableTimeValue

    var id: String {
        switch target {
        case .fixed(let b): return "f\(b.key)"
        case .actual(let t): return "a\(t.taskId)"
        }
    }
}

/// 블록을 눌러 여는 시트 (웹 Popover · Modal → iOS 시트 하나로)
enum TimetableSheet: Identifiable {
    case task(TimeBlockTask)
    case fixed(FixedBlock)
    case activity(TimetableActivityDraft)
    case timeEdit(TimetableTimeEditing)

    var id: String {
        switch self {
        case .task(let t): return "task-\(t.taskId)"
        case .fixed(let b): return "fixed-\(b.key)"
        case .activity(let d):
            let a = d.activity.map { String($0.activityId) } ?? "new"
            return "activity-\(a)-\(d.date)-\(d.start)"
        case .timeEdit(let e): return "edit-\(e.id)"
        }
    }
}

enum TimetableScopeAskKind {
    case routine(TimeBlockTask)
    /// edge: 수면이면 기상(아침 블록의 끝)을 바꿨는지 취침(밤 블록의 시작)을 바꿨는지
    case fixed(FixedBlock, edge: TimetableLayout.SleepEdge?)
}

/// 드래그 직후 "어디까지 바꿀까요?"를 묻는 중인 변경 (그동안 새 자리에 임시로 그려 둬요)
struct TimetableScopeAsk {
    var kind: TimetableScopeAskKind
    /// 끌어놓은 칸의 날짜 (start/end 는 이 날 0시 기준 분 — 수면은 음수 · 1440 이상일 수 있어요)
    var date: String
    var start: Int
    var end: Int
    var pendingId: UUID

    /// 확인 바 제목 (웹 ScopeChooser title)
    var title: String {
        switch kind {
        case .routine(let t):
            return "'\(t.taskName)' → \(KST.formatMinutes(start))–\(KST.formatMinutes(end))"
        case let .fixed(b, edge):
            if edge == .wake {
                return "☀️ 기상 \(KST.minutesToTime(KST.minutesFrom(date, b.end))) → \(KST.minutesToTime(end))"
            }
            if edge == .bed {
                return "🌙 취침 \(KST.minutesToTime(KST.minutesFrom(date, b.start))) → \(KST.minutesToTime(start))"
            }
            return "\(TimetableRules.title(b.emoji, b.title)) → \(KST.formatMinutes(start))–\(KST.formatMinutes(end))"
        }
    }

    var options: [TimetableScopeOption] {
        let isToday = date == KST.today()
        switch kind {
        case .routine:
            return TimetableScope.options(kind: .routine, isToday: isToday, crossesMidnight: end >= 1440)
        case let .fixed(_, edge):
            let wd = KST.weekdayLabel[KST.weekdayIndex(date)]
            var suffix = ""
            if edge == .wake { suffix = " 아침" } else if edge == .bed { suffix = " 밤" }
            return TimetableScope.options(kind: .fixed, isToday: isToday, crossesMidnight: end >= 1440,
                                          weekdayLabel: "\(wd)요일\(suffix)")
        }
    }
}

/// 드래그 직후 서버 응답 전까지 새 자리에 그려 둘 블록
struct TimetablePendingEntry: Identifiable {
    let id = UUID()
    var value: TimetableLayout.Pending
}

/// AI 배치 미리보기 대상 (웹 FillPreviewTarget)
enum TimetableFillTarget {
    case range(start: String, end: String)
    case tasks([Int])
}

/// fill = "AI로 배치하기" · added = 방금 추가한 Task (확인 바 문구가 달라요)
enum TimetableProposalKind { case fill, added }

/// 22시 이월 제안 버튼 (어느 버튼이 돌고 있는지)
enum TimetableCarryAction { case accept, decline, acceptAll, revert }
