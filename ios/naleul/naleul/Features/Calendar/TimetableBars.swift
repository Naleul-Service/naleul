// Features/Calendar/TimetableBars.swift
//
// 캘린더 위 · 아래에 뜨는 안내 바들.
// 웹 원본:
//   features/timetable/ui/AutoPlaceBar.tsx     — "AI로 TimeTable 배치하기" (iOS 는 떠 있는 ✨ 버튼)
//   features/timetable/ui/ScopeChooser.tsx     — 드래그 후 "어디까지 바꿀까요?" (ScopeChooser · ScopeChips)
//   features/timetable/ui/FillProposalBar.tsx  — AI 배치 미리보기 "이대로 진행할까요?"
//   features/timetable/ui/CarryProposalBar.tsx — 22시 이월 제안 "옮길까요?"
//   features/timetable/ui/useTimetableInteractions.tsx — 묶음 드래그 안내 바
//
// 웹은 화면 아래에 떠 있는(fixed) 바 → iOS 는 .safeAreaInset(edge: .bottom) 으로 붙여서 격자를 가리지 않게 해요.
// 키보드 단축키(1·2·3, Esc)는 아이폰에 없어서 뺐어요.

import SwiftUI
import UIKit

// MARK: - 아래 바 묶음 (한 번에 하나만)

/// 범위 고르기 > AI 배치 확인 > 묶음 안내 > 이월 제안 순서로 하나만 보여줘요 (웹과 같은 우선순위)
struct TimetableBottomBars: View {
    let board: TimetableBoardModel
    var showCarry = true
    var showFill = true

    var body: some View {
        let groupCount = board.groupIds.count
        Group {
            if let ask = board.ask {
                TimetableScopeChooserBar(title: ask.title, options: ask.options,
                                         onChoose: { board.chooseScopeByUser($0) },
                                         onCancel: { board.cancelAskByUser() })
            } else if let proposals = board.proposals, showFill {
                TimetableFillProposalBar(kind: board.proposalKind, count: proposals.count,
                                         edited: board.editedProposals, unscheduled: board.proposalUnscheduled,
                                         loading: board.fillApplying,
                                         onApply: { Task { await board.applyProposalsNow() } },
                                         onCancel: { board.cancelProposalsByUser() })
            } else if groupCount > 0 {
                TimetableGroupBar(count: groupCount, onClear: { board.clearGroupByUser() })
            } else if showCarry {
                let carry = board.carryProposals
                if let first = carry.first {
                    TimetableCarryProposalBar(tasks: carry, today: KST.today(), busy: board.carryBusy,
                                              onAccept: { t in
                                                  board.sheetTrack("carry_proposal.accept_click", ["task_id": t.taskId, "item_count": carry.count])
                                                  Task { await board.acceptCarry(t) }
                                              },
                                              onDecline: { t in
                                                  board.sheetTrack("carry_proposal.decline_click", ["task_id": t.taskId, "item_count": carry.count])
                                                  Task { await board.declineCarry(t) }
                                              },
                                              onAcceptAll: { d in
                                                  let same = carry.filter { (TimetableRules.blockDay($0) ?? $0.date) == d }.count
                                                  board.sheetTrack("carry_proposal.accept_all_click", ["item_count": same])
                                                  Task { await board.acceptAllCarry(date: d) }
                                              },
                                              onRevert: { t in
                                                  board.sheetTrack("carry_proposal.revert_click", ["task_id": t.taskId, "item_count": carry.count])
                                                  Task { await board.revertCarry(taskId: t.taskId, fromBar: true) }
                                              })
                    // 제안이 하나씩 바뀔 때마다 한 번 (웹 shownId)
                    .onAppear { trackCarryView(first, count: carry.count) }
                    .onChange(of: first.taskId) { trackCarryView(first, count: carry.count) }
                }
            }
        }
        .padding(.horizontal, 12)
        .padding(.bottom, 8)
        .animation(.easeOut(duration: 0.16), value: board.ask == nil)
    }

    private func trackCarryView(_ t: TimeBlockTask, count: Int) {
        board.sheetTrack("carry_proposal.view", ["item_count": count, "days_ago": t.carriedFromDate.map { CAL.daysAgo($0) }])
    }
}

/// 떠 있는 카드 모양 (웹 bg-surface shadow-pop border-line rounded-2xl p-4)
private struct TimetableFloatingCard: ViewModifier {
    func body(content: Content) -> some View {
        content
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.surface, in: RoundedRectangle(cornerRadius: Radius.xxl))
            .overlay(RoundedRectangle(cornerRadius: Radius.xxl).stroke(Color.line, lineWidth: 1))
            .popShadow()
            .frame(maxWidth: 600)
            .transition(.move(edge: .bottom).combined(with: .opacity))
    }
}

/// AI 제안 바 (DESIGN_SPEC §6B · 6D compact): glass 바탕 + 1.5pt AI 그라데이션 테두리 · padding 12/14 · radius 16
private struct TimetableAIProposalCard: ViewModifier {
    func body(content: Content) -> some View {
        content
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .aiBorder(cornerRadius: Radius.card, glass: true)
            .frame(maxWidth: 600)
            .transition(.move(edge: .bottom).combined(with: .opacity))
    }
}

// MARK: - AI로 TimeTable 배치하기 (떠 있는 ✨ 버튼)

/// 화면 오른쪽 아래 떠 있는 동그란 버튼. 시간 미정 Task 가 있으면 색이 켜지고 개수 배지가 붙어요.
/// 직접 정한 시간(📌)과 고정 시간은 그대로 두고 남은 빈 시간에 넣어요.
/// 누르면 바로 저장하지 않고 점선으로 먼저 보여줘요 → 화면 아래에서 "이대로 진행할까요?"
/// (웹 AutoPlaceBar 띠는 격자를 가려서, 모바일에서는 아이콘 버튼으로 바꿨어요)
struct TimetableAIFab: View {
    /// 서버 데이터 그대로 (점선 제안을 덧입히기 전)
    let days: [TimetableDay]
    let today: String
    var loading = false
    let onPreview: (_ startDate: String, _ endDate: String) -> Void

    var body: some View {
        let targets = TimetableRules.unplacedOf(days, today: today)
        let total = targets.reduce(0) { $0 + $1.count }
        let active = total > 0

        Button {
            guard active, let first = targets.first, let last = targets.last else {
                Toast.show("모든 Task가 TimeTable에 배치돼 있어요")
                return
            }
            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
            onPreview(first.date, last.date)
        } label: {
            TimetableAIFabFace(total: total, loading: loading)
        }
        .buttonStyle(TimetableFabPressStyle())
        .disabled(loading)
        .accessibilityLabel("AI로 TimeTable 배치하기")
        .accessibilityValue(active ? "시간 미정 Task \(total)개" : "모두 배치됨")
    }
}

/// ✨ 버튼 모양 (AI 그라데이션 원 + 시간 미정 개수 배지) — 캘린더 AI 메뉴의 라벨로도 써요
struct TimetableAIFabFace: View {
    let total: Int
    var loading = false
    /// true = 시간 미정이 없어도 색이 켜져 있어요 (메뉴라서 늘 누를 수 있을 때)
    var alwaysActive = false

    var body: some View {
        let active = alwaysActive || total > 0
        // 공통 FAB 모양 (56 · 아이콘 22 · 같은 그림자 · DESIGN_SPEC 13.6)
        NFabFace(icon: "sparkles", style: active ? .ai : .idle, loading: loading, badge: total)
    }
}

/// 누르는 동안 살짝 작아지는 버튼 효과
struct TimetableFabPressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.92 : 1)
            .animation(.spring(response: 0.25, dampingFraction: 0.7), value: configuration.isPressed)
    }
}

// MARK: - 범위 고르기 (드래그 직후)

/// 범위 칩 (시간 직접 입력 창 안에서 쓰는 알약 버튼 — 웹 ScopeChips)
struct TimetableScopeChips: View {
    let options: [TimetableScopeOption]
    let value: ChangeScope
    let onChange: (ChangeScope) -> Void

    var body: some View {
        let current = options.first(where: { $0.scope == value })
        let hint = options.first(where: { $0.disabledReason != nil })?.disabledReason ?? current?.hint ?? ""
        VStack(alignment: .leading, spacing: 6) {
            Text("적용 범위").font(.pt(12, .medium)).foregroundStyle(Color.ink3)
            FlowLayout(spacing: 6) {
                ForEach(options) { o in
                    Chip(o.label, selected: value == o.scope, size: .sm) { onChange(o.scope) }
                        .disabled(o.disabledReason != nil)
                        .opacity(o.disabledReason != nil ? 0.4 : 1)
                }
            }
            Text(hint).font(.pt(12)).foregroundStyle(Color.ink3).fixedSize(horizontal: false, vertical: true)
        }
    }
}

/// 드래그로 루틴 · 고정 시간을 옮긴 직후 화면 아래에 뜨는 선택 바
/// X = 취소(원래 자리로). 고르지 않고 다른 블록을 누르거나 끌면 "이날만"으로 저장돼요.
struct TimetableScopeChooserBar: View {
    let title: String
    let options: [TimetableScopeOption]
    var loading = false
    let onChoose: (ChangeScope) -> Void
    let onCancel: () -> Void

    var body: some View {
        let columns = Array(repeating: GridItem(.flexible(), spacing: 8), count: options.count > 3 ? 2 : 3)
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top, spacing: 8) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(.pt(15, .bold)).foregroundStyle(Color.ink).fixedSize(horizontal: false, vertical: true)
                    Text("어디까지 바꿀까요?").font(.pt(12)).foregroundStyle(Color.ink3)
                }
                Spacer(minLength: 0)
                IconButton(systemName: "xmark", size: 14, label: "취소하고 원래대로", action: onCancel)
                    .disabled(loading)
                    .padding(.top, -6)
                    .padding(.trailing, -6)
            }
            LazyVGrid(columns: columns, spacing: 8) {
                ForEach(options) { o in
                    TimetableScopeOptionButton(option: o, disabled: o.disabledReason != nil || loading) { onChoose(o.scope) }
                }
            }
        }
        .modifier(TimetableFloatingCard())
        .accessibilityLabel("적용 범위 고르기")
    }
}

private struct TimetableScopeOptionButton: View {
    let option: TimetableScopeOption
    let disabled: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 5) {
                    NSymbol(TimetableScope.icon(option.scope), size: 12, weight: .semibold)
                        .foregroundStyle(Color.textBrand)
                    Text(option.label)
                        .font(.pt(13, .bold))
                        .foregroundStyle(Color.ink)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                }
                Text(option.disabledReason ?? option.hint)
                    .font(.pt(10.5))
                    .foregroundStyle(Color.ink3)
                    .multilineTextAlignment(.leading)
                    .fixedSize(horizontal: false, vertical: true)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 9)
            .frame(maxWidth: .infinity, minHeight: 74, alignment: .topLeading)
            .background(Color.surface, in: RoundedRectangle(cornerRadius: Radius.xl))
            .overlay(RoundedRectangle(cornerRadius: Radius.xl).stroke(Color.borderSubtle, lineWidth: 1))
            .contentShape(RoundedRectangle(cornerRadius: Radius.xl))
        }
        .buttonStyle(.plain)
        .disabled(disabled)
        .opacity(disabled ? 0.4 : 1)
    }
}

// MARK: - AI 배치 미리보기 확인

/// 점선 블록을 끌어 고친 뒤 [이대로 진행] 하면 저장, [취소] 하면 아무것도 안 바뀌어요
struct TimetableFillProposalBar: View {
    /// fill = AI로 배치하기 · added = 방금 추가한 Task (Task 는 이미 저장됨, 시간만 묻는 중)
    var kind: TimetableProposalKind = .fill
    let count: Int
    /// 사용자가 끌어서 고친 제안 수
    let edited: Int
    /// 빈 시간이 없어 제안하지 못한 수
    let unscheduled: Int
    let loading: Bool
    let onApply: () -> Void
    let onCancel: () -> Void

    private var isNew: Bool { kind == .added }

    private var title: String {
        if isNew {
            return count > 0 ? "새 Task \(count)개를 점선 자리에 놓을게요. 이대로 진행할까요?" : "새 Task를 놓을 빈 시간을 찾지 못했어요"
        }
        return count > 0 ? "점선 자리에 \(count)개를 배치할게요. 이대로 진행할까요?" : "배치할 빈 시간을 찾지 못했어요"
    }

    private var note: String {
        var n = count > 0 ? "마음에 안 드는 블록은 끌어서 옮겨 보세요. 시간 미정 칸으로 끌면 이번 배치에서 빠져요." : ""
        if isNew { n += " Task는 이미 추가됐어요 — 아니요를 누르면 시간 미정으로 남아요." }
        if edited > 0 { n += " 직접 옮긴 \(edited)개는 그 시간에 고정돼요." }
        if unscheduled > 0 { n += " 빈 시간이 없어 \(unscheduled)개는 시간 미정으로 남아요." }
        return n.trimmingCharacters(in: .whitespaces)
    }

    private var cancelTitle: String {
        count == 0 ? "닫기" : (isNew ? "아니요 (시간 미정으로 두기)" : "취소 (반영 안 함)")
    }

    var body: some View {
        let trimmedNote = note
        // 6D compact: ① AIBadge + 제목 + Tag ② 안내 ③ [건너뛰기 ×] [적용 ✓]
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top, spacing: 8) {
                AIBadge()
                Text(title)
                    .font(.labelLG)
                    .tracking(-0.28)
                    .foregroundStyle(Color.textPrimary)
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.top, 5)
                Tag(isNew ? "새 Task" : "새로 배치")
                    .padding(.top, 2)
            }
            if !trimmedNote.isEmpty {
                HStack(alignment: .top, spacing: 6) {
                    NSymbol("info.circle", size: 12).padding(.top, 1)
                    Text(trimmedNote).lineSpacing(2).fixedSize(horizontal: false, vertical: true)
                }
                .font(.captionMD)
                .foregroundStyle(Color.textSecondary)
            }
            HStack(spacing: 8) {
                Spacer(minLength: 0)
                NButton(cancelTitle, variant: .secondary, size: .sm, icon: "xmark", action: onCancel)
                    .disabled(loading)
                if count > 0 {
                    NButton("이대로 진행", variant: .primary, size: .sm, icon: "checkmark", loading: loading, action: onApply)
                }
            }
        }
        .modifier(TimetableAIProposalCard())
        .accessibilityLabel("AI 배치 확인")
    }
}

// MARK: - 22시 이월 제안

/// 못 한 일을 말없이 옮기면 "내가 잘못 넣었나?" 하고 헷갈려서, 옮긴 자리를 점선으로 보여주고 물어봐요.
///   "어제 못한 '보고서 쓰기'를 오늘 15:00–16:00로 옮길까요?"  [옮기기] [안 옮기기]
struct TimetableCarryProposalBar: View {
    let tasks: [TimeBlockTask]
    let today: String
    let busy: TimetableCarryAction?
    let onAccept: (TimeBlockTask) -> Void
    let onDecline: (TimeBlockTask) -> Void
    let onAcceptAll: (String) -> Void
    /// 원래 날로 되돌리기 (2026-10)
    var onRevert: ((TimeBlockTask) -> Void)? = nil

    var body: some View {
        if let t = tasks.first {
            content(t)
        }
    }

    private func content(_ t: TimeBlockTask) -> some View {
        let date = TimetableRules.blockDay(t) ?? t.date ?? today
        let from = t.carriedFromDate.map { TimetableRules.relDay($0, base: today) } ?? "전날"
        let to = TimetableRules.relDay(date, base: today)
        let name = "'\(TimetableRules.title(t.emoji, t.taskName))'"
        let when = t.plannedStartAt != nil ? "\(to) \(KST.hm(t.plannedStartAt))–\(KST.hm(t.plannedEndAt))" : "\(to)(시간 미정)"
        let sameDay = tasks.filter { (TimetableRules.blockDay($0) ?? $0.date) == date }
        let isBusy = busy != nil

        // 6D compact: ① AIBadge + 제목 + Tag ② 시간 + 남은 제안 수 ③ [모두 옮기기] [안 옮기기 ×] [옮기기 ✓]
        return VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top, spacing: 8) {
                AIBadge()
                Text("\(from) 못한 \(TimetableRules.eulReul(name)) \(when)로 옮길까요?")
                    .font(.labelLG)
                    .tracking(-0.28)
                    .foregroundStyle(Color.textPrimary)
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.top, 5)
                Tag("옮기기 제안")
                    .padding(.top, 2)
            }
            HStack(spacing: 4) {
                if let cf = t.carriedFromDate {
                    Text(KST.formatMonthDay(cf))
                    NSymbol("arrow.right", size: 9, weight: .semibold)
                }
                Text("점선으로 표시한 자리예요. 블록을 직접 끌어 옮겨도 돼요.")
                if tasks.count > 1 {
                    Text("· 남은 제안 \(tasks.count)개").font(.bodySMMedium).foregroundStyle(Color.textPrimary)
                }
            }
            .font(.captionMD)
            .foregroundStyle(Color.textSecondary)
            .fixedSize(horizontal: false, vertical: true)
            HStack(spacing: 8) {
                if sameDay.count > 1 {
                    NButton("\(to) \(sameDay.count)개 모두 옮기기", variant: .ghost, size: .sm, loading: busy == .acceptAll) {
                        onAcceptAll(date)
                    }
                    .disabled(isBusy)
                }
                Spacer(minLength: 0)
                if let onRevert, t.carriedFromDate != nil {
                    NButton("원래 날로", variant: .secondary, size: .sm, icon: "arrow.uturn.backward", loading: busy == .revert) { onRevert(t) }
                        .disabled(isBusy)
                }
                NButton("안 옮기기", variant: .secondary, size: .sm, icon: "xmark", loading: busy == .decline) { onDecline(t) }
                    .disabled(isBusy)
                NButton("옮기기", variant: .primary, size: .sm, icon: "checkmark", loading: busy == .accept) { onAccept(t) }
                    .disabled(isBusy)
            }
        }
        .modifier(TimetableAIProposalCard())
        .accessibilityLabel("못 한 일 옮기기")
    }
}

// MARK: - 묶음 드래그 안내

struct TimetableGroupBar: View {
    let count: Int
    let onClear: () -> Void

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "link").font(.system(size: 13, weight: .semibold))
            if count > 1 {
                Text("\(count)개 묶음").font(.pt(13, .bold)) + Text(" · 하나를 끌면 같이 움직여요").font(.pt(13))
            } else {
                Text("1개 선택").font(.pt(13, .bold)) + Text(" · 다른 블록을 눌러 더 묶어요").font(.pt(13))
            }
            Spacer(minLength: 0)
            Button(action: onClear) {
                HStack(spacing: 4) {
                    NSymbol("xmark", size: 11, weight: .bold)
                    Text("묶음 풀기").font(.pt(12, .semibold))
                }
                .padding(.horizontal, 10)
                .frame(height: 28)
                .background(Color.onInk.opacity(0.15), in: Capsule())
            }
            .buttonStyle(.plain)
        }
        .foregroundStyle(Color.onInk)
        .padding(.leading, 16)
        .padding(.trailing, 8)
        .frame(height: 44)
        .frame(maxWidth: 560)
        .background(Color.inverse, in: Capsule())
        .popShadow()
        .transition(.move(edge: .bottom).combined(with: .opacity))
    }
}

#Preview {
    let today = KST.today()
    let days = TimetableSampleData.days(from: today, to: KST.addDays(today, 2))
    var carry = days[1].tasks[1]
    carry.carryPending = true
    carry.carriedFromDate = KST.addDays(today, -1)
    return ScrollView {
        VStack(spacing: 16) {
            HStack(spacing: 16) {
                TimetableAIFab(days: days, today: today) { _, _ in }
                TimetableAIFab(days: [], today: today) { _, _ in }
                TimetableAIFab(days: days, today: today, loading: true) { _, _ in }
            }
            TimetableScopeChooserBar(title: "'아침 스트레칭' → 08:00–08:30",
                                     options: TimetableScope.options(kind: .routine, isToday: true),
                                     onChoose: { _ in }, onCancel: {})
            TimetableScopeChooserBar(title: "☀️ 기상 07:00 → 07:30",
                                     options: TimetableScope.options(kind: .fixed, isToday: false, weekdayLabel: "토요일 아침"),
                                     onChoose: { _ in }, onCancel: {})
            TimetableFillProposalBar(kind: .added, count: 2, edited: 1, unscheduled: 1, loading: false,
                                     onApply: {}, onCancel: {})
            TimetableCarryProposalBar(tasks: [carry], today: today, busy: nil,
                                      onAccept: { _ in }, onDecline: { _ in }, onAcceptAll: { _ in })
            TimetableGroupBar(count: 3, onClear: {})
            TimetableScopeChips(options: TimetableScope.options(kind: .routine, isToday: false, sameDay: false),
                                value: .DAY, onChange: { _ in })
        }
        .padding(16)
    }
    .canvasBackground()
}
