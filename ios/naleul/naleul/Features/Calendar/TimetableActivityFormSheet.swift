// Features/Calendar/TimetableActivityFormSheet.swift
//
// 실제로 한 일 기록 · 수정 시트 (지난 빈 칸을 누르거나 "실제로 한 일" · "이 시간에 다른 일을 했어요"로 열어요).
// 웹 원본: features/timetable/ui/ActivityForm.tsx
//  - 끝 시각이 시작보다 이르면 다음 날로 봐요 (예: 23:30 ~ 00:30)
//  - 아직 오지 않은 시간은 기록할 수 없어요 · 24시간까지만
//  - 삭제는 같은 자리에서 한 번 더 확인

import SwiftUI

struct TimetableActivityFormSheet: View {
    let board: TimetableBoardModel
    let draft: TimetableActivityDraft

    @State private var title: String
    @State private var emoji: String
    @State private var date: String
    @State private var start: Int
    @State private var end: Int
    @State private var goalId: Int?
    @State private var replaced: TimetableReplacedTask?
    @State private var memo: String
    @State private var confirmDelete = false
    @State private var saving = false
    @State private var deleting = false
    @State private var goals: [GoalCategory] = []
    /// "saved" · "deleted" 면 닫혀도 activity_form.close 안 보냄
    @State private var outcome: String?

    init(board: TimetableBoardModel, draft: TimetableActivityDraft) {
        self.board = board
        self.draft = draft
        let a = draft.activity
        _title = State(initialValue: a?.title ?? "")
        _emoji = State(initialValue: a?.emoji ?? "")
        _date = State(initialValue: draft.date)
        _start = State(initialValue: ((draft.start % 1440) + 1440) % 1440)
        _end = State(initialValue: ((draft.end % 1440) + 1440) % 1440)
        _goalId = State(initialValue: a?.goalCategoryId)
        _replaced = State(initialValue: draft.replaced)
        _memo = State(initialValue: a?.memo ?? "")
    }

    // MARK: 계산

    private var trimmedTitle: String { title.trimmingCharacters(in: .whitespacesAndNewlines) }
    private var startMinutes: Int { start }
    /// 자정을 넘긴 기록
    private var endMinutes: Int { end <= start ? end + 1440 : end }
    private var startAt: String { KST.toDateTime(date, startMinutes) }
    private var endAt: String { KST.toDateTime(date, endMinutes) }

    private var error: String? {
        if trimmedTitle.isEmpty { return nil }
        let now = KST.now()
        if endAt > KST.toDateTime(now.date, now.minutes) { return "아직 오지 않은 시간은 기록할 수 없어요." }
        if endMinutes - startMinutes > 1440 { return "24시간까지만 기록할 수 있어요." }
        return nil
    }

    private var valid: Bool { !trimmedTitle.isEmpty && error == nil }

    /// 처음 연 값과 달라졌는지 (activity_form.close 의 is_dirty)
    private var isDirty: Bool {
        let a = draft.activity
        return title != (a?.title ?? "") || emoji != (a?.emoji ?? "") || date != draft.date
            || start != ((draft.start % 1440) + 1440) % 1440 || end != ((draft.end % 1440) + 1440) % 1440
            || goalId != a?.goalCategoryId || memo != (a?.memo ?? "") || replaced?.id != draft.replaced?.id
    }

    private var durationText: String {
        let m = endMinutes - startMinutes
        if m >= 60 { return "\(m / 60)시간\(m % 60 > 0 ? " \(m % 60)분" : "")" }
        return "\(m)분"
    }

    private var activeGoals: [GoalCategory] {
        goals.filter { $0.goalCategoryStatus.isOngoing || $0.goalCategoryId == goalId }
    }

    // MARK: 화면

    var body: some View {
        SheetScaffold(title: nil, dismissible: !saving && !deleting) {
            VStack(alignment: .leading, spacing: 14) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("✓ 실제로 한 일").font(.pt(12, .bold)).foregroundStyle(Color.success)
                    Text("계획과 상관없이 그 시간에 실제로 한 일을 남겨요.").font(.pt(12)).foregroundStyle(Color.ink3)
                }
                if let r = replaced {
                    replacedRow(r)
                }
                HStack(alignment: .bottom, spacing: 8) {
                    VStack(alignment: .leading, spacing: 4) {
                        fieldLabel("이모지")
                        TextField("🙂", text: $emoji)
                            .multilineTextAlignment(.center)
                            .frame(width: 26)
                            .inputStyle()
                    }
                    VStack(alignment: .leading, spacing: 4) {
                        fieldLabel("무엇을 했나요?")
                        TextField("예) 친구 만남, 유튜브, 낮잠", text: $title)
                            .submitLabel(.done)
                            .onSubmit { submit() }
                            .inputStyle()
                    }
                }
                VStack(alignment: .leading, spacing: 4) {
                    fieldLabel("날짜")
                    TimetableDateField(ymd: $date, maxDate: KST.today())
                }
                HStack(spacing: 8) {
                    VStack(alignment: .leading, spacing: 4) {
                        fieldLabel("시작")
                        TimetableClockPicker(minutes: $start, label: "시작")
                    }
                    VStack(alignment: .leading, spacing: 4) {
                        HStack(spacing: 4) {
                            fieldLabel("끝")
                            if endMinutes >= 1440 {
                                Text("(다음 날)").font(.pt(12)).foregroundStyle(Color.ink4)
                            }
                        }
                        TimetableClockPicker(minutes: $end, label: "끝")
                    }
                }
                goalPicker
                VStack(alignment: .leading, spacing: 4) {
                    fieldLabel("메모 (선택)")
                    TextField("왜 계획과 달라졌는지 적어 두면 패턴을 보기 좋아요", text: $memo, axis: .vertical)
                        .lineLimit(2...4)
                        .inputStyle(minHeight: 64)
                }
                if let error {
                    Text(error).font(.pt(13, .medium)).foregroundStyle(Color.danger)
                }
                bottomButtons
            }
        }
        .task { await loadGoals() }
        .onChange(of: error) { _, e in
            if let e { board.sheetTrack("activity_form.error_view", ["validation_error": e]) }
        }
        .onDisappear {
            guard let m = GD.closeMethod(outcome) else { return }
            board.sheetTrack("activity_form.close", ["is_new": draft.activity == nil, "close_method": m, "is_dirty": isDirty])
        }
        .onChange(of: title) { _, v in if v.count > 60 { title = String(v.prefix(60)) } }
        .onChange(of: emoji) { _, v in if v.count > 16 { emoji = String(v.prefix(16)) } }
        .onChange(of: memo) { _, v in if v.count > 200 { memo = String(v.prefix(200)) } }
        .accessibilityLabel("실제로 한 일")
    }

    private func fieldLabel(_ text: String) -> some View {
        Text(text).font(.pt(12, .medium)).foregroundStyle(Color.ink3)
    }

    private func replacedRow(_ r: TimetableReplacedTask) -> some View {
        HStack(spacing: 6) {
            Image(systemName: "link").font(.system(size: 12)).foregroundStyle(Color.ink3)
            Text("계획").font(.pt(13)).foregroundStyle(Color.ink3)
            Text(r.name).font(.pt(13, .bold)).foregroundStyle(Color.ink).lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .leading)
            Text("대신").font(.pt(13)).foregroundStyle(Color.ink3)
            IconButton(systemName: "xmark", size: 12, label: "계획 연결 끊기") {
                board.sheetTrack("activity_form.unlink_click")
                replaced = nil
            }
        }
        .padding(.leading, 12)
        .padding(.vertical, 4)
        .background(Color.subtle, in: RoundedRectangle(cornerRadius: Radius.xl))
    }

    private var goalPicker: some View {
        VStack(alignment: .leading, spacing: 4) {
            fieldLabel("목표 (선택)")
            Picker("목표 (선택)", selection: $goalId) {
                Text("연결 안 함").tag(Int?.none)
                ForEach(activeGoals) { g in
                    Text(TimetableRules.title(g.emoji, g.goalCategoryName)).tag(Int?.some(g.goalCategoryId))
                }
            }
            .pickerStyle(.menu)
            .tint(Color.ink)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 6)
            .frame(height: 44)
            .background(Color.surface, in: RoundedRectangle(cornerRadius: Radius.xl))
            .overlay(RoundedRectangle(cornerRadius: Radius.xl).stroke(Color.borderSubtle, lineWidth: 1))
        }
    }

    @ViewBuilder
    private var bottomButtons: some View {
        if confirmDelete, let a = draft.activity {
            TimetableInlineConfirm(message: "이 기록을 지울까요?", loading: deleting,
                                   onConfirm: {
                                       board.sheetTrack("activity_form.delete_confirm", ["days_ago": CAL.daysAgo(draft.date)])
                                       remove(a.activityId)
                                   },
                                   onCancel: {
                                       board.sheetTrack("activity_form.delete_cancel")
                                       confirmDelete = false
                                   })
        } else {
            // 바닥 버튼 줄로 (DESIGN_SPEC 13.2): 왼쪽 삭제 · 걸린 시간 · 저장
            SheetActions(key: "\(draft.activity != nil)|\(saving)|\(valid)|\(valid ? durationText : "")") {
                if draft.activity != nil {
                    Button {
                        board.sheetTrack("activity_form.delete_click", ["days_ago": CAL.daysAgo(draft.date)])
                        confirmDelete = true
                    } label: {
                        HStack(spacing: 4) {
                            NSymbol("trash", size: 13, weight: .semibold)
                            Text("삭제").font(.pt(13, .semibold))
                        }
                        .foregroundStyle(Color.danger)
                        .padding(.horizontal, 10)
                        .frame(height: 32)
                    }
                    .buttonStyle(.plain)
                }
                if valid {
                    Text(durationText).font(.ptMono(12)).foregroundStyle(Color.ink3).fixedSize()
                }
                NButton(draft.activity != nil ? "저장" : "기록", size: .sm, loading: saving) { submit() }
                    .disabled(!valid)
            }
        }
    }

    // MARK: 저장

    private func submit() {
        guard valid, !saving else { return }
        saving = true
        let e = emoji.trimmingCharacters(in: .whitespacesAndNewlines)
        let m = memo.trimmingCharacters(in: .whitespacesAndNewlines)
        board.sheetTrack("activity_form.save_click", [
            "is_new": draft.activity == nil, "duration_minutes": endMinutes - startMinutes,
            "crosses_midnight": endMinutes > 1440, "has_goal": goalId != nil,
            "is_habit_goal": goals.first { $0.goalCategoryId == goalId }?.isHabitGoal ?? false, "memo_length": m.count,
            "is_replacement": replaced != nil, "days_ago": CAL.daysAgo(date),
        ])
        let input = ActivityInput(title: trimmedTitle, emoji: e.isEmpty ? nil : e, startAt: startAt, endAt: endAt,
                                  goalCategoryId: goalId, replacedTaskId: replaced?.id, memo: m.isEmpty ? nil : m)
        let id = draft.activity?.activityId
        Task {
            if await board.saveActivity(input, activityId: id) { outcome = "saved" }
            saving = false
        }
    }

    private func remove(_ activityId: Int) {
        guard !deleting else { return }
        deleting = true
        Task {
            if await board.deleteActivity(activityId) { outcome = "deleted" }
            deleting = false
        }
    }

    private func loadGoals() async {
        if board.isOffline { return }
        goals = (try? await GoalAPI.goals(includeHabit: true)) ?? []   // 기본 생활 습관으로도 분류 (2026-10)
    }
}

#Preview {
    let d = KST.today()
    let board = TimetableBoardModel.sample(start: d, end: d)
    let draft = TimetableActivityDraft(activity: nil, date: d, start: 600, end: 660,
                                       replaced: TimetableReplacedTask(id: 2001, name: "영어 단어 외우기"))
    return Color.canvas
        .sheet(isPresented: .constant(true)) {
            TimetableActivityFormSheet(board: board, draft: draft)
        }
}
