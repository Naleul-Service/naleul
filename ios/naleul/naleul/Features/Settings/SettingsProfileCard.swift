// Features/Settings/SettingsProfileCard.swift
//
// 웹 원본: features/account/ui/ProfileSummaryCard.tsx (설정 맨 위 프로필 요약)
//          features/account/ui/ProfileEditModal.tsx (닉네임 수정)
//          features/account/api.ts (닉네임 규칙 · 탈퇴 사유 · 요청)
//
// 웹은 BFF(/me/nickname)를 거쳐 쿠키를 고치지만, iOS 는 백엔드 PATCH /v1/users/nickname 을 바로 부르고
// 성공하면 UserStore 의 이름을 바꿔서 화면에 바로 보이게 해요.

import SwiftUI

// MARK: - 계정 API

enum SettingsAccountAPI {
    /// 백엔드 UserNicknameUpdateRequest 의 @Pattern 과 같아요
    static let nicknamePattern = "^[가-힣a-zA-Z0-9]{2,10}$"
    static let nicknameHelp = "특수문자 제외 2~10자 이내로 입력해주세요."

    static func isValidNickname(_ s: String) -> Bool {
        s.range(of: nicknamePattern, options: .regularExpression) != nil
    }

    /// 백엔드 UserNicknameUpdateResponse
    struct NicknameResponse: Decodable {
        let userId: Int?
        let nickname: String?
    }

    /// PATCH /v1/users/nickname — 바뀐 닉네임을 돌려줘요
    static func updateNickname(_ nickname: String) async throws -> String {
        let r: NicknameResponse? = try await API.patch("/v1/users/nickname", body: ["nickname": .string(nickname)])
        return r?.nickname ?? nickname
    }

    /// DELETE /v1/users (body 에 사유). 성공하면 화면에서 로그아웃해요
    static func withdraw(reason: SettingsWithdrawalReason, detail: String) async throws {
        try await API.send("DELETE", "/v1/users", body: ["reason": .string(reason.rawValue), "detail": .string(detail)])
    }
}

/// 백엔드 UserWithdrawalReason enum 과 값이 정확히 같아야 해요
enum SettingsWithdrawalReason: String, CaseIterable, Identifiable {
    case LACK_OF_CONTENT, INCONVENIENT_OR_BUGGY, PREFER_OTHER_SERVICE, OTHER

    var id: String { rawValue }

    var label: String {
        switch self {
        case .LACK_OF_CONTENT: return "앱 콘텐츠가 부족하거나 만족스럽지 않아서"
        case .INCONVENIENT_OR_BUGGY: return "이용이 불편하고 장애가 많아서"
        case .PREFER_OTHER_SERVICE: return "다른 서비스가 더 좋아서"
        case .OTHER: return "기타"
        }
    }
}

// MARK: - 프로필 요약 카드

struct SettingsProfileSummaryCard: View {
    let name: String
    let role: UserRole

    /// 시트를 열 때마다 현재 이름으로 입력칸을 새로 채우려고 id 를 새로 만들어요 (웹 modalKey)
    @State private var editing: SettingsEditRequest?

    init(name: String, role: UserRole) {
        self.name = name
        self.role = role
    }

    /// 줄 전체를 누르면 닉네임 수정 (아이폰 설정 앱의 Apple ID 줄처럼)
    var body: some View {
        Button {
            ST.track("settings.profile_edit_open", ["is_premium": role.isPaid])
            editing = SettingsEditRequest()
        } label: {
            HStack(spacing: 14) {
                Text(String(name.prefix(1)))
                    .font(.pt(22, .bold))
                    .foregroundStyle(.white)
                    .frame(width: 56, height: 56)
                    .background(
                        // 아바타 장식 색 (AI 그라데이션)
                        Color.aiGradient,
                        in: Circle()
                    )
                VStack(alignment: .leading, spacing: 0) {
                    HStack(spacing: 6) {
                        Text(name)
                            .font(.pt(19, .bold))
                            .foregroundStyle(Color.ink)
                            .lineLimit(1)
                            .truncationMode(.tail)
                        Badge(role == .free ? "FREE" : role.rawValue, tone: role == .free ? .neutral : .brand)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                NSymbol("chevron.right", size: 13, weight: .semibold)
                    .foregroundStyle(Color.ink4)
            }
            .padding(16)
            .background(Color.surface, in: RoundedRectangle(cornerRadius: Radius.card))
            .overlay(RoundedRectangle(cornerRadius: Radius.card).stroke(Color.line, lineWidth: 1))
            .contentShape(RoundedRectangle(cornerRadius: Radius.card))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(name), \(role.rawValue), 닉네임 수정")
        .sheet(item: $editing) { _ in
            SettingsProfileEditSheet(currentName: name)
        }
    }
}

/// 시트 열기 요청 (매번 새 id)
struct SettingsEditRequest: Identifiable {
    let id = UUID()
}

// MARK: - 닉네임 수정 시트 (웹 ProfileEditModal)

struct SettingsProfileEditSheet: View {
    let currentName: String
    /// 미리보기에서는 저장하지 않아요
    var isPreview = false

    @Environment(\.dismiss) private var dismiss
    @State private var nickname: String
    @State private var error: String?
    @State private var saving = false
    /// 시트가 어떻게 끝났는지 — "saved" · "cancel_button" · nil(X 또는 아래로 쓸어 닫기)
    @State private var outcome: String?

    init(currentName: String, isPreview: Bool = false) {
        self.currentName = currentName
        self.isPreview = isPreview
        _nickname = State(initialValue: currentName)
    }

    private var trimmed: String { nickname.trimmingCharacters(in: .whitespacesAndNewlines) }
    private var valid: Bool { SettingsAccountAPI.isValidNickname(trimmed) }

    var body: some View {
        SheetScaffold(title: "프로필 수정", dismissible: !saving, detents: [.medium]) {
            VStack(alignment: .leading, spacing: 6) {
                (Text("닉네임 ") + Text("*").foregroundColor(Color.danger))
                    .font(.pt(12, .medium))
                    .foregroundStyle(Color.ink3)
                TextField("이름을 작성해주세요", text: Binding(
                    get: { nickname },
                    set: { v in
                        nickname = String(v.prefix(10))   // maxLength 10
                        error = nil
                    }
                ))
                .submitLabel(.done)
                .onSubmit { submit() }
                .inputStyle(invalid: error != nil)
                FieldMessage(text: error ?? SettingsAccountAPI.nicknameHelp, isError: error != nil)
            }
        } footer: {
            NButton("취소", variant: .secondary) {
                outcome = "cancel_button"
                dismiss()
            }
                .disabled(saving)
            NButton("저장", loading: saving) { submit() }
                .disabled(!valid || trimmed == currentName)
        }
        .onAppear { Analytics.sheetCloseHint = nil }
        .onDisappear {
            if let method = GD.closeMethod(outcome) {
                ST.track("settings.profile_edit_close", ["close_method": method, "is_dirty": trimmed != currentName])
            }
        }
    }

    private func submit() {
        guard valid else {
            error = SettingsAccountAPI.nicknameHelp
            // [UX] 형식 오류는 버튼만 회색이라 키보드 완료를 눌러야 문구가 보여요
            ST.track("settings.profile_error_view", ["validation_error": "format", "text_length": trimmed.count])
            return
        }
        guard !saving, trimmed != currentName else { return }
        if isPreview { dismiss(); return }
        let name = trimmed
        ST.track("settings.profile_save_click", ["text_length": name.count])
        saving = true
        Task { @MainActor in
            defer { saving = false }
            do {
                let saved = try await SettingsAccountAPI.updateNickname(name)
                UserStore.shared.updateUserName(saved)
                Invalidation.shared.invalidate(.account)
                ST.track("settings.profile_save_result", ["result": "success"])
                Toast.success("닉네임을 바꿨어요.")
                outcome = "saved"
                dismiss()
            } catch is CancellationError {
            } catch {
                let status = ST.status(error)
                ST.track("settings.profile_save_result", ["result": "fail", "error_status": status])
                ST.track("settings.profile_error_view", ["validation_error": "server", "text_length": name.count])
                self.error = errorMessage(error, fallback: "닉네임 수정에 실패했어요. 다시 시도해 주세요.")
            }
        }
    }
}

#Preview("프로필 카드") {
    VStack(spacing: 16) {
        SettingsProfileSummaryCard(name: "유림", role: .pro)
        SettingsProfileSummaryCard(name: "나를 사용자", role: .free)
    }
    .padding(16)
    .canvasBackground()
}

#Preview("닉네임 수정") {
    Color.canvas
        .sheet(isPresented: .constant(true)) {
            SettingsProfileEditSheet(currentName: "유림", isPreview: true)
        }
}
