// DesignSystem/Components/Inputs.swift
//
// 입력칸 · 스위치 · 세그먼트 · 필드 라벨 · 체크박스 · 라디오 · 요일 칩 · 시간 칸 (Component_ver.2)
//
//   TextField("목표 이름", text: $name).inputStyle()
//   TextEditor(text: $memo).inputStyle(minHeight: 96)
//   FieldLabel("시작 시간", hint: "10분 단위")
//   NSwitch(isOn: $enabled, label: "알림")
//   SegmentPicker(selection: $view, options: [("day", "일"), ("week", "주"), ("month", "월")])
//   NCheckbox(isOn: $agree, label: "동의해요") · RadioMark(selected: on)
//   DayChip("월", selected: on) { toggle() }
//   TimeField(value: "07:30", placeholder: "시간 선택") { openPicker() }

import SwiftUI

// MARK: - 입력칸 모양 (h44 · radius 10 · bg/surface · 1px border/subtle · px14 · Body/MD · focus = border/focus)

struct InputStyleModifier: ViewModifier {
    var minHeight: CGFloat = 44
    var invalid = false
    @FocusState private var focused: Bool

    func body(content: Content) -> some View {
        let shape = RoundedRectangle(cornerRadius: Radius.input, style: .continuous)
        return content
            .font(.inputText)
            .tracking(-0.28)
            .foregroundStyle(Color.textPrimary)
            .tint(Color.actionPrimary)
            .focused($focused)
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .frame(minHeight: minHeight, alignment: .topLeading)
            .scrollContentBackground(.hidden)
            .background(Color.bgSurface, in: shape)
            .overlay(
                shape.strokeBorder(invalid ? Color.statusErrorFg : (focused ? Color.borderFocus : Color.borderSubtle),
                                   lineWidth: focused || invalid ? 1.5 : 1)
            )
    }
}

extension View {
    func inputStyle(minHeight: CGFloat = 44, invalid: Bool = false) -> some View {
        modifier(InputStyleModifier(minHeight: minHeight, invalid: invalid))
    }
}

/// 입력칸 위 라벨 (+ 오른쪽 보조 글)
struct FieldLabel: View {
    let title: String
    var hint: String? = nil
    var required = false

    init(_ title: String, hint: String? = nil, required: Bool = false) {
        self.title = title
        self.hint = hint
        self.required = required
    }

    var body: some View {
        HStack(spacing: 4) {
            // 시트 · 폼 입력 라벨: Label/MD text/secondary (DESIGN_SPEC 13.2)
            Text(title).font(.labelMD).foregroundStyle(Color.textSecondary)
            if required { Text("*").font(.labelMD).foregroundStyle(Color.statusErrorFg) }
            Spacer(minLength: 0)
            if let hint { Text(hint).font(.captionMD).foregroundStyle(Color.textTertiary) }
        }
    }
}

/// 입력칸 아래 도움말/오류 문구
struct FieldMessage: View {
    let text: String
    var isError = false

    var body: some View {
        Text(text)
            .font(.pt(12, isError ? .medium : .regular))
            .foregroundStyle(isError ? Color.statusErrorFg : Color.textTertiary)
    }
}

// MARK: - 시간 칸 / 선택 칸 (TimeField · GoalSelect)

/// 누르면 피커를 여는 입력칸 모양 버튼. h44 · radius 10 · 오른쪽 16pt 아이콘
struct TimeField: View {
    var value: String?
    var placeholder = "선택"
    var icon = "clock"
    var focused = false
    let action: () -> Void

    init(value: String?, placeholder: String = "선택", icon: String = "clock", focused: Bool = false,
         action: @escaping () -> Void) {
        self.value = value
        self.placeholder = placeholder
        self.icon = icon
        self.focused = focused
        self.action = action
    }

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: Radius.input, style: .continuous)
        Button(action: action) {
            HStack(spacing: 8) {
                Text(value ?? placeholder)
                    .font(value == nil ? Font.inputPlaceholder : Font.inputText)
                    .tracking(-0.28)
                    .foregroundStyle(value == nil ? Color.textPlaceholder : Color.textPrimary)
                    .lineLimit(1)
                Spacer(minLength: 0)
                NSymbol(icon, size: 14).foregroundStyle(Color.textTertiary)
            }
            .padding(.horizontal, 12)
            .frame(height: 44)
            .background(Color.bgSurface, in: shape)
            .overlay(shape.strokeBorder(focused ? Color.borderFocus : Color.borderSubtle, lineWidth: focused ? 1.5 : 1))
            .contentShape(shape)
        }
        .buttonStyle(.plain)
    }
}

// MARK: - 스위치 (44×26 · on action/primary · off border/subtle · 손잡이 20 bg/surface)

struct NToggleStyle: ToggleStyle {
    func makeBody(configuration: Configuration) -> some View {
        HStack(spacing: 8) {
            configuration.label
            ZStack(alignment: configuration.isOn ? .trailing : .leading) {
                Capsule().fill(configuration.isOn ? Color.actionPrimary : Color.borderSubtle)
                Circle()
                    .fill(Color.bgSurface)
                    .frame(width: 20, height: 20)
                    .shadow(color: Color.black.opacity(0.12), radius: 1.5, x: 0, y: 1)
                    .padding(3)
            }
            .frame(width: 44, height: 26)
            .contentShape(Capsule())
            .onTapGesture {
                withAnimation(.easeOut(duration: 0.15)) { configuration.isOn.toggle() }
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityAddTraits(.isButton)
        .accessibilityValue(configuration.isOn ? "켬" : "끔")
        .accessibilityAction { configuration.isOn.toggle() }
    }
}

extension ToggleStyle where Self == NToggleStyle {
    static var naleul: NToggleStyle { NToggleStyle() }
}

struct NSwitch: View {
    @Binding var isOn: Bool
    var label: String = ""
    var disabled = false

    var body: some View {
        Toggle(label, isOn: $isOn)
            .labelsHidden()
            .toggleStyle(NToggleStyle())
            .disabled(disabled)
            .opacity(disabled ? 0.5 : 1)
            .accessibilityLabel(label)
    }
}

// MARK: - 체크박스 · 라디오

/// 20×20 · radius 6 · off 1.5px border/control · on action/primary 채우기 + 흰 ✓
struct CheckboxMark: View {
    var checked: Bool
    var disabled = false

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: Radius.checkbox, style: .continuous)
        ZStack {
            if checked {
                shape.fill(disabled ? Color.disabledBg : Color.actionPrimary)
                NSymbol("checkmark", size: 11, weight: .bold)
                    .foregroundStyle(disabled ? Color.disabledFg : Color.actionPrimaryFg)
            } else {
                shape.strokeBorder(disabled ? Color.borderSubtle : Color.borderControl, lineWidth: 1.5)
            }
        }
        .frame(width: 20, height: 20)
    }
}

struct NCheckbox: View {
    @Binding var isOn: Bool
    var label: String? = nil
    var disabled = false

    var body: some View {
        Button {
            isOn.toggle()
        } label: {
            HStack(spacing: 8) {
                CheckboxMark(checked: isOn, disabled: disabled)
                if let label {
                    Text(label).font(.bodyMD).foregroundStyle(disabled ? Color.disabledFg : Color.textPrimary)
                }
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(disabled)
        .accessibilityAddTraits(isOn ? .isSelected : [])
    }
}

/// 20×20 · off 1.5px border/control · on 2px primary 링 + 8pt primary 점
struct RadioMark: View {
    var selected: Bool

    var body: some View {
        ZStack {
            if selected {
                Circle().strokeBorder(Color.actionPrimary, lineWidth: 2)
                Circle().fill(Color.actionPrimary).frame(width: 8, height: 8)
            } else {
                Circle().strokeBorder(Color.borderControl, lineWidth: 1.5)
            }
        }
        .frame(width: 20, height: 20)
    }
}

// MARK: - 요일 칩 (44×36 · radius full · on primary + 흰 글자 · off bg/canvas + text/secondary)

struct DayChip: View {
    let title: String
    var selected = false
    var muted = false
    let action: () -> Void

    init(_ title: String, selected: Bool = false, muted: Bool = false, action: @escaping () -> Void) {
        self.title = title
        self.selected = selected
        self.muted = muted
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.bodyMDMedium)
                .tracking(-0.28)
                .foregroundStyle(selected ? Color.actionPrimaryFg : (muted ? Color.textTertiary : Color.textSecondary))
                .frame(width: 44, height: 36)
                .background(selected ? Color.actionPrimary : Color.bgSunken, in: Capsule())
                .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

// MARK: - 세그먼트 (SegmentedTabs / ViewSwitch / DayTabs)
// 바깥 bg/subtle · radius 12 · padding 3 · 간격 2 / 선택 항목 bg/surface · radius 10 · 그림자 0 1 3 8%

struct SegmentPicker<Value: Hashable>: View {
    @Binding var selection: Value
    let options: [(value: Value, label: String)]
    var icons: [Value: String] = [:]
    var fullWidth = false

    var body: some View {
        HStack(spacing: 2) {
            ForEach(options.indices, id: \.self) { i in
                let opt = options[i]
                let on = opt.value == selection
                Button {
                    withAnimation(.easeOut(duration: 0.15)) { selection = opt.value }
                } label: {
                    HStack(spacing: 6) {
                        if let icon = icons[opt.value] {
                            NSymbol(icon, size: 12, weight: .medium)
                        }
                        Text(opt.label)
                    }
                    .font(.labelMD)
                    .tracking(-0.26)
                    .foregroundStyle(on ? Color.textPrimary : Color.textSecondary)
                    .padding(.horizontal, 12)
                    .frame(height: 30)
                    .frame(maxWidth: fullWidth ? .infinity : nil)
                    .background {
                        if on {
                            RoundedRectangle(cornerRadius: Radius.segmentItem, style: .continuous)
                                .fill(Color.bgSurface)
                                .segShadow()
                        }
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(on ? .isSelected : [])
            }
        }
        .padding(3)
        .background(Color.bgSubtle, in: RoundedRectangle(cornerRadius: Radius.segment, style: .continuous))
    }
}

/// Figma 이름 (SegmentedTabs) 으로도 쓸 수 있어요
typealias SegmentedTabs<Value: Hashable> = SegmentPicker<Value>

// MARK: - 이모지 한 글자 입력칸

struct EmojiField: View {
    @Binding var emoji: String
    var placeholder = "🙂"

    var body: some View {
        TextField(placeholder, text: Binding(
            get: { emoji },
            set: { emoji = String($0.suffix(1)) }   // 마지막 한 글자(이모지)만
        ))
        .font(.system(size: 22))
        .multilineTextAlignment(.center)
        .frame(width: 52, height: 52)
        .background(Color.bgSubtle, in: RoundedRectangle(cornerRadius: Radius.btn, style: .continuous))
    }
}

#Preview {
    struct Demo: View {
        @State private var text = ""
        @State private var memo = ""
        @State private var on = true
        @State private var check = true
        @State private var view = "week"
        @State private var emoji = "🏃"
        var body: some View {
            VStack(alignment: .leading, spacing: 12) {
                FieldLabel("목표 이름", hint: "20자 이내", required: true)
                TextField("예: 3개월 동안 5kg 감량", text: $text).inputStyle()
                FieldMessage(text: "이름을 입력해 주세요.", isError: true)
                TextEditor(text: $memo).inputStyle(minHeight: 96)
                TimeField(value: nil, placeholder: "시작 시간") {}
                HStack { Text("알림").font(.bodyLG); Spacer(); NSwitch(isOn: $on, label: "알림") }
                HStack { NCheckbox(isOn: $check, label: "매일 반복"); RadioMark(selected: true); RadioMark(selected: false) }
                HStack { DayChip("월", selected: true) {}; DayChip("화") {} }
                SegmentPicker(selection: $view, options: [("day", "일"), ("week", "주"), ("month", "월")])
                EmojiField(emoji: $emoji)
            }
            .padding()
            .background(Color.canvas)
        }
    }
    return Demo()
}
