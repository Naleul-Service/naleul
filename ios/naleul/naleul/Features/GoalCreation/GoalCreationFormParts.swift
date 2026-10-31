// Features/GoalCreation/GoalCreationFormParts.swift
//
// 목표 만들기 폼에서 같이 쓰는 입력 부품.
// 웹 원본: features/goal-creation/ui/formParts.tsx (Field · inputClass · toNumber)
// 웹 input type="date" / type="time" → iOS DatePicker (한국 시간 기준으로 보여줘요)

import SwiftUI

/// 라벨 + 입력칸 + 아래 도움말 (웹 Field)
struct GoalCreationField<Content: View>: View {
    let label: String
    var hint: String? = nil
    @ViewBuilder var content: () -> Content

    init(_ label: String, hint: String? = nil, @ViewBuilder content: @escaping () -> Content) {
        self.label = label
        self.hint = hint
        self.content = content
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label).font(.pt(13, .semibold)).foregroundStyle(Color.ink2)
            content()
            if let hint {
                Text(hint).font(.pt(12)).foregroundStyle(Color.ink3)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

enum GoalCreationForm {
    /// "78" → 78, "" → nil(빈칸), "abc" → .nan (숫자가 아님) — 웹 toNumber
    static func toNumber(_ v: String) -> Double? {
        let t = v.trimmingCharacters(in: .whitespaces).replacingOccurrences(of: ",", with: ".")
        if t.isEmpty { return nil }
        return Double(t) ?? .nan
    }

    /// 앞뒤 공백 제거
    static func trim(_ v: String) -> String { v.trimmingCharacters(in: .whitespacesAndNewlines) }
}

/// "YYYY-MM-DD" 날짜 고르기. 비어 있으면 "날짜 선택" 버튼 → 누르면 기본 날짜로 채워요
struct GoalCreationDateField: View {
    @Binding var ymd: String
    var min: String? = nil
    var max: String? = nil
    var disabled = false
    /// 비울 수 있는 칸(선택 입력)이면 지우기 버튼을 보여줘요
    var clearable = false

    private var range: ClosedRange<Date> {
        let lo = KST.date(from: min ?? "2000-01-01")
        let hiRaw = KST.date(from: max ?? "2100-12-31")
        return lo...(hiRaw < lo ? lo : hiRaw)
    }

    var body: some View {
        HStack(spacing: 8) {
            if ymd.isEmpty {
                Button {
                    // 오늘(또는 고를 수 있는 첫날)로 채워요
                    var d = KST.today()
                    if let min, d < min { d = min }
                    if let max, d > max { d = max }
                    ymd = d
                } label: {
                    Text("날짜 선택").font(.pt(15)).foregroundStyle(Color.ink4)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .buttonStyle(.plain)
            } else {
                DatePicker("", selection: Binding(
                    get: { KST.date(from: ymd) },
                    set: { ymd = KST.ymd(from: $0) }
                ), in: range, displayedComponents: .date)
                .labelsHidden()
                .environment(\.timeZone, KST.timeZone)
                .environment(\.locale, Locale(identifier: "ko_KR"))
                Spacer(minLength: 0)
                if clearable && !disabled {
                    IconButton(systemName: "xmark.circle.fill", size: 14, color: .ink4, label: "지우기") { ymd = "" }
                }
            }
        }
        .padding(.horizontal, 10)
        .frame(minHeight: 44)
        .background(disabled ? Color.subtle : Color.surface, in: RoundedRectangle(cornerRadius: Radius.xl))
        .overlay(RoundedRectangle(cornerRadius: Radius.xl).stroke(Color.borderSubtle, lineWidth: 1))
        .disabled(disabled)
        .opacity(disabled ? 0.7 : 1)
    }
}

/// "HH:mm" 시각 고르기. 비어 있으면 placeholder 버튼 (선택 입력)
struct GoalCreationTimeField: View {
    @Binding var hm: String
    var placeholder = "시각 선택"
    var defaultValue = "19:00"
    var clearable = true

    var body: some View {
        HStack(spacing: 8) {
            if hm.isEmpty {
                Button { hm = defaultValue } label: {
                    Text(placeholder).font(.pt(15)).foregroundStyle(Color.ink4)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .buttonStyle(.plain)
            } else {
                DatePicker("", selection: Binding(
                    get: { KST.date(fromDateTime: "\(KST.today())T\(hm):00") },
                    set: { hm = KST.hm(KST.dateTime(from: $0)) }
                ), displayedComponents: .hourAndMinute)
                .labelsHidden()
                .environment(\.timeZone, KST.timeZone)
                .environment(\.locale, Locale(identifier: "ko_KR"))
                Spacer(minLength: 0)
                if clearable {
                    IconButton(systemName: "xmark.circle.fill", size: 14, color: .ink4, label: "지우기") { hm = "" }
                }
            }
        }
        .padding(.horizontal, 10)
        .frame(minHeight: 44)
        .background(Color.surface, in: RoundedRectangle(cornerRadius: Radius.xl))
        .overlay(RoundedRectangle(cornerRadius: Radius.xl).stroke(Color.borderSubtle, lineWidth: 1))
    }
}

/// 요일 칩 줄 (월~일)
struct GoalCreationDayChips: View {
    @Binding var days: [GoalCreationWeekday]

    var body: some View {
        HStack(spacing: 6) {
            ForEach(GoalCreationWeekday.allCases, id: \.self) { d in
                let on = days.contains(d)
                Button {
                    if on { days.removeAll { $0 == d } } else { days.append(d) }
                } label: {
                    Text(d.label)
                        .font(.pt(12, .medium))
                        .foregroundStyle(on ? Color.white : Color.ink2)
                        .frame(width: 40, height: 28)
                        .background(on ? Color.brand : Color.surface, in: Capsule())
                        .overlay(Capsule().stroke(on ? Color.brand : Color.borderSubtle, lineWidth: 1))
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(on ? .isSelected : [])
            }
        }
    }
}

#Preview {
    struct Demo: View {
        @State private var date = "2026-11-18"
        @State private var empty = ""
        @State private var time = "19:00"
        @State private var days: [GoalCreationWeekday] = [.MON, .WED]
        @State private var text = ""
        var body: some View {
            VStack(alignment: .leading, spacing: 16) {
                GoalCreationField("이루고 싶은 목표", hint: "100자 이내") {
                    TextField("예) 몸무게 10kg 감량", text: $text).inputStyle()
                }
                GoalCreationField("날짜") { GoalCreationDateField(ymd: $date, min: KST.today()) }
                GoalCreationField("실행일 (선택)") { GoalCreationDateField(ymd: $empty, clearable: true) }
                GoalCreationField("시작 시각 (선택)") { GoalCreationTimeField(hm: $time) }
                GoalCreationDayChips(days: $days)
            }
            .padding()
            .background(Color.surface)
        }
    }
    return Demo()
}
