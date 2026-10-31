// Features/GoalCreation/GoalKindPicker.swift
//
// 목표 카테고리 2단계 선택 (명세 6.A.1.2).
// 웹 원본: features/goal/ui/GoalKindPicker.tsx
//
// 1단계 카드(건강 / 학습·자기개발 / 생활습관 / 기타) → 2단계 칩 → "기타"면 이름 직접 입력(10자).
// 1단계 "기타"는 2단계 없이 바로 이름을 적어요.
// value 는 2단계까지 고른 뒤에만 채워지고, 고르는 중에는 nil 이에요 (GoalKind.isComplete 로 "기타 이름"까지 확인).

import SwiftUI

struct GoalKindPicker: View {
    @Binding var value: GoalKindValue?
    var disabled = false
    /// 목표 헤더 수정처럼 좁은 곳 — 1단계를 칩으로
    var compact = false
    /// 이벤트 기록용 (목표 만들기 시작 화면만 넘겨요)
    var onTypePick: ((GoalType) -> Void)? = nil
    var onSubPick: ((GoalType, GoalSubType) -> Void)? = nil
    /// AI로 만드는 목표형 (2026-10): 생활 패턴 항목을 빼고 1단계 없이 칩 한 줄 (GoalKind.aiFlatOptions)
    var flat = false

    /// 1단계만 고르고 2단계는 아직인 상태
    @State private var pendingType: GoalType?
    @FocusState private var labelFocused: Bool

    init(value: Binding<GoalKindValue?>, disabled: Bool = false, compact: Bool = false, flat: Bool = false,
         onTypePick: ((GoalType) -> Void)? = nil, onSubPick: ((GoalType, GoalSubType) -> Void)? = nil) {
        self._value = value
        self.disabled = disabled
        self.compact = compact
        self.flat = flat
        self.onTypePick = onTypePick
        self.onSubPick = onSubPick
        self._pendingType = State(initialValue: value.wrappedValue?.goalType)
    }

    private var activeType: GoalType? { value?.goalType ?? pendingType }
    private var typeOption: GoalTypeOption? { GoalKind.types.first { $0.value == activeType } }
    private var isCustom: Bool {
        guard let v = value else { return false }
        return GoalKind.subOption(v.goalSubType).custom
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            if flat {
                flatChips
            } else if compact { compactTypes } else { typeCards }

            if !flat, let type = typeOption, type.subs.count > 1 {
                FlowLayout(spacing: compact ? 6 : 8) {
                    ForEach(type.subs) { s in
                        Chip("\(s.emoji) \(s.label)", selected: value?.goalSubType == s.value,
                             size: compact ? .sm : .md) { pickSub(s.value) }
                    }
                }
                .accessibilityLabel("\(type.label) 세부 카테고리")
            }

            if isCustom { customLabelField }
        }
        .disabled(disabled)
        .opacity(disabled ? 0.4 : 1)
    }

    // MARK: 칩 한 줄 (AI 목표형)

    private var flatChips: some View {
        FlowLayout(spacing: 8) {
            ForEach(GoalKind.aiFlatOptions, id: \.sub.value) { o in
                Chip("\(o.sub.emoji) \(o.sub.label)", selected: value?.goalSubType == o.sub.value, size: .md) {
                    onTypePick?(o.type)
                    pendingType = o.type
                    onSubPick?(o.type, o.sub.value)
                    value = GoalKindValue(goalType: o.type, goalSubType: o.sub.value,
                                          goalKindLabel: o.sub.custom ? (value?.goalSubType == o.sub.value ? value?.goalKindLabel ?? "" : "") : nil)
                }
            }
        }
        .accessibilityLabel("목표 카테고리")
    }

    // MARK: 1단계

    private var compactTypes: some View {
        FlowLayout(spacing: 6) {
            ForEach(GoalKind.types) { t in
                Chip("\(t.emoji) \(t.label)", selected: activeType == t.value, size: .sm) { pickType(t.value) }
            }
        }
    }

    private var typeCards: some View {
        LazyVGrid(columns: [GridItem(.flexible(), spacing: 8), GridItem(.flexible(), spacing: 8)], spacing: 8) {
            ForEach(GoalKind.types) { t in
                GoalKindTypeCard(option: t, selected: activeType == t.value) { pickType(t.value) }
            }
        }
        .accessibilityLabel("목표 카테고리")
    }

    // MARK: 기타 이름

    private var customLabelField: some View {
        let label = value?.goalKindLabel ?? ""
        let placeholder: String = {
            switch typeOption?.value {
            case .ETC: return "예) 악기 연습"
            case .HEALTH: return "예) 필라테스"
            default: return "예) 주식 공부"
            }
        }()
        return HStack(spacing: 8) {
            TextField(placeholder, text: Binding(
                get: { value?.goalKindLabel ?? "" },
                set: { new in
                    guard var v = value else { return }
                    // 웹 maxLength — 10자까지만
                    v.goalKindLabel = String(new.prefix(GoalKind.labelMax))
                    value = v
                }
            ))
            .font(.pt(14))
            .foregroundStyle(Color.ink)
            .focused($labelFocused)
            .accessibilityLabel("카테고리 이름 직접 입력")
            Text("\(label.count)/\(GoalKind.labelMax)")
                .font(.ptMono(12))
                .foregroundStyle(Color.ink4)
        }
        .padding(.horizontal, 12)
        .frame(height: 40)
        .frame(maxWidth: 320)
        .background(Color.surface, in: RoundedRectangle(cornerRadius: Radius.xl))
        .overlay(RoundedRectangle(cornerRadius: Radius.xl).stroke(labelFocused ? Color.borderFocus : Color.borderSubtle, lineWidth: 1))
        // 웹 autoFocus — 이름 칸이 나타나면 바로 입력
        .onAppear { labelFocused = true }
    }

    // MARK: 동작

    private func pickType(_ t: GoalType) {
        onTypePick?(t)
        pendingType = t
        guard let opt = GoalKind.types.first(where: { $0.value == t }) else { return }
        // 2단계가 "기타" 하나뿐(1단계 기타)이면 바로 그걸로
        if opt.subs.count == 1, let only = opt.subs.first {
            value = GoalKindValue(goalType: t, goalSubType: only.value, goalKindLabel: "")
        } else {
            value = nil
        }
    }

    private func pickSub(_ s: GoalSubType) {
        guard let t = activeType else { return }
        onSubPick?(t, s)
        value = GoalKindValue(goalType: t, goalSubType: s,
                              goalKindLabel: GoalKind.subOption(s).custom ? (value?.goalKindLabel ?? "") : nil)
    }
}

/// 1단계 카드 (이모지 · 이름 · 힌트)
private struct GoalKindTypeCard: View {
    let option: GoalTypeOption
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 0) {
                Text(option.emoji).font(.system(size: 22))
                Text(option.label)
                    .font(.pt(15, .bold))
                    .foregroundStyle(selected ? Color.textBrand : Color.ink)
                    .padding(.top, 8)
                Text(option.hint)
                    .font(.pt(12))
                    .foregroundStyle(Color.ink3)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
                    .padding(.top, 2)
            }
            .frame(maxWidth: .infinity, minHeight: 92, alignment: .topLeading)
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(selected ? Color.brandSoft : Color.surface, in: RoundedRectangle(cornerRadius: Radius.xxl))
            .overlay(RoundedRectangle(cornerRadius: Radius.xxl).stroke(selected ? Color.brand : Color.borderSubtle, lineWidth: 1))
            .contentShape(RoundedRectangle(cornerRadius: Radius.xxl))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

#Preview {
    struct Demo: View {
        @State private var kind: GoalKindValue? = GoalKindValue(goalType: .HEALTH, goalSubType: .DIET, goalKindLabel: nil)
        @State private var custom: GoalKindValue? = GoalKindValue(goalType: .ETC, goalSubType: .OTHER, goalKindLabel: "악기 연습")
        @State private var empty: GoalKindValue? = nil
        var body: some View {
            ScrollView {
                VStack(alignment: .leading, spacing: 28) {
                    GoalKindPicker(value: $kind)
                    Text(GoalKind.name(kind) ?? "-").font(.pt(13)).foregroundStyle(Color.ink3)
                    GoalKindPicker(value: $custom, compact: true)
                    GoalKindPicker(value: $empty, disabled: true)
                }
                .padding(16)
            }
            .background(Color.surface)
        }
    }
    return Demo()
}
