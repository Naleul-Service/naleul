// Features/Pattern/PatternSuggestionBarView.swift
//
// 웹 features/pattern/ui/SuggestionBar.tsx (+ api.ts useApplySuggestions · useDismissSuggestions)
// 하단 제안 바 + [TimeBlock에 반영하기] 확인 시트 (웹 Modal → iOS 시트)

import SwiftUI

struct PatternSuggestionBarView: View {
    let suggestion: PatternSuggestionBar?
    @State private var open = false
    @State private var applying = false
    @State private var dismissing = false
    /// 시트가 어떻게 끝났는지 — "saved"(반영) · "cancel_button" · nil(X 또는 아래로 쓸어 닫기)
    @State private var outcome: String?
    @State private var openedAt = Date()

    // private @State 가 있으면 자동 생성 init 이 private 이 될 수 있어서, 다른 파일(PatternScreen)에서 부를 수 있게 직접 만들어요
    init(suggestion: PatternSuggestionBar?) {
        self.suggestion = suggestion
    }

    var body: some View {
        if let suggestion, let items = suggestion.items, !items.isEmpty {
            VStack(alignment: .leading, spacing: 16) {
                HStack(alignment: .top, spacing: 12) {
                    AIBadge()
                    PatternEmphasis.text(suggestion.text, color: Color.textBrand)
                        .font(.pt(15))
                        .foregroundStyle(Color.ink2)
                        .lineSpacing(3)
                        .fixedSize(horizontal: false, vertical: true)
                }
                HStack(spacing: 8) {
                    NButton("TimeBlock에 반영하기", size: .lg, fullWidth: true) {
                        PT.track("pattern.suggestion_apply_open", ["item_count": items.count, "suggestion_types": PT.suggestionTypes(items)])
                        outcome = nil
                        openedAt = Date()
                        Analytics.sheetCloseHint = nil
                        open = true
                    }
                    NButton("다음에", variant: .secondary, size: .lg, loading: dismissing) {
                        // [UX] 확인 · 되돌리기 없이 일주일 동안 숨겨요
                        PT.track("pattern.suggestion_dismiss_click", [
                            "item_count": items.count, "suggestion_types": PT.suggestionTypes(items), "period": PT.period.rawValue,
                        ])
                        Task { await dismiss(items.map(\.key)) }
                    }
                }
            }
            .padding(20)
            .frame(maxWidth: .infinity, alignment: .leading)
            .aiBorder()
            .accessibilityElement(children: .contain)
            .accessibilityLabel("패턴 기반 제안")
            .sheet(isPresented: $open, onDismiss: {
                // 반영하지 않고 닫음 — '취소' · X · 아래로 쓸어 닫기
                if let method = GD.closeMethod(outcome) {
                    PT.track("pattern.suggestion_apply_cancel", [
                        "item_count": items.count, "close_method": method,
                        "open_duration_seconds": Int(Date().timeIntervalSince(openedAt).rounded()),
                    ])
                }
            }) {
                SheetScaffold(title: "이렇게 바꿀게요",
                              description: "내일부터 적용돼요. 지난 기록과 오늘 일정은 그대로예요.",
                              dismissible: !applying) {
                    VStack(alignment: .leading, spacing: 12) {
                        ForEach(items) { item in
                            PatternChangeRow(item: item)
                        }
                        Text("그날만 직접 옮긴 블록은 그대로 두고, 새 시간과 겹치는 자동 배치 블록은 다시 배치해요.")
                            .font(.pt(13))
                            .foregroundStyle(Color.ink3)
                            .lineSpacing(3)
                            .fixedSize(horizontal: false, vertical: true)
                            .padding(.top, 4)
                    }
                } footer: {
                    NButton("취소", variant: .secondary) {
                        outcome = "cancel_button"
                        open = false
                    }
                        .disabled(applying)
                    NButton("반영하기", variant: .brand, loading: applying) {
                        outcome = "saved"
                        PT.track("pattern.suggestion_apply_click", ["item_count": items.count, "suggestion_types": PT.suggestionTypes(items)])
                        Task { await apply(items.map(\.key)) }
                    }
                }
            }
        }
    }

    /// 제안 여러 개를 차례로 반영해요 (하나 실패해도 나머지는 계속).
    /// 루틴 · TimeTable · 목표 화면이 모두 바뀌므로 관련 데이터를 전부 다시 불러와요.
    @MainActor
    private func apply(_ keys: [String]) async {
        applying = true
        var applied = 0
        var failed: [String] = []
        var firstStatus: Int?
        for key in keys {
            do {
                try await PatternAPI.applySuggestion(key: key)
                applied += 1
            } catch {
                failed.append(errorMessage(error))
                if firstStatus == nil { firstStatus = PT.status(error) }
            }
        }
        PT.track("pattern.suggestion_apply_result", [
            "result": failed.isEmpty ? "success" : "fail",
            "applied_count": applied, "skipped_count": failed.count, "error_status": firstStatus,
        ])
        if applied > 0 && failed.isEmpty {
            Toast.success("TimeBlock에 반영했어요. 내일부터 적용돼요.")
        } else if applied > 0 {
            Toast.error("\(applied)개는 반영했지만 \(failed.count)개는 못 했어요. \(failed[0])")
        } else if let first = failed.first {
            Toast.error(first)
        }
        Invalidation.shared.invalidate(.pattern, .timetable, .goals)
        applying = false
        open = false
    }

    @MainActor
    private func dismiss(_ keys: [String]) async {
        dismissing = true
        defer { dismissing = false }
        let done: Void? = await Mutation.run(invalidate: [.pattern], successMessage: "이 제안은 일주일 동안 보이지 않아요.") { () async throws -> Void in
            for key in keys { try await PatternAPI.dismissSuggestion(key: key) }
        }
        let ok = done != nil
        PT.track("pattern.suggestion_dismiss_result", GD.result(ok).merging(["item_count": keys.count]) { _, new in new })
    }
}

private struct PatternChangeRow: View {
    let item: PatternSuggestionItem

    private var isTime: Bool { item.type == .ROUTINE_TIME }

    private func describe(_ c: PatternSuggestionChange) -> String {
        if isTime { return "\(c.startTime ?? "")–\(c.endTime ?? "")" }
        return (c.days ?? []).map { PatternFormat.dayShort($0) }.joined(separator: "·")
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(item.routineName).font(.pt(15, .bold)).foregroundStyle(Color.ink)
            HStack(spacing: 8) {
                Text(isTime ? "시간" : "요일").foregroundStyle(Color.ink3)
                Text(describe(item.before)).strikethrough().foregroundStyle(Color.ink4)
                Text("→").accessibilityLabel("에서")
                Text(describe(item.after)).fontWeight(.bold).foregroundStyle(Color.textBrand)
            }
            .font(.pt(14))
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.subtle, in: RoundedRectangle(cornerRadius: Radius.xxl))
    }
}

#Preview {
    VStack {
        PatternSuggestionBarView(suggestion: PatternSampleData.report.suggestion)
    }
    .padding(16)
    .frame(maxHeight: .infinity)
    .canvasBackground()
}
