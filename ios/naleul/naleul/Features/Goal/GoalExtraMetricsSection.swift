// Features/Goal/GoalExtraMetricsSection.swift
//
// 웹 features/goal/ui/progress/ExtraMetricsSection.tsx — 목표형 추가 그래프 (V52).
// 메인 수치("지금 어디쯤?") 말고 같이 보고 싶은 수치를 더 그려요 (최대 5개).
// 예) SQLD: 메인 "모의고사 점수 40 → 70점" + 추가 "기출 회독 0 → 5회"
// 그래프마다 오늘 기대값("계획대로라면 오늘까지 2회")을 보여줘서 오늘 어디까지 해야 하는지 바로 알 수 있어요.
//
// 응답이 진행 데이터(GoalProgress) 전체라 vm.progress 에 그대로 넣어 바로 다시 그려요.

import SwiftUI
import UIKit

// MARK: - API

enum GoalExtraMetricAPI {
    /// 최대 개수
    static let maxCount = 5

    static func create(goalId: Int, name: String, unit: String?, startValue: Double, targetValue: Double) async throws -> GoalProgress? {
        try await API.post("/v1/goal-categories/\(goalId)/metrics", body: JSONValue.compact([
            "name": .string(name),
            "unit": unit.map { JSONValue.string($0) },
            "startValue": .double(startValue),
            "targetValue": .double(targetValue),
        ]))
    }

    static func update(goalId: Int, metricId: Int, name: String?, unit: String?,
                       startValue: Double?, targetValue: Double?) async throws -> GoalProgress? {
        try await API.patch("/v1/goal-categories/\(goalId)/metrics/\(metricId)", body: JSONValue.compact([
            "name": name.map { JSONValue.string($0) },
            "unit": unit.map { JSONValue.string($0) },
            "startValue": startValue.map { JSONValue.double($0) },
            "targetValue": targetValue.map { JSONValue.double($0) },
        ]))
    }

    static func delete(goalId: Int, metricId: Int) async throws -> GoalProgress? {
        try await API.delete("/v1/goal-categories/\(goalId)/metrics/\(metricId)")
    }

    /// 기록 (date 가 nil 이면 오늘). 같은 날 다시 보내면 고쳐요
    static func record(goalId: Int, metricId: Int, date: String? = nil, value: Double, memo: String? = nil) async throws -> GoalProgress? {
        try await API.put("/v1/goal-categories/\(goalId)/metrics/\(metricId)/logs", body: JSONValue.compact([
            "date": date.map { JSONValue.string($0) },
            "value": .double(value),
            "memo": memo.map { JSONValue.string($0) },
        ]))
    }

    static func deleteLog(goalId: Int, metricId: Int, date: String) async throws -> GoalProgress? {
        try await API.delete("/v1/goal-categories/\(goalId)/metrics/\(metricId)/logs/\(date)")
    }
}

/// 그래프 시트: 새로 만들기 · 고치기
enum GoalExtraMetricFormTarget: Identifiable, Hashable {
    case create
    case edit(ExtraMetric)

    var id: String {
        switch self {
        case .create: return "new"
        case .edit(let m): return "metric:\(m.metricId)"
        }
    }

    var metric: ExtraMetric? {
        if case .edit(let m) = self { return m }
        return nil
    }
}

// MARK: - 섹션

@MainActor
struct GoalExtraMetricsSection: View {
    let goal: GoalCategory
    @Environment(GoalDetailViewModel.self) private var vm

    @State private var form: GoalExtraMetricFormTarget?
    @State private var deleteTarget: ExtraMetric?
    @State private var confirmDelete = false

    var body: some View {
        // 임시 목표는 기간 · 계획선이 없어서 숨겨요
        if !goal.isTemporary, let p = vm.progress {
            content(p)
        }
    }

    private func content(_ p: GoalProgress) -> some View {
        let metrics = (p.extraMetrics ?? []).sorted { $0.sortOrder < $1.sortOrder }
        let readOnly = goal.goalCategoryStatus == .COMPLETED
        return SectionCard("함께 보는 그래프", aside: {
            if !metrics.isEmpty { Text("\(metrics.count) / \(GoalExtraMetricAPI.maxCount)") }
        }) {
            if metrics.isEmpty {
                HStack(alignment: .top, spacing: 8) {
                    Image(systemName: "chart.xyaxis.line")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(Color.ink3)
                        .padding(.top, 2)
                    Text("메인 수치 말고도 매일 보고 싶은 숫자가 있다면 그래프를 더 만들어 보세요. 예) 기출 회독 0 → 5회, 공부 시간 0 → 60시간")
                        .font(.pt(13))
                        .foregroundStyle(Color.ink3)
                        .lineSpacing(3)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            VStack(spacing: 0) {
                ForEach(Array(metrics.enumerated()), id: \.element.metricId) { i, metric in
                    if i > 0 {
                        Rectangle().fill(Color.borderSoft).frame(height: 1).padding(.vertical, 20)
                    }
                    GoalExtraMetricCard(
                        goalId: goal.goalCategoryId,
                        p: p,
                        metric: metric,
                        readOnly: readOnly,
                        onEdit: {
                            vm.track("goal_detail.extra_metric_edit_click", ["metric_id": metric.metricId])
                            form = .edit(metric)
                        },
                        onDelete: {
                            vm.track("goal_detail.extra_metric_delete_click", [
                                "metric_id": metric.metricId, "log_count": metric.progress.logs.count,
                            ])
                            deleteTarget = metric
                            confirmDelete = true
                        }
                    )
                }
            }
            if !readOnly && metrics.count < GoalExtraMetricAPI.maxCount {
                GoalAddButton("그래프 추가") {
                    vm.track("goal_detail.extra_metric_add_click", ["metric_count": metrics.count])
                    form = .create
                }
            }
        }
        .sheet(item: $form) { target in
            GoalExtraMetricFormSheet(goalId: goal.goalCategoryId, metric: target.metric,
                                     onSaved: { vm.progress = $0 },
                                     onDone: { form = nil })
        }
        .confirmDialog(
            isPresented: $confirmDelete,
            title: "'\(deleteTarget?.progress.name ?? "")' 그래프를 지울까요?",
            message: "이 그래프에 남긴 기록도 함께 사라져요.",
            confirmLabel: "지우기",
            tone: .danger,
            onCancel: {
                vm.track("goal_detail.extra_metric_delete_cancel", ["metric_id": deleteTarget?.metricId])
                deleteTarget = nil
            }
        ) {
            if let m = deleteTarget {
                vm.track("goal_detail.extra_metric_delete_confirm", [
                    "metric_id": m.metricId, "log_count": m.progress.logs.count,
                ])
                Task { @MainActor in await remove(m) }
            }
        }
    }

    private func remove(_ m: ExtraMetric) async {
        let goalId = goal.goalCategoryId
        let metricId = m.metricId
        let r = await Mutation.run(successMessage: "그래프를 지웠어요.") {
            try await GoalExtraMetricAPI.delete(goalId: goalId, metricId: metricId)
        }
        vm.track("goal_detail.extra_metric_result", GD.result(r != nil).merging(["metric_action": "delete"]) { $1 })
        if let r, let next = r { vm.progress = next }
        deleteTarget = nil
    }
}

// MARK: - 그래프 하나

@MainActor
private struct GoalExtraMetricCard: View {
    let goalId: Int
    let p: GoalProgress
    let metric: ExtraMetric
    let readOnly: Bool
    let onEdit: () -> Void
    let onDelete: () -> Void

    @Environment(GoalDetailViewModel.self) private var vm
    @State private var value = ""
    @State private var saving = false
    @State private var showLogs = false
    @State private var removingDate: String?
    @FocusState private var focused: Bool

    private typealias L = GoalDetailLogic

    private var m: MetricProgress { metric.progress }

    private var num: Double? {
        let t = value.trimmingCharacters(in: .whitespaces)
        guard !t.isEmpty, let v = Double(t), v.isFinite else { return nil }
        return v
    }

    private var titleText: String {
        let name = m.name ?? "수치"
        if let u = m.unit, !u.isEmpty { return "\(name) (\(u))" }
        return name
    }

    /// 시작 → 목표 사이 위치 (0~1)
    private func pos(_ v: Double) -> Double {
        let span = m.targetValue - m.startValue
        guard span != 0 else { return 1 }
        return min(max((v - m.startValue) / span, 0), 1)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            header
            track
            if let e = m.expectedValue, m.status != .ACHIEVED {
                expectedLine(e)
            } else {
                insight
            }
            if !readOnly { recordRow }
            if m.planLine.count > 1 || !m.logs.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    chart
                    GoalChartLegend(items: [("계획", .plan), ("내 기록", .actual)])
                }
                .padding(.top, 4)
            }
            logList
        }
    }

    // ── 이름 · 상태 · 메뉴 ──

    private var header: some View {
        HStack(spacing: 8) {
            Text(titleText)
                .font(.pt(15, .bold))
                .foregroundStyle(Color.ink)
                .lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .leading)
            GoalProgressStatusChip(status: m.status)
            if !readOnly {
                Menu {
                    Button { onEdit() } label: { Label("그래프 수정", systemImage: "pencil") }
                    Button(role: .destructive) { onDelete() } label: { Label("그래프 삭제", systemImage: "trash") }
                } label: {
                    NSymbol("ellipsis", size: 15, weight: .semibold)
                        .foregroundStyle(Color.ink3)
                        .frame(width: 32, height: 32)
                        .contentShape(Rectangle())
                }
                .accessibilityLabel("\(m.name ?? "그래프") 더보기")
            }
        }
    }

    // ── 지금 / 목표 막대 ──

    private var track: some View {
        let percent = m.progressPercent ?? (pos(m.currentValue) * 100)
        return VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .firstTextBaseline, spacing: 4) {
                Text(L.withUnit(m.currentValue, m.unit))
                    .font(.ptMono(20, .bold))
                    .foregroundStyle(Color.ink)
                Text("/ 목표 \(L.withUnit(m.targetValue, m.unit))")
                    .font(.pt(13))
                    .foregroundStyle(Color.ink3)
                Spacer(minLength: 0)
                Text("\(Int(min(max(percent, 0), 999).rounded()))%")
                    .font(.ptMono(13, .semibold))
                    .foregroundStyle(Color.ink2)
            }
            ProgressBar(value: pos(m.currentValue),
                        color: m.status == .ACHIEVED ? Color.success : Color.brand,
                        height: 8)
            Text("시작 \(L.withUnit(m.startValue, m.unit))")
                .font(.pt(12))
                .foregroundStyle(Color.ink4)
        }
    }

    // ── "계획대로라면 오늘까지 2회 · 지금 1회" ──

    private func expectedLine(_ e: Double) -> some View {
        let rounded = (e * 10).rounded() / 10
        return (Text("계획대로라면 오늘까지 \(L.withUnit(rounded, m.unit))").foregroundColor(Color.textBrand)
                + Text(m.currentValue != e ? " · 지금 \(L.withUnit(m.currentValue, m.unit))" : "")
                    .font(.pt(13, .medium))
                    .foregroundColor(Color.ink2))
            .font(.pt(13, .semibold))
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.brandSoft, in: RoundedRectangle(cornerRadius: Radius.xl))
    }

    private var insightLines: [String] {
        var lines: [String] = []
        if m.status == .ACHIEVED {
            lines.append("목표 \(L.withUnit(m.targetValue, m.unit))에 도달했어요. 정말 잘했어요!")
        } else if let projected = m.projectedDate {
            var tail = ""
            if let end = p.endDate {
                let diff = KST.diffDays(end, projected)
                tail = diff > 0 ? " (종료일보다 \(diff)일 늦어요)" : (diff < 0 ? " (종료일보다 \(-diff)일 빨라요)" : " (종료일에 딱 맞아요)")
            }
            lines.append("최근 기록 추세라면 \(L.md(projected))쯤 목표에 닿아요\(tail).")
        } else if m.logs.isEmpty {
            lines.append("아직 기록이 없어요. 오늘 값을 남기면 그래프가 그려져요.")
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
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.subtle, in: RoundedRectangle(cornerRadius: Radius.xl))
        }
    }

    // ── 오늘 기록 ──

    private var recordRow: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("오늘 기록").font(.pt(12, .medium)).foregroundStyle(Color.ink3)
            HStack(spacing: 8) {
                HStack(spacing: 6) {
                    TextField(m.loggedToday ? "오늘 기록을 고쳐요" : "오늘 \(m.name ?? "값")", text: $value)
                        .keyboardType(.decimalPad)
                        .font(.pt(15, .semibold))
                        .focused($focused)
                    if let u = m.unit, !u.isEmpty {
                        Text(u).font(.pt(13)).foregroundStyle(Color.ink3)
                    }
                }
                .inputStyle()
                NButton("저장", size: .sm, loading: saving) { record() }
                    .disabled(num == nil)
            }
        }
    }

    private func record() {
        guard let v = num, !saving else { return }
        let id = goalId
        let metricId = metric.metricId
        vm.track("goal_detail.extra_metric_record_submit", ["metric_id": metricId, "is_overwrite": m.lastLoggedDate == p.today])
        saving = true
        Task { @MainActor in
            let r = await Mutation.run(successMessage: "기록했어요.") {
                try await GoalExtraMetricAPI.record(goalId: id, metricId: metricId, value: v)
            }
            saving = false
            vm.track("goal_detail.extra_metric_result", GD.result(r != nil).merging(["metric_action": "record"]) { $1 })
            if let r {
                if let next = r { vm.progress = next }
                value = ""
                focused = false
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
            }
        }
    }

    // ── 그래프 ──

    private var chart: some View {
        let from = p.startDate ?? m.logs.first?.date ?? p.today
        let candidates = [p.endDate, m.logs.last?.date, p.today, m.planLine.last?.date].compactMap { $0 }
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
        let currentDate: String
        if let last = m.lastLoggedDate {
            currentDate = last
        } else if let s = p.startDate, s < p.today {
            currentDate = s
        } else {
            currentDate = p.today
        }
        let unit = m.unit

        return GoalProgressChart(
            series: series, from: from, to: to, today: p.today,
            target: GoalChartMarker(value: m.targetValue, label: "목표 \(L.withUnit(m.targetValue, unit))"),
            current: GoalChartMarker(date: currentDate, value: m.currentValue, label: "지금 \(L.withUnit(m.currentValue, unit))"),
            format: { L.withUnit($0, unit) },
            height: 180,
            onInteract: { vm.trackChartInteract("extra_metric", metricId: metric.metricId) }
        )
    }

    // ── 지난 기록 ──

    @ViewBuilder private var logList: some View {
        if !m.logs.isEmpty {
            let rows = Array(m.logs.reversed())
            VStack(alignment: .leading, spacing: 4) {
                Button(showLogs ? "기록 접기" : "기록 \(rows.count)개 보기") {
                    withAnimation(.easeOut(duration: 0.18)) { showLogs.toggle() }
                    vm.track("goal_detail.extra_metric_logs_toggle", ["metric_id": metric.metricId, "expanded": showLogs])
                }
                .font(.pt(12, .semibold))
                .foregroundStyle(Color.ink3)
                .buttonStyle(.plain)
                if showLogs {
                    VStack(spacing: 0) {
                        ForEach(Array(rows.enumerated()), id: \.element.date) { i, l in
                            if i > 0 { Rectangle().fill(Color.borderSoft).frame(height: 1) }
                            HStack(spacing: 10) {
                                Text(L.md(l.date)).font(.pt(13)).foregroundStyle(Color.ink3).frame(width: 70, alignment: .leading)
                                Text(L.withUnit(l.value, m.unit)).font(.ptMono(13, .bold)).foregroundStyle(Color.ink)
                                Spacer(minLength: 0)
                                if !readOnly {
                                    if removingDate == l.date {
                                        ProgressView().controlSize(.mini)
                                    } else {
                                        IconButton(systemName: "xmark", size: 12, color: Color.ink4, label: "\(L.md(l.date)) 기록 지우기") {
                                            removeLog(l.date)
                                        }
                                    }
                                }
                            }
                            .padding(.vertical, 4)
                        }
                    }
                }
            }
        }
    }

    private func removeLog(_ date: String) {
        guard removingDate == nil else { return }
        let id = goalId
        let metricId = metric.metricId
        vm.track("goal_detail.extra_metric_log_delete_click", ["metric_id": metricId, "days_ago": GD.daysAgo(date)])
        removingDate = date
        Task { @MainActor in
            let r = await Mutation.run(successMessage: "기록을 지웠어요.") {
                try await GoalExtraMetricAPI.deleteLog(goalId: id, metricId: metricId, date: date)
            }
            removingDate = nil
            vm.track("goal_detail.extra_metric_result", GD.result(r != nil).merging(["metric_action": "log_delete"]) { $1 })
            if let r, let next = r { vm.progress = next }
        }
    }
}

// MARK: - 그래프 만들기 · 고치기 시트

@MainActor
struct GoalExtraMetricFormSheet: View {
    let goalId: Int
    /// nil 이면 새로 만들기
    let metric: ExtraMetric?
    let onSaved: (GoalProgress) -> Void
    let onDone: () -> Void

    @State private var name: String
    @State private var unit: String
    @State private var start: String
    @State private var target: String
    @State private var saving = false
    @State private var outcome: String?

    init(goalId: Int, metric: ExtraMetric?, onSaved: @escaping (GoalProgress) -> Void, onDone: @escaping () -> Void) {
        self.goalId = goalId
        self.metric = metric
        self.onSaved = onSaved
        self.onDone = onDone
        let m = metric?.progress
        _name = State(initialValue: m?.name ?? "")
        _unit = State(initialValue: m?.unit ?? "")
        _start = State(initialValue: m.map { GoalDetailLogic.fmtNum($0.startValue) } ?? "")
        _target = State(initialValue: m.map { GoalDetailLogic.fmtNum($0.targetValue) } ?? "")
    }

    private var trimmedName: String { name.trimmingCharacters(in: .whitespacesAndNewlines) }
    private var startNum: Double? { GoalDetailLogic.toNum(start) }
    private var targetNum: Double? { GoalDetailLogic.toNum(target) }

    private var error: String? {
        guard let s = startNum, let t = targetNum else { return nil }
        if s.isNaN || t.isNaN { return "숫자로 입력해 주세요." }
        if s == t { return "시작값과 목표값을 다르게 정해 주세요." }
        return nil
    }

    private var valid: Bool { !trimmedName.isEmpty && startNum != nil && targetNum != nil && error == nil }

    var body: some View {
        SheetScaffold(title: metric == nil ? "그래프 추가" : "그래프 수정", dismissible: !saving) {
            GoalInlineForm(submitLabel: metric == nil ? "그래프 추가" : "저장", saving: saving,
                           valid: valid, error: error, onCancel: {
                               outcome = "cancel_button"
                               onDone()
                           }, onSubmit: submit) {
                HStack(alignment: .top, spacing: 8) {
                    GoalFormField("무엇을 잴까요?") {
                        TextField("예: 기출 회독", text: $name)
                            .inputStyle()
                            .goalMaxLength($name, 30)
                    }
                    GoalFormField("단위") {
                        TextField("회", text: $unit)
                            .inputStyle()
                            .goalMaxLength($unit, 10)
                    }
                    .frame(width: 88)
                }
                HStack(alignment: .top, spacing: 8) {
                    GoalFormField("시작값") {
                        TextField("0", text: $start)
                            .keyboardType(.numbersAndPunctuation)
                            .inputStyle()
                    }
                    GoalFormField("목표값") {
                        TextField("5", text: $target)
                            .keyboardType(.numbersAndPunctuation)
                            .inputStyle()
                    }
                }
                Text("목표 기간 동안 시작값에서 목표값까지 곧게 가는 계획선이 그려져요.")
                    .font(.pt(12))
                    .foregroundStyle(Color.ink3)
            }
            .goalSheetForm()
        }
        .onDisappear {
            if let m = GD.closeMethod(outcome) {
                GD.track("goal_detail.extra_metric_cancel", ["goal_category_id": goalId],
                         ["is_new": metric == nil, "close_method": m])
            }
        }
    }

    private func submit() {
        guard valid, !saving, let s = startNum, let t = targetNum else { return }
        let n = trimmedName
        let u = unit.trimmingCharacters(in: .whitespacesAndNewlines)
        let id = goalId
        let metricId = metric?.metricId
        GD.track("goal_detail.extra_metric_save", ["goal_category_id": id], ["is_new": metricId == nil])
        saving = true
        Task { @MainActor in
            let r: GoalProgress??
            if let metricId {
                r = await Mutation.run(successMessage: "그래프를 고쳤어요.") {
                    try await GoalExtraMetricAPI.update(goalId: id, metricId: metricId, name: n, unit: u,
                                                        startValue: s, targetValue: t)
                }
            } else {
                r = await Mutation.run(successMessage: "그래프를 추가했어요.") {
                    try await GoalExtraMetricAPI.create(goalId: id, name: n, unit: u.isEmpty ? nil : u,
                                                        startValue: s, targetValue: t)
                }
            }
            saving = false
            GD.track("goal_detail.extra_metric_result", ["goal_category_id": id],
                     GD.result(r != nil).merging(["metric_action": metricId == nil ? "create" : "update"]) { $1 })
            if let r {
                outcome = "saved"
                if let next = r { onSaved(next) }
                onDone()
            }
        }
    }
}

// MARK: - 미리보기

private enum GoalExtraMetricPreviewData {
    static var progress: GoalProgress {
        var p = GoalPreviewData.progress
        let today = p.today
        let start = p.startDate ?? KST.addDays(today, -30)
        let end = p.endDate ?? KST.addDays(today, 60)
        let json = """
        [
          {"metricId": 1, "sortOrder": 0, "progress": {
            "name": "기출 회독", "unit": "회", "startValue": 0, "targetValue": 5, "currentValue": 1,
            "expectedValue": 1.7, "progressPercent": 20, "status": "BEHIND",
            "lastLoggedDate": "\(KST.addDays(today, -2))", "loggedToday": false,
            "planLine": [{"date": "\(start)", "value": 0, "label": "시작"}, {"date": "\(end)", "value": 5, "label": "목표"}],
            "logs": [{"date": "\(KST.addDays(today, -12))", "value": 0.5}, {"date": "\(KST.addDays(today, -2))", "value": 1}]
          }},
          {"metricId": 2, "sortOrder": 1, "progress": {
            "name": "공부 시간", "unit": "시간", "startValue": 0, "targetValue": 60, "currentValue": 62,
            "progressPercent": 103, "status": "ACHIEVED", "loggedToday": true, "lastLoggedDate": "\(today)",
            "planLine": [{"date": "\(start)", "value": 0}, {"date": "\(end)", "value": 60}],
            "logs": [{"date": "\(KST.addDays(today, -20))", "value": 20}, {"date": "\(today)", "value": 62}]
          }}
        ]
        """
        // 미리보기 전용이라 실패하면 바로 알 수 있게 강제로 풀어요
        p.extraMetrics = try! JSONDecoder().decode([ExtraMetric].self, from: Data(json.utf8))
        return p
    }
}

#Preview("추가 그래프") {
    ScrollView {
        GoalExtraMetricsSection(goal: GoalPreviewData.goal).padding(16)
    }
    .canvasBackground()
    .environment(GoalDetailViewModel.preview(progress: GoalExtraMetricPreviewData.progress))
    .environment(AppRouter.shared)
}

#Preview("그래프 없음") {
    ScrollView {
        GoalExtraMetricsSection(goal: GoalPreviewData.goal).padding(16)
    }
    .canvasBackground()
    .environment(GoalDetailViewModel.preview())
    .environment(AppRouter.shared)
}

#Preview("그래프 추가 시트") {
    Color.canvas
        .sheet(isPresented: .constant(true)) {
            GoalExtraMetricFormSheet(goalId: 1, metric: nil, onSaved: { _ in }, onDone: {})
        }
}
