// Features/Habit/HabitFormSheet.swift
//
// 웹 features/habit/ui/HabitForm.tsx (+ HabitView 의 수정 · 쉬기 · 지우기) — 습관 추가 · 수정 시트.
// 반복 요일을 7일 모두 고르면 매일(repeatDays: [])로 보내요. 알림 시각은 선택.
// 수정할 때 알림을 끄면 clearRemindTime 을 보내요 (HabitInput.hadRemind).

import SwiftUI
import UIKit

@MainActor
struct HabitFormSheet: View {
    /// nil 이면 새로 추가
    let habit: Habit?
    /// 지금 있는 습관 이름들 (같은 이름 추가 감지 · habit_count 용)
    let existingNames: [String]
    let onDone: () -> Void

    @State private var emoji: String
    @State private var name: String
    @State private var days: [JavaDayOfWeek]
    @State private var remindOn: Bool
    /// "HH:mm"
    @State private var remind: String
    /// 하는 시간대 (선택) — 켜면 캘린더에 블록으로
    @State private var timeOn: Bool
    @State private var startTime: String
    @State private var endTime: String
    @State private var saving = false
    @State private var confirmDelete = false
    @State private var outcome: String?

    init(habit: Habit?, existingNames: [String] = [], onDone: @escaping () -> Void) {
        self.habit = habit
        self.existingNames = existingNames
        self.onDone = onDone
        _emoji = State(initialValue: habit?.emoji ?? "")
        _name = State(initialValue: habit?.name ?? "")
        // 매일이면 화면에선 7일 모두 켠 상태로 보여줘요 ("매일" 칩이 켜지게)
        if let habit, !habit.daily, !habit.repeatDays.isEmpty {
            _days = State(initialValue: habit.repeatDays)
        } else {
            _days = State(initialValue: JavaDayOfWeek.allCases)
        }
        let r = habit?.remindTime.map { String($0.prefix(5)) } ?? ""
        _remindOn = State(initialValue: !r.isEmpty)
        _remind = State(initialValue: r.isEmpty ? "08:00" : r)
        let st = habit?.startTime.map { String($0.prefix(5)) } ?? ""
        let et = habit?.endTime.map { String($0.prefix(5)) } ?? ""
        _timeOn = State(initialValue: !st.isEmpty && !et.isEmpty)
        _startTime = State(initialValue: st.isEmpty ? "20:00" : st)
        _endTime = State(initialValue: et.isEmpty ? "20:30" : et)
    }

    private var trimmedName: String { name.trimmingCharacters(in: .whitespacesAndNewlines) }
    private var error: String? {
        if days.isEmpty { return "요일을 하나 이상 골라 주세요." }
        if timeOn && endTime <= startTime { return "끝나는 시간이 시작보다 뒤여야 해요." }
        return nil
    }

    private func hmBinding(_ value: Binding<String>) -> Binding<Date> {
        Binding(
            get: { KST.date(fromDateTime: "\(KST.today())T\(value.wrappedValue):00") },
            set: { value.wrappedValue = KST.hm(KST.dateTime(from: $0)) }
        )
    }
    private var valid: Bool { !trimmedName.isEmpty && error == nil }

    private var remindDate: Binding<Date> {
        Binding(
            get: { KST.date(fromDateTime: "\(KST.today())T\(remind):00") },
            set: { remind = KST.hm(KST.dateTime(from: $0)) }
        )
    }

    var body: some View {
        SheetScaffold(title: habit == nil ? "습관 추가" : "습관 수정",
                      description: habit == nil ? "했는지만 체크하는 생활 습관이에요. 마감 · 수치가 없어요." : nil,
                      dismissible: !saving,
                      detents: [.large]) {
            VStack(alignment: .leading, spacing: 16) {
                fields
                if let habit {
                    manageRow(habit)
                }
            }
        } footer: {
            NButton("취소", variant: .ghost) {
                outcome = "cancel_button"
                onDone()
            }
                .disabled(saving)
            NButton(habit == nil ? "습관 추가" : "저장", loading: saving) { submit(method: "button") }
                .disabled(!valid)
        }
        .confirmDialog(
            isPresented: $confirmDelete,
            title: "'\(habit?.name ?? "")' 습관을 지울까요?",
            message: "지금까지 체크한 기록도 화면에서 사라져요. 잠깐 쉬려면 '쉬기'를 써 주세요.",
            confirmLabel: "지우기",
            tone: .danger,
            onCancel: { track("habit.delete_cancel", ["habit_id": habit?.habitId]) }
        ) {
            track("habit.delete_confirm", ["habit_id": habit?.habitId, "streak": habit?.streak])
            Task { @MainActor in await remove() }
        }
        .onChange(of: error) { _, e in
            if let e { track("habit.edit_error", ["is_new": habit == nil, "validation_error": e]) }
        }
        .onDisappear {
            if let m = GD.closeMethod(outcome) {
                track("habit.edit_cancel", ["is_new": habit == nil, "close_method": m])
            }
        }
    }

    private func track(_ event: String, _ props: EventProps) {
        if ProcessInfo.processInfo.environment["XCODE_RUNNING_FOR_PREVIEWS"] == "1" { return }
        Analytics.track(event, props)
    }

    private func trackResult(_ action: String, _ ok: Bool) {
        track("habit.result", GD.result(ok).merging(["habit_action": action]) { $1 })
    }

    @ViewBuilder private var fields: some View {
        HStack(alignment: .top, spacing: 10) {
            VStack(alignment: .leading, spacing: 4) {
                Text("이모지").font(.pt(12, .medium)).foregroundStyle(Color.ink3)
                EmojiField(emoji: $emoji, placeholder: "✅")
            }
            VStack(alignment: .leading, spacing: 4) {
                Text("습관").font(.pt(12, .medium)).foregroundStyle(Color.ink3)
                TextField("예: 영양제 먹기", text: $name)
                    .inputStyle(minHeight: 52)
                    .goalMaxLength($name, 30)
                    .submitLabel(.done)
                    .onSubmit { submit(method: "keyboard_done") }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }

        VStack(alignment: .leading, spacing: 6) {
            Text("언제 하나요?").font(.pt(12, .medium)).foregroundStyle(Color.ink3)
            GoalDaysPicker(value: $days)
            if let error {
                FieldMessage(text: error, isError: true)
            } else {
                Text(HabitFormat.daysLabel(daily: days.count == 7, days: days))
                    .font(.pt(12))
                    .foregroundStyle(Color.ink3)
            }
        }

        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 8) {
                NSymbol("calendar.badge.clock", size: 14, weight: .semibold)
                    .foregroundStyle(timeOn ? Color.textBrand : Color.ink3)
                Text("하는 시간 (선택)").font(.pt(14, .semibold)).foregroundStyle(Color.ink)
                Spacer(minLength: 0)
                NSwitch(isOn: $timeOn, label: "하는 시간")
            }
            if timeOn {
                HStack(spacing: 8) {
                    DatePicker("", selection: hmBinding($startTime), displayedComponents: .hourAndMinute)
                        .labelsHidden()
                    Text("~").foregroundStyle(Color.ink3)
                    DatePicker("", selection: hmBinding($endTime), displayedComponents: .hourAndMinute)
                        .labelsHidden()
                    Spacer(minLength: 0)
                }
                .environment(\.timeZone, KST.timeZone)
                .environment(\.locale, Locale(identifier: "ko_KR"))
            }
            Text("정하면 캘린더에 그 시간이 블록으로 보이고, 자동 배치가 그 시간은 비워 둬요. (예: 샤워 20:00 ~ 20:30)")
                .font(.pt(12))
                .foregroundStyle(Color.ink4)
                .fixedSize(horizontal: false, vertical: true)
        }

        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 8) {
                NSymbol(remindOn ? "bell" : "bell.slash", size: 14, weight: .semibold)
                    .foregroundStyle(remindOn ? Color.textBrand : Color.ink3)
                Text("알림 (선택)").font(.pt(14, .semibold)).foregroundStyle(Color.ink)
                Spacer(minLength: 0)
                NSwitch(isOn: $remindOn, label: "알림")
            }
            if remindOn {
                HStack {
                    Text("알림 시각").font(.pt(13)).foregroundStyle(Color.ink2)
                    Spacer(minLength: 0)
                    DatePicker("", selection: remindDate, displayedComponents: .hourAndMinute)
                        .labelsHidden()
                }
                .environment(\.timeZone, KST.timeZone)
                .environment(\.locale, Locale(identifier: "ko_KR"))
            }
            Text("그 시각까지 체크하지 않았을 때만 앱으로 알려드려요.")
                .font(.pt(12))
                .foregroundStyle(Color.ink4)
        }
    }

    /// 수정할 때만: 쉬기 · 다시 시작 · 지우기
    private func manageRow(_ habit: Habit) -> some View {
        HStack(spacing: 8) {
            if habit.isPaused {
                NButton("다시 시작", variant: .secondary, size: .sm, icon: "play.fill") {
                    track("habit.pause_toggle", ["habit_id": habit.habitId, "pause_action": "resume", "streak": habit.streak])
                    Task { @MainActor in await setPaused(habit, false) }
                }
            } else {
                NButton("쉬기", variant: .secondary, size: .sm, icon: "pause.fill") {
                    track("habit.pause_toggle", ["habit_id": habit.habitId, "pause_action": "pause", "streak": habit.streak])
                    Task { @MainActor in await setPaused(habit, true) }
                }
            }
            Spacer(minLength: 0)
            Button {
                track("habit.delete_click", ["habit_id": habit.habitId, "streak": habit.streak])
                confirmDelete = true
            } label: {
                HStack(spacing: 4) {
                    NSymbol("trash", size: 12, weight: .semibold)
                    Text("습관 지우기")
                }
                .font(.pt(13, .semibold))
                .foregroundStyle(Color.danger)
                .padding(.horizontal, 8)
                .frame(height: 32)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
        .disabled(saving)
        .padding(.top, 8)
        .overlay(alignment: .top) { Rectangle().fill(Color.borderSoft).frame(height: 1).offset(y: -4) }
    }

    // MARK: 저장

    private func submit(method: String) {
        guard valid, !saving else { return }
        track("habit.save_click", [
            "is_new": habit == nil, "habit_id": habit?.habitId, "repeat_day_count": days.count,
            "has_time": timeOn, "has_remind": remindOn, "submit_method": method,
        ])
        if habit == nil, existingNames.contains(trimmedName) {
            track("habit.duplicate_add", ["habit_count": existingNames.count])
        }
        let input = HabitInput(
            name: trimmedName,
            emoji: emoji.trimmingCharacters(in: .whitespacesAndNewlines),
            repeatDays: days.count == 7 ? [] : days,
            remindTime: remindOn ? remind : nil,
            hadRemind: habit?.remindTime != nil,
            startTime: timeOn ? startTime : nil,
            endTime: timeOn ? endTime : nil,
            hadTimeRange: habit?.hasTimeRange ?? false
        )
        let habitId = habit?.habitId
        let createdName = trimmedName
        saving = true
        Task { @MainActor in
            let ok: Habit??
            if let habitId {
                ok = await Mutation.run(invalidate: [.habits, .home, .timetable], successMessage: "습관을 수정했어요.") {
                    try await HabitAPI.update(habitId: habitId, input)
                }
            } else {
                ok = await Mutation.run(invalidate: [.habits, .home, .timetable], successMessage: "'\(createdName)' 습관을 추가했어요.") {
                    try await HabitAPI.create(input)
                }
            }
            saving = false
            trackResult(habitId == nil ? "create" : "update", ok != nil)
            if ok != nil {
                outcome = "saved"
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                onDone()
            }
        }
    }

    private func setPaused(_ habit: Habit, _ pause: Bool) async {
        guard !saving else { return }
        saving = true
        let id = habit.habitId
        let ok: Void?
        if pause {
            ok = await Mutation.run(invalidate: [.habits, .home, .timetable],
                                    successMessage: "잠시 쉬어요. 쉬는 동안은 안 한 날로 세지 않아요.") {
                try await HabitAPI.pause(habitId: id)
            }
        } else {
            ok = await Mutation.run(invalidate: [.habits, .home, .timetable], successMessage: "다시 시작했어요.") {
                try await HabitAPI.resume(habitId: id)
            }
        }
        saving = false
        trackResult(pause ? "pause" : "resume", ok != nil)
        if ok != nil {
            outcome = "saved"
            onDone()
        }
    }

    private func remove() async {
        guard let habit, !saving else { return }
        saving = true
        let id = habit.habitId
        let ok: Void? = await Mutation.run(invalidate: [.habits, .home, .timetable], successMessage: "습관을 지웠어요.") {
            try await HabitAPI.delete(habitId: id)
        }
        saving = false
        trackResult("delete", ok != nil)
        if ok != nil {
            outcome = "deleted"
            onDone()
        }
    }
}

#Preview("습관 추가") {
    Color.canvas
        .sheet(isPresented: .constant(true)) {
            HabitFormSheet(habit: nil, onDone: {})
        }
}

#Preview("습관 수정") {
    Color.canvas
        .sheet(isPresented: .constant(true)) {
            HabitFormSheet(habit: HabitPreviewData.habits[2], onDone: {})
        }
}
