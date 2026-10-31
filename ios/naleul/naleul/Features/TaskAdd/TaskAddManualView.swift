// Features/TaskAdd/TaskAddManualView.swift
//
// 웹 원본: features/brain-dump/ui/ManualTaskAdd.tsx
//
// Task 직접 추가 — 목표를 고르고, 이름·날짜·시간(선택)·영역(선택)을 정해 저장.
// 시간을 비워 두면 그날 "시간 미정"으로 들어가고, 바로 "AI로 빈 시간에 배치하기"를 누를 수 있어요.
// 입력 폼은 Goal 작업자의 GoalTaskFormView (웹 goal/ui/GoalTaskForm) 를 그대로 써요.
// 시트(웹 panel) 안이라 "캘린더에서 보기" 버튼은 숨겨요 (웹 inPanel).

import SwiftUI

@MainActor
struct TaskAddManualView: View {
    /// 진행 중 · 시작 전 목표 (목록이 아직이면 loading)
    let goals: [GoalCategory]
    let loading: Bool
    /// 목표 목록을 못 불러왔는지 (no_goal_view 의 goal_list_state)
    var goalsFailed = false
    /// 새로 만든 Task — 캘린더가 그 날짜로 옮기고 NEW 를 붙여요
    let onCreated: (TimeBlockTask) -> Void
    /// "목표 만들기" 전에 시트를 닫아요
    let closeSheet: () -> Void
    /// 폼에 적던 내용이 있는지 (Task 추가 시트의 has_input)
    var onInputChange: (Bool) -> Void = { _ in }
    /// 캘린더 빈 칸에서 열었을 때 미리 채울 날짜 · 시각
    var prefill: TaskAddPrefill? = nil

    @State private var goalId: Int? = nil
    @State private var goalDetail: GoalCategory? = nil
    @State private var formKey = 0
    @State private var added: [TimeBlockTask] = []
    /// AI 배치를 요청한 Task (시간은 캘린더에서 확인)
    @State private var placed: Set<Int> = []
    @State private var filling = false
    @State private var hasInput = false

    private var selectedId: Int? { goalId ?? goals.first?.goalCategoryId }
    private var selectedGoal: GoalCategory? { goals.first { $0.goalCategoryId == selectedId } }

    private var unplaced: [TimeBlockTask] {
        added.filter { $0.plannedStartAt == nil && !placed.contains($0.taskId) }
    }
    private var unplacedDates: [String] {
        Array(Set(unplaced.compactMap { $0.date ?? $0.scheduledDate })).sorted()
    }

    var body: some View {
        if loading {
            LoadingView(minHeight: 200).card()
        } else if goals.isEmpty {
            emptyCard
        } else {
            formCard
        }
    }

    // MARK: 목표가 없을 때

    private var emptyCard: some View {
        VStack(spacing: 4) {
            Text("Task를 연결할 목표가 없어요")
                .font(.pt(15, .bold))
                .foregroundStyle(Color.ink)
            Text("목표를 먼저 만들거나, AI로 정리하기를 쓰면 임시 목표가 자동으로 만들어져요.")
                .font(.pt(14))
                .foregroundStyle(Color.ink3)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
            NButton("목표 만들기") {
                TA.track("task_add_manual.goal_create_click")
                closeSheet()
                AppRouter.shared.openGoalAdd()
            }
            .padding(.top, 16)
        }
        .padding(32)
        .frame(maxWidth: .infinity)
        .card()
        .onAppear {
            TA.track("task_add_manual.no_goal_view", ["goal_list_state": goalsFailed ? "error" : "empty"])
        }
    }

    // MARK: 폼

    private var formCard: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("직접 추가하기")
                .font(.pt(16, .semibold))
                .foregroundStyle(Color.ink)
            Text("이름과 날짜만 넣고 Enter. 시간을 비워 두면 그날 빈 시간에 AI가 배치할 수 있어요.")
                .font(.pt(14))
                .foregroundStyle(Color.ink3)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 4)

            VStack(alignment: .leading, spacing: 4) {
                Text("목표").font(.pt(12, .medium)).foregroundStyle(Color.ink3)
                BrainDumpMenuField(text: selectedGoal.map(goalTitle) ?? "", isPlaceholder: false) {
                    ForEach(goals) { g in
                        Button(goalTitle(g)) {
                            TA.track("task_add_manual.goal_select", [
                                "goal_category_id": g.goalCategoryId, "is_temporary": g.isTemporary, "has_input": hasInput,
                                "is_habit_goal": g.isHabitGoal,
                            ])
                            goalId = g.goalCategoryId
                            formKey += 1
                            setHasInput(false)
                        }
                    }
                }
            }
            .padding(.top, 16)

            Group {
                if let detail = goalDetail, detail.goalCategoryId == selectedId {
                    GoalTaskFormView(
                        goal: detail,
                        task: nil,
                        // 저장하면 폼을 비우고 다음 Task 를 바로 적을 수 있게
                        onDone: {
                            formKey += 1
                            setHasInput(false)
                        },
                        onCreated: { t in
                            added.insert(t, at: 0)
                            onCreated(t)
                        },
                        // 폼 안 동작은 goal_task_list.* (surface=task_add_manual). 폼이 다시 만들어질 때(목표 변경 · 저장)
                        // 생기는 '취소'는 사용자가 닫은 게 아니라서 edit_cancel 은 안 보내요
                        analyticsSurface: "task_add_manual",
                        tracksCancel: false,
                        onDirtyChange: { setHasInput($0) },
                        prefill: prefill
                    )
                    .id("\(detail.goalCategoryId)-\(formKey)")
                } else {
                    LoadingView(minHeight: 160)
                }
            }
            .padding(.top, 12)

            if !added.isEmpty { addedList }
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card()
        // 목표를 바꾸면 그 목표의 상세(영역 · 마일스톤 포함)를 불러와요 (웹 useGoalCategory)
        .reloadOn([.goals], id: selectedId) { await loadDetail() }
    }

    private var addedList: some View {
        VStack(alignment: .leading, spacing: 0) {
            Divider().overlay(Color.borderSoft)
            Text("방금 추가한 Task \(added.count)")
                .font(.pt(12, .semibold))
                .foregroundStyle(Color.ink3)
                .padding(.top, 16)
            VStack(alignment: .leading, spacing: 6) {
                ForEach(added) { t in
                    HStack(spacing: 8) {
                        NSymbol("checkmark", size: 12, weight: .bold)
                            .foregroundStyle(Color.success)
                        Text((t.emoji.map { "\($0) " } ?? "") + t.taskName)
                            .font(.pt(14, .medium))
                            .foregroundStyle(Color.ink)
                            .lineLimit(1)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        Text(whenText(t))
                            .font(.pt(12))
                            .foregroundStyle(Color.ink3)
                            .lineLimit(1)
                    }
                }
            }
            .padding(.top, 8)
            if !unplacedDates.isEmpty {
                NButton("시간 미정 \(unplaced.count)개 AI로 빈 시간에 배치하기", variant: .ai, size: .sm,
                        icon: "sparkles", loading: filling) {
                    Task { await fill() }
                }
                .padding(.top, 12)
            }
        }
        .padding(.top, 20)
    }

    // MARK: 도움 함수

    private func setHasInput(_ v: Bool) {
        hasInput = v
        onInputChange(v)
    }

    private func goalTitle(_ g: GoalCategory) -> String {
        (g.emoji.map { "\($0) " } ?? "") + g.goalCategoryName + (g.isTemporary ? " (임시)" : "")
    }

    private func whenText(_ t: TimeBlockTask) -> String {
        let day = String((t.plannedStartAt ?? t.date ?? "").prefix(10))
        let d = day.isEmpty ? (t.scheduledDate ?? "") : day
        let dayLabel = KST.isYmd(d) ? KST.formatMonthDay(d) : ""
        let time: String
        if let s = t.plannedStartAt {
            time = "\(KST.hm(s))–\(KST.hm(t.plannedEndAt))"
        } else if placed.contains(t.taskId) {
            time = "AI가 빈 시간에 배치했어요"
        } else {
            time = "시간 미정"
        }
        return "\(dayLabel) · \(time)"
    }

    private func loadDetail() async {
        guard let id = selectedId else { return }
        do {
            if let g = try await GoalAPI.goal(id) { goalDetail = g }
        } catch is CancellationError {
            return
        } catch {
            // 상세를 못 읽으면 목록에 있는 정보로라도 폼을 보여 줘요
            if goalDetail?.goalCategoryId != id { goalDetail = selectedGoal }
        }
    }

    /// 웹 useFillTimetable — 시간 미정 Task 를 그 날짜들의 빈 시간에 바로 배치
    private func fill() async {
        let sorted = unplacedDates
        guard let first = sorted.first, let last = sorted.last else { return }
        let ids = unplaced.map(\.taskId)
        TA.track("task_add_manual.ai_fill_click", ["unscheduled_count": ids.count, "day_count": KST.diffDays(first, last) + 1])
        filling = true
        defer { filling = false }
        do {
            let r = try await TimetableAPI.fill(startDate: first, endDate: last)
            TA.track("task_add_manual.ai_fill_result", ["result": "success"])
            TimetableAPI.toastFillResult(r)
            placed.formUnion(ids)
        } catch is CancellationError {
            // 화면을 닫았어요
        } catch {
            TA.track("task_add_manual.ai_fill_result", ["result": "fail", "error_status": TA.status(error)])
            Toast.error(errorMessage(error))
        }
        Invalidation.shared.invalidate(.timetable, .goals)
    }
}

#Preview {
    ScrollView {
        TaskAddManualView(goals: [], loading: false, onCreated: { _ in }, closeSheet: {})
            .padding(16)
    }
    .canvasBackground()
}
