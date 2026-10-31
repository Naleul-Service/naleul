//
//  TaskListWidgetView.swift
//  NaleulWidget

import SwiftUI
import WidgetKit

struct TaskListWidgetView: View {
    let entry: TaskListEntry
    @Environment(\.widgetFamily) var family

    // ✅ 창(window) 크기 — Large는 9개, Medium은 기존처럼 4개
    private var maxVisible: Int {
        family == .systemLarge ? 8 : 4
    }

    private var sortedTasks: [WidgetTask] {
        entry.tasks.sorted { widgetSortKey($0) < widgetSortKey($1) }
    }

    private func widgetSortKey(_ task: WidgetTask) -> Int {
        if let actual = task.actual { return widgetUtcToKstMinutes(actual.actualStartAt) }
        return widgetUtcToKstMinutes(task.plannedStartAt)
    }

    // ✅ "현재/다음 할 일" 기준점: 완료 안 됐고 아직 안 끝난 첫 태스크. 없으면 마지막 항목을 기준으로.
    private var anchorIndex: Int {
        guard !sortedTasks.isEmpty else { return 0 }
        if let idx = sortedTasks.firstIndex(where: { task in
            guard task.actual == nil, let end = task.plannedEndAt else { return false }
            return widgetUtcToKstMinutes(end) > widgetCurrentKstMinutes()
        }) {
            return idx
        }
        return sortedTasks.count - 1
    }

    // ✅ 기준점을 중심으로 앞뒤를 채우되, 배열 끝에 닿으면 반대쪽으로 밀어서 항상 maxVisible개를 채움
    private var windowedTasks: (visible: [WidgetTask], hiddenCount: Int) {
        guard !sortedTasks.isEmpty else { return ([], 0) }
        let total = sortedTasks.count
        let windowSize = min(maxVisible, total)

        var start = anchorIndex - windowSize / 2
        var end = start + windowSize - 1

        if start < 0 {
            start = 0
            end = windowSize - 1
        }
        if end > total - 1 {
            end = total - 1
            start = max(0, end - windowSize + 1)
        }

        let visible = Array(sortedTasks[start...end])
        return (visible, total - visible.count)
    }

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            VStack(alignment: .leading, spacing: 0) {
                Text(todayLabel)
                    .textStyle(.h4, color: .primary600)
                    .padding(.horizontal, 12)
                    .padding(.top, 10)
                    .padding(.bottom, 4)

                let window = windowedTasks

                if window.visible.isEmpty {
                    Text("오늘 할 일이 없어요")
                        .font(.system(size: 12))
                        .foregroundColor(.gray300)
                        .padding(.horizontal, 12)
                        .padding(.top, 8)
                    Spacer(minLength: 0)
                } else {
                    VStack(spacing: 3) {
                        ForEach(window.visible) { task in
                            TaskListRow(task: task)
                        }
                    }
                    Spacer(minLength: 0)
                }
            }

            // ✅ "+N" — 위젯 우측 하단에 오버레이로 표시 (리스트 행이 아님)
            if windowedTasks.hiddenCount > 0 {
                Text("+\(windowedTasks.hiddenCount)")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundColor(.gray500)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(
                        Capsule().fill(Color.gray50)
                    )
                    .padding(10)
            }
        }
    }

    private var todayLabel: String {
        let fmt = DateFormatter()
        fmt.locale = Locale(identifier: "ko_KR")
        fmt.dateFormat = "yyyy.MM.dd(E)"
        return fmt.string(from: Date())
    }
}

// MARK: - TaskListRow
private struct TaskListRow: View {
    let task: WidgetTask

    private var isDone: Bool { task.actual != nil }
    private var isManual: Bool { task.sourceType == .manual || task.sourceType == nil }

    // ✅ 현재 KST 시각이 계획된 시작~종료 구간 안에 있고, 아직 완료 안 된 태스크인지
    private var isCurrentlyActive: Bool {
        guard !isDone,
              let start = task.plannedStartAt,
              let end = task.plannedEndAt else { return false }

        let nowMinutes = widgetCurrentKstMinutes()
        let startMinutes = widgetUtcToKstMinutes(start)
        let endMinutes = widgetUtcToKstMinutes(end)

        // 자정을 넘어가는 계획(예: 23:00~01:00)도 처리
        if endMinutes < startMinutes {
            return nowMinutes >= startMinutes || nowMinutes < endMinutes
        }
        return nowMinutes >= startMinutes && nowMinutes < endMinutes
    }

    private var sourceTypeIcon: String? {
        switch task.sourceType {
        case .routine: return "icon_routine"
        case .mission: return nil   // 미션 기능 종료 — 아이콘 없이 표시
        default: return nil
        }
    }

    private var timeLabel: String {
        guard let start = task.plannedStartAt, let end = task.plannedEndAt else { return "" }
        return "\(widgetKstTimeLabel(start)) - \(widgetKstTimeLabel(end))"
    }

    private var deepLinkURL: URL {
        URL(string: "naleul://task?taskId=\(task.taskId)") ?? URL(string: "naleul://")!
    }

    var body: some View {
        Link(destination: deepLinkURL) {
            HStack(spacing: 4) {
                WidgetCheckbox(isDone: isDone)   // ✅ 배경 밖으로 분리

                // ✅ 아이콘~시간까지만 배경 적용되는 블록
                HStack(spacing: 4) {
                    if isManual {
                        WidgetPriorityBadge(priority: task.taskPriority)
                    } else if let icon = sourceTypeIcon {
                        Image(icon)
                            .renderingMode(.template)
                            .resizable()
                            .scaledToFit()
                            .frame(width: 13, height: 13)
                            .foregroundColor(isDone ? .gray300 : .primary500)
                    }

                    Text(task.taskName)
                        .textStyle(.bodyMDMedium, color: .gray500)
                        .foregroundColor(isDone ? .gray300 : .gray900)
                        .strikethrough(isDone, color: .gray300)
                        .lineLimit(1)

                    Spacer(minLength: 4)

                    Text(timeLabel)
                        .textStyle(.bodySM, color: .gray300)
                        .foregroundColor(.gray400)
                }
                .padding(.vertical, 6)
                .padding(.horizontal, 4)
                .background(
                    RoundedRectangle(cornerRadius: 4)
                        .fill(isCurrentlyActive ? Color.primary50 : Color.clear)
                )
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

// ✅ 추가: 현재 시각을 KST 기준 "자정부터 몇 분 지났는지"로 반환
func widgetCurrentKstMinutes() -> Int {
    var cal = Calendar(identifier: .gregorian)
    cal.timeZone = TimeZone(identifier: "Asia/Seoul")!
    let now = Date()
    return cal.component(.hour, from: now) * 60 + cal.component(.minute, from: now)
}

// MARK: - WidgetPriorityBadge (PriorityBadgeView 대응)

private struct WidgetPriorityBadge: View {
    let priority: TaskPriority?

    // ✅ PriorityBadgeView와 동일한 실제 색상
    private var color: Color {
        switch priority {
        case .a: return Color(hex: "#FF6B6B")
        case .b: return Color(hex: "#FB923C")
        case .c: return Color(hex: "#FFE46A")
        case .d: return Color(hex: "#9AE470")
        case .e: return Color(hex: "#3BBBF6")
        case nil: return .gray300   // ✅ 옛날 캐시 데이터 등으로 값이 없을 때의 안전장치
        }
    }

    var body: some View {
        Text(priority?.rawValue ?? "")
            .font(.system(size: 9, weight: .semibold))
            .foregroundColor(.white)
            .frame(width: 15, height: 15)
            .background(Circle().fill(color))
    }
}

// MARK: - WidgetCheckbox

private struct WidgetCheckbox: View {
    let isDone: Bool

    var body: some View {
        Image(isDone ? "icon_check" : "icon_uncheck")
            .renderingMode(.template)
            .resizable()
            .scaledToFit()
            .foregroundColor(isDone ? Color(hex: "#4CAF6D") : .gray200)
            .frame(width: 20, height: 20)
    }
}
