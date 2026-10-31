// Features/GoalCreation/GoalCreationReviewView.swift
//
// G-2 정리 확인 (/goal/new/{sessionId}/review) — 초안 검토에서 "조건 수정"으로 돌아와도 써요.
// 웹 원본: features/goal-creation/ui/review/ReviewScreen.tsx · ReviewView.tsx

import SwiftUI

struct GoalCreationReviewView: View {
    @Environment(GoalCreationNavigator.self) private var nav
    /// 다시 불러오기 (409 → 서버 상태에 맞는 화면으로)
    let reload: () async -> Void

    /// 웹은 저장 응답으로 query 캐시를 바로 고쳐요 → iOS 는 이 화면의 세션 사본을 고쳐요
    @State private var session: GoalCreationSessionDetail
    @State private var editing: GoalCreationSlotKey?
    /// 꼭 할 일 수정 (슬롯이 아니라 따로 저장돼요)
    @State private var mustDoOpen = false
    @State private var mustDoDraft: [String]
    @State private var mustDoText = ""
    @State private var saving = false
    @State private var saveError: String?

    @State private var style: PlanningStyle
    @State private var styleByUser: Bool
    @State private var generating = false
    /// 계획 강도 — 처음엔 아무것도 고르지 않은 상태 (2026-10-25: "추천" 표시 · 기본 선택 제거 — AI 가 정한 것처럼 보이지 않게).
    /// 고르지 않고 계획 만들기를 누르면 안정형으로 만들어요. 초안 화면에서 바꾸면 한 번 다시 만들어요
    @State private var intensity: GoalCreationPlanIntensity? = nil

    init(session: GoalCreationSessionDetail, reload: @escaping () async -> Void) {
        self.reload = reload
        _session = State(initialValue: session)
        _mustDoDraft = State(initialValue: session.mustDoItems ?? [])
        _style = State(initialValue: session.summary?.planningStyle ?? .PLANNER)
        _styleByUser = State(initialValue: GoalCreationStyleMemory.wasSelectedByUser(session.sessionId))
    }

    private var sessionId: Int { session.sessionId }
    private var summary: GoalCreationSummary? { session.summary }
    private var regenerate: Bool { session.status == .DRAFT_READY }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Badge("정리 확인", tone: .ai, icon: "sparkles")
                Text("이렇게 정리했어요")
                    .font(.pt(20, .semibold)).foregroundStyle(Color.ink)
                    .padding(.top, 12)
                Text("항목을 눌러 고칠 수 있어요. 확인되면 계획을 만들어 드릴게요.")
                    .font(.pt(15)).foregroundStyle(Color.ink3)
                    .padding(.top, 6)

                infoSection.padding(.top, 32)
                styleSection.padding(.top, 32)
                intensitySection.padding(.top, 32)
            }
            .padding(.horizontal, 16)
            .padding(.top, 32)
            .padding(.bottom, 40)
        }
        .background(Color.surface.ignoresSafeArea())
        .safeAreaInset(edge: .bottom) { bottomBar }
        .goalCreationHeader(onClose: {
            GCTracker.shared.track(GCEvent.reviewClose, ["session_id": sessionId])
            nav.close()
        })
        .sheet(item: $editing, onDismiss: { trackSheetCancel() }) { slot in
            GoalCreationSlotEditSheet(slot: slot, slots: session.slots, saving: saving, serverError: saveError,
                                      onClose: { editing = nil },
                                      onSave: { patch in Task { await save(patch, slotKey: slot.rawValue) } })
        }
        .sheet(isPresented: $mustDoOpen, onDismiss: { trackSheetCancel() }) { mustDoSheet }
        .onAppear {
            guard !pageviewSent else { return }
            pageviewSent = true
            let s = summary
            let filled = [s?.goalStatement, s?.metricText, s?.deadlineText, s?.approach, s?.motivation, s?.preferenceText]
                .compactMap { $0 }.filter { !$0.isEmpty }.count + ((session.mustDoItems ?? []).isEmpty ? 0 : 1)
            GCTracker.shared.track(GCEvent.reviewPageview, [
                "session_id": sessionId, "is_regenerate": regenerate, "filled_slot_count": filled,
                "ai_style": s?.planningStyle?.rawValue,
            ])
        }
    }

    // MARK: 이벤트

    /// 지금 열린 시트의 항목 (닫힐 때 slot_edit_cancel 에 붙여요)
    @State private var openSlotKey: String?
    @State private var pageviewSent = false

    private func trackSlotOpen(_ key: String, wasEmpty: Bool) {
        openSlotKey = key
        GCTracker.shared.sheetClose = nil
        GCTracker.shared.track(GCEvent.slotEditOpen, ["session_id": sessionId, "slot_key": key, "was_empty": wasEmpty])
    }

    /// 시트가 닫힌 뒤 — 저장으로 닫힌 게 아니면 취소(cancel_button / swipe_down)
    private func trackSheetCancel() {
        guard let key = openSlotKey else { return }
        openSlotKey = nil
        if let method = GCTracker.shared.takeSheetClose() {
            GCTracker.shared.track(GCEvent.slotEditCancel, ["session_id": sessionId, "slot_key": key, "close_method": method])
        }
    }

    private func isEmpty(_ slot: GoalCreationSlotKey) -> Bool {
        let s = summary
        let v: String?
        switch slot {
        case .goalStatement: v = s?.goalStatement
        case .metric: v = s?.metricText
        case .motivation: v = s?.motivation
        case .deadline: v = s?.deadlineText
        case .approach: v = s?.approach
        case .practicePreference: v = s?.preferenceText
        }
        return (v ?? "").isEmpty
    }

    // MARK: 목표 정보

    private var infoSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("목표 정보").font(.pt(15, .bold)).foregroundStyle(Color.ink)
            VStack(spacing: 0) {
                summaryRow("목표", summary?.goalStatement) { openEdit(.goalStatement) }
                Divider().overlay(Color.borderSoft)
                summaryRow("수치", summary?.metricText) { openEdit(.metric) }
                Divider().overlay(Color.borderSoft)
                summaryRow("기한", summary?.deadlineText) { openEdit(.deadline) }
                Divider().overlay(Color.borderSoft)
                summaryRow("계획", summary?.approach) { openEdit(.approach) }
                Divider().overlay(Color.borderSoft)
                // 예전 세션(이유를 묻던 때)에 답한 이유가 있으면 보여줘요
                if let motivation = summary?.motivation, !motivation.isEmpty {
                    summaryRow("이유", motivation) { openEdit(.motivation) }
                    Divider().overlay(Color.borderSoft)
                }
                summaryRow("실천 시간", summary?.preferenceText) { openEdit(.practicePreference) }
                Divider().overlay(Color.borderSoft)
                summaryRow("꼭 할 일", (session.mustDoItems ?? []).isEmpty ? nil : (session.mustDoItems ?? []).joined(separator: " · ")) {
                    saveError = nil
                    mustDoDraft = session.mustDoItems ?? []
                    mustDoText = ""
                    trackSlotOpen("must_do", wasEmpty: (session.mustDoItems ?? []).isEmpty)
                    mustDoOpen = true
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: Radius.card))
            .overlay(RoundedRectangle(cornerRadius: Radius.card).stroke(Color.line, lineWidth: 1))
        }
    }

    private func summaryRow(_ label: String, _ value: String?, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 16) {
                Text(label).font(.pt(13)).foregroundStyle(Color.ink3).frame(width: 64, alignment: .leading)
                Text(value ?? "AI가 알맞게 정해줄게요")
                    .font(.pt(15, value == nil ? .regular : .semibold))
                    .foregroundStyle(value == nil ? Color.ink4 : Color.ink)
                    .lineSpacing(3)
                    .multilineTextAlignment(.leading)
                    .frame(maxWidth: .infinity, alignment: .leading)
                NSymbol("chevron.right", size: 13, weight: .semibold).foregroundStyle(Color.ink4)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 16)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    // MARK: 실행 성향 · 계획 강도

    private static func styleDescription(_ s: PlanningStyle) -> String {
        switch s {
        case .PLANNER: return "요일·시간을 정해 매일 조금씩"
        case .SPONTANEOUS: return "여유 있게, 몰아서 해도 괜찮게"
        }
    }

    private func sectionTitle(_ title: String, aside: String?) -> some View {
        HStack {
            Text(title).font(.pt(15, .bold)).foregroundStyle(Color.ink)
            Spacer(minLength: 8)
            if let aside { Text(aside).font(.pt(12)).foregroundStyle(Color.ink3) }
        }
    }

    private var styleSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            sectionTitle("실행 성향", aside: styleByUser ? nil : "대화를 바탕으로 AI가 골랐어요")
            HStack(spacing: 12) {
                ForEach([PlanningStyle.PLANNER, .SPONTANEOUS], id: \.self) { value in
                    GoalCreationChoiceCard(title: value.goalCreationLabel, badge: nil,
                                           description: Self.styleDescription(value),
                                           selected: style == value) {
                        GCTracker.shared.track(GCEvent.styleSelect, [
                            "session_id": sessionId, "planning_style": value.rawValue,
                            "ai_style": summary?.planningStyle?.rawValue,
                        ])
                        chooseStyle(value)
                    }
                }
            }
            // 두 카드 높이를 같게
            .fixedSize(horizontal: false, vertical: true)
        }
    }

    private var intensitySection: some View {
        VStack(alignment: .leading, spacing: 12) {
            sectionTitle("계획 강도", aside: "고르지 않으면 안정형으로 만들어요")
            HStack(spacing: 12) {
                ForEach(GoalCreationPlanIntensity.allCases, id: \.self) { value in
                    GoalCreationChoiceCard(title: value.label, badge: nil,
                                           description: value.descriptionText,
                                           selected: intensity == value) {
                        GCTracker.shared.track(GCEvent.intensitySelect, ["session_id": sessionId, "plan_intensity": value.rawValue])
                        intensity = value
                    }
                }
            }
            .fixedSize(horizontal: false, vertical: true)
        }
    }

    // MARK: 하단 버튼

    private var bottomBar: some View {
        VStack(spacing: 0) {
            Divider().overlay(Color.borderSoft)
            NButton(regenerate ? "이 정보로 다시 만들기" : "계획 만들기", variant: .ai, size: .lg,
                    icon: generating ? nil : "sparkles", loading: generating, fullWidth: true) {
                Task { await generate() }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
        }
        .background(Color.surface)
    }

    // MARK: 꼭 할 일 시트

    private var mustDoSheet: some View {
        SheetScaffold(title: "꼭 하고 싶은 일", description: "고른 일은 계획에 꼭 루틴으로 넣어 드려요.", dismissible: !saving) {
            VStack(alignment: .leading, spacing: 8) {
                GoalCreationMustDoPicker(value: $mustDoDraft, draft: $mustDoText, disabled: saving, surface: "goal_review")
                if let saveError { Text(saveError).font(.pt(13)).foregroundStyle(Color.danger) }
            }
        } footer: {
            NButton("취소", variant: .secondary) {
                GCTracker.shared.sheetClose = "cancel_button"
                mustDoOpen = false
            }
                .disabled(saving)
            NButton("저장", loading: saving) {
                GCTracker.shared.track(GCEvent.slotEditSave, ["session_id": sessionId, "slot_key": "must_do"])
                let items = GoalCreationMustDo.withDraft(mustDoDraft, mustDoText)
                Task { await save(GoalCreationSlotsPatch(mustDoItems: items), slotKey: "must_do") }
            }
        }
    }

    // MARK: 동작

    @MainActor
    private func openEdit(_ slot: GoalCreationSlotKey) {
        saveError = nil
        trackSlotOpen(slot.rawValue, wasEmpty: isEmpty(slot))
        editing = slot
    }

    @MainActor
    private func chooseStyle(_ next: PlanningStyle) {
        style = next
        styleByUser = true
        GoalCreationStyleMemory.markSelectedByUser(sessionId)
    }

    @MainActor
    private func save(_ patch: GoalCreationSlotsPatch, slotKey: String) async {
        saving = true
        saveError = nil
        defer { saving = false }
        do {
            let turn = try await GoalCreationAPI.updateSlots(sessionId, patch)
            GCTracker.shared.track(GCEvent.slotEditResult, ["session_id": sessionId, "slot_key": slotKey, "result": "success"])
            GCTracker.shared.sheetClose = "saved"
            // 화면 데이터를 응답으로 바로 갱신 → 다시 불러오지 않아도 요약이 바뀌어요
            session.status = turn.status
            session.slots = turn.slots
            session.summary = turn.summary
            session.mustDoItems = turn.mustDoItems
            editing = nil
            mustDoOpen = false
            Toast.success("수정했어요.")
        } catch {
            GCTracker.shared.track(GCEvent.slotEditResult, [
                "session_id": sessionId, "slot_key": slotKey, "result": "fail", "error_status": gcErrorStatus(error),
            ])
            if let e = error as? APIError, e.httpStatus == 409 {
                // 이미 생성이 시작됐거나 끝난 세션 → 서버 상태로 화면 이동 (시트는 저장 실패로 닫혀요 — 취소로 세지 않게)
                GCTracker.shared.sheetClose = "saved"
                Toast.show(e.errorDescription ?? "")
                editing = nil
                mustDoOpen = false
                await reload()
                return
            }
            saveError = errorMessage(error, fallback: "저장하지 못했어요. 다시 시도해 주세요.")
        }
    }

    @MainActor
    private func generate() async {
        if generating { return }
        GCTracker.shared.track(GCEvent.generateClick, [
            "session_id": sessionId, "planning_style": style.rawValue, "style_by_user": styleByUser,
            "plan_intensity": (intensity ?? .STEADY).rawValue, "intensity_by_user": intensity != nil,
            "is_regenerate": regenerate,
        ])
        generating = true
        do {
            // 성향은 사용자가 직접 골랐을 때만 보내요 (안 보내면 서버가 대화에서 추정한 값 사용)
            let res = try await GoalCreationAPI.requestDraft(
                sessionId, GoalCreationDraftRequestBody(planningStyle: styleByUser ? style : nil, intensity: intensity ?? .STEADY))
            GCTracker.shared.track(GCEvent.generateResult, ["session_id": sessionId, "result": "success", "draft_id": res.draftId])
            Invalidation.shared.invalidate(.usage)
            GCTracker.shared.setGenerateSource("review")
            nav.go(.generating(sessionId, draftId: res.draftId))
        } catch {
            generating = false
            GCTracker.shared.track(GCEvent.generateResult, [
                "session_id": sessionId, "result": "fail", "error_status": gcErrorStatus(error),
            ])
            if let e = error as? APIError, e.httpStatus == 409 {
                // 이미 생성 중 (다른 기기에서 눌렀거나 두 번 누름) → 생성 중 화면으로
                GCTracker.shared.setGenerateSource("review")
                nav.go(.generating(sessionId, draftId: nil))
                return
            }
            Toast.error(errorMessage(error, fallback: "계획을 만들지 못했어요. 다시 시도해 주세요."))
        }
    }
}

/// 둘 중 하나 고르는 카드 (실행 성향 · 계획 강도)
struct GoalCreationChoiceCard: View {
    let title: String
    let badge: String?
    let description: String
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 6) {
                    Text(title).font(.pt(15, .bold)).foregroundStyle(selected ? Color.textBrand : Color.ink)
                    if let badge { Text(badge).font(.pt(12, .medium)).foregroundStyle(Color.ink3) }
                }
                Text(description).font(.pt(13)).foregroundStyle(Color.ink3)
                    .multilineTextAlignment(.leading)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .padding(16)
            .background(selected ? Color.brandSoft : Color.surface, in: RoundedRectangle(cornerRadius: Radius.xxl))
            .overlay(RoundedRectangle(cornerRadius: Radius.xxl).stroke(selected ? Color.brand : Color.borderSubtle, lineWidth: 1))
            .contentShape(RoundedRectangle(cornerRadius: Radius.xxl))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

#Preview {
    NavigationStack {
        GoalCreationReviewView(session: GoalCreationSamples.session, reload: {})
    }
    .environment(GoalCreationNavigator(route: .review(101)))
}
