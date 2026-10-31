// Features/Calendar/ScheduleAiSheet.swift
//
// 캘린더 "AI로 일정 조정" — 웹 features/timetable/ui/ScheduleAiBar.tsx.
// 여러 목표의 루틴 · 일정을 한 문장으로 한꺼번에 옮겨요.
//  1) 문장을 보내면 AI 가 "옮길 항목 목록"을 만들어요 (보고 있는 주/날의 일정 + 모든 루틴 기준) — 이때는 아무것도 안 바뀌어요
//  2) 전/후 · 이유를 보고 항목마다 시간 · 날짜 · 요일 · 길이를 고치거나 빼고 [적용] (2026-10, 웹 오른쪽 패널과 같게)
// 루틴 변경은 "앞으로 계속", Task 변경은 그날만 바뀌어요.
// 루틴 폼의 "캘린더에서 AI로 정리하기"로 열면 문장이 채워져 있어요 (router.openCalendarAi).

import SwiftUI
import UIKit

// MARK: - API (POST /timetable/ai-edit/preview · /apply)

struct ScheduleEditOp: Codable, Hashable {
    var type: String
    var targetId: Int
    var days: [String]?
    var startTime: String?
    var durationMinutes: Int?
    var date: String?
    /// AI 가 이렇게 옮기는 이유 (2026-10) — 항목 아래에 보여줘요
    var reason: String? = nil
}

struct ScheduleEditChange: Codable, Hashable {
    var op: ScheduleEditOp
    /// ROUTINE · TASK
    var kind: String
    var target: String
    var goal: String?
    var colorCode: String?
    var before: String
    var after: String
}

struct ScheduleEditPreview: Codable, Hashable {
    var summary: String
    var changes: [ScheduleEditChange]
    /// 지금 겹쳐 있는 루틴 — "화 16:30–17:00 '기출 풀기' · 'SQL 문제'"
    var overlaps: [String]
}

struct ScheduleEditApplyResult: Codable, Hashable {
    var applied: Int
    var skipped: [String]
}

enum ScheduleEditAPI {
    private struct PreviewBody: Encodable { let instruction: String; let startDate: String; let endDate: String }
    private struct ApplyBody: Encodable { let ops: [ScheduleEditOp]; let startDate: String; let endDate: String }

    static func preview(_ instruction: String, start: String, end: String) async throws -> ScheduleEditPreview? {
        try await API.request("POST", "/v1/timetable/ai-edit/preview",
                              encodable: PreviewBody(instruction: instruction, startDate: start, endDate: end))
    }

    static func apply(_ ops: [ScheduleEditOp], start: String, end: String) async throws -> ScheduleEditApplyResult? {
        try await API.request("POST", "/v1/timetable/ai-edit/apply",
                              encodable: ApplyBody(ops: ops, startDate: start, endDate: end))
    }
}

/// 시트를 여는 요청 (router.scheduleAi)
struct ScheduleAiRequest: Identifiable, Equatable {
    let id = UUID()
    var prompt: String?
}

// MARK: - 시트

struct ScheduleAiSheet: View {
    /// 보고 있는 기간 (최대 7일)
    let start: String
    let end: String

    @State private var text: String
    @State private var asking = false
    @State private var applying = false
    @State private var preview: ScheduleEditPreview?
    @State private var picked: [Bool] = []
    /// 항목마다 고친 값 (처음엔 AI 제안 그대로)
    @State private var ops: [ScheduleEditOp] = []
    @FocusState private var focused: Bool
    @Environment(\.dismiss) private var dismiss
    /// 루틴 폼 겹침 안내에서 채워 온 문장 (calendar_ai.submit 의 is_prefilled)
    private let prefilled: String?
    /// 적용해서 닫히면 "saved" (닫기 이벤트 안 보냄)
    @State private var outcome: String?

    static let maxLength = 300
    static let examples = ["겹치는 루틴 정리해줘", "이번 주 운동 루틴은 저녁 8시로 옮겨줘", "내일 할 일을 오후로 몰아줘"]

    init(start: String, end: String, prompt: String? = nil, preview: ScheduleEditPreview? = nil) {
        self.start = start
        self.end = end
        _text = State(initialValue: String((prompt ?? "").prefix(Self.maxLength)))
        prefilled = prompt.map { String($0.prefix(Self.maxLength)) }
        _preview = State(initialValue: preview)
        _picked = State(initialValue: preview?.changes.map { _ in true } ?? [])
        _ops = State(initialValue: preview?.changes.map(\.op) ?? [])
    }

    private var chosen: [ScheduleEditOp] {
        ops.enumerated().filter { picked.indices.contains($0.offset) && picked[$0.offset] }.map(\.element)
    }

    var body: some View {
        SheetScaffold(title: preview == nil ? "AI로 일정 옮기기" : "이렇게 옮길까요?",
                      description: preview?.summary ?? "이미 있는 루틴 · 일정을 말로 한꺼번에 옮겨요. 말한 시간이 가장 먼저예요. 바꾸기 전에 꼭 확인을 거쳐요.",
                      dismissible: !applying, detents: [.large]) {
            if let preview {
                previewList(preview)
            } else {
                composer
            }
        } footer: {
            if let preview {
                if preview.changes.isEmpty {
                    NButton("다시 적기", variant: .secondary, fullWidth: true) { rewrite() }
                } else {
                    NButton("다시 적기", variant: .secondary) { rewrite() }
                        .disabled(applying)
                    NButton("\(chosen.count)개 적용", loading: applying, fullWidth: true) { Task { await apply() } }
                        .disabled(chosen.isEmpty)
                }
            } else {
                NButton("조정하기", variant: .ai, icon: "sparkles", loading: asking, fullWidth: true) {
                    let v = text.trimmingCharacters(in: .whitespacesAndNewlines)
                    if !v.isEmpty && !asking {
                        CAL.track("calendar_ai.submit", [
                            "view_mode": start == end ? "day" : "week", "text_length": v.count,
                            "is_prefilled": prefilled != nil && text == prefilled, "submit_method": "button",
                        ])
                    }
                    Task { await ask(text) }
                }
                .disabled(text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
        }
        .onDisappear { trackClose() }
    }

    private var composer: some View {
        VStack(alignment: .leading, spacing: 12) {
            TextField("예) 겹치는 루틴 정리해줘 · 운동 루틴은 전부 저녁 8시로", text: $text, axis: .vertical)
                .lineLimit(3...6)
                .inputStyle(minHeight: 96)
                .focused($focused)
                .onChange(of: text) { if text.count > Self.maxLength { text = String(text.prefix(Self.maxLength)) } }
                .accessibilityLabel("AI로 일정 옮기기")
            FlowLayout(spacing: 6) {
                ForEach(Array(Self.examples.enumerated()), id: \.element) { i, ex in
                    Button {
                        CAL.track("calendar_ai.example_click", ["example_index": i])
                        text = ex
                    } label: {
                        Text(ex)
                            .font(.pt(12))
                            .foregroundStyle(Color.ink2)
                            .padding(.horizontal, 10)
                            .frame(height: 30)
                            .overlay(Capsule().stroke(Color.line, lineWidth: 1))
                            .contentShape(Capsule())
                    }
                    .buttonStyle(.plain)
                }
            }
            Text("\(KST.formatRange(start, end)) 일정과 모든 루틴을 보고 바꿀 항목을 골라 줘요. 루틴은 앞으로 계속, 하루짜리 일정은 그날만 바뀌어요.")
                .font(.pt(12))
                .foregroundStyle(Color.ink3)
        }
        .onAppear { if text.isEmpty { focused = true } }
    }

    @ViewBuilder
    private func previewList(_ p: ScheduleEditPreview) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            if !p.changes.isEmpty {
                Text("아직 아무것도 바뀌지 않았어요. 시간을 고치거나 뺄 항목은 여기서 바꾸고 적용해 주세요.")
                    .font(.pt(13))
                    .foregroundStyle(Color.ink3)
                ForEach(Array(p.changes.enumerated()), id: \.offset) { i, c in
                    VStack(alignment: .leading, spacing: 0) {
                        changeRow(c, on: picked.indices.contains(i) && picked[i]) {
                            guard picked.indices.contains(i) else { return }
                            UISelectionFeedbackGenerator().selectionChanged()
                            CAL.track("calendar_ai.item_toggle", ["change_kind": c.kind, "checked": !picked[i]])
                            picked[i].toggle()
                        }
                        if ops.indices.contains(i), picked.indices.contains(i), picked[i] {
                            opEditor(i, kind: c.kind)
                        }
                    }
                }
            }
            if !p.overlaps.isEmpty {
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 6) {
                        Image(systemName: "exclamationmark.triangle.fill").font(.system(size: 13)).foregroundStyle(Color.warning)
                        Text("지금 겹쳐 있는 루틴 \(p.overlaps.count)").font(.pt(13, .semibold)).foregroundStyle(Color.ink)
                    }
                    ForEach(p.overlaps, id: \.self) { o in
                        Text(o).font(.pt(13)).foregroundStyle(Color.ink2)
                    }
                    if p.changes.isEmpty {
                        Button {
                            CAL.track("calendar_ai.overlap_fix_click", ["overlap_count": p.overlaps.count])
                            Task { await ask("겹치는 루틴 정리해줘") }
                        } label: {
                            Text("겹치는 루틴 정리해 달라고 하기").font(.pt(12, .semibold)).foregroundStyle(Color.textBrand)
                                .frame(minHeight: 28)
                        }
                        .buttonStyle(.plain)
                        .disabled(asking)
                    }
                }
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.warningSoft, in: RoundedRectangle(cornerRadius: 12))
            }
        }
    }

    private func changeRow(_ c: ScheduleEditChange, on: Bool, toggle: @escaping () -> Void) -> some View {
        Button(action: toggle) {
            HStack(alignment: .top, spacing: 10) {
                NSymbol(on ? "checkmark.square.fill" : "square", size: 18)
                    .foregroundStyle(on ? Color.ink : Color.ink4)
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 6) {
                        if c.kind == "ROUTINE" {
                            NSymbol("repeat", size: 11, weight: .bold).foregroundStyle(Color.textBrand)
                        }
                        Text(c.target).font(.pt(14, .semibold)).foregroundStyle(Color.ink).lineLimit(2)
                    }
                    if let goal = c.goal {
                        HStack(spacing: 4) {
                            Circle().fill(Color.goal(c.colorCode)).frame(width: 7, height: 7)
                            Text(goal).font(.pt(12)).foregroundStyle(Color.ink3)
                        }
                    }
                    Text(c.before).font(.pt(13)).foregroundStyle(Color.ink3).strikethrough()
                    Text(c.after).font(.pt(14)).foregroundStyle(Color.ink)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(12)
            .background(on ? Color.surface : Color.bgSunken, in: RoundedRectangle(cornerRadius: 16))
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(on ? Color.actionPrimary : Color.borderSubtle, lineWidth: 1))
            .opacity(on ? 1 : 0.6)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(on ? .isSelected : [])
    }

    // MARK: 항목 고치기 (2026-10)

    private static let dayCodes = ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"]
    private static let dayLabels = ["월", "화", "수", "목", "금", "토", "일"]
    private static let lengths = [10, 15, 20, 30, 40, 45, 60, 90, 120, 150, 180, 240]

    private func setOp(_ i: Int, field: String, kind: String, _ change: (inout ScheduleEditOp) -> Void) {
        guard ops.indices.contains(i) else { return }
        CAL.track("calendar_ai.item_edit", ["change_kind": kind, "edit_field": field])
        change(&ops[i])
    }

    /// "HH:mm(:ss)" ↔ Date (오늘 날짜에 시각만)
    private func timeBinding(_ i: Int, kind: String) -> Binding<Date> {
        Binding(get: {
            let t = ops.indices.contains(i) ? (ops[i].startTime ?? "09:00") : "09:00"
            let p = t.split(separator: ":").compactMap { Int($0) }
            return Calendar.current.date(bySettingHour: p.first ?? 9, minute: p.count > 1 ? p[1] : 0, second: 0, of: Date()) ?? Date()
        }, set: { d in
            let c = Calendar.current.dateComponents([.hour, .minute], from: d)
            let m = ((c.minute ?? 0) / 10) * 10
            setOp(i, field: "start", kind: kind) { $0.startTime = String(format: "%02d:%02d", c.hour ?? 0, m) }
        })
    }

    private func dateBinding(_ i: Int, kind: String) -> Binding<Date> {
        Binding(get: {
            guard ops.indices.contains(i) else { return Date() }
            return KST.date(from: ops[i].date ?? start)
        }, set: { d in
            setOp(i, field: "date", kind: kind) { $0.date = KST.ymd(from: d) }
        })
    }

    @ViewBuilder
    private func opEditor(_ i: Int, kind: String) -> some View {
        let o = ops[i]
        VStack(alignment: .leading, spacing: 8) {
            if let r = o.reason, !r.isEmpty {
                HStack(alignment: .top, spacing: 6) {
                    NSymbol("sparkles", size: 11).foregroundStyle(Color.textBrand).padding(.top, 2)
                    Text(r).font(.pt(12)).foregroundStyle(Color.ink2).fixedSize(horizontal: false, vertical: true)
                }
            }
            if o.type == "MOVE_TASK" {
                DatePicker("날짜", selection: dateBinding(i, kind: kind), in: Date()..., displayedComponents: .date)
                    .font(.pt(13)).foregroundStyle(Color.ink3)
            } else if let days = o.days {
                HStack(spacing: 4) {
                    ForEach(Array(Self.dayCodes.enumerated()), id: \.offset) { k, code in
                        DayChip(Self.dayLabels[k], selected: days.contains(code)) {
                            let next = days.contains(code) ? days.filter { $0 != code } : days + [code]
                            if !next.isEmpty { setOp(i, field: "days", kind: kind) { $0.days = next } }
                        }
                        .scaleEffect(0.8)
                        .frame(width: 36)
                    }
                }
            }
            HStack(spacing: 12) {
                DatePicker("시작", selection: timeBinding(i, kind: kind), displayedComponents: .hourAndMinute)
                    .font(.pt(13)).foregroundStyle(Color.ink3)
                    .disabled(o.type == "MOVE_ROUTINE" && o.startTime == nil)
                Menu {
                    Button("그대로") { setOp(i, field: "duration", kind: kind) { $0.durationMinutes = nil } }
                    ForEach(Self.lengths, id: \.self) { m in
                        Button(KST.formatDuration(m)) { setOp(i, field: "duration", kind: kind) { $0.durationMinutes = m } }
                    }
                } label: {
                    HStack(spacing: 4) {
                        Text("길이 " + (o.durationMinutes.map { KST.formatDuration($0) } ?? "그대로"))
                        NSymbol("chevron.down", size: 10)
                    }
                    .font(.pt(13)).foregroundStyle(Color.ink2)
                }
            }
        }
        .padding(.horizontal, 12)
        .padding(.top, 8)
        .padding(.bottom, 4)
    }

    // MARK: 동작

    /// '다시 적기' — 미리보기만 닫고 적은 문장은 남겨요
    private func rewrite() {
        CAL.track("calendar_ai.cancel", ["close_method": "rewrite", "change_count": preview?.changes.count ?? 0])
        preview = nil
    }

    /// 시트가 닫힐 때 — 미리보기가 떠 있었으면 cancel, 적던 중이면 close
    private func trackClose() {
        guard let m = GD.closeMethod(outcome) else { return }
        if let p = preview {
            CAL.track("calendar_ai.cancel", ["close_method": m, "change_count": p.changes.count])
        } else {
            CAL.track("calendar_ai.close", ["close_method": m, "text_length": text.trimmingCharacters(in: .whitespacesAndNewlines).count])
        }
    }

    private func ask(_ value: String) async {
        let v = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !v.isEmpty, !asking else { return }
        focused = false
        asking = true
        defer { asking = false }
        do {
            if let p = try await ScheduleEditAPI.preview(v, start: start, end: end) {
                CAL.track("calendar_ai.preview_result", ["result": "success", "change_count": p.changes.count,
                                                         "overlap_count": p.overlaps.count])
                text = v
                preview = p
                picked = p.changes.map { _ in true }
                ops = p.changes.map(\.op)
            } else {
                // [UX] 빈 응답이면 화면에 아무 변화가 없어요
                CAL.track("calendar_ai.preview_result", ["result": "fail", "error_status": 0])
            }
        } catch {
            CAL.track("calendar_ai.preview_result", ["result": "fail", "error_status": CAL.status(error)])
            Toast.error(errorMessage(error, fallback: "AI가 잠시 응답하지 않아요. 잠시 후 다시 시도해주세요."))
        }
    }

    private func apply() async {
        let selected = chosen
        guard !selected.isEmpty, !applying else { return }
        let edited = ops.enumerated().filter { e in
            guard let p = preview, p.changes.indices.contains(e.offset) else { return false }
            return p.changes[e.offset].op != e.element && picked.indices.contains(e.offset) && picked[e.offset]
        }.count
        CAL.track("calendar_ai.apply_click", ["selected_count": selected.count, "change_count": preview?.changes.count ?? 0,
                                              "edited_count": edited])
        applying = true
        let r = await Mutation.run(invalidate: [.timetable, .goals, .home, .record]) {
            try await ScheduleEditAPI.apply(selected, start: start, end: end)
        }
        applying = false
        guard let result = r else {
            CAL.track("calendar_ai.apply_result", CAL.result(false))
            return
        }
        let applied = result?.applied ?? 0
        let skipped = result?.skipped ?? []
        CAL.track("calendar_ai.apply_result", ["result": "success", "applied_count": applied, "skipped_count": skipped.count])
        if applied > 0 {
            UINotificationFeedbackGenerator().notificationOccurred(.success)
            Toast.success("\(applied)개를 옮겼어요." + (skipped.isEmpty ? "" : " \(skipped.count)개는 못 옮겼어요."))
            outcome = "saved"
            dismiss()
        } else {
            Toast.error(skipped.first ?? "옮긴 일정이 없어요.")
        }
    }
}

#if DEBUG
private let samplePreview = ScheduleEditPreview(
    summary: "화·목 16시에 '기출 풀기'와 겹치던 'SQL 문제 풀기'를 19:00으로 옮길게요.",
    changes: [
        ScheduleEditChange(op: ScheduleEditOp(type: "MOVE_ROUTINE", targetId: 82, startTime: "19:00"), kind: "ROUTINE",
                           target: "루틴 'SQL 문제 풀기'", goal: "SQLD 합격", colorCode: "12B76A",
                           before: "화·목 16:30–17:30", after: "화·목 19:00–20:00 · 앞으로 계속"),
        ScheduleEditChange(op: ScheduleEditOp(type: "MOVE_TASK", targetId: 505, startTime: "19:30"), kind: "TASK",
                           target: "Task '습관 체크 API 연결'", goal: "나를 앱 v2 출시", colorCode: "3D5AFE",
                           before: "10/14(수) 16:00–18:00", after: "10/14(수) 19:30–21:30"),
    ],
    overlaps: ["화·목 16:30–17:00 '기출 풀기' · 'SQL 문제 풀기'"])

#Preview("AI로 일정 조정 — 적기") {
    Color.canvas
        .sheet(isPresented: .constant(true)) {
            ScheduleAiSheet(start: KST.startOfWeek(KST.today()), end: KST.addDays(KST.startOfWeek(KST.today()), 6),
                            prompt: "겹치는 루틴 정리해줘")
        }
}

#Preview("AI로 일정 조정 — 미리보기") {
    Color.canvas
        .sheet(isPresented: .constant(true)) {
            ScheduleAiSheet(start: KST.today(), end: KST.today(), preview: samplePreview)
        }
}
#endif
