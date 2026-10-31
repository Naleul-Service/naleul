// Features/Home/HomeCheckInForm.swift
//
// 홈 "오늘 회고" 시트의 목표형 칸 — 웹 features/home/ui/HomeCheckInForm.tsx.
// 목표 상세 "오늘 기록"(GoalDailyCheckInCard)과 같은 기록이에요 (PUT /goal-categories/{id}/check-in).
//  - 수치 목표면 그날 수치 (예: 64kg)
//  - 어땠는지 (잘했어요 · 그럭저럭 · 아쉬워요)
//  - 한 줄 회고
// 이미 남긴 기록이 있으면 채워진 채로 열리고, 저장하면 덮어써요. 업무형은 이 칸 대신 회고 글을 써요.

import SwiftUI
import UIKit

struct HomeCheckInForm: View {
    let goal: HomeGoalToday
    let checkIn: HomeCheckIn
    let date: String
    var onSaved: () -> Void = {}
    var onSkip: () -> Void = {}

    @State private var value: String
    @State private var mood: ReflectionMood?
    @State private var note: String
    @State private var saving = false
    @FocusState private var focus: Field?

    private enum Field { case value, note }
    private typealias L = GoalDetailLogic

    init(goal: HomeGoalToday, checkIn: HomeCheckIn, date: String,
         onSaved: @escaping () -> Void = {}, onSkip: @escaping () -> Void = {}) {
        self.goal = goal
        self.checkIn = checkIn
        self.date = date
        self.onSaved = onSaved
        self.onSkip = onSkip
        _value = State(initialValue: checkIn.value.map { L.fmtNum($0) } ?? "")
        _mood = State(initialValue: checkIn.mood)
        _note = State(initialValue: checkIn.note ?? "")
    }

    private var had: Bool { checkIn.written }
    private var name: String { (checkIn.metricName).flatMap { $0.isEmpty ? nil : $0 } ?? "수치" }

    private var valueNum: Double? {
        let t = value.trimmingCharacters(in: .whitespaces)
        return t.isEmpty ? nil : (Double(t) ?? .nan)
    }
    private var valueOk: Bool { valueNum.map { $0.isFinite } ?? true }
    private var trimmedNote: String { note.trimmingCharacters(in: .whitespacesAndNewlines) }
    /// 처음 쓰는 날은 하나라도 채워야 해요 (이미 남긴 기록은 비워서 지울 수도 있어요)
    private var canSave: Bool {
        valueOk && !saving && ((checkIn.hasMetric && valueNum != nil) || mood != nil || !trimmedNote.isEmpty || had)
    }

    /// 받침에 맞는 조사: 체중은 / 점수는
    private func josa(_ word: String, _ withFinal: String, _ without: String) -> String {
        guard let last = word.unicodeScalars.last, last.value >= 0xAC00, last.value <= 0xD7A3 else { return word + without }
        return word + ((last.value - 0xAC00) % 28 != 0 ? withFinal : without)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            if checkIn.hasMetric {
                VStack(alignment: .leading, spacing: 4) {
                    Text("\(josa(name, "은", "는")) 얼마예요?").font(.pt(13, .semibold)).foregroundStyle(Color.ink2)
                    HStack(spacing: 6) {
                        TextField(checkIn.currentValue.map { L.fmtNum($0) } ?? "", text: $value)
                            .keyboardType(.decimalPad)
                            .font(.pt(16, .semibold))
                            .inputStyle()
                            .frame(maxWidth: 180)
                            .focused($focus, equals: .value)
                            .onChange(of: value) {
                                let filtered = value.filter { "0123456789.-".contains($0) }
                                if filtered != value { value = filtered }
                            }
                            .accessibilityLabel("\(goal.goalName) \(name) 값")
                        if let u = checkIn.unit, !u.isEmpty {
                            Text(u).font(.pt(13)).foregroundStyle(Color.ink3)
                        }
                    }
                    if !valueOk {
                        Text("숫자로 입력해 주세요").font(.pt(12)).foregroundStyle(Color.danger)
                            .onAppear {
                                HomeTracker.shared.system(HomeEvent.checkinValueInvalid, ["goal_category_id": goal.goalCategoryId])
                            }
                    }
                }
            }

            VStack(alignment: .leading, spacing: 4) {
                Text("어땠어요?").font(.pt(13, .semibold)).foregroundStyle(Color.ink2)
                HStack(spacing: 6) {
                    ForEach(GoalMoodOption.all) { o in
                        let on = mood == o.value
                        Button {
                            UISelectionFeedbackGenerator().selectionChanged()
                            HomeTracker.shared.track(HomeEvent.checkinMoodSelect, [
                                "mood": o.value.rawValue, "mood_action": on ? "clear" : "select",
                            ])
                            mood = on ? nil : o.value
                        } label: {
                            HStack(spacing: 4) {
                                Text(o.emoji)
                                Text(o.label)
                            }
                            .font(.pt(13, .semibold))
                            .foregroundStyle(on ? Color.white : Color.ink2)
                            .frame(maxWidth: .infinity)
                            .frame(height: 40)
                            .background(on ? Color.brand : Color.surface, in: Capsule())
                            .overlay(Capsule().stroke(on ? Color.brand : Color.borderSubtle, lineWidth: 1))
                            .contentShape(Capsule())
                        }
                        .buttonStyle(.plain)
                        .accessibilityAddTraits(on ? .isSelected : [])
                    }
                }
            }

            VStack(alignment: .leading, spacing: 4) {
                Text("무엇을 했나요? (한 줄 회고)").font(.pt(13, .semibold)).foregroundStyle(Color.ink2)
                TextField(goal.prompt, text: $note, axis: .vertical)
                    .lineLimit(3...6)
                    .inputStyle(minHeight: 88)
                    .goalMaxLength($note, 500)
                    .focused($focus, equals: .note)
                    .onChange(of: note) { old, v in
                        // 처음 글자를 칠 때 한 번 (home.retro_input_start)
                        if old.isEmpty, !v.isEmpty, (checkIn.note ?? "").isEmpty {
                            HomeTracker.shared.track(HomeEvent.retroInputStart, [
                                "goal_category_id": goal.goalCategoryId, "form_type": "checkin",
                                "goal_mode": goal.goalMode?.rawValue,
                            ])
                        }
                    }
                    .accessibilityLabel("\(goal.goalName) 한 줄 회고")
            }

            HStack(spacing: 8) {
                NButton("건너뛰기", variant: .ghost, size: .sm) { onSkip() }
                Spacer(minLength: 0)
                Button { submit(method: "button") } label: {
                    HStack(spacing: 6) {
                        if saving {
                            ProgressView().controlSize(.small).tint(Color.onInk)
                        }
                        Text(had ? "고치고 다음" : "저장하고 다음")
                        NSymbol("arrow.right", size: 12, weight: .semibold)
                    }
                }
                .buttonStyle(.n(.primary, .sm))
                .disabled(!canSave)
            }
            Text("목표 상세의 '오늘 기록'과 같은 기록이에요.")
                .font(.pt(11))
                .foregroundStyle(Color.ink4)
        }
        .padding(14)
        .background(Color.brandSoft.opacity(0.3), in: RoundedRectangle(cornerRadius: Radius.xl))
        .overlay(RoundedRectangle(cornerRadius: Radius.xl).stroke(Color.borderSubtle, lineWidth: 1))
    }

    private func submit(method: String = "button") {
        guard canSave else { return }
        HomeTracker.shared.track(HomeEvent.checkinSaveClick, [
            "goal_category_id": goal.goalCategoryId, "has_metric": checkIn.hasMetric,
            "has_value": checkIn.hasMetric && valueNum != nil, "mood": mood?.rawValue,
            "note_length": trimmedNote.count, "is_edit": had, "submit_method": method,
            "view_date": date, "date_relation": HomeTracker.relation(date),
        ])
        let v: Double? = checkIn.hasMetric ? valueNum : nil
        let n: String? = trimmedNote.isEmpty ? nil : trimmedNote
        let md = mood
        let id = goal.goalCategoryId
        let day = date
        focus = nil
        saving = true
        Task { @MainActor in
            let r = await Mutation.run(invalidate: [.home, .goals], successMessage: "오늘 기록을 남겼어요.",
                                       errorFallback: "기록하지 못했어요. 다시 시도해 주세요.") {
                try await HomeAPI.checkIn(goalId: id, date: day, value: v, mood: md, note: n)
            }
            HomeTracker.shared.system(HomeEvent.retroSaveResult, [
                "retro_action": "checkin", "result": r == nil ? "fail" : "success", "goal_category_id": id,
            ])
            saving = false
            if r != nil {
                UINotificationFeedbackGenerator().notificationOccurred(.success)
                onSaved()
            }
        }
    }
}

#Preview("목표형 오늘 기록 — 수치 목표") {
    let home = HomeSampleData.homeToday(today: KST.today())
    let g = home.goals.first { $0.goalCategoryId == 3 }!
    return ScrollView {
        HomeCheckInForm(goal: g, checkIn: g.checkIn!, date: KST.today())
            .padding(16)
    }
    .canvasBackground()
}

#Preview("목표형 오늘 기록 — 이미 남김") {
    let home = HomeSampleData.homeToday(today: KST.today())
    let g = home.goals.first { $0.goalCategoryId == 1 }!
    return ScrollView {
        HomeCheckInForm(goal: g, checkIn: g.checkIn!, date: KST.today())
            .padding(16)
    }
    .canvasBackground()
}

#Preview("목표형 오늘 기록 — 수치 없는 목표") {
    let home = HomeSampleData.homeToday(today: KST.today())
    let g = home.goals.first { $0.goalCategoryId == 4 }!
    return ScrollView {
        HomeCheckInForm(goal: g, checkIn: g.checkIn!, date: KST.today())
            .padding(16)
    }
    .canvasBackground()
}
