// Features/Goal/GoalAddScreen.swift
//
// 웹 app/(main)/goal/add/page.tsx + features/goal/ui/GoalAddView.tsx — 목표 추가.
// 목표는 성격에 따라 세 가지 (V52):
//  - 기본 생활 습관: 했는지만 체크하는 습관. 모든 사용자에게 하나씩 이미 있어서 습관 화면으로 보내요 (goal_mode = HABIT)
//  - 업무형: 회사 업무 · 취업 준비 · 동아리 등. 주제마다 마감을 둔 로드맵 + Task 보드 (RecordGoalForm)
//  - 목표형: 수치로 잴 수 있는 목표. AI 가 대화로 "어떻게 이룰지"를 묻고 루틴까지 계획 (router.startGoalCreation), 계획이 있으면 직접 입력
// initialMode 가 .record 면 업무형 폼, .manual 이면 생활형 직접 입력 폼을 바로 열어요.

import SwiftUI

@MainActor
struct GoalAddScreen: View {
    let initialMode: GoalAddMode?

    /// 열린 직접 만들기 시트 (2026-10: 아래 인라인 폼 → 바텀 시트)
    @State private var open: GoalAddMode?
    /// 기본 생활 습관 그릇 (처음 부르면 서버가 만들어요)
    @State private var habitHome: HabitHome?
    @State private var pageviewSent = false
    /// 방금 직접 만든 것 — "추가했어요 · 보러 갈까요?"
    @State private var created: GoalAddCreated?
    /// 습관 바로 추가 입력칸
    @State private var habitDraft = ""
    @State private var habitSaving = false
    @State private var habitSheet = false
    /// initialMode 시트를 한 번만 자동으로 열어요
    @State private var autoOpened = false
    @FocusState private var habitFocused: Bool
    @Environment(AppRouter.self) private var router

    /// 직접 만들기 시트 열기
    private func openForm(_ mode: GoalAddMode) {
        guard open != mode else { return }
        GD.track("goal_add.mode_open", [
            "goal_mode": mode == .record ? "RECORD" : "ACHIEVEMENT", "from_mode": open?.rawValue ?? "none",
            "presentation": "sheet",
        ])
        open = mode
    }

    init(initialMode: GoalAddMode?) {
        self.initialMode = initialMode
    }

    private static let habitExamples = ["영양제 먹기", "물 2L 마시기", "샤워하기", "집안일 하기", "금연 · 금주", "일기 쓰기"]
    private static let workExamples = ["취업 준비", "OO팀 업무", "동아리 활동", "사이드 프로젝트"]
    private static let lifeExamples = ["3개월에 5kg 감량", "SQLD 합격", "토익 900점", "1000만원 모으기", "올해 책 24권"]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("어떤 성격의 목표인가요?").font(.pt(14)).foregroundStyle(Color.ink3)

                habitCard
                workCard
                lifeCard
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .scrollDismissesKeyboard(.interactively)
        .canvasBackground()
        .reloadOn([.habits]) { habitHome = try? await HabitAPI.home() }
        .task {
            // 처음 들어올 때 정해진 폼이 있으면 화면이 자리 잡은 뒤 시트로 열어요
            guard let m = initialMode, !autoOpened, open == nil else { return }
            autoOpened = true
            try? await Task.sleep(nanoseconds: 350_000_000)
            if open == nil { open = m }
        }
        .onAppear {
            guard !pageviewSent else { return }
            pageviewSent = true
            GD.track("goal_add.pageview", ["initial_mode": initialMode?.rawValue ?? "none"])
        }
        .sheet(item: $open) { mode in
            GoalAddFormSheet(mode: mode, onCancel: { open = nil }, onCreated: { c in
                open = nil
                // 시트가 내려간 뒤에 물어봐요
                Task { @MainActor in
                    try? await Task.sleep(nanoseconds: 450_000_000)
                    created = c
                    GD.track("goal_add.created_prompt_view", ["goal_mode": c.mode, "goal_category_id": c.goalId])
                }
            })
        }
        .sheet(isPresented: $habitSheet) {
            HabitFormSheet(habit: nil, existingNames: habitHome?.habits.map(\.name) ?? []) { habitSheet = false }
        }
        .alert(created?.title ?? "", isPresented: Binding(get: { created != nil }, set: { if !$0 { created = nil } }),
               presenting: created) { c in
            Button("나중에", role: .cancel) {
                GD.track("goal_add.created_prompt_click", ["goal_mode": c.mode, "goal_category_id": c.goalId, "prompt_action": "stay"])
            }
            Button("보러 가기") {
                GD.track("goal_add.created_prompt_click", ["goal_mode": c.mode, "goal_category_id": c.goalId, "prompt_action": "go"])
                router.replaceWithGoal(c.goalId, created: c.mode != "HABIT")
            }
        } message: { c in
            Text(c.message)
        }
        .navigationTitle("목표 추가")
        .navigationBarTitleDisplayMode(.inline)
    }

    // MARK: 기본 생활 습관 (이미 하나씩 있어요 — 이 자리에서 바로 추가)

    private var habitCard: some View {
        VStack(alignment: .leading, spacing: 0) {
            NSymbol("checklist", size: 18, weight: .semibold)
                .foregroundStyle(Color.success)
                .frame(width: 44, height: 44)
                .background(Color.successSoft, in: RoundedRectangle(cornerRadius: Radius.xxl))
            FlowLayout(spacing: 8) {
                Text("기본 생활 습관").font(.pt(16, .semibold)).foregroundStyle(Color.ink)
                Badge("체크만", tone: .success)
            }
            .padding(.top, 16)
            Text("매일 챙겨야 하는데 잊기 쉬운 것들이에요. 마감 없이 했는지만 체크하고, 잔디로 쌓여요. 필요 없으면 쉬거나 지우면 돼요.")
                .font(.pt(14))
                .foregroundStyle(Color.ink3)
                .lineSpacing(3)
                .padding(.top, 6)
            // 예시를 누르면 입력칸에 채워요
            VStack(alignment: .leading, spacing: 6) {
                Text("눌러서 바로 추가").font(.pt(12, .medium)).foregroundStyle(Color.ink3)
                FlowLayout(spacing: 6) {
                    ForEach(Self.habitExamples, id: \.self) { x in
                        Button {
                            GD.track("goal_add.habit_example_click", ["example": x])
                            habitDraft = x
                            habitFocused = true
                        } label: {
                            Text(x)
                                .font(.pt(12))
                                .foregroundStyle(habitDraft == x ? Color.success : Color.ink2)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 4)
                                .background(habitDraft == x ? Color.successSoft : Color.subtle, in: Capsule())
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .padding(.top, 16)
            HStack(spacing: 8) {
                TextField("습관 이름 (예: 영양제 먹기)", text: $habitDraft)
                    .inputStyle()
                    .focused($habitFocused)
                    .submitLabel(.done)
                    .onSubmit { Task { await addHabit(method: "keyboard_done") } }
                    .goalMaxLength($habitDraft, 30)
                NButton("추가", variant: .secondary, size: .md, loading: habitSaving) {
                    Task { await addHabit(method: "button") }
                }
                .disabled(habitDraft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || habitSaving)
            }
            .padding(.top, 12)
            HStack(spacing: 16) {
                Button {
                    GD.track("goal_add.habit_detail_form_click")
                    habitSheet = true
                } label: {
                    Text("요일 · 알림까지 정하기")
                        .font(.pt(13, .medium))
                        .foregroundStyle(Color.ink3)
                }
                .buttonStyle(.plain)
                Spacer(minLength: 0)
                if let home = habitHome, !home.habits.isEmpty {
                    Button {
                        GD.track("goal_add.habit_click", ["habit_count": home.habits.count])
                        router.openGoal(home.goalCategoryId)
                    } label: {
                        HStack(spacing: 4) {
                            Text("습관 \(home.habits.count)개 보기")
                            NSymbol("arrow.right", size: 12, weight: .semibold)
                        }
                        .font(.pt(13, .semibold))
                        .foregroundStyle(Color.success)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.top, 12)
        }
        .modifier(GoalAddCardStyle(selected: false))
    }

    /// 기본 생활 습관 바로 추가 — 매일 · 알림 없이 (자세한 건 습관 화면이나 "요일 · 알림까지 정하기")
    private func addHabit(method: String) async {
        let name = habitDraft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty, !habitSaving else { return }
        let existing = habitHome?.habits.map(\.name) ?? []
        GD.track("goal_add.habit_quick_add", [
            "from_example": Self.habitExamples.contains(name), "habit_count": existing.count, "submit_method": method,
        ])
        habitSaving = true
        let input = HabitInput(name: name, emoji: "", repeatDays: [], remindTime: nil)
        let r = await Mutation.run(invalidate: [.habits, .home, .timetable]) {
            try await HabitAPI.create(input)
        }
        habitSaving = false
        GD.track("goal_add.create_result", GD.result(r != nil).merging(["goal_mode": "HABIT"]) { $1 })
        guard r != nil else { return }
        habitDraft = ""
        habitFocused = false
        let home = try? await HabitAPI.home()
        if let home { habitHome = home }
        if let gid = home?.goalCategoryId ?? habitHome?.goalCategoryId {
            created = GoalAddCreated(goalId: gid, mode: "HABIT", title: "'\(name)' 습관을 추가했어요",
                                     message: "습관 화면으로 가서 확인할까요?")
            GD.track("goal_add.created_prompt_view", ["goal_mode": "HABIT", "goal_category_id": gid])
        } else {
            Toast.success("'\(name)' 습관을 추가했어요.")
        }
    }

    // MARK: 업무형

    private var workCard: some View {
        let selected = open == .record
        return VStack(alignment: .leading, spacing: 0) {
            Image(systemName: "briefcase")
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(Color.textBrand)
                .frame(width: 44, height: 44)
                .background(Color.actionSecondaryBg, in: RoundedRectangle(cornerRadius: Radius.xl))
            FlowLayout(spacing: 8) {
                Text("업무형").font(.pt(16, .semibold)).foregroundStyle(Color.ink)
                Badge("Task 관리", tone: .neutral)
            }
            .padding(.top, 16)
            Text("수치보다 해야 할 일이 많은 일이에요. 취업 준비 · 동아리 · 회사 업무처럼 단계(세부 목표)마다 마감을 정하고 남은 일을 체크해요.")
                .font(.pt(14))
                .foregroundStyle(Color.ink3)
                .lineSpacing(3)
                .padding(.top, 6)
            examples(Self.workExamples)
            Button { openForm(.record) } label: {
                HStack(spacing: 4) {
                    Text("업무 목표 만들기")
                    NSymbol("arrow.right", size: 13, weight: .semibold)
                }
                .font(.pt(14, .semibold))
                .foregroundStyle(Color.ink)
                .padding(.vertical, 4)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .padding(.top, 20)
            .accessibilityAddTraits(selected ? .isSelected : [])
        }
        .modifier(GoalAddCardStyle(selected: selected))
    }

    // MARK: 생활형

    private var lifeCard: some View {
        let selected = open == .manual
        return VStack(alignment: .leading, spacing: 0) {
            Image(systemName: "leaf")
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(Color.textBrand)
                .frame(width: 44, height: 44)
                .background(Color.brandSoft, in: RoundedRectangle(cornerRadius: Radius.xxl))
            FlowLayout(spacing: 8) {
                Text("목표형").font(.pt(16, .semibold)).foregroundStyle(Color.ink)
                Badge("수치 · 기한", tone: .brand)
            }
            .padding(.top, 16)
            Text("숫자로 잴 수 있는 목표예요. AI가 지금 상황과 계획을 물어본 뒤, 오늘 어디까지 하면 되는지 그래프와 루틴으로 짜 드려요.")
                .font(.pt(14))
                .foregroundStyle(Color.ink3)
                .lineSpacing(3)
                .padding(.top, 6)
            examples(Self.lifeExamples)
            VStack(alignment: .leading, spacing: 10) {
                NButton("AI와 계획 세우기", variant: .ai, size: .sm, icon: "sparkles") {
                    GD.track("goal_add.ai_click", ["form_open": open?.rawValue ?? "none"])
                    router.startGoalCreation()
                }
                Button { openForm(.manual) } label: {
                    HStack(spacing: 4) {
                        NSymbol("pencil.line", size: 12, weight: .medium)
                        Text("직접 입력하기")
                    }
                    .font(.pt(13, .medium))
                    .foregroundStyle(Color.ink3)
                    .padding(.vertical, 4)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(selected ? .isSelected : [])
            }
            .padding(.top, 20)
        }
        .modifier(GoalAddCardStyle(selected: selected))
    }

    private func examples(_ items: [String]) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("이런 걸 담아요").font(.pt(12, .medium)).foregroundStyle(Color.ink3)
            FlowLayout(spacing: 6) {
                ForEach(items, id: \.self) { x in
                    Text(x)
                        .font(.pt(12))
                        .foregroundStyle(Color.ink2)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 4)
                        .background(Color.subtle, in: Capsule())
                }
            }
        }
        .padding(.top, 16)
    }
}

private struct GoalAddCardStyle: ViewModifier {
    let selected: Bool

    func body(content: Content) -> some View {
        content
            .padding(20)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.surface, in: RoundedRectangle(cornerRadius: Radius.card))
            .overlay(RoundedRectangle(cornerRadius: Radius.card).stroke(selected ? Color.ink : Color.line, lineWidth: selected ? 2 : 1))
    }
}

// MARK: - 생활형 직접 만들기 폼 (웹 ManualGoalForm)

/// 직접 만든 것 — "추가했어요 · 보러 갈까요?"
struct GoalAddCreated: Identifiable, Equatable {
    var id: Int { goalId }
    let goalId: Int
    /// ACHIEVEMENT · RECORD · HABIT
    let mode: String
    let title: String
    let message: String
}

extension GoalAddMode: Identifiable {
    var id: String { rawValue }
}

/// 목표형 · 업무형 직접 만들기 바텀 시트 (2026-10)
@MainActor
private struct GoalAddFormSheet: View {
    let mode: GoalAddMode
    let onCancel: () -> Void
    let onCreated: (GoalAddCreated) -> Void

    var body: some View {
        SheetScaffold(title: mode == .record ? "업무형 목표 만들기" : "목표형 직접 만들기",
                      description: mode == .record ? "세부 목표마다 마감을 정하고 남은 일을 체크해요" : "기간과 수치를 정해 목표를 만들어요",
                      detents: [.large]) {
            if mode == .record {
                RecordGoalForm(onCancel: onCancel, analytics: true, onCreated: { g in
                    onCreated(GoalAddCreated(goalId: g.goalCategoryId, mode: "RECORD",
                                             title: "'\(g.goalCategoryName)' 업무형 목표를 만들었어요",
                                             message: "목표 화면으로 가서 세부 목표와 마감부터 정해 볼까요?"))
                })
            } else {
                GoalManualForm(onCancel: onCancel, onCreated: { g in
                    onCreated(GoalAddCreated(goalId: g.goalCategoryId, mode: "ACHIEVEMENT",
                                             title: "'\(g.goalCategoryName)' 목표를 만들었어요",
                                             message: "목표 화면으로 가서 루틴 · 마일스톤 · Task를 추가해 볼까요?"))
                })
                .goalSheetForm()
            }
        }
    }
}

@MainActor
private struct GoalManualForm: View {
    let onCancel: () -> Void
    /// 만든 뒤 — nil 이면 바로 목표 상세로
    var onCreated: ((GoalCategory) -> Void)? = nil

    @Environment(AppRouter.self) private var router

    @State private var colors: [UserColor] = []
    @State private var emoji = ""
    @State private var name = ""
    @State private var start = KST.today()
    @State private var end = KST.addDays(KST.today(), 90)
    @State private var colorId: Int? = nil
    @State private var motive = ""
    @State private var metricName = ""
    @State private var metricUnit = ""
    @State private var startValue = ""
    @State private var targetValue = ""
    @State private var showMetric = false
    @State private var kind: GoalKindValue? = nil
    @State private var subs: [String] = []
    @State private var subDraft = ""
    @State private var saving = false

    private var effectiveColor: Int? { colorId ?? GoalDetailLogic.colorIdOf(colors, nil) }
    private var nums: [Double?] { [GoalDetailLogic.toNum(startValue), GoalDetailLogic.toNum(targetValue)] }
    private var trimmedName: String { name.trimmingCharacters(in: .whitespacesAndNewlines) }

    private var error: String? {
        if trimmedName.isEmpty { return nil }
        if start.isEmpty || end.isEmpty { return "기간을 입력해 주세요." }
        if start > end { return "종료일이 시작일보다 빨라요." }
        if nums.contains(where: { $0?.isNaN == true }) { return "수치는 숫자로 입력해 주세요." }
        if !GoalKind.isComplete(kind) { return "카테고리를 골라 주세요. (기타는 이름 1~10자)" }
        return nil
    }

    private var valid: Bool { !trimmedName.isEmpty && error == nil && effectiveColor != nil }

    private var hasInput: Bool {
        !trimmedName.isEmpty || !emoji.isEmpty || !motive.trimmingCharacters(in: .whitespaces).isEmpty
            || !subs.isEmpty || !subDraft.isEmpty || kind != nil || showMetric
    }

    var body: some View {
        GoalInlineForm(submitLabel: "목표 만들기", saving: saving, valid: valid, error: error,
                       onCancel: {
                           GD.track("goal_add.manual_cancel", ["has_input": hasInput])
                           onCancel()
                       }, onSubmit: submit) {
            HStack(alignment: .top, spacing: 8) {
                GoalFormField("이모지") {
                    TextField("🎯", text: $emoji)
                        .multilineTextAlignment(.center)
                        .inputStyle()
                        .goalMaxLength($emoji, 16)
                }
                .frame(width: 72)
                GoalFormField("목표 이름") {
                    TextField("예: 토익 900점 달성", text: $name)
                        .inputStyle()
                        .goalMaxLength($name, 50)
                }
            }
            GoalFormField("카테고리") {
                GoalKindPicker(value: $kind, compact: true)
            }
            HStack(alignment: .top, spacing: 8) {
                GoalFormField("시작일") { GoalDateInput(value: $start) }
                GoalFormField("종료일") { GoalDateInput(value: $end) }
            }
            GoalFormField("색상") {
                ColorPickerRow(colors: colors, selectedId: Binding(get: { effectiveColor }, set: { colorId = $0 }))
            }
            GoalFormField("이 목표를 시작한 이유 (선택)") {
                TextField("", text: $motive, axis: .vertical)
                    .lineLimit(2...6)
                    .inputStyle()
                    .goalMaxLength($motive, 500)
            }
            subGoalsField
            metricFields
            Text("만든 뒤 목표 상세에서 루틴·마일스톤·Task를 바로 추가할 수 있어요.")
                .font(.pt(12))
                .foregroundStyle(Color.ink3)
        }
        .task {
            if colors.isEmpty { colors = (try? await ColorAPI.userColors()) ?? [] }
        }
        .onChange(of: error) { _, e in
            if let e { GD.track("goal_add.manual_error", ["validation_error": e]) }
        }
    }

    /// 세부 목표: 완료(return) 키로 하나씩
    private var subGoalsField: some View {
        GoalFormField("세부 목표 (선택 · 완료 키로 추가)") {
            VStack(alignment: .leading, spacing: 8) {
                if !subs.isEmpty {
                    FlowLayout(spacing: 6) {
                        ForEach(subs, id: \.self) { s in
                            HStack(spacing: 4) {
                                Text(s).font(.pt(13, .semibold))
                                Button { subs.removeAll { $0 == s } } label: {
                                    NSymbol("xmark", size: 10, weight: .bold)
                                }
                                .buttonStyle(.plain)
                                .accessibilityLabel("\(s) 빼기")
                            }
                            .foregroundStyle(Color.textBrand)
                            .padding(.leading, 10)
                            .padding(.trailing, 8)
                            .frame(height: 28)
                            .background(Color.brandSoft, in: Capsule())
                        }
                    }
                }
                HStack(spacing: 6) {
                    TextField(subs.isEmpty ? "예: LC 연습, 단어 외우기" : "", text: $subDraft)
                        .inputStyle()
                        .goalMaxLength($subDraft, 30)
                        .submitLabel(.next)
                        .onSubmit { addSub() }
                        .accessibilityLabel("세부 목표 추가")
                    if !subDraft.trimmingCharacters(in: .whitespaces).isEmpty {
                        NButton("추가", variant: .secondary, size: .sm) { addSub() }
                    }
                }
            }
        }
    }

    @ViewBuilder private var metricFields: some View {
        if showMetric {
            VStack(alignment: .leading, spacing: 8) {
                HStack(alignment: .top, spacing: 8) {
                    GoalFormField("수치 이름") {
                        TextField("체중", text: $metricName).inputStyle().goalMaxLength($metricName, 30)
                    }
                    GoalFormField("단위") {
                        TextField("kg", text: $metricUnit).inputStyle().goalMaxLength($metricUnit, 10)
                    }
                }
                HStack(alignment: .top, spacing: 8) {
                    GoalFormField("시작") {
                        TextField("80", text: $startValue).keyboardType(.decimalPad).inputStyle()
                    }
                    GoalFormField("목표") {
                        TextField("72", text: $targetValue).keyboardType(.decimalPad).inputStyle()
                    }
                }
            }
        } else {
            Button("+ 수치 목표 추가 (예: 체중 80 → 72kg)") {
                GD.track("goal_add.manual_metric_add_click")
                showMetric = true
            }
                .font(.pt(13, .semibold))
                .foregroundStyle(Color.textBrand)
                .buttonStyle(.plain)
        }
    }

    private func addSub() {
        let v = subDraft.trimmingCharacters(in: .whitespaces)
        guard !v.isEmpty else { return }
        if !subs.contains(v) && subs.count < 10 { subs.append(String(v.prefix(30))) }
        subDraft = ""
    }

    private func submit() {
        guard valid, !saving, let color = effectiveColor else { return }
        // 입력칸에 적어 두고 추가를 안 누른 것도 넣어요
        var allSubs = subs
        let draft = subDraft.trimmingCharacters(in: .whitespaces)
        if !draft.isEmpty && !subs.contains(draft) { allSubs.append(draft) }
        let trimmedMotive = motive.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedEmoji = emoji.trimmingCharacters(in: .whitespacesAndNewlines)
        let metricOn = showMetric && !metricName.trimmingCharacters(in: .whitespaces).isEmpty
        let input = GoalCreateInput(
            goalCategoryName: trimmedName,
            goalCategoryStartDate: start,
            goalCategoryEndDate: end,
            colorId: color,
            motive: trimmedMotive.isEmpty ? nil : trimmedMotive,
            emoji: trimmedEmoji.isEmpty ? nil : trimmedEmoji,
            metricName: metricOn ? metricName.trimmingCharacters(in: .whitespaces) : nil,
            metricUnit: metricOn ? metricUnit.trimmingCharacters(in: .whitespaces) : nil,
            startValue: metricOn ? nums[0] : nil,
            targetValue: metricOn ? nums[1] : nil,
            hasMetric: metricOn,
            kind: kind,
            subGoals: allSubs
        )
        let typedValue = !startValue.trimmingCharacters(in: .whitespaces).isEmpty || !targetValue.trimmingCharacters(in: .whitespaces).isEmpty
        GD.track("goal_add.manual_submit", [
            "goal_type": kind?.goalType.rawValue, "goal_sub_type": kind?.goalSubType.rawValue,
            "period_days": KST.diffDays(start, end) + 1, "has_metric": metricOn,
            "metric_dropped": showMetric && !metricOn && typedValue,
            "has_motive": input.motive != nil, "has_emoji": input.emoji != nil, "color_changed": colorId != nil,
            "sub_goal_count": allSubs.count,
        ])
        saving = true
        Task { @MainActor in
            let r = await Mutation.run(invalidate: [.goals],
                                       success: { (res: (goal: GoalCategory, failed: Int)) in
                                           res.failed > 0 ? nil : "목표를 만들었어요. 루틴·마일스톤·Task도 바로 추가해 보세요."
                                       }) {
                try await GoalEditAPI.createGoal(input)
            }
            saving = false
            GD.track("goal_add.create_result", GD.result(r != nil).merging([
                "goal_mode": "ACHIEVEMENT", "goal_category_id": r?.goal.goalCategoryId,
            ]) { $1 })
            if let r {
                if r.failed > 0 {
                    Toast.error("목표는 만들었지만 세부 목표 \(r.failed)개를 만들지 못했어요. 상세 화면에서 다시 추가해 주세요.")
                }
                if let onCreated { onCreated(r.goal) } else { router.replaceWithGoal(r.goal.goalCategoryId) }
            }
        }
    }
}

#Preview("목표 추가") {
    NavigationStack {
        GoalAddScreen(initialMode: nil)
    }
    .environment(AppRouter.shared)
}
