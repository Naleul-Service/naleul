// Features/Goal/GoalRoutinesSection.swift
//
// 웹 features/goal/ui/RoutinesSection.tsx — 루틴 목록 · 추가 · 수정 · 삭제.
// 웹은 줄을 누르면 그 자리에서 수정 → 아이폰은 줄을 누르면 수정 시트가 떠요.

import SwiftUI

// MARK: - 섹션

@MainActor
struct GoalRoutinesSection: View {
    let goal: GoalCategory
    @Environment(GoalDetailViewModel.self) private var vm

    private struct RowItem: Identifiable {
        var id: Int { routine.routineId }
        let routine: RoutineSummary
        /// 영역 이름 ("기타 할 일" 그릇이면 "")
        let subGoal: String
        let subGoalId: Int
    }

    private var rows: [RowItem] {
        GoalDetailLogic.activeSubGoals(goal).flatMap { sg in
            sg.routines.map { r in
                RowItem(routine: r, subGoal: (sg.defaultBucket ?? false) ? "" : sg.generalCategoryName, subGoalId: sg.generalCategoryId)
            }
        }
    }

    var body: some View {
        let items = rows
        SectionCard("루틴", aside: { Text("\(items.count)개") }) {
            VStack(alignment: .leading, spacing: 12) {
                if !items.isEmpty {
                    VStack(spacing: 0) {
                        ForEach(Array(items.enumerated()), id: \.element.id) { i, item in
                            if i > 0 { Rectangle().fill(Color.borderSoft).frame(height: 1) }
                            GoalRoutineRow(routine: item.routine, subGoal: item.subGoal,
                                           practice: vm.heatmap?.first(where: { $0.routineId == item.routine.routineId })?.timeStats?.practiceSentence) { entry in
                                vm.track("goal_detail.routine_edit_open", ["routine_id": item.routine.routineId, "entry_point": entry])
                                vm.sheet = .routine(item.routine, subGoalId: item.subGoalId)
                            } onDelete: {
                                vm.ask(.deleteRoutine(item.routine))
                            } onExtraDay: {
                                vm.track("goal_detail.routine_extra_day_click", ["routine_id": item.routine.routineId])
                                vm.sheet = .routineExtraDay(item.routine)
                            }
                        }
                    }
                }
                GoalAddButton("루틴 추가") {
                    vm.track("goal_detail.routine_add_click", ["routine_count": items.count])
                    vm.sheet = .routine(nil, subGoalId: nil)
                }
            }
        }
    }
}

// MARK: - 한 줄

@MainActor
private struct GoalRoutineRow: View {
    let routine: RoutineSummary
    let subGoal: String
    /// "화 19:00~19:30 · 목 17:10~17:40에 가장 많이 실천했어요" (기록이 없으면 nil)
    let practice: String?
    /// 줄 누르기(row) · 연필(icon)
    let onEdit: (String) -> Void
    let onDelete: () -> Void
    /// 반복 요일이 아닌 날에도 했을 때 — 그날을 추가해요
    let onExtraDay: () -> Void

    private var active: Bool { routine.routineStatus != "STOPPED" && routine.routineStatus != "DELETED" }

    private var timeLine: String {
        var t = GoalDetailLogic.routineTimeText(routine)
        let hasStart = GoalFormat.hhmm(routine.repeatStartTime) != nil
        if !hasStart && (routine.dayTimes ?? []).isEmpty, let d = routine.durationMinutes, d > 0 {
            t += " · \(d)분"
        }
        return t
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top, spacing: 8) {
                Button { onEdit("row") } label: {
                    VStack(alignment: .leading, spacing: 4) {
                        HStack(spacing: 6) {
                            Text(routine.routineName).font(.pt(15, .semibold)).foregroundStyle(Color.ink)
                            if routine.notificationEnabled {
                                NSymbol("bell", size: 12).foregroundStyle(Color.ink3)
                                    .accessibilityLabel("알림 켜짐")
                            }
                        }
                        // 영역은 부모가 아니라 라벨 — "기타 할 일"이면 안 보여줘요 (2026-10: 시간은 카드 맨 아래로)
                        if !subGoal.isEmpty {
                            Text(subGoal)
                                .font(.pt(12, .medium))
                                .foregroundStyle(Color.ink2)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 2)
                                .background(Color.subtle, in: Capsule())
                        }
                        if let d = routine.description, !d.isEmpty {
                            Text(d)
                                .font(.pt(13))
                                .foregroundStyle(Color.ink2)
                                .lineSpacing(3)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(Color.subtle, in: RoundedRectangle(cornerRadius: Radius.lg))
                                .padding(.top, 4)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .multilineTextAlignment(.leading)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                GoalRowActions(label: routine.routineName, onEdit: { onEdit("icon") }, onDelete: onDelete)
            }
            HStack(spacing: 8) {
                GoalRoutineDayChips(days: routine.repeatDays)
                Spacer(minLength: 0)
                if active {
                    Button(action: onExtraDay) {
                        HStack(spacing: 4) {
                            Image(systemName: "calendar.badge.plus").font(.system(size: 12))
                            Text("다른 날 추가")
                        }
                        .font(.pt(12, .medium))
                        .foregroundStyle(Color.ink2)
                        .padding(.horizontal, 10)
                        .frame(height: 28)
                        .overlay(Capsule().stroke(Color.line, lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                    .accessibilityHint("반복 요일이 아닌 날에도 했다면 그날을 추가해요")
                }
            }
            // 계획한 시간 · 실제로 가장 많이 한 시간 — 무슨 시간인지 이름을 붙여 따로 보여줘요
            VStack(alignment: .leading, spacing: 3) {
                if !timeLine.isEmpty {
                    (Text("계획 · ").font(.pt(12, .semibold)).foregroundColor(Color.ink2)
                        + Text(timeLine).font(.pt(12)).foregroundColor(Color.ink3))
                        .fixedSize(horizontal: false, vertical: true)
                }
                if let practice {
                    (Text("실천 · ").font(.pt(12, .semibold)).foregroundColor(Color.brand)
                        + Text(practice).font(.pt(12)).foregroundColor(Color.ink2))
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.vertical, 12)
    }
}

/// 월~일 칸 (반복하는 요일만 파랗게)
@MainActor
private struct GoalRoutineDayChips: View {
    let days: [JavaDayOfWeek]

    var body: some View {
        HStack(spacing: 4) {
            ForEach(JavaDayOfWeek.allCases) { d in
                let on = days.contains(d)
                Text(d.label)
                    .font(.pt(11, .semibold))
                    .foregroundStyle(on ? Color.white : Color.ink4)
                    .frame(width: 24, height: 24)
                    .background(on ? Color.brand : Color.subtle, in: RoundedRectangle(cornerRadius: 6))
            }
        }
    }
}

// MARK: - 다른 날 추가 시트 (웹 ExtraDayForm)

/// 평일 루틴을 주말에도 했을 때처럼, 반복 요일이 아닌 날에 그날 하루만 루틴 블록을 만들어요.
/// "이미 했어요"면 그 시간을 실제로 한 시간으로 바로 남겨요 (앞날은 예정으로만).
@MainActor
struct GoalRoutineExtraDaySheet: View {
    let routine: RoutineSummary
    @Environment(GoalDetailViewModel.self) private var vm

    @State private var date: String
    @State private var startTime: String
    @State private var endTime: String
    @State private var done = true
    @State private var saving = false
    @State private var outcome: String?

    init(routine: RoutineSummary) {
        self.routine = routine
        let today = KST.today()
        let t = Self.timeOn(routine, today)
        _date = State(initialValue: today)
        _startTime = State(initialValue: t.start)
        _endTime = State(initialValue: t.end)
    }

    /// 그 날의 루틴 시간 — 요일별 시간이 있으면 그 시간, 없으면 기본 시간
    static func timeOn(_ r: RoutineSummary, _ ymd: String) -> (start: String, end: String) {
        let day = JavaDayOfWeek.allCases[KST.weekdayIndex(ymd)]
        let own = (r.dayTimes ?? []).first { $0.dayOfWeek == day }
        return (GoalFormat.hhmm(own?.startTime ?? r.repeatStartTime) ?? "",
                GoalFormat.hhmm(own?.endTime ?? r.repeatEndTime) ?? "")
    }

    private var today: String { KST.today() }
    private var maxDate: String { KST.addDays(today, 60) }
    private var future: Bool { date > today }
    private var day: JavaDayOfWeek { JavaDayOfWeek.allCases[KST.weekdayIndex(date)] }
    private var isRepeatDay: Bool { routine.repeatDays.contains(day) }

    private var error: String? {
        if date > maxDate { return "60일 뒤까지만 추가할 수 있어요." }
        if startTime.isEmpty != endTime.isEmpty { return "시작·종료 시간을 함께 입력하거나 둘 다 비워 주세요." }
        if !startTime.isEmpty, !endTime.isEmpty {
            let s = KST.timeToMinutes(startTime), e = KST.timeToMinutes(endTime)
            // 끝이 시작보다 이르면 자정을 넘기는 것 (12시간까지)
            if s == e || (e < s && 1440 - (s - e) > 12 * 60) { return "시작과 종료 시간을 확인해 주세요." }
        }
        return nil
    }

    var body: some View {
        SheetScaffold(title: "‘\(routine.routineName)’를 다른 날에도 했어요",
                      description: "반복 요일은 그대로 두고, 고른 날 하루만 루틴 블록이 생겨요. 실천 기록에도 함께 들어가요.",
                      dismissible: !saving) {
            GoalInlineForm(submitLabel: done && !future ? "한 걸로 남기기" : "그날 추가", saving: saving,
                           valid: error == nil, error: error,
                           onCancel: {
                               outcome = "cancel_button"
                               vm.sheet = nil
                           }, onSubmit: submit) {
                GoalFormField("날짜") { GoalDateInput(value: $date, maxDate: maxDate) }
                HStack(alignment: .top, spacing: 8) {
                    GoalFormField("시작") { GoalTimeInput(value: $startTime) }
                    GoalFormField("종료") {
                        GoalTimeInput(value: $endTime,
                                      suggested: startTime.isEmpty ? "09:30" : KST.minutesToTime(KST.timeToMinutes(startTime) + 30))
                    }
                }
                if isRepeatDay {
                    Text("\(day.label)요일은 원래 하는 날이에요. 이미 블록이 있으면 캘린더에서 그 블록을 완료해 주세요.")
                        .font(.pt(12))
                        .foregroundStyle(Color.warning)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Toggle(isOn: Binding(get: { done && !future }, set: { done = $0 })) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("이미 했어요").font(.pt(14, .semibold)).foregroundStyle(Color.ink)
                        Text(future ? "앞날은 예정으로만 넣어요"
                             : (startTime.isEmpty ? "루틴 시간에 한 걸로 남겨요" : "\(startTime)~\(endTime) 에 한 걸로 남겨요"))
                            .font(.pt(12))
                            .foregroundStyle(Color.ink3)
                    }
                }
                .tint(Color.brand)
                .disabled(future)
            }
            .goalSheetForm()
        }
        .onChange(of: date) { _, v in
            let t = Self.timeOn(routine, v)
            startTime = t.start
            endTime = t.end
        }
        .onDisappear {
            if let m = GD.closeMethod(outcome) {
                vm.track("goal_detail.routine_extra_day_cancel", ["routine_id": routine.routineId, "close_method": m])
            }
        }
    }

    private func submit() {
        guard error == nil, !saving else { return }
        let id = routine.routineId
        let d = date, s = startTime.isEmpty ? nil : startTime, e = endTime.isEmpty ? nil : endTime
        let didIt = done && !future
        vm.track("goal_detail.routine_extra_day_save", [
            "routine_id": id, "days_from_today": GD.daysFromToday(d), "marked_done": didIt,
            "is_repeat_day": isRepeatDay, "has_time": s != nil,
        ])
        saving = true
        Task { @MainActor in
            let ok = await Mutation.run(invalidate: [.goals, .timetable, .home, .pattern],
                                        successMessage: didIt ? "그날 한 걸로 남겼어요." : "그날 캘린더에 넣었어요.") {
                try await GoalEditAPI.addRoutineExtraDay(routineId: id, date: d, startTime: s, endTime: e, done: didIt)
            }
            saving = false
            vm.track("goal_detail.routine_result", GD.result(ok != nil).merging(["routine_action": "extra_day"]) { $1 })
            if ok != nil {
                outcome = "saved"
                vm.sheet = nil
            }
        }
    }
}

// MARK: - 추가 · 수정 시트 (웹 RoutineForm)

@MainActor
struct GoalRoutineFormSheet: View {
    let goal: GoalCategory
    /// nil 이면 새로 추가
    let routine: RoutineSummary?

    @Environment(GoalDetailViewModel.self) private var vm

    private struct DayTime: Hashable {
        var start: String
        var end: String
    }

    @State private var name: String
    @State private var sub: Int?
    /// 반복 기간은 보통 목표 기간 그대로라 접어 둬요
    @State private var showPeriod = false
    @State private var days: [JavaDayOfWeek]
    @State private var startTime: String
    @State private var endTime: String
    @State private var start: String
    @State private var end: String
    @State private var notify: Bool
    @State private var howTo: String
    /// 요일별 시간: 기본은 모든 요일 같은 시간, 원하면 요일마다 다르게 (기본과 다른 요일만 저장돼요)
    @State private var perDay: Bool
    @State private var dayTimes: [JavaDayOfWeek: DayTime]
    @State private var saving = false
    @State private var outcome: String?
    /// 처음 값 (고쳤는지 · 기간을 바꿨는지 보려고)
    @State private var initialKey: String?
    @State private var initialPeriod: String?
    /// 지금 다른 루틴과 겹치는 수 (주간표가 알려줘요)
    @State private var conflictCount = 0

    private var formKey: String {
        [trimmedName, days.map(\.rawValue).joined(), startTime, endTime, start, end, String(notify), howTo, String(perDay),
         dayTimes.map { "\($0.key.rawValue)\($0.value.start)\($0.value.end)" }.sorted().joined(), String(sub ?? 0)].joined(separator: "|")
    }

    private func trackR(_ event: String, _ props: EventProps = [:]) {
        vm.track(event, props.merging(["is_new": routine == nil]) { $1 })
    }

    init(goal: GoalCategory, routine: RoutineSummary?, subGoalId: Int?) {
        self.goal = goal
        self.routine = routine
        let areas = GoalDetailLogic.areas(goal)
        let period = GoalDetailLogic.defaultPeriod(goal)
        let today = KST.today()
        _name = State(initialValue: routine?.routineName ?? "")
        if let subGoalId, areas.contains(where: { $0.generalCategoryId == subGoalId }) {
            _sub = State(initialValue: subGoalId)
        } else {
            _sub = State(initialValue: nil)
        }
        let rd = routine?.repeatDays ?? []
        _days = State(initialValue: rd.isEmpty ? JavaDayOfWeek.allCases : rd)
        _startTime = State(initialValue: GoalDetailLogic.hhmmOrEmpty(routine?.repeatStartTime))
        _endTime = State(initialValue: GoalDetailLogic.hhmmOrEmpty(routine?.repeatEndTime))
        _start = State(initialValue: routine?.repeatStartDate ?? (period.start < today ? today : period.start))
        _end = State(initialValue: routine?.repeatEndDate ?? (period.end < today ? today : period.end))
        _notify = State(initialValue: routine?.notificationEnabled ?? true)
        _howTo = State(initialValue: routine?.description ?? "")
        let dts = routine?.dayTimes ?? []
        _perDay = State(initialValue: !dts.isEmpty)
        var map: [JavaDayOfWeek: DayTime] = [:]
        for t in dts {
            map[t.dayOfWeek] = DayTime(start: GoalDetailLogic.hhmmOrEmpty(t.startTime), end: GoalDetailLogic.hhmmOrEmpty(t.endTime))
        }
        _dayTimes = State(initialValue: map)
    }

    private var areas: [SubGoalInfo] { GoalDetailLogic.areas(goal) }

    private var subOptions: [(value: Int?, label: String)] {
        var o: [(value: Int?, label: String)] = [(value: nil, label: "세부 목표 없음")]
        for a in areas { o.append((value: a.generalCategoryId, label: a.generalCategoryName)) }
        return o
    }
    private var trimmedName: String { name.trimmingCharacters(in: .whitespacesAndNewlines) }
    private var shownDays: [JavaDayOfWeek] { JavaDayOfWeek.allCases.filter { days.contains($0) } }

    /// 요일별 칸에 보여줄 값 — 따로 정한 게 없으면 기본 시간
    private func timeOf(_ d: JavaDayOfWeek) -> DayTime { dayTimes[d] ?? DayTime(start: startTime, end: endTime) }

    private func dayTimeBinding(_ d: JavaDayOfWeek, start isStart: Bool) -> Binding<String> {
        Binding(
            get: { isStart ? timeOf(d).start : timeOf(d).end },
            set: { v in
                var t = timeOf(d)
                if isStart { t.start = v } else { t.end = v }
                dayTimes[d] = t
            }
        )
    }

    /// 주간표의 빈 곳을 눌렀을 때 — 그 요일을 넣고, 지금 길이(없으면 1시간) 그대로 시작 시각만 옮겨요
    private func pickFromMap(_ d: JavaDayOfWeek, _ m: Int) {
        var len = 60
        if !startTime.isEmpty, !endTime.isEmpty {
            let l = (KST.timeToMinutes(endTime) - KST.timeToMinutes(startTime) + 1440) % 1440
            if l > 0 { len = l }
        }
        if !days.contains(d) { days = JavaDayOfWeek.allCases.filter { $0 == d || days.contains($0) } }
        let st = KST.minutesToTime(m)
        let et = KST.minutesToTime((m + len) % 1440)
        if perDay && days.count > 1 {
            dayTimes[d] = DayTime(start: st, end: et)
        } else {
            startTime = st
            endTime = et
        }
    }

    /// 기본 시간과 실제로 다른 요일만 보내요
    private var overrides: [RoutineDayTime] {
        guard perDay else { return [] }
        return shownDays.compactMap { d in
            let t = timeOf(d)
            guard !t.start.isEmpty, !t.end.isEmpty, t.start != startTime || t.end != endTime else { return nil }
            return RoutineDayTime(dayOfWeek: d, startTime: t.start, endTime: t.end)
        }
    }

    private var badDay: JavaDayOfWeek? {
        guard perDay else { return nil }
        return shownDays.first { d in
            let t = timeOf(d)
            return t.start.isEmpty != t.end.isEmpty || (!t.start.isEmpty && !t.end.isEmpty && !KST.isValidRoutineRange(t.start, t.end))
        }
    }

    private var error: String? {
        if trimmedName.isEmpty { return nil }
        if days.isEmpty { return "요일을 하나 이상 골라 주세요." }
        if startTime.isEmpty != endTime.isEmpty { return "시작·종료 시간을 함께 입력하거나 둘 다 비워 주세요." }
        if !startTime.isEmpty && !endTime.isEmpty && !KST.isValidRoutineRange(startTime, endTime) {
            return "시작과 종료 시간을 확인해 주세요. 자정을 넘기는 루틴(예: 23:00 ~ 00:00)은 12시간까지 돼요."
        }
        if routine?.repeatStartTime != nil && startTime.isEmpty {
            return "이미 정한 시간은 비울 수 없어요. 시간 없이 하려면 루틴을 다시 만들어 주세요."
        }
        if start.isEmpty || end.isEmpty || start > end { return "반복 기간을 확인해 주세요." }
        if let badDay { return "\(badDay.label)요일 시간을 확인해 주세요. (시작·종료를 함께, 자정을 넘기면 12시간까지)" }
        return nil
    }

    var body: some View {
        SheetScaffold(title: routine == nil ? "루틴 추가" : "루틴 수정", dismissible: !saving, detents: [.large]) {
            GoalInlineForm(submitLabel: routine == nil ? "추가" : "저장", saving: saving,
                           valid: !trimmedName.isEmpty && error == nil, error: error,
                           onCancel: {
                               outcome = "cancel_button"
                               vm.sheet = nil
                           }, onSubmit: submit) {
                fields
            } extra: {
                Button {
                    trackR("goal_detail.routine_notify_toggle", ["notification_enabled": !notify])
                    notify.toggle()
                } label: {
                    HStack(spacing: 4) {
                        NSymbol(notify ? "bell" : "bell.slash", size: 12, weight: .semibold)
                        Text("알림 \(notify ? "켜짐" : "꺼짐")")
                    }
                    .font(.pt(13, .semibold))
                    .foregroundStyle(notify ? Color.textBrand : Color.ink3)
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(notify ? .isSelected : [])
            }
            .goalSheetForm()
        }
        .onAppear {
            if initialKey == nil { initialKey = formKey; initialPeriod = start + end }
        }
        .onChange(of: error) { _, e in
            if let e { trackR("goal_detail.routine_edit_error", ["validation_error": e]) }
        }
        .onDisappear {
            if let m = GD.closeMethod(outcome) {
                trackR("goal_detail.routine_edit_cancel", ["close_method": m, "is_dirty": formKey != initialKey])
            }
        }
    }

    @ViewBuilder private var fields: some View {
        GoalFormField("루틴 이름") {
            TextField("예: 아침 스트레칭", text: $name)
                .inputStyle()
                .goalMaxLength($name, 50)
        }
        // 영역이 있을 때만 (선택) — 없으면 아예 안 보여요
        if !areas.isEmpty {
            GoalFormField("세부 목표 (선택)") {
                GoalSelectMenu(selection: $sub, options: subOptions)
            }
        }
        GoalFormField("반복 요일") { GoalDaysPicker(value: $days) }
        HStack(alignment: .top, spacing: 8) {
            GoalFormField(perDay ? "기본 시작 시간" : "시작 시간 (선택)") { GoalTimeInput(value: $startTime) }
            GoalFormField(perDay ? "기본 종료 시간" : "종료 시간 (선택)") {
                GoalTimeInput(value: $endTime, suggested: startTime.isEmpty ? "09:30" : KST.minutesToTime(KST.timeToMinutes(startTime) + 30))
            }
        }
        if showPeriod {
            HStack(alignment: .top, spacing: 8) {
                GoalFormField("반복 시작일") { GoalDateInput(value: $start) }
                GoalFormField("반복 종료일") { GoalDateInput(value: $end) }
            }
        }
        // 다른 루틴과 겹치는지 · 내 루틴 주간표 (캘린더에 다녀오지 않아도 돼요)
        GoalRoutineWeekMap(draft: GoalRoutineDraft(routineId: routine?.routineId, name: trimmedName, days: days,
                                                   timeOf: { d in let t = timeOf(d); return (t.start, t.end) }),
                           onPick: { d, m in
                               trackR("goal_detail.routine_weekmap_pick", [
                                   "day_of_week": d.rawValue, "start_minute": m, "per_day_time": perDay && days.count > 1,
                               ])
                               pickFromMap(d, m)
                           },
                           isNew: routine == nil,
                           onConflictCount: { conflictCount = $0 })
        // 요일별 시간 — 기본은 같은 시간, 필요할 때만 펼쳐요
        if days.count > 1 {
            if perDay {
                perDayBlock
            } else {
                Button("+ 요일마다 시간을 다르게 하기") {
                    trackR("goal_detail.routine_per_day_toggle", ["per_day_time": true])
                    perDay = true
                }
                    .font(.pt(13, .semibold))
                    .foregroundStyle(Color.textBrand)
                    .buttonStyle(.plain)
            }
        }
        if !showPeriod {
            HStack(spacing: 4) {
                Text("\(GoalFormat.formatDot(start)) – \(GoalFormat.formatDot(end)) 동안 반복해요.")
                    .foregroundStyle(Color.ink3)
                Button("기간 바꾸기") {
                    trackR("goal_detail.routine_period_open")
                    showPeriod = true
                }
                    .foregroundStyle(Color.ink2)
                    .underline()
                    .buttonStyle(.plain)
            }
            .font(.pt(12))
        }
        GoalFormField("하는 방법 (선택)") {
            TextField("예) 스쿼트 4x10 → 런지 3x12(각), 세트 사이 60초 휴식", text: $howTo, axis: .vertical)
                .lineLimit(2...6)
                .inputStyle()
                .goalMaxLength($howTo, 300)
        }
        Text((startTime.isEmpty ? "시간을 비워 두면 자동 배치가 빈 시간에 넣어 줘요." : "정한 시간에 매번 고정돼요.")
             + (routine != nil ? " 저장하면 오늘 이후의 루틴 Task가 새 설정으로 바뀌어요 (지난 기록은 그대로)." : ""))
            .font(.pt(12))
            .foregroundStyle(Color.ink3)
    }

    private var perDayBlock: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("요일별 시간").font(.pt(13, .semibold)).foregroundStyle(Color.ink)
                Spacer(minLength: 0)
                Button("모든 요일 같은 시간으로") {
                    trackR("goal_detail.routine_per_day_toggle", ["per_day_time": false])
                    perDay = false
                    dayTimes = [:]
                }
                .font(.pt(12, .semibold))
                .foregroundStyle(Color.ink3)
                .underline()
                .buttonStyle(.plain)
            }
            ForEach(shownDays) { d in
                let t = timeOf(d)
                let custom = dayTimes[d] != nil && (t.start != startTime || t.end != endTime)
                HStack(spacing: 6) {
                    Text(d.label)
                        .font(.pt(13, .bold))
                        .foregroundStyle(custom ? Color.white : Color.ink2)
                        .frame(width: 32, height: 32)
                        .background(custom ? Color.brand : Color.surface, in: RoundedRectangle(cornerRadius: Radius.lg))
                    GoalTimeInput(value: dayTimeBinding(d, start: true), placeholder: "시작")
                    Text("~").foregroundStyle(Color.ink4)
                    GoalTimeInput(value: dayTimeBinding(d, start: false), placeholder: "종료")
                    Button("기본으로") { dayTimes[d] = nil }
                        .font(.pt(12, .semibold))
                        .foregroundStyle(Color.ink3)
                        .buttonStyle(.plain)
                        .opacity(custom ? 1 : 0)
                        .disabled(!custom)
                }
            }
            Text("파란 요일만 기본 시간과 달라요. 비워 둔 요일은 "
                 + (!startTime.isEmpty && !endTime.isEmpty ? "기본 시간(\(startTime)~\(endTime))" : "자동 배치") + "을 따라요.")
                .font(.pt(12))
                .foregroundStyle(Color.ink3)
        }
        .padding(12)
        .background(Color.subtle.opacity(0.6), in: RoundedRectangle(cornerRadius: Radius.xl))
    }

    private func submit() {
        guard !trimmedName.isEmpty, error == nil, !saving else { return }
        // 영역 없음 → 새로 만들 땐 nil(서버가 "기타 할 일"로), 수정할 땐 "기타 할 일"로 옮기기 (그릇이 없으면 nil)
        let areaId: Int? = sub ?? (routine != nil ? GoalDetailLogic.bucket(goal)?.generalCategoryId : nil)
        let input = GoalRoutineInput(
            generalCategoryId: areaId,
            routineName: trimmedName,
            repeatStartDate: start,
            repeatEndDate: end,
            repeatDays: days,
            repeatStartTime: startTime.isEmpty ? nil : startTime,
            repeatEndTime: endTime.isEmpty ? nil : endTime,
            notificationEnabled: notify,
            description: howTo.trimmingCharacters(in: .whitespacesAndNewlines),
            dayTimes: overrides
        )
        let goalId = goal.goalCategoryId
        let routineId = routine?.routineId
        trackR("goal_detail.routine_save_click", [
            "repeat_day_count": days.count, "has_time": !startTime.isEmpty, "per_day_time": !overrides.isEmpty,
            "notification_enabled": notify, "has_description": !input.description.isEmpty, "has_sub_goal": sub != nil,
            "period_changed": initialPeriod.map { $0 != start + end } ?? false, "has_conflict": conflictCount > 0,
        ])
        saving = true
        Task { @MainActor in
            let ok: Void?
            if let routineId {
                ok = await Mutation.run(invalidate: [.goals, .timetable], successMessage: "루틴을 수정했어요.") {
                    try await GoalEditAPI.updateRoutine(id: routineId, input)
                }
            } else {
                ok = await Mutation.run(invalidate: [.goals, .timetable], successMessage: "루틴을 추가했어요.") {
                    try await GoalEditAPI.createRoutine(goalId: goalId, input)
                }
            }
            saving = false
            vm.track("goal_detail.routine_result", GD.result(ok != nil).merging(["routine_action": routineId == nil ? "create" : "update"]) { $1 })
            if ok != nil {
                outcome = "saved"
                vm.sheet = nil
            }
        }
    }
}

#Preview("루틴") {
    ScrollView {
        GoalRoutinesSection(goal: GoalPreviewData.goal).padding(16)
    }
    .canvasBackground()
    .environment(GoalDetailViewModel.preview())
    .environment(AppRouter.shared)
}

#Preview("루틴 수정") {
    let goal = GoalPreviewData.goal
    return Color.canvas
        .sheet(isPresented: .constant(true)) {
            GoalRoutineFormSheet(goal: goal, routine: goal.generalCategories[1].routines[0], subGoalId: 22)
                .environment(GoalDetailViewModel.preview())
                .environment(AppRouter.shared)
        }
}

#Preview("다른 날 추가") {
    let goal = GoalPreviewData.goal
    return Color.canvas
        .sheet(isPresented: .constant(true)) {
            GoalRoutineExtraDaySheet(routine: goal.generalCategories[1].routines[0])
                .environment(GoalDetailViewModel.preview())
                .environment(AppRouter.shared)
        }
}
