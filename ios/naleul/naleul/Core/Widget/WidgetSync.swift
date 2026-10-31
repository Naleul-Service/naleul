// Core/Widget/WidgetSync.swift
//
// 홈 화면 위젯에 보여줄 일정을 앱이 채워요.
// 위젯은 Keychain 토큰을 못 읽어서 서버를 직접 부르지 않고, 앱이 App Group 에 저장한 걸 읽기만 해요.
//
// 언제 채우나요?
//  - 앱이 앞으로 올 때 (RootView scenePhase)
//  - 캘린더 데이터(.timetable)가 바뀔 때 (Task 완료 · 추가 · 시간 변경 …) — RootView 에서 구독
//  - 위젯 설정에서 스타일을 바꿀 때
// 로그아웃 · 탈퇴하면 비워요 (다른 사람 일정이 남지 않게)

import Foundation
import WidgetKit

@MainActor
enum WidgetSync {
    private static var running = false
    private static var lastRun: Date?

    /// 짧은 시간에 여러 번 불려도(연속 invalidate) 한 번만 — force 면 바로
    static func refresh(force: Bool = false) async {
        guard TokenManager.shared.accessToken != nil else { return }
        if running { return }
        if !force, let last = lastRun, Date().timeIntervalSince(last) < 5 { return }
        running = true
        defer { running = false; lastRun = Date() }

        let today = KST.today()
        let monday = KST.startOfWeek(today)
        let sunday = KST.addDays(monday, 6)

        // 이번 주 (일간 · 일정 태스크 · 주간 위젯이 같이 써요)
        if let week = try? await TimetableAPI.timetable(start: monday, end: sunday) {
            var weekly: [String: [WidgetTask]] = [:]
            for day in week.days {
                let idx = KST.weekdayIndex(day.date)   // 0 = 월
                guard dayKeys.indices.contains(idx) else { continue }
                weekly[dayKeys[idx]] = day.tasks.compactMap(toWidgetTask)
            }
            WidgetDataStore.saveWeeklyTasks(weekly)
            let todayTasks = week.days.first { $0.date == today }?.tasks.compactMap(toWidgetTask) ?? []
            WidgetDataStore.saveDailyTasks(todayTasks)
        }

        // 이번 달
        if let month = try? await TimetableAPI.monthly(year: KST.yearOf(today), month: KST.monthOf(today)) {
            var monthly: [String: [WidgetTask]] = [:]
            for day in month.days {
                monthly[day.date] = day.chips.map { chip in
                    WidgetTask(
                        taskId: chip.taskId,
                        taskName: chip.title,
                        plannedStartAt: nil,
                        plannedEndAt: nil,
                        goalCategoryColorCode: chip.colorCode,
                        generalCategoryColorCode: chip.colorCode,
                        actual: chip.completed ? WidgetTaskActual(actualStartAt: "", actualEndAt: "") : nil,
                        sourceType: .manual,   // 월간 칩은 서버가 루틴을 빼고 줘요
                        taskPriority: nil
                    )
                }
            }
            WidgetDataStore.saveMonthlyTasks(monthly)
        }

        WidgetDataStore.markUpdated()
        WidgetCenter.shared.reloadTimelines(ofKind: WidgetDataStore.widgetKind)
    }

    static func clear() {
        WidgetDataStore.clearAll()
        WidgetCenter.shared.reloadTimelines(ofKind: WidgetDataStore.widgetKind)
    }

    private static let dayKeys = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"]

    /// 시간이 정해진 Task 만 (삭제 · 건너뜀 · AI 미리보기 제외). 완료했는데 실제 시각이 없으면 계획 시각을 실제로 봐요
    private static func toWidgetTask(_ t: TimeBlockTask) -> WidgetTask? {
        guard t.taskStatus == .TODO || t.taskStatus == .COMPLETED,
              t.proposed != true,
              let start = t.plannedStartAt, let end = t.plannedEndAt else { return nil }
        let actual: WidgetTaskActual? = t.isCompleted
            ? WidgetTaskActual(actualStartAt: t.actualStartAt ?? start, actualEndAt: t.actualEndAt ?? end)
            : nil
        return WidgetTask(
            taskId: t.taskId,
            taskName: t.taskName,
            plannedStartAt: start,
            plannedEndAt: end,
            goalCategoryColorCode: t.goalColorCode,
            generalCategoryColorCode: t.goalColorCode,
            actual: actual,
            sourceType: WidgetTaskSourceType(rawValue: t.sourceType.rawValue) ?? .manual,
            taskPriority: t.taskPriority.flatMap { WidgetTaskPriority(rawValue: $0.rawValue) }
        )
    }
}
