// Features/Goal/GoalDailyCheckInCard.swift
//
// 웹 features/goal/ui/progress/DailyCheckInCard.tsx — 목표 상세 맨 위 "오늘 기록".
//  - 수치 목표면 오늘 수치 (예: 64kg)
//  - 오늘 어땠는지 (잘했어요 · 그럭저럭 · 아쉬워요)
//  - 무엇을 했는지 한 줄 회고 (예: "점심에 마라탕 먹음")
// 남긴 기록은 "말로 고치기" AI 가 계획을 조정할 때 근거로 써요.

import SwiftUI
import UIKit

/// 그날 느낌 선택지 (웹 MOODS)
struct GoalMoodOption: Hashable, Identifiable {
    var id: ReflectionMood { value }
    let value: ReflectionMood
    let emoji: String
    let label: String

    static let all: [GoalMoodOption] = [
        GoalMoodOption(value: .GOOD, emoji: "😊", label: "잘했어요"),
        GoalMoodOption(value: .OK, emoji: "🙂", label: "그럭저럭"),
        GoalMoodOption(value: .BAD, emoji: "😣", label: "아쉬워요"),
    ]

    static func of(_ m: ReflectionMood?) -> GoalMoodOption? {
        guard let m else { return nil }
        return all.first { $0.value == m }
    }
}

/// 오늘 탭의 작은 요약 — 기록은 오른쪽 아래 ✏️ 버튼(또는 이 카드)을 눌러 시트에서 해요
@MainActor
struct GoalDailyCheckInCard: View {
    let goal: GoalCategory
    @Environment(GoalDetailViewModel.self) private var vm

    var body: some View {
        if let p = vm.progress {
            let today = GoalCheckInToday(p)
            if today.value != nil || today.reflection != nil {
                doneCard(p, today)
            } else {
                promptRow
            }
        }
    }

    /// 아직 안 남겼을 때: 한 줄 안내 (누르면 시트)
    private var promptRow: some View {
        Button {
            vm.track("goal_detail.checkin_open", ["entry_point": "prompt_card", "is_edit": false])
            vm.sheet = .checkIn
        } label: {
            HStack(spacing: 12) {
                NSymbol("square.and.pencil", size: 15, weight: .semibold)
                    .foregroundStyle(Color.textBrand)
                    .frame(width: 36, height: 36)
                    .background(Color.brandSoft, in: Circle())
                VStack(alignment: .leading, spacing: 2) {
                    Text("오늘 어땠나요?").font(.pt(15, .bold)).foregroundStyle(Color.ink)
                    Text("기록을 남기면 그래프와 AI 조정에 바로 반영돼요")
                        .font(.pt(12))
                        .foregroundStyle(Color.ink3)
                }
                Spacer(minLength: 0)
                NSymbol("chevron.right", size: 13, weight: .semibold).foregroundStyle(Color.ink4)
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.surface, in: RoundedRectangle(cornerRadius: Radius.card))
            .overlay(RoundedRectangle(cornerRadius: Radius.card).stroke(Color.borderSubtle, style: StrokeStyle(lineWidth: 1.5, dash: [5, 4])))
            .contentShape(RoundedRectangle(cornerRadius: Radius.card))
        }
        .buttonStyle(.plain)
    }

    /// 남겼을 때: 오늘 기록 요약 + 수정 · 계획 조정받기
    private func doneCard(_ p: GoalProgress, _ today: GoalCheckInToday) -> some View {
        let m = p.metric
        let name = (m?.name).flatMap { $0.isEmpty ? nil : $0 } ?? "수치"
        let mo = GoalMoodOption.of(today.reflection?.mood)
        return VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 6) {
                Image(systemName: "checkmark.circle.fill").font(.system(size: 15)).foregroundStyle(Color.success)
                Text("오늘 기록").font(.pt(15, .bold)).foregroundStyle(Color.ink)
                Text(GoalDetailLogic.md(p.today)).font(.pt(13)).foregroundStyle(Color.ink3)
                Spacer(minLength: 0)
                Button("수정") {
                    vm.track("goal_detail.checkin_open", ["entry_point": "edit_button", "is_edit": true])
                    vm.sheet = .checkIn
                }
                    .font(.pt(13, .semibold))
                    .foregroundStyle(Color.ink3)
                    .buttonStyle(.plain)
            }
            FlowLayout(spacing: 8) {
                if let v = today.value {
                    (Text("\(name) ").font(.pt(13)).foregroundColor(Color.ink3)
                     + Text(GoalDetailLogic.fmtNum(v) + (m?.unit ?? "")).font(.pt(16, .semibold)).foregroundColor(Color.ink))
                }
                if let mo {
                    Text("\(mo.emoji) \(mo.label)")
                        .font(.pt(13, .semibold))
                        .foregroundStyle(Color.ink)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 4)
                        .background(Color.subtle, in: Capsule())
                }
            }
            if let n = today.reflection?.note, !n.isEmpty {
                Text(n).font(.pt(14)).foregroundStyle(Color.ink2).lineSpacing(3).lineLimit(3)
            }
            // 기록을 남긴 뒤 — 쌓인 기록으로 계획을 조정받을 수 있다는 걸 알려줘요
            Button {
                vm.trackAdjustHint()
                vm.aiEditText = "최근 기록과 회고를 보고 계획을 조정해줘"
                vm.aiFocusTick += 1
            } label: {
                HStack(spacing: 4) {
                    AIBadge(size: .sm)
                    Text("최근 기록 보고 계획 조정받기")
                }
                .font(.pt(12, .semibold))
                .foregroundStyle(Color.textBrand)
            }
            .buttonStyle(.plain)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card()
    }
}

/// ✏️ 버튼으로 올라오는 "오늘 기록" 시트 — 수치 · 기분 · 한 줄 회고 + 최근 기록
@MainActor
struct GoalCheckInSheet: View {
    let goal: GoalCategory
    @Environment(GoalDetailViewModel.self) private var vm

    var body: some View {
        let today = vm.progress?.today ?? KST.today()
        SheetScaffold(title: "오늘 기록 · \(GoalDetailLogic.md(today))",
                      description: "남긴 기록은 그래프와 AI 계획 조정에 바로 반영돼요.",
                      detents: [.medium, .large]) {
            if let p = vm.progress {
                GoalCheckInBody(goalId: goal.goalCategoryId, p: p, sheetMode: true)
            } else {
                ProgressView().tint(Color.ink3).frame(maxWidth: .infinity, minHeight: 160)
            }
        }
    }
}

extension GoalDetailViewModel {
    /// '최근 기록 보고 계획 조정받기' — 문장을 채운 말로 고치기 시트가 열려요
    func trackAdjustHint() {
        let canAi = goal.map { !$0.isRecordGoal && !$0.isHabitGoal && !$0.isTemporary && $0.goalCategoryStatus != .COMPLETED } ?? false
        track("goal_detail.checkin_adjust_hint_click", ["has_ai_edit": canAi])
        track("goal_detail.ai_edit_open", ["entry_point": "adjust_hint", "is_prefilled": true])
    }

    /// 오늘 기록을 이미 남겼는지 (✏️ 버튼에 체크 표시)
    var checkedInToday: Bool {
        guard let p = progress else { return false }
        let t = GoalCheckInToday(p)
        return t.value != nil || t.reflection != nil
    }
}

/// 진행 데이터에서 오늘 기록 꺼내기
private struct GoalCheckInToday {
    let value: Double?
    let reflection: GoalReflection?

    init(_ p: GoalProgress) {
        value = p.metric?.logs.first { $0.date == p.today }?.value
        reflection = p.reflections?.first { $0.date == p.today }
    }

    /// 오늘 기록이 바뀌었는지 알아보는 열쇠
    var key: String {
        let v = value.map { String($0) } ?? ""
        let n = reflection?.note ?? ""
        let mo = reflection?.mood?.rawValue ?? ""
        return v + "|" + n + "|" + mo
    }
}

@MainActor
private struct GoalCheckInBody: View {
    let goalId: Int
    let p: GoalProgress
    /// 시트 안에서: 늘 입력 화면 · 카드 테두리 없음 · 저장하면 시트를 닫아요
    var sheetMode = false

    @Environment(GoalDetailViewModel.self) private var vm
    @FocusState private var noteFocused: Bool
    @State private var editing: Bool
    @State private var value: String
    @State private var mood: ReflectionMood?
    @State private var note: String
    @State private var saving = false
    /// 시트가 어떻게 닫혔는지 (saved · next — 없으면 X · 쓸어 닫기)
    @State private var outcome: String?
    /// 처음 값 (고쳤는지 보려고)
    private let initialKey: String

    private typealias L = GoalDetailLogic

    init(goalId: Int, p: GoalProgress, sheetMode: Bool = false) {
        self.goalId = goalId
        self.p = p
        self.sheetMode = sheetMode
        let today = GoalCheckInToday(p)
        let done = today.value != nil || today.reflection != nil
        _editing = State(initialValue: sheetMode || !done)
        _value = State(initialValue: today.value.map { L.fmtNum($0) } ?? "")
        _mood = State(initialValue: today.reflection?.mood)
        _note = State(initialValue: today.reflection?.note ?? "")
        initialKey = (today.value.map { L.fmtNum($0) } ?? "") + "|" + (today.reflection?.mood?.rawValue ?? "") + "|" + (today.reflection?.note ?? "")
    }

    private var isDirty: Bool { value + "|" + (mood?.rawValue ?? "") + "|" + note != initialKey }

    private var m: MetricProgress? { p.metric }
    private var today: GoalCheckInToday { GoalCheckInToday(p) }
    private var done: Bool { today.value != nil || today.reflection != nil }
    private var name: String { (m?.name).flatMap { $0.isEmpty ? nil : $0 } ?? "수치" }
    private var recent: [GoalReflection] { Array((p.reflections ?? []).filter { $0.date != p.today }.prefix(3)) }

    private var valueNum: Double? {
        let t = value.trimmingCharacters(in: .whitespaces)
        return t.isEmpty ? nil : (Double(t) ?? .nan)
    }
    private var valueOk: Bool { valueNum.map { $0.isFinite } ?? true }
    private var hasSomething: Bool {
        (m != nil && valueNum != nil) || mood != nil || !note.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || today.reflection != nil
    }
    private var canSave: Bool { valueOk && hasSomething && !saving }

    /// 받침에 맞는 조사: 체중은 / 점수는
    private func josa(_ word: String, _ withFinal: String, _ without: String) -> String {
        guard let last = word.unicodeScalars.last, last.value >= 0xAC00, last.value <= 0xD7A3 else { return word + without }
        return word + ((last.value - 0xAC00) % 28 != 0 ? withFinal : without)
    }

    var body: some View {
        if sheetMode {
            VStack(alignment: .leading, spacing: 18) {
                form
                if !recent.isEmpty {
                    recentList
                }
            }
            .onChange(of: valueOk) { _, ok in
                if !ok { vm.track("goal_detail.checkin_invalid") }
            }
            .onDisappear {
                if let m = GD.closeMethod(outcome) {
                    vm.track("goal_detail.checkin_close", ["close_method": m, "is_dirty": isDirty])
                }
            }
        } else {
            cardBody
        }
    }

    private var cardBody: some View {
        VStack(alignment: .leading, spacing: 12) {
            header
            if editing {
                form
            } else {
                summary
            }
            if !recent.isEmpty {
                recentList
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(editing ? Color.brandSoft.opacity(0.4) : Color.surface, in: RoundedRectangle(cornerRadius: Radius.card))
        .overlay(RoundedRectangle(cornerRadius: Radius.card).stroke(editing ? Color.borderFocus : Color.borderSubtle, lineWidth: editing ? 1.5 : 1))
    }

    private var headerHint: String {
        let lead = m != nil ? name + "와(과) 오늘 한 일을 남기면" : "오늘 한 일을 남기면"
        return lead + " 그래프와 AI 조정에 바로 반영돼요"
    }

    private var header: some View {
        HStack(spacing: 8) {
            NSymbol("square.and.pencil", size: 14, weight: .semibold)
                .foregroundStyle(.white)
                .frame(width: 32, height: 32)
                .background(Color.brand, in: Circle())
            VStack(alignment: .leading, spacing: 1) {
                HStack(alignment: .firstTextBaseline, spacing: 6) {
                    Text("오늘 기록").font(.pt(16, .semibold)).foregroundStyle(Color.ink)
                    Text(L.md(p.today)).font(.pt(13, .medium)).foregroundStyle(Color.ink3)
                }
                Text(headerHint)
                    .font(.pt(12))
                    .foregroundStyle(Color.ink3)
            }
            Spacer(minLength: 0)
            if !editing {
                NButton("수정", variant: .secondary, size: .sm, icon: "pencil") { editing = true }
            }
        }
    }

    @ViewBuilder private var form: some View {
        if let m {
            VStack(alignment: .leading, spacing: 4) {
                Text("오늘 \(josa(name, "은", "는")) 얼마예요?").font(.pt(13, .semibold)).foregroundStyle(Color.ink2)
                HStack(spacing: 6) {
                    TextField(L.fmtNum(m.currentValue), text: $value)
                        .keyboardType(.decimalPad)
                        .font(.pt(16, .semibold))
                        .inputStyle()
                        .frame(maxWidth: 180)
                        .onChange(of: value) {
                            let filtered = value.filter { "0123456789.-".contains($0) }
                            if filtered != value { value = filtered }
                        }
                        .accessibilityLabel("\(name) 값")
                    if let u = m.unit, !u.isEmpty {
                        Text(u).font(.pt(13)).foregroundStyle(Color.ink3)
                    }
                }
            }
        }

        VStack(alignment: .leading, spacing: 4) {
            Text("오늘 어땠어요?").font(.pt(13, .semibold)).foregroundStyle(Color.ink2)
            HStack(spacing: 6) {
                ForEach(GoalMoodOption.all) { o in
                    let on = mood == o.value
                    Button {
                        vm.track("goal_detail.checkin_mood_select", ["mood": o.value.rawValue, "mood_action": on ? "deselect" : "select"])
                        mood = on ? nil : o.value
                    } label: {
                        HStack(spacing: 4) {
                            Text(o.emoji)
                            Text(o.label)
                        }
                        .font(.pt(13, .semibold))
                        .foregroundStyle(on ? Color.white : Color.ink2)
                        .padding(.horizontal, 12)
                        .frame(height: sheetMode ? 44 : 36)
                        .frame(maxWidth: sheetMode ? .infinity : nil)
                        .background(on ? Color.brand : Color.surface, in: Capsule())
                        .overlay(Capsule().stroke(on ? Color.brand : Color.borderSubtle, lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(on ? .isSelected : [])
                }
            }
        }

        VStack(alignment: .leading, spacing: 4) {
            Text("무엇을 했나요? (한 줄 회고)").font(.pt(13, .semibold)).foregroundStyle(Color.ink2)
            TextField(m != nil ? "예) 점심에 마라탕 먹음, 저녁은 샐러드 · 30분 걸음" : "예) 영어 단어 50개 외움, 저녁 약속 때문에 인강은 못 들음",
                      text: $note, axis: .vertical)
                .lineLimit(sheetMode ? 3...6 : 2...5)
                .inputStyle(minHeight: sheetMode ? 88 : 64)
                .goalMaxLength($note, 500)
                .focused($noteFocused)
        }

        // 시트 안이면 바닥 버튼 줄로 (DESIGN_SPEC 13.2) · 카드 안이면 그 자리에
        if !valueOk {
            Text("숫자로 입력해 주세요").font(.pt(12)).foregroundStyle(Color.danger)
        }
        SheetActions(key: "\(done)|\(saving)|\(canSave)|\(sheetMode)") {
            NButton(done ? "기록 수정하기" : "기록하기", variant: .brand, loading: saving, fullWidth: sheetMode) { submit() }
                .disabled(!canSave)
            if done && !sheetMode {
                NButton("취소", variant: .ghost) { editing = false }
            }
        }
    }

    private var summary: some View {
        let mo = GoalMoodOption.of(today.reflection?.mood)
        return VStack(alignment: .leading, spacing: 8) {
            FlowLayout(spacing: 12) {
                if let v = today.value {
                    (Text("\(name) ").font(.pt(13)).foregroundColor(Color.ink3)
                     + Text(L.fmtNum(v) + (m?.unit ?? "")).font(.pt(16, .semibold)).foregroundColor(Color.ink))
                }
                if let mo {
                    Text("\(mo.emoji) \(mo.label)")
                        .font(.pt(13, .semibold))
                        .foregroundStyle(Color.ink)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 4)
                        .background(Color.subtle, in: Capsule())
                }
            }
            if let n = today.reflection?.note, !n.isEmpty {
                Text(n).font(.pt(14)).foregroundStyle(Color.ink2).lineSpacing(3)
            }
            // 기록을 남긴 뒤 — 쌓인 기록으로 계획을 조정받을 수 있다는 걸 알려줘요
            Button {
                vm.trackAdjustHint()
                vm.aiEditText = "최근 기록과 회고를 보고 계획을 조정해줘"
                vm.aiFocusTick += 1
            } label: {
                HStack(spacing: 4) {
                    AIBadge(size: .sm)
                    Text("최근 기록 보고 계획 조정받기")
                }
                .font(.pt(12, .semibold))
                .foregroundStyle(Color.textBrand)
                .padding(.vertical, 4)
            }
            .buttonStyle(.plain)
        }
    }

    private var recentList: some View {
        let logs = m?.logs ?? []
        return VStack(alignment: .leading, spacing: 6) {
            Rectangle().fill(Color.borderSoft).frame(height: 1).padding(.bottom, 6)
            HStack {
                Text("최근 기록").font(.pt(12, .semibold)).foregroundStyle(Color.ink3)
                Spacer(minLength: 0)
                Button {
                    // 목표 상세 안이라 화면을 새로 열지 않고 "회고" 탭으로 바꿔요
                    vm.track("goal_detail.recent_records_all_click")
                    if sheetMode {
                        outcome = "next"
                        vm.sheet = nil
                    }
                    vm.reflectionsTabRequested = true
                } label: {
                    Text("모두 보기").font(.pt(12, .semibold)).foregroundStyle(Color.textBrand)
                        .frame(minHeight: 28).contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
            ForEach(recent, id: \.date) { r in
                let v = logs.first { $0.date == r.date }?.value
                let mo = GoalMoodOption.of(r.mood)
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text("\(KST.monthOf(r.date))/\(KST.dayOfMonth(r.date))")
                        .foregroundStyle(Color.ink3)
                        .frame(width: 44, alignment: .leading)
                    if let mo { Text(mo.emoji).accessibilityLabel(mo.label) }
                    if let v {
                        Text(L.fmtNum(v) + (m?.unit ?? "")).fontWeight(.bold).foregroundStyle(Color.ink)
                    }
                    Text(r.note ?? "").foregroundStyle(Color.ink2).lineLimit(1)
                }
                .font(.pt(13))
            }
        }
    }

    private func submit() {
        guard canSave else { return }
        let v: Double? = m != nil ? valueNum : nil
        let trimmed = note.trimmingCharacters(in: .whitespacesAndNewlines)
        let n: String? = trimmed.isEmpty ? nil : trimmed
        let md = mood
        let id = goalId
        vm.track("goal_detail.checkin_submit", [
            "has_value": v != nil, "mood": md?.rawValue, "note_length": trimmed.count, "is_edit": done,
        ])
        saving = true
        Task { @MainActor in
            let r = await Mutation.run(invalidate: [.goals, .home], successMessage: "오늘 기록을 남겼어요.",
                                       errorFallback: "기록하지 못했어요. 다시 시도해 주세요.") {
                try await GoalEditAPI.checkIn(goalId: id, value: v, mood: md, note: n)
            }
            saving = false
            vm.track("goal_detail.checkin_result", GD.result(r != nil))
            if let r {
                editing = false
                outcome = "saved"
                if let p = r { vm.progress = p }
                if sheetMode {
                    UINotificationFeedbackGenerator().notificationOccurred(.success)
                    vm.sheet = nil
                }
            }
        }
    }
}

#Preview("요약 카드") {
    ScrollView {
        VStack(spacing: 16) {
            GoalDailyCheckInCard(goal: GoalPreviewData.goal)
        }
        .padding(16)
    }
    .canvasBackground()
    .environment(GoalDetailViewModel.preview())
    .environment(AppRouter.shared)
}

#Preview("기록 시트") {
    Color.canvas
        .sheet(isPresented: .constant(true)) {
            GoalCheckInSheet(goal: GoalPreviewData.goal)
                .environment(GoalDetailViewModel.preview())
                .environment(AppRouter.shared)
        }
}
