// Features/TaskAdd/BrainDumpStepInput.swift
//
// 웹 원본: features/brain-dump/ui/StepInput.tsx
//
// 1단계 · Task 입력 (Brain dump)
//  - 한 줄에 Task 하나. 키보드의 return 을 누를 때마다 목록에 추가돼요.
//  - 여러 줄을 붙여 넣으면 줄마다 나눠서 추가해요.
//  - Free 한도: 목록 개수가 남은 개수에 닿으면 입력창을 막고 안내해요.
//
// iOS 에서 바꾼 점
//  - 웹은 input 의 Enter 키를 잡지만, iOS 는 여러 줄 입력칸(axis: .vertical)에 줄바꿈("\n")이 들어오는 순간을
//    "Enter" 로 봐요. 그래서 붙여 넣기(여러 줄)와 return 키를 같은 코드로 처리할 수 있어요.
//    (한글 조합 중 Enter 문제는 iOS 키보드가 알아서 처리해서 따로 막을 필요가 없어요)
//  - 날짜 칩 위에 겹친 투명 date input → 칩을 누르면 달력 시트가 떠요.
//  - 시간 input → 시각 고르기(DatePicker). 비어 있을 땐 "--:--" 를 눌러 채워요.
//  - 걸리는 시간 select → 칩을 누르면 메뉴가 떠요.

import SwiftUI

@MainActor
struct BrainDumpStepInput: View {
    @Binding var lines: [BrainDumpLine]
    let quota: TaskQuota?
    let quotaLoading: Bool
    let today: String
    let loading: Bool
    let onNext: () -> Void
    /// "Pro 알아보기" (멤버십 화면으로)
    let onPro: () -> Void

    @State private var draft = ""
    @FocusState private var focused: Bool

    // 다른 파일(TaskAddSheet)에서 만들기 때문에 init 을 직접 적어요
    // (private 저장 속성(@FocusState)이 있으면 자동 memberwise init 이 private 이 될 수 있어요)
    init(lines: Binding<[BrainDumpLine]>, quota: TaskQuota?, quotaLoading: Bool, today: String, loading: Bool,
         onNext: @escaping () -> Void, onPro: @escaping () -> Void) {
        _lines = lines
        self.quota = quota
        self.quotaLoading = quotaLoading
        self.today = today
        self.loading = loading
        self.onNext = onNext
        self.onPro = onPro
    }

    // MARK: 한도 계산 (웹과 같아요)

    /// Free 플랜이면 남은 개수, 아니면 한 번에 20개
    private var limit: Int {
        if let q = quota, !q.premium, let r = q.remaining { return min(r, BrainDumpConst.maxLines) }
        return BrainDumpConst.maxLines
    }
    private var full: Bool { lines.count >= limit }
    private var freeLimited: Bool {
        guard let q = quota, !q.premium, let r = q.remaining else { return false }
        return lines.count >= r
    }
    /// 앰플리튜드 — 무료 남은 개수 (Pro 면 nil)
    private var quotaRemaining: Int? {
        guard let q = quota, !q.premium else { return nil }
        return q.remaining
    }
    /// limit_hit 의 이유 — 무료 남은 개수가 20보다 적으면 free_quota, 아니면 한 번에 20개(max_lines)
    private var limitReason: String {
        if let r = quotaRemaining, r < BrainDumpConst.maxLines { return "free_quota" }
        return "max_lines"
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            header

            if !lines.isEmpty {
                VStack(spacing: 8) {
                    ForEach(Array(lines.enumerated()), id: \.element.clientKey) { i, l in
                        BrainDumpLineRow(
                            index: i,
                            line: l,
                            today: today,
                            disabled: loading,
                            onChange: { patch in update(l.clientKey, patch) },
                            onRemove: { remove(l.clientKey) },
                            onTrack: { event, props in
                                var p = props
                                p["list_position"] = i
                                if event == "task_add_input.line_cleared" { p["line_count"] = lines.count }
                                TA.track(event, p)
                            }
                        )
                    }
                }
                .padding(.top, 20)
            }

            inputArea.padding(.top, 16)

            footer.padding(.top, 24)
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card()
        .onChange(of: draft) { _, value in handleDraft(value) }
        .onAppear {
            // 시트를 열자마자 오늘 남은 개수가 0 이라 입력칸이 잠겨 있음
            if lines.isEmpty && full && !quotaLoading { trackLimit(dropped: 0) }
        }
        .onChange(of: quotaLoading) { _, loading in
            if !loading && lines.isEmpty && full { trackLimit(dropped: 0) }
        }
    }

    // MARK: 위쪽 안내 + 남은 개수

    @ViewBuilder
    private var header: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("머릿속에 있는 일을 한 줄씩 적어 주세요")
                .font(.pt(16, .semibold))
                .foregroundStyle(Color.ink)
            Text("Enter를 누를 때마다 추가돼요. \"저녁 8시 정기 회의\", \"인강 듣기 3시간\"처럼 적으면 시각과 걸리는 시간을 바로 알아채요. 아래 칩으로 고쳐도 돼요.")
                .font(.pt(14))
                .foregroundStyle(Color.ink3)
                .fixedSize(horizontal: false, vertical: true)
            if let q = quota, !q.premium, let remaining = q.remaining {
                let text = freeLimited ? "오늘 추가할 수 있는 만큼 적었어요" : "오늘 \(remaining - lines.count)개 더 추가할 수 있어요"
                Text(text)
                    .font(.pt(12, .semibold))
                    .foregroundStyle(freeLimited ? Color.warningInk : Color.ink2)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 4)
                    .background(freeLimited ? Color.warningSoft : Color.subtle, in: Capsule())
            }
        }
    }

    // MARK: 입력창 (또는 한도 안내)

    @ViewBuilder
    private var inputArea: some View {
        if full {
            HStack(alignment: .top, spacing: 8) {
                NSymbol("lock.fill", size: 13)
                    .foregroundStyle(Color.ink3)
                    .padding(.top, 2)
                if freeLimited {
                    VStack(alignment: .leading, spacing: 4) {
                        Text((quota?.remaining == 0 ? "오늘은 더 추가할 수 없어요. " : "")
                             + "무료 플랜은 하루 \(quota?.dailyLimit ?? 2)개까지 추가할 수 있어요 ·")
                            .foregroundStyle(Color.ink2)
                        Button("Pro 알아보기") {
                            TA.track("task_add_input.pro_click", ["quota_remaining": quotaRemaining])
                            onPro()
                        }
                            .font(.pt(14, .semibold))
                            .foregroundStyle(Color.textBrand)
                            .buttonStyle(.plain)
                    }
                } else {
                    Text("한 번에 \(BrainDumpConst.maxLines)개까지 정리할 수 있어요.")
                        .foregroundStyle(Color.ink2)
                }
                Spacer(minLength: 0)
            }
            .font(.pt(14))
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .background(Color.subtle, in: RoundedRectangle(cornerRadius: Radius.xxl))
            .overlay(
                RoundedRectangle(cornerRadius: Radius.xxl)
                    .stroke(Color.borderSubtle, style: StrokeStyle(lineWidth: 1, dash: [4, 3]))
            )
        } else {
            HStack(spacing: 8) {
                Text("\(lines.count + 1)")
                    .font(.ptMono(14))
                    .foregroundStyle(Color.ink4)
                    .frame(width: 20, alignment: .trailing)
                TextField(lines.isEmpty ? "예) 저녁 8시 정기 회의 · 인강 듣기 3시간" : "다음 할 일 (비워 두고 Enter = 다음 단계)",
                          text: $draft, axis: .vertical)
                    .font(.pt(15))
                    .foregroundStyle(Color.ink)
                    .lineLimit(1...3)
                    .focused($focused)
                    .disabled(loading || quotaLoading)
                    .submitLabel(.return)
                    .accessibilityLabel("할 일 입력")
                    .frame(minHeight: 44)
                Button {
                    submitDraft()
                    focused = true
                } label: {
                    Image(systemName: "return")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundStyle(Color.ink3)
                        .frame(width: 32, height: 32)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .disabled(draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                .opacity(draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? 0.3 : 1)
                .accessibilityLabel("추가")
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 4)
            .background(Color.surface, in: RoundedRectangle(cornerRadius: Radius.xxl))
            .overlay(
                RoundedRectangle(cornerRadius: Radius.xxl)
                    .stroke(focused ? Color.borderFocus : Color.borderSubtle, lineWidth: focused ? 1.5 : 1)
            )
        }
    }

    // MARK: 아래 버튼

    private var footer: some View {
        VStack(alignment: .trailing, spacing: 10) {
            if loading {
                HStack(spacing: 8) {
                    ProgressView().controlSize(.small).tint(Color.ink3)
                    Text("AI가 목표와 연결하고 있어요…")
                        .font(.pt(14))
                        .foregroundStyle(Color.ink3)
                }
            }
            NButton("다음 · 목표 연결", variant: .ai, icon: "sparkles", loading: loading) {
                focused = false
                next(method: "button")
            }
            .disabled(lines.isEmpty || loading)
        }
        .frame(maxWidth: .infinity, alignment: .trailing)
    }

    // MARK: 동작

    /// 입력칸에 줄바꿈이 들어오면(= return 키 또는 여러 줄 붙여 넣기) 줄마다 나눠서 추가
    private func handleDraft(_ value: String) {
        guard value.contains("\n") else {
            if value.count > 200 { draft = String(value.prefix(200)) }
            return
        }
        let parts = value.components(separatedBy: .newlines)
        let texts = parts.map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
        draft = ""
        if texts.isEmpty {
            // 빈 칸에서 Enter = 다음 단계
            if !lines.isEmpty && !loading {
                focused = false
                next(method: "return_empty")
            }
            return
        }
        // 키보드 return 은 글 + 줄바꿈 하나, 붙여 넣기는 여러 줄
        add(texts, method: texts.count > 1 ? "paste" : "enter")
    }

    private func submitDraft() {
        add([draft], method: "button")
        draft = ""
    }

    private func next(method: String) {
        TA.track("task_add_input.next_click", [
            "line_count": lines.count,
            "empty_line_count": lines.filter { $0.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }.count,
            "dated_count": lines.filter { $0.date != nil }.count,
            "timed_count": lines.filter { $0.dateType != .DUE && $0.startTime != nil }.count,
            "duration_count": lines.filter { $0.minutes != nil }.count,
            "submit_method": method,
        ])
        onNext()
    }

    private func trackLimit(dropped: Int) {
        TA.track("task_add_input.limit_hit", [
            "limit_reason": limitReason, "quota_remaining": quotaRemaining,
            "line_count": lines.count, "dropped_count": dropped,
        ])
    }

    private func add(_ texts: [String], method: String) {
        let clean = texts.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty }
        guard !clean.isEmpty else { return }
        let room = max(limit - lines.count, 0)
        let accepted = Array(clean.prefix(room))
        let added = accepted.map { BrainDumpLine.make($0, today: today) }
        if !added.isEmpty {
            lines.append(contentsOf: added)
        }
        let dropped = clean.count - accepted.count
        if !added.isEmpty {
            TA.track("task_add_input.line_add", [
                "input_method": method, "added_count": added.count, "dropped_count": dropped,
                "line_count": lines.count,
                "has_date": added.contains { $0.date != nil },
                "has_parsed_time": added.contains { $0.startTime != nil },
                "has_parsed_duration": added.contains { $0.minutes != nil },
            ])
        }
        // 이번에 한도에 닿아 입력칸이 잠겼거나, 붙여 넣은 일부가 빠짐
        if dropped > 0 || lines.count >= limit { trackLimit(dropped: dropped) }
        if dropped > 0 {
            Toast.show("\(dropped)개는 한도를 넘어서 넣지 않았어요.")
        }
    }

    private func update(_ key: String, _ patch: (inout BrainDumpLine) -> Void) {
        guard let i = lines.firstIndex(where: { $0.clientKey == key }) else { return }
        var l = lines[i]
        patch(&l)
        lines[i] = l
    }

    private func remove(_ key: String) {
        let index = lines.firstIndex { $0.clientKey == key } ?? 0
        TA.track("task_add_input.line_remove", ["list_position": index, "line_count": lines.count - 1])
        lines.removeAll { $0.clientKey == key }
        focused = true
    }
}

// MARK: - 한 줄 (번호 · 글 · 날짜/시간/걸리는 시간 칩)

private struct BrainDumpLineRow: View {
    let index: Int
    let line: BrainDumpLine
    let today: String
    let disabled: Bool
    let onChange: ((inout BrainDumpLine) -> Void) -> Void
    let onRemove: () -> Void
    /// 앰플리튜드 (list_position 은 부모가 붙여요)
    let onTrack: (String, EventProps) -> Void

    @State private var showTime: Bool
    @State private var pickingDate = false

    init(index: Int, line: BrainDumpLine, today: String, disabled: Bool,
         onChange: @escaping ((inout BrainDumpLine) -> Void) -> Void, onRemove: @escaping () -> Void,
         onTrack: @escaping (String, EventProps) -> Void) {
        self.index = index
        self.line = line
        self.today = today
        self.disabled = disabled
        self.onChange = onChange
        self.onRemove = onRemove
        self.onTrack = onTrack
        // 시작 시각이 이미 있으면(문장에서 읽었으면) 시간 칸을 펼쳐 둬요
        _showTime = State(initialValue: line.startTime != nil)
    }

    private var tomorrow: String { KST.addDays(today, 1) }
    /// 오늘 · 내일이 아닌 날을 골랐는지
    private var customDate: String? {
        guard let d = line.date, d != today, d != tomorrow else { return nil }
        return d
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Text("\(index + 1)")
                    .font(.ptMono(14))
                    .foregroundStyle(Color.ink4)
                    .frame(width: 20, alignment: .trailing)
                TextField("", text: Binding(
                    get: { line.text },
                    set: { v in
                        // 이미 추가한 줄을 다 지워 빈 줄이 됨 → 다음 단계로 갈 때 말없이 빠져요
                        if !line.text.trimmingCharacters(in: .whitespaces).isEmpty && v.trimmingCharacters(in: .whitespaces).isEmpty {
                            onTrack("task_add_input.line_cleared", [:])
                        }
                        onChange { $0.text = String(v.prefix(200)) }
                    }
                ))
                .font(.pt(15, .medium))
                .foregroundStyle(Color.ink)
                .disabled(disabled)
                .accessibilityLabel("\(index + 1)번 할 일")
                IconButton(systemName: "xmark", size: 14, color: .ink4, label: "\(index + 1)번 삭제", action: onRemove)
                    .disabled(disabled)
            }

            FlowLayout(spacing: 6) {
                Chip("오늘", selected: line.date == today, size: .sm) { pick(today, kind: "today") }
                Chip("내일", selected: line.date == tomorrow, size: .sm) { pick(tomorrow, kind: "tomorrow") }
                Chip(customDate.map { KST.formatMonthDay($0) } ?? "날짜 선택", selected: customDate != nil, size: .sm) {
                    pickingDate = true
                }
                if line.date != nil {
                    Chip("~까지", selected: line.dateType == .DUE, size: .sm) {
                        onTrack("task_add_input.chip_click", ["chip_kind": "due", "checked": line.dateType != .DUE])
                        onChange { l in
                            l.dateType = l.dateType == .DUE ? .ON : .DUE
                            l.startTime = nil
                            l.endTime = nil
                        }
                    }
                }
                if line.dateType != .DUE {
                    Chip("시간", selected: showTime, size: .sm, icon: "clock") {
                        onTrack("task_add_input.chip_click", ["chip_kind": "time", "checked": !showTime])
                        if showTime { onChange { $0.startTime = nil; $0.endTime = nil } }
                        showTime.toggle()
                    }
                }
                if showTime && line.dateType != .DUE {
                    HStack(spacing: 4) {
                        BrainDumpTimeField(value: startBinding, label: "\(index + 1)번 시작 시각", defaultValue: "09:00")
                        Text("~").font(.pt(12)).foregroundStyle(Color.ink3)
                        BrainDumpTimeField(value: endBinding, label: "\(index + 1)번 종료 시각",
                                           defaultValue: endDefault)
                    }
                }
                BrainDumpDurationChip(line: line, index: index, disabled: disabled) { m in
                    onTrack("task_add_input.duration_select", ["duration_minutes": m])
                    onChange { $0.minutes = m }
                }
            }
            .disabled(disabled)
            .padding(.leading, 28)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(Color.surface, in: RoundedRectangle(cornerRadius: Radius.xxl))
        .overlay(RoundedRectangle(cornerRadius: Radius.xxl).stroke(Color.line, lineWidth: 1))
        .sheet(isPresented: $pickingDate) {
            BrainDumpDatePickSheet(initial: line.date ?? today, minDate: today) { d in
                onTrack("task_add_input.chip_click", ["chip_kind": "date", "checked": true])
                onChange { $0.date = d }
            }
        }
    }

    private var startBinding: Binding<String?> {
        Binding(get: { line.startTime }, set: { v in onChange { $0.startTime = v } })
    }

    private var endBinding: Binding<String?> {
        Binding(get: { line.endTime }, set: { v in onChange { $0.endTime = v } })
    }

    /// 종료 시각을 처음 채울 땐 시작 + 1시간
    private var endDefault: String {
        guard let s = line.startTime else { return "10:00" }
        return KST.minutesToTime(min(KST.timeToMinutes(s) + 60, 23 * 60 + 50))
    }

    /// 같은 칩을 다시 누르면 날짜를 지워요
    private func pick(_ d: String, kind: String) {
        onTrack("task_add_input.chip_click", ["chip_kind": kind, "checked": line.date != d])
        onChange { $0.date = ($0.date == d) ? nil : d }
    }
}

// MARK: - 걸리는 시간 칩

/// 시작 시각을 몰라도 "3시간 걸리는 일"이면 자동 배치가 3시간짜리 빈칸을 찾아 넣어요.
/// 시작·종료를 둘 다 고르면 그 차이가 소요 시간이라 여기서는 보여주기만 해요.
private struct BrainDumpDurationChip: View {
    let line: BrainDumpLine
    let index: Int
    let disabled: Bool
    let onChange: (Int?) -> Void

    private var fixed: Int? {
        guard line.dateType != .DUE, let s = line.startTime, let e = line.endTime else { return nil }
        return BrainDumpConst.span(s, e)
    }

    private var options: [Int] {
        var set = Set(BrainDumpConst.durations)
        if let m = line.minutes { set.insert(m) }
        return set.sorted()
    }

    var body: some View {
        let value = fixed ?? line.minutes
        let on = value != nil
        Menu {
            Button("AI가 추정") { onChange(nil) }
            ForEach(options, id: \.self) { m in
                Button(KST.formatDuration(m)) { onChange(m) }
            }
        } label: {
            HStack(spacing: 4) {
                Image(systemName: "timer").font(.system(size: 11, weight: .semibold))
                Text(value.map { KST.formatDuration($0) } ?? "걸리는 시간")
            }
            .font(.pt(12, .medium))
            .foregroundStyle(on ? Color.white : Color.ink2)
            .padding(.horizontal, 10)
            .frame(height: 28)
            .background(on ? Color.brand : Color.surface, in: Capsule())
            .overlay(Capsule().stroke(on ? Color.brand : Color.borderSubtle, lineWidth: 1))
            .opacity(fixed != nil ? 0.8 : 1)
        }
        .disabled(disabled || fixed != nil)
        .accessibilityLabel("\(index + 1)번 걸리는 시간")
        .accessibilityHint(fixed != nil ? "시작·종료 시각으로 정해져요" : "걸리는 시간 (비워 두면 AI가 추정)")
    }
}

// MARK: - 시각 입력 (웹 <input type="time">) — 2단계 변경 시트에서도 써요

/// "HH:mm" 문자열(없으면 nil)을 고르는 칸. 비어 있으면 "--:--" 를 눌러 채우고, x 로 지워요.
struct BrainDumpTimeField: View {
    @Binding var value: String?
    var label: String
    /// 처음 누를 때 채울 시각
    var defaultValue: String = "09:00"
    /// 칸을 넓게 (변경 시트)
    var wide = false

    /// "HH:mm" ↔ Date (한국 시간 기준 아무 날의 그 시각)
    private var dateBinding: Binding<Date> {
        Binding(
            get: { KST.date(fromDateTime: "2000-01-01T\(value ?? defaultValue):00") },
            set: { d in value = String(KST.dateTime(from: d).dropFirst(11).prefix(5)) }
        )
    }

    var body: some View {
        HStack(spacing: 2) {
            if value != nil {
                DatePicker(label, selection: dateBinding, displayedComponents: .hourAndMinute)
                    .labelsHidden()
                    .environment(\.timeZone, KST.timeZone)
                    .accessibilityLabel(label)
                Button {
                    value = nil
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 14))
                        .foregroundStyle(Color.ink4)
                        .frame(width: 24, height: 28)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("\(label) 지우기")
            } else {
                Button {
                    value = defaultValue
                } label: {
                    Text("--:--")
                        .font(.ptMono(wide ? 15 : 13))
                        .foregroundStyle(Color.ink3)
                        .padding(.horizontal, 10)
                        .frame(maxWidth: wide ? .infinity : nil, alignment: .leading)
                        .frame(height: wide ? 44 : 28)
                        .background(Color.surface, in: RoundedRectangle(cornerRadius: wide ? Radius.xl : Radius.lg))
                        .overlay(RoundedRectangle(cornerRadius: wide ? Radius.xl : Radius.lg).stroke(Color.borderSubtle, lineWidth: 1))
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(label)
            }
        }
    }
}

// MARK: - 날짜 고르기 시트 (웹 <input type="date" min={today}>)

private struct BrainDumpDatePickSheet: View {
    let minDate: String
    let onPick: (String) -> Void
    @State private var selected: Date
    @Environment(\.dismiss) private var dismiss

    init(initial: String, minDate: String, onPick: @escaping (String) -> Void) {
        self.minDate = minDate
        self.onPick = onPick
        _selected = State(initialValue: KST.date(from: max(initial, minDate)))
    }

    var body: some View {
        SheetScaffold(title: "날짜 선택") {
            DatePicker("날짜", selection: $selected, in: KST.date(from: minDate)..., displayedComponents: .date)
                .datePickerStyle(.graphical)
                .environment(\.timeZone, KST.timeZone)
                .tint(Color.brand)
        } footer: {
            NButton("취소", variant: .secondary) { dismiss() }
            NButton("확인") {
                onPick(KST.ymd(from: selected))
                dismiss()
            }
        }
    }
}

#Preview {
    struct Demo: View {
        @State private var lines: [BrainDumpLine] = [
            BrainDumpLine.make("저녁 8시 정기 회의", today: KST.today()),
            BrainDumpLine.make("인강 듣기 3시간", today: KST.today()),
        ]
        var body: some View {
            ScrollView {
                BrainDumpStepInput(
                    lines: $lines,
                    quota: TaskQuota(premium: false, dailyLimit: 2, usedToday: 0, remaining: 2),
                    quotaLoading: false,
                    today: KST.today(),
                    loading: false,
                    onNext: {},
                    onPro: {}
                )
                .padding(16)
            }
            .canvasBackground()
        }
    }
    return Demo()
}
