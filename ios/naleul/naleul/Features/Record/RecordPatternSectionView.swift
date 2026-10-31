// Features/Record/RecordPatternSectionView.swift
//
// 웹 features/record/ui/pattern/RecordPatternSection.tsx · RecordSummaryTiles.tsx
// 기록형 목표 상세 — "이 목표에 대한 나의 패턴" (이 목표의 완료한 Task 기준).
//
// 기록형은 기한도 목표 수치도 없어서 달성률(%)을 낼 수 없어요.
// 대신 완료한 Task 가 "언제·얼마나" 쌓였는지와 루틴별로 한 것을 숫자로 보여주고,
// 성장은 "그 전 4주보다 늘어난 것"과 "줄지 않는 누적"으로 보여줘요.
// 웹의 넓은 화면 2단 배치는 아이폰에서 세로로 쌓았어요.

import SwiftUI

// MARK: - 상태

@MainActor
@Observable
final class RecordPatternViewModel {
    private(set) var phase: LoadPhase<RecordPattern> = .loading
    private var goalId: Int?
    private let isPreview: Bool
    /// 통계 탭을 연 뒤 처음 그렸는지 (record_stats.view 는 탭을 열 때마다 한 번)
    private var viewSent = false

    init(preview: RecordPattern? = nil) {
        isPreview = preview != nil
        if let preview { phase = .loaded(preview) }
    }

    func load(goalId: Int) async {
        if isPreview { return }
        if self.goalId != goalId {
            self.goalId = goalId
            phase = .loading
        }
        do {
            if let p = try await RecordAPI.pattern(goalId: goalId) {
                phase = .loaded(p)
                if !viewSent {
                    viewSent = true
                    WK.track("record_stats.view", goalId: goalId, [
                        "stats_state": p.summary.totalCount == 0 ? "empty" : "ok",
                        "completed_task_total": p.summary.totalCount, "routine_count": p.routines.items.count,
                    ])
                }
            } else {
                phase = .failed("이 목표의 패턴을 불러오지 못했어요.")
                WK.track("record_stats.load_fail", goalId: goalId, ["error_status": 404])
            }
        } catch is CancellationError {
        } catch {
            // 이미 보여주던 패턴이 있으면 그대로 둬요
            if phase.value == nil {
                phase = .failed(errorMessage(error))
                WK.track("record_stats.load_fail", goalId: goalId, ["error_status": (error as? APIError)?.httpStatus ?? 0])
            }
        }
    }

    func retry() async {
        guard let goalId else { return }
        phase = .loading
        await load(goalId: goalId)
    }
}

// MARK: - 화면

struct RecordPatternSectionView: View {
    let goalId: Int
    @State private var vm: RecordPatternViewModel

    init(goalId: Int) {
        self.goalId = goalId
        _vm = State(initialValue: RecordPatternViewModel())
    }

    /// 미리보기용 (네트워크 없이)
    init(goalId: Int, preview: RecordPattern) {
        self.goalId = goalId
        _vm = State(initialValue: RecordPatternViewModel(preview: preview))
    }

    var body: some View {
        content
            // 기록을 남기거나 지우면(.record) · Task 완료를 바꾸면(.timetable · .goals) 같이 다시 불러와요
            .reloadOn([.record, .timetable, .goals], id: goalId) { await vm.load(goalId: goalId) }
    }

    @ViewBuilder
    private var content: some View {
        switch vm.phase {
        case .loading:
            LoadingView(minHeight: 192)
        case .failed:
            HStack(spacing: 12) {
                Text("이 목표의 패턴을 불러오지 못했어요.").font(.pt(14)).foregroundStyle(Color.ink2)
                Spacer(minLength: 0)
                NButton("다시 시도", variant: .secondary, size: .sm) {
                    WK.track("record_stats.retry_click", goalId: goalId)
                    Task { await vm.retry() }
                }
            }
            .padding(20)
            .card()
        case .loaded(let data):
            RecordPatternContent(data: data, goalId: goalId)
                .environment(\.recordStatsGoalId, goalId)
        }
    }
}

/// 불러온 패턴 → 카드들
private struct RecordPatternContent: View {
    let data: RecordPattern
    let goalId: Int

    /// 카드가 화면에 절반 이상 처음 보일 때 (탭을 열 때마다 카드당 1회)
    private func seen(_ card: String) -> () -> Void {
        { WK.track("record_stats.card_view", goalId: goalId, ["stats_card": card]) }
    }

    var body: some View {
        let hasRoutines = !data.routines.items.isEmpty
        VStack(alignment: .leading, spacing: 16) {
            if data.summary.totalCount == 0 {
                // 완료한 Task 가 아직 없으면 빈 그래프 대신 안내 한 줄 (루틴이 있으면 루틴 카드는 보여줘요)
                VStack(alignment: .leading, spacing: 6) {
                    Text("아직 완료한 Task가 없어요").font(.pt(16, .semibold)).foregroundStyle(Color.ink)
                    Text("이 목표의 Task를 완료하면 주차별로 완료한 Task, 완료의 리듬, 주로 하는 때가 여기에 보여요.")
                        .font(.pt(14))
                        .foregroundStyle(Color.ink3)
                        .lineSpacing(3)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(24)
                .frame(maxWidth: .infinity, alignment: .leading)
                .card()
            } else {
                RecordSummaryTiles(summary: data.summary).onFirstHalfVisible(seen("summary"))
                RecordWeeklyCard(weeks: data.rhythm.weeks).onFirstHalfVisible(seen("weekly"))
                RecordRhythmCard(rhythm: data.rhythm).onFirstHalfVisible(seen("rhythm"))
                RecordWhenCard(when: data.when).onFirstHalfVisible(seen("when"))
            }
            if hasRoutines {
                RecordRoutineCard(routines: data.routines).onFirstHalfVisible(seen("routine"))
            }
        }
    }
}

// MARK: - 요약 4칸

/// 완료한 Task 기준. 기한·목표 수치가 없으니 "쌓인 양"과 "그 전 4주보다 늘어난 것"만 보여줘요.
/// 줄었을 때는 빨간색 대신 그냥 최근 값을 보여줘요 (기록형은 쉬어가는 주도 자연스러워요).
struct RecordSummaryTiles: View {
    let summary: RecordPatternSummary

    var body: some View {
        let s = summary
        let r = s.recent
        let p = s.previous
        let moreCount = p.count > 0 && r.count > p.count ? r.count - p.count : 0
        let moreDays = p.activeDays > 0 && r.activeDays > p.activeDays ? r.activeDays - p.activeDays : 0
        let moreMinutes = p.minutes > 0 && r.minutes > p.minutes ? r.minutes - p.minutes : 0

        let countSub = moreCount > 0 ? "▲ 최근 4주 \(r.count)개 · \(moreCount)개 더" : "최근 4주 \(r.count)개"
        let daysSub = moreDays > 0 ? "▲ 최근 4주 \(r.activeDays)일 · \(moreDays)일 더" : "최근 4주 \(r.activeDays)일"
        let minutesSub: String = s.totalMinutes == 0
            ? "Task에 시간을 정하면 쌓여요"
            : (moreMinutes > 0 ? "▲ 최근 4주 \(KST.formatDuration(moreMinutes)) 더" : "최근 4주 \(KST.formatDuration(r.minutes))")
        let streakSub: String = s.thisWeekDone
            ? "이번 주도 완료했어요"
            : (s.weekStreak > 0 ? "이번 주에 하나 끝내면 \(s.weekStreak + 1)주째예요" : "이번 주에 첫 칸을 채워보세요")

        VStack(spacing: 12) {
            HStack(alignment: .top, spacing: 12) {
                PatternTile(label: "완료한 Task", value: "\(s.totalCount)개", sub: countSub,
                            tone: moreCount > 0 ? PatternTileTone.up : nil, valueSize: 20)
                PatternTile(label: "완료한 날", value: "\(s.activeDays)일", sub: daysSub,
                            tone: moreDays > 0 ? PatternTileTone.up : nil, valueSize: 20)
            }
            .fixedSize(horizontal: false, vertical: true)
            HStack(alignment: .top, spacing: 12) {
                PatternTile(label: "쌓인 시간", value: s.totalMinutes > 0 ? KST.formatDuration(s.totalMinutes) : "–",
                            sub: minutesSub, tone: moreMinutes > 0 ? PatternTileTone.up : nil, valueSize: 20)
                PatternTile(label: "연속 완료", value: (s.weekStreak > 0 ? "🔥 " : "") + "\(s.weekStreak)주",
                            sub: streakSub, tone: s.thisWeekDone && s.weekStreak >= 2 ? PatternTileTone.up : nil, valueSize: 20)
            }
            .fixedSize(horizontal: false, vertical: true)
        }
    }
}

#Preview("패턴") {
    ScrollView {
        RecordPatternSectionView(goalId: 7, preview: RecordSampleData.pattern)
            .padding(16)
    }
    .canvasBackground()
}

#Preview("완료 없음") {
    ScrollView {
        RecordPatternSectionView(goalId: 7, preview: RecordSampleData.emptyPattern)
            .padding(16)
    }
    .canvasBackground()
}
