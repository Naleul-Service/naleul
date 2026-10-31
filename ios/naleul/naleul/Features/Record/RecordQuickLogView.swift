// Features/Record/RecordQuickLogView.swift
//
// 웹 features/record/ui/RecordQuickLog.tsx
// 기록형 목표 상세 — "오늘 한 일" 한 줄 기록.
// Task 추가(Brain dump)처럼 문장만 적으면 시각·걸린 시간을 읽어서 바로 저장해요.
//  - "저녁 8시 나를 회의 1시간" → 20:00~21:00
//  - "운동 30분"                → 방금 끝낸 30분
//  - "어제 코드 리뷰 30분"       → 어제
// 목표가 이미 정해져 있으니 AI 없이 바로 저장해요 (그래서 빨라요).

import SwiftUI
import UIKit

struct RecordQuickLogView: View {
    let goal: GoalCategory
    /// 시트 안에서: 카드 테두리 없음 · 바로 키보드 · 저장하면 onDone
    var sheetMode = false
    var onDone: (() -> Void)? = nil
    /// 업무형 적기 시트에서 쓸 때만 앰플리튜드 work_compose.log_* 를 보내요
    var analytics = false
    /// 적던 글이 바뀔 때 (적기 시트가 닫힐 때 is_dirty 를 알려고)
    var onDraftChange: ((String) -> Void)? = nil
    /// 기록에 성공했을 때 (적기 시트의 added_count)
    var onLogged: (() -> Void)? = nil

    @State private var text = ""
    @State private var saving = false
    @FocusState private var focused: Bool

    private static let examples = ["주간 회의 1시간", "오후 2시 기획서 작성 2시간", "어제 코드 리뷰 30분"]

    init(goal: GoalCategory, sheetMode: Bool = false, onDone: (() -> Void)? = nil,
         analytics: Bool = false, onDraftChange: ((String) -> Void)? = nil, onLogged: (() -> Void)? = nil) {
        self.goal = goal
        self.sheetMode = sheetMode
        self.onDone = onDone
        self.analytics = analytics
        self.onDraftChange = onDraftChange
        self.onLogged = onLogged
    }

    private func track(_ event: String, _ props: EventProps = [:]) {
        guard analytics else { return }
        WK.track(event, goalId: goal.goalCategoryId, props)
    }

    private var value: String { text.trimmingCharacters(in: .whitespacesAndNewlines) }

    var body: some View {
        // 적는 동안 바로 읽은 결과 (웹 parseLine 과 같은 규칙)
        let preview: ParsedLine? = value.isEmpty ? nil : LineParser.parse(value, today: KST.today())
        if sheetMode {
            sheetBody(preview)
        } else {
            cardBody(preview)
        }
    }

    /// 시트 모양: 큰 입력칸 + 읽은 시각 + 예시 + 꽉 찬 [기록하기]
    private func sheetBody(_ preview: ParsedLine?) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            TextField("예) \(Self.examples[0])", text: $text, axis: .vertical)
                .font(.pt(17))
                .foregroundStyle(Color.ink)
                .tint(Color.brand)
                .lineLimit(1...3)
                .focused($focused)
                .submitLabel(.done)
                .onSubmit { Task { await submit(method: "keyboard_done") } }
                .disabled(saving)
                .padding(14)
                .background(Color.surface, in: RoundedRectangle(cornerRadius: Radius.xxl))
                .overlay(RoundedRectangle(cornerRadius: Radius.xxl).stroke(focused ? Color.borderFocus : Color.borderSubtle, lineWidth: focused ? 1.5 : 1))
                .accessibilityLabel("한 일")
                .onChange(of: text) {
                    if text.count > 200 { text = String(text.prefix(200)) }
                    onDraftChange?(text)
                }
            previewRow(preview)
                .frame(minHeight: 24, alignment: .leading)
            Text("시각이 없으면 방금 끝낸 일로 기록돼요. \"어제\"를 붙이면 어제로 남아요.")
                .font(.pt(12))
                .foregroundStyle(Color.ink3)
            NButton("기록하기", variant: .brand, size: .lg, loading: saving, fullWidth: true) {
                Task { await submit(method: "button") }
            }
            .disabled(value.isEmpty)
        }
        .onAppear {
            // 시트가 다 올라온 뒤 키보드
            Task { @MainActor in
                try? await Task.sleep(nanoseconds: 350_000_000)
                focused = true
            }
        }
    }

    private func cardBody(_ preview: ParsedLine?) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("오늘 한 일 기록하기").font(.pt(16, .semibold)).foregroundStyle(Color.ink)
            Text("한 줄로 적으면 시각·걸린 시간을 알아서 읽어요. 시각이 없으면 방금 끝낸 일로 기록돼요.")
                .font(.pt(13))
                .foregroundStyle(Color.ink3)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 4)

            HStack(spacing: 8) {
                TextField("예) \(Self.examples[0])", text: $text)
                    .font(.pt(15))
                    .foregroundStyle(Color.ink)
                    .tint(Color.brand)
                    .frame(height: 36)
                    .submitLabel(.done)
                    .onSubmit { Task { await submit(method: "keyboard_done") } }
                    .disabled(saving)
                    .accessibilityLabel("한 일")
                    .onChange(of: text) {
                        if text.count > 200 { text = String(text.prefix(200)) }
                    }
                NButton("기록", size: .sm, icon: "return", loading: saving) {
                    Task { await submit(method: "button") }
                }
                .disabled(value.isEmpty)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(Color.surface, in: RoundedRectangle(cornerRadius: Radius.xxl))
            .overlay(RoundedRectangle(cornerRadius: Radius.xxl).stroke(Color.borderSubtle, lineWidth: 1))
            .padding(.top, 16)

            previewRow(preview)
                .frame(minHeight: 24, alignment: .leading)
                .padding(.top, 8)
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card()
    }

    @ViewBuilder
    private func previewRow(_ p: ParsedLine?) -> some View {
        if let p, p.startTime != nil || p.minutes != nil {
            HStack(spacing: 6) {
                if let s = p.startTime {
                    RecordQuickChip(icon: "clock", text: s + (p.endTime.map { "–\($0)" } ?? ""))
                }
                if let m = p.minutes {
                    RecordQuickChip(icon: "timer", text: KST.formatDuration(m))
                }
            }
        } else if value.isEmpty {
            FlowLayout(spacing: 6) {
                ForEach(Self.examples, id: \.self) { ex in
                    Button {
                        track("work_compose.log_example_click", ["example_index": Self.examples.firstIndex(of: ex)])
                        text = ex
                    } label: {
                        Text(ex)
                            .font(.pt(12))
                            .foregroundStyle(Color.ink3)
                            .padding(.horizontal, 8)
                            .frame(height: 24)
                            .overlay(Capsule().stroke(Color.line, lineWidth: 1))
                            .contentShape(Capsule())
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    @MainActor
    private func submit(method: String) async {
        let v = value
        guard !v.isEmpty, !saving else { return }
        let parsed = LineParser.parse(v, today: KST.today())
        track("work_compose.log_submit", [
            "text_length": v.count, "has_parsed_time": parsed.startTime != nil, "has_parsed_duration": parsed.minutes != nil,
            "name_from_example": Self.examples.contains(v), "submit_method": method,
        ])
        saving = true
        defer { saving = false }
        let request = RecordActivityLogRequest(
            items: [RecordActivityLogItem(clientKey: RecordAPI.newClientKey(), text: v, goalCategoryId: goal.goalCategoryId)]
        )
        let result = await Mutation.run {
            try await RecordAPI.logActivities(request)
        }
        track("work_compose.log_result", GD.result(Mutation.lastErrorStatus == nil && result != nil))
        guard let res = result ?? nil else { return }
        onLogged?()
        var keys: [DataKey] = [.activities, .record]
        if !res.tempGoals.isEmpty { keys.append(.goals) }
        Invalidation.shared.invalidate(keys)
        text = ""
        // 서버가 정한 시간을 알려줘야 "시간 없이 적으면 어떻게 되지?"가 풀려요
        if let a = res.activities.first {
            Toast.success("기록했어요 · \(KST.hm(a.startAt))–\(KST.hm(a.endAt))")
        }
        if sheetMode {
            UINotificationFeedbackGenerator().notificationOccurred(.success)
            onDone?()
        }
    }
}

private struct RecordQuickChip: View {
    let icon: String
    let text: String

    var body: some View {
        HStack(spacing: 4) {
            NSymbol(icon, size: 10, weight: .semibold)
            Text(text)
        }
        .font(.pt(12, .semibold))
        .foregroundStyle(Color.ink2)
        .padding(.horizontal, 8)
        .frame(height: 24)
        .background(Color.subtle, in: Capsule())
    }
}

#Preview {
    VStack {
        RecordQuickLogView(goal: RecordSampleData.goal)
    }
    .padding(16)
    .frame(maxHeight: .infinity, alignment: .top)
    .canvasBackground()
}
