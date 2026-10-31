// Features/Settings/SettingsWithdrawScreen.swift
//
// 웹 원본: features/account/ui/WithdrawView.tsx (/settings/withdraw)
//
// 회원 탈퇴: 사유 고르기 → (기타면 상세 사유) → 유의 사항 동의 → 확인창 → DELETE /v1/users → 로그아웃

import SwiftUI

struct SettingsWithdrawScreen: View {
    @State private var reason: SettingsWithdrawalReason?
    @State private var detail = ""
    @State private var agreed = false
    @State private var confirmOpen = false
    @State private var withdrawing = false
    // MARK: Amplitude
    @State private var didTrack = false
    /// 탈퇴 요청을 보냈으면 이탈(abandon)로 보지 않아요
    @State private var withdrew = false

    init() {}

    /// "기타"일 때만 상세 사유가 필수 (백엔드 서비스 단 검증과 같은 규칙)
    private var reasonOk: Bool {
        guard let reason else { return false }
        return reason != .OTHER || !detail.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
    private var canSubmit: Bool { reasonOk && agreed }

    /// 누를 수 없는 이유 (withdraw.submit_click 의 validation_error) — no_reason · no_detail · not_agreed · nil
    private var validationError: String? {
        if reason == nil { return "no_reason" }
        if !reasonOk { return "no_detail" }
        if !agreed { return "not_agreed" }
        return nil
    }

    private var validationMessage: String? {
        switch validationError {
        case "no_reason": return "떠나시는 이유를 선택해 주세요."
        case "no_detail": return "탈퇴 사유를 적어 주세요."
        case "not_agreed": return "탈퇴 유의 사항에 동의해 주세요."
        default: return nil
        }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Text("정말 탈퇴하실건가요?").font(.pt(16, .semibold)).foregroundStyle(Color.ink)
                Text("탈퇴 시 유의사항").font(.pt(13, .medium)).foregroundStyle(Color.ink3).padding(.top, 12)
                HStack(alignment: .firstTextBaseline, spacing: 6) {
                    Text("•")
                    Text("수집된 개인정보 및 나를 계정에 저장된 모든 정보(목표, 루틴, 일정, 미션과 관련된 모든 컨텐츠)는 삭제되어 복구할 수 없고 연결된 기기의 연결이 해제됩니다.")
                        .lineSpacing(3)
                }
                .font(.pt(13))
                .foregroundStyle(Color.ink3)
                .padding(.top, 4)
                .padding(.leading, 6)

                reasonPicker.padding(.top, 24)

                if reason == .OTHER {
                    TextField("탈퇴 사유를 자유롭게 적어주세요", text: Binding(
                        get: { detail },
                        set: { detail = String($0.prefix(500)) }   // maxLength 500
                    ), axis: .vertical)
                    .lineLimit(4...8)
                    .inputStyle(minHeight: 104)
                    .accessibilityLabel("탈퇴 상세 사유")
                    .padding(.top, 12)
                }

                agreeCheckbox.padding(.top, 24)

                // 예전엔 사유 · 동의 전에는 버튼이 꺼져 있어서 "눌리지 않는다"고 느껴졌어요
                // → 항상 누를 수 있게 두고, 빠진 게 있으면 무엇을 해야 하는지 알려줘요
                NButton("탈퇴하기", variant: .danger, size: .lg, loading: withdrawing, fullWidth: true) {
                    ST.track("withdraw.submit_click", [
                        "withdraw_reason": reason?.rawValue,
                        "text_length": detail.trimmingCharacters(in: .whitespacesAndNewlines).count,
                        "validation_error": validationError,
                    ])
                    if let message = validationMessage {
                        Toast.show(message)
                        return
                    }
                    confirmOpen = true
                }
                .opacity(canSubmit ? 1 : 0.55)
                .padding(.top, 20)
            }
            .padding(20)
            .frame(maxWidth: .infinity, alignment: .leading)
            .card()
            .padding(16)
        }
        .canvasBackground()
        .navigationTitle("회원 탈퇴")
        .navigationBarTitleDisplayMode(.inline)
        .confirmDialog(isPresented: $confirmOpen, title: "정말 탈퇴하시겠어요?", message: "이 작업은 되돌릴 수 없어요.",
                       confirmLabel: "탈퇴하기", tone: .danger, onCancel: {
            ST.track("withdraw.confirm_cancel", ["withdraw_reason": reason?.rawValue])
        }) {
            ST.track("withdraw.confirm_click", [
                "withdraw_reason": reason?.rawValue, "is_premium": ST.isPremium, "membership_state": ST.currentMembershipState,
            ])
            submit()
        }
        .onAppear {
            if !didTrack {
                didTrack = true
                ST.track("withdraw.pageview", ["is_premium": ST.isPremium, "membership_state": ST.currentMembershipState])
            }
        }
        .onDisappear {
            // [UX] 탈퇴하지 않고 떠남 — 어디까지 쓰다 마음을 바꿨는지
            guard !withdrew else { return }
            ST.track("withdraw.abandon", [
                "withdraw_reason": reason?.rawValue, "checked": agreed,
                "has_input": reason != nil || !detail.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
            ])
        }
    }

    /// 웹 <select> → iOS 메뉴
    private var reasonPicker: some View {
        VStack(alignment: .leading, spacing: 6) {
            (Text("떠나시는 이유를 알려주세요 ") + Text("*").foregroundColor(Color.danger))
                .font(.pt(14, .semibold))
                .foregroundStyle(Color.ink2)
            Menu {
                ForEach(SettingsWithdrawalReason.allCases) { r in
                    Button(r.label) {
                        if reason != r { ST.track("withdraw.reason_select", ["withdraw_reason": r.rawValue]) }
                        reason = r
                        if r != .OTHER { detail = "" }
                    }
                }
            } label: {
                HStack(spacing: 8) {
                    Text(reason?.label ?? "탈퇴 사유 선택")
                        .font(.pt(15))
                        .foregroundStyle(reason == nil ? Color.ink4 : Color.ink)
                        .multilineTextAlignment(.leading)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Image(systemName: "chevron.up.chevron.down")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(Color.ink3)
                }
                .padding(.horizontal, 14)
                .frame(minHeight: 44)
                .background(Color.surface, in: RoundedRectangle(cornerRadius: Radius.xl))
                .overlay(RoundedRectangle(cornerRadius: Radius.xl).stroke(Color.borderSubtle, lineWidth: 1))
                .contentShape(Rectangle())
            }
        }
    }

    private var agreeCheckbox: some View {
        Button {
            agreed.toggle()
            ST.track("withdraw.agree_toggle", ["checked": agreed, "withdraw_reason": reason?.rawValue])
        } label: {
            HStack(spacing: 10) {
                ZStack {
                    RoundedRectangle(cornerRadius: 6)
                        .fill(agreed ? Color.brand : Color.surface)
                    RoundedRectangle(cornerRadius: 6)
                        .stroke(agreed ? Color.brand : Color.borderSubtle, lineWidth: 1)
                    if agreed {
                        NSymbol("checkmark", size: 11, weight: .bold).foregroundStyle(.white)
                    }
                }
                .frame(width: 20, height: 20)
                Text("회원 탈퇴 유의 사항을 확인했으며 동의합니다.")
                    .font(.pt(14))
                    .foregroundStyle(Color.ink2)
                    .multilineTextAlignment(.leading)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(agreed ? .isSelected : [])
    }

    private func submit() {
        guard let reason, canSubmit, !withdrawing else { return }
        let detailText = reason == .OTHER ? detail.trimmingCharacters(in: .whitespacesAndNewlines) : ""
        withdrawing = true
        withdrew = true
        Task { @MainActor in
            do {
                try await SettingsAccountAPI.withdraw(reason: reason, detail: detailText)
                ST.track("withdraw.result", ["result": "success", "withdraw_reason": reason.rawValue])
                // 성공하면 토큰을 지우고 로그인 화면으로 (RootView 가 isLoggedIn 변화를 보고 바꿔요)
                AuthService.shared.logout(reason: .withdraw)
            } catch is CancellationError {
                withdrawing = false
                withdrew = false
            } catch {
                ST.track("withdraw.result", ["result": "fail", "error_status": ST.status(error), "withdraw_reason": reason.rawValue])
                withdrawing = false
                withdrew = false
                Toast.error(errorMessage(error, fallback: "탈퇴에 실패했어요. 잠시 후 다시 시도해 주세요."))
            }
        }
    }
}

#Preview {
    NavigationStack { SettingsWithdrawScreen() }
}
