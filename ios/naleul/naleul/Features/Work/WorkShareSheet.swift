// Features/Work/WorkShareSheet.swift
//
// 웹 features/work/ui/WorkShareModal.tsx
// 공유하기 — 서버가 정리해 준 텍스트를 카톡 · 슬랙에 붙여 넣기 좋게 보여주고 복사 · 공유해요.
// 열 때마다 · 범위를 바꿀 때마다 새로 받아요 (보드가 바뀌었을 수 있어서).

import SwiftUI
import UIKit

@MainActor
struct WorkShareSheet: View {
    let goalId: Int
    /// 미리보기용 글 (주면 네트워크 안 씀)
    private let previewText: String?

    @State private var scope: WorkShareScope = .REMAINING
    @State private var text = ""
    @State private var loading = true
    @State private var failed: String?
    /// 닫기 전에 복사 · 공유를 했는지 (work_share.close 의 copied)
    @State private var copied = false

    init(goalId: Int, previewText: String? = nil) {
        self.goalId = goalId
        self.previewText = previewText
    }

    private var scopeOptions: [(value: WorkShareScope, label: String)] {
        WorkShareScope.allCases.map { s in (value: s, label: s.label) }
    }

    private func hint(_ s: WorkShareScope) -> String {
        switch s {
        case .TODAY: return "오늘 할 일 + 오늘 끝낸 일"
        case .REMAINING: return "진행 중 + 시작 전 (세부 목표별)"
        case .ALL: return "남은 일 + 최근 7일 완료"
        }
    }

    var body: some View {
        SheetScaffold(title: "Task 공유하기", description: "팀 채팅방에 붙여 넣기 좋게 정리했어요.", detents: [.large]) {
            VStack(alignment: .leading, spacing: 10) {
                SegmentPicker(selection: Binding(get: { scope }, set: { s in
                    guard s != scope else { return }
                    scope = s
                    track("work_share.scope_select", ["share_scope": s.rawValue])
                }), options: scopeOptions, fullWidth: true)
                Text(hint(scope)).font(.pt(12)).foregroundStyle(Color.ink3)
                textBox.padding(.top, 4)
            }
        } footer: {
            ShareLink(item: text) {
                HStack(spacing: 6) {
                    NSymbol("square.and.arrow.up", size: 14, weight: .semibold)
                    Text("공유")
                }
            }
            .buttonStyle(.n(.secondary))
            .disabled(text.isEmpty)
            // ShareLink 는 누른 순간을 알려주지 않아서 같이 눌리는 제스처로 남겨요 (실제로 보냈는지는 알 수 없어요)
            .simultaneousGesture(TapGesture().onEnded {
                guard !text.isEmpty else { return }
                copied = true
                track("work_share.native_share_click", ["share_scope": scope.rawValue, "text_length": text.count])
            })
            NButton("복사", icon: "doc.on.doc") { copy() }
                .disabled(text.isEmpty)
        }
        .task(id: scope) { await load() }
        .onDisappear {
            guard let m = GD.closeMethod(nil) else { return }
            track("work_share.close", ["close_method": m, "copied": copied])
        }
    }

    private func track(_ event: String, _ props: EventProps = [:]) {
        if previewText != nil { return }
        WK.track(event, goalId: goalId, props)
    }

    @ViewBuilder
    private var textBox: some View {
        Group {
            if loading && text.isEmpty {
                ProgressView()
                    .tint(Color.ink3)
                    .frame(maxWidth: .infinity, minHeight: 128)
            } else if let msg = failed {
                Text(msg).font(.pt(14)).foregroundStyle(Color.danger)
            } else if text.isEmpty {
                Text("공유할 Task가 없어요").font(.pt(14)).foregroundStyle(Color.ink3)
            } else {
                Text(text)
                    .font(.system(size: 13, design: .monospaced))
                    .foregroundStyle(Color.ink)
                    .lineSpacing(4)
                    .textSelection(.enabled)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, minHeight: 160, alignment: .topLeading)
        .background(Color.bgSunken, in: RoundedRectangle(cornerRadius: Radius.xxl))
        .overlay(RoundedRectangle(cornerRadius: Radius.xxl).stroke(Color.line, lineWidth: 1))
        .opacity(loading && !text.isEmpty ? 0.5 : 1)
    }

    @MainActor
    private func load() async {
        if let previewText {
            text = previewText
            loading = false
            return
        }
        loading = true
        failed = nil
        do {
            text = try await WorkAPI.shareText(goalId: goalId, scope: scope)
        } catch is CancellationError {
            return
        } catch {
            text = ""
            failed = errorMessage(error)
            track("work_share.text_load_fail", [
                "share_scope": scope.rawValue, "error_status": (error as? APIError)?.httpStatus ?? 0,
            ])
        }
        loading = false
    }

    private func copy() {
        track("work_share.copy_click", ["share_scope": scope.rawValue, "text_length": text.count])
        copied = true
        UIPasteboard.general.string = text
        UINotificationFeedbackGenerator().notificationOccurred(.success)
        Toast.success("복사했어요. 원하는 곳에 붙여 넣으세요.")
    }
}

#Preview("공유") {
    WorkShareSheet(goalId: 2, previewText: WorkSampleData.shareText)
}

#Preview("시트로") {
    Color.canvas
        .sheet(isPresented: .constant(true)) {
            WorkShareSheet(goalId: 2, previewText: WorkSampleData.shareText)
        }
}
