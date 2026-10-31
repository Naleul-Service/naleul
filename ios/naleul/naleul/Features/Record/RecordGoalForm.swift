// Features/Record/RecordGoalForm.swift
//
// 웹 features/record/ui/RecordGoalForm.tsx (+ goal/edit/inline.tsx InlineForm 모양)
// 업무형 목표 만들기 (goal_mode = RECORD).
// 회사 업무 · 취업 준비 · 동아리처럼 종류가 다양해서 종류를 고르지 않고 이름만 받아요.
// 기한 · 수치 · 세부 목표도 묻지 않아요 — 만든 뒤 주제마다 마감을 정하면 로드맵이 그려져요 (V53).
//
// 웹은 만든 뒤 router.push(`/goal/{id}`) → iOS 는 지금 화면(목표 추가)을 목표 상세로 바꿔요 (replaceWithGoal).
// 웹의 Enter 저장 · Esc 취소 단축키는 아이폰에 없어서 버튼만 둬요.

import SwiftUI

/// 이름 예시 — 누르면 이름과 이모지가 같이 채워져요 (웹 EXAMPLES)
private struct RecordNameExample: Hashable {
    let emoji: String
    let name: String

    static let all: [RecordNameExample] = [
        RecordNameExample(emoji: "💼", name: "회사 업무"),
        RecordNameExample(emoji: "🎯", name: "취업 준비"),
        RecordNameExample(emoji: "🚀", name: "사이드 프로젝트"),
        RecordNameExample(emoji: "🎸", name: "동아리 활동"),
        RecordNameExample(emoji: "🌍", name: "대외활동"),
        RecordNameExample(emoji: "📚", name: "스터디 운영"),
        RecordNameExample(emoji: "🎓", name: "졸업 프로젝트"),
    ]
}

private let recordEmojis = ["💼", "🎯", "🚀", "🎸", "🌍", "📚", "🎓", "🧑‍💻", "📊", "✍️"]

struct RecordGoalForm: View {
    let onCancel: () -> Void
    /// 목표 추가 화면에서 쓸 때 goal_add.record_* 이벤트를 보내요
    var analytics = false
    /// 만든 뒤 (목표 추가 시트 — "보러 갈까요?"를 묻게). nil 이면 바로 목표 상세로
    var onCreated: ((GoalCategory) -> Void)? = nil

    @State private var name = ""
    @State private var description = ""
    @State private var emoji = RecordNameExample.all[0].emoji
    @State private var colorId: Int?
    @State private var styleOpen = false
    @State private var colors: [UserColor] = []
    @State private var saving = false
    /// 마지막으로 누른 예시 (이름을 그 뒤로 고쳤는지 보려고)
    @State private var pickedExample: RecordNameExample?
    @FocusState private var nameFocused: Bool

    init(onCancel: @escaping () -> Void, analytics: Bool = false, onCreated: ((GoalCategory) -> Void)? = nil) {
        self.onCancel = onCancel
        self.analytics = analytics
        self.onCreated = onCreated
    }

    private func track(_ event: String, _ props: EventProps = [:]) {
        if analytics { GD.track(event, props) }
    }

    private var shownEmoji: String { emoji }
    private var value: String { name.trimmingCharacters(in: .whitespacesAndNewlines) }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            nameField
            descriptionField
            styleSection
            exampleNote
            footer
        }
        .padding(16)
        .background(Color.brandSoft.opacity(0.4), in: RoundedRectangle(cornerRadius: Radius.xxl))
        .overlay(RoundedRectangle(cornerRadius: Radius.xxl).stroke(Color.borderSubtle, lineWidth: 1))
        .reloadOn([.userColors]) {
            colors = (try? await ColorAPI.userColors()) ?? []
        }
        .onAppear { nameFocused = true }
    }

    // MARK: ① 이름

    private var nameField: some View {
        VStack(alignment: .leading, spacing: 4) {
            RecordFormLabel(text: "이름")
            TextField("예: 취업 준비 · 동아리 활동 · OO팀 업무", text: $name)
                .focused($nameFocused)
                .submitLabel(.done)
                .onSubmit { Task { await submit(method: "keyboard_done") } }
                .recordInlineInput()
                .onChange(of: name) {
                    if name.count > 40 { name = String(name.prefix(40)) }
                }
            FlowLayout(spacing: 6) {
                ForEach(RecordNameExample.all, id: \.self) { ex in
                    Button {
                        track("goal_add.record_example_click", ["example_index": RecordNameExample.all.firstIndex(of: ex)])
                        pickedExample = ex
                        name = ex.name
                        emoji = ex.emoji
                    } label: {
                        Text("\(ex.emoji) \(ex.name)")
                            .font(.pt(12))
                            .foregroundStyle(Color.ink3)
                            .padding(.horizontal, 10)
                            .frame(height: 28)
                            .overlay(Capsule().stroke(Color.line, lineWidth: 1))
                            .contentShape(Capsule())
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.top, 4)
        }
    }

    // MARK: ② 한 줄 설명

    private var descriptionField: some View {
        VStack(alignment: .leading, spacing: 4) {
            RecordFormLabel(text: "무엇을 하나요? (선택)")
            TextField("예: 하반기 백엔드 개발자 공채 지원", text: $description)
                .recordInlineInput()
                .onChange(of: description) {
                    if description.count > 100 { description = String(description.prefix(100)) }
                }
        }
    }

    // MARK: ③ 꾸미기 — 기본값이 있어서 접어 둬요

    private var styleSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Button {
                track("goal_add.record_style_toggle", ["expanded": !styleOpen])
                withAnimation(.easeOut(duration: 0.15)) { styleOpen.toggle() }
            } label: {
                HStack(spacing: 6) {
                    Text(shownEmoji).font(.system(size: 16)).accessibilityHidden(true)
                    Text("이모지·색상 바꾸기").font(.pt(13, .semibold))
                    NSymbol("chevron.down", size: 12, weight: .semibold)
                        .rotationEffect(.degrees(styleOpen ? 180 : 0))
                }
                .foregroundStyle(Color.ink2)
            }
            .buttonStyle(.plain)
            .accessibilityValue(styleOpen ? "펼침" : "접힘")

            if styleOpen {
                HStack(spacing: 6) {
                    ForEach(recordEmojis, id: \.self) { e in
                        let on = shownEmoji == e
                        Button { emoji = e } label: {
                            Text(e)
                                .font(.system(size: 18))
                                .frame(width: 36, height: 36)
                                .background(on ? Color.subtle : Color.clear, in: RoundedRectangle(cornerRadius: Radius.xl))
                                .overlay(RoundedRectangle(cornerRadius: Radius.xl).stroke(on ? Color.ink : Color.line, lineWidth: 1))
                        }
                        .buttonStyle(.plain)
                        .accessibilityAddTraits(on ? .isSelected : [])
                    }
                }
                .accessibilityLabel("이모지")
                if !colors.isEmpty {
                    VStack(alignment: .leading, spacing: 4) {
                        RecordFormLabel(text: "색상 (선택 안 하면 자동)")
                        ColorPickerRow(colors: colors, selectedId: $colorId)
                    }
                }
            }
        }
    }

    // MARK: 예시 — 만든 뒤 어떻게 쓰는지

    private var exampleNote: some View {
        (Text("전체 마감 없이 만들어요. 만든 뒤 ")
                + Text("포트폴리오 → 이력서 제출 → 면접").bold()
                + Text("처럼 세부 목표마다 마감을 정하면 마감 순서대로 로드맵이 그려지고, 세부 목표마다 남은 할 일을 체크해요."))
            .font(.pt(13))
            .foregroundStyle(Color.ink2)
            .lineSpacing(3)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.subtle, in: RoundedRectangle(cornerRadius: Radius.xl))
    }

    // MARK: 버튼

    private var footer: some View {
        HStack(spacing: 8) {
            Spacer(minLength: 0)
            NButton("취소", variant: .ghost, size: .sm) {
                track("goal_add.record_cancel", ["has_input": !value.isEmpty || !description.isEmpty])
                onCancel()
            }
                .disabled(saving)
            NButton("업무형 목표 만들기", size: .sm, loading: saving) {
                Task { await submit(method: "button") }
            }
            .disabled(value.isEmpty)
        }
        .padding(.top, 4)
    }

    @MainActor
    private func submit(method: String) async {
        guard !value.isEmpty, !saving else { return }
        let desc0 = description.trimmingCharacters(in: .whitespacesAndNewlines)
        track("goal_add.record_submit", [
            "name_from_example": pickedExample?.name == value, "has_description": !desc0.isEmpty,
            "emoji_changed": shownEmoji != (pickedExample?.emoji ?? RecordNameExample.all[0].emoji),
            "color_changed": colorId != nil, "submit_method": method,
        ])
        saving = true
        defer { saving = false }
        let desc = description.trimmingCharacters(in: .whitespacesAndNewlines)
        let result = await Mutation.run(invalidate: [.goals],
                                        successMessage: "업무형 목표를 만들었어요. 세부 목표와 마감부터 정해 보세요.") {
            try await RecordAPI.createRecordGoal(name: value, emoji: shownEmoji, colorId: colorId,
                                                 description: desc.isEmpty ? nil : desc)
        }
        let created = result ?? nil
        track("goal_add.create_result", GD.result(result != nil).merging([
            "goal_mode": "RECORD", "goal_category_id": created?.goalCategoryId,
        ]) { $1 })
        if let goal = created {
            if let onCreated { onCreated(goal) } else { AppRouter.shared.replaceWithGoal(goal.goalCategoryId) }
        }
    }
}

/// 입력칸 위 작은 회색 라벨 (웹 inline Field: text-ink-3 text-[12px] font-medium)
private struct RecordFormLabel: View {
    let text: String
    var body: some View {
        Text(text).font(.pt(12, .medium)).foregroundStyle(Color.ink3)
    }
}

private extension View {
    /// 웹 inlineInput: h-10 rounded-xl border-line-strong bg-surface px-3 text-[14px]
    func recordInlineInput() -> some View {
        self
            .font(.pt(14))
            .foregroundStyle(Color.ink)
            .tint(Color.brand)
            .padding(.horizontal, 12)
            .frame(height: 40)
            .background(Color.surface, in: RoundedRectangle(cornerRadius: Radius.xl))
            .overlay(RoundedRectangle(cornerRadius: Radius.xl).stroke(Color.borderSubtle, lineWidth: 1))
    }
}

#Preview {
    ScrollView {
        RecordGoalForm(onCancel: {})
            .padding(16)
    }
    .canvasBackground()
}
