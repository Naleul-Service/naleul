// Features/Home/HomeRetroSheet.swift
//
// 오늘 회고 시트 — 웹 features/home/ui/TodayRetro.tsx RetroPanel.
// 목표를 하나씩 고를 필요 없이 입력칸 하나로 써요.
//  - 오늘 할 일이 있던 목표 중 아직 회고가 없는 것부터 자동으로 골라 둬요
//  - 키보드의 "다음"(리턴)으로 저장하면 다음 목표로 넘어가요 (건너뛰기도 가능)
//    웹은 Shift+Enter 줄바꿈이 있지만 아이폰 키보드에는 없어서 한 덩어리로 써요
//  - 위의 목표 칩을 눌러 직접 고를 수도 있어요 (✓ = 오늘 회고 있음)
//  - 목표형: 목표 상세 "오늘 기록"과 같은 칸 (수치 + 어땠어요 + 한 줄 회고, HomeCheckInForm) — 어디서 쓰든 같은 기록
//  - 업무형: 지금처럼 회고 글
// 저장 · 수정 · 삭제 후 .home 을 다시 불러오면 HomeViewModel 의 goals 가 바뀌어 ✓ 가 따라와요.
// 위의 "지난 회고 모아보기"로 날짜별 회고 모음(ReflectionsScreen)을 열어요.

import SwiftUI
import UIKit

struct HomeRetroSheet: View {
    let vm: HomeViewModel
    let today: String
    /// 연 경로 — button · push (home.retro_panel_view 의 entry_point)
    var entryPoint: String = "button"

    @State private var picked: Int?
    @State private var finished = false
    /// 저장 직후 다시 불러오기 전에도 "썼다"로 보이게
    @State private var written: Set<Int> = []
    @State private var text = ""
    @State private var showIdle = false
    @State private var saving = false
    @FocusState private var inputFocused: Bool
    @Environment(\.dismiss) private var dismiss
    @Environment(AppRouter.self) private var router
    /// 이벤트: 열린 시각 · 닫힌 방법 · 글을 쓰기 시작한 목표
    @State private var openedAt = Date()
    @State private var closeMethod: String?
    @State private var startedInput: Set<Int> = []

    private var goals: [HomeGoalToday] { vm.home?.goals ?? [] }

    var body: some View {
        SheetScaffold(title: "오늘 회고", description: "오늘 한 일을 보면서 목표마다 한 줄씩", detents: [.large]) {
            content
        }
        .onAppear {
            openedAt = Date()
            let active = goals.filter { $0.activeToday }
            HomeTracker.shared.system(HomeEvent.retroPanelView, [
                "view_date": today, "date_relation": HomeTracker.relation(today),
                "goal_count": goals.count, "active_goal_count": active.count,
                "retro_done_count": active.filter { hasRetro($0) }.count, "entry_point": entryPoint,
            ])
        }
        .onDisappear {
            let draft = text.trimmingCharacters(in: .whitespacesAndNewlines)
            if !draft.isEmpty {
                HomeTracker.shared.system(HomeEvent.retroDraftDiscard, [
                    "discard_reason": closeMethod ?? "sheet_close", "content_length": draft.count,
                    "form_type": "retro", "goal_category_id": current?.goalCategoryId,
                ])
            }
            HomeTracker.shared.track(HomeEvent.retroPanelClose, [
                "close_method": closeMethod ?? "swipe_down", "written_count": written.count,
                "open_duration_seconds": Int(Date().timeIntervalSince(openedAt)),
            ])
        }
    }

    @ViewBuilder
    private var content: some View {
        if vm.homeFailed {
            Text("회고할 목표를 불러오지 못했어요.").font(.pt(14)).foregroundStyle(Color.ink3)
        } else if vm.home == nil {
            RoundedRectangle(cornerRadius: Radius.card).fill(Color.subtle).frame(height: 240)
        } else if goals.isEmpty {
            Text("진행 중인 목표가 없어요.").font(.pt(14)).foregroundStyle(Color.ink3)
        } else {
            panel
        }
    }

    // MARK: 고르기

    private func hasRetro(_ g: HomeGoalToday) -> Bool {
        g.hasRecord || written.contains(g.goalCategoryId)
    }

    /// 아직 회고를 안 쓴 다음 목표 (오늘 할 일이 있던 목표 순서로 돌아가며, 지금 목표는 빼고)
    private func nextGoal(after: Int?, written w: Set<Int>) -> HomeGoalToday? {
        let order = goals.filter { $0.activeToday }
        var start = -1
        if let after, let i = order.firstIndex(where: { $0.goalCategoryId == after }) { start = i }
        let rest = Array(order[(start + 1)...]) + Array(order[0..<max(start, 0)])
        return rest.first { g in
            !g.hasRecord && !w.contains(g.goalCategoryId) && g.goalCategoryId != after
        }
    }

    private var current: HomeGoalToday? {
        if finished { return nil }
        if let picked, let g = goals.first(where: { $0.goalCategoryId == picked }) { return g }
        return nextGoal(after: nil, written: written) ?? goals.first(where: { $0.activeToday }) ?? goals.first
    }

    private func pick(_ id: Int) {
        if let g = goals.first(where: { $0.goalCategoryId == id }) {
            HomeTracker.shared.track(HomeEvent.retroGoalChipClick, [
                "goal_category_id": id, "goal_mode": g.goalMode?.rawValue, "has_record": hasRetro(g),
                "is_active_today": g.activeToday, "content_length": text.count,
            ])
        }
        UISelectionFeedbackGenerator().selectionChanged()
        picked = id
        finished = false
        inputFocused = true
    }

    private func goNext(from: Int, justWrote: Bool) {
        var w = written
        if justWrote { w.insert(from) }
        if let n = nextGoal(after: from, written: w) {
            picked = n.goalCategoryId
            inputFocused = true
        } else {
            picked = nil
            finished = true
            inputFocused = false
        }
    }

    /// method — button · return_key
    private func submit(method: String = "button") {
        let v = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !v.isEmpty, let g = current, !saving else { return }
        let id = g.goalCategoryId
        HomeTracker.shared.track(HomeEvent.retroSaveClick, [
            "goal_category_id": id, "goal_mode": g.goalMode?.rawValue, "submit_method": method,
            "content_length": v.count, "is_auto_selected": picked == nil,
            "view_date": today, "date_relation": HomeTracker.relation(today),
        ])
        saving = true
        Task { @MainActor in
            let ok = await Mutation.run(invalidate: [.home, .work], successMessage: "회고를 남겼어요.") {
                try await HomeAPI.createRetro(goalId: id, content: v, date: today)
            }
            HomeTracker.shared.system(HomeEvent.retroSaveResult, [
                "retro_action": "create", "result": ok == nil ? "fail" : "success", "goal_category_id": id,
            ])
            saving = false
            if ok != nil {
                text = ""
                written.insert(id)
                goNext(from: id, justWrote: true)
            }
        }
    }

    // MARK: 화면

    private var panel: some View {
        let active = goals.filter { $0.activeToday }
        let idle = goals.filter { !$0.activeToday }
        let doneCount = active.filter { hasRetro($0) }.count
        let cur = current
        return VStack(alignment: .leading, spacing: 16) {
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 8) {
                    if !active.isEmpty {
                        Text("\(doneCount) / \(active.count) 목표")
                            .font(.ptMono(12))
                            .foregroundStyle(Color.ink3)
                    }
                    Spacer(minLength: 0)
                    Button {
                        HomeTracker.shared.track(HomeEvent.retroReflectionsLinkClick, ["view_date": today])
                        closeMethod = "reflections_link"
                        dismiss()
                        RF.pendingEntry = "home_retro"
                        router.openReflections()
                    } label: {
                        Label("지난 회고 모아보기", systemImage: "books.vertical")
                            .font(.pt(12, .semibold))
                            .foregroundStyle(Color.textBrand)
                            .frame(minHeight: 32)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
                // 목표 고르기 — 자동으로 골라 두지만 눌러서 바꿀 수 있어요
                FlowLayout(spacing: 6) {
                    ForEach(active.isEmpty ? idle : active) { g in chip(g, selected: cur?.goalCategoryId == g.goalCategoryId) }
                    if !active.isEmpty && !idle.isEmpty && !showIdle {
                        Button {
                            HomeTracker.shared.track(HomeEvent.retroMoreGoalsClick, ["item_count": idle.count])
                            showIdle = true
                        } label: {
                            Text("+ 다른 목표 \(idle.count)")
                                .font(.pt(12, .semibold))
                                .foregroundStyle(Color.ink3)
                                .padding(.horizontal, 6)
                                .frame(height: 32)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                    if !active.isEmpty && showIdle {
                        ForEach(idle) { g in chip(g, selected: cur?.goalCategoryId == g.goalCategoryId) }
                    }
                }
            }

            if let g = cur {
                editor(g)
            } else {
                VStack(spacing: 4) {
                    Text("오늘 회고를 모두 남겼어요 🎉").font(.pt(15, .bold)).foregroundStyle(Color.ink)
                        .onAppear {
                            HomeTracker.shared.system(HomeEvent.retroAllDoneView, [
                                "written_count": written.count, "active_goal_count": active.count,
                            ])
                        }
                    Text("더 쓰고 싶은 목표가 있으면 위에서 골라 주세요.")
                        .font(.pt(13))
                        .foregroundStyle(Color.ink3)
                        .multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 20)
            }
        }
    }

    private func chip(_ g: HomeGoalToday, selected: Bool) -> some View {
        Button { pick(g.goalCategoryId) } label: {
            HStack(spacing: 4) {
                if hasRetro(g) {
                    NSymbol("checkmark", size: 11, weight: .heavy)
                        .foregroundStyle(Color.success)
                } else {
                    Circle().fill(Color.goal(g.colorCode)).frame(width: 8, height: 8)
                }
                Text(g.title).lineLimit(1)
            }
            .font(.pt(12, .semibold))
            .foregroundStyle(selected ? Color.actionPrimaryFg : Color.ink2)
            .padding(.horizontal, 10)
            .frame(height: 32)
            .background(selected ? Color.actionPrimary : Color.surface, in: Capsule())
            .overlay(Capsule().stroke(selected ? Color.actionPrimary : Color.borderSubtle, lineWidth: 1))
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(g.goalName + (hasRetro(g) ? ", 회고 있음" : ""))
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

    private func editor(_ g: HomeGoalToday) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 6) {
                Circle().fill(Color.goal(g.colorCode)).frame(width: 8, height: 8)
                Text(g.goalName).font(.pt(13, .bold)).foregroundStyle(Color.ink).lineLimit(1)
                if g.planned + g.done > 0 {
                    Text("· 오늘 \(g.done)\(g.planned > 0 ? "/\(g.planned)" : "")개 끝냄")
                        .font(.pt(12))
                        .foregroundStyle(Color.ink3)
                        .monospacedDigit()
                        .lineLimit(1)
                }
            }

            if !g.doneTasks.isEmpty || !g.openTasks.isEmpty {
                FlowLayout(spacing: 4) {
                    ForEach(Array(g.doneTasks.enumerated()), id: \.offset) { _, name in
                        HStack(spacing: 3) {
                            NSymbol("checkmark", size: 9, weight: .heavy)
                            Text(name).lineLimit(1)
                        }
                        .font(.pt(11, .semibold))
                        .foregroundStyle(Color.success)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(Color.successSoft, in: Capsule())
                    }
                    ForEach(Array(g.openTasks.enumerated()), id: \.offset) { _, name in
                        Text(name)
                            .font(.pt(11, .medium))
                            .foregroundStyle(Color.ink3)
                            .lineLimit(1)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(Color.subtle, in: Capsule())
                            .accessibilityLabel("오늘 못 한 일: \(name)")
                    }
                }
            }

            if !g.retros.isEmpty {
                VStack(spacing: 6) {
                    ForEach(g.retros) { r in HomeRetroItem(retro: r, goalId: g.goalCategoryId) }
                }
            }

            if today > KST.today() {
                // 앞날은 아직 기록할 수 없어요 (목표형 기록은 서버도 미래 날짜를 막아요)
                Text("앞날은 아직 기록할 수 없어요. 그날이 되면 남겨 주세요.")
                    .font(.pt(13))
                    .foregroundStyle(Color.ink3)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(12)
                    .background(Color.subtle, in: RoundedRectangle(cornerRadius: Radius.xl))
                    .onAppear { HomeTracker.shared.system(HomeEvent.retroFutureBlockedView, ["view_date": today]) }
            } else if let c = g.checkIn {
                HomeCheckInForm(goal: g, checkIn: c, date: today,
                                onSaved: {
                                    written.insert(g.goalCategoryId)
                                    goNext(from: g.goalCategoryId, justWrote: true)
                                },
                                onSkip: {
                                    HomeTracker.shared.track(HomeEvent.retroSkipClick, [
                                        "goal_category_id": g.goalCategoryId, "form_type": "checkin",
                                    ])
                                    goNext(from: g.goalCategoryId, justWrote: false)
                                })
                    .id("\(g.goalCategoryId)-\(today)")
            } else {
                retroEditor(g)
            }
        }
    }

    /// 업무형: 회고 글
    @ViewBuilder
    private func retroEditor(_ g: HomeGoalToday) -> some View {
        TextField(g.prompt, text: $text, axis: .vertical)
            .font(.pt(14))
            .foregroundStyle(Color.ink)
            .tint(Color.brand)
            .lineLimit(3...8)
            .submitLabel(.next)
            .focused($inputFocused)
            .onSubmit { submit(method: "return_key") }
            // 세로로 늘어나는 입력칸은 리턴 키가 줄바꿈으로 들어와서, 그걸 "저장하고 다음"으로 바꿔요
            .onChange(of: text) { _, v in
                if v.hasSuffix("\n") {
                    text = String(v.dropLast())
                    submit(method: "return_key")
                    return
                }
                // 목표마다 처음 글자를 칠 때 한 번 (home.retro_input_start)
                if !v.isEmpty, !startedInput.contains(g.goalCategoryId) {
                    startedInput.insert(g.goalCategoryId)
                    HomeTracker.shared.track(HomeEvent.retroInputStart, [
                        "goal_category_id": g.goalCategoryId, "form_type": "retro", "goal_mode": g.goalMode?.rawValue,
                    ])
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .frame(minHeight: 96, alignment: .topLeading)
            .background(Color.surface, in: RoundedRectangle(cornerRadius: Radius.xl))
            .overlay(
                RoundedRectangle(cornerRadius: Radius.xl)
                    .stroke(inputFocused ? Color.borderFocus : Color.borderSubtle, lineWidth: inputFocused ? 1.5 : 1)
            )
            .accessibilityLabel("\(g.goalName) 오늘 회고")

        HStack(spacing: 8) {
            NButton("건너뛰기", variant: .ghost, size: .sm) {
                HomeTracker.shared.track(HomeEvent.retroSkipClick, [
                    "goal_category_id": g.goalCategoryId, "form_type": "retro", "content_length": text.count,
                ])
                goNext(from: g.goalCategoryId, justWrote: false)
            }
            Spacer(minLength: 0)
            Button { submit(method: "button") } label: {
                HStack(spacing: 6) {
                    if saving {
                        ProgressView().controlSize(.small).tint(Color.onInk)
                    }
                    Text("저장하고 다음")
                    NSymbol("arrow.right", size: 12, weight: .semibold)
                }
            }
            .buttonStyle(.n(.primary, .sm))
            .disabled(saving || text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
        }
        Text("키보드의 다음 버튼으로 저장하고 다음 목표로 넘어가요.")
            .font(.pt(11))
            .foregroundStyle(Color.ink4)
    }
}

// MARK: - 이미 쓴 회고 (고치기 · 지우기)

private struct HomeRetroItem: View {
    let retro: HomeRetro
    var goalId: Int? = nil

    @State private var editing = false
    @State private var value = ""
    @State private var busy = false

    var body: some View {
        if editing {
            VStack(alignment: .trailing, spacing: 6) {
                TextEditor(text: $value)
                    .frame(minHeight: 72)
                    .inputStyle(minHeight: 72)
                    .accessibilityLabel("회고 고치기")
                HStack(spacing: 8) {
                    NButton("취소", variant: .ghost, size: .sm) {
                        HomeTracker.shared.track(HomeEvent.retroEditCancelClick, ["close_method": "cancel_button"])
                        editing = false
                    }
                    NButton("저장", size: .sm, loading: busy) {
                        HomeTracker.shared.track(HomeEvent.retroEditSaveClick, [
                            "content_length": value.trimmingCharacters(in: .whitespacesAndNewlines).count,
                        ])
                        save()
                    }
                        .disabled(value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        } else {
            HStack(alignment: .top, spacing: 8) {
                Text(retro.content)
                    .font(.pt(13))
                    .foregroundStyle(Color.ink)
                    .lineSpacing(3)
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(maxWidth: .infinity, alignment: .leading)
                HStack(spacing: 0) {
                    IconButton(systemName: "pencil", size: 13, color: .ink4, label: "회고 고치기") {
                        HomeTracker.shared.track(HomeEvent.retroEditClick, ["goal_category_id": goalId])
                        value = retro.content
                        editing = true
                    }
                    IconButton(systemName: "trash", size: 13, color: .ink4, label: "회고 지우기") {
                        HomeTracker.shared.track(HomeEvent.retroDeleteClick, ["goal_category_id": goalId])
                        remove()
                    }
                        .disabled(busy)
                }
                .padding(.top, -6)
                .padding(.trailing, -6)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(Color.subtle.opacity(0.7), in: RoundedRectangle(cornerRadius: Radius.xl))
        }
    }

    private func save() {
        let v = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !v.isEmpty, !busy else { return }
        busy = true
        let id = retro.retrospectiveId
        Task { @MainActor in
            let ok = await Mutation.run(invalidate: [.home, .work], successMessage: "회고를 고쳤어요.") {
                try await WorkAPI.updateRetro(retrospectiveId: id, content: v)
            }
            HomeTracker.shared.system(HomeEvent.retroSaveResult, [
                "retro_action": "update", "result": ok == nil ? "fail" : "success", "goal_category_id": goalId,
            ])
            busy = false
            if ok != nil { editing = false }
        }
    }

    private func remove() {
        guard !busy else { return }
        busy = true
        let id = retro.retrospectiveId
        Task { @MainActor in
            let ok = await Mutation.run(invalidate: [.home, .work], successMessage: "회고를 지웠어요.") {
                try await WorkAPI.deleteRetro(retrospectiveId: id)
            }
            HomeTracker.shared.system(HomeEvent.retroSaveResult, [
                "retro_action": "delete", "result": ok == nil ? "fail" : "success", "goal_category_id": goalId,
            ])
            busy = false
        }
    }
}

// MARK: - 미리보기

#Preview("오늘 회고") {
    Color.canvas
        .sheet(isPresented: .constant(true)) {
            HomeRetroSheet(vm: .preview(), today: KST.today())
                .environment(AppRouter.shared)
        }
}

#Preview("오늘 회고 — 불러오기 실패") {
    Color.canvas
        .sheet(isPresented: .constant(true)) {
            HomeRetroSheet(vm: .preview(homeFailed: true), today: KST.today())
                .environment(AppRouter.shared)
        }
}
