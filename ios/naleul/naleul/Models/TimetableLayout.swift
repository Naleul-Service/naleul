// Models/TimetableLayout.swift
//
// 웹 features/timetable/layout.ts — TimeTable 격자에 블록을 놓기 위한 순수 계산 (화면과 분리).
// 모든 위치는 "그날 0시부터 몇 분" (음수 · 1440 이상 가능) 이에요.

import Foundation

enum TimetableLayout {
    static let defaultStartHour = 7
    static let defaultEndHour = 24
    /// 취침이 자정을 넘기면(예: 01:00) 격자를 다음 날 새벽까지 늘려요. 최대 다음 날 06:00
    static let maxEndHour = 30
    /// 너무 짧은 블록도 글자가 보이도록
    static let minTaskMinutes = 15
    /// 기상 전 · 취침 후로 더 보여줄 시간 — 기상·취침을 끌어서 바꿀 수 있게 (분)
    static let sleepMarginMinutes = 120
    /// 한 시각에 같이 둘 수 있는 (밀어낼 수 없는) Task 수 — 백엔드 Concurrency.MAX_TASKS_AT_ONCE 와 같아야 해요
    static let maxTasksAtOnce = 2

    struct ShownTime: Hashable {
        var startAt: String
        var endAt: String?
        /// true 면 실제로 한 시각, false 면 계획 시각
        var actual: Bool
    }

    struct MinuteRange: Hashable {
        var start: Int
        var end: Int
    }

    /// 블록을 그릴 시각 (백엔드 TimetableQueryService.blockRange 와 같은 규칙)
    ///  - 완료 전: 계획 시각
    ///  - 완료 후: 실제 시각이 기록돼 있으면 실제로 한 시각, 없으면 계획 시각
    /// 시간이 없으면 nil (시간 미정)
    static func shownTime(_ t: TimeBlockTask) -> ShownTime? {
        if t.taskStatus == .COMPLETED, let s = t.actualStartAt, let e = t.actualEndAt {
            return ShownTime(startAt: s, endAt: e, actual: true)
        }
        guard let s = t.plannedStartAt else { return nil }
        return ShownTime(startAt: s, endAt: t.plannedEndAt, actual: false)
    }

    /// 계획 시각 기준 구간 (완료했어도 원래 계획한 자리)
    static func plannedMinutes(_ day: String, _ t: TimeBlockTask) -> MinuteRange? {
        guard let s = t.plannedStartAt else { return nil }
        let start = KST.minutesFrom(day, s)
        let end = t.plannedEndAt.map { KST.minutesFrom(day, $0) } ?? start + (t.plannedDurationMinutes ?? 30)
        return MinuteRange(start: start, end: end)
    }

    /// 그날 0시 기준 분으로 바꾼 블록 구간. 끝이 없으면 소요 시간(없으면 30분)으로
    static func shownMinutes(_ day: String, _ t: TimeBlockTask, fallbackMinutes: Int? = nil) -> MinuteRange? {
        guard let st = shownTime(t) else { return nil }
        let start = KST.minutesFrom(day, st.startAt)
        let end = st.endAt.map { KST.minutesFrom(day, $0) } ?? start + (fallbackMinutes ?? t.plannedDurationMinutes ?? 30)
        return MinuteRange(start: start, end: end)
    }

    /// 격자에 보여줄 시간 범위 (시 단위). 기본은 하루 범위(기상~취침) + 앞뒤 2시간, 그 밖에 Task 가 있으면 넓혀요
    static func visibleHours(_ days: [TimetableDay], activities: [ActualActivity] = []) -> (startHour: Int, endHour: Int) {
        if days.isEmpty { return (defaultStartHour, defaultEndHour) }
        var start = Int.max
        var end = Int.min
        for d in days {
            start = min(start, KST.minutesFrom(d.date, d.dayRange.start) - (d.sleep?.wake != nil ? sleepMarginMinutes : 0))
            end = max(end, KST.minutesFrom(d.date, d.dayRange.end) + (d.sleep?.bed != nil ? sleepMarginMinutes : 0))
            for t in d.tasks {
                guard let m = shownMinutes(d.date, t, fallbackMinutes: 30) else { continue }
                start = min(start, m.start)
                end = max(end, m.end)
            }
            for a in activities {
                let s = KST.minutesFrom(d.date, a.startAt)
                let e = KST.minutesFrom(d.date, a.endAt)
                if e <= 0 || s >= maxEndHour * 60 { continue }
                start = min(start, max(s, 0))
                end = max(end, min(e, maxEndHour * 60))
            }
        }
        let startHour = max(0, min(defaultEndHour - 1, Int(floor(Double(start) / 60))))
        // 끝은 최소 24시 — 취침 직전·늦은 밤으로도 끌어다 놓을 수 있게
        let endHour = min(maxEndHour, max(startHour + 1, Int(ceil(Double(end) / 60)), defaultEndHour))
        return (startHour, endHour)
    }

    // MARK: 배치

    struct Placed<Item> {
        var item: Item
        /// 그날 0시 기준 분 (화면 범위로 잘림)
        var top: Int
        var bottom: Int
        /// 잘리기 전 원래 시각(분) — 글자로 보여줄 때
        var start: Int
        var end: Int
        /// 원래 시간이 화면 범위 밖까지 이어지는지
        var clippedTop: Bool
        var clippedBottom: Bool
        /// 겹칠 때 나란히 놓기 위한 열 정보
        var col: Int = 0
        var cols: Int = 1
        /// 한 Task 를 여러 구간으로 나눠 그릴 때 몇 번째 조각인지 (나눠서 한 실제 시간)
        var part: Int = 0
    }

    private struct Clip {
        var top: Int, bottom: Int, start: Int, end: Int, clippedTop: Bool, clippedBottom: Bool
    }

    private static func clip(_ start: Int, _ end: Int, _ from: Int, _ to: Int) -> Clip? {
        let top = max(start, from)
        let bottom = min(end, to)
        if bottom <= top { return nil }
        return Clip(top: top, bottom: bottom, start: start, end: end, clippedTop: start < from, clippedBottom: end > to)
    }

    static func placeFixed(_ day: TimetableDay, from: Int, to: Int) -> [Placed<FixedBlock>] {
        // 기상 · 취침 수면은 placeSleep 이 자르지 않은 원본으로 따로 그려요 (기상·취침 가장자리를 끌 수 있게).
        // 그 둘이 아닌 수면(예전 서버가 늦잠 기상을 못 알아본 경우 등)은 여기서 그대로 그려서 사라지지 않게 해요
        func same(_ a: FixedBlock, _ b: FixedBlock?) -> Bool {
            guard let b else { return false }
            return a.lifePatternId == b.lifePatternId && a.targetDate == b.targetDate
        }
        var out: [Placed<FixedBlock>] = []
        for b in day.fixedBlocks {
            if b.patternType == .SLEEP && (same(b, day.sleep?.wake) || same(b, day.sleep?.bed)) { continue }
            if let c = clip(KST.minutesFrom(day.date, b.start), KST.minutesFrom(day.date, b.end), from, to) {
                out.append(Placed(item: b, top: c.top, bottom: c.bottom, start: c.start, end: c.end,
                                  clippedTop: c.clippedTop, clippedBottom: c.clippedBottom))
            }
        }
        return out
    }

    enum SleepEdge: String { case wake, bed }

    struct PlacedSleep {
        var placed: Placed<FixedBlock>
        /// wake = 아침에 끝나는 수면 (아래 가장자리 = 기상), bed = 밤에 시작하는 수면 (위 가장자리 = 취침)
        var edge: SleepEdge
    }

    /// 그날 칸에 그릴 수면 블록 (기상 전 · 취침 후)
    static func placeSleep(_ day: TimetableDay, from: Int, to: Int) -> [PlacedSleep] {
        var out: [PlacedSleep] = []
        for edge in [SleepEdge.wake, .bed] {
            guard let b = (edge == .wake ? day.sleep?.wake : day.sleep?.bed) else { continue }
            if let c = clip(KST.minutesFrom(day.date, b.start), KST.minutesFrom(day.date, b.end), from, to) {
                out.append(PlacedSleep(placed: Placed(item: b, top: c.top, bottom: c.bottom, start: c.start, end: c.end,
                                                      clippedTop: c.clippedTop, clippedBottom: c.clippedBottom), edge: edge))
            }
        }
        return out
    }

    enum TaskBasis {
        /// 화면 기본 (완료 전엔 계획 시각, 완료 후엔 실제 시각)
        case shown
        /// 모두 계획 시각 (일간 "계획" 칸)
        case planned
        /// 완료한 Task 만, 실제 시각(없으면 계획 시각) (일간 "실제" 칸)
        case done
    }

    /// Task 블록 배치. 겹치면 나란히 그려요
    static func placeTasks(_ day: TimetableDay, from: Int, to: Int, basis: TaskBasis = .shown) -> [Placed<TimeBlockTask>] {
        var items: [Placed<TimeBlockTask>] = []
        for t in day.tasks {
            if basis == .done && t.taskStatus != .COMPLETED { continue }
            // 여러 번 나눠서 한 완료 Task 는 실제로 한 구간마다 블록 하나씩 (계획 기준일 땐 계획 자리 하나)
            let actualSegs = basis != .planned && t.taskStatus == .COMPLETED ? (t.actualSegments ?? []) : []
            // 자동 배치가 점심 앞뒤로 나눠 넣은 Task 는 계획 구간마다 블록 하나씩 (실제 시각으로 그릴 땐 제외)
            let drawnAtActual = basis != .planned && t.taskStatus == .COMPLETED && t.actualStartAt != nil
            let segs = actualSegs.count > 1 ? actualSegs : (drawnAtActual ? [] : (t.plannedSegments ?? []))
            let ranges: [MinuteRange?] = segs.count > 1
                ? segs.map { MinuteRange(start: KST.minutesFrom(day.date, $0.start), end: KST.minutesFrom(day.date, $0.end)) }
                : [basis == .planned ? plannedMinutes(day.date, t) : shownMinutes(day.date, t)]
            for (part, r) in ranges.enumerated() {
                guard let m = r else { continue }
                guard let c = clip(m.start, max(m.end, m.start + minTaskMinutes), from, to) else { continue }
                items.append(Placed(item: t, top: c.top, bottom: c.bottom, start: c.start, end: m.end,
                                    clippedTop: c.clippedTop, clippedBottom: c.clippedBottom, part: part))
            }
        }
        return layoutColumns(items)
    }

    /// 시간대 습관 블록 (V56) — 그날 칸의 위치
    static func placeHabits(_ day: TimetableDay, from: Int, to: Int) -> [Placed<HabitBlock>] {
        (day.habits ?? []).compactMap { h in
            guard let c = clip(KST.minutesFrom(day.date, h.start), KST.minutesFrom(day.date, h.end), from, to) else { return nil }
            return Placed(item: h, top: c.top, bottom: c.bottom, start: c.start, end: c.end,
                          clippedTop: c.clippedTop, clippedBottom: c.clippedBottom)
        }
    }

    /// 겹치는 블록을 나란히 놓도록 열(col / cols)을 정해요 (위→아래 순으로 돌려줘요)
    static func layoutColumns<T>(_ list: [Placed<T>]) -> [Placed<T>] {
        let sorted = list.sorted { a, b in a.top != b.top ? a.top < b.top : a.bottom > b.bottom }
        var out: [Placed<T>] = []
        var cluster: [Placed<T>] = []
        var clusterEnd = Int.min
        var colEnds: [Int] = []

        func flush() {
            let cols = max(1, colEnds.count)
            for i in cluster.indices { cluster[i].cols = cols }
            out.append(contentsOf: cluster)
            cluster = []
            colEnds = []
        }

        for var p in sorted {
            if p.top >= clusterEnd { flush() }
            var col = colEnds.firstIndex { $0 <= p.top } ?? -1
            if col == -1 {
                col = colEnds.count
                colEnds.append(p.bottom)
            } else {
                colEnds[col] = p.bottom
            }
            p.col = col
            p.cols = 1
            cluster.append(p)
            clusterEnd = max(clusterEnd, p.bottom)
        }
        flush()
        return out
    }

    /// 그날 칸에 그릴 "실제로 한 일" 블록. 자정을 넘긴 기록은 날짜마다 잘린 조각으로 그려요
    static func placeActivities(_ day: String, _ activities: [ActualActivity], from: Int, to: Int) -> [Placed<ActualActivity>] {
        var out: [Placed<ActualActivity>] = []
        for a in activities {
            let start = KST.minutesFrom(day, a.startAt)
            let end = KST.minutesFrom(day, a.endAt)
            if end <= 0 || start >= to { continue }
            let top = max(start, 0, from)
            let bottom = min(max(end, start + minTaskMinutes), to)
            if bottom <= top { continue }
            out.append(Placed(item: a, top: top, bottom: bottom, start: start, end: end,
                              clippedTop: start < top, clippedBottom: end > bottom))
        }
        return out
    }

    // MARK: 놓을 수 있는지

    /// 자동 배치가 밀어낼 수 없는 블록 — 백엔드 isMovableEngineBlock 의 반대 (건너뛴 Task 는 자리를 차지하지 않아요)
    static func isBlockingTask(_ t: TimeBlockTask) -> Bool {
        t.taskStatus != .SKIPPED &&
            (t.locked || t.placedBy != .ENGINE || t.taskStatus != .TODO || t.sourceType == .MISSION)
    }

    /// window 안에서 ranges 가 가장 많이 겹치는 순간의 개수 (끝과 시작이 같으면 겹치지 않음)
    static func maxOverlap(_ ranges: [MinuteRange], window: MinuteRange) -> Int {
        var events: [(Int, Int)] = []
        for r in ranges where r.start < window.end && r.end > window.start {
            events.append((max(r.start, window.start), 1))
            events.append((min(r.end, window.end), -1))
        }
        // 같은 시각이면 끝(-1)을 먼저 → 10~11 과 11~12 는 겹치지 않음
        events.sort { $0.0 != $1.0 ? $0.0 < $1.0 : $0.1 < $1.1 }
        var cur = 0
        var best = 0
        for (_, d) in events {
            cur += d
            best = max(best, cur)
        }
        return best
    }

    /// start~end 에 놓으면 한 시각에 밀어낼 수 없는 Task 가 3개 이상이 되는지.
    /// 놓을 수 없으면 그 자리에 이미 있는 Task 들, 놓을 수 있으면 nil
    /// - exclude: 같이 옮기는 Task 들 (원래 자리는 빼고 봐요)
    /// - extra: 같이 옮기는 다른 Task 들의 새 자리
    static func crowdedTasks(_ day: TimetableDay, start: Int, end: Int, exclude: Set<Int>,
                             extra: [MinuteRange] = []) -> [TimeBlockTask]? {
        var hits: [(task: TimeBlockTask, range: MinuteRange)] = []
        for t in day.tasks {
            if exclude.contains(t.taskId) || !isBlockingTask(t) { continue }
            if let r = shownMinutes(day.date, t, fallbackMinutes: 30), r.start < end, r.end > start {
                hits.append((t, r))
            }
        }
        let ranges = hits.map { $0.range } + extra
        if maxOverlap(ranges, window: MinuteRange(start: start, end: end)) < maxTasksAtOnce { return nil }
        return hits.map { $0.task }
    }

    // MARK: 드래그 결과를 서버 응답 전에 먼저 보여주기 (낙관적 표시)

    enum Pending: Hashable {
        /// Task 를 date 의 start~end 로 옮김. date 가 nil 이면 "시간 미정"으로 보냄
        case task(taskId: Int, date: String?, start: Int, end: Int)
        /// 고정 블록(key)을 그날 start~end 로 바꿈
        case fixed(key: String, date: String, start: Int, end: Int)
    }

    /// 서버 응답이 오기 전까지 옮긴 위치에 블록을 그려요
    static func applyPending(_ days: [TimetableDay], _ pending: [Pending]) -> [TimetableDay] {
        if pending.isEmpty { return days }
        var taskMoves: [Int: (date: String?, start: Int, end: Int)] = [:]
        var fixedMoves: [String: (date: String, start: Int, end: Int)] = [:]
        for p in pending {
            switch p {
            case let .task(id, date, s, e): taskMoves[id] = (date, s, e)
            case let .fixed(key, date, s, e): fixedMoves[key] = (date, s, e)
            }
        }
        let all = days.flatMap { $0.tasks + $0.unscheduledTasks }

        return days.map { d0 in
            var d = d0
            d.tasks = d0.tasks.filter { taskMoves[$0.taskId] == nil }
            d.unscheduledTasks = d0.unscheduledTasks.filter { taskMoves[$0.taskId] == nil }
            for (id, p) in taskMoves {
                guard var t = all.first(where: { $0.taskId == id }) else { continue }
                if p.date == d.date {
                    t.date = d.date
                    t.plannedStartAt = KST.toDateTime(d.date, p.start)
                    t.plannedEndAt = KST.toDateTime(d.date, p.end)
                    t.plannedSegments = nil   // 옮기면 한 덩어리 (서버 응답 전까지 옛 조각 자리에 그리지 않게)
                    t.locked = true
                    t.placedBy = .USER
                    t.missed = false
                    d.tasks.append(t)
                } else if p.date == nil, (t.date ?? t.plannedStartAt.map { String($0.prefix(10)) }) == d.date {
                    t.plannedStartAt = nil
                    t.plannedEndAt = nil
                    t.plannedSegments = nil
                    t.locked = false
                    d.unscheduledTasks.append(t)
                }
            }
            // 클로저 안에서 바꾸는 중인 d 를 읽지 않도록 날짜를 상수로 (d0.date == d.date)
            let dayDate = d0.date
            d.fixedBlocks = d0.fixedBlocks.map { b in
                guard let p = fixedMoves[b.key], p.date == dayDate else { return b }
                var nb = b
                nb.start = KST.toDateTime(dayDate, p.start)
                nb.end = KST.toDateTime(dayDate, p.end)
                nb.overridden = true
                return nb
            }
            // 수면 블록은 이틀 칸에 걸쳐 보여서, 끌어놓은 칸의 날짜 기준으로 바꾼 시각을 양쪽에 반영해요
            func moveSleep(_ b: FixedBlock?) -> FixedBlock? {
                guard let b, let p = fixedMoves[b.key] else { return b }
                var nb = b
                nb.start = KST.toDateTime(p.date, p.start)
                nb.end = KST.toDateTime(p.date, p.end)
                nb.overridden = true
                return nb
            }
            if let s = d0.sleep {
                d.sleep = SleepEdges(wake: moveSleep(s.wake), bed: moveSleep(s.bed))
            }
            return d
        }
    }

    // MARK: "AI로 배치하기" 미리보기

    /// 미리보기 제안 하나 (화면 상태)
    struct Proposal: Hashable, Identifiable {
        var id: Int { taskId }
        var taskId: Int
        var reason: String?
        /// AI 가 처음 제안한 자리 — 확정할 때 "고쳤는지" 비교용
        var proposedStart: String
        var proposedEnd: String
        /// 지금 자리 (끌어서 고치면 바뀜)
        var start: String
        var end: String
    }

    /// 제안을 점선 블록으로 덧입혀요: 시간 미정 칸에서 빼서 제안 자리에 그려요 (저장 전)
    static func applyProposals(_ days: [TimetableDay], _ proposals: [Proposal]?) -> [TimetableDay] {
        guard let proposals, !proposals.isEmpty else { return days }
        let ids = Set(proposals.map(\.taskId))
        let all = days.flatMap { $0.tasks + $0.unscheduledTasks }
        return days.map { d0 in
            var d = d0
            d.tasks = d0.tasks.filter { !ids.contains($0.taskId) }
            d.unscheduledTasks = d0.unscheduledTasks.filter { !ids.contains($0.taskId) }
            for p in proposals where String(p.start.prefix(10)) == d.date {
                guard var t = all.first(where: { $0.taskId == p.taskId }) else { continue }
                t.date = d.date
                t.plannedStartAt = p.start
                t.plannedEndAt = p.end
                t.plannedSegments = nil
                t.placedBy = .ENGINE
                t.locked = false
                t.missed = false
                t.placementReason = p.reason ?? t.placementReason
                t.proposed = true
                d.tasks.append(t)
            }
            return d
        }
    }
}

extension TimetableLayout.Placed where Item == TimeBlockTask {
    /// ForEach 용 — 나눠서 한 Task 는 조각마다 따로
    var uid: String { "\(item.taskId)-\(part)" }
}
