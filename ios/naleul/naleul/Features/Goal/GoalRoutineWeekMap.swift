// Features/Goal/GoalRoutineWeekMap.swift
//
// 루틴 폼 안의 "내 루틴 한눈에 보기" — 웹 features/goal/ui/RoutineWeekMap.tsx.
// 루틴을 만들 때 캘린더에 다녀오지 않아도 다른 목표의 루틴이 언제 있는지 보여요.
//  - 위: 같은 요일 · 시간에 겹치는 루틴이 있으면 바로 경고 (+ 캘린더에서 AI로 한 번에 정리)
//  - 펼치면: 월~일 주간표에 모든 목표의 루틴(목표 색)과 지금 만드는 루틴(점선)을 같이 그려요.
//    빈 곳을 누르면 그 요일 · 시각으로 정해져요.

import SwiftUI
import UIKit

/// GET /routines/all — 내 모든 목표의 루틴 (목표 색 포함)
struct AllRoutine: Codable, Hashable, Identifiable {
    var id: Int { routineId }
    var routineId: Int
    var routineName: String
    var goalCategoryId: Int
    var goalCategoryName: String
    var goalCategoryColorCode: String?
    var repeatStartDate: String?
    var repeatEndDate: String?
    var repeatStartTime: String?
    var repeatEndTime: String?
    var routineStatus: String
    var repeatDays: [RepeatDay]

    struct RepeatDay: Codable, Hashable {
        var dayOfWeek: JavaDayOfWeek
        var startTime: String?
        var endTime: String?
    }

    /// 지금 반복 중인(또는 곧 시작할) 루틴만
    func isLive(today: String) -> Bool {
        (routineStatus == "NOT_STARTED" || routineStatus == "IN_PROGRESS") && (repeatEndDate.map { $0 >= today } ?? true)
    }
}

enum RoutineAPI {
    static func all() async throws -> [AllRoutine] {
        let list: [AllRoutine]? = try await API.get("/v1/routines/all")
        return list ?? []
    }
}

/// 만들고 있는 루틴 (폼 값)
struct GoalRoutineDraft {
    var routineId: Int?
    var name: String
    var days: [JavaDayOfWeek]
    /// 요일별 실제 시간 ("HH:mm", 없으면 빈 문자열)
    var timeOf: (JavaDayOfWeek) -> (start: String, end: String)
}

enum GoalRoutineMap {
    struct Slot: Identifiable {
        var id: String
        var routineId: Int?
        var name: String
        var goalName: String
        var color: String?
        var day: JavaDayOfWeek
        /// 그날 0시 기준 분. 자정을 넘기면 end > 1440
        var start: Int
        var end: Int
    }

    struct Conflict: Identifiable {
        var id: Int
        var name: String
        var goalName: String
        var days: [JavaDayOfWeek]
        var time: String
    }

    static func range(_ s: String, _ e: String) -> (Int, Int) {
        let a = KST.timeToMinutes(s)
        var b = KST.timeToMinutes(e)
        if b <= a { b += 1440 }
        return (a, b)
    }

    static func slots(_ r: AllRoutine) -> [Slot] {
        r.repeatDays.compactMap { d in
            guard let st = d.startTime ?? r.repeatStartTime, let et = d.endTime ?? r.repeatEndTime else { return nil }
            let (a, b) = range(st, et)
            return Slot(id: "\(r.routineId)-\(d.dayOfWeek.rawValue)", routineId: r.routineId, name: r.routineName,
                        goalName: r.goalCategoryName, color: r.goalCategoryColorCode, day: d.dayOfWeek, start: a, end: b)
        }
    }

    static func existing(_ all: [AllRoutine], draft: GoalRoutineDraft, today: String = KST.today()) -> [Slot] {
        all.filter { $0.routineId != draft.routineId && $0.isLive(today: today) }.flatMap(slots)
    }

    static func mine(_ draft: GoalRoutineDraft) -> [Slot] {
        draft.days.compactMap { d in
            let t = draft.timeOf(d)
            guard !t.start.isEmpty, !t.end.isEmpty else { return nil }
            let (a, b) = range(t.start, t.end)
            return Slot(id: "new-\(d.rawValue)", routineId: nil, name: draft.name.isEmpty ? "새 루틴" : draft.name,
                        goalName: "", color: nil, day: d, start: a, end: b)
        }
    }

    /// 만들고 있는 루틴과 같은 요일 · 시간에 겹치는 기존 루틴
    static func conflicts(_ all: [AllRoutine], draft: GoalRoutineDraft) -> [Conflict] {
        let ex = existing(all, draft: draft)
        var hits: [Int: Conflict] = [:]
        var order: [Int] = []
        for m in mine(draft) {
            for s in ex where s.day == m.day && s.start < m.end && m.start < s.end {
                guard let rid = s.routineId else { continue }
                if hits[rid] == nil {
                    hits[rid] = Conflict(id: rid, name: s.name, goalName: s.goalName, days: [],
                                         time: "\(KST.minutesToTime(s.start))–\(KST.minutesToTime(s.end))")
                    order.append(rid)
                }
                if !(hits[rid]!.days.contains(m.day)) { hits[rid]!.days.append(m.day) }
            }
        }
        return order.compactMap { hits[$0] }
    }
}

struct GoalRoutineWeekMap: View {
    let draft: GoalRoutineDraft
    /// 빈 곳을 눌렀을 때 — 그 요일을 넣고 시작 시각을 그 시각(30분 단위)으로
    var onPick: (JavaDayOfWeek, Int) -> Void
    /// 새 루틴을 만드는 중인지 (이벤트 is_new)
    var isNew = false
    /// 겹치는 루틴 수가 바뀔 때 (저장 이벤트의 has_conflict)
    var onConflictCount: ((Int) -> Void)? = nil

    @State private var all: [AllRoutine] = []
    @State private var loaded = false
    @State private var open = false
    /// 겹침 안내를 이 시트에서 이미 보냈는지
    @State private var conflictSeen = false
    @Environment(\.dismiss) private var dismiss
    @Environment(AppRouter.self) private var router
    @Environment(GoalDetailViewModel.self) private var vm

    private let hourHeight: CGFloat = 16
    private let labelWidth: CGFloat = 24

    var body: some View {
        let conflicts = GoalRoutineMap.conflicts(all, draft: draft)
        let ex = GoalRoutineMap.existing(all, draft: draft)
        let liveCount = Set(ex.compactMap(\.routineId)).count
        VStack(alignment: .leading, spacing: 8) {
            if !conflicts.isEmpty { warning(conflicts) }
            Button {
                UISelectionFeedbackGenerator().selectionChanged()
                vm.track("goal_detail.routine_weekmap_toggle", ["expanded": !open, "routine_count": liveCount])
                withAnimation(.easeOut(duration: 0.18)) { open.toggle() }
            } label: {
                HStack(spacing: 6) {
                    NSymbol("calendar", size: 13, weight: .semibold)
                    Text("내 루틴 한눈에 보기").font(.pt(13, .semibold))
                    if liveCount > 0 { Text("(\(liveCount)개)").font(.pt(13)).foregroundStyle(Color.ink3) }
                    NSymbol("chevron.down", size: 11, weight: .semibold)
                        .rotationEffect(.degrees(open ? 180 : 0))
                }
                .foregroundStyle(Color.ink2)
                .frame(minHeight: 32)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            if open {
                if loaded { map(ex) } else { RoundedRectangle(cornerRadius: 12).fill(Color.subtle).frame(height: 160) }
            }
        }
        .task {
            all = (try? await RoutineAPI.all()) ?? []
            loaded = true
        }
        .onChange(of: conflicts.count, initial: true) { _, n in
            onConflictCount?(n)
            if n > 0 && !conflictSeen {
                conflictSeen = true
                vm.track("goal_detail.routine_conflict_view", ["conflict_count": n, "is_new": isNew])
            }
        }
    }

    private func warning(_ conflicts: [GoalRoutineMap.Conflict]) -> some View {
        let command = conflicts.map { "'\($0.name)'" }.joined(separator: ", ")
            + " 루틴과 '\(draft.name.isEmpty ? "새 루틴" : draft.name)' 시간이 겹쳐. 겹치지 않게 정리해줘"
        return VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 6) {
                Image(systemName: "exclamationmark.triangle.fill").font(.system(size: 13)).foregroundStyle(Color.warning)
                Text("같은 시간에 이미 루틴이 있어요").font(.pt(13, .semibold)).foregroundStyle(Color.ink)
            }
            ForEach(conflicts) { c in
                (Text(c.name).fontWeight(.bold).foregroundColor(Color.ink)
                 + Text(" (\(c.goalName)) · ").foregroundColor(Color.ink3)
                 + Text("\(c.days.map(\.label).joined(separator: "·")) \(c.time)").foregroundColor(Color.ink2))
                    .font(.pt(13))
            }
            Text("그대로 둬도 괜찮아요 (같은 시간에 둘 다 보여요). 많이 겹치면 캘린더에서 AI로 한 번에 정리할 수 있어요.")
                .font(.pt(12))
                .foregroundStyle(Color.ink3)
            Button {
                vm.track("goal_detail.routine_conflict_ai_click", ["conflict_count": conflicts.count, "is_new": isNew])
                // 다른 화면으로 넘어가는 거라 루틴 시트 취소 이벤트는 보내지 않아요
                Analytics.sheetCloseHint = "next"
                dismiss()
                router.openCalendarAi(command)
            } label: {
                Label("캘린더에서 AI로 정리하기", systemImage: "sparkles")
                    .font(.pt(12, .semibold))
                    .foregroundStyle(Color.textBrand)
                    .frame(minHeight: 28)
            }
            .buttonStyle(.plain)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.warningSoft, in: RoundedRectangle(cornerRadius: 12))
    }

    private func map(_ ex: [GoalRoutineMap.Slot]) -> some View {
        let mine = GoalRoutineMap.mine(draft)
        let everything = ex + mine
        let lo = min(6 * 60, everything.map(\.start).min() ?? 6 * 60)
        let hi = max(24 * 60, everything.map { min($0.end, 1440 + 6 * 60) }.max() ?? 24 * 60)
        let from = lo / 60 * 60
        let to = Int((Double(hi) / 60).rounded(.up)) * 60
        let height = CGFloat(to - from) / 60 * hourHeight
        func y(_ m: Int) -> CGFloat { CGFloat(m - from) / 60 * hourHeight }
        func overlaps(_ a: GoalRoutineMap.Slot, _ b: GoalRoutineMap.Slot) -> Bool {
            a.day == b.day && a.start < b.end && b.start < a.end
        }

        return VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 2) {
                Color.clear.frame(width: labelWidth, height: 1)
                ForEach(JavaDayOfWeek.allCases) { d in
                    Text(d.label)
                        .font(.pt(11, .bold))
                        .foregroundStyle(draft.days.contains(d) ? Color.textBrand : Color.ink3)
                        .frame(maxWidth: .infinity)
                }
            }
            HStack(alignment: .top, spacing: 2) {
                ZStack(alignment: .topTrailing) {
                    ForEach(Array(stride(from: from, through: to, by: 180)), id: \.self) { m in
                        Text(String(format: "%02d", (m / 60) % 24))
                            .font(.ptMono(9))
                            .foregroundStyle(Color.ink4)
                            .offset(y: y(m) - 6)
                    }
                }
                .frame(width: labelWidth, height: height, alignment: .topTrailing)
                ForEach(JavaDayOfWeek.allCases) { d in
                    GeometryReader { g in
                        let w = g.size.width
                        ZStack(alignment: .topLeading) {
                            RoundedRectangle(cornerRadius: 5).fill(Color.subtle.opacity(0.6))
                            ForEach(ex.filter { $0.day == d }) { s in
                                let half = mine.contains { overlaps($0, s) }
                                RoundedRectangle(cornerRadius: 3)
                                    .fill(Color.goal(s.color).opacity(0.85))
                                    .frame(width: half ? w / 2 - 2 : w - 2,
                                           height: max(y(min(s.end, to)) - y(s.start), 5))
                                    .offset(x: 1, y: y(s.start))
                            }
                            ForEach(mine.filter { $0.day == d }) { s in
                                let half = ex.contains { overlaps($0, s) }
                                RoundedRectangle(cornerRadius: 3)
                                    .fill(Color.brandSoft.opacity(0.8))
                                    .overlay(RoundedRectangle(cornerRadius: 3)
                                        .strokeBorder(Color.brand, style: StrokeStyle(lineWidth: 1.5, dash: [3, 2])))
                                    .frame(width: half ? w / 2 - 1 : w - 2,
                                           height: max(y(min(s.end, to)) - y(s.start), 5))
                                    .offset(x: half ? w / 2 : 1, y: y(s.start))
                            }
                        }
                        .contentShape(Rectangle())
                        .onTapGesture { loc in
                            let m = Double(from) + Double(loc.y / hourHeight) * 60
                            let snapped = max(0, min(1410, Int(m / 30) * 30))
                            UISelectionFeedbackGenerator().selectionChanged()
                            onPick(d, snapped)
                        }
                    }
                    .frame(height: height)
                    .accessibilityLabel("\(d.label)요일 시간 고르기")
                }
            }
            Text("색 칸 = 다른 루틴 · 점선 = 지금 만드는 루틴 · 빈 곳을 누르면 그 요일 · 시각으로 정해져요")
                .font(.pt(11))
                .foregroundStyle(Color.ink3)
        }
        .padding(8)
        .background(Color.surface, in: RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.line, lineWidth: 1))
    }
}

#Preview("내 루틴 한눈에 보기 — 겹침") {
    let draft = GoalRoutineDraft(routineId: nil, name: "SQL 문제 풀기", days: [.TUESDAY, .THURSDAY],
                                 timeOf: { _ in ("16:30", "17:30") })
    return ScrollView {
        GoalRoutineWeekMap(draft: draft) { _, _ in }
            .padding(16)
    }
    .canvasBackground()
    .environment(GoalDetailViewModel.preview())
    .environment(AppRouter.shared)
}
