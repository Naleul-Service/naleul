// Features/GoalCreation/InterviewChatView.swift
//
// G-1 목표 대화 (/goal/new/{sessionId}).
// 웹 원본: features/goal-creation/ui/SessionScreen.tsx · InterviewChatView.tsx

import SwiftUI

/// 세션을 불러와서 대화 화면을 그려요 (웹 SessionScreen)
struct InterviewChatScreen: View {
    let sessionId: Int
    /// 409 등으로 서버와 어긋나면 이 값을 올려 대화 화면을 새로 그려요
    @State private var syncKey = 0

    /// private @State 가 있으면 자동 생성 init 이 private 이 돼요 → 다른 파일(GoalCreationFlowView)에서 부르려고 직접 둬요
    init(sessionId: Int) {
        self.sessionId = sessionId
    }

    var body: some View {
        GoalCreationSessionGate(sessionId: sessionId, allow: [.INTERVIEWING]) { session, refetch in
            InterviewChatView(initial: session, isResume: syncKey > 0, onResync: {
                Task { @MainActor in
                    await refetch()
                    syncKey += 1
                }
            })
            .id("\(sessionId)-\(syncKey)")
        }
    }
}

struct InterviewChatView: View {
    @Environment(GoalCreationNavigator.self) private var nav
    @State private var chat: InterviewChatModel
    @State private var leaveOpen = false
    @FocusState private var composerFocused: Bool
    /// 409 로 다시 불러와 새로 그린 경우 (pageview 의 is_resume)
    private let isResume: Bool

    init(initial: GoalCreationSessionDetail, isResume: Bool = false, onResync: @escaping () -> Void) {
        _chat = State(initialValue: InterviewChatModel(initial: initial, onResync: onResync))
        self.isResume = isResume
    }

    private var gc: GCTracker { .shared }

    /// 새 말풍선 · 입력 중 표시 · 상태가 바뀌면 맨 아래로 스크롤
    private var scrollKey: String {
        "\(chat.items.count)-\(chat.pending)-\(chat.meta.status.rawValue)-\(chat.rejectedMessage ?? "")"
    }

    var body: some View {
        VStack(spacing: 0) {
            messages
            Divider().overlay(Color.borderSoft)
            bottomPanel
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
                .background(Color.surface)
        }
        .background(Color.surface.ignoresSafeArea())
        .goalCreationHeader(onClose: { onClose() }, center: {
            if chat.interviewing {
                InterviewProgress(current: chat.meta.questionCount, max: chat.meta.maxQuestions)
            }
        }, action: {
            if chat.interviewing && chat.meta.canFinishEarly {
                Button {
                    gc.track(GCEvent.finishEarly, [
                        "session_id": chat.sessionId, "question_count": chat.meta.questionCount,
                        "max_questions": chat.meta.maxQuestions,
                    ])
                    Task { await chat.finishEarly() }
                } label: {
                    HStack(spacing: 4) {
                        NSymbol("sparkles", size: 12, weight: .semibold)
                        Text("바로 만들기")
                    }
                }
                .buttonStyle(.n(.ai, .sm, iconPosition: .leading))
                .disabled(!chat.canInteract)
            }
        })
        .confirmDialog(isPresented: $leaveOpen, title: "대화를 잠시 멈출까요?", message: GoalCreationConst.leaveNotice,
                       confirmLabel: "나가기", cancelLabel: "계속 만들기",
                       onCancel: {
                           gc.track(GCEvent.leaveCancel, ["session_id": chat.sessionId, "question_count": chat.meta.questionCount])
                       }) {
            gc.track(GCEvent.leaveConfirm, ["session_id": chat.sessionId, "question_count": chat.meta.questionCount])
            nav.close()
        }
        .onAppear {
            let n = nav
            chat.onNotFound = { n.go(.start) }
            if chat.interviewing { composerFocused = true }
            gc.questionCount = chat.meta.questionCount
            gc.track(GCEvent.chatPageview, [
                "session_id": chat.sessionId, "question_count": chat.meta.questionCount,
                "max_questions": chat.meta.maxQuestions, "is_resume": isResume,
            ])
        }
        .onChange(of: chat.meta.questionCount) { _, n in gc.questionCount = n }
        // 인터뷰가 끝나면 마지막 말풍선을 잠깐 보여준 뒤 정리 확인 화면(G-2)으로 이동
        .task(id: chat.done) { await autoAdvance() }
    }

    // MARK: 대화 영역

    private var messages: some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    ForEach(chat.items) { item in
                        if item.role == .ASSISTANT {
                            InterviewAssistantBubble(content: item.content)
                        } else {
                            InterviewUserBubble(item: item,
                                                onRetry: { key in
                                                    gc.track(GCEvent.chatRetry, ["session_id": chat.sessionId])
                                                    Task { await chat.retry(key) }
                                                },
                                                onDiscard: { key in
                                                    gc.track(GCEvent.chatDiscard, ["session_id": chat.sessionId])
                                                    chat.discardFailed(key)
                                                })
                        }
                    }

                    // 마지막 질문의 빠른 답변
                    if chat.interviewing, let q = chat.currentQuestion, !q.quickReplies.isEmpty, !chat.pending {
                        FlowLayout(spacing: 8) {
                            ForEach(Array(q.quickReplies.enumerated()), id: \.element) { i, reply in
                                QuickReply(reply) {
                                    gc.track(GCEvent.quickReply, [
                                        "session_id": chat.sessionId, "question_index": chat.meta.questionCount, "reply_index": i,
                                    ])
                                    Task { await chat.sendQuickReply(reply, index: i) }
                                }
                            }
                        }
                        .disabled(!chat.canInteract)
                        .opacity(chat.canInteract ? 1 : 0.4)
                        .padding(.leading, 42)
                        .padding(.top, -4)
                    }

                    if chat.pending { InterviewTypingIndicator() }

                    if let rejected = chat.rejectedMessage { rejectedCard(rejected) }

                    Color.clear.frame(height: 1).id("bottom")
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 24)
            }
            .scrollDismissesKeyboard(.interactively)
            .onAppear { proxy.scrollTo("bottom", anchor: .bottom) }
            .onChange(of: scrollKey) {
                withAnimation(.easeOut(duration: 0.25)) { proxy.scrollTo("bottom", anchor: .bottom) }
            }
            .onChange(of: composerFocused) { _, focused in
                guard focused else { return }
                // 키보드가 올라온 뒤에 마지막 말풍선이 보이게
                Task { @MainActor in
                    try? await Task.sleep(nanoseconds: 300_000_000)
                    withAnimation { proxy.scrollTo("bottom", anchor: .bottom) }
                }
            }
        }
    }

    private func rejectedCard(_ message: String) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(message)
                .font(.pt(15)).foregroundStyle(Color.ink).lineSpacing(4)
                .fixedSize(horizontal: false, vertical: true)
            NButton("다른 목표로 시작하기") {
                gc.track(GCEvent.restart, ["session_id": chat.sessionId])
                nav.go(.start)
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.bgSunken, in: RoundedRectangle(cornerRadius: Radius.xxl))
        .overlay(RoundedRectangle(cornerRadius: Radius.xxl).stroke(Color.line, lineWidth: 1))
        .padding(.top, 8)
    }

    // MARK: 하단: 입력창 또는 인터뷰 완료 패널

    @ViewBuilder
    private var bottomPanel: some View {
        // 바인딩($)용으로만 따로 이름을 둬요 — Task 클로저가 지역 var 를 잡지 않게 (동시성 검사 에러 방지)
        @Bindable var bindableChat = chat
        if chat.interviewing {
            VStack(alignment: .trailing, spacing: 8) {
                if chat.currentQuestion?.skippable == true {
                    Button {
                        gc.track(GCEvent.skip, ["session_id": chat.sessionId, "question_index": chat.meta.questionCount,
                                                  "extra_note": chat.currentQuestion?.targetSlot == "extraNote"])
                        Task { await chat.skipQuestion() }
                    } label: {
                        Text("건너뛰기").font(.pt(13, .medium)).foregroundStyle(Color.ink3)
                            .padding(.horizontal, 8).padding(.vertical, 4)
                    }
                    .buttonStyle(.plain)
                    .disabled(!chat.canInteract)
                    .opacity(chat.canInteract ? 1 : 0.4)
                }
                InterviewChatComposer(value: $bindableChat.input, disabled: !chat.canInteract, focus: $composerFocused) {
                    gc.track(GCEvent.answerSend, [
                        "session_id": chat.sessionId, "question_index": chat.meta.questionCount,
                        "answer_length": chat.input.trimmingCharacters(in: .whitespacesAndNewlines).count,
                        "extra_note": chat.currentQuestion?.targetSlot == "extraNote",
                    ])
                    Task { await chat.sendText() }
                }
            }
        } else if chat.done {
            VStack(spacing: 12) {
                HStack(spacing: 6) {
                    Image(systemName: "checkmark.circle.fill").font(.system(size: 18)).foregroundStyle(Color.success)
                    Text("필요한 정보를 모두 모았어요").font(.pt(15, .bold)).foregroundStyle(Color.ink)
                }
                NButton("정리 확인하기", variant: .primary) {
                    gc.track(GCEvent.reviewClick, ["session_id": chat.sessionId])
                    nav.go(.review(chat.sessionId))
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
        } else if chat.rejectedMessage == nil {
            Text("대화가 끝났어요.")
                .font(.pt(14)).foregroundStyle(Color.ink3)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 8)
        }
    }

    // MARK: 동작

    @MainActor
    private func autoAdvance() async {
        guard chat.done else { return }
        try? await Task.sleep(nanoseconds: 1_200_000_000)
        if !Task.isCancelled { nav.go(.review(chat.sessionId)) }
    }

    @MainActor
    private func onClose() {
        gc.track(GCEvent.chatClose, [
            "session_id": chat.sessionId, "has_confirm": chat.interviewing, "question_count": chat.meta.questionCount,
        ])
        if chat.interviewing { leaveOpen = true } else { nav.close() }
    }
}

#Preview {
    NavigationStack {
        InterviewChatView(initial: GoalCreationSamples.session, onResync: {})
    }
    .environment(GoalCreationNavigator(route: .chat(101)))
}
