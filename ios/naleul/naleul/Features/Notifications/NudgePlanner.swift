// Features/Notifications/NudgePlanner.swift
//
// 앞으로 사흘 동안 울릴 "실천 도우미" 알림을 계산해요 (화면 · 네트워크 없이 데이터만 받아서 → 테스트 · 미리보기 쉬움).
// 앱을 열 때마다 다시 계산해서 예약을 통째로 바꿔요. 그래서
//  - 일정이 바뀌면 다음에 앱을 열 때 알림 내용도 바뀌고
//  - "다시 시작" 알림은 앱을 열 때마다 뒤로 밀려서, 정말 며칠 안 들어왔을 때만 울려요.
//
// 시간 규칙
//  - 07:00 전 · 23:45 뒤에는 울리지 않아요 (잠을 깨우지 않게)
//  - 기상 · 취침 시각은 그날 캘린더의 수면 블록에서 읽고, 없으면 08:00 기상 · 23:30 취침으로 봐요
//  - 하루에 많아야 3개 (가장 중요한 순서대로) — 알림은 적을수록 피로가 덜해요
//  - 2026-10: "오늘 할 일이 많아 보여요" · "내일 할 일 n개가 기다려요"는 뺐어요 (실천에 도움이 안 돼서).
//    루틴을 놓친 순간의 알림은 서버가 구체적인 숫자로 보내요 (ROUTINE_NUDGE)

import Foundation

struct NudgeSnapshot {
    var now: Date
    /// 오늘부터 사흘 (오늘 · 내일 · 모레)
    var days: [TimetableDay]
    /// 이번 주 월요일 ~ 오늘 (주간 돌아보기 숫자용)
    var week: [TimetableDay]
    var goals: [GoalCategory]
    /// 꺼 둔 묶음은 빼요
    var enabled: Set<NudgeGroup>
}

enum NudgePlanner {
    static let quietStart = 7 * 60
    static let quietEnd = 23 * 60 + 45
    static let maxPerDay = 3
    static let defaultWake = 8 * 60
    static let defaultBed = 23 * 60 + 30

    /// 우선순위가 높은 것부터 (하루 상한에 걸리면 뒤의 것이 빠져요)
    private static let priority: [NudgeKind] = [
        .dayWrapUp, .firstStep, .countdown, .weeklyReport, .monthlyReport, .why, .comeback,
    ]

    static func plan(_ s: NudgeSnapshot) -> [NudgeEntry] {
        let now = KST.now(s.now)
        let today = now.date
        var out: [NudgeEntry] = []

        func add(_ kind: NudgeKind, day: String, minutes: Int, key: String = "", title: String, body: String, route: NudgeRoute) {
            guard let g = kind.group, s.enabled.contains(g) else { return }
            // 조용한 시간 · 이미 지난 시각은 빼요
            guard minutes >= quietStart, minutes <= quietEnd else { return }
            let fire = KST.date(fromDateTime: KST.toDateTime(day, minutes))
            guard fire > s.now.addingTimeInterval(60) else { return }
            out.append(NudgeEntry(id: "nudge.\(kind.rawValue).\(day)\(key.isEmpty ? "" : ".\(key)")",
                                  kind: kind, title: title, body: body, date: fire, route: route.raw))
        }

        let byDate = Dictionary(s.days.map { ($0.date, $0) }, uniquingKeysWith: { a, _ in a })
        for offset in 0..<3 {
            let d = KST.addDays(today, offset)
            guard let day = byDate[d] else { continue }
            let wake = wakeMinutes(day) ?? defaultWake
            let bed = bedMinutes(day) ?? defaultBed
            let tasks = scheduled(day)
            let open = tasks.filter { !$0.task.isCompleted }

            // ① 아침 첫걸음 (A) — 전부가 아니라 "하나만"
            if let first = open.first(where: { $0.start >= wake }) ?? open.first {
                let rest = open.count - 1
                add(.firstStep, day: d, minutes: wake + 20,
                    title: "오늘은 이것 하나만 먼저 👣",
                    body: "\(KST.formatMinutes(first.start)) \(name(first.task))"
                        + (rest > 0 ? " — 나머지 \(rest)개는 이것부터 끝내고 봐요." : " — 오늘은 이것만 하면 돼요."),
                    route: .calendar(date: d))
            }

            // ③ 하루 마무리 (P) — 마지막 일정이 끝나고 10분 뒤 (18:00 ~ 23:00 사이)
            if !tasks.isEmpty {
                let lastEnd = tasks.map(\.end).max() ?? (21 * 60)
                let m = min(max(lastEnd + 10, 18 * 60), min(23 * 60, bed - 40))
                add(.dayWrapUp, day: d, minutes: m,
                    title: "오늘 일정이 끝났어요 🌙",
                    body: "3분만 오늘을 돌아볼까요? 한 일은 체크하고 못 한 이유는 한 줄로 남겨 봐요. 체크한 기록으로 나의 패턴이 잘 지키는 시간을 찾아요.",
                    route: .homeRetro)
            }

            // ⑤ 주간 돌아보기 — 일요일 20:30
            if KST.weekdayIndex(d) == 6 {
                add(.weeklyReport, day: d, minutes: 20 * 60 + 30,
                    title: "이번 주 돌아보기 📊",
                    body: weekLine(s.week) + " 잘한 것과 못 한 이유를 한 줄씩 남겨 봐요.",
                    route: .reflections)
            }

            // ⑥ 월간 돌아보기 — 그 달 마지막 날 21:00
            if KST.dayOfMonth(d) == KST.lastDayOfMonth(year: KST.yearOf(d), month: KST.monthOf(d)) {
                add(.monthlyReport, day: d, minutes: 21 * 60,
                    title: "\(KST.monthOf(d))월을 돌아볼 시간이에요",
                    body: "한 달 동안 쌓인 기록을 모아 뒀어요. 다음 달에 가져갈 것 하나만 골라 봐요.",
                    route: .patterns)
            }
        }

        // ⑦ 목표: 끝이 보여요 · 시작한 이유 (M)
        let active = s.goals.filter { $0.goalCategoryStatus == .IN_PROGRESS && !$0.isTemporary && !$0.isHabitGoal }
        for g in active {
            guard let end = g.goalCategoryEndDate else { continue }
            let left = KST.diffDays(today, end)
            let title = goalName(g)
            for offset in 0..<3 {
                let d = KST.addDays(today, offset)
                let l = left - offset
                if let line = countdownLine(goal: title, left: l, start: g.goalCategoryStartDate, end: end, day: d) {
                    add(.countdown, day: d, minutes: 9 * 60, key: "\(g.goalCategoryId)",
                        title: line.title, body: line.body, route: .goal(g.goalCategoryId))
                }
            }
        }
        // 시작한 이유 — 수 · 토 19:30 에 목표 하나씩 돌아가며 (이유를 적어 둔 목표 먼저)
        let whyGoals = active.sorted { ($0.motive?.isEmpty == false ? 0 : 1, $0.goalCategoryId) < ($1.motive?.isEmpty == false ? 0 : 1, $1.goalCategoryId) }
        if !whyGoals.isEmpty {
            for offset in 0..<3 {
                let d = KST.addDays(today, offset)
                let wd = KST.weekdayIndex(d)
                guard wd == 2 || wd == 5 else { continue }
                let pick = whyGoals[(KST.diffDays("2026-01-01", d) / 3) % whyGoals.count]
                let line = whyLine(pick, day: d)
                add(.why, day: d, minutes: 19 * 60 + 30, key: "\(pick.goalCategoryId)",
                    title: line.title, body: line.body, route: .goal(pick.goalCategoryId))
            }
        }

        // ⑧ 다시 시작 — 지금부터 이틀 · 닷새 동안 안 들어오면 (앱을 열면 다시 뒤로 밀려요)
        add(.comeback, day: KST.addDays(today, 2), minutes: 20 * 60, key: "2",
            title: "완벽하지 않아도 괜찮아요",
            body: "다시 시작하면 돼요. 오늘은 10분짜리 하나만 해볼까요?",
            route: .calendar(date: KST.addDays(today, 2)))
        add(.comeback, day: KST.addDays(today, 5), minutes: 20 * 60, key: "5",
            title: "쉬어 가도 괜찮아요",
            body: "돌아오면 밀린 일은 같이 정리해 줄게요. 다시 짜기만 하면 돼요.",
            route: .calendarAi("며칠 쉬어서 밀린 일정이 있어요. 무리하지 않게 다시 짜줘"))

        return limitPerDay(out)
    }

    // MARK: - 문구

    /// 끝이 있다는 걸 알려줘요. 얼마 안 남았으면 "해방까지 n일", 길면 다른 단위(남은 주말 · 지난 기간 %)로.
    static func countdownLine(goal: String, left: Int, start: String?, end: String, day: String) -> (title: String, body: String)? {
        switch left {
        case 0: return ("오늘이 '\(goal)' 마지막 날이에요 🏁", "여기까지 온 것만으로도 대단해요. 마지막 하나만 해내면 해방이에요.")
        case 1: return ("내일이면 해방! 🎉", "'\(goal)' 마지막 하루 전이에요. 오늘 할 것만 끝내면 돼요.")
        case 3: return ("'\(goal)' 해방까지 3일", "끝이 보여요. 남은 사흘은 꼭 해야 할 것만 해도 충분해요.")
        case 7: return ("'\(goal)' 끝까지 일주일", "마지막 스퍼트예요. 이번 주만 버티면 돼요.")
        default:
            // 긴 목표: 월요일마다 한 번, 남은 주말 수 + 지난 기간 %
            guard left > 14, KST.weekdayIndex(day) == 0 else { return nil }
            let weekends = max(1, left / 7)
            var body = "주말 \(weekends)번만 더 보내면 끝나요."
            if let start, KST.diffDays(start, end) > 0 {
                let total = KST.diffDays(start, end)
                let passed = max(0, min(total, KST.diffDays(start, day)))
                body += " 벌써 기간의 \(passed * 100 / total)%를 지나왔어요."
            }
            return ("'\(goal)' 끝까지 \(left)일", body)
        }
    }

    /// 시작한 이유 — 적어 둔 이유가 있으면 "n일 전의 내가 보낸 말", 없으면 이루고 난 나를 떠올리게
    static func whyLine(_ g: GoalCategory, day: String) -> (title: String, body: String) {
        let goal = goalName(g)
        if let motive = g.motive?.trimmingCharacters(in: .whitespacesAndNewlines), !motive.isEmpty {
            if let start = g.goalCategoryStartDate, KST.diffDays(start, day) > 0 {
                return ("\(KST.diffDays(start, day))일 전의 내가 보낸 말 💌", "“\(motive)” — '\(goal)'을(를) 시작한 이유예요.")
            }
            return ("'\(goal)'을(를) 시작한 이유 💌", "“\(motive)”")
        }
        if let target = g.targetValue, let metric = g.metricName, !metric.isEmpty {
            let unit = g.metricUnit ?? ""
            let v = target == target.rounded() ? String(Int(target)) : String(format: "%.1f", target)
            return ("이루고 난 나를 떠올려 봐요 ✨", "\(metric) \(v)\(unit)에 닿은 날, 무엇이 달라져 있을까요? 오늘 한 걸음이 거기로 가요.")
        }
        return ("이루고 난 나를 떠올려 봐요 ✨", "\(TimetableRules.eulReul("'\(goal)'")) 이루면 무엇이 달라질까요? 오늘 한 걸음이면 충분해요.")
    }

    /// 주간 돌아보기 첫 줄 — 지금까지 이번 주 숫자
    static func weekLine(_ week: [TimetableDay]) -> String {
        let total = week.reduce(0) { $0 + $1.stats.total }
        let done = week.reduce(0) { $0 + $1.stats.completed }
        guard total > 0 else { return "이번 주는 기록이 거의 없었어요. 괜찮아요, 다음 주는 하나부터." }
        return "이번 주 \(total)개 중 \(done)개를 했어요 (\(done * 100 / total)%)."
    }

    // MARK: - 데이터 읽기

    struct Slot {
        let task: TimeBlockTask
        let start: Int
        let end: Int
    }

    /// 시간을 정한 Task (그날 0시 기준 분, 시작 순)
    static func scheduled(_ day: TimetableDay) -> [Slot] {
        day.tasks
            .filter { $0.taskStatus != .DELETED && $0.taskStatus != .SKIPPED && $0.proposed != true }
            .compactMap { t -> Slot? in
                guard let s = t.plannedStartAt else { return nil }
                let start = KST.minutesFrom(day.date, s)
                let end = t.plannedEndAt.map { KST.minutesFrom(day.date, $0) } ?? (start + (t.plannedDurationMinutes ?? 30))
                return Slot(task: t, start: start, end: end)
            }
            .sorted { $0.start < $1.start }
    }

    static func wakeMinutes(_ day: TimetableDay) -> Int? {
        guard let w = day.sleep?.wake else { return nil }
        let m = KST.minutesFrom(day.date, w.end)
        return (0..<(14 * 60)).contains(m) ? m : nil
    }

    static func bedMinutes(_ day: TimetableDay) -> Int? {
        guard let b = day.sleep?.bed else { return nil }
        let m = KST.minutesFrom(day.date, b.start)
        return ((18 * 60)...(26 * 60)).contains(m) ? min(m, 24 * 60) : nil
    }

    private static func name(_ t: TimeBlockTask) -> String {
        "'\(TimetableRules.title(t.emoji, t.taskName))'"
    }

    private static func goalName(_ g: GoalCategory) -> String {
        TimetableRules.title(g.emoji, g.goalCategoryName)
    }

    /// 하루에 많아야 maxPerDay 개 — 우선순위 순으로 남겨요
    private static func limitPerDay(_ list: [NudgeEntry]) -> [NudgeEntry] {
        let rank = { (k: NudgeKind) in priority.firstIndex(of: k) ?? priority.count }
        let byDay = Dictionary(grouping: list) { KST.ymd(from: $0.date) }
        return byDay.values
            .flatMap { $0.sorted { rank($0.kind) < rank($1.kind) }.prefix(maxPerDay) }
            .sorted { $0.date < $1.date }
    }
}
