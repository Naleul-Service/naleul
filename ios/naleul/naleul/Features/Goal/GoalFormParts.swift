// Features/Goal/GoalFormParts.swift
//
// 웹 features/goal/edit/inline.tsx 의 입력 부품들 (InlineForm · InlineConfirm · Field · RowActions · AddButton · DaysPicker).
//
// 웹은 "그 자리에서 바로 고치기"(항목 자리가 입력창으로 바뀜, 한 번에 하나만 열림)였어요.
// 아이폰은 화면이 좁아서 입력창은 시트(SheetScaffold)로 띄워요. 시트는 한 번에 하나만 뜨니까
// "한 번에 하나만 열기" 규칙이 저절로 지켜져요. 삭제 확인은 iOS 알림창(.confirmDialog)으로 바꿨어요.
// 웹 Enter 저장 / Esc 취소 → 키보드의 완료(return) 키로 저장, 취소 버튼 · 아래로 끌어 닫기로 취소.

import SwiftUI

// MARK: - 시트 안에서는 테두리 없는 모양으로

private struct GoalFormPlainKey: EnvironmentKey {
    static let defaultValue = false
}

extension EnvironmentValues {
    /// true 면 GoalInlineForm 이 테두리·배경 없이 그려져요 (시트 안에 넣을 때)
    var goalFormPlain: Bool {
        get { self[GoalFormPlainKey.self] }
        set { self[GoalFormPlainKey.self] = newValue }
    }
}

// MARK: - 입력 폼 틀 (웹 InlineForm)

/// 입력칸들 + 오류 문구 + [취소] [저장] 버튼.
/// valid 가 false 면 저장 버튼이 꺼지고, saving 중엔 스피너가 돌아요.
@MainActor
struct GoalInlineForm<Content: View, Extra: View>: View {
    var submitLabel: String
    var saving: Bool
    var valid: Bool
    var error: String?
    let onCancel: () -> Void
    let onSubmit: () -> Void
    @ViewBuilder var content: () -> Content
    @ViewBuilder var extra: () -> Extra

    @Environment(\.goalFormPlain) private var plain

    init(submitLabel: String = "저장", saving: Bool = false, valid: Bool = true, error: String? = nil,
         onCancel: @escaping () -> Void, onSubmit: @escaping () -> Void,
         @ViewBuilder content: @escaping () -> Content,
         @ViewBuilder extra: @escaping () -> Extra) {
        self.submitLabel = submitLabel
        self.saving = saving
        self.valid = valid
        self.error = error
        self.onCancel = onCancel
        self.onSubmit = onSubmit
        self.content = content
        self.extra = extra
    }

    var body: some View {
        // 시트 안: 입력칸 16 간격 · 버튼은 시트 버튼 줄 모양 (Secondary + Primary LG · 8 간격 · DESIGN_SPEC 13.2)
        VStack(alignment: .leading, spacing: plain ? 16 : 12) {
            content()
            if let error {
                Text(error).font(.pt(12)).foregroundStyle(Color.danger)
            }
            if plain {
                // 시트 안: 바닥에 붙은 버튼 줄로 올려요 (SheetActions · 검사 · 저장 중 상태는 그대로)
                SheetActions(key: "\(submitLabel)|\(saving)|\(valid)") {
                    extra()
                    NButton("취소", variant: .secondary, size: .lg, fullWidth: true) { onCancel() }
                        .disabled(saving)
                    NButton(submitLabel, size: .lg, loading: saving, fullWidth: true) {
                        if valid && !saving { onSubmit() }
                    }
                    .disabled(!valid)
                }
            } else {
                HStack(spacing: 8) {
                    extra()
                    Spacer(minLength: 0)
                    NButton("취소", variant: .ghost, size: .sm) { onCancel() }
                        .disabled(saving)
                    NButton(submitLabel, size: .sm, loading: saving) {
                        if valid && !saving { onSubmit() }
                    }
                    .disabled(!valid)
                }
                .padding(.top, 4)
            }
        }
        .padding(plain ? 0 : 14)
        .background {
            if !plain {
                RoundedRectangle(cornerRadius: Radius.xxl).fill(Color.brandSoft.opacity(0.4))
            }
        }
        .overlay {
            if !plain {
                RoundedRectangle(cornerRadius: Radius.xxl).stroke(Color.borderSubtle, lineWidth: 1)
            }
        }
    }
}

extension GoalInlineForm where Extra == EmptyView {
    init(submitLabel: String = "저장", saving: Bool = false, valid: Bool = true, error: String? = nil,
         onCancel: @escaping () -> Void, onSubmit: @escaping () -> Void,
         @ViewBuilder content: @escaping () -> Content) {
        self.init(submitLabel: submitLabel, saving: saving, valid: valid, error: error,
                  onCancel: onCancel, onSubmit: onSubmit, content: content, extra: { EmptyView() })
    }
}

// MARK: - 라벨 + 입력칸 (웹 Field)

@MainActor
struct GoalFormField<Content: View>: View {
    let label: String
    @ViewBuilder var content: () -> Content

    init(_ label: String, @ViewBuilder content: @escaping () -> Content) {
        self.label = label
        self.content = content
    }

    var body: some View {
        // 라벨 Label/MD text/secondary · 8 아래 입력칸 (DESIGN_SPEC 13.2)
        VStack(alignment: .leading, spacing: 8) {
            Text(label).font(.labelMD).foregroundStyle(Color.textSecondary)
            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

// MARK: - 날짜 · 시간 입력 (웹 <input type="date|time">)

/// "YYYY-MM-DD" 문자열과 주고받는 날짜 칸. optional 이면 비울 수 있어요 ("" = 없음)
@MainActor
struct GoalDateInput: View {
    @Binding var value: String
    var optional = false
    var disabled = false
    /// 이 날짜까지만 고를 수 있어요 (예: 지난 기록 고치기는 오늘까지)
    var maxDate: String? = nil
    var placeholder = "날짜 선택"

    private var dateBinding: Binding<Date> {
        Binding(
            get: { KST.date(from: value.isEmpty ? KST.today() : value) },
            set: { value = KST.ymd(from: $0) }
        )
    }

    var body: some View {
        HStack(spacing: 6) {
            if value.isEmpty {
                Button { value = KST.today() } label: {
                    Text(placeholder)
                        .font(.pt(14, .medium))
                        .foregroundStyle(Color.ink3)
                        .padding(.horizontal, 12)
                        .frame(height: 36)
                        .background(Color.subtle, in: RoundedRectangle(cornerRadius: Radius.lg))
                }
                .buttonStyle(.plain)
                .disabled(disabled)
            } else {
                picker
                    .labelsHidden()
                    .disabled(disabled)
                if optional && !disabled {
                    IconButton(systemName: "xmark.circle.fill", size: 14, color: .ink4, label: "날짜 지우기") { value = "" }
                }
            }
            Spacer(minLength: 0)
        }
        .environment(\.timeZone, KST.timeZone)
        .environment(\.locale, Locale(identifier: "ko_KR"))
    }

    @ViewBuilder private var picker: some View {
        if let maxDate {
            DatePicker("", selection: dateBinding, in: ...KST.date(from: maxDate), displayedComponents: .date)
        } else {
            DatePicker("", selection: dateBinding, displayedComponents: .date)
        }
    }
}

/// "HH:mm" 문자열과 주고받는 시간 칸 ("" = 비움 → 자동 배치)
@MainActor
struct GoalTimeInput: View {
    @Binding var value: String
    var placeholder = "시간 선택"
    /// 비어 있을 때 누르면 넣을 시간
    var suggested = "09:00"

    private var dateBinding: Binding<Date> {
        Binding(
            get: { KST.date(fromDateTime: "\(KST.today())T\(value.isEmpty ? suggested : value):00") },
            set: { value = KST.hm(KST.dateTime(from: $0)) }
        )
    }

    var body: some View {
        HStack(spacing: 6) {
            if value.isEmpty {
                Button { value = suggested } label: {
                    Text(placeholder)
                        .font(.pt(14, .medium))
                        .foregroundStyle(Color.ink3)
                        .padding(.horizontal, 12)
                        .frame(height: 36)
                        .background(Color.subtle, in: RoundedRectangle(cornerRadius: Radius.lg))
                }
                .buttonStyle(.plain)
            } else {
                DatePicker("", selection: dateBinding, displayedComponents: .hourAndMinute)
                    .labelsHidden()
                IconButton(systemName: "xmark.circle.fill", size: 14, color: .ink4, label: "시간 지우기") { value = "" }
            }
            Spacer(minLength: 0)
        }
        .environment(\.timeZone, KST.timeZone)
        .environment(\.locale, Locale(identifier: "ko_KR"))
    }
}

// MARK: - 고르기 (웹 <select>)

@MainActor
struct GoalSelectMenu<Value: Hashable>: View {
    @Binding var selection: Value
    let options: [(value: Value, label: String)]

    var body: some View {
        Picker("", selection: $selection) {
            ForEach(options.indices, id: \.self) { i in
                Text(options[i].label).tag(options[i].value)
            }
        }
        .pickerStyle(.menu)
        .labelsHidden()
        .tint(Color.ink)
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 4)
        .frame(minHeight: 40)
        .background(Color.surface, in: RoundedRectangle(cornerRadius: Radius.xl))
        .overlay(RoundedRectangle(cornerRadius: Radius.xl).stroke(Color.borderSubtle, lineWidth: 1))
    }
}

// MARK: - 글자 수 제한 (웹 maxLength)

private struct GoalMaxLength: ViewModifier {
    @Binding var text: String
    let max: Int

    func body(content: Content) -> some View {
        content.onChange(of: text) {
            if text.count > max { text = String(text.prefix(max)) }
        }
    }
}

extension View {
    func goalMaxLength(_ text: Binding<String>, _ max: Int) -> some View {
        modifier(GoalMaxLength(text: text, max: max))
    }
}

// MARK: - 줄 끝 아이콘 버튼 (웹 RowActions — 아이폰은 마우스가 없어서 항상 보여요)

@MainActor
struct GoalRowActions: View {
    let label: String
    var onEdit: (() -> Void)? = nil
    var onDelete: (() -> Void)? = nil

    var body: some View {
        HStack(spacing: 0) {
            if let onEdit {
                IconButton(systemName: "pencil", size: 13, color: .ink3, label: "\(label) 수정", action: onEdit)
            }
            if let onDelete {
                IconButton(systemName: "trash", size: 13, color: .ink3, label: "\(label) 삭제", action: onDelete)
            }
        }
    }
}

// MARK: - "+ 추가" 점선 버튼 (웹 AddButton)

@MainActor
struct GoalAddButton: View {
    let title: String
    let action: () -> Void

    init(_ title: String, action: @escaping () -> Void) {
        self.title = title
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                NSymbol("plus", size: 14, weight: .semibold)
                Text(title)
            }
            .font(.pt(14, .semibold))
            .foregroundStyle(Color.ink3)
            .frame(maxWidth: .infinity)
            .frame(height: 42)
            .overlay(
                RoundedRectangle(cornerRadius: Radius.xxl)
                    .stroke(Color.borderSubtle, style: StrokeStyle(lineWidth: 1, dash: [4, 3]))
            )
            .contentShape(RoundedRectangle(cornerRadius: Radius.xxl))
        }
        .buttonStyle(.plain)
    }
}

// MARK: - 요일 고르기 (웹 DaysPicker)

@MainActor
struct GoalDaysPicker: View {
    @Binding var value: [JavaDayOfWeek]

    private static let all = JavaDayOfWeek.allCases
    private static let weekdays = Array(JavaDayOfWeek.allCases.prefix(5))
    private static let weekend = Array(JavaDayOfWeek.allCases.suffix(2))

    private func isSet(_ t: [JavaDayOfWeek]) -> Bool {
        value.count == t.count && t.allSatisfy { value.contains($0) }
    }

    private func toggle(_ d: JavaDayOfWeek) {
        if value.contains(d) {
            value = value.filter { $0 != d }
        } else {
            // 요일 순서를 지켜요
            value = Self.all.filter { $0 == d || value.contains($0) }
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 4) {
                ForEach(Self.all) { d in
                    Chip(d.label, selected: value.contains(d), size: .sm) { toggle(d) }
                }
            }
            HStack(spacing: 4) {
                Chip("매일", selected: isSet(Self.all), size: .sm) { value = Self.all }
                Chip("평일", selected: isSet(Self.weekdays), size: .sm) { value = Self.weekdays }
                Chip("주말", selected: isSet(Self.weekend), size: .sm) { value = Self.weekend }
            }
        }
    }
}

// MARK: - 시트 안 내용 묶음

extension View {
    /// 시트 안에 GoalInlineForm 을 넣을 때 (테두리 없이)
    func goalSheetForm() -> some View { environment(\.goalFormPlain, true) }
}

#Preview {
    struct Demo: View {
        @State private var date = "2026-10-09"
        @State private var due = ""
        @State private var time = ""
        @State private var days: [JavaDayOfWeek] = [.MONDAY, .WEDNESDAY]
        @State private var pick: Int? = nil
        var body: some View {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    GoalInlineForm(submitLabel: "추가", error: "날짜를 입력해 주세요.", onCancel: {}, onSubmit: {}) {
                        GoalFormField("날짜") { GoalDateInput(value: $date) }
                        GoalFormField("마감일") { GoalDateInput(value: $due, optional: true) }
                        GoalFormField("시작 (선택)") { GoalTimeInput(value: $time) }
                        GoalFormField("반복 요일") { GoalDaysPicker(value: $days) }
                        GoalFormField("세부 목표 (선택)") {
                            GoalSelectMenu(selection: $pick, options: [(nil, "세부 목표 없음"), (1, "식단 관리")])
                        }
                    } extra: {
                        Text("알림 켜짐").font(.pt(13, .semibold)).foregroundStyle(Color.textBrand)
                    }
                    GoalAddButton("루틴 추가") {}
                    HStack { Text("아침 스트레칭"); Spacer(); GoalRowActions(label: "루틴", onEdit: {}, onDelete: {}) }
                }
                .padding(16)
            }
            .canvasBackground()
        }
    }
    return Demo()
}
