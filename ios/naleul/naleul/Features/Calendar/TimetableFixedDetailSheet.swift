// Features/Calendar/TimetableFixedDetailSheet.swift
//
// 고정 시간(수면 · 점심 · 저녁 · 직접 만든 생활 패턴) 블록 상세 + 시간 직접 입력 창.
// 웹 원본: features/timetable/ui/FixedDetail.tsx · TimeEditModal.tsx
//
// 시간 입력 창은 두 곳에서 써요
//  - 고정 시간 "시간 변경": 적용 범위(이날만 / 이번 주 / 앞으로 ○요일마다 / 앞으로 매일)와 함께
//  - Task "계획과 다른 시간에 했어요": 실제로 시작 · 끝낸 시각 → 완료

import SwiftUI

struct TimetableFixedDetailSheet: View {
    let board: TimetableBoardModel
    let block: FixedBlock

    private static func typeLabel(_ t: LifePatternType) -> String {
        switch t {
        case .SLEEP: return "수면"
        case .LUNCH: return "점심"
        case .DINNER: return "저녁"
        case .CUSTOM: return "고정 일정"
        }
    }

    var body: some View {
        let b = block
        let covered = b.coveredBy ?? []
        // 자정을 넘긴 수면의 다음 날 조각: 시작 시각을 몰라 시간 변경은 시작한 날에서 해요
        let canEditTime = !b.clippedFromPreviousDay
        let endText = KST.hm(b.end) == "00:00" ? "24:00" : KST.hm(b.end)
        SheetScaffold(title: nil) {
            VStack(alignment: .leading, spacing: 0) {
                FlowLayout(spacing: 6) {
                    Badge("고정 시간 · \(TimetableFixedDetailSheet.typeLabel(b.patternType))")
                    if b.overridden { Badge("기본과 다른 시간", tone: .brand) }
                }
                Text(TimetableRules.title(b.emoji, b.title))
                    .font(.pt(16, .semibold))
                    .foregroundStyle(Color.ink)
                    .padding(.top, 10)
                HStack(spacing: 8) {
                    NSymbol("clock", size: 13).foregroundStyle(Color.ink3)
                    Text("\(KST.formatMonthDay(b.targetDate)) · \(KST.hm(b.start)) – \(endText)")
                        .font(.pt(14))
                        .foregroundStyle(Color.ink2)
                }
                .padding(.top, 8)

                if b.clippedFromPreviousDay {
                    note("전날 밤에 시작한 \(b.title) 시간이에요. 시간을 바꾸려면 \(KST.formatMonthDay(b.targetDate)) 블록을 눌러 주세요.",
                         background: .subtle, foreground: .ink3)
                }
                if !covered.isEmpty {
                    note("직접 정한 Task가 이 시간의 일부를 덮고 있어요.", background: .warningSoft, foreground: .warningInk)
                }

                VStack(spacing: 8) {
                    HStack(spacing: 8) {
                        if canEditTime {
                            NButton("시간 변경", variant: .secondary, icon: "clock", fullWidth: true) {
                                board.sheetTrack("fixed_detail.time_edit_click", ["pattern_type": b.patternType.rawValue])
                                board.editFixedTime(b)
                            }
                                .disabled(board.busyFixed)
                        }
                        NButton("이날만 비우기", variant: .secondary, icon: "calendar.badge.minus",
                                loading: board.busyFixed, fullWidth: true) {
                            board.sheetTrack("fixed_detail.skip_click", ["pattern_type": b.patternType.rawValue,
                                                                         "days_from_today": KST.diffDays(KST.today(), b.targetDate)])
                            Task { await board.skipFixed(b) }
                        }
                    }
                    if b.overridden {
                        NButton("기본 시간으로 되돌리기", variant: .ghost, icon: "arrow.counterclockwise", fullWidth: true) {
                            board.sheetTrack("fixed_detail.reset_click", ["pattern_type": b.patternType.rawValue])
                            Task { await board.resetFixed(b) }
                        }
                        .disabled(board.busyFixed)
                    }
                    NButton("기본 패턴 수정", variant: .ghost, icon: "gearshape", fullWidth: true) {
                        board.sheetTrack("fixed_detail.settings_click", ["pattern_type": b.patternType.rawValue])
                        board.close()
                        ST.lifePatternEntry = "calendar_fixed_detail"
                        AppRouter.shared.openSettings(.lifePattern)
                    }
                }
                .padding(.top, 20)

                Text("시간을 옮기면 이날만 · 이번 주 · 앞으로 중에서 고를 수 있어요.")
                    .font(.pt(11))
                    .foregroundStyle(Color.ink4)
                    .frame(maxWidth: .infinity)
                    .multilineTextAlignment(.center)
                    .padding(.top, 12)
            }
        }
        .accessibilityLabel("고정 시간 상세")
    }

    private func note(_ text: String, background: Color, foreground: Color) -> some View {
        Text(text)
            .font(.pt(12))
            .foregroundStyle(foreground)
            .lineSpacing(2)
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(background, in: RoundedRectangle(cornerRadius: Radius.xl))
            .padding(.top, 12)
    }
}

// MARK: - 시간 직접 입력 (웹 TimeEditModal)

/// 종료가 시작보다 이르면 다음 날로 넘어가는 것으로 봐요 (예: 23:00 ~ 01:00). 날짜는 고정 (웹 dateEditable=false)
struct TimetableTimeEditSheet: View {
    let board: TimetableBoardModel
    let editing: TimetableTimeEditing

    @State private var start: Int
    @State private var end: Int
    @State private var chosen: ChangeScope = .DAY

    init(board: TimetableBoardModel, editing: TimetableTimeEditing) {
        self.board = board
        self.editing = editing
        _start = State(initialValue: ((editing.initial.start % 1440) + 1440) % 1440)
        _end = State(initialValue: ((editing.initial.end % 1440) + 1440) % 1440)
    }

    private var title: String {
        switch editing.target {
        case .actual: return "실제로 한 시간"
        case .fixed(let b): return "\(KST.formatMonthDay(b.targetDate)) \(b.title) 시간"
        }
    }

    private var description: String {
        switch editing.target {
        case .actual: return "실제로 시작하고 끝낸 시각을 남기면 완료로 표시되고, 나의 패턴의 시작 지연이 더 정확해져요."
        case .fixed: return "적용 범위를 골라 주세요. 이날만 바꾸면 다음 날부터는 기본 시간이에요."
        }
    }

    private var isFixed: Bool {
        if case .fixed = editing.target { return true }
        return false
    }

    var body: some View {
        let date = editing.initial.date
        let s = start
        let e = end <= start ? end + 1440 : end
        let valid = e - s > 0 && e - s <= 1440 && e != s + 1440
        let crosses = valid && e >= 1440
        let options: [TimetableScopeOption]? = isFixed
            ? TimetableScope.options(kind: .fixed, isToday: date == KST.today(), sameDay: true, crossesMidnight: crosses,
                                     weekdayLabel: "\(KST.weekdayLabel[KST.weekdayIndex(date)])요일")
            : nil
        let effective = TimetableScope.effective(chosen, in: options)
        let saving = board.editSaving
        SheetScaffold(title: title, description: description, dismissible: !saving) {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 8) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("시작").font(.pt(12, .medium)).foregroundStyle(Color.ink3)
                        TimetableClockPicker(minutes: $start, label: "시작 시각")
                    }
                    VStack(alignment: .leading, spacing: 4) {
                        Text("종료").font(.pt(12, .medium)).foregroundStyle(Color.ink3)
                        TimetableClockPicker(minutes: $end, label: "종료 시각")
                    }
                }
                Text(valid ? "\(KST.formatDuration(e - s))\(crosses ? " · 다음 날까지 이어져요" : "")" : "시작과 종료 시각을 다르게 입력해 주세요.")
                    .font(.pt(12))
                    .foregroundStyle(Color.ink3)
                if let options {
                    TimetableScopeChips(options: options, value: effective, onChange: { chosen = $0 })
                }
            }
        } footer: {
            NButton("취소", variant: .secondary) { board.close() }
                .disabled(saving)
            NButton("저장", loading: saving) {
                if case .fixed(let b) = editing.target {
                    board.sheetTrack("fixed_detail.time_save", ["pattern_type": b.patternType.rawValue,
                                                                "change_scope": effective.rawValue, "crosses_midnight": e > 1440])
                }
                Task {
                    await board.submitEdit(editing, value: TimetableTimeValue(date: date, start: s, end: e), scope: effective)
                }
            }
            .disabled(!valid)
        }
    }
}

#Preview("고정 시간") {
    let d = KST.today()
    let board = TimetableBoardModel.sample(start: d, end: d)
    let block = TimetableSampleData.day(d, index: 2).fixedBlocks[0]
    return Color.canvas
        .sheet(isPresented: .constant(true)) {
            TimetableFixedDetailSheet(board: board, block: block)
        }
}

#Preview("시간 입력") {
    let d = KST.today()
    let board = TimetableBoardModel.sample(start: d, end: d)
    let block = TimetableSampleData.day(d, index: 2).fixedBlocks[0]
    let editing = TimetableTimeEditing(target: .fixed(block), initial: TimetableTimeValue(date: d, start: 720, end: 780))
    return Color.canvas
        .sheet(isPresented: .constant(true)) {
            TimetableTimeEditSheet(board: board, editing: editing)
        }
}
