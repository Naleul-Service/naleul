//
//  NaleulWidget.swift
//  NaleulWidget

import WidgetKit
import SwiftUI

struct NaleulWidgetEntryView: View {
    let entry: NaleulEntry
    @Environment(\.widgetFamily) var family

    var body: some View {
        switch family {
        case .accessoryRectangular:
            LockScreenRectangularView(tasks: entry.dailyTasks)
        default:
            switch entry.displayType {
            case .daily:
                DailyWidgetView(tasks: entry.dailyTasks, entry: entry)
            case .week:
                WeeklyWidgetView(weeklyTasks: entry.weeklyTasks)
            case .month:
                MonthlyWidgetView(monthlyTasks: entry.monthlyTasks)
            case .taskList:
                TaskListWidgetView(entry: TaskListEntry(date: entry.date, tasks: entry.dailyTasks))
            }
        }
    }
}

struct NaleulWidget: Widget {
    let kind: String = "NaleulWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: NaleulWidgetProvider()) { entry in
            NaleulWidgetEntryView(entry: entry)
                .containerBackground(.white, for: .widget)
        }
        .configurationDisplayName("나를 위젯")
        .description("일정을 한눈에 확인하세요")
        .supportedFamilies([
            .systemLarge,          // ✅ .systemMedium 제거 — Large만 허용
            .accessoryRectangular
        ])
    }
}
