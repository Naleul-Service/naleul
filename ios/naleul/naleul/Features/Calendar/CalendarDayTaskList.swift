// Features/Calendar/CalendarDayTaskList.swift
//
// 일간 캘린더 아래 "오늘 할 일" 목록 (웹은 오른쪽, 아이폰은 격자 아래).
// TimeTable 은 "언제" 하는지를, 이 목록은 "무엇을 남겼는지"를 한눈에 보여줘요.
//  - 남은 일(시간순, 시간 미정은 아래) / 완료한 일
//  - 동그라미를 누르면 바로 완료 · 완료 취소, 줄을 누르면 TimeTable 과 같은 상세 시트
// 웹 원본: features/timetable/ui/DayTaskList.tsx

import SwiftUI

struct CalendarDayTaskList: View {
    let board: TimetableBoardModel
    let day: TimetableDay?
    let today: String

    var body: some View {
        if let day {
            content(day)
        }
    }

    /// 전날 밤부터 이어진 블록은 그날 칸에도 보이지만, 목록은 "이날 하기로 한 일"만 (계획 시작일 또는 시간 미정 날짜)
    static func items(of day: TimetableDay) -> [TimeBlockTask] {
        var seen = Set<Int>()
        var all: [TimeBlockTask] = []
        for t in day.tasks + day.unscheduledTasks {
            if seen.contains(t.taskId) || t.taskStatus == .SKIPPED || t.taskStatus == .DELETED { continue }
            seen.insert(t.taskId)
            let planDay = TimetableRules.blockDay(t) ?? t.date ?? t.scheduledDate
            let completedDay = t.completedAt.map { String($0.prefix(10)) }
            if planDay == nil || planDay == day.date || completedDay == day.date { all.append(t) }
        }
        // 그날 정렬용 시작 분 (시간 미정은 맨 뒤)
        func startOf(_ t: TimeBlockTask) -> Int {
            t.plannedStartAt.map { KST.minutesFrom(day.date, $0) } ?? Int.max
        }
        return all.sorted { startOf($0) < startOf($1) }
    }

    private func content(_ day: TimetableDay) -> some View {
        let all = CalendarDayTaskList.items(of: day)
        let todo = all.filter { $0.taskStatus != .COMPLETED }
        let done = all.filter { $0.taskStatus == .COMPLETED }
        let title = day.date == today ? "오늘 할 일" : "\(KST.formatMonthDay(day.date)) 할 일"
        return VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 8) {
                Text(title).font(.pt(15, .bold)).foregroundStyle(Color.ink)
                Spacer(minLength: 0)
                Text("\(done.count) / \(all.count) 완료").font(.ptMono(13)).foregroundStyle(Color.textSecondary)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            Rectangle().fill(Color.borderSoft).frame(height: 1)
            if !all.isEmpty {
                ProgressBar(value: Double(done.count) / Double(all.count), color: .progressFill, height: 6)
                    .padding(.horizontal, 16)
                    .padding(.top, 12)
            }
            VStack(alignment: .leading, spacing: 0) {
                if all.isEmpty {
                    Text("이날 할 일이 없어요.")
                        .font(.pt(13))
                        .foregroundStyle(Color.ink3)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 28)
                } else {
                    ForEach(todo) { t in row(t) }
                    if todo.isEmpty {
                        Text("모두 끝냈어요 🎉")
                            .font(.pt(13, .semibold))
                            .foregroundStyle(Color.success)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 10)
                    }
                    if !done.isEmpty {
                        Text("완료 \(done.count)")
                            .font(.pt(12, .semibold))
                            .foregroundStyle(Color.ink3)
                            .padding(.horizontal, 8)
                            .padding(.top, 12)
                            .padding(.bottom, 4)
                        ForEach(done) { t in row(t) }
                    }
                }
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 8)
        }
        .background(Color.surface, in: RoundedRectangle(cornerRadius: Radius.xxl))
        .overlay(RoundedRectangle(cornerRadius: Radius.xxl).stroke(Color.line, lineWidth: 1))
        .accessibilityLabel(title)
    }

    private func row(_ t: TimeBlockTask) -> some View {
        CalendarTaskCheckRow(task: t, busy: board.togglingId == t.taskId, roundCheck: true,
                             onToggle: {
                                 CAL.track("calendar_day_list.check_click", [
                                     "task_id": t.taskId, "check_action": t.taskStatus == .COMPLETED ? "uncomplete" : "complete",
                                     "is_scheduled": t.plannedStartAt != nil, "date_relation": CAL.relation(day?.date ?? today),
                                 ])
                                 Task { await board.toggleComplete(t, closeOnSuccess: false) }
                             },
                             onSelect: {
                                 CAL.track("calendar_day_list.row_click", ["task_id": t.taskId, "task_status": t.taskStatus.rawValue,
                                                                          "is_scheduled": t.plannedStartAt != nil])
                                 board.selectTask(t, trackGrid: false)
                             })
    }
}

/// 체크 + 이름 한 줄 (일간 할 일 목록 · 월간 그날 할 일 공통)
struct CalendarTaskCheckRow: View {
    let task: TimeBlockTask
    let busy: Bool
    /// 일간 목록: 동그라미 / 월간: 네모
    var roundCheck = false
    /// 월간은 시간을 앞에, 일간은 뒤에
    var timeLeading = false
    let onToggle: () -> Void
    let onSelect: () -> Void

    var body: some View {
        let t = task
        let done = t.taskStatus == .COMPLETED
        let mission = t.sourceType == .MISSION
        HStack(spacing: 10) {
            Button(action: onToggle) {
                ZStack {
                    if roundCheck {
                        Circle().strokeBorder(done ? Color.actionPrimary : Color.borderControl, lineWidth: 1.5)
                            .background(Circle().fill(done ? Color.actionPrimary : Color.clear))
                    } else {
                        RoundedRectangle(cornerRadius: Radius.checkbox).strokeBorder(done ? Color.actionPrimary : Color.borderControl, lineWidth: 1.5)
                            .background(RoundedRectangle(cornerRadius: Radius.checkbox).fill(done ? Color.actionPrimary : Color.bgSurface))
                    }
                    if done {
                        NSymbol("checkmark", size: 10, weight: .heavy).foregroundStyle(.white)
                    }
                }
                .frame(width: 20, height: 20)
                .frame(width: 32, height: 36)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(busy || (roundCheck && mission))
            .opacity(busy ? 0.5 : 1)
            .accessibilityLabel(done ? "\(t.taskName) 완료 취소" : "\(t.taskName) 완료")

            Button(action: onSelect) {
                HStack(spacing: 8) {
                    if timeLeading {
                        Text(timeText(t) ?? "시간 미정")
                            .font(.ptMono(12))
                            .foregroundStyle(Color.ink3)
                            .frame(width: 54, alignment: .leading)
                    }
                    Circle().fill(Color.goal(t.goalColorCode)).frame(width: 8, height: 8)
                    if (t.taskPriority ?? .fallback).isTop {
                        TimetablePriorityMark(priority: .A, color: Color.goal(t.goalColorCode), size: 14)
                    }
                    Text(TimetableRules.title(t.emoji, t.taskName))
                        .font(.pt(14))
                        .foregroundStyle(done ? Color.ink3 : Color.ink)
                        .strikethrough(done)
                        .lineLimit(1)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    if !timeLeading && t.sourceType == .ROUTINE {
                        NSymbol("repeat", size: 12).foregroundStyle(Color.ink4)
                            .accessibilityLabel("루틴")
                    }
                    if t.missed && (timeLeading || !done) {
                        Text("놓침")
                            .font(.pt(10, .bold))
                            .foregroundStyle(Color.danger)
                            .padding(.horizontal, 4)
                            .background(timeLeading ? Color.clear : Color.dangerSoft, in: RoundedRectangle(cornerRadius: 4))
                    }
                    if !timeLeading {
                        Text(t.plannedStartAt != nil ? KST.hm(t.plannedStartAt) : "미정")
                            .font(.ptMono(12))
                            .foregroundStyle(Color.ink3)
                            .frame(width: 42, alignment: .trailing)
                    }
                }
                .padding(.vertical, 8)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
        .padding(.trailing, 8)
    }

    /// 월간: 실제 시각(완료) 또는 계획 시각
    private func timeText(_ t: TimeBlockTask) -> String? {
        guard let st = TimetableLayout.shownTime(t) else { return nil }
        let s = KST.hm(st.startAt)
        return s.isEmpty ? nil : s
    }
}

#Preview {
    let d = KST.today()
    let board = TimetableBoardModel.sample(start: d, end: d)
    return ScrollView {
        CalendarDayTaskList(board: board, day: board.days.first, today: d)
            .padding(16)
    }
    .canvasBackground()
}
