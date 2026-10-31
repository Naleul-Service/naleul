// Features/Calendar/CalendarMonthView.swift
//
// 월간 보기 (명세 3-5, 8)
//  - 날짜 칸: 실행률 막대 · Task 점(좁은 화면용) · 마일스톤 🏁
//  - 날짜를 누르면 아래 패널에 그날 실행률 · 이번 주 마감 · 시간순 Task (체크 가능)
//  - "일간 보기"를 누르면 그날 일간 보기 (웹의 두 번 누르기는 한 번 누르기와 헷갈려서 버튼만 남겼어요)
// 웹 원본: features/timetable/ui/MonthView.tsx

import SwiftUI
import Observation

/// GET /timetable/monthly 상태
@MainActor
@Observable
final class CalendarMonthModel {
    private(set) var data: MonthlyTimetableResponse?
    private(set) var failed = false
    /// 실패의 HTTP 상태 (calendar_month.load_fail · 0 = 네트워크)
    private(set) var errorStatus: Int?
    private(set) var loading = false
    @ObservationIgnored private var offline = false

    init() {}

    func load(year: Int, month: Int) async {
        if offline { return }
        loading = true
        do {
            let r = try await TimetableAPI.monthly(year: year, month: month)
            data = r
            failed = false
        } catch is CancellationError {
            // 다음 달로 넘어갔어요
        } catch {
            failed = true
            errorStatus = CAL.status(error)
        }
        loading = false
    }

    /// 미리보기용
    static func sample(for date: String) -> CalendarMonthModel {
        let m = CalendarMonthModel()
        m.offline = true
        m.data = TimetableSampleData.monthly(year: KST.yearOf(date), month: KST.monthOf(date))
        return m
    }
}

/// 실행률 막대 색: progress/fill (민트) — DESIGN_SPEC 8.5
private func calendarRateColor(_ rate: Int) -> Color {
    .progressFill
}

struct CalendarMonthView: View {
    let model: CalendarMonthModel
    let board: TimetableBoardModel
    /// 선택한 날짜 (이 날짜가 속한 달을 보여줘요)
    let date: String
    let onSelectDate: (String) -> Void
    let onOpenDay: (String) -> Void
    /// 칸을 누르면 그날 실행률 · 할 일을 아래 시트로 (DESIGN_SPEC 9.9 — 예전 달력 아래 패널)
    @State private var daySheetOpen = false

    var body: some View {
        let year = KST.yearOf(date)
        let month = KST.monthOf(date)
        let today = KST.today()
        // 이전 달 데이터가 잠깐 남아 있을 수 있어 해당 달인지 확인
        let current: MonthlyTimetableResponse? = (model.data?.year == year && model.data?.month == month) ? model.data : nil
        let byDate = Dictionary((current?.days ?? []).map { ($0.date, $0) }, uniquingKeysWith: { a, _ in a })
        ScrollView {
            VStack(spacing: 16) {
                CalendarMonthGrid(year: year, month: month, date: date, today: today, byDate: byDate,
                                  loading: current == nil && !model.failed,
                                  failed: model.failed && current == nil,
                                  onRetry: {
                                      CAL.track("calendar_month.retry_click", ["view_date": date])
                                      Task { await model.load(year: year, month: month) }
                                  },
                                  onSelect: { d in
                                      CAL.track("calendar_month.day_select", ["view_date": d, "date_relation": CAL.relation(d),
                                                                              "task_count": byDate[d]?.taskCount ?? 0])
                                      onSelectDate(d)
                                      daySheetOpen = true
                                  },
                                  onFailView: {
                                      CAL.track("calendar_month.load_fail", ["view_date": date, "error_status": model.errorStatus])
                                  })
                // 이 달 목표별 실천 시간 — 참고용 작은 막대 (2026-10)
                GoalTimeStatsView(start: KST.ymd(year: year, month: month, day: 1),
                                  end: KST.ymd(year: year, month: month, day: KST.lastDayOfMonth(year: year, month: month)),
                                  viewMode: "month", periodLabel: "\(month)월")
            }
            .pagePadding()
            // 제목 줄과 달력 사이 12 (DESIGN_SPEC 10.1)
            .padding(.top, 12)
            .padding(.bottom, 24)
        }
        .sheet(isPresented: $daySheetOpen) {
            // 공통 시트 (DESIGN_SPEC 13.2): 제목 = 날짜 · 아래 버튼 = 일간 보기
            SheetScaffold(title: KST.formatMonthDay(date), description: date == today ? "오늘" : nil) {
                CalendarMonthDayPanel(board: board, date: date, today: today, summary: byDate[date],
                                      detail: board.days.first(where: { $0.date == date }),
                                      loading: board.isFetching,
                                      deadlines: current?.deadlinesThisWeek ?? [],
                                      onOpenDay: { d in
                                          daySheetOpen = false
                                          onOpenDay(d)
                                      },
                                      flat: true,
                                      onSelectTask: { t in
                                          // 시트를 닫은 뒤 Task 상세 시트를 띄워요 (시트 두 개가 겹치지 않게)
                                          daySheetOpen = false
                                          let board = self.board
                                          DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) {
                                              board.selectTask(t, trackGrid: false)
                                          }
                                      })
            } footer: {
                NButton("일간 보기", variant: .secondary, icon: "calendar") {
                    CAL.track("calendar_month.open_day_click", ["view_date": date])
                    daySheetOpen = false
                    onOpenDay(date)
                }
            }
        }
        .reloadOn([.timetable], id: "\(year)-\(month)") { await model.load(year: year, month: month) }
    }
}

// MARK: - 달력

private struct CalendarMonthGrid: View {
    let year: Int
    let month: Int
    let date: String
    let today: String
    let byDate: [String: MonthlyDay]
    let loading: Bool
    let failed: Bool
    let onRetry: () -> Void
    let onSelect: (String) -> Void
    /// 실패 문구가 보일 때 (calendar_month.load_fail)
    var onFailView: () -> Void = {}

    var body: some View {
        let weeks = KST.monthWeeks(year: year, month: month)
        VStack(spacing: 0) {
            HStack(spacing: 0) {
                ForEach(0..<7, id: \.self) { i in
                    // MonthGrid 요일 줄: h28 · Caption/MD · text/secondary (토 · 일 색)
                    Text(KST.weekdayLabel[i])
                        .font(.captionMD)
                        .foregroundStyle(i == 6 ? Color.calendarHoliday : (i == 5 ? Color.calendarSaturday : Color.textSecondary))
                        .frame(maxWidth: .infinity)
                        .frame(height: 28)
                }
            }
            Rectangle().fill(Color.borderSoft).frame(height: 1)
            if failed {
                VStack(spacing: 12) {
                    Text("월간 TimeTable을 불러오지 못했어요").font(.pt(15, .bold)).foregroundStyle(Color.ink)
                    NButton("다시 시도", variant: .secondary, action: onRetry)
                }
                .frame(maxWidth: .infinity, minHeight: 280)
                .onAppear(perform: onFailView)
            } else {
                VStack(spacing: 0) {
                    ForEach(weeks.indices, id: \.self) { w in
                        HStack(spacing: 0) {
                            ForEach(weeks[w], id: \.self) { d in
                                CalendarMonthDayCell(date: d, inMonth: KST.monthOf(d) == month, day: byDate[d],
                                                     today: today, selected: d == date) { onSelect(d) }
                            }
                        }
                        if w < weeks.count - 1 {
                            Rectangle().fill(Color.borderSoft).frame(height: 1)
                        }
                    }
                }
                .overlay {
                    if loading {
                        ZStack {
                            Color.bgSurface.opacity(0.6)
                            ProgressView().tint(Color.textTertiary)
                        }
                    }
                }
            }
        }
        // MonthGrid 바깥: radius 16 · bg/surface · 1px border/subtle · 잘라내기 (칸마다 모서리 · 바탕 없음)
        .background(Color.bgSurface)
        .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).strokeBorder(Color.borderSubtle, lineWidth: 1))
    }
}

private struct CalendarMonthDayCell: View {
    let date: String
    let inMonth: Bool
    let day: MonthlyDay?
    let today: String
    let selected: Bool
    let onTap: () -> Void

    var body: some View {
        let wd = KST.weekdayIndex(date)
        let isToday = date == today
        let past = date <= today
        // 날짜 숫자: Body/SM·Medium 13 · 토 = saturday · 일 = holiday · 다른 달 = text/placeholder
        let numberColor: Color = isToday ? .calendarTodayFg
            : (!inMonth ? .textPlaceholder : (wd == 6 ? .calendarHoliday : (wd == 5 ? .calendarSaturday : .textPrimary)))
        let info = inMonth ? day : nil
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: 4) {
                Text("\(KST.dayOfMonth(date))")
                    .font(.bodySMMedium)
                    .foregroundStyle(numberColor)
                    .frame(width: 22, height: 22)
                    .background(isToday ? Color.calendarToday : Color.clear, in: Circle())
                // 실행률: 지난 날 · 오늘만, Task 가 없는 날은 막대를 빼요 (h3 · r2 · track 연한 청록 · fill 민트)
                if let d = info, past, d.taskCount > 0, let rate = d.rate {
                    GeometryReader { g in
                        ZStack(alignment: .leading) {
                            RoundedRectangle(cornerRadius: 2).fill(Color.progressTrack)
                            RoundedRectangle(cornerRadius: 2).fill(calendarRateColor(rate))
                                .frame(width: g.size.width * CGFloat(min(max(rate, 4), 100)) / 100)
                        }
                    }
                    .frame(height: 3)
                }
                // Task 칩 최대 3개 + "+N" (h14 · r3 · 목표 tint + 2pt 막대 · DESIGN_SPEC 9.9)
                if let d = info, !d.chips.isEmpty {
                    VStack(alignment: .leading, spacing: 2) {
                        ForEach(Array(d.chips.prefix(3)), id: \.taskId) { c in
                            CalendarMonthTaskChip(chip: c)
                        }
                        if d.chips.count > 3 {
                            Text("+\(d.chips.count - 3)")
                                .font(.captionSM)
                                .foregroundStyle(Color.textTertiary)
                                .lineLimit(1)
                        }
                    }
                } else if let d = info, !d.milestones.isEmpty {
                    Text("🏁").font(.system(size: 8))
                }
                Spacer(minLength: 0)
            }
            .padding(5)
            .frame(maxWidth: .infinity, alignment: .topLeading)
            // 칩 3개 + "+N" 이 들어가게 높이를 늘렸어요
            .frame(height: 104)
            .background(isToday ? Color.aiTintSoft.opacity(0.6) : Color.clear)
            .overlay {
                if isToday {
                    Rectangle().strokeBorder(Color.textBrand, lineWidth: 2)
                } else if selected {
                    Rectangle().strokeBorder(Color.borderFocus, lineWidth: 1.5)
                }
            }
            // 칸 오른쪽 1px border/soft (마지막 열 빼고) · 아래 선은 줄 사이 구분선
            .overlay(alignment: .trailing) {
                if wd != 6 { Rectangle().fill(Color.borderSoft).frame(width: 1) }
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(accessibility)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

    private var accessibility: String {
        var s = KST.formatMonthDay(date)
        if let d = day, d.taskCount > 0 { s += " Task \(d.taskCount)개" }
        if let r = day?.rate, date <= today { s += " 실행률 \(r)%" }
        return s
    }
}

// MARK: - 달력 칸 Task 칩

/// 아주 작은 Task 칩 — h14 · r3 · 목표 tint 바탕 + 왼쪽 2pt accent 막대 · 제목 9~10pt
private struct CalendarMonthTaskChip: View {
    let chip: MonthlyChip

    var body: some View {
        let tone = Color.goalTone(chip.colorCode)
        HStack(spacing: 2) {
            Rectangle().fill(tone.accent).frame(width: 2)
            Text(chip.title)
                .font(.pt(9.5, .medium))
                .foregroundStyle(tone.fg)
                .lineLimit(1)
                .truncationMode(.tail)
                .strikethrough(chip.completed)
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, minHeight: 14, maxHeight: 14, alignment: .leading)
        .background(tone.bg)
        .clipShape(RoundedRectangle(cornerRadius: 3))
        .opacity(chip.completed ? 0.6 : 1)
    }
}

// MARK: - 선택한 날짜 패널

private struct CalendarMonthDayPanel: View {
    let board: TimetableBoardModel
    let date: String
    let today: String
    let summary: MonthlyDay?
    /// 선택한 날짜의 상세 (GET /timetable?startDate=date&endDate=date)
    let detail: TimetableDay?
    let loading: Bool
    let deadlines: [MonthlyDeadline]
    let onOpenDay: (String) -> Void
    /// 시트 안: 카드 테두리 · 그림자 없이 시트 바탕에 바로 (DESIGN_SPEC 10.2)
    var flat = false
    /// Task 를 눌렀을 때 (시트 안에서는 시트를 닫고 상세를 띄워요). nil 이면 바로 상세
    var onSelectTask: ((TimeBlockTask) -> Void)? = nil

    @ViewBuilder
    var body: some View {
        if flat {
            panel
                .frame(maxWidth: .infinity, alignment: .leading)
                .accessibilityLabel("선택한 날짜")
        } else {
            panel
                .padding(20)
                .frame(maxWidth: .infinity, alignment: .leading)
                .card()
                .accessibilityLabel("선택한 날짜")
        }
    }

    private var panel: some View {
        VStack(alignment: .leading, spacing: flat ? 24 : 20) {
            // 시트 안(flat)에서는 날짜 · 일간 보기가 시트 머리 · 아래 버튼으로 가요
            if !flat {
            HStack(spacing: 8) {
                Text(KST.formatMonthDay(date)).font(.pt(16, .semibold)).foregroundStyle(Color.ink)
                if date == today {
                    Text("오늘").font(.pt(13, .bold)).foregroundStyle(Color.textBrand)
                }
                Spacer(minLength: 0)
                NButton("일간 보기", variant: .secondary, size: .sm, icon: "calendar") {
                    CAL.track("calendar_month.open_day_click", ["view_date": date])
                    onOpenDay(date)
                }
            }
            }
            rateBox
            if let s = summary, !s.milestones.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    Text("마일스톤").font(.pt(12, .semibold)).foregroundStyle(Color.ink3)
                    ForEach(s.milestones, id: \.milestoneId) { m in
                        HStack(spacing: 8) {
                            Image(systemName: "flag.fill").font(.system(size: 12)).foregroundStyle(Color.goal(m.colorCode))
                            Text(m.title).font(.pt(14)).foregroundStyle(Color.ink).lineLimit(1)
                        }
                    }
                }
            }
            if !deadlines.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    Text("이번 주 마감").font(.pt(12, .semibold)).foregroundStyle(Color.ink3)
                    ForEach(deadlines, id: \.self) { d in deadlineRow(d) }
                }
            }
            taskSection
        }
    }

    @ViewBuilder
    private var rateBox: some View {
        let stats = detail?.stats
        let rate = stats?.rate
        let future = date > today
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .bottom) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(future ? "계획된 Task" : "실행률").font(.pt(12, .medium)).foregroundStyle(Color.textSecondary)
                    Text(future ? "\(stats?.total ?? summary?.taskCount ?? 0)개" : (rate.map { "\($0)%" } ?? "–"))
                        .font(.ptMono(26, .bold))
                        .foregroundStyle(Color.ink)
                }
                Spacer(minLength: 0)
                if let s = stats, s.total > 0 {
                    VStack(alignment: .trailing, spacing: 1) {
                        Text("완료 \(s.completed) / 전체 \(s.total)").font(.ptMono(12)).foregroundStyle(Color.textSecondary)
                        if s.missed > 0 {
                            Text("놓침 \(s.missed)").font(.ptMono(12)).foregroundStyle(Color.danger)
                        }
                        if let c = s.carriedOver, c > 0 {
                            Text("(다음 날로 넘김 \(c) 포함)").font(.pt(11)).foregroundStyle(Color.ink4)
                        }
                    }
                }
            }
            if !future, let r = rate {
                ProgressBar(value: Double(max(r, 3)) / 100, color: calendarRateColor(r), track: .progressTrack, height: 6)
            }
        }
        .padding(16)
        .background(Color.bgSunken, in: RoundedRectangle(cornerRadius: Radius.xl))
    }

    private func deadlineRow(_ d: MonthlyDeadline) -> some View {
        HStack(spacing: 8) {
            if d.type == "MILESTONE" {
                Image(systemName: "flag.fill").font(.system(size: 12)).foregroundStyle(Color.goal(d.colorCode))
            } else {
                Circle().fill(Color.goal(d.colorCode)).frame(width: 8, height: 8).padding(.horizontal, 2)
            }
            Text(d.title).font(.pt(14)).foregroundStyle(Color.ink).lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .leading)
            Text(KST.dDay(d.date, today: today))
                .font(.ptMono(12, .semibold))
                .foregroundStyle(d.date <= today ? Color.danger : Color.ink3)
        }
    }

    @ViewBuilder
    private var taskSection: some View {
        // 시간순 (시간 미정은 맨 뒤)
        let tasks = (detail?.tasks ?? []).sorted {
            (TimetableLayout.shownTime($0)?.startAt ?? "") < (TimetableLayout.shownTime($1)?.startAt ?? "")
        }
        let all = tasks + (detail?.unscheduledTasks ?? [])
        VStack(alignment: .leading, spacing: 8) {
            Text("할 일").font(.pt(12, .semibold)).foregroundStyle(Color.ink3)
            if loading && detail == nil {
                ProgressView().tint(Color.ink4).frame(maxWidth: .infinity, minHeight: 96)
            } else if all.isEmpty {
                Text("이날은 Task가 없어요.")
                    .font(.pt(14))
                    .foregroundStyle(Color.ink3)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 24)
                    .background(Color.subtle, in: RoundedRectangle(cornerRadius: Radius.xl))
            } else {
                VStack(spacing: 0) {
                    ForEach(all) { t in
                        CalendarTaskCheckRow(task: t, busy: board.togglingId == t.taskId, timeLeading: true,
                                             onToggle: {
                                                 CAL.track("calendar_month.task_check", [
                                                     "task_id": t.taskId, "check_action": t.taskStatus == .COMPLETED ? "uncomplete" : "complete",
                                                     "date_relation": CAL.relation(date),
                                                 ])
                                                 Task { await board.toggleComplete(t, closeOnSuccess: false) }
                                             },
                                             onSelect: {
                                                 CAL.track("calendar_month.task_click", ["task_id": t.taskId, "task_status": t.taskStatus.rawValue])
                                                 if let onSelectTask {
                                                     onSelectTask(t)
                                                 } else {
                                                     board.selectTask(t, trackGrid: false)
                                                 }
                                             })
                    }
                }
            }
        }
    }
}

#Preview {
    let d = KST.today()
    let board = TimetableBoardModel.sample(start: d, end: d)
    return CalendarMonthView(model: CalendarMonthModel.sample(for: d), board: board, date: d,
                             onSelectDate: { _ in }, onOpenDay: { _ in })
        .canvasBackground()
}
