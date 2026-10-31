// Features/Home/HomeSections.swift
//
// 홈 카드들. 웹 원본: features/home/ui/HomeView.tsx(Summary) · TodayChecklist.tsx · Upcoming.tsx · TaskCheck.tsx
//  - HomeSummaryCard   : 오늘 요약 (날짜 · 인사 · 진행률 · 지금/다음 할 일 · 마감 · 오늘 회고)
//  - HomeTasksCard     : 할 일 (루틴 아닌 Task, 끝낸 일은 접어 둬요)
//  - HomeRoutinesCard  : 루틴 · 습관 (목표 루틴 Task + 기본 생활 습관)
//  - HomeUpcomingCard  : 다가오는 일정 (앞으로 6일)
//  - HomeRoadmapCard   : 마감 로드맵 (Task · 업무형 주제 · 목표 종료일을 날짜순 세로 선으로)

import SwiftUI

private let homeWeekdayFull = ["월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "일요일"]

/// "📝 이름" (이모지가 없으면 이름만)
func homeTitle(_ emoji: String?, _ name: String) -> String {
    guard let e = emoji, !e.isEmpty else { return name }
    return "\(e) \(name)"
}

// MARK: - 공통 부품

/// 동그란 완료 체크 — 홈의 Task · 루틴 · 습관 공통 (웹 TaskCheck)
struct HomeCheckCircle: View {
    let done: Bool
    let label: String
    var disabled = false
    /// 꺼진 체크를 눌렀을 때 (home.disabled_click)
    var onDisabledTap: (() -> Void)? = nil
    let onToggle: () -> Void

    var body: some View {
        Button(action: onToggle) {
            ZStack {
                if done {
                    Circle().fill(Color.actionPrimary)
                    NIcon.check.image(size: 14)
                        .foregroundStyle(Color.actionPrimaryFg)
                } else {
                    Circle().strokeBorder(Color.borderControl, lineWidth: 1.5)
                }
            }
            .frame(width: 22, height: 22)
            .frame(width: 36, height: 36)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(disabled)
        .opacity(disabled ? 0.5 : 1)
        // 꺼진 버튼은 탭을 받지 않아서, 위에 투명한 칸을 얹어 "눌렀는데 반응 없음"을 세요
        .overlay {
            if disabled, let onDisabledTap {
                Color.clear.contentShape(Rectangle()).onTapGesture(perform: onDisabledTap)
            }
        }
        .accessibilityLabel("\(label) \(done ? "완료 취소" : "완료")")
        .accessibilityAddTraits(done ? .isSelected : [])
    }
}

/// 카드 머리 — 제목 · 끝낸 수/전체 · 오른쪽 버튼 · 진행 막대
struct HomeCardHeader<Trailing: View>: View {
    let title: String
    let done: Int
    let total: Int
    @ViewBuilder var trailing: () -> Trailing

    init(_ title: String, done: Int, total: Int, @ViewBuilder trailing: @escaping () -> Trailing) {
        self.title = title
        self.done = done
        self.total = total
        self.trailing = trailing
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Text(title).font(.pt(16, .semibold)).foregroundStyle(Color.ink)
                if total > 0 {
                    Text("\(done) / \(total)").font(.ptMono(13)).foregroundStyle(Color.ink3)
                }
                Spacer(minLength: 0)
                trailing()
            }
            if total > 0 {
                ProgressBar(value: Double(done) / Double(total), color: .progressFill, height: 6)
            }
        }
    }
}

extension HomeCardHeader where Trailing == EmptyView {
    init(_ title: String, done: Int, total: Int) {
        self.init(title, done: done, total: total) { EmptyView() }
    }
}

/// 작은 네모 칩 (마감 · 놓침 · D-day)
private struct HomeTinyChip: View {
    let text: String
    let background: Color
    let foreground: Color

    var body: some View {
        Text(text)
            .font(.pt(10, .bold))
            .foregroundStyle(foreground)
            .lineLimit(1)
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(background, in: RoundedRectangle(cornerRadius: 4))
    }
}

/// 카드 공통 겉모양
private extension View {
    func homeCard() -> some View {
        self.padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .card()
    }
}

// MARK: - 오늘 요약

/// 홈 날짜 ‹ › — 지난날 · 앞날의 홈을 볼 수 있어요
struct HomeDayNav {
    var isToday: Bool
    var isPast: Bool
    var onPrev: () -> Void
    var onNext: () -> Void
    var onToday: () -> Void
}

struct HomeSummaryCard: View {
    let today: String
    let nowMinutes: Int
    let userName: String
    let day: TimetableDay?
    let tasks: [TimeBlockTask]
    let routines: [TimeBlockTask]
    let habitDone: Int
    let habitTotal: Int
    let dueToday: Int
    let overdue: Int
    let retroLeft: Int
    /// /home/today 를 못 받으면 회고 버튼을 숨겨요
    let showRetro: Bool
    let onRetro: () -> Void
    /// 날짜 ‹ › (지난날 · 앞날 보기). nil 이면 날짜만 글자로
    var nav: HomeDayNav? = nil

    private var isToday: Bool { nav?.isToday ?? true }

    private struct Agenda {
        var start: Int
        var end: Int
        var task: TimeBlockTask
    }

    var body: some View {
        let taskDone = tasks.filter { $0.isCompleted }.count
        let routineDone = routines.filter { $0.isCompleted }.count
        let total = tasks.count + routines.count + habitTotal
        let done = taskDone + routineDone + habitDone
        let percent = total > 0 ? Int((Double(done) / Double(total) * 100).rounded()) : 0
        let left = total - done

        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .top, spacing: 12) {
                VStack(alignment: .leading, spacing: 4) {
                    dateRow
                    Text(greeting(total: total, left: left))
                        .font(.pt(19, .bold))
                        .foregroundStyle(Color.textPrimary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
                if showRetro { retroButton }
            }

            HStack(spacing: 12) {
                Text("\(percent)%")
                    .font(.pt(26, .bold))
                    .foregroundStyle(Color.textPrimary)
                    .monospacedDigit()
                    .onTapGesture { deadTap("progress_percent") }
                VStack(alignment: .leading, spacing: 5) {
                    ProgressBar(value: total > 0 ? Double(done) / Double(total) : 0, color: .progressFill,
                                track: Color.bgSurface.opacity(0.7), height: 6)
                    Text(countsLine(taskDone: taskDone, routineDone: routineDone))
                        .font(.pt(11))
                        .foregroundStyle(Color.textSecondary)
                        .monospacedDigit()
                        .lineLimit(1)
                }
            }

            if dueToday > 0 || overdue > 0 {
                HStack(spacing: 6) {
                    if dueToday > 0 {
                        pill("오늘 마감 \(dueToday)", bg: Color.statusWarningBg, fg: Color.statusWarningFg)
                    }
                    if overdue > 0 {
                        pill("마감 지남 \(overdue)", bg: Color.statusErrorBg, fg: Color.statusErrorFg)
                    }
                }
            }

            if isToday { focusBox }
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        // NowCard 스타일: 연한 브랜드 tint 바탕 (목표가 없는 홈이라 brand tone)
        .background(Color.actionSecondaryBg, in: RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
    }

    /// 날짜 + ‹ › + (오늘이 아니면) "오늘"
    @ViewBuilder private var dateRow: some View {
        let label = Text("\(KST.monthOf(today))월 \(KST.dayOfMonth(today))일 \(homeWeekdayFull[KST.weekdayIndex(today)])")
            .font(.pt(12, .semibold))
            .foregroundStyle(Color.textSecondary)
        if let nav {
            HStack(spacing: 2) {
                navButton("chevron.left", label: "이전 날", action: nav.onPrev)
                label
                navButton("chevron.right", label: "다음 날", action: nav.onNext)
                if !nav.isToday {
                    Button(action: nav.onToday) {
                        Text("오늘")
                            .font(.pt(12, .semibold))
                            .foregroundStyle(Color.textPrimary)
                            .padding(.horizontal, 10)
                            .frame(height: 24)
                            .background(Color.bgSurface.opacity(0.7), in: Capsule())
                    }
                    .buttonStyle(.plain)
                    .padding(.leading, 4)
                }
            }
            .padding(.leading, -6)
        } else {
            label
        }
    }

    private func navButton(_ icon: String, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            NSymbol(icon, size: 12, weight: .bold)
                .foregroundStyle(Color.textSecondary)
                .frame(width: 30, height: 30)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }

    /// 오늘이면 이름 뒤에서 줄을 바꿔요 ("황유림님,\n오늘 16개 남았어요") — 숫자와 "개"가 갈라지지 않게
    private func greeting(total: Int, left: Int) -> String {
        if !isToday {
            if total == 0 { return "이날은 비어 있어요" }
            if nav?.isPast == true {
                return left <= 0 ? "이날 할 일을 모두 끝냈어요 🎉" : "이날 \(total)개 중 \(total - left)개를 했어요"
            }
            return "이날 할 일 \(total)개가 잡혀 있어요"
        }
        if total == 0 { return "\(userName)님,\n오늘은 비어 있어요" }
        if left <= 0 { return "\(userName)님,\n오늘 할 일을 모두 끝냈어요 🎉" }
        return "\(userName)님,\n오늘 \(left)개 남았어요"
    }

    private func countsLine(taskDone: Int, routineDone: Int) -> String {
        var s = "할 일 \(taskDone)/\(tasks.count)"
        if !routines.isEmpty { s += " · 루틴 \(routineDone)/\(routines.count)" }
        if habitTotal > 0 { s += " · 습관 \(habitDone)/\(habitTotal)" }
        return s
    }

    private func pill(_ text: String, bg: Color, fg: Color) -> some View {
        Text(text)
            .font(.pt(12, .semibold))
            .foregroundStyle(fg)
            .monospacedDigit()
            .padding(.horizontal, 10)
            .padding(.vertical, 3)
            .background(bg, in: Capsule())
    }

    private var retroButton: some View {
        Button(action: onRetro) {
            HStack(spacing: 5) {
                NSymbol("square.and.pencil", size: 13, weight: .semibold)
                Text(isToday ? "오늘 회고" : "이날 회고").font(.pt(13, .semibold))
                if retroLeft > 0 {
                    Text("\(retroLeft)")
                        .font(.pt(10, .bold))
                        .foregroundStyle(Color.actionPrimaryFg)
                        .padding(.horizontal, 5)
                        .frame(minWidth: 16, minHeight: 16)
                        .background(Color.actionPrimary, in: Capsule())
                }
            }
            .foregroundStyle(Color.textPrimary)
            .padding(.horizontal, 12)
            .frame(height: 34)
            .background(Color.bgSurface.opacity(0.7), in: Capsule())
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(retroLeft > 0 ? "오늘 회고, 남은 목표 \(retroLeft)개" : "오늘 회고")
    }

    /// 지금 할 일 / 다음 일정 — 오늘 시간이 정해진 Task 중 아직 안 끝낸 것
    private var focusBox: some View {
        let open = agenda.filter { !$0.task.isCompleted }
        let current = open.first { $0.start <= nowMinutes && nowMinutes < $0.end }
        let next = open.first { $0.start > nowMinutes }
        let focus = current ?? next
        return VStack(alignment: .leading, spacing: 2) {
            Text(current != nil ? "지금 할 일" : "다음 일정")
                .font(.pt(11, .semibold))
                .foregroundStyle(Color.textSecondary)
            if let f = focus {
                HStack(spacing: 0) {
                    Text("\(KST.formatMinutes(f.start)) \(homeTitle(f.task.emoji, f.task.taskName))")
                        .font(.pt(14, .bold))
                        .foregroundStyle(Color.textPrimary)
                        .lineLimit(1)
                    if current == nil {
                        Text(" · \(HomeDay.inLabel(f.start - nowMinutes))")
                            .font(.pt(13, .medium))
                            .foregroundStyle(Color.textSecondary)
                            .lineLimit(1)
                            .layoutPriority(1)
                    }
                }
            } else {
                Text("남은 일정이 없어요")
                    .font(.pt(13, .semibold))
                    .foregroundStyle(Color.textSecondary)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.bgSurface.opacity(0.7), in: RoundedRectangle(cornerRadius: 12))
        .contentShape(Rectangle())
        .onTapGesture { deadTap("focus_box") }
    }

    /// 눌릴 것 같지만 반응 없는 곳 (home.dead_click)
    private func deadTap(_ target: String) {
        HomeTracker.shared.system(HomeEvent.deadClick, ["dead_target": target, "section": "summary", "view_date": today])
    }

    /// 웹 agendaOf 의 Task 부분 — 오늘 시작하는 시간이 정해진 Task
    private var agenda: [Agenda] {
        guard let day else { return [] }
        var out: [Agenda] = []
        for t in HomeDay.tasksOfDay(day) {
            guard let s = t.plannedStartAt, String(s.prefix(10)) == day.date else { continue }
            out.append(Agenda(start: HomeDay.startMinutes(day.date, t), end: HomeDay.endMinutes(day.date, t), task: t))
        }
        return out.sorted { $0.start < $1.start }
    }
}

// MARK: - 할 일 · 루틴 줄

private struct HomeTaskRow: View {
    let task: TimeBlockTask
    let today: String
    let busy: Bool
    let onToggle: () -> Void
    let onSelect: () -> Void

    var body: some View {
        let t = task
        let done = t.isCompleted
        HStack(spacing: 8) {
            HomeCheckCircle(done: done, label: t.taskName, disabled: busy || t.sourceType == .MISSION,
                            onDisabledTap: {
                                HomeTracker.shared.system(HomeEvent.disabledClick, [
                                    "disabled_reason": t.sourceType == .MISSION ? "mission_task" : "saving",
                                    "task_id": t.taskId, "source_type": t.sourceType.rawValue,
                                ])
                            },
                            onToggle: onToggle)
            Button(action: onSelect) {
                HStack(spacing: 8) {
                    Circle().fill(Color.goal(t.goalColorCode)).frame(width: 8, height: 8)
                    VStack(alignment: .leading, spacing: 1) {
                        Text(homeTitle(t.emoji, t.taskName))
                            .font(.pt(14, done ? .regular : .medium))
                            .foregroundStyle(done ? Color.ink3 : Color.ink)
                            .strikethrough(done, color: Color.ink3)
                            .lineLimit(1)
                        if let g = t.goalCategoryName, !g.isEmpty, !t.goalTemporary {
                            Text(g).font(.pt(11)).foregroundStyle(Color.ink4).lineLimit(1)
                        }
                    }
                    Spacer(minLength: 4)
                    if t.sourceType == .ROUTINE {
                        NSymbol("repeat", size: 11, weight: .semibold)
                            .foregroundStyle(Color.ink4)
                            .accessibilityLabel("루틴")
                    }
                    if let due = t.dueDate, !done, due <= KST.addDays(today, 2) {
                        if due < today {
                            HomeTinyChip(text: KST.dDay(due, today: today), background: .dangerSoft, foreground: .danger)
                        } else {
                            HomeTinyChip(text: "\(KST.dDay(due, today: today)) 마감", background: .warningSoft, foreground: .warningInk)
                        }
                    }
                    if t.missed && !done {
                        HomeTinyChip(text: "놓침", background: .dangerSoft, foreground: .danger)
                    }
                    Text(t.plannedStartAt != nil ? KST.hm(t.plannedStartAt) : "미정")
                        .font(.ptMono(12))
                        .foregroundStyle(Color.ink3)
                        .frame(width: 42, alignment: .trailing)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
        .padding(.vertical, 1)
    }
}

// MARK: - 할 일

/// 오늘 할 일 — 루틴이 아닌 Task. 남은 일(시간순, 시간 미정은 아래) → 끝낸 일(접힘)
struct HomeTasksCard: View {
    let today: String
    let tasks: [TimeBlockTask]
    let togglingId: Int?
    let onToggle: (TimeBlockTask) -> Void
    let onSelect: (TimeBlockTask) -> Void
    let onAdd: () -> Void

    @State private var showDone = false

    /// 홈에서 다른 날을 보고 있으면 "이날"
    var dayLabel: String = "오늘"

    var body: some View {
        let todo = tasks.filter { !$0.isCompleted }
        let done = tasks.filter { $0.isCompleted }
        VStack(alignment: .leading, spacing: 10) {
            HomeCardHeader("할 일", done: done.count, total: tasks.count) {
                NButton("추가", variant: .ghost, size: .sm, icon: "plus", action: onAdd)
                    .accessibilityLabel("Task 추가")
            }

            if tasks.isEmpty {
                Text("\(dayLabel)로 정한 할 일이 없어요.")
                    .font(.pt(14))
                    .foregroundStyle(Color.ink3)
                    .padding(.vertical, 8)
                    .onAppear { HomeTracker.shared.emptyOnce("tasks", viewDate: today) }
            } else {
                if todo.isEmpty {
                    Text("오늘 할 일을 모두 끝냈어요 🎉")
                        .font(.pt(14, .semibold))
                        .foregroundStyle(Color.success)
                        .padding(.vertical, 4)
                } else {
                    VStack(spacing: 0) {
                        ForEach(todo) { t in row(t) }
                    }
                }
                if !done.isEmpty {
                    VStack(alignment: .leading, spacing: 0) {
                        Rectangle().fill(Color.borderSoft).frame(height: 1)
                        Button {
                            HomeTracker.shared.track(HomeEvent.taskDoneToggleClick, ["expanded": !showDone, "done_count": done.count])
                            withAnimation(.easeOut(duration: 0.18)) { showDone.toggle() }
                        } label: {
                            HStack(spacing: 4) {
                                Text("끝낸 일 \(done.count)").font(.pt(13, .semibold))
                                NSymbol("chevron.down", size: 11, weight: .bold)
                                    .rotationEffect(.degrees(showDone ? 180 : 0))
                            }
                            .foregroundStyle(Color.success)
                            .frame(height: 34)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityValue(showDone ? "펼침" : "접힘")
                        if showDone {
                            ForEach(done) { t in row(t) }
                        }
                    }
                }
            }
        }
        .homeCard()
    }

    private func row(_ t: TimeBlockTask) -> some View {
        HomeTaskRow(task: t, today: today, busy: togglingId == t.taskId,
                    onToggle: { onToggle(t) }, onSelect: { onSelect(t) })
    }
}

// MARK: - 루틴 · 습관

/// 오늘 루틴 — 목표에서 반복으로 만든 루틴 Task + 기본 생활 습관 체크
struct HomeRoutinesCard: View {
    let today: String
    let routines: [TimeBlockTask]
    /// ACTIVE 이고 오늘 하는 날인 습관
    let habits: [Habit]
    let habitHome: HabitHome?
    let togglingId: Int?
    let onToggleTask: (TimeBlockTask) -> Void
    let onSelectTask: (TimeBlockTask) -> Void
    let onToggleHabit: (Habit) -> Void
    let onOpenHabits: () -> Void

    /// 홈에서 다른 날을 보고 있으면 "이날"
    var dayLabel: String = "오늘"

    var body: some View {
        let total = routines.count + habits.count
        let done = routines.filter { $0.isCompleted }.count + habits.filter { $0.checked }.count
        // 안 한 것 먼저
        let sortedRoutines = routines.filter { !$0.isCompleted } + routines.filter { $0.isCompleted }
        let sortedHabits = habits.filter { !$0.checked } + habits.filter { $0.checked }

        VStack(alignment: .leading, spacing: 10) {
            HomeCardHeader("루틴 · 습관", done: done, total: total)

            if total == 0 {
                VStack(alignment: .leading, spacing: 6) {
                    Text("\(dayLabel) 할 루틴이 없어요.").font(.pt(14)).foregroundStyle(Color.ink3)
                        .onAppear { HomeTracker.shared.emptyOnce("routines", viewDate: today) }
                    Button {
                        HomeTracker.shared.track(HomeEvent.habitAddClick, ["view_date": today])
                        onOpenHabits()
                    } label: {
                        Text("생활 습관 추가하기").font(.pt(13, .semibold)).foregroundStyle(Color.textBrand)
                    }
                    .buttonStyle(.plain)
                }
                .padding(.vertical, 6)
            } else {
                if !sortedRoutines.isEmpty {
                    VStack(alignment: .leading, spacing: 0) {
                        subTitle("목표 루틴")
                        ForEach(sortedRoutines) { t in
                            HomeTaskRow(task: t, today: today, busy: togglingId == t.taskId,
                                        onToggle: { onToggleTask(t) }, onSelect: { onSelectTask(t) })
                        }
                    }
                }
                if !sortedHabits.isEmpty {
                    VStack(alignment: .leading, spacing: 0) {
                        Button {
                            HomeTracker.shared.track(HomeEvent.habitGroupLinkClick, ["goal_category_id": habitHome?.goalCategoryId])
                            onOpenHabits()
                        } label: {
                            HStack(spacing: 2) {
                                Text("\(habitHome?.emoji ?? "🌱") 생활 습관")
                                NSymbol("chevron.right", size: 9, weight: .bold)
                            }
                            .font(.pt(12, .semibold))
                            .foregroundStyle(Color.ink3)
                            .frame(height: 24)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        ForEach(sortedHabits) { h in habitRow(h) }
                    }
                }
            }
        }
        .homeCard()
    }

    private func subTitle(_ s: String) -> some View {
        Text(s).font(.pt(12, .semibold)).foregroundStyle(Color.ink3).frame(height: 24)
    }

    private func habitRow(_ h: Habit) -> some View {
        HStack(spacing: 8) {
            HomeCheckCircle(done: h.checked, label: h.name) { onToggleHabit(h) }
            Text(homeTitle(h.emoji, h.name))
                .font(.pt(14))
                .foregroundStyle(h.checked ? Color.ink3 : Color.ink)
                .strikethrough(h.checked, color: Color.ink3)
                .lineLimit(1)
            Spacer(minLength: 4)
            if h.streak > 1 {
                HStack(spacing: 1) {
                    Image(systemName: "flame.fill").font(.system(size: 10, weight: .bold))
                    Text("\(h.streak)").font(.pt(11, .semibold)).monospacedDigit()
                }
                .foregroundStyle(GoalPalette.orange.tone.fg)
                .accessibilityLabel("🔥 \(h.streak)일 연속")
            }
        }
        .padding(.vertical, 1)
    }
}

// MARK: - 다가오는 일정

/// 다가오는 일정 — 내일부터 6일 동안 할 일 (루틴은 매일 반복이라 빼요). 약속은 강조해요
struct HomeUpcomingCard: View {
    let days: [TimetableDay]
    let today: String
    let onOpenDay: (String) -> Void

    private struct DayGroup: Identifiable {
        var id: String { date }
        var date: String
        var items: [TimeBlockTask]
    }

    private var groups: [DayGroup] {
        days.map { d in
            DayGroup(date: d.date,
                  items: HomeDay.tasksOfDay(d)
                    .filter { !HomeDay.isRoutine($0) && $0.taskStatus == .TODO }
                    .sorted { HomeDay.startMinutes(d.date, $0) < HomeDay.startMinutes(d.date, $1) })
        }
        .filter { !$0.items.isEmpty }
    }

    var body: some View {
        let gs = groups
        VStack(alignment: .leading, spacing: 10) {
            CardHeader("다가오는 일정", aside: "앞으로 6일")
            if gs.isEmpty {
                Text("이번 주에 잡힌 일정이 없어요.")
                    .font(.pt(14))
                    .foregroundStyle(Color.ink3)
                    .padding(.vertical, 8)
                    .onAppear { HomeTracker.shared.emptyOnce("upcoming", viewDate: today) }
            } else {
                VStack(alignment: .leading, spacing: 12) {
                    ForEach(gs) { g in
                        VStack(alignment: .leading, spacing: 2) {
                            Button {
                                HomeTracker.shared.track(HomeEvent.upcomingDayClick, [
                                    "target_date": g.date, "days_from_today": KST.diffDays(KST.today(), g.date),
                                    "item_count": g.items.count,
                                ])
                                onOpenDay(g.date)
                            } label: {
                                HStack(spacing: 4) {
                                    Text(WorkFormat.dayLabel(g.date, today: today))
                                        .font(.pt(13, .bold))
                                        .foregroundStyle(Color.ink2)
                                    Text("\(g.items.count)개")
                                        .font(.pt(13, .semibold))
                                        .foregroundStyle(Color.ink4)
                                }
                                .frame(height: 24)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            ForEach(g.items) { t in scheduleRow(t) }
                        }
                    }
                }
            }
        }
        .homeCard()
    }

    private func scheduleRow(_ t: TimeBlockTask) -> some View {
        let appointment = t.taskKind == .APPOINTMENT
        return HStack(spacing: 8) {
            Text(t.plannedStartAt != nil ? KST.hm(t.plannedStartAt) : "미정")
                .font(.ptMono(12))
                .foregroundStyle(Color.ink3)
                .frame(width: 44, alignment: .leading)
            Circle().fill(Color.goal(t.goalColorCode)).frame(width: 8, height: 8)
            Text(homeTitle(t.emoji, t.taskName))
                .font(.pt(14, appointment ? .semibold : .regular))
                .foregroundStyle(Color.ink)
                .lineLimit(1)
            Spacer(minLength: 4)
            if appointment {
                Badge("약속", tone: .brand)
            }
        }
        .frame(minHeight: 28)
        // 다가오는 일정의 Task 줄은 눌러도 아무 일이 없어요 (home.dead_click)
        .contentShape(Rectangle())
        .onTapGesture {
            HomeTracker.shared.system(HomeEvent.deadClick, ["dead_target": "upcoming_item", "section": "upcoming", "view_date": today])
        }
    }
}

// MARK: - 마감 로드맵

/// 마감 로드맵 — 모든 목표의 마감을 날짜 순으로 세로 선 위에 (업무형 보드의 로드맵과 같은 모양)
///  - Task 마감(앞으로 2주) · 업무형 주제 마감 · 목표형 종료일(앞으로 1년)
///  - 마감이 지난 것(최근 7일)은 맨 위에 빨갛게, 그다음 "오늘" 줄
struct HomeRoadmapCard: View {
    /// nil = 아직 불러오는 중
    let deadlines: [HomeDeadline]?
    let failed: Bool
    let today: String
    let onOpenGoal: (Int) -> Void
    let onOpenDay: (String) -> Void

    /// 날짜 칸 52 + 2 + 점 칸 28 의 가운데
    private static let dateWidth: CGFloat = 52
    private static let nodeWidth: CGFloat = 28
    private static var lineX: CGFloat { dateWidth + 2 + nodeWidth / 2 - 1 }

    private enum Row: Identifiable {
        case today
        case item(HomeDeadline, showDate: Bool)

        var id: String {
            switch self {
            case .today: return "today"
            case .item(let d, _): return d.id
            }
        }
    }

    private func rows(_ list: [HomeDeadline]) -> [Row] {
        let todayAt = list.firstIndex { !$0.overdue } ?? list.count
        var out: [Row] = []
        for (i, d) in list.enumerated() {
            if i == todayAt { out.append(.today) }
            let newDate = i == 0 || list[i - 1].date != d.date
            out.append(.item(d, showDate: newDate))
        }
        if todayAt == list.count { out.append(.today) }
        return out
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("마감 로드맵").font(.pt(16, .semibold)).foregroundStyle(Color.ink)
                Spacer(minLength: 0)
                if let list = deadlines, !list.isEmpty, !failed {
                    Text("\(list.filter { !$0.overdue }.count)개 남음").font(.pt(13)).foregroundStyle(Color.ink3)
                }
            }

            if failed {
                Text("마감을 불러오지 못했어요.")
                    .font(.pt(14))
                    .foregroundStyle(Color.ink3)
                    .padding(.vertical, 8)
            } else if let list = deadlines {
                if list.isEmpty {
                    Text("다가오는 마감이 없어요. 목표형 종료일 · 업무형 세부 목표 마감 · Task 마감이 여기 이어져요.")
                        .font(.pt(14))
                        .foregroundStyle(Color.ink3)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.vertical, 8)
                        .onAppear { HomeTracker.shared.emptyOnce("deadlines", viewDate: today) }
                } else {
                    timeline(list)
                    legend
                }
            } else {
                RoundedRectangle(cornerRadius: Radius.xl)
                    .fill(Color.subtle)
                    .frame(height: 120)
            }
        }
        .homeCard()
    }

    private func timeline(_ list: [HomeDeadline]) -> some View {
        VStack(spacing: 0) {
            ForEach(rows(list)) { r in
                switch r {
                case .today:
                    todayLine
                case let .item(d, showDate):
                    itemRow(d, showDate: showDate)
                }
            }
        }
        // 세로 선
        .background(alignment: .topLeading) {
            Rectangle()
                .fill(Color.line)
                .frame(width: 2)
                .padding(.vertical, 10)
                .offset(x: HomeRoadmapCard.lineX)
                .accessibilityHidden(true)
        }
    }

    private var todayLine: some View {
        HStack(spacing: 0) {
            Text("오늘")
                .font(.pt(12, .bold))
                .foregroundStyle(Color.ink)
                .frame(width: HomeRoadmapCard.dateWidth, alignment: .trailing)
            Circle().fill(Color.ink).frame(width: 10, height: 10)
                .frame(width: HomeRoadmapCard.nodeWidth)
                .padding(.leading, 2)
            Rectangle().fill(Color.ink.opacity(0.4)).frame(height: 1)
                .padding(.leading, 10)
        }
        .padding(.vertical, 6)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("오늘")
    }

    private func itemRow(_ d: HomeDeadline, showDate: Bool) -> some View {
        Button {
            HomeTracker.shared.track(HomeEvent.deadlineItemClick, [
                "deadline_type": d.type.rawValue, "d_day": d.dDay, "is_overdue": d.overdue,
                "goal_category_id": d.goalCategoryId, "destination": d.goalCategoryId != nil ? "goal" : "calendar",
            ])
            if let id = d.goalCategoryId { onOpenGoal(id) } else { onOpenDay(d.date) }
        } label: {
            HStack(alignment: .top, spacing: 0) {
                VStack(alignment: .trailing, spacing: 1) {
                    if showDate {
                        Text("\(KST.monthOf(d.date))/\(KST.dayOfMonth(d.date))")
                            .font(.pt(13, .bold))
                            .foregroundStyle(d.overdue ? Color.danger : Color.ink)
                            .monospacedDigit()
                        Text(weekdayText(d.date))
                            .font(.pt(11))
                            .foregroundStyle(Color.ink4)
                    }
                }
                .frame(width: HomeRoadmapCard.dateWidth, alignment: .trailing)
                .padding(.top, 1)

                node(d)
                    .frame(width: HomeRoadmapCard.nodeWidth, height: 22)
                    .padding(.leading, 2)

                VStack(alignment: .leading, spacing: 1) {
                    Text(homeTitle(d.emoji, d.title))
                        .font(.pt(14, d.type == .GOAL ? .bold : .semibold))
                        .foregroundStyle(Color.ink)
                        .lineLimit(1)
                    Text(subtitle(d))
                        .font(.pt(12))
                        .foregroundStyle(Color.ink3)
                        .lineLimit(1)
                }
                .padding(.leading, 10)
                .frame(maxWidth: .infinity, alignment: .leading)

                dDayChip(d)
                    .padding(.leading, 8)
                    .padding(.top, 1)
            }
            .padding(.vertical, 8)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    /// 해가 바뀌는 마감은 "27년 · 금"처럼 해를 같이 적어요
    private func weekdayText(_ ymd: String) -> String {
        let w = KST.weekdayLabel[KST.weekdayIndex(ymd)]
        if KST.yearOf(ymd) != KST.yearOf(today) {
            return "\(String(ymd.prefix(4)).suffix(2))년 · \(w)"
        }
        return w
    }

    private func subtitle(_ d: HomeDeadline) -> String {
        var s = d.type.label
        if d.type != .GOAL, let g = d.goalName, !g.isEmpty { s += " · \(g)" }
        if d.type == .TOPIC, let r = d.remaining, r > 0 { s += " · 남은 \(r)개" }
        return s
    }

    /// 점 — Task 는 작은 점, 업무형 주제는 테두리 점, 목표 종료는 깃발
    @ViewBuilder
    private func node(_ d: HomeDeadline) -> some View {
        let color = Color.goal(d.colorCode)
        switch d.type {
        case .GOAL:
            ZStack {
                Circle().fill(color)
                Image(systemName: "flag.fill")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(Color.white)
            }
            .frame(width: 26, height: 26)
            .offset(y: -2)
        case .TOPIC:
            Circle()
                .fill(Color.surface)
                .overlay(Circle().strokeBorder(d.overdue ? Color.danger : color, lineWidth: 3))
                .frame(width: 18, height: 18)
        case .TASK:
            Circle()
                .fill(d.overdue ? Color.danger : color)
                .frame(width: 12, height: 12)
                .padding(4)
                .background(Color.surface, in: Circle())
        }
    }

    private func dDayChip(_ d: HomeDeadline) -> some View {
        let bg: Color
        let fg: Color
        if d.overdue {
            bg = .dangerSoft
            fg = .danger
        } else if d.dDay <= 2 {
            bg = .warningSoft
            fg = .warningInk
        } else {
            bg = .subtle
            fg = .ink2
        }
        return Text(d.dDayText)
            .font(.pt(11, .bold))
            .foregroundStyle(fg)
            .monospacedDigit()
            .lineLimit(1)
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(bg, in: RoundedRectangle(cornerRadius: 6))
    }

    private var legend: some View {
        HStack(spacing: 12) {
            HStack(spacing: 4) {
                Circle().fill(Color.ink3).frame(width: 8, height: 8)
                Text("Task")
            }
            HStack(spacing: 4) {
                Circle().strokeBorder(Color.ink3, lineWidth: 2).frame(width: 10, height: 10)
                Text("업무형 세부 목표")
            }
            HStack(spacing: 4) {
                Image(systemName: "flag.fill").font(.system(size: 10))
                Text("목표 종료일")
            }
        }
        .font(.pt(11))
        .foregroundStyle(Color.ink4)
        .padding(.top, 2)
    }
}

// MARK: - 미리보기 샘플

/// #Preview 전용 홈 샘플 (백엔드와 같은 JSON 을 디코딩해요)
enum HomeSampleData {
    static func homeToday(today: String) -> HomeToday {
        let d = { (n: Int) -> String in KST.addDays(today, n) }
        let json = """
        {
          "today": "\(today)",
          "deadlines": [
            {"type": "TASK", "id": 11, "title": "견적서 보내기", "emoji": "📨", "date": "\(d(-2))", "dDay": -2, "overdue": true,
             "goalCategoryId": 2, "goalName": "커리어", "colorCode": "F59E0B", "goalMode": "RECORD"},
            {"type": "TOPIC", "id": 5, "title": "2분기 리포트", "date": "\(today)", "dDay": 0, "overdue": false,
             "goalCategoryId": 2, "goalName": "커리어", "colorCode": "F59E0B", "goalMode": "RECORD", "remaining": 3},
            {"type": "TASK", "id": 12, "title": "모의고사 신청", "date": "\(d(3))", "dDay": 3, "overdue": false,
             "goalCategoryId": 3, "goalName": "토익 900", "colorCode": "3D5AFE", "goalMode": "ACHIEVEMENT"},
            {"type": "TASK", "id": 13, "title": "단어장 2권 끝내기", "date": "\(d(3))", "dDay": 3, "overdue": false,
             "goalCategoryId": 3, "goalName": "토익 900", "colorCode": "3D5AFE"},
            {"type": "GOAL", "id": 3, "title": "토익 900", "emoji": "📚", "date": "\(d(40))", "dDay": 40, "overdue": false,
             "goalCategoryId": 3, "goalName": "토익 900", "colorCode": "3D5AFE", "goalMode": "ACHIEVEMENT"}
          ],
          "goals": [
            {"goalCategoryId": 3, "goalName": "토익 900", "emoji": "📚", "colorCode": "3D5AFE", "goalMode": "ACHIEVEMENT",
             "activeToday": true, "planned": 2, "done": 1, "doneTasks": ["LC 파트 2 풀기"], "openTasks": ["영어 단어 외우기"],
             "retros": [], "checkIn": {"hasMetric": true, "metricName": "모의고사 점수", "unit": "점", "currentValue": 820}},
            {"goalCategoryId": 2, "goalName": "커리어", "emoji": "💼", "colorCode": "F59E0B", "goalMode": "RECORD",
             "activeToday": true, "planned": 1, "done": 0, "doneTasks": [], "openTasks": ["보고서 초안 쓰기"],
             "retros": [{"retrospectiveId": 1, "reviewType": "DAILY", "content": "회의가 길어져서 초안을 못 썼다. 내일 오전에 먼저 하기."}]},
            {"goalCategoryId": 1, "goalName": "건강", "emoji": "🏃", "colorCode": "12B76A", "goalMode": "ACHIEVEMENT",
             "activeToday": true, "planned": 1, "done": 1, "doneTasks": ["아침 스트레칭"], "openTasks": [], "retros": [],
             "checkIn": {"hasMetric": true, "metricName": "체중", "unit": "kg", "currentValue": 64.2, "value": 64.0, "mood": "OK", "note": "점심 마라탕, 저녁은 샐러드"}},
            {"goalCategoryId": 4, "goalName": "독서", "emoji": "📖", "colorCode": "8B5CF6", "goalMode": "ACHIEVEMENT",
             "activeToday": false, "planned": 0, "done": 0, "doneTasks": [], "openTasks": [], "retros": [],
             "checkIn": {"hasMetric": false}}
          ]
        }
        """
        return try! JSONDecoder().decode(HomeToday.self, from: Data(json.utf8))
    }

    static func habitHome(today: String) -> HabitHome {
        let json = """
        {
          "goalCategoryId": 10, "goalName": "생활 습관", "emoji": "🌱", "date": "\(today)", "today": "\(today)",
          "scheduledCount": 3, "doneCount": 1,
          "habits": [
            {"habitId": 1, "name": "영양제 먹기", "emoji": "💊", "status": "ACTIVE", "startDate": "\(today)",
             "scheduled": true, "checked": true, "state": "DONE", "streak": 6},
            {"habitId": 2, "name": "물 2L 마시기", "emoji": "💧", "status": "ACTIVE", "startDate": "\(today)",
             "scheduled": true, "checked": false, "state": "TODO", "streak": 0},
            {"habitId": 3, "name": "일기 쓰기", "emoji": "📓", "status": "ACTIVE", "startDate": "\(today)",
             "scheduled": true, "checked": false, "state": "TODO", "streak": 2},
            {"habitId": 4, "name": "분리수거", "emoji": "🗑️", "status": "ACTIVE", "startDate": "\(today)",
             "scheduled": false, "checked": false, "state": "OFF", "streak": 0}
          ]
        }
        """
        return try! JSONDecoder().decode(HabitHome.self, from: Data(json.utf8))
    }

    static func days(today: String) -> [TimetableDay] {
        TimetableSampleData.days(from: today, to: KST.addDays(today, 6))
    }
}

// MARK: - 미리보기

#Preview("요약") {
    let today = KST.today()
    let days = HomeSampleData.days(today: today)
    let all = HomeDay.tasksOfDay(days.first)
    return ScrollView {
        VStack(spacing: 14) {
            HomeSummaryCard(today: today, nowMinutes: 8 * 60 + 20, userName: "유림", day: days.first,
                            tasks: all.filter { !HomeDay.isRoutine($0) }, routines: all.filter { HomeDay.isRoutine($0) },
                            habitDone: 1, habitTotal: 3, dueToday: 1, overdue: 1, retroLeft: 2,
                            showRetro: true, onRetro: {},
                            nav: HomeDayNav(isToday: true, isPast: false, onPrev: {}, onNext: {}, onToday: {}))
            HomeSummaryCard(today: today, nowMinutes: 600, userName: "나를 사용자", day: nil,
                            tasks: [], routines: [], habitDone: 0, habitTotal: 0, dueToday: 0, overdue: 0,
                            retroLeft: 0, showRetro: false, onRetro: {})
            // 지난날을 보고 있을 때 (날짜 ‹ › + "오늘")
            HomeSummaryCard(today: KST.addDays(today, -1), nowMinutes: 600, userName: "유림", day: days.first,
                            tasks: all.filter { !HomeDay.isRoutine($0) }, routines: all.filter { HomeDay.isRoutine($0) },
                            habitDone: 1, habitTotal: 3, dueToday: 0, overdue: 0, retroLeft: 1,
                            showRetro: true, onRetro: {},
                            nav: HomeDayNav(isToday: false, isPast: true, onPrev: {}, onNext: {}, onToday: {}))
        }
        .padding(16)
    }
    .canvasBackground()
}

#Preview("할 일 · 루틴") {
    let today = KST.today()
    let days = HomeSampleData.days(today: today)
    let all = HomeDay.tasksOfDay(days.first)
    let hh = HomeSampleData.habitHome(today: today)
    return ScrollView {
        VStack(spacing: 14) {
            HomeTasksCard(today: today, tasks: all.filter { !HomeDay.isRoutine($0) }, togglingId: nil,
                          onToggle: { _ in }, onSelect: { _ in }, onAdd: {})
            HomeRoutinesCard(today: today, routines: all.filter { HomeDay.isRoutine($0) },
                             habits: hh.habits.filter { $0.scheduled }, habitHome: hh, togglingId: nil,
                             onToggleTask: { _ in }, onSelectTask: { _ in }, onToggleHabit: { _ in }, onOpenHabits: {})
            HomeRoutinesCard(today: today, routines: [], habits: [], habitHome: nil, togglingId: nil,
                             onToggleTask: { _ in }, onSelectTask: { _ in }, onToggleHabit: { _ in }, onOpenHabits: {})
        }
        .padding(16)
    }
    .canvasBackground()
}

#Preview("다가오는 일정 · 마감") {
    let today = KST.today()
    let days = HomeSampleData.days(today: today)
    return ScrollView {
        VStack(spacing: 14) {
            HomeUpcomingCard(days: Array(days.dropFirst()), today: today, onOpenDay: { _ in })
            HomeRoadmapCard(deadlines: HomeSampleData.homeToday(today: today).deadlines, failed: false, today: today,
                            onOpenGoal: { _ in }, onOpenDay: { _ in })
            HomeRoadmapCard(deadlines: [], failed: false, today: today, onOpenGoal: { _ in }, onOpenDay: { _ in })
            HomeRoadmapCard(deadlines: nil, failed: true, today: today, onOpenGoal: { _ in }, onOpenDay: { _ in })
        }
        .padding(16)
    }
    .canvasBackground()
}
