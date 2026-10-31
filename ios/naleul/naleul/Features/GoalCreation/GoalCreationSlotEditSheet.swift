// Features/GoalCreation/GoalCreationSlotEditSheet.swift
//
// 정리 확인 화면에서 항목 하나 고치기 (목표 · 수치 · 이유 · 기한 · 실천 시간).
// 웹 원본: features/goal-creation/ui/review/SlotEditModal.tsx (Modal → 시트)
//
// 각 항목 폼은 "검증 통과 시 patch, 실패 시 에러 문구" 를 만드는 build() 를 가져요 (웹과 같은 구조).

import SwiftUI

struct GoalCreationSlotEditSheet: View {
    let slot: GoalCreationSlotKey
    let saving: Bool
    /// 서버에서 거절된 이유 (400 등)
    let serverError: String?
    let onClose: () -> Void
    let onSave: (GoalCreationSlotsPatch) -> Void

    @State private var localError: String?

    // 목표 · 이유
    @State private var goalStatement: String
    @State private var motivation: String
    @State private var approach: String
    // 수치
    @State private var metricName: String
    @State private var metricUnit: String
    @State private var metricCurrent: String
    @State private var metricTarget: String
    // 기한
    @State private var endDate: String
    @State private var startDate: String
    @State private var flexible: Bool
    // 실천 시간
    @State private var days: [GoalCreationWeekday]
    @State private var ranges: [GoalCreationTimeRange]
    @State private var unavailable: String
    @State private var workStyle: String

    init(slot: GoalCreationSlotKey, slots: GoalCreationSlots, saving: Bool, serverError: String?,
         onClose: @escaping () -> Void, onSave: @escaping (GoalCreationSlotsPatch) -> Void) {
        self.slot = slot
        self.saving = saving
        self.serverError = serverError
        self.onClose = onClose
        self.onSave = onSave
        _goalStatement = State(initialValue: slots.goalStatement.value ?? "")
        _motivation = State(initialValue: slots.motivation.value ?? "")
        _approach = State(initialValue: slots.approach.value ?? "")
        let m = slots.metric.value
        _metricName = State(initialValue: m?.name ?? "")
        _metricUnit = State(initialValue: m?.unit ?? "")
        _metricCurrent = State(initialValue: m?.currentValue.map { DraftPlanUtils.num($0) } ?? "")
        _metricTarget = State(initialValue: m?.targetValue.map { DraftPlanUtils.num($0) } ?? "")
        let d = slots.deadline.value
        _endDate = State(initialValue: d?.endDate ?? "")
        _startDate = State(initialValue: d?.startDate ?? "")
        _flexible = State(initialValue: d?.isFlexible ?? false)
        let p = slots.practicePreference.value
        _days = State(initialValue: p?.preferredDays ?? [])
        _ranges = State(initialValue: p?.preferredTimeRanges ?? [])
        _unavailable = State(initialValue: p?.unavailableNote ?? "")
        _workStyle = State(initialValue: p?.workStyleAnswer ?? "")
    }

    /// 기한은 내일 ~ 2년 뒤
    private var minDate: String { KST.addDays(KST.today(), 1) }
    private var maxDate: String { KST.addDays(KST.today(), 365 * 2) }

    var body: some View {
        SheetScaffold(title: slot.editTitle, dismissible: !saving, detents: [.large]) {
            VStack(alignment: .leading, spacing: 0) {
                form
                if let error = localError ?? serverError {
                    Text(error).font(.pt(14, .medium)).foregroundStyle(Color.danger).padding(.top, 12)
                }
            }
        } footer: {
            NButton("취소", variant: .secondary) { close() }
                .disabled(saving)
            NButton("저장", loading: saving) { submit() }
        }
    }

    @ViewBuilder
    private var form: some View {
        switch slot {
        case .goalStatement: goalForm
        case .metric: metricForm
        case .motivation: motivationForm
        case .deadline: deadlineForm
        case .approach: approachForm
        case .practicePreference: practiceForm
        }
    }

    // MARK: 폼

    private var goalForm: some View {
        GoalCreationField("이루고 싶은 목표") {
            TextField("예) 몸무게 10kg 감량", text: limited($goalStatement, 100))
                .inputStyle()
                .submitLabel(.done)
                .onSubmit { submit() }
        }
    }

    private var metricForm: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(alignment: .top, spacing: 12) {
                GoalCreationField("무엇을 잴까요?") {
                    TextField("체중", text: $metricName).inputStyle()
                }
                GoalCreationField("단위") {
                    TextField("kg", text: $metricUnit).inputStyle()
                }
                .frame(width: 96)
            }
            HStack(alignment: .top, spacing: 12) {
                GoalCreationField("지금") {
                    TextField("78", text: $metricCurrent).keyboardType(.decimalPad).inputStyle()
                }
                GoalCreationField("목표") {
                    TextField("68", text: $metricTarget).keyboardType(.decimalPad).inputStyle()
                }
            }
        }
    }

    private var motivationForm: some View {
        GoalCreationField("왜 이루고 싶나요?", hint: "이유가 구체적일수록 계획 문구에 동기 부여가 잘 담겨요.") {
            TextField("예) 건강검진에서 지방간이 나와서", text: limited($motivation, 300), axis: .vertical)
                .lineLimit(4...8)
                .inputStyle(minHeight: 112)
        }
    }

    private var approachForm: some View {
        GoalCreationField("어떻게 이루고 싶나요?", hint: "이미 정해 둔 순서 · 교재 · 꼭 할 일을 적으면 그대로 계획의 뼈대가 돼요.") {
            TextField("예) 이론 강의 2주 → 기출 5회독", text: limited($approach, 500), axis: .vertical)
                .lineLimit(4...8)
                .inputStyle(minHeight: 112)
        }
    }

    private var deadlineForm: some View {
        VStack(alignment: .leading, spacing: 16) {
            GoalCreationField("언제부터 시작할까요? (비우면 오늘부터)") {
                GoalCreationDateField(ymd: $startDate, min: KST.today(), max: maxDate, clearable: true)
            }
            GoalCreationField("언제까지 이루고 싶나요?") {
                GoalCreationDateField(ymd: $endDate, min: minDate, max: maxDate)
            }
            Button { flexible.toggle() } label: {
                HStack(spacing: 10) {
                    NSymbol(flexible ? "checkmark.square.fill" : "square", size: 18)
                        .foregroundStyle(flexible ? Color.textBrand : Color.ink4)
                    Text("상황에 따라 조금 늦어져도 괜찮아요").font(.pt(14)).foregroundStyle(Color.ink)
                }
            }
            .buttonStyle(.plain)
        }
    }

    private var practiceForm: some View {
        VStack(alignment: .leading, spacing: 20) {
            VStack(alignment: .leading, spacing: 6) {
                Text("실천하기 좋은 요일").font(.pt(13, .semibold)).foregroundStyle(Color.ink2)
                GoalCreationDayChips(days: $days)
            }
            VStack(alignment: .leading, spacing: 8) {
                Text("시간대").font(.pt(13, .semibold)).foregroundStyle(Color.ink2)
                ForEach(ranges.indices, id: \.self) { i in
                    HStack(spacing: 8) {
                        GoalCreationTimeField(hm: rangeBinding(i, start: true), clearable: false)
                        Text("~").foregroundStyle(Color.ink3)
                        GoalCreationTimeField(hm: rangeBinding(i, start: false), clearable: false)
                        IconButton(systemName: "xmark", size: 15, label: "시간대 삭제") {
                            if ranges.indices.contains(i) { ranges.remove(at: i) }
                        }
                    }
                }
                if ranges.count < 3 {
                    Button {
                        ranges.append(GoalCreationTimeRange(start: "19:00", end: "21:00"))
                    } label: {
                        HStack(spacing: 4) {
                            NSymbol("plus", size: 13, weight: .semibold)
                            Text("시간대 추가")
                        }
                        .font(.pt(14, .semibold))
                        .foregroundStyle(Color.textBrand)
                    }
                    .buttonStyle(.plain)
                }
            }
            GoalCreationField("안 되는 시간 (선택)") {
                TextField("예) 평일 9~18시 회사", text: limited($unavailable, 100)).inputStyle()
            }
            GoalCreationField("평소 일하는 방식 (선택)") {
                TextField("예) 미리미리 나눠서 하는 편", text: limited($workStyle, 100)).inputStyle()
            }
        }
    }

    // MARK: 도우미

    /// 웹 maxLength
    private func limited(_ b: Binding<String>, _ n: Int) -> Binding<String> {
        Binding(get: { b.wrappedValue }, set: { b.wrappedValue = String($0.prefix(n)) })
    }

    private func rangeBinding(_ i: Int, start: Bool) -> Binding<String> {
        Binding(
            get: { ranges.indices.contains(i) ? (start ? ranges[i].start : ranges[i].end) : "" },
            set: { v in
                guard ranges.indices.contains(i) else { return }
                if start { ranges[i].start = v } else { ranges[i].end = v }
            }
        )
    }

    private enum BuildResult {
        case patch(GoalCreationSlotsPatch)
        case error(String)
    }

    private func build() -> BuildResult {
        switch slot {
        case .goalStatement:
            let v = GoalCreationForm.trim(goalStatement)
            if v.isEmpty { return .error("목표를 입력해 주세요.") }
            return .patch(GoalCreationSlotsPatch(goalStatement: v))
        case .metric:
            let name = GoalCreationForm.trim(metricName)
            if name.isEmpty { return .error("무엇을 잴지 이름을 입력해 주세요. (예: 체중)") }
            let c = GoalCreationForm.toNumber(metricCurrent)
            let t = GoalCreationForm.toNumber(metricTarget)
            if c?.isNaN == true || t?.isNaN == true { return .error("수치는 숫자로 입력해 주세요.") }
            guard let target = t else { return .error("목표 수치를 입력해 주세요.") }
            let unit = GoalCreationForm.trim(metricUnit)
            return .patch(GoalCreationSlotsPatch(metric: GoalCreationMetricValue(
                name: name, currentValue: c, targetValue: target, unit: unit.isEmpty ? nil : unit)))
        case .motivation:
            let v = GoalCreationForm.trim(motivation)
            if v.isEmpty { return .error("이유를 입력해 주세요.") }
            return .patch(GoalCreationSlotsPatch(motivation: v))
        case .approach:
            let v = GoalCreationForm.trim(approach)
            if v.isEmpty { return .error("어떻게 하고 싶은지 적어 주세요. 정한 게 없으면 \"정한 계획 없음\"이라고 적어도 돼요.") }
            return .patch(GoalCreationSlotsPatch(approach: v))
        case .deadline:
            if endDate.isEmpty { return .error("날짜를 골라 주세요.") }
            if endDate < minDate { return .error("오늘 이후 날짜를 골라 주세요.") }
            if endDate > maxDate { return .error("기한은 최대 2년까지 정할 수 있어요.") }
            if !startDate.isEmpty && startDate < KST.today() { return .error("시작일은 오늘부터 고를 수 있어요.") }
            if !startDate.isEmpty && endDate <= startDate { return .error("마감일은 시작일 뒤여야 해요.") }
            return .patch(GoalCreationSlotsPatch(deadline: GoalCreationDeadlineValue(
                endDate: endDate, isFlexible: flexible, startDate: startDate.isEmpty ? nil : startDate)))
        case .practicePreference:
            for r in ranges {
                if r.start.isEmpty || r.end.isEmpty { return .error("시간대의 시작·끝 시각을 모두 정해 주세요.") }
                if r.start >= r.end { return .error("끝 시각은 시작 시각보다 늦어야 해요.") }
            }
            let un = GoalCreationForm.trim(unavailable)
            let ws = GoalCreationForm.trim(workStyle)
            return .patch(GoalCreationSlotsPatch(practicePreference: GoalCreationPracticePreference(
                // 요일 순서를 월→일로 정렬해서 보내요
                preferredDays: days.isEmpty ? nil : GoalCreationWeekday.allCases.filter { days.contains($0) },
                preferredTimeRanges: ranges.isEmpty ? nil : ranges,
                unavailableNote: un.isEmpty ? nil : un,
                workStyleAnswer: ws.isEmpty ? nil : ws)))
        }
    }

    private func submit() {
        GCTracker.shared.track(GCEvent.slotEditSave, ["slot_key": slot.rawValue])
        switch build() {
        case .error(let message):
            // 시트 안 검사에서 막힘 — 서버로 보내지 않아요
            GCTracker.shared.track(GCEvent.slotEditResult, [
                "slot_key": slot.rawValue, "result": "invalid", "validation_error": message,
            ])
            localError = message
        case .patch(let patch):
            localError = nil
            onSave(patch)
        }
    }

    private func close() {
        localError = nil
        GCTracker.shared.sheetClose = "cancel_button"
        onClose()
    }
}

#Preview {
    let slots = try! JSONDecoder().decode(GoalCreationSlots.self, from: Data(GoalCreationSamples.slotsJSON.utf8))
    return Color.canvas
        .sheet(isPresented: .constant(true)) {
            GoalCreationSlotEditSheet(slot: .practicePreference, slots: slots, saving: false, serverError: nil,
                                      onClose: {}, onSave: { _ in })
        }
}
