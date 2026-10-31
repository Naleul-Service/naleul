// Features/Record/RecordJournalView.swift
//
// 웹 features/record/ui/RecordJournal.tsx (+ api.ts useRecordJournal · useDeleteRecord, journalText.ts)
// 업무 일지 — 날짜별로 "그날 완료한 Task"와 "직접 남긴 기록"을 한 목록으로.
//  - 2주씩 불러오고 "이전 2주 더 보기"로 과거를 이어 불러요 (웹 useInfiniteQuery)
//  - 하루 / 이번 주 단위로 글로 복사 (업무 보고·회고에 바로 붙여 넣기)
//  - 직접 남긴 기록만 여기서 지울 수 있어요 (Task 완료 취소는 Task 목록에서)

import SwiftUI
import UIKit

// MARK: - 상태

@MainActor
@Observable
final class RecordJournalViewModel {
    private(set) var days: [RecordJournalDay] = []
    private(set) var nextBefore: String?
    private(set) var loaded = false
    private(set) var failed = false
    private(set) var loadingMore = false
    /// 지금까지 불러온 쪽 수 — 다시 불러올 때 같은 만큼 다시 받아요 (웹 infinite query 의 refetch 와 같게)
    private var pages = 1
    private var goalId: Int?
    private let isPreview: Bool

    init(preview: RecordJournal? = nil) {
        isPreview = preview != nil
        if let preview {
            days = preview.days
            nextBefore = preview.nextBefore
            loaded = true
        }
    }

    func load(goalId: Int) async {
        if isPreview { return }
        if self.goalId != goalId {
            // 다른 목표로 바뀌면 처음부터
            self.goalId = goalId
            days = []
            nextBefore = nil
            loaded = false
            failed = false
            pages = 1
        }
        do {
            var all: [RecordJournalDay] = []
            var before: String? = nil
            var count = 0
            repeat {
                let page = try await RecordAPI.journal(goalId: goalId, before: before)
                all += page?.days ?? []
                before = page?.nextBefore
                count += 1
            } while count < pages && before != nil
            days = all
            nextBefore = before
            pages = max(count, 1)
            loaded = true
            failed = false
        } catch is CancellationError {
        } catch {
            // 이미 보여주던 일지가 있으면 그대로 두고, 처음이면 오류 상자를 보여줘요
            if !loaded {
                failed = true
                WK.track("work_journal.load_fail", goalId: goalId, ["error_status": (error as? APIError)?.httpStatus ?? 0])
            }
        }
    }

    func loadMore() async {
        guard let goalId, let before = nextBefore, !loadingMore else { return }
        loadingMore = true
        defer { loadingMore = false }
        do {
            let page = try await RecordAPI.journal(goalId: goalId, before: before)
            days += page?.days ?? []
            nextBefore = page?.nextBefore
            pages += 1
            WK.track("work_journal.load_more_result", goalId: goalId, ["day_count": days.count, "result": "success"])
        } catch is CancellationError {
        } catch {
            Toast.error(errorMessage(error))
            WK.track("work_journal.load_more_result", goalId: goalId, [
                "day_count": days.count, "result": "fail", "error_status": (error as? APIError)?.httpStatus ?? 0,
            ])
        }
    }

    func retry() async {
        guard let goalId else { return }
        failed = false
        await load(goalId: goalId)
    }
}

// MARK: - 화면

struct RecordJournalView: View {
    let goalId: Int
    let goalName: String

    @State private var vm: RecordJournalViewModel
    @State private var deleteTarget: RecordJournalItem?
    @State private var confirmOpen = false
    @State private var deleting = false
    @State private var emptySent = false
    /// 지우려는 기록의 날짜 (days_ago)
    @State private var deleteDay = ""

    private func track(_ event: String, _ props: EventProps = [:]) {
        WK.track(event, goalId: goalId, props)
    }

    init(goalId: Int, goalName: String) {
        self.goalId = goalId
        self.goalName = goalName
        _vm = State(initialValue: RecordJournalViewModel())
    }

    /// 미리보기용 (네트워크 없이)
    init(goalId: Int, goalName: String, preview: RecordJournal) {
        self.goalId = goalId
        self.goalName = goalName
        _vm = State(initialValue: RecordJournalViewModel(preview: preview))
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 12) {
                Text("업무 일지").font(.pt(16, .semibold)).foregroundStyle(Color.ink)
                Spacer(minLength: 0)
                NButton("이번 주 복사", variant: .secondary, size: .sm, icon: "doc.on.doc") { copyWeek() }
                    .disabled(vm.days.isEmpty)
            }
            HStack(spacing: 12) {
                HStack(spacing: 4) {
                    Image(systemName: "checkmark.circle").foregroundStyle(Color.textBrand)
                    Text("완료한 Task")
                }
                HStack(spacing: 4) {
                    Image(systemName: "pencil.line")
                    Text("직접 남긴 기록")
                }
            }
            .font(.pt(12))
            .foregroundStyle(Color.ink3)
            .padding(.top, 4)
            .padding(.bottom, 16)

            content

            if vm.nextBefore != nil {
                NButton("이전 2주 더 보기", variant: .ghost, loading: vm.loadingMore, fullWidth: true) {
                    track("work_journal.load_more_click", ["day_count": vm.days.count])
                    Task { await vm.loadMore() }
                }
                .padding(.top, 16)
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card()
        // Task 완료 토글(.timetable)이나 기록 추가/삭제(.record)가 있으면 다시 불러와요
        .reloadOn([.record, .activities, .timetable], id: goalId) { await vm.load(goalId: goalId) }
        .confirmDialog(isPresented: $confirmOpen,
                       title: "이 기록을 지울까요?",
                       message: deleteTarget.map { "\"\($0.title)\" 기록이 사라져요." },
                       confirmLabel: "지우기",
                       tone: .danger,
                       onCancel: {
            track("work_journal.record_delete_cancel", ["close_method": "cancel_button"])
            deleteTarget = nil
        }) {
            if let item = deleteTarget {
                track("work_journal.record_delete_confirm", ["days_ago": GD.daysAgo(deleteDay)])
                Task { await delete(item) }
            }
        }
    }

    @ViewBuilder
    private var content: some View {
        if !vm.loaded && !vm.failed {
            ProgressView().tint(Color.ink3).frame(maxWidth: .infinity, minHeight: 96)
        } else if vm.failed {
            HStack(spacing: 12) {
                Text("일지를 불러오지 못했어요.").font(.pt(14)).foregroundStyle(Color.ink2)
                Spacer(minLength: 0)
                NButton("다시 시도", variant: .secondary, size: .sm) {
                    track("work_journal.retry_click")
                    Task { await vm.retry() }
                }
            }
            .padding(16)
            .background(Color.bgSunken, in: RoundedRectangle(cornerRadius: Radius.xxl))
        } else if vm.days.isEmpty {
            Text(vm.nextBefore != nil
                 ? "최근 2주에는 일지가 없어요. 아래에서 이전 일지를 볼 수 있어요."
                 : "아직 일지가 없어요. 이 목표의 Task를 완료하거나 위에서 한 일을 남기면 날짜별로 쌓여요.")
                .onAppear {
                    guard !emptySent else { return }
                    emptySent = true
                    track("work_journal.empty_view", ["has_older": vm.nextBefore != nil])
                }
                .font(.pt(14))
                .foregroundStyle(Color.ink3)
                .multilineTextAlignment(.center)
                .lineSpacing(3)
                .padding(.horizontal, 16)
                .padding(.vertical, 24)
                .frame(maxWidth: .infinity)
                .background(Color.bgSunken, in: RoundedRectangle(cornerRadius: Radius.xxl))
        } else {
            VStack(alignment: .leading, spacing: 20) {
                ForEach(vm.days) { d in
                    RecordJournalDaySection(day: d, today: KST.today(), deleting: deleting,
                                            onCopy: {
                                                track("work_journal.copy_day_click", [
                                                    "days_ago": GD.daysAgo(d.date), "item_count": d.items.count,
                                                ])
                                                copy(RecordJournalText.text("\(goalName) · 업무 일지", [d]))
                                            },
                                            onDelete: { item in
                                                track("work_journal.record_delete_click", ["days_ago": GD.daysAgo(d.date)])
                                                deleteDay = d.date
                                                deleteTarget = item
                                                confirmOpen = true
                                            })
                }
            }
        }
    }

    // MARK: 동작

    private func copyWeek() {
        let today = KST.today()
        let weekStart = KST.startOfWeek(today)
        let week = vm.days.filter { $0.date >= weekStart }
        track("work_journal.copy_week_click", [
            "day_count": week.count, "item_count": week.reduce(0) { $0 + $1.items.count }, "was_empty": week.isEmpty,
        ])
        guard !week.isEmpty else {
            Toast.show("이번 주 일지가 아직 없어요.")
            return
        }
        copy(RecordJournalText.text(
            "\(goalName) · 업무 일지 · \(KST.formatMonthDay(weekStart)) – \(KST.formatMonthDay(today))", week))
    }

    /// 웹 navigator.clipboard.writeText → UIPasteboard
    private func copy(_ text: String) {
        UIPasteboard.general.string = text
        Toast.success("복사했어요. 원하는 곳에 붙여 넣으세요.")
    }

    @MainActor
    private func delete(_ item: RecordJournalItem) async {
        deleting = true
        defer {
            deleting = false
            deleteTarget = nil
        }
        let ok = await Mutation.run(invalidate: [.activities, .record], successMessage: "기록을 지웠어요.") {
            try await RecordAPI.deleteRecord(activityId: item.id)
        }
        track("work_journal.record_delete_result", GD.result(ok != nil))
    }
}

// MARK: - 하루

private struct RecordJournalDaySection: View {
    let day: RecordJournalDay
    let today: String
    let deleting: Bool
    let onCopy: () -> Void
    let onDelete: (RecordJournalItem) -> Void

    private var dayLabel: String {
        if day.date == today { return "오늘 · \(KST.formatMonthDay(day.date))" }
        if day.date == KST.addDays(today, -1) { return "어제 · \(KST.formatMonthDay(day.date))" }
        return KST.formatMonthDay(day.date)
    }

    private var summary: String {
        var parts: [String] = []
        if day.taskCount > 0 { parts.append("Task \(day.taskCount)") }
        if day.recordCount > 0 { parts.append("기록 \(day.recordCount)") }
        if day.minutes > 0 { parts.append(KST.formatDuration(day.minutes)) }
        return parts.joined(separator: " · ")
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Text(dayLabel).font(.pt(14, .bold)).foregroundStyle(Color.ink)
                Spacer(minLength: 0)
                Text(summary).font(.ptMono(12)).foregroundStyle(Color.ink3)
                IconButton(systemName: "doc.on.doc", size: 13, color: .ink4,
                           label: "\(KST.formatMonthDay(day.date)) 일지 복사", action: onCopy)
            }
            VStack(spacing: 0) {
                ForEach(Array(day.items.enumerated()), id: \.element.key) { i, item in
                    if i > 0 { Divider().overlay(Color.borderSoft) }
                    RecordJournalItemRow(item: item, deleting: deleting, onDelete: { onDelete(item) })
                }
            }
            .overlay(RoundedRectangle(cornerRadius: Radius.xxl).stroke(Color.line, lineWidth: 1))
        }
    }
}

private struct RecordJournalItemRow: View {
    let item: RecordJournalItem
    let deleting: Bool
    let onDelete: () -> Void

    private var timeLabel: String {
        if let s = item.startAt, let e = item.endAt { return "\(KST.hm(s))–\(KST.hm(e))" }
        return "시간 미정"
    }

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Text(timeLabel)
                .font(.ptMono(13))
                .foregroundStyle(Color.ink3)
                .frame(width: 84, alignment: .leading)
                .padding(.top, 1)
            if item.type == .RECORD {
                NSymbol("pencil.line", size: 14)
                    .foregroundStyle(Color.ink3)
                    .padding(.top, 2)
                    .accessibilityLabel("직접 남긴 기록")
            } else {
                Image(systemName: "checkmark.circle")
                    .font(.system(size: 14))
                    .foregroundStyle(Color.textBrand)
                    .padding(.top, 2)
                    .accessibilityLabel("완료한 Task")
            }
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text((item.emoji.map { "\($0) " } ?? "") + item.title)
                        .font(.pt(15, .medium))
                        .foregroundStyle(Color.ink)
                        .lineLimit(1)
                    if item.routine {
                        HStack(spacing: 2) {
                            NSymbol("repeat", size: 8, weight: .bold)
                            Text("루틴")
                        }
                        .font(.pt(10, .bold))
                        .foregroundStyle(Color.textBrand)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 1)
                        .background(Color.brandSoft, in: Capsule())
                        .fixedSize()
                    }
                }
                if let memo = item.memo, !memo.isEmpty {
                    Text(memo).font(.pt(13)).foregroundStyle(Color.ink3).fixedSize(horizontal: false, vertical: true)
                }
                if let m = item.minutes, m > 0 {
                    Text(KST.formatDuration(m)).font(.pt(12)).foregroundStyle(Color.ink3)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            if item.type == .RECORD {
                IconButton(systemName: "trash", size: 14, color: .ink4, label: "\(item.title) 기록 지우기", action: onDelete)
                    .disabled(deleting)
                    .padding(.top, -6)
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
    }
}

#Preview {
    ScrollView {
        VStack(spacing: 16) {
            RecordJournalView(goalId: 7, goalName: "나를 업무", preview: RecordSampleData.journal)
            RecordJournalView(goalId: 8, goalName: "빈 목표", preview: RecordJournal(days: [], nextBefore: nil))
        }
        .padding(16)
    }
    .canvasBackground()
}
