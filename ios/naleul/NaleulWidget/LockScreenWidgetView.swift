//
//  LockScreenWidgetView.swift
//  NaleulWidget

import SwiftUI

struct LockScreenRectangularView: View {
    let tasks: [WidgetTask]

    private var nowMin: Int {
        Calendar.current.component(.hour, from: Date()) * 60
            + Calendar.current.component(.minute, from: Date())
    }

    private var currentTask: WidgetTask? {
      tasks.first { task in
          guard let start = task.plannedStartAt, let end = task.plannedEndAt else { return false }
          let startMin = widgetUtcToKstMinutes(start)
          let endMin = widgetUtcToKstMinutes(end)
          return startMin <= nowMin && endMin > nowMin
      }
    }

    private var upcomingTasks: [WidgetTask] {
        tasks
            .filter { task in
                guard let start = task.plannedStartAt else { return false }
                return widgetUtcToKstMinutes(start) > nowMin
            }
            .prefix(2)
            .map { $0 }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            // 지금 하고 있는 일
            if let current = currentTask {
                HStack(spacing: 4) {
                    Image(systemName: "bolt.fill")
                        .font(.system(size: 8))
                    Text(current.taskName)
                        .font(.system(size: 11, weight: .semibold))
                        .lineLimit(1)
                }
            }

            // 다음 일정들
            ForEach(upcomingTasks) { task in
                // ✅ upcomingTasks가 이미 필터링했어도, 타입상 여전히 옵셔널이라 재확인 필요
                if let start = task.plannedStartAt {
                    let hour = widgetUtcToKstMinutes(start) / 60
                    let min = widgetUtcToKstMinutes(start) % 60
                    HStack(spacing: 4) {
                        Text(String(format: "%02d:%02d", hour, min))
                            .font(.system(size: 9))
                            .foregroundColor(.secondary)
                        Text(task.taskName)
                            .font(.system(size: 10))
                            .lineLimit(1)
                    }
                }
            }

            if currentTask == nil && upcomingTasks.isEmpty {
                Text("오늘 남은 일정이 없어요")
                    .font(.system(size: 10))
                    .foregroundColor(.secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
