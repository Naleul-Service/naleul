// Features/Calendar/TimetableRangePreview.swift
//
// 정한 기간(최대 7일)의 TimeTable 격자 미리보기 — Task 추가 결과 화면(StepResult)에서 써요.
// 캘린더와 같은 격자 · 같은 동작이라 블록을 누르면 같은 상세 시트가 뜨고, 길게 눌러 끌어 옮길 수도 있어요.
// 웹 원본: features/brain-dump/ui/StepResult.tsx 의 미리보기 격자 부분
//          (TimeGrid + useTimetableInteractions, maxHeightClass="max-h-[560px]")
//
//   TimetableRangePreview(startDate: "2026-11-18", endDate: "2026-11-20", highlightTaskIds: [12, 13])

import SwiftUI

struct TimetableRangePreview: View {
    let startDate: String
    let endDate: String
    /// "NEW" 로 표시할 Task (방금 만든 것)
    let highlightTaskIds: Set<Int>

    @State private var board: TimetableBoardModel

    init(startDate: String, endDate: String, highlightTaskIds: Set<Int> = []) {
        self.startDate = startDate
        self.endDate = endDate
        self.highlightTaskIds = highlightTaskIds
        _board = State(initialValue: TimetableBoardModel())
    }

    /// 미리보기용 (네트워크 없이 샘플 데이터)
    init(sampleStart: String, sampleEnd: String, highlightTaskIds: Set<Int> = []) {
        self.startDate = sampleStart
        self.endDate = sampleEnd
        self.highlightTaskIds = highlightTaskIds
        _board = State(initialValue: TimetableBoardModel.sample(start: sampleStart, end: sampleEnd))
    }

    /// 보여줄 기간: 최대 7일 (TimeTable API 제한)
    private var end: String {
        let start = startDate
        let last = KST.addDays(start, 6)
        let wanted = endDate < start ? start : endDate
        return wanted > last ? last : wanted
    }

    var body: some View {
        let days = board.days
        VStack(alignment: .leading, spacing: 10) {
            if days.isEmpty && (board.isFetching || !board.loadedOnce) && board.loadError == nil {
                LoadingView(minHeight: 320)
                    .card(radius: Radius.xxl)
            } else if days.isEmpty, let error = board.loadError {
                ErrorView(message: error) { await board.load(withActivities: false) }
                    .card(radius: Radius.xxl)
            } else {
                let hours = TimetableLayout.visibleHours(days)
                TimetableGridView(board: board, days: days, startHour: hours.startHour, endHour: hours.endHour,
                                  highlightIds: highlightTaskIds)
                    .frame(height: 520)
                    .background(Color.surface)
                    .clipShape(RoundedRectangle(cornerRadius: Radius.xxl))
                    .overlay(RoundedRectangle(cornerRadius: Radius.xxl).stroke(Color.line, lineWidth: 1))
            }
            if endDate > end {
                Text("\(KST.formatMonthDay(KST.addDays(end, 1))) 이후에 놓인 Task는 캘린더에서 확인할 수 있어요.")
                    .font(.pt(12))
                    .foregroundStyle(Color.ink3)
            }
            // 끌어서 옮긴 루틴 · 고정 시간의 범위 고르기 / 묶음 안내 (이 화면 안에 붙여서 보여줘요)
            TimetableBottomBars(board: board, showCarry: false, showFill: false)
                .padding(.horizontal, -12)
        }
        .reloadOn([.timetable], id: "\(startDate)_\(end)") {
            board.setRange(startDate, end)
            await board.load(withActivities: false)
        }
        .timetableSheets(board)
        .onDisappear { board.saveAskOnLeave() }
    }
}

#Preview {
    let today = KST.today()
    return ScrollView {
        TimetableRangePreview(sampleStart: today, sampleEnd: KST.addDays(today, 2), highlightTaskIds: [2000, 2001])
            .padding(16)
    }
    .canvasBackground()
}
