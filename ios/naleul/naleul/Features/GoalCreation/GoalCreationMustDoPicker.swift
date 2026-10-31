// Features/GoalCreation/GoalCreationMustDoPicker.swift
//
// "이 목표를 위해 꼭 해야 할 일" 고르기 — 추천 칩을 누르거나 입력창에 적어요.
// 웹 원본: features/goal-creation/ui/MustDoPicker.tsx
//
// 예) 다이어트 → 헬스 · 식단 기록. 고른 일은 계획 AI 가 반드시 루틴으로 넣어요.
//  - 하나만: 입력창에 적기만 하면 돼요 (부모가 GoalCreationMustDo.withDraft 로 함께 보내요)
//  - 여러 개: "+ 추가"(또는 키보드 완료)로 칩을 만들고 다음 걸 적어요

import SwiftUI

enum GoalCreationMustDo {
    static let max = 5
    static let itemMax = 30

    /// 앞뒤 공백 제거 · 연속 공백 하나로 · 30자까지
    static func clean(_ v: String) -> String {
        let collapsed = v.trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: #"\s{2,}"#, with: " ", options: .regularExpression)
        return String(collapsed.prefix(itemMax))
    }

    /// 고른 일 + 입력창에 적어 두고 아직 추가 안 한 일 → 실제로 보낼 목록
    static func withDraft(_ value: [String], _ draft: String) -> [String] {
        let d = clean(draft)
        let all = (!d.isEmpty && !value.contains(d)) ? value + [d] : value
        return Array(all.prefix(max))
    }
}

struct GoalCreationMustDoPicker: View {
    @Binding var value: [String]
    /// 입력창 내용 (부모가 "적기만 한 것"도 보내려고 알고 있어야 해요)
    @Binding var draft: String
    var suggestions: [String] = []
    var disabled = false
    /// 부모가 이 입력칸으로 포커스를 옮길 때 (목표 입력에서 완료 → 여기로)
    var focus: FocusState<Bool>.Binding? = nil

    /// 이벤트의 surface (goal_new: 시작 화면 / goal_review: 정리 확인 시트)
    var surface = "goal_new"

    @FocusState private var innerFocus: Bool

    init(value: Binding<[String]>, draft: Binding<String>, suggestions: [String] = [], disabled: Bool = false,
         focus: FocusState<Bool>.Binding? = nil, surface: String = "goal_new") {
        self.surface = surface
        self._value = value
        self._draft = draft
        self.suggestions = suggestions
        self.disabled = disabled
        self.focus = focus
    }

    private var full: Bool { value.count >= GoalCreationMustDo.max }
    private var custom: [String] { value.filter { !suggestions.contains($0) } }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            if !suggestions.isEmpty || !custom.isEmpty {
                FlowLayout(spacing: 8) {
                    ForEach(suggestions, id: \.self) { s in
                        suggestionChip(s)
                    }
                    ForEach(custom, id: \.self) { s in
                        customChip(s)
                    }
                }
            }
            inputRow
        }
        .disabled(disabled)
        .opacity(disabled ? 0.4 : 1)
    }

    private func suggestionChip(_ s: String) -> some View {
        let on = value.contains(s)
        return Button { toggle(s) } label: {
            HStack(spacing: 4) {
                if on { NSymbol("checkmark", size: 11, weight: .heavy) }
                Text(s)
            }
            .font(.pt(14, .medium))
            .foregroundStyle(on ? Color.textBrand : Color.ink2)
            .padding(.horizontal, 14)
            .frame(height: 36)
            .background(on ? Color.brandSoft : Color.surface, in: Capsule())
            .overlay(Capsule().stroke(on ? Color.brand : Color.borderSubtle, lineWidth: 1))
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .disabled(!on && full)
        .opacity(!on && full ? 0.4 : 1)
        .accessibilityAddTraits(on ? .isSelected : [])
    }

    private func customChip(_ s: String) -> some View {
        HStack(spacing: 4) {
            Text(s)
            Button { toggle(s) } label: {
                NSymbol("xmark", size: 11, weight: .bold)
                    .frame(width: 22, height: 22)
                    .contentShape(Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("\(s) 빼기")
        }
        .font(.pt(14, .medium))
        .foregroundStyle(Color.textBrand)
        .padding(.leading, 14)
        .padding(.trailing, 6)
        .frame(height: 36)
        .background(Color.brandSoft, in: Capsule())
        .overlay(Capsule().stroke(Color.brand, lineWidth: 1))
    }

    private var placeholder: String {
        if full { return "최대 \(GoalCreationMustDo.max)개까지 고를 수 있어요" }
        return value.isEmpty ? "직접 적어 주세요 (예: 주말 등산)" : "하나 더 있다면 적고 + 추가"
    }

    @ViewBuilder
    private var inputField: some View {
        let field = TextField(placeholder, text: Binding(
            get: { draft },
            set: { draft = String($0.prefix(GoalCreationMustDo.itemMax)) }
        ))
        .font(.pt(14))
        .foregroundStyle(Color.ink)
        .submitLabel(.done)
        .onSubmit { addDraft(method: "keyboard") }
        .disabled(full)
        .accessibilityLabel("꼭 하고 싶은 일 직접 적기")
        if let focus {
            field.focused(focus)
        } else {
            field.focused($innerFocus)
        }
    }

    private var inputRow: some View {
        HStack(spacing: 8) {
            inputField
            Button { addDraft(method: "button") } label: {
                HStack(spacing: 4) {
                    NSymbol("plus", size: 12, weight: .semibold)
                    Text("추가")
                }
                .font(.pt(13, .semibold))
                .foregroundStyle(Color.ink2)
            }
            .buttonStyle(.plain)
            .disabled(full || GoalCreationForm.trim(draft).isEmpty)
            .opacity(full || GoalCreationForm.trim(draft).isEmpty ? 0.4 : 1)
        }
        .padding(.horizontal, 12)
        .frame(height: 40)
        .background(Color.surface, in: RoundedRectangle(cornerRadius: Radius.xl))
        .overlay(RoundedRectangle(cornerRadius: Radius.xl).stroke(Color.borderSubtle, lineWidth: 1))
    }

    private func toggle(_ v: String) {
        let removing = value.contains(v)
        if removing { value.removeAll { $0 == v } }
        else if !full { value.append(v) } else { return }
        GCTracker.shared.track(GCEvent.mustDoToggle, [
            "surface": surface, "must_do_action": removing ? "remove" : "add",
            "item_source": suggestions.contains(v) ? "suggestion" : "custom",
            "must_do_count": value.count,
            "session_id": surface == "goal_new" ? nil : GCTracker.shared.sessionId,
        ])
    }

    /// method: button(+ 추가) · keyboard(완료). 빈 글 · 이미 있는 일은 조용히 무시해서 이벤트도 안 보내요
    private func addDraft(method: String) {
        let v = GoalCreationMustDo.clean(draft)
        if !v.isEmpty && !value.contains(v) && !full {
            value.append(v)
            GCTracker.shared.track(GCEvent.mustDoAdd, [
                "surface": surface, "submit_method": method, "must_do_count": value.count,
                "session_id": surface == "goal_new" ? nil : GCTracker.shared.sessionId,
            ])
        }
        draft = ""
    }
}

#Preview {
    struct Demo: View {
        @State private var value = ["헬스", "주말 등산"]
        @State private var draft = ""
        var body: some View {
            VStack(alignment: .leading, spacing: 12) {
                GoalCreationMustDoPicker(value: $value, draft: $draft,
                                         suggestions: ["헬스", "식단 기록", "유산소", "체중 기록", "간헐적 단식"])
                Text("보낼 목록: \(GoalCreationMustDo.withDraft(value, draft).joined(separator: ", "))")
                    .font(.pt(12)).foregroundStyle(Color.ink3)
            }
            .padding()
            .background(Color.surface)
        }
    }
    return Demo()
}
