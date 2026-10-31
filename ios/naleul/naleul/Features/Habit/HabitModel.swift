// Features/Habit/HabitModel.swift
//
// 기본 생활 습관 화면(HabitGoalBody)의 데이터 — 웹 HabitView 의 useHabitHome + HabitHeatmap 의 useHabitHeatmap.
//
// - home: 고른 날(day)의 습관 목록 (오늘이면 date 없이 불러요)
// - heatmap: 습관별 잔디 (from ~ 오늘, 서버 최대 400일)
// 체크는 누르자마자 화면에 반영하고(낙관적 업데이트), 실패하면 되돌려요.

import Foundation
import Observation

@MainActor
@Observable
final class HabitModel {
    /// 지금 보고 있는 날 (오늘 또는 지난 날)
    var day: String
    var phase: LoadPhase<HabitHome> = .loading
    var heatmap: HabitHeatmap?
    /// 잔디를 불러온 from (같으면 다시 안 불러요 — 바뀐 게 있으면 .habits 로 다시)
    var heatmapFrom: String?
    var heatmapFailed = false
    /// 체크 중인 습관 (두 번 연속 눌림 막기)
    var togglingIds: Set<Int> = []
    /// 추천 습관을 추가하는 중
    var addingSuggestion = false

    /// 미리보기용 (네트워크 안 씀)
    var isPreview = false

    // 앰플리튜드 — 화면당 1회 보내는 이벤트 기록
    private var pageviewSent = false
    private var allDoneSeen: Set<String> = []

    /// 오늘에서 며칠 전인지 (오늘 = 0)
    var daysAgo: Int { GD.daysAgo(day) ?? 0 }

    func track(_ event: String, _ props: EventProps = [:]) {
        if isPreview { return }
        Analytics.track(event, props)
    }

    init(day: String = KST.today()) {
        self.day = day
    }

    var home: HabitHome? { phase.value }
    var habits: [Habit] { home?.habits ?? [] }
    var today: String { KST.today() }
    var isToday: Bool { day == today }

    /// 그날 해야 하는 습관 (진행 중 · 그 요일)
    var due: [Habit] { habits.filter { $0.isActive && $0.scheduled } }
    /// 그날은 안 해도 되는 습관
    var off: [Habit] { habits.filter { $0.isActive && !$0.scheduled } }
    /// 쉬는 중
    var paused: [Habit] { habits.filter { $0.isPaused } }
    var doneCount: Int { due.filter { $0.checked }.count }

    // MARK: 불러오기

    func load(day: String) async {
        if isPreview { return }
        // 다른 날로 넘기면 그날 목록이 올 때까지 스피너
        if home?.date != day { phase = .loading }
        do {
            let isToday = day == KST.today()
            if let h = try await HabitAPI.home(date: isToday ? nil : day) {
                phase = .loaded(h)
                if !pageviewSent {
                    pageviewSent = true
                    track("habit.pageview", [
                        "goal_category_id": h.goalCategoryId, "habit_count": h.habits.count,
                        "due_today_count": due.count, "done_count": doneCount, "paused_count": paused.count,
                    ])
                }
            } else {
                phase = .failed("습관을 불러오지 못했어요")
                track("habit.load_fail", ["error_status": 404, "days_ago": daysAgo])
            }
        } catch is CancellationError {
            return
        } catch {
            if home == nil || home?.date != day {
                phase = .failed(errorMessage(error))
                track("habit.load_fail", ["error_status": (error as? APIError)?.httpStatus ?? 0, "days_ago": daysAgo])
            }
        }
    }

    /// 잔디 — from(포함) ~ 오늘
    func loadHeatmap(from: String) async {
        if isPreview { return }
        do {
            heatmap = try await HabitAPI.heatmap(from: from)
            heatmapFrom = from
            heatmapFailed = false
        } catch is CancellationError {
            return
        } catch {
            if heatmap == nil { heatmapFailed = true }
        }
    }

    // MARK: 체크

    /// 체크 · 체크 풀기 (낙관적 — 실패하면 되돌려요)
    func toggle(_ habit: Habit) async {
        guard !habit.isPaused, !togglingIds.contains(habit.habitId) else { return }
        guard case .loaded(let before) = phase else { return }
        let target = !habit.checked
        let d = day
        let action = target ? "complete" : "uncomplete"
        track("habit.check_toggle", [
            "habit_id": habit.habitId, "check_action": action, "days_ago": daysAgo,
            "is_due_day": habit.scheduled, "streak": habit.streak,
        ])
        apply(habitId: habit.habitId, checked: target)
        if target, !due.isEmpty, doneCount == due.count, allDoneSeen.insert(d).inserted {
            track("habit.all_done_view", ["days_ago": daysAgo, "due_today_count": due.count])
        }
        if isPreview { return }

        togglingIds.insert(habit.habitId)
        let ok: Void? = await Mutation.run(invalidate: [.habits, .home], successMessage: nil) {
            try await HabitAPI.setChecked(habitId: habit.habitId, day: d, checked: target)
        }
        togglingIds.remove(habit.habitId)
        track("habit.check_result", GD.result(ok != nil).merging(["habit_id": habit.habitId, "check_action": action]) { $1 })
        if ok == nil, home?.date == before.date {
            // 실패: 그 습관만 원래대로
            if let orig = before.habits.first(where: { $0.habitId == habit.habitId }) {
                replace(orig)
            }
        }
    }

    private func apply(habitId: Int, checked: Bool) {
        guard var h = home else { return }
        guard let i = h.habits.firstIndex(where: { $0.habitId == habitId }) else { return }
        var x = h.habits[i]
        x.checked = checked
        if checked {
            x.state = .DONE
        } else if x.scheduled {
            x.state = (h.date == KST.today()) ? .TODO : .MISSED
        }
        if let last = x.recent7.indices.last, x.recent7[last].date == h.date {
            x.recent7[last].state = x.state
        }
        h.habits[i] = x
        h.doneCount = h.habits.filter { $0.isActive && $0.scheduled && $0.checked }.count
        phase = .loaded(h)
    }

    private func replace(_ habit: Habit) {
        guard var h = home else { return }
        guard let i = h.habits.firstIndex(where: { $0.habitId == habit.habitId }) else { return }
        h.habits[i] = habit
        h.doneCount = h.habits.filter { $0.isActive && $0.scheduled && $0.checked }.count
        phase = .loaded(h)
    }

    // MARK: 추천 습관 바로 추가

    func add(_ s: HabitSuggestion) async {
        guard !addingSuggestion, !isPreview else { return }
        addingSuggestion = true
        let input = HabitInput(name: s.name, emoji: s.emoji, repeatDays: s.repeatDays, remindTime: nil)
        let ok = await Mutation.run(invalidate: [.habits, .home, .timetable], successMessage: "'\(s.name)' 습관을 추가했어요.") {
            try await HabitAPI.create(input)
        }
        track("habit.result", GD.result(ok != nil).merging(["habit_action": "create"]) { $1 })
        addingSuggestion = false
    }

    // MARK: 미리보기

    static func preview(empty: Bool = false) -> HabitModel {
        let m = HabitModel()
        m.isPreview = true
        let home = empty ? HabitPreviewData.emptyHome : HabitPreviewData.home
        m.phase = .loaded(home)
        if !empty {
            m.heatmap = HabitPreviewData.heatmap
            m.heatmapFrom = HabitPreviewData.heatmap.from
        }
        return m
    }
}

// MARK: - 미리보기 샘플 (네트워크 없이)

enum HabitPreviewData {
    private static var today: String { KST.today() }

    private static func recent(_ states: [HabitDayState]) -> [HabitRecentDay] {
        let n = states.count
        return states.enumerated().map { i, s in HabitRecentDay(date: KST.addDays(today, i - (n - 1)), state: s) }
    }

    static var habits: [Habit] {
        [
            Habit(habitId: 1, name: "영양제 먹기", emoji: "💊", repeatDays: [], daily: true, remindTime: "08:30",
                  status: .ACTIVE, startDate: KST.addDays(today, -75), sortOrder: 0, scheduled: true, checked: true,
                  state: .DONE, streak: 12, rate30: 90,
                  recent7: recent([.DONE, .DONE, .MISSED, .DONE, .DONE, .DONE, .DONE])),
            Habit(habitId: 2, name: "물 2L 마시기", emoji: "💧", repeatDays: [], daily: true, remindTime: nil,
                  status: .ACTIVE, startDate: KST.addDays(today, -40), sortOrder: 1, scheduled: true, checked: false,
                  state: .TODO, streak: 3, rate30: 70,
                  recent7: recent([.MISSED, .DONE, .DONE, .MISSED, .DONE, .DONE, .TODO])),
            Habit(habitId: 3, name: "분리수거", emoji: "🗑️", repeatDays: [.WEDNESDAY, .SUNDAY], daily: false, remindTime: "21:00",
                  status: .ACTIVE, startDate: KST.addDays(today, -20), sortOrder: 2, scheduled: false, checked: false,
                  state: .OFF, streak: 2, rate30: 100,
                  recent7: recent([.OFF, .DONE, .OFF, .OFF, .OFF, .DONE, .OFF])),
            Habit(habitId: 4, name: "일기 쓰기", emoji: "📓", repeatDays: [], daily: true, remindTime: nil,
                  status: .PAUSED, startDate: KST.addDays(today, -50), sortOrder: 3, scheduled: false, checked: false,
                  state: .PAUSED, streak: 0, rate30: 40,
                  recent7: recent([.DONE, .MISSED, .PAUSED, .PAUSED, .PAUSED, .PAUSED, .PAUSED])),
        ]
    }

    static var home: HabitHome {
        let hs = habits
        let due = hs.filter { $0.isActive && $0.scheduled }
        return HabitHome(goalCategoryId: 9, goalName: "기본 생활 습관", emoji: "🌱", date: today, today: today,
                         scheduledCount: due.count, doneCount: due.filter { $0.checked }.count, habits: hs)
    }

    static var emptyHome: HabitHome {
        HabitHome(goalCategoryId: 9, goalName: "기본 생활 습관", emoji: "🌱", date: today, today: today,
                  scheduledCount: 0, doneCount: 0, habits: [])
    }

    /// 가장 먼저 시작한 습관의 그 달 1일부터 오늘까지
    static var heatmap: HabitHeatmap {
        let hs = habits
        let earliest = hs.map(\.startDate).min() ?? today
        let p = KST.parts(earliest)
        let from = KST.ymd(year: p.y, month: p.m, day: 1)
        let count = KST.diffDays(from, today) + 1
        var rows: [HabitHeatmap.Row] = []
        for h in hs {
            var cells: [HabitDayState] = []
            for i in 0..<max(count, 0) {
                let d = KST.addDays(from, i)
                if d < h.startDate {
                    cells.append(.BEFORE_START)
                } else if d == today {
                    cells.append(h.checked ? .DONE : (h.scheduled ? .TODO : (h.isPaused ? .PAUSED : .OFF)))
                } else if h.isPaused && KST.diffDays(d, today) <= 5 {
                    cells.append(.PAUSED)
                } else if !h.daily && !h.repeatDays.contains(JavaDayOfWeek.of(d)) {
                    cells.append(.OFF)
                } else {
                    cells.append((i * 7 + h.habitId * 3) % 5 == 0 ? .MISSED : .DONE)
                }
            }
            rows.append(HabitHeatmap.Row(habitId: h.habitId, name: h.name, emoji: h.emoji, status: h.status,
                                         streak: h.streak, rate: h.rate30,
                                         doneDays: cells.filter { $0 == .DONE }.count, cells: cells))
        }
        return HabitHeatmap(from: from, to: today, today: today, days: [], habits: rows)
    }

    /// 기본 생활 습관 목표 (goalMode = HABIT)
    static var goal: GoalCategory {
        let json = """
        {
          "goalCategoryId": 9, "goalCategoryName": "기본 생활 습관", "goalCategoryStatus": "IN_PROGRESS",
          "colorCode": "12B76A", "emoji": "🌱", "goalMode": "HABIT", "generalCategories": []
        }
        """
        // 미리보기 전용이라 실패하면 바로 알 수 있게 강제로 풀어요
        return try! JSONDecoder().decode(GoalCategory.self, from: Data(json.utf8))
    }

    /// 일회성 할 일 (병원 가기 · 친구 만나기)
    static var tasks: GoalTasksResponse {
        let json = """
        {
          "totalCount": 2, "todoCount": 1, "completedCount": 1,
          "tasks": [
            {"taskId": 901, "taskName": "치과 가기", "emoji": "🦷", "taskStatus": "TODO", "sourceType": "MANUAL",
             "goalCategoryId": 9, "goalColorCode": "12B76A", "date": "\(KST.addDays(today, 2))"},
            {"taskId": 902, "taskName": "친구 만나기", "taskStatus": "COMPLETED", "sourceType": "MANUAL",
             "goalCategoryId": 9, "goalColorCode": "12B76A", "date": "\(KST.addDays(today, -3))"}
          ]
        }
        """
        return try! JSONDecoder().decode(GoalTasksResponse.self, from: Data(json.utf8))
    }
}
