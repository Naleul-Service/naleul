// Features/TaskAdd/BrainDumpStepResult.swift
//
// 웹 원본: features/brain-dump/ui/StepResult.tsx (+ TaskAddView.tsx 의 PanelResult)
//
// 3단계 · TimeBlock 확인
//  - TaskAddPanelResult : 시트 안에서 저장 직후 보여주는 요약 (웹 panel 변형의 PanelResult 와 같아요)
//  - BrainDumpStepResult: "TimeBlock 확인" 을 누르면 나오는 자세한 결과 (웹 /tasks/new 페이지의 StepResult)
//      · 그 기간 TimeTable 격자 (새 블록은 NEW) — Calendar 작업자의 TimetableRangePreview 를 써요
//      · 블록마다 배치 이유 · 다른 날로 배치된 Task · 시간 미정 Task
//      · 임시 목표 안내 → AI 목표 만들기로 구체화
//
// iOS 에서 바꾼 점
//  - 점선 제안("이대로 진행할까요?")은 캘린더 화면이 맡아요. 시간을 정하지 않은 Task(pending)가 있으면
//    캘린더가 시트를 닫고 캘린더 위에서 점선을 보여 줘요 (CalendarScreen.handleAdded).
//    그래서 여기 자세한 결과는 pending 이 없을 때만 열 수 있어요.

import SwiftUI

// MARK: - 시트 안 요약 (웹 PanelResult)

@MainActor
struct TaskAddPanelResult: View {
    let result: ConfirmResponse
    let onAddMore: () -> Void
    /// 자세한 결과(격자) 화면으로
    let onShowDetail: (() -> Void)?

    private struct Row: Identifiable {
        let id: Int
        let title: String
        let when: String
    }

    private var pending: [BrainDumpTaskRef] { result.pending ?? [] }

    private var rows: [Row] {
        var out: [Row] = []
        for p in result.placements {
            out.append(Row(id: p.taskId, title: p.title, when: "\(KST.formatMonthDay(p.date)) \(KST.hm(p.start))–\(KST.hm(p.end))"))
        }
        for p in result.movedToOtherDays {
            out.append(Row(id: p.taskId, title: p.title,
                           when: "\(KST.formatMonthDay(p.date)) \(KST.hm(p.start))–\(KST.hm(p.end)) (다른 날로)"))
        }
        for u in result.unscheduled {
            out.append(Row(id: u.taskId, title: u.title, when: u.date.map { "\(KST.formatMonthDay($0)) 시간 미정" } ?? "시간 미정"))
        }
        for u in pending {
            out.append(Row(id: u.taskId, title: u.title, when: u.date.map { "\(KST.formatMonthDay($0)) · 점선 확인" } ?? "점선 확인"))
        }
        return out
    }

    var body: some View {
        let list = rows
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 6) {
                Image(systemName: "calendar.badge.checkmark")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Color.success)
                Text("\(list.count)개를 추가했어요")
                    .font(.pt(15, .bold))
                    .foregroundStyle(Color.ink)
            }
            // 웹은 "왼쪽 캘린더" — 아이폰은 캘린더가 시트 뒤에 있어서 "캘린더"로만 적어요
            Text(pending.isEmpty
                 ? "캘린더에 NEW로 표시돼요. 끌어서 바로 옮길 수 있어요."
                 : "시간을 정하지 않은 \(pending.count)개는 캘린더에 점선으로 먼저 보여 드려요. 끌어서 고친 뒤 아래에서 \"이대로 진행\"을 누르면 그 자리에 놓이고, \"아니요\"면 시간 미정으로 남아요.")
                .font(.pt(13))
                .foregroundStyle(Color.ink3)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 4)

            VStack(alignment: .leading, spacing: 6) {
                ForEach(list) { r in
                    HStack(spacing: 8) {
                        NSymbol("checkmark", size: 11, weight: .bold)
                            .foregroundStyle(Color.success)
                        Text(r.title)
                            .font(.pt(13, .medium))
                            .foregroundStyle(Color.ink)
                            .lineLimit(1)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        Text(r.when)
                            .font(.ptMono(13))
                            .foregroundStyle(Color.ink3)
                            .lineLimit(1)
                    }
                }
            }
            .padding(.top, 12)

            HStack(spacing: 8) {
                NButton("더 추가하기", variant: .secondary, size: .sm, icon: "arrow.counterclockwise", action: onAddMore)
                if let onShowDetail {
                    NButton("TimeBlock 확인", variant: .ghost, size: .sm, icon: "calendar", action: onShowDetail)
                }
            }
            .padding(.top, 16)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card(radius: Radius.xxl)
    }
}

// MARK: - 자세한 결과 (웹 StepResult)

@MainActor
struct BrainDumpStepResult: View {
    let result: ConfirmResponse
    let today: String
    let onAddMore: () -> Void
    /// "캘린더에서 보기" · "AI로 구체화하기" 전에 Task 추가 시트를 닫아요
    let closeSheet: () -> Void
    /// 미리보기(#Preview)에서는 격자(서버 조회) 대신 빈 칸을 그려요
    var showsGrid = true

    @State private var replanning = false

    /// 화면 표시 최대 일수 (TimeTable API 제한)
    private let maxDays = 7

    private var pending: [BrainDumpTaskRef] { result.pending ?? [] }

    private var newIds: Set<Int> {
        Set(result.placements.map(\.taskId) + result.movedToOtherDays.map(\.taskId)
            + result.unscheduled.map(\.taskId) + pending.map(\.taskId))
    }

    /// 보여줄 기간: 결과가 있는 첫날부터 마지막 날까지
    private var dates: [String] {
        let placed = (result.placements + result.movedToOtherDays).map(\.date)
        let others = (result.unscheduled + pending).compactMap(\.date)
        let all = (placed + others).sorted()
        return all.isEmpty ? [today] : all
    }
    private var start: String { dates.first ?? today }
    private var lastWanted: String { dates.last ?? today }

    private var placedNow: [Placement] { result.placements.filter { !$0.locked } }
    private var lockedNow: [Placement] { result.placements.filter { $0.locked } }
    /// 점선 확인은 캘린더가 맡으므로, 여기서는 아직 자리가 없는 Task 로 보여 줘요
    private var leftUnscheduled: [BrainDumpTaskRef] { result.unscheduled + pending }

    private var replanDates: [String] {
        var seen = Set<String>()
        return dates.filter { $0 >= today && seen.insert($0).inserted }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                summaryCard
                if showsGrid {
                    TimetableRangePreview(startDate: start, endDate: lastWanted, highlightTaskIds: newIds)
                } else {
                    LoadingView(minHeight: 320).card(radius: Radius.xxl)
                }
                reasonCard
                bottomButtons
            }
            .padding(16)
        }
        .canvasBackground()
        .navigationTitle("TimeBlock 확인")
        .navigationBarTitleDisplayMode(.inline)
    }

    // MARK: 위 요약

    private var summaryCard: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("TimeBlock을 만들었어요")
                .font(.pt(16, .semibold))
                .foregroundStyle(Color.ink)
            Text(summaryText)
                .font(.pt(14))
                .foregroundStyle(Color.ink3)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 4)
            if !replanDates.isEmpty {
                NButton("다시 배치", variant: .ai, size: .sm, icon: "sparkles", loading: replanning) {
                    Task { await replan() }
                }
                .padding(.top, 12)
            }

            // 임시 목표 안내
            ForEach(result.tempGoals, id: \.goalCategoryId) { t in
                VStack(alignment: .leading, spacing: 10) {
                    (Text((t.emoji.map { "\($0) " } ?? "") + t.name).bold()
                     + Text("은(는) 임시 목표로 만들어졌어요. 목표를 구체화해볼까요?"))
                        .font(.pt(14))
                        .foregroundStyle(Color.warningInkStrong)
                        .fixedSize(horizontal: false, vertical: true)
                    NButton("AI로 구체화하기", variant: .ai, size: .sm, icon: "sparkles") {
                        refine(t.goalCategoryId)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.warningSoft, in: RoundedRectangle(cornerRadius: Radius.xxl))
                .padding(.top, 16)
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .aiBorder()   // AI 배치 결과
    }

    private var summaryText: String {
        var s = "\(result.placements.count)개를 배치했어요"
        if !result.movedToOtherDays.isEmpty { s += " · 다른 날로 \(result.movedToOtherDays.count)개" }
        if !leftUnscheduled.isEmpty { s += " · 시간 미정 \(leftUnscheduled.count)개" }
        return s + ". 블록을 끌어서 옮기면 그 시간에 고정돼요."
    }

    // MARK: 배치 이유

    private var reasonCard: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("이렇게 배치했어요")
                .font(.pt(15, .bold))
                .foregroundStyle(Color.ink)
            VStack(alignment: .leading, spacing: 10) {
                ForEach(placedNow, id: \.taskId) { p in
                    BrainDumpReasonRow(title: p.title, when: when(p), reason: p.reason)
                }
                ForEach(lockedNow, id: \.taskId) { p in
                    BrainDumpReasonRow(title: p.title, when: when(p), reason: "직접 정한 시간이에요")
                }
                if result.placements.isEmpty {
                    Text("그날 안에 놓인 Task가 없어요.")
                        .font(.pt(14))
                        .foregroundStyle(Color.ink3)
                }
            }
            .padding(.top, 12)

            if !result.movedToOtherDays.isEmpty {
                HStack(spacing: 6) {
                    NSymbol("calendar.badge.clock", size: 14)
                    Text("다른 날로 배치된 Task")
                }
                .font(.pt(15, .bold))
                .foregroundStyle(Color.ink)
                .padding(.top, 24)
                VStack(alignment: .leading, spacing: 10) {
                    ForEach(result.movedToOtherDays, id: \.taskId) { p in
                        BrainDumpReasonRow(title: p.title, when: when(p), reason: p.reason)
                    }
                }
                .padding(.top, 12)
            }

            if !leftUnscheduled.isEmpty {
                HStack(spacing: 6) {
                    Image(systemName: "calendar.badge.exclamationmark").font(.system(size: 14))
                    Text("시간 미정")
                }
                .font(.pt(15, .bold))
                .foregroundStyle(Color.ink)
                .padding(.top, 24)
                Text("빈 시간이 없어서 날짜만 정했어요. 격자의 \"시간 미정\"에서 끌어다 놓을 수 있어요.")
                    .font(.pt(12))
                    .foregroundStyle(Color.ink3)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, 4)
                VStack(alignment: .leading, spacing: 6) {
                    ForEach(leftUnscheduled, id: \.taskId) { u in
                        (Text(u.title).foregroundColor(Color.ink)
                         + Text(u.date.map { " · \(KST.formatMonthDay($0))" } ?? "").foregroundColor(Color.ink3))
                            .font(.pt(14))
                    }
                }
                .padding(.top, 8)
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card()
    }

    private var bottomButtons: some View {
        HStack(spacing: 8) {
            Spacer(minLength: 0)
            NButton("Task 더 추가하기", variant: .secondary, action: onAddMore)
            NButton("캘린더에서 보기", icon: "arrow.right") {
                let d = start
                TA.plain("task_add_result.calendar_click", ["days_from_today": KST.diffDays(today, d)])
                closeSheet()
                AppRouter.shared.openCalendar(view: .week, date: d)
            }
        }
    }

    // MARK: 동작

    private func when(_ p: Placement) -> String {
        "\(KST.formatMonthDay(p.date)) \(KST.hm(p.start))–\(KST.hm(p.end))"
    }

    /// 웹 useReplan — 결과 날짜(오늘 이후)를 다시 배치
    private func replan() async {
        replanning = true
        defer { replanning = false }
        let ds = replanDates
        TA.track("task_add_result.replan_click", ["day_count": ds.count])
        let r = await Mutation.run(invalidate: [.timetable, .goals], success: { (res: [ReplanResponse]) -> String? in
            let n = res.reduce(0) { $0 + $1.placed.count + $1.movedToOtherDays.count }
            return n > 0 ? "\(n)개 블록을 다시 배치했어요." : "다시 배치할 블록이 없었어요."
        }) {
            try await TimetableAPI.replan(dates: ds)
        }
        TA.track("task_add_result.replan_result", GD.result(r != nil))
    }

    /// 임시 목표 → AI 목표 만들기 (전체 화면). 시트가 떠 있으면 전체 화면이 안 떠서 먼저 닫아요
    private func refine(_ goalId: Int) {
        TA.track("task_add_result.temp_refine_click", ["goal_category_id": goalId])
        closeSheet()
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 450_000_000)
            AppRouter.shared.startGoalCreation(sourceGoalId: goalId)
        }
    }
}

/// 배치 이유 한 줄 (NEW 표시 + 제목 + 시각 + 이유)
private struct BrainDumpReasonRow: View {
    let title: String
    let when: String
    let reason: String?

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Text("NEW")
                .font(.system(size: 9, weight: .bold))
                .foregroundStyle(.white)
                .padding(.horizontal, 4)
                .padding(.vertical, 1)
                .background(Color.actionDanger, in: RoundedRectangle(cornerRadius: 3))
                .padding(.top, 3)
            VStack(alignment: .leading, spacing: 2) {
                (Text(title).font(.pt(14, .semibold)).foregroundColor(Color.ink)
                 + Text("  " + when).font(.ptMono(12)).foregroundColor(Color.ink3))
                if let reason, !reason.isEmpty {
                    Text(reason)
                        .font(.pt(12))
                        .foregroundStyle(Color.ink3)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            Spacer(minLength: 0)
        }
    }
}

#Preview("시트 안 요약") {
    ScrollView {
        TaskAddPanelResult(result: TaskAddSample.result(today: KST.today()), onAddMore: {}, onShowDetail: {})
            .padding(16)
    }
    .canvasBackground()
}

#Preview("자세한 결과") {
    NavigationStack {
        BrainDumpStepResult(result: TaskAddSample.result(today: KST.today()), today: KST.today(),
                            onAddMore: {}, closeSheet: {}, showsGrid: false)
    }
}
