// Features/Record/LogDoneView.swift
//
// 웹 features/record/ui/LogDoneView.tsx
// Task 추가 › "한 일 기록" — 이미 한 일을 Brain dump 처럼 한 번에 적어요.
//   1. 한 줄에 하나씩 적기 → AI 가 제목·시간을 정리하고 목표를 연결 (있는 목표, 없으면 임시 목표 제안)
//   2. 연결만 확인하고 저장 → TimeTable 의 "실제" 칸과 각 목표(기록형이면 업무 일지)에 쌓여요
//   3. 완료 — 무엇을 기록했는지 보여주고 "더 기록하기"
//
// 웹 <select> 목표 고르기 → iOS Menu. 웹 ⌘+Enter 단축키는 아이폰에 없어서 뺐어요.
// 이 화면은 Task 추가 시트 안에 들어가요 (시트가 스크롤을 맡으므로 여기서는 ScrollView 를 만들지 않아요).

import SwiftUI

/// 검토 화면의 한 줄
private struct RecordLogRow: Identifiable, Hashable {
    var id: String { clientKey }
    var clientKey: String
    var text: String
    var title: String
    var date: String?
    var startTime: String?
    var endTime: String?
    var minutes: Int?
    /// "goal:12" · "temp:t1" · "" (목표 없이)
    var link: String
}

struct LogDoneView: View {
    let goals: [GoalCategory]
    /// 입력칸에 글자가 있는지 (Task 추가 시트의 has_input — 방식을 바꾸거나 닫으면 확인 없이 사라져요)
    var onInputChange: (Bool) -> Void = { _ in }

    @State private var text = ""
    @State private var rows: [RecordLogRow]?
    @State private var temps: [TempGoalInfo] = []
    @State private var done: RecordActivityLogResponse?
    @State private var parsing = false
    @State private var saving = false

    private static let maxLines = 20
    private static let placeholder = "오전 10시 주간 회의 1시간\n기획서 초안 작성 2시간\n어제 코드 리뷰 30분\n운동 30분"

    init(goals: [GoalCategory], onInputChange: @escaping (Bool) -> Void = { _ in }) {
        self.goals = goals
        self.onInputChange = onInputChange
    }

    /// 미리보기용 — 단계별 화면을 네트워크 없이 그려 보려고
    fileprivate init(goals: [GoalCategory], previewRows: [RecordLogRow]?, previewTemps: [TempGoalInfo] = [],
                     previewDone: RecordActivityLogResponse? = nil) {
        self.goals = goals
        _rows = State(initialValue: previewRows)
        _temps = State(initialValue: previewTemps)
        _done = State(initialValue: previewDone)
    }

    private var lines: [String] {
        Array(text.split(separator: "\n", omittingEmptySubsequences: false)
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty }
            .prefix(Self.maxLines))
    }

    var body: some View {
        Group {
            if let done {
                doneStep(done)
            } else if let rows {
                reviewStep(rows)
            } else {
                writeStep
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card()
        .onChange(of: text) { _, v in onInputChange(!v.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty) }
    }

    // MARK: 1. 적기

    private var writeStep: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("오늘 한 일을 한 줄씩 적어 주세요").font(.pt(16, .semibold)).foregroundStyle(Color.ink)
            Text("시각(오후 2시)·걸린 시간(30분)·날짜(어제)를 같이 적으면 그대로 기록해요. 목표는 AI가 알아서 연결해요.")
                .font(.pt(13))
                .foregroundStyle(Color.ink3)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 4)
            ZStack(alignment: .topLeading) {
                TextEditor(text: $text)
                    .inputStyle(minHeight: 150)
                if text.isEmpty {
                    Text(Self.placeholder)
                        .font(.pt(15))
                        .foregroundStyle(Color.ink4)
                        .lineSpacing(4)
                        .padding(.horizontal, 19)
                        .padding(.vertical, 18)
                        .allowsHitTesting(false)
                }
            }
            .padding(.top, 16)
            HStack(spacing: 8) {
                Text("\(lines.count)/\(Self.maxLines)줄")
                    .font(.ptMono(12))
                    .foregroundStyle(Color.ink3)
                Spacer(minLength: 0)
                NButton("정리하기", variant: .ai, icon: "sparkles", loading: parsing) {
                    Task { await goParse() }
                }
                .disabled(lines.isEmpty)
            }
            .padding(.top, 12)
        }
    }

    // MARK: 2. 목표 연결 확인

    private func reviewStep(_ rows: [RecordLogRow]) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 8) {
                Text("이렇게 기록할게요").font(.pt(16, .semibold)).foregroundStyle(Color.ink)
                Spacer(minLength: 0)
                Button {
                    TA.plain("task_add_log.rewrite_click", ["item_count": rows.count])
                    self.rows = nil
                } label: {
                    HStack(spacing: 4) {
                        NSymbol("arrow.left", size: 13, weight: .medium)
                        Text("다시 적기")
                    }
                    .font(.pt(14))
                    .foregroundStyle(Color.ink3)
                }
                .buttonStyle(.plain)
            }
            Text("목표 연결만 확인하세요. 시각이 없는 일은 방금 끝낸 일로, 다른 기록과 겹치지 않게 채워요.")
                .font(.pt(13))
                .foregroundStyle(Color.ink3)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 4)
            VStack(spacing: 8) {
                ForEach(rows) { r in
                    RecordLogRowEditor(
                        row: r,
                        goals: goals,
                        temps: temps,
                        title: titleBinding(r.clientKey),
                        onLink: { link in
                            TA.plain("task_add_log.link_change", [
                                "goal_match": link.hasPrefix("goal:") ? "existing" : (link.hasPrefix("temp:") ? "temp" : "none"),
                            ])
                            update(r.clientKey) { $0.link = link }
                        },
                        onRemove: {
                            TA.plain("task_add_log.row_remove", ["line_count": rows.count - 1])
                            self.rows = self.rows?.filter { $0.clientKey != r.clientKey }
                        }
                    )
                }
            }
            .padding(.top, 16)
            HStack {
                Spacer(minLength: 0)
                NButton("\(rows.count)개 기록하기", loading: saving) {
                    Task { await goSave() }
                }
                .disabled(rows.isEmpty)
            }
            .padding(.top, 16)
        }
    }

    private func titleBinding(_ key: String) -> Binding<String> {
        Binding(
            get: { rows?.first { $0.clientKey == key }?.title ?? "" },
            set: { v in update(key) { $0.title = String(v.prefix(60)) } }
        )
    }

    private func update(_ key: String, _ change: (inout RecordLogRow) -> Void) {
        guard var rs = rows, let i = rs.firstIndex(where: { $0.clientKey == key }) else { return }
        change(&rs[i])
        rows = rs
    }

    // MARK: 3. 완료

    private func doneStep(_ done: RecordActivityLogResponse) -> some View {
        let today = KST.today()
        return VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 8) {
                NSymbol("checkmark", size: 12, weight: .heavy)
                    .foregroundStyle(Color.onAccentMint)
                    .frame(width: 24, height: 24)
                    .background(Color.accentMint, in: Circle())
                Text("\(done.activities.count)개를 기록했어요").font(.pt(16, .semibold)).foregroundStyle(Color.ink)
            }
            VStack(spacing: 0) {
                ForEach(Array(done.activities.enumerated()), id: \.element.activityId) { i, a in
                    if i > 0 { Divider().overlay(Color.borderSoft) }
                    RecordLoggedActivityRow(activity: a, today: today)
                }
            }
            .overlay(RoundedRectangle(cornerRadius: Radius.xxl).stroke(Color.line, lineWidth: 1))
            .padding(.top, 16)
            HStack(spacing: 8) {
                NButton("더 기록하기") {
                    TA.plain("task_add_log.more_click", ["created_count": done.activities.count])
                    reset()
                }
                NButton("TimeTable에서 보기", variant: .ghost, size: .sm) {
                    // 웹 Link href="/" — Task 추가 시트를 닫고 캘린더로
                    TA.track("task_add_log.done_link_click", ["destination": "calendar"])
                    Analytics.sheetCloseHint = "next"
                    AppRouter.shared.taskAdd = nil
                    AppRouter.shared.openCalendar()
                }
            }
            .padding(.top, 16)
        }
    }

    private func reset() {
        text = ""
        rows = nil
        temps = []
        done = nil
    }

    // MARK: 동작

    private static let pastWord = "(어제|그제|그저께)"

    private static func hasPastWord(_ s: String) -> Bool {
        s.range(of: pastWord, options: .regularExpression) != nil
    }

    @MainActor
    private func goParse() async {
        let ls = lines
        guard !ls.isEmpty, !parsing else { return }
        let today = KST.today()
        let req: [ParseItemRequest] = ls.map { line in
            let p = LineParser.parse(line, today: today)
            return ParseItemRequest(clientKey: RecordAPI.newClientKey(), text: line,
                                    startTime: p.startTime, endTime: p.endTime, estimatedMinutes: p.minutes)
        }
        let allLines = text.split(separator: "\n").filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }.count
        TA.plain("task_add_log.parse_click", [
            "line_count": ls.count, "dropped_count": allLines - ls.count,
            "has_parsed_time": req.contains { $0.startTime != nil }, "submit_method": "button",
        ])
        parsing = true
        defer { parsing = false }
        let result = await Mutation.run(errorFallback: "정리하지 못했어요. 다시 시도해 주세요.") {
            try await BrainDumpAPI.parse(req)
        }
        guard let parsed = result ?? nil else {
            TA.plain("task_add_log.parse_result", ["result": "fail", "error_status": Mutation.lastErrorStatus ?? 0])
            return
        }
        rows = parsed.items.map { (i: ParsedItem) -> RecordLogRow in
            let original = req.first { $0.clientKey == i.clientKey }?.text ?? i.title
            var link = ""
            if i.goal.type == .EXISTING, let gid = i.goal.goalCategoryId {
                link = "goal:\(gid)"
            } else if i.goal.type == .TEMP, let tk = i.goal.tempKey {
                link = "temp:\(tk)"
            }
            return RecordLogRow(
                clientKey: i.clientKey,
                text: original,
                title: i.title,
                // "어제"는 Task 기준 AI 가 오늘로 당겨 오니 서버가 문장으로 다시 읽게 비워요
                date: Self.hasPastWord(original) ? nil : i.scheduledDate,
                startTime: i.startTime.map { String($0.prefix(5)) },
                endTime: i.endTime.map { String($0.prefix(5)) },
                minutes: i.estimatedMinutes,
                link: link
            )
        }
        temps = parsed.tempGoals
        TA.plain("task_add_log.parse_result", [
            "result": "success", "item_count": parsed.items.count, "ai_failed": parsed.aiFailed,
            "temp_count": parsed.tempGoals.count, "missing_goal_count": (rows ?? []).filter { $0.link.isEmpty }.count,
        ])
    }

    @MainActor
    private func goSave() async {
        guard let rows, !rows.isEmpty, !saving else { return }
        let usedTemps = Set(rows.filter { $0.link.hasPrefix("temp:") }.map { String($0.link.dropFirst(5)) })
        let items = rows.map { r -> RecordActivityLogItem in
            let trimmed = r.title.trimmingCharacters(in: .whitespacesAndNewlines)
            return RecordActivityLogItem(
                clientKey: r.clientKey,
                text: r.text,
                title: trimmed.isEmpty ? r.text : trimmed,
                date: r.date,
                startTime: r.startTime,
                endTime: r.startTime != nil ? r.endTime : nil,
                minutes: r.minutes,
                goalCategoryId: r.link.hasPrefix("goal:") ? Int(r.link.dropFirst(5)) : nil,
                tempGoalKey: r.link.hasPrefix("temp:") ? String(r.link.dropFirst(5)) : nil
            )
        }
        let tempGoals = temps.filter { usedTemps.contains($0.tempKey) }
            .map { TempGoalInput(tempKey: $0.tempKey, name: $0.name, emoji: $0.emoji) }
        let request = RecordActivityLogRequest(items: items, tempGoals: tempGoals)
        TA.plain("task_add_log.save_click", [
            "item_count": rows.count, "missing_goal_count": rows.filter { $0.link.isEmpty }.count, "temp_count": usedTemps.count,
        ])

        saving = true
        defer { saving = false }
        let result = await Mutation.run {
            try await RecordAPI.logActivities(request)
        }
        guard let res = result ?? nil else {
            TA.plain("task_add_log.save_result", ["result": "fail", "error_status": Mutation.lastErrorStatus ?? 0])
            return
        }
        TA.plain("task_add_log.save_result", [
            "result": "success", "created_count": res.activities.count, "temp_count": res.tempGoals.count,
        ])
        var keys: [DataKey] = [.activities, .record]
        if !res.tempGoals.isEmpty { keys.append(.goals) }
        Invalidation.shared.invalidate(keys)
        done = res
        Toast.success("\(res.activities.count)개를 기록했어요.")
    }
}

// MARK: - 검토 줄 하나

private struct RecordLogRowEditor: View {
    let row: RecordLogRow
    let goals: [GoalCategory]
    let temps: [TempGoalInfo]
    @Binding var title: String
    let onLink: (String) -> Void
    let onRemove: () -> Void

    private var whenLabel: String {
        let today = KST.today()
        var prefix = ""
        if let d = row.date, d != today {
            prefix = "\(KST.formatMonthDay(d)) "
        } else if row.text.range(of: "(어제|그제|그저께)", options: .regularExpression) != nil {
            prefix = "지난 날 "
        }
        if let s = row.startTime {
            return prefix + s + (row.endTime.map { "–\($0)" } ?? "")
        }
        return prefix + "방금"
    }

    private var linkLabel: String {
        if row.link.hasPrefix("goal:"), let id = Int(row.link.dropFirst(5)),
           let g = goals.first(where: { $0.goalCategoryId == id }) {
            return (g.emoji.map { "\($0) " } ?? "") + g.goalCategoryName
        }
        if row.link.hasPrefix("temp:") {
            let key = String(row.link.dropFirst(5))
            if let t = temps.first(where: { $0.tempKey == key }) { return "＋ 새 임시 목표: \(t.name)" }
        }
        return "목표 없이"
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 8) {
                TextField("제목", text: $title)
                    .font(.pt(15, .semibold))
                    .foregroundStyle(Color.ink)
                    .frame(height: 36)
                    .accessibilityLabel("제목")
                IconButton(systemName: "xmark", size: 14, color: .ink4, label: "빼기", action: onRemove)
            }
            HStack(spacing: 6) {
                RecordLogChip(icon: "clock", text: whenLabel)
                if let m = row.minutes, row.endTime == nil {
                    RecordLogChip(icon: "timer", text: KST.formatDuration(m))
                }
                Spacer(minLength: 0)
                goalMenu
            }
        }
        .padding(12)
        .overlay(RoundedRectangle(cornerRadius: Radius.xxl).stroke(Color.line, lineWidth: 1))
    }

    private var goalMenu: some View {
        Menu {
            Button("목표 없이") { onLink("") }
            ForEach(goals) { g in
                Button((g.emoji.map { "\($0) " } ?? "") + g.goalCategoryName) { onLink("goal:\(g.goalCategoryId)") }
            }
            ForEach(temps, id: \.tempKey) { t in
                Button("＋ 새 임시 목표: \(t.name)") { onLink("temp:\(t.tempKey)") }
            }
        } label: {
            HStack(spacing: 4) {
                Text(linkLabel).lineLimit(1)
                Image(systemName: "chevron.up.chevron.down").font(.system(size: 10, weight: .semibold))
            }
            .font(.pt(13))
            .foregroundStyle(row.link.isEmpty ? Color.ink3 : Color.ink)
            .padding(.horizontal, 8)
            .frame(height: 32)
            .frame(maxWidth: 200)
            .background(Color.surface, in: RoundedRectangle(cornerRadius: Radius.lg))
            .overlay(RoundedRectangle(cornerRadius: Radius.lg).stroke(Color.borderSubtle, lineWidth: 1))
        }
        .accessibilityLabel("연결할 목표")
    }
}

/// 회색 알약 (시각 · 걸린 시간)
private struct RecordLogChip: View {
    let icon: String
    let text: String

    var body: some View {
        HStack(spacing: 4) {
            NSymbol(icon, size: 10, weight: .semibold)
            Text(text).lineLimit(1)
        }
        .font(.pt(12, .semibold))
        .foregroundStyle(Color.ink2)
        .padding(.horizontal, 8)
        .frame(height: 24)
        .background(Color.subtle, in: Capsule())
    }
}

// MARK: - 기록한 한 줄 (완료 화면)

private struct RecordLoggedActivityRow: View {
    let activity: ActualActivity
    let today: String

    var body: some View {
        let day = String(activity.startAt.prefix(10))
        let dayLabel = day == today ? "오늘" : KST.formatMonthDay(day)
        HStack(spacing: 12) {
            Text("\(dayLabel) \(KST.hm(activity.startAt))–\(KST.hm(activity.endAt))")
                .font(.ptMono(13))
                .foregroundStyle(Color.ink3)
                .lineLimit(1)
                .fixedSize()
            Text(activity.title)
                .font(.pt(14, .medium))
                .foregroundStyle(Color.ink)
                .lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .leading)
            if let gid = activity.goalCategoryId {
                Button {
                    // 웹 Link /goal/{id} — 시트를 닫고 목표 상세로
                    TA.track("task_add_log.done_link_click", ["destination": "goal"])
                    Analytics.sheetCloseHint = "next"
                    AppRouter.shared.taskAdd = nil
                    AppRouter.shared.openGoal(gid)
                } label: {
                    Text(activity.goalCategoryName ?? "")
                        .font(.pt(12))
                        .foregroundStyle(Color.ink3)
                        .lineLimit(1)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }
}

// MARK: - 미리보기

#Preview("적기") {
    ScrollView {
        LogDoneView(goals: RecordSampleData.goals).padding(16)
    }
    .canvasBackground()
}

#Preview("확인") {
    let rows = [
        RecordLogRow(clientKey: "a", text: "오전 10시 주간 회의 1시간", title: "주간 회의", date: nil,
                     startTime: "10:00", endTime: "11:00", minutes: 60, link: "goal:7"),
        RecordLogRow(clientKey: "b", text: "어제 코드 리뷰 30분", title: "코드 리뷰", date: nil,
                     startTime: nil, endTime: nil, minutes: 30, link: ""),
        RecordLogRow(clientKey: "c", text: "블로그 글 쓰기", title: "블로그 글 쓰기", date: nil,
                     startTime: nil, endTime: nil, minutes: nil, link: "temp:t1"),
    ]
    return ScrollView {
        LogDoneView(goals: RecordSampleData.goals, previewRows: rows,
                    previewTemps: [TempGoalInfo(tempKey: "t1", name: "블로그", emoji: "✍️")])
            .padding(16)
    }
    .canvasBackground()
}

#Preview("완료") {
    let today = KST.today()
    let json = """
    {"activities": [
      {"activityId": 1, "title": "주간 회의", "startAt": "\(today)T10:00:00", "endAt": "\(today)T11:00:00",
       "durationMinutes": 60, "goalCategoryId": 7, "goalCategoryName": "나를 업무"},
      {"activityId": 2, "title": "코드 리뷰", "startAt": "\(KST.addDays(today, -1))T16:00:00",
       "endAt": "\(KST.addDays(today, -1))T16:30:00", "durationMinutes": 30}
    ], "tempGoals": []}
    """
    let done = try! JSONDecoder().decode(RecordActivityLogResponse.self, from: Data(json.utf8))
    return ScrollView {
        LogDoneView(goals: RecordSampleData.goals, previewRows: nil, previewDone: done).padding(16)
    }
    .canvasBackground()
}
