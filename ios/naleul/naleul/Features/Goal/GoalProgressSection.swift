// Features/Goal/GoalProgressSection.swift
//
// 웹 features/goal/ui/progress/ProgressSection.tsx — 목표 상세 "지금 어디쯤?"
//
// - 수치 목표(체중 72 → 69kg 등): 시작 → 목표 막대 위 현재 위치 + 계획선 대비 기록 꺾은선 + 지난 기록 목록
// - 수치가 없는 목표: 오늘까지 해야 했던 Task 대비 완료한 Task 누적 그래프
// 오늘 기록은 위 "오늘 기록" 카드에서 해요. 여기서는 지난 기록을 고칠 때만 시트를 열어요.

import SwiftUI

@MainActor
struct GoalProgressSection: View {
    let goal: GoalCategory
    @Environment(GoalDetailViewModel.self) private var vm

    var body: some View {
        if !vm.progressLoaded {
            SectionCard("지금 어디쯤?") {
                ProgressView().tint(Color.ink3).frame(maxWidth: .infinity, minHeight: 140)
            }
        } else if let p = vm.progress {
            if let m = p.metric {
                SectionCard("지금 어디쯤?", aside: { GoalProgressStatusChip(status: m.status) }) {
                    GoalMetricProgressView(p: p, m: m)
                }
            } else {
                SectionCard("지금 어디쯤?") {
                    GoalTaskProgressView(p: p)
                }
            }
        }
    }
}

// MARK: - 상태 칩

@MainActor
struct GoalProgressStatusChip: View {
    let status: ProgressStatus

    private var info: (label: String, fg: Color, bg: Color) {
        switch status {
        case .ACHIEVED: return ("목표 달성 🎉", .success, .successSoft)
        case .AHEAD: return ("계획보다 앞서요", .success, .successSoft)
        case .ON_TRACK: return ("계획대로 가는 중", .brand, .brandSoft)
        case .BEHIND: return ("계획보다 조금 늦어요", .warning, .warningSoft)
        case .NOT_STARTED: return ("시작 전", .ink3, .subtle)
        case .NO_PLAN: return ("종료일 없음", .ink3, .subtle)
        }
    }

    var body: some View {
        let i = info
        Text(i.label)
            .font(.pt(12, .semibold))
            .foregroundStyle(i.fg)
            .padding(.horizontal, 10)
            .padding(.vertical, 4)
            .background(i.bg, in: Capsule())
    }
}

// MARK: - 수치 목표

@MainActor
private struct GoalMetricProgressView: View {
    let p: GoalProgress
    let m: MetricProgress
    @Environment(GoalDetailViewModel.self) private var vm
    @State private var showAll = false

    private typealias L = GoalDetailLogic

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            metricHead
            trackBar
            insight
            chart.padding(.top, 20)
            GoalChartLegend(items: [("계획", .plan), ("내 기록", .actual)]).padding(.top, 8)
            recentLogs
            GoalTaskProgressLine(t: p.tasks)
        }
    }

    // ── 수치 마감일 · 수치만 고치기 (2026-10) ──

    private var metricHead: some View {
        HStack(spacing: 8) {
            let goalEnd = vm.goal?.goalCategoryEndDate
            if let d = m.targetDate ?? goalEnd {
                HStack(spacing: 4) {
                    NSymbol("flag", size: 11, weight: .semibold)
                    Text("\(m.name ?? "수치") 마감 \(L.md(d))")
                    if let g = goalEnd, g != d {
                        Text("· 목표 종료 \(L.md(g))").foregroundStyle(Color.ink4)
                    }
                }
                .font(.pt(12, .medium))
                .foregroundStyle(Color.ink3)
                .lineLimit(1)
            }
            Spacer(minLength: 0)
            Button {
                vm.sheet = .editMetric(entryPoint: "progress_section")
            } label: {
                HStack(spacing: 3) {
                    NSymbol("pencil", size: 11, weight: .semibold)
                    Text("수치 수정")
                }
                .font(.pt(12, .semibold))
                .foregroundStyle(Color.textBrand)
                .padding(.horizontal, 10)
                .frame(height: 28)
                .background(Color.brandSoft, in: Capsule())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("수치 목표 수정")
        }
        .padding(.bottom, 12)
    }

    // ── 시작 → 목표 막대 위의 현재 위치 ──

    private func pos(_ v: Double) -> Double {
        let span = m.targetValue - m.startValue
        guard span != 0 else { return 1 }
        return min(max((v - m.startValue) / span, 0), 1)
    }

    private var trackBar: some View {
        let now = pos(m.currentValue)
        let expected = m.expectedValue.map { pos($0) }
        let percent = m.progressPercent ?? 0
        return VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .bottom) {
                (Text("시작 ").foregroundColor(Color.ink3) + Text(L.withUnit(m.startValue, m.unit)).bold())
                    .font(.pt(13))
                Spacer(minLength: 0)
                HStack(alignment: .firstTextBaseline, spacing: 1) {
                    Text("\(Int(min(max(percent, 0), 999).rounded()))").font(.ptMono(28, .bold))
                    Text("%").font(.pt(15, .semibold)).foregroundStyle(Color.ink3)
                }
                Spacer(minLength: 0)
                (Text("목표 ").foregroundColor(Color.ink3) + Text(L.withUnit(m.targetValue, m.unit)).bold().foregroundColor(Color.success))
                    .font(.pt(13))
            }
            .foregroundStyle(Color.ink)

            GeometryReader { geo in
                let w = geo.size.width
                ZStack(alignment: .leading) {
                    Capsule().fill(Color.progressTrack).frame(height: 12)
                    Capsule().fill(Color.brand).frame(width: max(w * CGFloat(now), 0), height: 12)
                    if let expected {
                        RoundedRectangle(cornerRadius: 1)
                            .fill(Color.ink2)
                            .frame(width: 2, height: 20)
                            .offset(x: w * CGFloat(expected) - 1)
                    }
                    Circle()
                        .fill(Color.brand)
                        .overlay(Circle().stroke(Color.surface, lineWidth: 3))
                        .frame(width: 20, height: 20)
                        .shadow(color: .black.opacity(0.15), radius: 2, y: 1)
                        .offset(x: w * CGFloat(now) - 10)
                }
                .frame(height: 20)
            }
            .frame(height: 20)

            FlowLayout(spacing: 12) {
                (Text("지금 ") + Text(L.withUnit(m.currentValue, m.unit)).bold().foregroundColor(Color.ink)
                 + Text(m.status != .ACHIEVED ? " · 목표까지 \(L.withUnit(abs(m.targetValue - m.currentValue), m.unit)) 남음" : ""))
                if let e = m.expectedValue {
                    HStack(spacing: 4) {
                        RoundedRectangle(cornerRadius: 1).fill(Color.ink2).frame(width: 2, height: 12)
                        Text("오늘 계획 \(L.withUnit1(e, m.unit))")
                    }
                }
            }
            .font(.pt(12))
            .foregroundStyle(Color.ink3)
        }
        .padding(.top, 4)
    }

    // ── "오늘 계획보다 0.3kg 늦어요 · 이 속도면 10월 16일 도달" ──

    private var insightLines: [String] {
        var lines: [String] = []
        let dir: Double = m.targetValue - m.startValue >= 0 ? 1 : -1
        if m.status == .ACHIEVED {
            lines.append("목표 \(L.withUnit(m.targetValue, m.unit))에 도달했어요. 정말 잘했어요!")
        } else if let e = m.expectedValue, m.status == .AHEAD || m.status == .BEHIND || m.status == .ON_TRACK {
            let gap = (m.currentValue - e) * dir
            let g = L.withUnit1(abs(gap), m.unit)
            switch m.status {
            case .AHEAD: lines.append("오늘 계획보다 \(g) 앞서 있어요.")
            case .BEHIND: lines.append("오늘 계획보다 \(g) 뒤처져 있어요. 조금만 더 힘내요!")
            default: lines.append("계획한 속도에 맞춰 잘 가고 있어요.")
            }
        }
        if let projected = m.projectedDate, m.status != .ACHIEVED {
            var tail = ""
            if let end = p.endDate {
                let diff = KST.diffDays(end, projected)
                tail = diff > 0 ? " (종료일보다 \(diff)일 늦어요)" : (diff < 0 ? " (종료일보다 \(-diff)일 빨라요)" : " (종료일에 딱 맞아요)")
            }
            lines.append("최근 기록 추세라면 \(L.md(projected))쯤 목표에 닿아요\(tail).")
        }
        return lines
    }

    @ViewBuilder private var insight: some View {
        let lines = insightLines
        if !lines.isEmpty {
            VStack(alignment: .leading, spacing: 2) {
                ForEach(lines, id: \.self) { Text($0) }
            }
            .font(.pt(13))
            .foregroundStyle(Color.ink)
            .lineSpacing(3)
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.subtle, in: RoundedRectangle(cornerRadius: Radius.xxl))
            .padding(.top, 16)
        }
    }

    // ── 그래프 ──

    private var chart: some View {
        let from = p.startDate ?? m.logs.first?.date ?? p.today
        let lastLog = m.logs.last?.date
        let candidates = [p.endDate, lastLog, p.today, m.planLine.last?.date].compactMap { $0 }
        var to = candidates.max() ?? p.today
        if p.endDate == nil { to = KST.addDays(to, 7) }
        if to <= from { to = KST.addDays(from, 7) }

        var actual = m.logs.map { GoalChartPoint(date: $0.date, value: $0.value) }
        if let s = p.startDate, !m.logs.contains(where: { $0.date <= s }) {
            actual.insert(GoalChartPoint(date: s, value: m.startValue), at: 0)
        }
        let series = [
            GoalChartSeries(key: "plan", name: "계획", kind: .plan,
                            points: m.planLine.map { GoalChartPoint(date: $0.date, value: $0.value, label: $0.label) }),
            GoalChartSeries(key: "actual", name: "기록", kind: .actual, points: actual),
        ]
        let currentDate = m.lastLoggedDate ?? ((p.startDate.map { $0 < p.today } ?? false) ? p.startDate! : p.today)
        let unit = m.unit

        return GoalProgressChart(
            series: series, from: from, to: to, today: p.today,
            target: GoalChartMarker(value: m.targetValue, label: "목표 \(L.withUnit(m.targetValue, unit))"),
            current: GoalChartMarker(date: currentDate, value: m.currentValue, label: "지금 \(L.withUnit(m.currentValue, unit))"),
            format: { L.withUnit($0, unit) },
            onInteract: { vm.trackChartInteract("main_metric") }
        )
    }

    // ── 지난 기록 ──

    @ViewBuilder private var recentLogs: some View {
        if !m.logs.isEmpty {
            let rows = Array(m.logs.reversed())
            let shown = showAll ? rows : Array(rows.prefix(5))
            let notes: [String: GoalReflection] = Dictionary((p.reflections ?? []).map { ($0.date, $0) }, uniquingKeysWith: { a, _ in a })
            VStack(alignment: .leading, spacing: 8) {
                Text("기록").font(.pt(12, .semibold)).foregroundStyle(Color.ink3)
                VStack(spacing: 0) {
                    ForEach(Array(shown.enumerated()), id: \.element.date) { i, l in
                        if i > 0 { Rectangle().fill(Color.borderSoft).frame(height: 1) }
                        logRow(l, note: notes[l.date])
                    }
                }
                if rows.count > 5 {
                    Button(showAll ? "접기" : "기록 \(rows.count - 5)개 더 보기") {
                        showAll.toggle()
                        vm.track("goal_detail.metric_logs_more_toggle", ["expanded": showAll, "log_count": rows.count])
                    }
                        .font(.pt(13, .medium))
                        .foregroundStyle(Color.ink3)
                        .buttonStyle(.plain)
                }
            }
            .padding(.top, 20)
        }
    }

    private func logRow(_ l: MetricLog, note: GoalReflection?) -> some View {
        let dir: Double = m.targetValue - m.startValue >= 0 ? 1 : -1
        let idx = m.logs.firstIndex { $0.date == l.date } ?? 0
        let prev = idx > 0 ? m.logs[idx - 1].value : m.startValue
        let delta = l.value - prev
        let good = delta * dir > 0
        let mood = GoalMoodOption.of(note?.mood)
        let memo = note?.note ?? l.memo ?? ""
        let noteText = (mood.map { $0.emoji + " " } ?? "") + memo

        return HStack(spacing: 10) {
            Text(L.md(l.date)).font(.pt(13)).foregroundStyle(Color.ink3).frame(width: 64, alignment: .leading)
            Text(L.withUnit(l.value, m.unit)).font(.ptMono(14, .bold)).foregroundStyle(Color.ink)
            if delta != 0 {
                HStack(spacing: 2) {
                    NSymbol(delta < 0 ? "arrow.down.right" : "arrow.up.right", size: 10, weight: .bold)
                    Text(L.fmtNum(abs(delta)))
                }
                .font(.pt(12, .semibold))
                .foregroundStyle(good ? Color.success : Color.warning)
            }
            Text(noteText)
                .font(.pt(13))
                .foregroundStyle(Color.ink3)
                .lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .leading)
            GoalRowActions(label: "\(L.md(l.date)) 기록",
                           onEdit: {
                               vm.track("goal_detail.metric_log_edit_click", ["days_ago": GD.daysAgo(l.date)])
                               vm.sheet = .metricLog(date: l.date, value: l.value)
                           },
                           onDelete: { vm.ask(.deleteMetricLog(date: l.date, value: l.value, unit: m.unit)) })
        }
        .padding(.vertical, 6)
    }
}

/// 수치 목표 아래 한 줄: Task 실행 현황
@MainActor
private struct GoalTaskProgressLine: View {
    let t: TaskProgress

    var body: some View {
        if t.total > 0 {
            VStack(alignment: .leading, spacing: 0) {
                Rectangle().fill(Color.borderSoft).frame(height: 1).padding(.bottom, 16)
                line
                    .font(.pt(13))
                    .foregroundStyle(Color.ink3)
            }
            .padding(.top, 20)
        }
    }

    private var line: Text {
        var text = Text("관련 Task ") + Text("\(t.completed)").bold().foregroundColor(Color.ink) + Text(" / \(t.total)개 완료")
        if t.dueByToday > 0, let c = t.completionPercent {
            text = text + Text(" · 오늘까지 할 일 중 ") + Text("\(Int(c.rounded()))%").bold().foregroundColor(Color.ink) + Text(" 해냈어요")
        }
        return text
    }
}

// MARK: - 수치 없는 목표: Task 누적

@MainActor
private struct GoalTaskProgressView: View {
    let p: GoalProgress
    @Environment(GoalDetailViewModel.self) private var vm

    var body: some View {
        let t = p.tasks
        let behind = max(t.dueByToday - t.completed, 0)
        let ratio = t.dueByToday == 0 ? 0 : min(Double(t.completed) / Double(t.dueByToday), 1)

        VStack(alignment: .leading, spacing: 0) {
            if t.dueByToday == 0 {
                Text(t.total == 0 ? "아직 이 목표에 Task가 없어요." : "오늘까지 해야 할 Task는 아직 없어요.")
                    .font(.pt(14))
                    .foregroundStyle(Color.ink)
            } else {
                HStack(alignment: .bottom) {
                    (Text("오늘까지 할 Task ") + Text("\(t.dueByToday)개").bold() + Text(" 중 ")
                     + Text("\(t.completed)개").bold().foregroundColor(Color.textBrand) + Text(" 완료"))
                        .font(.pt(14))
                        .foregroundStyle(Color.ink)
                    Spacer(minLength: 0)
                    HStack(alignment: .firstTextBaseline, spacing: 1) {
                        Text("\(Int((ratio * 100).rounded()))").font(.ptMono(28, .bold)).foregroundStyle(Color.ink)
                        Text("%").font(.pt(15, .semibold)).foregroundStyle(Color.ink3)
                    }
                }
                ProgressBar(value: ratio, height: 12).padding(.top, 8)
                Text(behind == 0 ? "밀린 Task 없이 계획대로 가고 있어요." : "밀린 Task \(behind)개 — 하나씩 끝내면 그래프가 따라잡아요.")
                    .font(.pt(12))
                    .foregroundStyle(Color.ink3)
                    .padding(.top, 8)
            }

            if let from = t.series.first?.date, let to = t.series.last?.date, t.total > 0 {
                chart(from: from, to: to).padding(.top, 20)
                GoalChartLegend(items: [("해야 할 Task (누적)", .plan), ("완료한 Task (누적)", .actual)]).padding(.top, 8)
            }

            HStack(spacing: 12) {
                Text("몸무게·점수·권수처럼 숫자로 잴 수 있는 목표라면, 목표 수치를 정하고 매일 기록해 보세요. 목표까지 남은 거리를 그래프로 보여드려요.")
                    .font(.pt(13))
                    .foregroundStyle(Color.ink2)
                    .frame(maxWidth: .infinity, alignment: .leading)
                NButton("목표 수치 정하기", variant: .secondary, size: .sm) {
                    vm.sheet = .editMetric(entryPoint: "progress_cta")
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(Color.subtle, in: RoundedRectangle(cornerRadius: Radius.xxl))
            .padding(.top, 20)
        }
    }

    private func chart(from: String, to: String) -> some View {
        let t = p.tasks
        let series = [
            GoalChartSeries(key: "plan", name: "해야 할 Task", kind: .plan,
                            points: t.series.map { GoalChartPoint(date: $0.date, value: Double($0.planned)) }),
            GoalChartSeries(key: "actual", name: "완료한 Task", kind: .actual,
                            points: t.series.compactMap { s in s.completed.map { GoalChartPoint(date: s.date, value: Double($0)) } }),
        ]
        let todayPoint = t.series.first { $0.date == p.today }
        let current = todayPoint.map {
            GoalChartMarker(date: p.today, value: Double($0.completed ?? 0), label: "완료 \(t.completed)개")
        }
        return GoalProgressChart(
            series: series, from: from, to: to, today: p.today,
            current: current,
            format: { "\(GoalDetailLogic.fmtNum($0))개" },
            height: 200,
            zeroBased: true,
            onInteract: { vm.trackChartInteract("task_cumulative") }
        )
    }
}

// MARK: - 지난 수치 기록 고치기 시트 (웹 CheckInPrefilled)

@MainActor
struct GoalMetricLogEditSheet: View {
    let goalId: Int
    let metric: MetricProgress
    let today: String
    let initialDate: String

    @Environment(GoalDetailViewModel.self) private var vm
    @State private var date: String
    @State private var value: String
    @State private var saving = false
    @State private var outcome: String?

    init(goalId: Int, metric: MetricProgress, today: String, date: String, value: Double) {
        self.goalId = goalId
        self.metric = metric
        self.today = today
        self.initialDate = date
        _date = State(initialValue: date)
        _value = State(initialValue: GoalDetailLogic.fmtNum(value))
    }

    private var num: Double? {
        let t = value.trimmingCharacters(in: .whitespaces)
        guard !t.isEmpty, let v = Double(t), v.isFinite else { return nil }
        return v
    }

    var body: some View {
        SheetScaffold(title: "\(GoalDetailLogic.md(date)) 기록 고치기", dismissible: !saving) {
            GoalInlineForm(saving: saving, valid: num != nil, onCancel: {
                outcome = "cancel_button"
                vm.sheet = nil
            }, onSubmit: submit) {
                GoalFormField("기록 날짜") {
                    GoalDateInput(value: Binding(get: { date }, set: { date = $0.isEmpty ? initialDate : $0 }), maxDate: today)
                }
                GoalFormField("\(metric.name ?? "수치") 값") {
                    HStack(spacing: 6) {
                        TextField("", text: $value)
                            .keyboardType(.decimalPad)
                            .font(.pt(15, .semibold))
                            .inputStyle()
                        if let u = metric.unit, !u.isEmpty {
                            Text(u).font(.pt(13)).foregroundStyle(Color.ink3)
                        }
                    }
                }
            }
            .goalSheetForm()
        }
        .onDisappear {
            if let m = GD.closeMethod(outcome) {
                vm.track("goal_detail.metric_log_edit_cancel", ["close_method": m])
            }
        }
    }

    private func submit() {
        guard let v = num, !saving else { return }
        let d = date
        let id = goalId
        vm.track("goal_detail.metric_log_edit_save", ["days_ago": GD.daysAgo(initialDate), "date_changed": d != initialDate])
        saving = true
        Task { @MainActor in
            let r = await Mutation.run(invalidate: [.goals], successMessage: "기록했어요.",
                                       errorFallback: "기록하지 못했어요. 다시 시도해 주세요.") {
                try await GoalEditAPI.recordMetric(goalId: id, date: d, value: v)
            }
            saving = false
            vm.track("goal_detail.metric_result", GD.result(r != nil).merging(["metric_action": "record"]) { $1 })
            if let r {
                outcome = "saved"
                if let p = r { vm.progress = p }
                vm.sheet = nil
            }
        }
    }
}

#Preview("수치 목표") {
    ScrollView {
        GoalProgressSection(goal: GoalPreviewData.goal).padding(16)
    }
    .canvasBackground()
    .environment(GoalDetailViewModel.preview())
}

#Preview("Task 누적") {
    ScrollView {
        GoalProgressSection(goal: GoalPreviewData.goal).padding(16)
    }
    .canvasBackground()
    .environment(GoalDetailViewModel.preview(progress: GoalPreviewData.taskOnlyProgress))
}
