// Features/Goal/GoalListScreen.swift
//
// 웹 app/(main)/goal/page.tsx + features/goal/ui/GoalListView.tsx — 목표 탭 첫 화면 (내 목표 목록).
// 임시 목표 · 진행 중 · 완료 순서로 보여줘요. 목표가 하나도 없으면 "첫 목표를 만들어 보세요".
//
// 웹 사이드바의 "내 목표" 목록(GoalNavList)은 아이폰엔 사이드바가 없어서 이 화면이 그 역할을 해요.

import SwiftUI
import Observation

@MainActor
@Observable
final class GoalListViewModel {
    var phase: LoadPhase<[GoalCategory]> = .loading
    /// 업무형 목표의 연속 완료 주 (목표 id → 값). 아직 못 받았으면 없음
    var streaks: [Int: GoalWorkStreak] = [:]
    var isPreview = false
    /// 이어서 만들기 카드가 세션을 다 불러왔는지 (pageview 의 has_resume)
    var hasResume: Bool?
    private var pageviewSent = false

    func load() async {
        if isPreview { return }
        do {
            let goals = try await GoalAPI.goals()
            phase = .loaded(goals)
            trackPageviewIfReady()
            // 업무형 카드의 "연속 완료"는 목표마다 따로 받아요 (웹 useRecordPattern)
            for g in goals where g.isRecordGoal {
                if let s = try? await GoalEditAPI.workStreak(goalId: g.goalCategoryId) {
                    streaks[g.goalCategoryId] = s
                }
            }
        } catch is CancellationError {
            return
        } catch {
            if phase.value == nil {
                phase = .failed(errorMessage(error))
                lastErrorStatus = (error as? APIError)?.httpStatus ?? 0
                GD.track("goal_list.load_fail", ["error_status": lastErrorStatus])
            }
        }
    }

    var lastErrorStatus: Int?

    func setHasResume(_ v: Bool) {
        hasResume = v
        trackPageviewIfReady()
    }

    /// 목록 · 이어서 만들기 카드를 다 불러온 뒤 화면당 한 번
    private func trackPageviewIfReady() {
        guard !pageviewSent, !isPreview, let goals = phase.value, let hasResume else { return }
        pageviewSent = true
        let temps = goals.filter { $0.isTemporary && $0.goalCategoryStatus.isOngoing }.count
        let ongoing = goals.filter { !$0.isTemporary && $0.goalCategoryStatus.isOngoing }.count
        let done = goals.filter { $0.goalCategoryStatus == .COMPLETED }.count
        GD.track("goal_list.pageview", [
            "goal_count": goals.count, "ongoing_count": ongoing, "temp_count": temps, "completed_count": done,
            "has_resume": hasResume, "goal_list_state": goals.isEmpty ? "empty" : "ok",
        ])
    }

    static func preview(_ goals: [GoalCategory]) -> GoalListViewModel {
        let vm = GoalListViewModel()
        vm.isPreview = true
        vm.phase = .loaded(goals)
        vm.streaks = [GoalPreviewData.recordGoal.goalCategoryId: GoalPreviewData.workStreak]
        return vm
    }
}

@MainActor
struct GoalListScreen: View {
    @State private var vm: GoalListViewModel
    @Environment(AppRouter.self) private var router

    init() {
        _vm = State(initialValue: GoalListViewModel())
    }

    /// 미리보기용
    init(previewVM: GoalListViewModel) {
        _vm = State(initialValue: previewVM)
    }

    private var isPreview: Bool { vm.isPreview }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                PageHeader(title: "목표") {
                    NButton("목표 추가", icon: "plus") {
                        GD.track("goal_list.add_click", ["goal_count": vm.phase.value?.count])
                        router.openGoalAdd()
                    }
                }
                GoalCreationResumeCard(onLoaded: { vm.setHasResume($0) })
                // 기본 생활 습관 — 모든 사용자에게 하나씩 있는 체크 전용 목표 (V52)
                if isPreview {
                    HabitSummaryCard(preview: HabitPreviewData.home)
                } else {
                    HabitSummaryCard()
                }
                content
            }
            .pagePadding()
            .padding(.vertical, 16)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .canvasBackground()
        .toolbar(.hidden, for: .navigationBar)
        .reloadOn([.goals, .record]) { await vm.load() }
        .refreshable {
            GD.track("goal_list.pull_refresh", ["goal_count": vm.phase.value?.count])
            await vm.load()
        }
    }

    @ViewBuilder private var content: some View {
        switch vm.phase {
        case .loading:
            VStack(spacing: 16) {
                ForEach(0..<3, id: \.self) { _ in
                    RoundedRectangle(cornerRadius: Radius.card)
                        .fill(Color.surface)
                        .overlay(RoundedRectangle(cornerRadius: Radius.card).stroke(Color.line, lineWidth: 1))
                        .frame(height: 180)
                }
            }
            .redacted(reason: .placeholder)
        case .failed(let message):
            VStack(spacing: 6) {
                Text("목표를 불러오지 못했어요").font(.pt(15, .bold)).foregroundStyle(Color.ink)
                Text(message).font(.pt(14)).foregroundStyle(Color.ink3).multilineTextAlignment(.center)
                NButton("다시 시도") {
                    GD.track("goal_list.retry_click", ["error_status": vm.lastErrorStatus])
                    Task { @MainActor in
                        vm.phase = .loading
                        await vm.load()
                    }
                }
                .padding(.top, 10)
            }
            .padding(32)
            .frame(maxWidth: .infinity)
            .card()
        case .loaded(let goals):
            if goals.isEmpty {
                emptyCard
            } else {
                lists(goals)
            }
        }
    }

    private var emptyCard: some View {
        VStack(spacing: 6) {
            Text("첫 목표를 만들어 보세요").font(.pt(16, .semibold)).foregroundStyle(Color.ink)
            Text("AI와 대화하며 계획까지 설계하거나, 이미 정한 계획이 있다면 직접 만들 수 있어요.")
                .font(.pt(14))
                .foregroundStyle(Color.ink3)
                .multilineTextAlignment(.center)
            HStack(spacing: 8) {
                NButton("AI로 설계하기", variant: .ai, size: .lg, icon: "sparkles") {
                    GD.track("goal_list.empty_cta_click", ["cta": "ai"])
                    router.startGoalCreation()
                }
                NButton("직접 만들기", variant: .secondary, size: .lg) {
                    GD.track("goal_list.empty_cta_click", ["cta": "manual"])
                    router.openGoalAdd(mode: .manual)
                }
            }
            .padding(.top, 18)
        }
        .padding(28)
        .frame(maxWidth: .infinity, minHeight: 300)
        .card()
    }

    @ViewBuilder private func lists(_ goals: [GoalCategory]) -> some View {
        let temps = goals.filter { $0.isTemporary && $0.goalCategoryStatus.isOngoing }
        let ongoing = goals.filter { !$0.isTemporary && $0.goalCategoryStatus.isOngoing }
        let done = goals.filter { $0.goalCategoryStatus == .COMPLETED }

        if !temps.isEmpty {
            VStack(alignment: .leading, spacing: 12) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("임시 목표 \(temps.count)").font(.pt(15, .bold)).foregroundStyle(Color.ink)
                    Text("AI로 구체화하면 기간·마일스톤·루틴이 생기고, 지금 있는 Task는 그대로 옮겨져요.")
                        .font(.pt(13))
                        .foregroundStyle(Color.ink3)
                }
                ForEach(Array(temps.enumerated()), id: \.element.id) { i, g in GoalTempCard(goal: g, position: i + 1) }
            }
        }
        if !ongoing.isEmpty {
            VStack(alignment: .leading, spacing: 12) {
                Text("진행 중 \(ongoing.count)").font(.pt(15, .bold)).foregroundStyle(Color.ink)
                ForEach(Array(ongoing.enumerated()), id: \.element.id) { i, g in
                    GoalListCard(goal: g, streak: vm.streaks[g.goalCategoryId], section: "ongoing", position: i + 1)
                }
            }
        }
        if !done.isEmpty {
            VStack(alignment: .leading, spacing: 12) {
                Text("완료 \(done.count)").font(.pt(15, .bold)).foregroundStyle(Color.ink3)
                ForEach(Array(done.enumerated()), id: \.element.id) { i, g in
                    GoalListCard(goal: g, streak: vm.streaks[g.goalCategoryId], section: "completed", position: i + 1)
                }
            }
            .opacity(0.8)
            .padding(.top, 8)
        }
    }
}

// MARK: - 임시 목표 카드 (보기 / AI로 구체화하기)

@MainActor
private struct GoalTempCard: View {
    let goal: GoalCategory
    var position: Int = 1
    @Environment(AppRouter.self) private var router

    private var subCount: Int { goal.generalCategories.filter { $0.generalCategoryStatus != .DELETED }.count }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 8) {
                Circle().fill(Color.goal(goal.colorCode)).frame(width: 10, height: 10)
                Badge("임시 목표", tone: .warning)
            }
            Button {
                GD.track("goal_list.temp_card_click", ["goal_category_id": goal.goalCategoryId, "list_position": position])
                router.openGoal(goal.goalCategoryId)
            } label: {
                VStack(alignment: .leading, spacing: 4) {
                    Text((goal.emoji.map { $0 + " " } ?? "") + goal.goalCategoryName)
                        .font(.pt(16, .semibold))
                        .foregroundStyle(Color.ink)
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                    Text("Task를 적다가 만들어진 목표예요" + (subCount > 0 ? " · 세부 목표 \(subCount)개" : ""))
                        .font(.pt(13))
                        .foregroundStyle(Color.ink3)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            NButton("AI로 구체화하기", variant: .ai, size: .sm, icon: "sparkles") {
                GD.track("goal_list.temp_refine_click", ["goal_category_id": goal.goalCategoryId, "list_position": position])
                router.startGoalCreation(sourceGoalId: goal.goalCategoryId)
            }
        }
        .padding(20)
        // [UX] 이름 · 버튼이 아닌 빈 곳 (안의 버튼이 먼저 탭을 받아요)
        .contentShape(RoundedRectangle(cornerRadius: Radius.card))
        .onTapGesture {
            GD.track("goal_list.temp_card_dead_click", ["goal_category_id": goal.goalCategoryId])
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.warningSoft.opacity(0.4), in: RoundedRectangle(cornerRadius: Radius.card))
        .overlay(
            RoundedRectangle(cornerRadius: Radius.card)
                .stroke(Color.borderSubtle, style: StrokeStyle(lineWidth: 1, dash: [5, 4]))
        )
    }
}

// MARK: - 목표 카드

@MainActor
private struct GoalListCard: View {
    let goal: GoalCategory
    let streak: GoalWorkStreak?
    var section: String = "ongoing"
    var position: Int = 1
    @Environment(AppRouter.self) private var router

    private var period: GoalFormat.Period? {
        GoalFormat.periodProgress(goal.goalCategoryStartDate, goal.goalCategoryEndDate)
    }

    private var statusTone: BadgeTone {
        switch goal.goalCategoryStatus {
        case .COMPLETED: return .success
        case .IN_PROGRESS: return .brand
        default: return .neutral
        }
    }

    private var routineCount: Int { goal.generalCategories.reduce(0) { $0 + $1.routines.count } }

    private var footer: String {
        if goal.isRecordGoal {
            return "업무형 · 세부 목표별 로드맵" + (routineCount > 0 ? " · 루틴 \(routineCount)개" : "")
        }
        return "세부 목표 \(goal.generalCategories.count)개 · 루틴 \(routineCount)개"
    }

    var body: some View {
        let color = Color.goal(goal.colorCode)
        Button {
            GD.track("goal_list.goal_card_click", goal.analyticsBase, [
                "goal_status": goal.goalCategoryStatus.rawValue, "goal_section": section, "list_position": position,
                "progress_percent": goal.isRecordGoal ? nil : goal.analyticsProgressPercent,
                "paused_days": goal.pausedDays, "d_day": goal.isRecordGoal ? nil : goal.analyticsDDay,
            ])
            router.openGoal(goal.goalCategoryId)
        } label: {
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 6) {
                    Circle().fill(color).frame(width: 10, height: 10)
                    Badge(goal.goalCategoryStatus.label, tone: statusTone)
                    if goal.isRecordGoal { Badge("업무형", tone: .neutral) }
                    if let paused = goal.pausedDays { Badge("멈춤 \(paused)일째", tone: .warning) }
                    Spacer(minLength: 0)
                    if let period, goal.goalCategoryStatus.isOngoing {
                        Text(GoalFormat.dDayLabel(period.remain)).font(.pt(12, .semibold)).foregroundStyle(Color.ink3)
                    }
                }
                Text((goal.emoji.map { $0 + " " } ?? "") + goal.goalCategoryName)
                    .font(.pt(16, .semibold))
                    .foregroundStyle(Color.ink)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
                    .padding(.top, 12)
                if let start = goal.goalCategoryStartDate {
                    // 업무형은 전체 마감이 없어요 (V53 — 마감은 주제마다)
                    let end = goal.isRecordGoal ? "마감은 세부 목표마다" : (goal.goalCategoryEndDate.map { GoalFormat.formatDot($0) } ?? "계속")
                    Text("\(GoalFormat.formatDot(start)) – \(end)")
                        .font(.pt(13))
                        .foregroundStyle(Color.ink3)
                        .padding(.top, 4)
                }

                if goal.isRecordGoal {
                    GoalWorkStreakView(streak: streak, color: color)
                } else {
                    GoalLifeProgressView(goal: goal, color: color)
                }

                Text(footer).font(.pt(12)).foregroundStyle(Color.ink3).padding(.top, 12)
            }
            .padding(20)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.surface, in: RoundedRectangle(cornerRadius: Radius.card))
            .overlay(RoundedRectangle(cornerRadius: Radius.card).stroke(Color.line, lineWidth: 1))
            .contentShape(RoundedRectangle(cornerRadius: Radius.card))
        }
        .buttonStyle(.plain)
    }
}

/// 생활형: 수치가 있으면 목표까지 남은 수치, 없으면 기간 경과
@MainActor
private struct GoalLifeProgressView: View {
    let goal: GoalCategory
    let color: Color

    private func fmt(_ v: Double) -> String { GoalDetailLogic.withUnit(v, goal.metricUnit) }

    var body: some View {
        if let start = goal.startValue, let target = goal.targetValue, start != target {
            let current = goal.currentValue ?? start
            let ratio = min(max((current - start) / (target - start), 0), 1)
            let remain = abs(target - current)
            let done = ratio >= 1
            VStack(alignment: .leading, spacing: 6) {
                HStack(alignment: .bottom) {
                    (Text(goal.metricName.map { $0 + " " } ?? "")
                     + Text(fmt(current)).bold().foregroundColor(Color.ink)
                     + Text(" → \(fmt(target))"))
                        .foregroundStyle(Color.ink3)
                    Spacer(minLength: 0)
                    Text("\(Int((ratio * 100).rounded()))%").font(.ptMono(12, .semibold)).foregroundStyle(Color.ink)
                }
                .font(.pt(12))
                ProgressBar(value: ratio, color: color, height: 6)
                Text(done ? "목표 달성 🎉" : "목표까지 \(fmt(remain)) 남았어요")
                    .font(.pt(12, .semibold))
                    .foregroundStyle(done ? Color.ink : color)
            }
            .padding(.top, 20)
        } else if let period = GoalFormat.periodProgress(goal.goalCategoryStartDate, goal.goalCategoryEndDate) {
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Text("기간 경과")
                    Spacer(minLength: 0)
                    Text("\(Int((period.ratio * 100).rounded()))%").font(.ptMono(12))
                }
                .font(.pt(12))
                .foregroundStyle(Color.ink3)
                ProgressBar(value: period.ratio, color: color, height: 6)
            }
            .padding(.top, 20)
        }
    }
}

/// 업무형: 완료한 Task 가 있는 주가 몇 주째 이어지는지 + 최근 8주 칸
@MainActor
private struct GoalWorkStreakView: View {
    let streak: GoalWorkStreak?
    let color: Color

    var body: some View {
        if let s = streak {
            let weeks = Array(s.weeks.suffix(8))
            VStack(alignment: .leading, spacing: 6) {
                HStack(alignment: .bottom) {
                    Text("연속 완료").foregroundStyle(Color.ink3)
                    Spacer(minLength: 0)
                    Text((s.weekStreak > 0 ? "🔥 " : "") + "\(s.weekStreak)주").font(.ptMono(12, .semibold)).foregroundStyle(Color.ink)
                }
                .font(.pt(12))
                HStack(spacing: 4) {
                    ForEach(weeks, id: \.weekStart) { w in
                        Capsule().fill(w.count > 0 ? color : Color.subtle).frame(height: 6)
                    }
                }
                .accessibilityHidden(true)
                Text(s.thisWeekDone
                     ? "이번 주도 완료했어요"
                     : (s.weekStreak > 0 ? "이번 주에 하나 끝내면 \(s.weekStreak + 1)주째예요" : "이번 주에 첫 칸을 채워보세요"))
                    .font(.pt(12))
                    .foregroundStyle(Color.ink3)
            }
            .padding(.top, 20)
        } else {
            RoundedRectangle(cornerRadius: Radius.lg)
                .fill(Color.subtle)
                .frame(height: 48)
                .padding(.top, 20)
        }
    }
}

#Preview("목표 목록") {
    NavigationStack {
        GoalListScreen(previewVM: .preview([
            GoalPreviewData.tempGoal, GoalPreviewData.goal, GoalPreviewData.recordGoal, GoalPreviewData.doneGoal,
        ]))
    }
    .environment(AppRouter.shared)
}

#Preview("빈 목록") {
    NavigationStack {
        GoalListScreen(previewVM: .preview([]))
    }
    .environment(AppRouter.shared)
}
