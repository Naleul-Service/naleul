// Features/Calendar/TimetableAddMenu.swift
//
// TimeTable 빈 칸을 눌렀을 때 뜨는 + 메뉴 (DESIGN_SPEC 11.4) — 캘린더 일간 · 주간과 홈 오늘 일정이 같이 써요 (2026-10).
// 웹 원본: features/timetable/ui/useSlotAddMenu.tsx
//  - AI로 자동 Task 추가 · 계획 할 일 추가 → onTaskAdd (그 날짜 · 시각을 미리 채워 Task 추가 시트)
//  - 실제 한 일 추가 → 지난 시간에만 (그 시각부터 1시간 기록)
// 분석 이벤트는 화면마다 이름이 달라서(calendar.* / home.calendar_*) 부르는 쪽 클로저에서 보내요.

import SwiftUI

extension View {
    /// board.emptySlot 이 생기면 선택지를 띄워요 (board.emptyTapOpensMenu 가 true 인 화면)
    func timetableEmptySlotMenu(board: TimetableBoardModel,
                                onTaskAdd: @escaping (TaskAddMode, TimetableEmptySlot) -> Void,
                                onActivity: @escaping (TimetableEmptySlot) -> Void) -> some View {
        confirmationDialog("추가", isPresented: Binding(get: { board.emptySlot != nil },
                                                      set: { if !$0 { board.emptySlot = nil } }),
                           titleVisibility: .hidden, presenting: board.emptySlot) { slot in
            Button("AI로 자동 Task 추가") { onTaskAdd(.ai, slot) }
            Button("계획 할 일 추가") { onTaskAdd(.manual, slot) }
            // 실제 한 일은 지난 시간에만
            if slot.past {
                Button("실제 한 일 추가") {
                    onActivity(slot)
                    board.openActivity(TimetableActivityDraft(activity: nil, date: slot.date, start: slot.minutes,
                                                              end: slot.minutes + 60, replaced: nil))
                }
            }
            Button("취소", role: .cancel) {}
        }
    }
}

/// 머리글 "추가" 메뉴 — 캘린더 떠 있는 + 버튼과 같은 3가지 (홈 오늘 일정 카드에서 써요)
struct TimetableAddMenuItems: View {
    let onTaskAdd: (TaskAddMode) -> Void
    let onActivity: () -> Void

    var body: some View {
        Button { onTaskAdd(.ai) } label: { Label("AI로 자동 Task 추가", systemImage: "sparkles") }
        Button { onTaskAdd(.manual) } label: { Label("계획 할 일 추가", systemImage: "square.and.pencil") }
        Button(action: onActivity) { Label("실제 한 일 추가", systemImage: "checkmark.circle") }
    }
}
