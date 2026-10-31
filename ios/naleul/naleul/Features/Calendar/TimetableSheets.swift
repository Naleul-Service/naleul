// Features/Calendar/TimetableSheets.swift
//
// 블록을 누르면 뜨는 시트를 한 곳에서 띄워요 (웹 useTimetableInteractions 의 overlays 중 Popover · Modal 부분).
// 웹 Popover(블록 옆에 뜨는 작은 창) → iOS 는 아래에서 올라오는 시트.
// 같이 쓰는 입력 부품(시각 · 날짜 고르기, 삭제 확인)도 여기 있어요.
//
//   .timetableSheets(board)   // board.sheet 가 바뀌면 그에 맞는 시트를 띄워요

import SwiftUI

// MARK: - 시트 띄우기

private struct TimetableSheetsModifier: ViewModifier {
    @Bindable var board: TimetableBoardModel

    func body(content: Content) -> some View {
        // ⚠️ board.sheet 를 body 에서 "직접" 읽어야 해요.
        // 예전엔 Binding(get: { b.sheet }) 처럼 클로저 안에서만 읽어서, @Observable 이
        // "이 화면은 sheet 를 쓴다"는 걸 몰랐어요 → 블록을 눌러 sheet 가 바뀌어도 이 modifier 가
        // 다시 그려지지 않아 시트가 안 떴어요 (선택 테두리만 바뀌고 모달이 안 뜨던 원인).
        let current = board.sheet
        let b = board
        content
            .sheet(item: $board.sheet, onDismiss: { b.sheetDismissed() }) { sheet in
                TimetableSheetContent(board: b, sheet: sheet)
            }
            .onChange(of: current?.id) { _, _ in }
    }
}

extension View {
    /// TimeTable 블록 상세 · 고정 시간 · 실제로 한 일 · 시간 입력 시트
    func timetableSheets(_ board: TimetableBoardModel) -> some View {
        modifier(TimetableSheetsModifier(board: board))
    }
}

private struct TimetableSheetContent: View {
    let board: TimetableBoardModel
    let sheet: TimetableSheet

    var body: some View {
        switch sheet {
        case .task(let t):
            // 데이터가 새로 오면 시트 안의 Task 도 최신 값으로
            TimetableTaskDetailSheet(board: board, task: board.liveTask(t))
        case .fixed(let b):
            TimetableFixedDetailSheet(board: board, block: b)
        case .activity(let d):
            TimetableActivityFormSheet(board: board, draft: d)
        case .timeEdit(let e):
            if case .actual(let t) = e.target {
                // 실제로 한 시간 — 나눠서 한 경우 여러 구간
                TimetableActualTimeSheet(board: board, task: board.liveTask(t), initial: e.initial)
            } else {
                TimetableTimeEditSheet(board: board, editing: e)
            }
        }
    }
}

// MARK: - 시각 고르기 (10분 단위 — 웹 input type="time" step=600)

struct TimetableClockPicker: View {
    /// 그날 0시 기준 분 (0~1439)
    @Binding var minutes: Int
    var label: String = ""

    var body: some View {
        let total = ((minutes % 1440) + 1440) % 1440
        let h = total / 60
        let m = total % 60
        HStack(spacing: 0) {
            Picker("시", selection: Binding(get: { h }, set: { minutes = $0 * 60 + m })) {
                ForEach(0..<24, id: \.self) { v in
                    Text(TimetableClockPicker.pad(v)).tag(v)
                }
            }
            Text(":").font(.pt(15, .semibold)).foregroundStyle(Color.ink3)
            Picker("분", selection: Binding(get: { m }, set: { minutes = h * 60 + $0 })) {
                ForEach(TimetableClockPicker.minuteOptions(m), id: \.self) { v in
                    Text(TimetableClockPicker.pad(v)).tag(v)
                }
            }
        }
        .pickerStyle(.menu)
        .tint(Color.ink)
        .frame(maxWidth: .infinity)
        .frame(height: 44)
        .background(Color.surface, in: RoundedRectangle(cornerRadius: Radius.xl))
        .overlay(RoundedRectangle(cornerRadius: Radius.xl).stroke(Color.borderSubtle, lineWidth: 1))
        .accessibilityElement(children: .contain)
        .accessibilityLabel(label)
    }

    /// 10분 단위 + 지금 값(10분 단위가 아니면 그것도)
    static func minuteOptions(_ current: Int) -> [Int] {
        var list = Array(stride(from: 0, to: 60, by: 10))
        if current % 10 != 0 {
            list.append(current)
            list.sort()
        }
        return list
    }

    static func pad(_ n: Int) -> String { n < 10 ? "0\(n)" : "\(n)" }
}

// MARK: - 날짜 고르기 ("YYYY-MM-DD" 문자열 ↔ DatePicker)

struct TimetableDateField: View {
    @Binding var ymd: String
    /// 이 날짜까지만 고를 수 있어요 (실제로 한 일: 오늘까지)
    var maxDate: String? = nil

    var body: some View {
        let binding = Binding<Date>(get: { KST.date(from: ymd) }, set: { ymd = KST.ymd(from: $0) })
        Group {
            if let maxDate {
                DatePicker("날짜", selection: binding, in: ...KST.date(from: maxDate), displayedComponents: .date)
            } else {
                DatePicker("날짜", selection: binding, displayedComponents: .date)
            }
        }
        .labelsHidden()
        .datePickerStyle(.compact)
        // 기기 시간대가 한국이 아니어도 같은 날짜로 보이게
        .environment(\.timeZone, KST.timeZone)
        .tint(Color.brand)
    }
}

// MARK: - 그 자리에서 "삭제할까요?" (웹 goal/edit/inline InlineConfirm)

struct TimetableInlineConfirm: View {
    let message: String
    var detail: String? = nil
    var confirmLabel = "삭제"
    let loading: Bool
    let onConfirm: () -> Void
    let onCancel: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            VStack(alignment: .leading, spacing: 2) {
                Text(message).font(.pt(14, .semibold)).foregroundStyle(Color.ink)
                if let detail {
                    Text(detail).font(.pt(12)).foregroundStyle(Color.ink3)
                }
            }
            HStack(spacing: 8) {
                Spacer(minLength: 0)
                NButton("취소", variant: .ghost, size: .sm, action: onCancel).disabled(loading)
                NButton(confirmLabel, variant: .danger, size: .sm, loading: loading, action: onConfirm)
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.dangerSoft.opacity(0.6), in: RoundedRectangle(cornerRadius: Radius.xxl))
        .overlay(RoundedRectangle(cornerRadius: Radius.xxl).stroke(Color.borderSubtle, lineWidth: 1))
        .accessibilityLabel("삭제 확인")
    }
}

#Preview {
    struct Demo: View {
        @State private var start = 9 * 60
        @State private var date = KST.today()
        var body: some View {
            VStack(alignment: .leading, spacing: 16) {
                TimetableClockPicker(minutes: $start, label: "시작")
                TimetableDateField(ymd: $date, maxDate: KST.today())
                TimetableInlineConfirm(message: "'영어 단어 외우기'을 삭제할까요?", detail: "되돌릴 수 없어요.",
                                       loading: false, onConfirm: {}, onCancel: {})
            }
            .padding(16)
            .canvasBackground()
        }
    }
    return Demo()
}
