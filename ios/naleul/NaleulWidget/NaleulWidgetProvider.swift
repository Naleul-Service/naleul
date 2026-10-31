//
//  NaleulWidgetProvider.swift
//  NaleulWidget
//
//  앱이 App Group 에 넣어둔 데이터만 읽어요 (네트워크 X).
//  앱이 켜지거나 일정이 바뀌면 앱(WidgetSync)이 새로 저장하고 타임라인을 다시 불러요.
//  자정이 지나면 "오늘" 기준이 바뀌니 자정 직후 한 번 더 그려요.

import WidgetKit
import Foundation

struct NaleulWidgetProvider: TimelineProvider {

    func placeholder(in context: Context) -> NaleulEntry {
        NaleulEntry(date: Date(), displayType: .daily, dailyTasks: [], weeklyTasks: [:], monthlyTasks: [:])
    }

    func getSnapshot(in context: Context, completion: @escaping (NaleulEntry) -> Void) {
        completion(makeEntry(at: Date()))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<NaleulEntry>) -> Void) {
        let now = Date()
        // 시간선 위치(현재 할 일)가 움직이니 30분마다 다시 그리고, 자정에도 한 번
        var entries: [NaleulEntry] = []
        for step in 0..<6 {
            if let d = Calendar.current.date(byAdding: .minute, value: step * 30, to: now) {
                entries.append(makeEntry(at: d))
            }
        }
        let nextMidnight = Calendar.current.nextDate(after: now, matching: DateComponents(hour: 0, minute: 1),
                                                     matchingPolicy: .nextTime) ?? now.addingTimeInterval(3600 * 3)
        let reload = min(nextMidnight, now.addingTimeInterval(3600 * 3))
        completion(Timeline(entries: entries, policy: .after(reload)))
    }

    private func makeEntry(at date: Date) -> NaleulEntry {
        NaleulEntry(
            date: date,
            displayType: WidgetDataStore.loadDisplayType(),
            dailyTasks: WidgetDataStore.loadDailyTasks(),
            weeklyTasks: WidgetDataStore.loadWeeklyTasks(),
            monthlyTasks: WidgetDataStore.loadMonthlyTasks()
        )
    }
}
