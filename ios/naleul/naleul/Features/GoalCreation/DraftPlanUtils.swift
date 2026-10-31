// Features/GoalCreation/DraftPlanUtils.swift
//
// 초안(GoalPlan) 날짜 표시 · 자동 보정 · 검증.
// 웹 원본: features/goal-creation/ui/draft/planUtils.ts
//
// 날짜는 "YYYY-MM-DD" 문자열이라 문자열 비교(<, >)가 곧 날짜 비교예요 (웹과 같아요).

import Foundation

enum DraftPlanUtils {
    // MARK: 날짜

    /// "2026-10-02" → "10.02 (금)"
    static func formatMd(_ value: String?, withWeekday: Bool = true) -> String {
        guard let value, !value.isEmpty else { return "" }
        let p = KST.parts(value)
        let md = "\(pad(p.m)).\(pad(p.d))"
        return withWeekday ? "\(md) (\(KST.weekdayLabel[KST.weekdayIndex(value)]))" : md
    }

    /// "2026-10-02" → "2026.10.02"
    static func formatYmdDot(_ value: String) -> String { value.replacingOccurrences(of: "-", with: ".") }

    static func daysBetween(_ from: String, _ to: String) -> Int { KST.diffDays(from, to) }

    /// 오늘 기준 D-day 숫자 (남은 일수)
    static func dDay(_ to: String) -> Int { daysBetween(KST.today(), to) }

    static func formatDays(_ days: [GoalCreationWeekday]?) -> String {
        guard let days, !days.isEmpty else { return "" }
        if days.count == 7 { return "매일" }
        return days.map(\.label).joined(separator: "·")
    }

    private static func pad(_ n: Int) -> String { n < 10 ? "0\(n)" : "\(n)" }

    /// 숫자를 웹(JS)처럼: 72.0 → "72", 72.5 → "72.5"
    static func num(_ v: Double) -> String {
        if v.isFinite, v == v.rounded(), abs(v) < 1e15 { return String(Int(v)) }
        return String(v)
    }

    // MARK: 편집 도우미

    private static var seq = 0
    /// 새로 추가하는 항목의 tempId (중복만 안 되면 돼요)
    static func newTempId(_ prefix: String) -> String {
        seq += 1
        let t = String(Int(Date().timeIntervalSince1970 * 1000), radix: 36)
        return "\(prefix)-new-\(t)-\(seq)"
    }

    /// 루틴 주간 총 시간(분)
    static func weeklyRoutineMinutes(_ tasks: [DraftPlanTask]) -> Int {
        tasks.filter(\.isRoutine).reduce(0) { $0 + $1.durationMinutes * ($1.routineDays?.count ?? 0) }
    }

    /// 60 → "1시간", 75 → "1시간 15분", 40 → "40분" (웹 hoursText)
    static func hoursText(_ minutes: Int) -> String {
        if minutes < 60 { return "\(minutes)분" }
        let h = minutes / 60
        let m = minutes % 60
        return m > 0 ? "\(h)시간 \(m)분" : "\(h)시간"
    }

    // MARK: 자동 보정

    /// 시작값·목표값 자리수에 맞춰 반올림 (80 → 72 면 정수, 80.5 → 72 면 소수 첫째 자리)
    private static func roundLike(_ value: Double, _ refs: Double...) -> Double {
        let decimals = refs.map { r -> Int in
            let s = num(r)
            guard let dot = s.firstIndex(of: ".") else { return 0 }
            return s.distance(from: dot, to: s.endIndex) - 1
        }.max() ?? 0
        let f = pow(10.0, Double(min(max(decimals, 0), 2)))
        return (value * f).rounded() / f
    }

    /// 사용자가 무엇을 고치든 계획이 "깨지지 않게" 자동으로 맞춰요.
    ///  - 마일스톤: 날짜순 정렬 · 목표 기간 안으로 · 겹치는 날짜는 하루씩 밀기 · 마지막은 종료일
    ///  - 마일스톤 수치: 시작값 → 목표값 직선 위의 값 (날짜 비율대로) — 그래프가 항상 한 방향
    ///  - 일회성 할 일: 마감이 연결된 마일스톤보다 늦으면 마일스톤 날짜로, 실행일이 마감보다 늦으면 마감으로
    ///  - 시작일을 미뤘을 때: 루틴 시작일이 그보다 앞이면 비워서(= 목표 시작일부터), 일회성 날짜도 시작일 이후로
    static func normalizePlan(_ plan: DraftGoalPlan) -> DraftGoalPlan {
        let goal = plan.goal
        let start = goal.startDate
        let end = goal.endDate
        if start.isEmpty || end.isEmpty || end <= start { return plan }

        // 1) 날짜
        var milestones = plan.milestones.sorted { $0.dueDate < $1.dueDate }
        for i in milestones.indices {
            let m = milestones[i]
            var due = m.dueDate < start ? KST.addDays(start, 1) : (m.dueDate > end ? end : m.dueDate)
            if i > 0, due <= milestones[i - 1].dueDate { due = KST.addDays(milestones[i - 1].dueDate, 1) }
            milestones[i].dueDate = due > end ? end : due
        }
        if !milestones.isEmpty { milestones[milestones.count - 1].dueDate = end }

        // 2) 수치 — 직선 보간
        let total = max(daysBetween(start, end), 1)
        for i in milestones.indices {
            guard let metric = goal.metric else {
                milestones[i].targetValue = nil
                continue
            }
            let ratio = min(max(Double(daysBetween(start, milestones[i].dueDate)) / Double(total), 0), 1)
            let raw = metric.startValue + (metric.targetValue - metric.startValue) * ratio
            milestones[i].targetValue = roundLike(raw, metric.startValue, metric.targetValue)
        }
        if let metric = goal.metric, !milestones.isEmpty {
            milestones[milestones.count - 1].targetValue = metric.targetValue
        }

        // 3) 할 일 날짜
        var dueOf: [String: String] = [:]
        for m in milestones { dueOf[m.tempId] = m.dueDate }
        let tasks: [DraftPlanTask] = plan.tasks.map { t in
            var t2 = t
            if t.isRoutine {
                if let s = t.startDate, s < start { t2.startDate = nil }
                if let e = t.endDate, e > end || e < start { t2.endDate = nil }
                return t2
            }
            let msDue = t.milestoneTempId.flatMap { dueOf[$0] }
            var due = t.dueDate
            if let d = due, let md = msDue, d > md { due = md }
            if let d = due, d > end { due = end }
            if let d = due, d < start { due = msDue ?? start }
            var scheduled = t.scheduledDate
            if let s = scheduled, let d = due, s > d { scheduled = d }
            if let s = scheduled, s < start { scheduled = nil }
            t2.dueDate = due
            t2.scheduledDate = scheduled
            return t2
        }

        var next = plan
        next.milestones = milestones
        next.tasks = tasks
        return next
    }

    // MARK: 검증
    // 서버 HARD 규칙 중 앱에서 바로 확인 가능한 것들. path 형식을 서버 violations 와 같게 맞춰서
    // 화면 표시 로직을 하나로 써요.

    /// 사용자가 초안을 고칠 때의 한도 = 서버 GoalPlanValidator.USER_LIMITS
    enum Limits {
        static let title = 40
        static let subGoals = (min: 1, max: 5)
        // V52: AI 초안은 마일스톤 없이 와요 (서버 USER_LIMITS 0~8)
        static let milestones = (min: 0, max: 8)
        static let routines = (min: 0, max: 10)
        static let oneTimes = (min: 0, max: 30)
        static let routineMinutes = (min: 1, max: 1440)
        static let oneTimeMinutes = (min: 1, max: 1440)
        static let routineDescription = 300
    }

    static func validatePlan(_ plan: DraftGoalPlan) -> [DraftPlanIssue] {
        var issues: [DraftPlanIssue] = []
        func add(_ path: String, _ message: String) { issues.append(DraftPlanIssue(ruleId: "CLIENT", path: path, message: message)) }
        func titleOk(_ t: String) -> Bool {
            let n = t.trimmingCharacters(in: .whitespacesAndNewlines).count
            return n >= 1 && n <= Limits.title
        }

        if !titleOk(plan.goal.title) { add("goal.title", "목표 이름은 1~\(Limits.title)자로 적어 주세요.") }

        let sg = Limits.subGoals
        if plan.subGoals.count < sg.min || plan.subGoals.count > sg.max {
            add("subGoals", "세부 목표는 \(sg.min)~\(sg.max)개여야 해요.")
        }
        for (i, s) in plan.subGoals.enumerated() where !titleOk(s.title) {
            add("subGoals[\(i)].title", "제목은 1~\(Limits.title)자로 적어 주세요.")
        }

        let ms = Limits.milestones
        if plan.milestones.count < ms.min || plan.milestones.count > ms.max {
            add("milestones", "마일스톤은 \(ms.min)~\(ms.max)개여야 해요.")
        }
        for (i, m) in plan.milestones.enumerated() {
            if !titleOk(m.title) { add("milestones[\(i)].title", "제목은 1~\(Limits.title)자로 적어 주세요.") }
            if i > 0, m.dueDate <= plan.milestones[i - 1].dueDate {
                add("milestones[\(i)].dueDate", "마일스톤 날짜는 앞 단계보다 뒤여야 해요.")
            }
        }
        if let last = plan.milestones.last, last.dueDate != plan.goal.endDate {
            add("milestones[\(plan.milestones.count - 1)].dueDate",
                "마지막 마일스톤은 목표 종료일(\(formatYmdDot(plan.goal.endDate)))이어야 해요.")
        }

        let routineCount = plan.tasks.filter(\.isRoutine).count
        if plan.tasks.isEmpty { add("tasks", "할 일을 하나 이상 남겨 주세요.") }
        if routineCount < Limits.routines.min || routineCount > Limits.routines.max {
            add("tasks", "루틴은 \(Limits.routines.max)개까지 만들 수 있어요.")
        }
        // 일회성 Task 개수는 검사하지 않아요 — AI 목표는 루틴 중심이라 0개가 기본이에요

        for (i, t) in plan.tasks.enumerated() {
            let p = "tasks[\(i)]"
            if !titleOk(t.title) { add("\(p).title", "제목은 1~\(Limits.title)자로 적어 주세요.") }
            if t.isRoutine {
                let r = Limits.routineMinutes
                if t.durationMinutes < r.min || t.durationMinutes > r.max {
                    add("\(p).durationMinutes", "루틴은 \(r.min)~\(r.max)분으로 설정해 주세요.")
                }
                if (t.routineDays ?? []).isEmpty { add("\(p).routineDays", "요일을 하나 이상 골라 주세요.") }
            } else {
                let r = Limits.oneTimeMinutes
                if t.durationMinutes < r.min || t.durationMinutes > r.max {
                    add("\(p).durationMinutes", "할 일은 \(r.min)~\(r.max)분으로 설정해 주세요.")
                }
                if t.dueDate == nil { add("\(p).dueDate", "마감일을 정해 주세요.") }
                if let s = t.scheduledDate, let d = t.dueDate, s > d {
                    add("\(p).scheduledDate", "실행일은 마감일보다 늦을 수 없어요.")
                }
                if let m = plan.milestones.first(where: { $0.tempId == t.milestoneTempId }),
                   let d = t.dueDate, d > m.dueDate {
                    add("\(p).dueDate", "마감일은 연결된 마일스톤(\(formatMd(m.dueDate, withWeekday: false)))보다 늦을 수 없어요.")
                }
            }
        }
        return issues
    }

    /// violation path → 화면 항목 키
    ///  "tasks[1].durationMinutes" → "tasks[1]",  "goal.title" → "goal",  "milestones" → "milestones"
    static func itemKeyOf(_ path: String) -> String {
        if let r = path.range(of: #"^[a-zA-Z]+(\[\d+\])?"#, options: .regularExpression) {
            return String(path[r])
        }
        return path
    }

    /// 항목 키별 메시지 묶기
    static func groupIssues(_ issues: [DraftPlanIssue]) -> [String: [String]] {
        var map: [String: [String]] = [:]
        for issue in issues {
            let key = itemKeyOf(issue.path)
            var list = map[key] ?? []
            if !list.contains(issue.message) { list.append(issue.message) }
            map[key] = list
        }
        return map
    }
}
