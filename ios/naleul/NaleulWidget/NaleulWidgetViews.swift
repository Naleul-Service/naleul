//
//  NaleulWidgetViews.swift
//  NaleulWidget

import SwiftUI
import WidgetKit

// MARK: - Week Range

struct WidgetWeekRange {
    let monday: Date
    let sunday: Date
}

func widgetGetWeekRange(from date: Date) -> WidgetWeekRange {
    let cal = Calendar(identifier: .gregorian)
    let weekday = cal.component(.weekday, from: date)
    let daysFromMonday = weekday == 1 ? 6 : weekday - 2
    let monday = cal.date(byAdding: .day, value: -daysFromMonday, to: date) ?? date
    let sunday = cal.date(byAdding: .day, value: 6, to: monday) ?? date
    return WidgetWeekRange(monday: monday, sunday: sunday)
}

// MARK: - Daily

private let ROW_HEIGHT: CGFloat = 23

struct DailyWidgetView: View {
    let tasks: [WidgetTask]
    let entry: NaleulEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(todayLabel)
                    .textStyle(.h4, color: .primary600)
                Spacer()
                Text("일간 타임테이블")
                    .textStyle(.labelXS, color: .primary600)
            }

            HStack(alignment: .top, spacing: 8) {
                DailyWidgetColumn(hourRange: 0..<12, positionedByHour: positionedByHour)
                DailyWidgetColumn(hourRange: 12..<24, positionedByHour: positionedByHour)
            }
        }
        .padding(10)
    }

    // ✅ 수정 — 본체 앱의 splitByHour와 동일하게, 여러 시간(hour)에 걸친 Task를
    //    각 hour 버킷 전부에 나눠서 등록하도록 변경.
    //    기존엔 시작 hour 하나에만 등록되고 60분으로 clamp되어,
    //    2시간 이상 걸치는 일정은 첫 시간대 외에는 전부 사라졌었음.
    private var positionedByHour: [Int: [WidgetPositionedTask]] {
        var map: [Int: [WidgetPositionedTask]] = [:]
        let todayKey = widgetDateKey(Date())

        for task in tasks {
            let isDone = task.actual != nil
            if isDone, let actual = task.actual {
                widgetSplitByHour(
                    task: task,
                    startIso: actual.actualStartAt,
                    endIso: actual.actualEndAt,
                    isDone: true,
                    map: &map,
                    viewDateKey: todayKey
                )
            } else if let start = task.plannedStartAt, let end = task.plannedEndAt {
                widgetSplitByHour(
                    task: task,
                    startIso: start,
                    endIso: end,
                    isDone: false,
                    map: &map,
                    viewDateKey: todayKey
                )
            }
        }
        return map
    }

    private var todayLabel: String {
        let fmt = DateFormatter()
        fmt.locale = Locale(identifier: "ko_KR")
        fmt.dateFormat = "M월 d일 (E)"
        return fmt.string(from: Date())
    }
}

// ✅ HourSlot의 PositionedTask<T>와 같은 역할을 하는 위젯 전용 타입
//    (id에 hour를 포함시켜, 같은 Task가 여러 hour에 걸쳐 등록될 때 id 충돌이 안 나도록 함)
private struct WidgetPositionedTask: Identifiable {
    let id: String
    let task: WidgetTask
    let isDone: Bool
    let colStart: Int   // 0~5
    let colSpan: Int    // 1~6
}

// ✅ 신규 추가 — TimeTableUtils.swift의 splitByHour를 위젯용(10분=1칸, colStart/colSpan)으로 이식.
//    본체 로직과 동일하게: 시작~종료 사이의 모든 hour를 순회하며 각 hour마다
//    그 hour에 걸치는 구간만큼 colStart/colSpan을 계산해 등록한다.
private func widgetSplitByHour(
    task: WidgetTask,
    startIso: String,
    endIso: String,
    isDone: Bool,
    map: inout [Int: [WidgetPositionedTask]],
    viewDateKey: String
) {
    let startMin = widgetUtcToKstMinutes(startIso)
    var endMin = widgetUtcToKstMinutes(endIso)

    // 시작 시각의 날짜가 오늘이 아니면(자정을 넘겨 시작된 일정 등) 0분부터 시작한 것으로 취급
    let startDateKey = widgetIsoToDateKey(startIso)
    let isOvernight = startDateKey != viewDateKey
    let adjustedStartMin = isOvernight ? 0 : startMin

    // 종료가 시작보다 앞서면(자정을 넘어가는 일정) 24시간을 더해 넘어가는 구간까지 계산
    if endMin <= adjustedStartMin { endMin += 24 * 60 }
    let clampedEnd = min(endMin, 24 * 60)

    let startHour = adjustedStartMin / 60
    let endHour = clampedEnd % 60 == 0 ? clampedEnd / 60 - 1 : clampedEnd / 60

    guard startHour <= endHour, startHour < 24 else { return }

    for hour in startHour...min(endHour, 23) {
        let slotStart = hour * 60
        let slotEnd = slotStart + 60
        let segStart = max(adjustedStartMin, slotStart)
        let segEnd = min(clampedEnd, slotEnd)
        guard segEnd > segStart else { continue }

        // 10분 = 1칸(6칸/시간) 기준으로 colStart/colSpan 계산
        let colStart = min(5, max(0, (segStart - slotStart) / 10))
        let colSpanRaw = Int(ceil(Double(segEnd - segStart) / 10.0))
        let colSpan = max(1, min(colSpanRaw, 6 - colStart))

        let positioned = WidgetPositionedTask(
            id: "\(task.taskId)-\(isDone)-\(hour)",   // ✅ hour 포함해 ForEach id 충돌 방지
            task: task,
            isDone: isDone,
            colStart: colStart,
            colSpan: colSpan
        )
        map[hour, default: []].append(positioned)
    }
}

// ✅ 신규 추가 — ISO(naive KST) 문자열에서 "yyyy-MM-dd" 부분만 뽑아 widgetDateKey와 비교 가능하게 함
private func widgetIsoToDateKey(_ isoString: String) -> String {
    let inFmt = DateFormatter()
    inFmt.dateFormat = "yyyy-MM-dd'T'HH:mm:ss"
    inFmt.timeZone = TimeZone(identifier: "Asia/Seoul")
    guard let date = inFmt.date(from: isoString) else { return isoString }

    let outFmt = DateFormatter()
    outFmt.dateFormat = "yyyy-MM-dd"
    outFmt.timeZone = TimeZone(identifier: "Asia/Seoul")
    return outFmt.string(from: date)
}

private struct DailyWidgetColumn: View {
    let hourRange: Range<Int>
    let positionedByHour: [Int: [WidgetPositionedTask]]

    var body: some View {
        VStack(spacing: 2) {
            ForEach(Array(hourRange), id: \.self) { hour in
                HStack(spacing: 4) {
                    Text(String(format: "%02d:00", hour))
                        .font(.system(size: 10))
                        .foregroundColor(.gray300)
                        .frame(width: 34, alignment: .leading)

                    WidgetSlotGrid(items: positionedByHour[hour] ?? []) { positioned in
                        WidgetTaskChip(
                            name: positioned.task.taskName,
                            goalColorCode: positioned.task.goalCategoryColorCode,
                            generalColorCode: positioned.task.generalCategoryColorCode,
                            isFilled: positioned.isDone,
                            sourceType: positioned.task.sourceType
                        )
                    }
                }
                .frame(height: ROW_HEIGHT)
            }
        }
        .frame(maxWidth: .infinity)
    }
}

// MARK: - SlotGrid (colStart/colSpan 기반 배치)

private struct WidgetSlotGrid<BlockView: View>: View {
    let items: [WidgetPositionedTask]
    let blockBuilder: (WidgetPositionedTask) -> BlockView

    var body: some View {
        GeometryReader { geo in
            let colWidth = geo.size.width / 6

            ZStack(alignment: .topLeading) {
                // 그리드 배경
                HStack(spacing: 0) {
                    ForEach(0..<6, id: \.self) { col in
                        Rectangle()
                            .fill(Color.clear)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                            .overlay(alignment: .trailing) {
                                if col < 5 {
                                    Rectangle()
                                        .fill(Color.gray.opacity(0.15))
                                        .frame(width: 0.5)
                                }
                            }
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .overlay(
                    RoundedRectangle(cornerRadius: 2)
                        .strokeBorder(Color.gray.opacity(0.2), lineWidth: 0.5)
                )

                // colStart/colSpan에 맞춰 정확한 위치·너비로 배치.
                // 2026-10: 같은 시간에 겹치는 Task 는 위·아래 두 줄로 나눠요 (겹쳐 쓰여 글자가 안 보이던 문제).
                // 세 번째부터는 숨기고 오른쪽 끝에 "+N" 으로 알려줘요.
                let layout = widgetLanes(items)
                let laneHeight = geo.size.height / CGFloat(layout.laneCount)
                ForEach(layout.placed) { placed in
                    blockBuilder(placed.item)
                        .frame(width: colWidth * CGFloat(placed.item.colSpan), height: laneHeight)
                        .offset(x: colWidth * CGFloat(placed.item.colStart), y: laneHeight * CGFloat(placed.lane))
                }
                if layout.hidden > 0 {
                    Text("+\(layout.hidden)")
                        .font(.system(size: 8, weight: .bold))
                        .foregroundColor(.gray500)
                        .padding(.horizontal, 2)
                        .background(RoundedRectangle(cornerRadius: 2).fill(Color.white.opacity(0.9)))
                        .frame(width: geo.size.width, height: geo.size.height, alignment: .bottomTrailing)
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .clipped()
    }
}

/// 한 시간 칸 안에서 겹치는 Task 를 줄(lane)에 나눠요 — 최대 2줄, 나머지는 hidden 으로 셈
private struct WidgetLanePlacement: Identifiable {
    let item: WidgetPositionedTask
    let lane: Int
    var id: String { item.id }
}

private func widgetLanes(_ items: [WidgetPositionedTask]) -> (placed: [WidgetLanePlacement], laneCount: Int, hidden: Int) {
    let maxLanes = 2
    var laneEnds: [Int] = []          // 줄마다 마지막 블록이 끝나는 칸
    var placed: [WidgetLanePlacement] = []
    var hidden = 0
    for item in items.sorted(by: { ($0.colStart, -$0.colSpan) < ($1.colStart, -$1.colSpan) }) {
        if let lane = laneEnds.firstIndex(where: { $0 <= item.colStart }) {
            laneEnds[lane] = item.colStart + item.colSpan
            placed.append(WidgetLanePlacement(item: item, lane: lane))
        } else if laneEnds.count < maxLanes {
            laneEnds.append(item.colStart + item.colSpan)
            placed.append(WidgetLanePlacement(item: item, lane: laneEnds.count - 1))
        } else {
            hidden += 1
        }
    }
    return (placed, max(1, laneEnds.count), hidden)
}

// 계획/완료를 구분하기 위한 병합 모델
private struct MergedWidgetTask: Identifiable {
    let task: WidgetTask
    let isDone: Bool
    var id: String { "\(task.taskId)-\(isDone)" }
}

// MARK: - TaskChip
struct WidgetTaskChip: View {
    let name: String
    let goalColorCode: String?
    let generalColorCode: String?
    let isFilled: Bool
    let sourceType: TaskSourceType?

    private var goalColor: Color { Color(hex: goalColorCode ?? "#1E7A90") }
    private var generalColor: Color { Color(hex: generalColorCode ?? "#1E7A90") }
    private var isMission: Bool { sourceType == .mission }

    private var sourceTypeIcon: String? {
        guard isFilled else { return nil }
        switch sourceType {
        case .routine: return "icon_routine"
        case .mission: return nil   // 미션 기능 종료 — 아이콘 없이 표시
        default: return nil
        }
    }

    var body: some View {
        HStack(spacing: 0) {
            // 왼쪽 막대 자리는 그대로 비워두되(레이아웃 유지), 실제 색은 아래 overlay에서 그림
            if !isMission {
                Color.clear.frame(width: 3)
            }

            Text(name)
                .font(.system(size: 10, weight: .medium))
                .foregroundColor(.gray900)
                .lineLimit(1)
                .minimumScaleFactor(0.75)   // 같은 시간 두 줄로 나뉘면 칸이 낮아져요
                .padding(.leading, isMission ? 4 : 2)
                .frame(maxWidth: .infinity, alignment: .leading)

            if let icon = sourceTypeIcon {
                Image(icon)
                    .renderingMode(.template)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 10, height: 10)
                    .foregroundColor(.gray500)
                    .padding(.trailing, 3)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(
            RoundedRectangle(cornerRadius: 4)
                .fill(isFilled ? generalColor.opacity(0.2) : Color.clear)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 4)
                .strokeBorder(generalColor, lineWidth: 0.5)
        )
        .overlay(alignment: .leading) {
            if !isMission {
                Rectangle()
                    .fill(goalColor)
                    .frame(width: 3)
                    .clipShape(.rect(topLeadingRadius: 4, bottomLeadingRadius: 4))
            }
        }
        .padding(.horizontal, 1)
    }
}

// MARK: - Weekly
private let WIDGET_TOTAL_MINUTES: CGFloat = 24 * 60
private let WIDGET_HOUR_HEIGHT: CGFloat = 12
private let WIDGET_TOTAL_HEIGHT: CGFloat = WIDGET_HOUR_HEIGHT * 24
private let WIDGET_TIME_LABEL_WIDTH: CGFloat = 12
private let WIDGET_DAY_HEADER_HEIGHT: CGFloat = 24   // 요일 헤더와 시간 레이블이 공유하는 단일 기준값

struct WeeklyWidgetView: View {
    let weeklyTasks: [String: [WidgetTask]]

    private let dayOrder = ["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY"]
    private let dayLabels = ["월","화","수","목","금","토","일"]

    private var weekRange: WidgetWeekRange { widgetGetWeekRange(from: Date()) }

    private var weekHeaderLabel: String {
        let cal = Calendar(identifier: .gregorian)
        let month = cal.component(.month, from: weekRange.monday)
        let day = cal.component(.day, from: weekRange.monday)
        let weekOfMonth = (day - 1) / 7 + 1
        return "\(String(month))월 \(String(weekOfMonth))주차"
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Text(weekHeaderLabel)
                    .textStyle(.h4, color: .primary600)

                Spacer()

                Text("주간 타임테이블")
                    .textStyle(.labelXS, color: .primary600)
            }
            .padding(.horizontal, 8)
            .padding(.top, 8)
            .padding(.bottom, 4)

            HStack(alignment: .top, spacing: 0) {
                // 시간 레이블 — offset 계산 대신 실제 행으로 쌓는 방식
                VStack(spacing: 0) {
                    Color.clear.frame(height: WIDGET_DAY_HEADER_HEIGHT)   // 요일 헤더와 같은 높이만큼 공백

                    ForEach(0..<24, id: \.self) { h in
                        Text("\(h)")
                            .font(.system(size: 5))
                            .foregroundColor(.gray300)
                            .frame(width: WIDGET_TIME_LABEL_WIDTH, height: WIDGET_HOUR_HEIGHT, alignment: .top)
                    }
                }
                .frame(width: WIDGET_TIME_LABEL_WIDTH)

                HStack(spacing: 0) {
                    ForEach(Array(dayOrder.enumerated()), id: \.offset) { i, day in
                        WeeklyWidgetDayColumn(
                            label: dayLabels[i],
                            dateLabel: dayDateLabel(index: i),
                            tasks: weeklyTasks[day] ?? [],
                            dayIndex: i,
                            allDayTasks: weeklyTasks
                        )
                    }
                }
            }
            .padding(.horizontal, 8)
            .padding(.bottom, 8)
        }
    }

    private func dayDateLabel(index: Int) -> String {
        let cal = Calendar(identifier: .gregorian)
        guard let date = cal.date(byAdding: .day, value: index, to: weekRange.monday) else { return "" }
        let fmt = DateFormatter()
        fmt.dateFormat = "MM/dd"
        return fmt.string(from: date)
    }
}

// MARK: - WeeklyWidgetDayColumn

private struct WeeklyWidgetDayColumn: View {
    let label: String
    let dateLabel: String
    let tasks: [WidgetTask]
    let dayIndex: Int
    let allDayTasks: [String: [WidgetTask]]

    private let dayOrder = ["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY"]

    private var resolvedTasks: [WidgetTask] {
        var result = tasks
        guard dayIndex > 0 else { return result }
        let existingIds = Set(tasks.map { $0.taskId })

        for prevIndex in 0..<dayIndex {
            let prevDay = dayOrder[prevIndex]
            let prevTasks = allDayTasks[prevDay] ?? []
            let overflow = prevTasks.filter { task in
                guard !existingIds.contains(task.taskId) else { return false }
                let endStr = task.actual?.actualEndAt ?? task.plannedEndAt
                let startStr = task.actual?.actualStartAt ?? task.plannedStartAt
                let endMin = widgetUtcToKstMinutes(endStr)
                let startMin = widgetUtcToKstMinutes(startStr)
                return endMin < startMin && endMin > 0
            }
            result += overflow
        }
        return result
    }

    var body: some View {
        VStack(spacing: 0) {
            VStack(spacing: 0) {
                Text(label)
                    .font(.system(size: 8, weight: .medium))
                    .foregroundColor(.gray500)
                Text(dateLabel)
                    .font(.system(size: 8))
                    .foregroundColor(.gray400)
            }
            .frame(maxWidth: .infinity)
            .frame(height: WIDGET_DAY_HEADER_HEIGHT)
            .background(Color.gray50)

            ZStack(alignment: .topLeading) {
                weekTimelineBackground
                ForEach(resolvedTasks) { task in
                    weekTaskBlock(task)
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: WIDGET_TOTAL_HEIGHT)
            .clipped()
        }
    }

    // 30분 점선 제거 — 정시 실선만 남김
    private var weekTimelineBackground: some View {
        VStack(spacing: 0) {
            ForEach(0..<24, id: \.self) { _ in
                Rectangle()
                    .fill(Color.gray200)
                    .frame(height: 0.5)
                    .frame(height: WIDGET_HOUR_HEIGHT, alignment: .top)
            }
            Rectangle().fill(Color.gray200).frame(height: 0.5)
        }
        .overlay(alignment: .leading) {
            Rectangle().fill(Color.gray200).frame(width: 0.5)
        }
        .overlay(alignment: .trailing) {
            Rectangle().fill(Color.gray200).frame(width: 0.5)
        }
    }

    private func clampedMinutes(task: WidgetTask) -> (start: Int, end: Int) {
        let isActual = task.actual != nil
        let rawStart = isActual
            ? widgetUtcToKstMinutes(task.actual!.actualStartAt)
            : widgetUtcToKstMinutes(task.plannedStartAt)
        let rawEnd = isActual
            ? widgetUtcToKstMinutes(task.actual!.actualEndAt)
            : widgetUtcToKstMinutes(task.plannedEndAt)

        if rawEnd < rawStart {
            return rawStart > rawEnd ? (rawStart, Int(WIDGET_TOTAL_MINUTES)) : (0, rawEnd)
        }
        return (rawStart, rawEnd)
    }

    @ViewBuilder
    private func weekTaskBlock(_ task: WidgetTask) -> some View {
        let isActual = task.actual != nil
        let clamped = clampedMinutes(task: task)
        let top = CGFloat(clamped.start) / WIDGET_TOTAL_MINUTES * WIDGET_TOTAL_HEIGHT
        let duration = max(clamped.end - clamped.start, 15)
        let height = max(CGFloat(duration) / WIDGET_TOTAL_MINUTES * WIDGET_TOTAL_HEIGHT, 4)

        WidgetWeekTaskBlock(
            taskName: task.taskName,
            goalColor: task.goalCategoryColorCode,
            generalColor: task.generalCategoryColorCode,
            sourceType: task.sourceType,
            isActual: isActual,
            height: height
        )
        .offset(y: top)
    }
}

// MARK: - WidgetWeekTaskBlock

private struct WidgetWeekTaskBlock: View {
    let taskName: String
    let goalColor: String?
    let generalColor: String?
    let sourceType: TaskSourceType?
    let isActual: Bool
    let height: CGFloat

    private var genColor: Color { Color(hex: generalColor ?? "#1E7A90") }
    private var goalClr: Color { Color(hex: goalColor ?? "#1E7A90") }
    private var isMission: Bool { sourceType == .mission }
    private var hasSpaceForIcon: Bool { height >= 14 }

    private var sourceTypeIcon: String? {
        guard isActual, hasSpaceForIcon else { return nil }
        switch sourceType {
        case .routine: return "icon_routine"
        case .mission: return nil   // 미션 기능 종료 — 아이콘 없이 표시
        default: return nil
        }
    }

    var body: some View {
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 3)
                .fill(isActual ? genColor.opacity(0.15) : Color.clear)
                .overlay {
                    if !isActual {
                        RoundedRectangle(cornerRadius: 3)
                            .strokeBorder(genColor, lineWidth: 0.5)
                    }
                }
                .overlay(alignment: .leading) {
                    if !isMission {
                        Rectangle()
                            .fill(goalClr)
                            .frame(width: 2)
                            .clipShape(.rect(topLeadingRadius: 3, bottomLeadingRadius: 3))
                    }
                }

            Text(taskName)
                .font(.system(size: 5.5))
                .foregroundColor(.gray900)
                .lineLimit(1)
                .padding(.leading, isMission ? 2 : 3)
                .padding(.top, 1)

            if let icon = sourceTypeIcon {
                Image(icon)
                    .renderingMode(.template)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 6, height: 6)
                    .foregroundColor(.gray500)
                    .padding(.trailing, 1)
                    .padding(.bottom, 1)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
            }
        }
        .frame(height: height)
        .clipped()
    }
}

// MARK: - Monthly

struct MonthlyWidgetView: View {
    let monthlyTasks: [String: [WidgetTask]]

    private let dayLabels = ["일","월","화","수","목","금","토"]
    private var year: Int { Calendar.current.component(.year, from: Date()) }
    private var month: Int { Calendar.current.component(.month, from: Date()) }

    private var days: [Date?] {
        var comps = DateComponents()
        comps.year = year
        comps.month = month
        comps.day = 1
        let cal = Calendar(identifier: .gregorian)
        guard let firstDay = cal.date(from: comps) else { return [] }

        let lastDay = cal.date(byAdding: DateComponents(month: 1, day: -1), to: firstDay) ?? firstDay
        let startOffset = cal.component(.weekday, from: firstDay) - 1
        let endOffset = 6 - cal.component(.weekday, from: lastDay)
        let daysInMonth = cal.component(.day, from: lastDay)

        var result: [Date?] = []
        for i in stride(from: startOffset, through: 1, by: -1) {
            result.append(cal.date(byAdding: .day, value: -i, to: firstDay))
        }
        for d in 1...daysInMonth {
            comps.day = d
            result.append(cal.date(from: comps))
        }
        if endOffset > 0 {
            for i in 1...endOffset {
                result.append(cal.date(byAdding: .day, value: i, to: lastDay))
            }
        }
        return result
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Text("\(String(year))년 \(String(month))월")
                    .textStyle(.h4, color: .primary600)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 8)
                    .padding(.top, 8)
                    .padding(.bottom, 4)

                Spacer()

                Text("월간 타임테이블")
                    .textStyle(.labelXS, color: .primary600)
            }

            HStack(spacing: 0) {
                ForEach(dayLabels, id: \.self) { label in
                    Text(label)
                        .font(.system(size: 8, weight: .medium))
                        .foregroundColor(.gray500)
                        .frame(maxWidth: .infinity)
                        .frame(height: 14)
                        .background(Color.gray50)
                }
            }

            LazyVGrid(
                columns: Array(repeating: GridItem(.flexible(), spacing: 0), count: 7),
                spacing: 0
            ) {
                ForEach(days.indices, id: \.self) { i in
                    if let date = days[i] {
                        MonthlyWidgetCell(
                            date: date,
                            tasks: monthlyTasks[widgetDateKey(date)] ?? [],
                            isCurrentMonth: Calendar.current.component(.month, from: date) == month,
                            isToday: Calendar.current.isDateInToday(date)
                        )
                    } else {
                        Color.clear.frame(height: 50)
                    }
                }
            }
        }
        .padding(.bottom, 8)
    }
}

// MARK: - MonthlyWidgetCell

private struct MonthlyWidgetCell: View {
    let date: Date
    let tasks: [WidgetTask]
    let isCurrentMonth: Bool
    let isToday: Bool

    private let maxVisible = 3
    private var dayNumber: Int { Calendar.current.component(.day, from: date) }

    // 루틴/미션 제외, 일반(MANUAL) 태스크만
    private var generalTasks: [WidgetTask] {
        tasks.filter { $0.isGeneralTask }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 1) {
            Text("\(dayNumber)")
                .font(.system(size: 8, weight: isToday ? .bold : .medium))
                .foregroundColor(isToday ? .white : .gray500)
                .frame(width: 14, height: 14)
                .background(isToday ? Color.primary400 : Color.clear)
                .clipShape(Circle())
                .frame(maxWidth: .infinity, alignment: .center)

            VStack(spacing: 1) {
                ForEach(generalTasks.prefix(maxVisible), id: \.taskId) { task in
                    MonthlyWidgetTaskChip(task: task)
                }
                if generalTasks.count > maxVisible {
                    Text("+\(generalTasks.count - maxVisible)")
                        .font(.system(size: 6))
                        .foregroundColor(.gray400)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.leading, 2)
                }
            }

            Spacer(minLength: 0)
        }
        .padding(2)
        .frame(maxWidth: .infinity)
        .frame(height: 50)
        .opacity(isCurrentMonth ? 1 : 0.3)
        .overlay(
            Rectangle().strokeBorder(Color.gray100, lineWidth: 0.5)
        )
    }
}

// MARK: - MonthlyWidgetTaskChip

private struct MonthlyWidgetTaskChip: View {
    let task: WidgetTask

    // TaskChip(mobile:true)과 동일 규칙: actual 있으면 완료(채움), 없으면 예정(테두리만)
    private var isDone: Bool { task.actual != nil }
    private var goalColor: Color { Color(hex: task.goalCategoryColorCode ?? "#1E7A90") }
    private var generalColor: Color { Color(hex: task.generalCategoryColorCode ?? "#1E7A90") }

    var body: some View {
        HStack(spacing: 0) {
            Rectangle()
                .fill(goalColor)
                .frame(width: 2)
                .clipShape(.rect(topLeadingRadius: 1, bottomLeadingRadius: 1))

            Text(task.taskName)
                .font(.system(size: 6, weight: .medium))
                .foregroundColor(.gray800)
                .lineLimit(1)
                .padding(.leading, 2)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .frame(height: 9)
        .background(isDone ? generalColor.opacity(0.2) : Color.clear)
        .overlay(
            RoundedRectangle(cornerRadius: 2)
                .strokeBorder(isDone ? Color.clear : generalColor, lineWidth: isDone ? 0 : 0.5)
        )
        .clipShape(.rect(topLeadingRadius: 1, bottomLeadingRadius: 1, bottomTrailingRadius: 2, topTrailingRadius: 2))
    }
}

// private 제거
func widgetDateKey(_ date: Date) -> String {
    let cal = Calendar.current
    return String(format: "%04d-%02d-%02d",
                  cal.component(.year, from: date),
                  cal.component(.month, from: date),
                  cal.component(.day, from: date))
}

