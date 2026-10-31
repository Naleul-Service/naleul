// Features/Calendar/TimetableActualTimeSheet.swift
//
// "실제로 한 시간" — 한 번에 했으면 한 줄, 중간에 쉬었다 이어서 했으면 "나눠서 했어요"로 여러 줄 (웹 ActualTimeModal).
// 예) 계획 13:00~18:00 → 실제 13:00~15:00, 16:00~18:00
// 자정을 넘겨도 돼요: 끝이 시작보다 이르면 끝은 다음 날 (23:00~00:00),
// 앞 구간보다 이른 시각에 시작하는 구간은 통째로 다음 날로 봐요 (21:30~22:00, 23:00~00:00, 00:30~01:00).

import SwiftUI

struct TimetableActualTimeSheet: View {
    let board: TimetableBoardModel
    let task: TimeBlockTask
    /// 기준 날짜 (실제로 한 날 — 없으면 계획한 날)
    let date: String

    @State private var rows: [Row]
    /// "saved" · "cancel_button" — 없으면 X 또는 아래로 쓸어 닫기
    @State private var outcome: String?

    struct Row: Identifiable, Hashable {
        let id = UUID()
        var start: Int
        var end: Int
    }

    static let maxSegments = 10

    init(board: TimetableBoardModel, task: TimeBlockTask, initial: TimetableTimeValue) {
        self.board = board
        self.task = task
        self.date = initial.date
        let saved = (task.actualSegments ?? []).map {
            Row(start: Self.wrap(KST.minutesFrom(initial.date, $0.start)), end: Self.wrap(KST.minutesFrom(initial.date, $0.end)))
        }
        _rows = State(initialValue: saved.count > 1
                      ? saved
                      : [Row(start: Self.wrap(initial.start), end: Self.wrap(initial.end))])
    }

    /// 화면의 시계는 0~1439분만 보여줘요 — 다음 날 시각(1440 이상)도 시계 값으로
    static func wrap(_ m: Int) -> Int { ((m % 1440) + 1440) % 1440 }

    /// 화면 값 → 실제 구간.
    /// 끝 ≤ 시작이면 끝은 다음 날, 앞 구간보다 이르게 시작하면 그 구간부터 다음 날로 (자정을 지나 이어서 한 경우)
    static func parse(_ rows: [Row], date: String) -> [TimetableTimeValue] {
        var offset = 0
        var prevStart = Int.min
        return rows.map { r in
            if prevStart != Int.min && r.start + offset < prevStart { offset += 1440 }
            let s = r.start + offset
            var e = r.end + offset
            if e <= s { e += 1440 }
            prevStart = s
            return TimetableTimeValue(date: date, start: s, end: e)
        }
    }

    private var parsed: [TimetableTimeValue] { Self.parse(rows, date: date) }

    private var error: String? {
        let p = parsed
        if p.contains(where: { $0.end <= $0.start }) { return "종료 시각이 시작보다 뒤여야 해요." }
        if p.contains(where: { $0.end - $0.start > 1440 }) { return "한 구간은 하루를 넘을 수 없어요." }
        let sorted = p.sorted { $0.start < $1.start }
        for i in sorted.indices.dropFirst() where sorted[i].start < sorted[i - 1].end {
            return "구간끼리 겹치지 않게 입력해 주세요."
        }
        return nil
    }

    private var total: Int { parsed.reduce(0) { $0 + max($1.end - $1.start, 0) } }

    var body: some View {
        let saving = board.editSaving
        SheetScaffold(title: "실제로 한 시간",
                      description: "실제로 시작하고 끝낸 시각을 남기면 완료로 표시되고, 나의 패턴이 더 정확해져요. 중간에 쉬었다가 이어서 했으면 나눠서 적어 주세요.",
                      dismissible: !saving) {
            VStack(alignment: .leading, spacing: 10) {
                ForEach($rows) { $row in
                    HStack(alignment: .bottom, spacing: 8) {
                        VStack(alignment: .leading, spacing: 4) {
                            if row.id == rows.first?.id {
                                Text("시작").font(.pt(12, .medium)).foregroundStyle(Color.ink3)
                            }
                            TimetableClockPicker(minutes: $row.start, label: "시작 시각")
                        }
                        VStack(alignment: .leading, spacing: 4) {
                            if row.id == rows.first?.id {
                                Text("종료").font(.pt(12, .medium)).foregroundStyle(Color.ink3)
                            }
                            TimetableClockPicker(minutes: $row.end, label: "종료 시각")
                        }
                        if rows.count > 1 {
                            IconButton(systemName: "xmark", size: 15, label: "구간 지우기") {
                                board.sheetTrack("actual_time.segment_remove_click", ["segment_count": rows.count - 1])
                                rows.removeAll { $0.id == row.id }
                            }
                            .frame(height: 44)
                        }
                    }
                }
                if rows.count < Self.maxSegments {
                    Button(action: addRow) {
                        HStack(spacing: 4) {
                            NSymbol("plus", size: 13, weight: .bold)
                            Text(rows.count == 1 ? "나눠서 했어요 (구간 추가)" : "구간 추가")
                        }
                        .font(.pt(13, .semibold))
                        .foregroundStyle(Color.textBrand)
                    }
                    .buttonStyle(.plain)
                    .padding(.top, 2)
                }
                Text(error ?? "\(rows.count > 1 ? "\(rows.count)번에 나눠서 · " : "")총 \(KST.formatDuration(total))")
                    .font(.pt(12))
                    .foregroundStyle(error == nil ? Color.ink3 : Color.danger)
            }
        } footer: {
            NButton("취소", variant: .secondary) {
                outcome = "cancel_button"
                board.close()
            }
                .disabled(saving)
            NButton("저장", loading: saving) {
                let segs = parsed.sorted { $0.start < $1.start }
                board.sheetTrack("actual_time.save_click", [
                    "segment_count": segs.count, "duration_minutes": total,
                    "crosses_midnight": segs.contains { $0.end > 1440 },
                    "is_edit": task.taskStatus == .COMPLETED && task.actualStartAt != nil && task.actualEndAt != nil,
                ])
                Task {
                    if await board.submitActual(task, date: date, segments: segs) { outcome = "saved" }
                }
            }
            .disabled(error != nil)
        }
        .onChange(of: error) { _, e in
            if let e { board.sheetTrack("actual_time.error_view", ["validation_error": e]) }
        }
        .onDisappear {
            guard let m = GD.closeMethod(outcome) else { return }
            board.sheetTrack("actual_time.close", ["close_method": m])
        }
    }

    private func addRow() {
        board.sheetTrack("actual_time.segment_add_click", ["segment_count": rows.count + 1])
        // 직전 구간이 끝나고 30분 뒤부터 1시간으로 제안
        let last = rows.last
        let s = Self.wrap((last?.end ?? 12 * 60) + 30)
        rows.append(Row(start: s, end: Self.wrap(s + 60)))
    }
}

#Preview("자정을 넘겨 나눠서 한 시간") {
    let d = KST.today()
    let board = TimetableBoardModel.sample(start: d, end: d)
    var t = TimetableSampleData.day(d, index: 1).tasks[0]
    t.taskStatus = .COMPLETED
    t.actualSegments = [ActualSegment(start: "\(d)T21:30:00", end: "\(d)T22:00:00"),
                        ActualSegment(start: "\(d)T23:00:00", end: "\(KST.addDays(d, 1))T00:00:00")]
    return Color.canvas
        .sheet(isPresented: .constant(true)) {
            TimetableActualTimeSheet(board: board, task: t, initial: TimetableTimeValue(date: d, start: 21 * 60 + 30, end: 24 * 60))
        }
}

#Preview("나눠서 한 시간") {
    let d = KST.today()
    let board = TimetableBoardModel.sample(start: d, end: d)
    var t = TimetableSampleData.day(d, index: 1).tasks[0]
    t.taskStatus = .COMPLETED
    t.actualSegments = [ActualSegment(start: "\(d)T13:00:00", end: "\(d)T15:00:00"),
                        ActualSegment(start: "\(d)T16:00:00", end: "\(d)T18:00:00")]
    return Color.canvas
        .sheet(isPresented: .constant(true)) {
            TimetableActualTimeSheet(board: board, task: t, initial: TimetableTimeValue(date: d, start: 13 * 60, end: 18 * 60))
        }
}
