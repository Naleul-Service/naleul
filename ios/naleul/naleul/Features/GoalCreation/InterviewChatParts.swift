// Features/GoalCreation/InterviewChatParts.swift
//
// 대화 화면 부품: 말풍선 · 입력 중 표시 · 입력창 · 질문 진행 점.
// 웹 원본: features/goal-creation/ui/ChatBubbles.tsx · ChatComposer.tsx · InterviewProgress.tsx

import SwiftUI

// MARK: - 말풍선

private struct InterviewAiAvatar: View {
    var body: some View {
        AIBadge()
    }
}

/// AI 말풍선 (왼쪽 위 모서리만 덜 둥글게)
struct InterviewAssistantBubble: View {
    let content: String?

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            InterviewAiAvatar()
            Text(content ?? "")
                .textStyle(.bodyLG)
                .foregroundStyle(Color.textPrimary)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 5)
            Spacer(minLength: 32)
        }
    }
}

/// 내 말풍선 + 실패 시 다시 보내기
struct InterviewUserBubble: View {
    let item: InterviewChatItem
    let onRetry: (String) -> Void
    let onDiscard: (String) -> Void

    private var failed: Bool { item.sendState == .failed }

    var body: some View {
        VStack(alignment: .trailing, spacing: 6) {
            HStack(spacing: 0) {
                Spacer(minLength: 48)
                if item.skip {
                    Text("건너뛸게요")
                        .font(.pt(14))
                        .foregroundStyle(Color.ink3)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .overlay(
                            UnevenRoundedRectangle(topLeadingRadius: 16, bottomLeadingRadius: 16,
                                                   bottomTrailingRadius: 16, topTrailingRadius: 6)
                                .stroke(Color.borderSubtle, style: StrokeStyle(lineWidth: 1, dash: [4, 3]))
                        )
                } else {
                    Text(item.content ?? "")
                        .textStyle(.bodyLG)
                        .foregroundStyle(Color.textPrimary)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 11)
                        .background(Color.aiTintSoft, in: UnevenRoundedRectangle(topLeadingRadius: 18, bottomLeadingRadius: 18,
                                                                               bottomTrailingRadius: 6, topTrailingRadius: 18))
                        .opacity(failed ? 0.5 : (item.sendState == .sending ? 0.7 : 1))
                }
            }

            if failed {
                HStack(spacing: 8) {
                    HStack(spacing: 4) {
                        Image(systemName: "exclamationmark.circle").font(.system(size: 12))
                        Text(item.failedMessage ?? "전송하지 못했어요.")
                    }
                    .foregroundStyle(Color.danger)
                    Button { onRetry(item.key) } label: {
                        HStack(spacing: 4) {
                            Image(systemName: "arrow.clockwise").font(.system(size: 12, weight: .semibold))
                            Text("다시 보내기")
                        }
                        .font(.pt(13, .semibold))
                        .foregroundStyle(Color.ink)
                    }
                    .buttonStyle(.plain)
                    Button { onDiscard(item.key) } label: {
                        Text("지우기").foregroundStyle(Color.ink3)
                    }
                    .buttonStyle(.plain)
                }
                .font(.pt(13))
                .multilineTextAlignment(.trailing)
            }
        }
        .frame(maxWidth: .infinity, alignment: .trailing)
    }
}

/// AI 가 답변을 만드는 중 (점 3개)
struct InterviewTypingIndicator: View {
    @State private var up = false

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            InterviewAiAvatar()
            HStack(spacing: 4) {
                ForEach(0..<3, id: \.self) { i in
                    Circle()
                        .fill(Color.ink4)
                        .frame(width: 6, height: 6)
                        .offset(y: up ? -3 : 2)
                        .animation(.easeInOut(duration: 0.45).repeatForever(autoreverses: true).delay(Double(i) * 0.14),
                                   value: up)
                }
            }
            .padding(.horizontal, 16)
            .frame(height: 46)
            .background(Color.subtle, in: UnevenRoundedRectangle(topLeadingRadius: 6, bottomLeadingRadius: 16,
                                                               bottomTrailingRadius: 16, topTrailingRadius: 16))
            Spacer(minLength: 0)
        }
        .onAppear { up = true }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("AI가 답변을 준비하고 있어요")
    }
}

// MARK: - 입력창

/// 채팅 입력창. 내용에 맞춰 높이가 늘어나요 (최대 6줄).
/// 웹은 Enter 전송 · Shift+Enter 줄바꿈인데, 아이폰 키보드는 줄바꿈 키만 있어서 → 오른쪽 보내기 버튼으로 보내요.
struct InterviewChatComposer: View {
    static let maxLength = 600

    @Binding var value: String
    var disabled = false
    var placeholder = "답변을 입력하세요"
    var focus: FocusState<Bool>.Binding
    let onSubmit: () -> Void

    init(value: Binding<String>, disabled: Bool = false, placeholder: String = "답변을 입력하세요",
         focus: FocusState<Bool>.Binding, onSubmit: @escaping () -> Void) {
        self._value = value
        self.disabled = disabled
        self.placeholder = placeholder
        self.focus = focus
        self.onSubmit = onSubmit
    }

    private var canSend: Bool { !disabled && !GoalCreationForm.trim(value).isEmpty }
    private var nearLimit: Bool { value.count > Self.maxLength - 50 }

    var body: some View {
        HStack(alignment: .bottom, spacing: 8) {
            TextField(placeholder, text: Binding(
                get: { value },
                set: { value = String($0.prefix(Self.maxLength)) }
            ), axis: .vertical)
            .lineLimit(1...6)
            .font(.bodyLG)
            .foregroundStyle(Color.textPrimary)
            .tint(Color.actionPrimary)
            .focused(focus)
            .disabled(disabled)
            .padding(.vertical, 9)
            .accessibilityLabel("답변 입력")

            if nearLimit {
                Text("\(value.count)/\(Self.maxLength)")
                    .font(.ptMono(12))
                    .foregroundStyle(Color.ink3)
                    .padding(.bottom, 12)
            }

            Button {
                if canSend { onSubmit() }
            } label: {
                NSymbol("arrow.up", size: 18, weight: .bold)
                    .foregroundStyle(canSend ? Color.actionPrimaryFg : Color.textTertiary)
                    .frame(width: 40, height: 40)
                    .background(canSend ? Color.actionPrimary : Color.borderSubtle, in: Circle())
            }
            .buttonStyle(.plain)
            .disabled(!canSend)
            .accessibilityLabel("보내기")
        }
        .padding(.leading, 18)
        .padding(.trailing, 6)
        .padding(.vertical, 6)
        .frame(minHeight: 52)
        .background(disabled ? Color.disabledBg : Color.bgSurface, in: RoundedRectangle(cornerRadius: Radius.composer))
        .overlay(RoundedRectangle(cornerRadius: Radius.composer).strokeBorder(Color.borderSubtle, lineWidth: 1))
    }
}

// MARK: - 질문 진행 (●●○○○)

struct InterviewProgress: View {
    let current: Int
    let max: Int

    var body: some View {
        let shown = Swift.min(Swift.max(current, 0), max)
        // 웹은 좁은 화면에서 "질문 2 / 5" 글자를 숨기고 점만 보여줘요
        HStack(spacing: 4) {
            ForEach(0..<Swift.max(max, 0), id: \.self) { i in
                Circle()
                    .fill(i < shown - 1 ? Color.ink : (i == shown - 1 ? Color.brand : Color.lineStrong))
                    .frame(width: 6, height: 6)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("질문 \(shown) / \(max)")
    }
}

#Preview {
    struct Demo: View {
        @State private var text = ""
        @FocusState private var focused: Bool
        var body: some View {
            VStack(spacing: 16) {
                InterviewProgress(current: 2, max: 5)
                InterviewAssistantBubble(content: "좋아요! 지금 몸무게와 목표 몸무게를 알려주세요.")
                InterviewUserBubble(item: InterviewChatItem(key: "a", role: .USER, content: "78kg인데 70kg까지", skip: false,
                                                            quickReplies: [], skippable: false), onRetry: { _ in }, onDiscard: { _ in })
                InterviewUserBubble(item: InterviewChatItem(key: "b", role: .USER, content: nil, skip: true,
                                                            quickReplies: [], skippable: false), onRetry: { _ in }, onDiscard: { _ in })
                InterviewUserBubble(item: InterviewChatItem(key: "c", role: .USER, content: "건강 때문에요", skip: false,
                                                            quickReplies: [], skippable: false, sendState: .failed,
                                                            failedMessage: "AI 응답이 늦어지고 있어요."),
                                    onRetry: { _ in }, onDiscard: { _ in })
                InterviewTypingIndicator()
                Spacer()
                InterviewChatComposer(value: $text, focus: $focused) {}
            }
            .padding(16)
            .background(Color.surface)
        }
    }
    return Demo()
}
