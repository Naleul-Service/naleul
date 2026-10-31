// Features/Calendar/TimetableBlocks.swift
//
// TimeTable 격자 위에 그리는 블록들 (Task · 고정 시간 · 수면 · 실제로 한 일 · 드래그 미리보기).
// 웹 원본: features/timetable/ui/Blocks.tsx (+ TimeGrid.tsx 의 DragGhost · GroupGhost)
//
// 블록 스타일 (Component_ver.2 Event)
//  - Task · 루틴: 목표 tint 바탕(goal/X/bg) + 왼쪽 4pt accent 막대 · 제목 goal/X/fg · 시간 text/secondary
//  - 완료(Done): 제목 앞 accent 원 ✓ / 놓침(Incomplete): 바탕 55% + 1pt accent 테두리
//  - 22시 이월 제안 · AI 제안(Proposal): 바탕 55% + 1.5pt accent 테두리 + ✨ / 직접 정한 시간: 📌
//  - 고정 시간: bg/subtle + 1pt border/subtle + 시계 아이콘
// 위치(top/height)는 격자(TimetableGridView)가 정해서 .frame + .offset 으로 놓아요. 여기서는 모양만 그려요.

import SwiftUI

// MARK: - 공통 모양

enum TimetableShapes {
    /// 화면 범위 밖으로 이어지는 쪽은 모서리를 둥글리지 않아요
    /// squareLeading: 왼쪽은 각지게 — 왼쪽 목표 색 막대가 위아래 끝까지 곧게 (Task · 실제로 한 일 블록, 2026-10 웹과 같게)
    static func block(radius: CGFloat, clippedTop: Bool = false, clippedBottom: Bool = false,
                      squareLeading: Bool = false) -> UnevenRoundedRectangle {
        UnevenRoundedRectangle(topLeadingRadius: clippedTop || squareLeading ? 0 : radius,
                               bottomLeadingRadius: clippedBottom || squareLeading ? 0 : radius,
                               bottomTrailingRadius: clippedBottom ? 0 : radius,
                               topTrailingRadius: clippedTop ? 0 : radius)
    }

    static let dash = StrokeStyle(lineWidth: 1, dash: [3, 2])
    static let thickDash = StrokeStyle(lineWidth: 2, dash: [4, 3])
}

/// 빗금 배경 (고정 시간 · 수면) — 웹 HATCH (repeating-linear-gradient 135deg)
struct TimetableHatchFill: View {
    var background: Color = .hatch
    var line: Color = .hatchLine
    /// 빗금 간격 (DESIGN_SPEC 9.5: 45° · 1px · 6px 마다)
    var spacing: CGFloat = 6

    var body: some View {
        Canvas { ctx, size in
            var path = Path()
            var x: CGFloat = -size.height
            while x < size.width {
                path.move(to: CGPoint(x: x, y: size.height))
                path.addLine(to: CGPoint(x: x + size.height, y: 0))
                x += spacing
            }
            ctx.stroke(path, with: .color(line), lineWidth: 1)
        }
        // self. 를 붙여 View 의 .background(...) 메서드가 아니라 이 구조체의 색 속성임을 분명히 해요
        .background(self.background)
    }
}

// MARK: - Task 색

/// Task 블록 색 계산 (웹 taskColors + TaskBlock 의 proposed/solid)
struct TimetableTaskStyle {
    let done: Bool
    let routine: Bool
    /// 22시 이월 제안 · AI 제안 (확정 전)
    let proposed: Bool
    /// 진한 목표 색 바탕 + 흰 글씨
    let solid: Bool
    /// 놓친 Task (Incomplete)
    let missed: Bool
    /// 중요도 A — 제목 앞 'A' 배지 + 1pt 목표 색 테두리 (웹 Blocks.tsx 와 같게)
    let top: Bool
    /// 목표 색 묶음 (bg / fg / accent)
    let tone: GoalTone
    /// 목표 accent 색
    var goal: Color { tone.accent }

    init(_ t: TimeBlockTask) {
        done = t.taskStatus == .COMPLETED
        routine = t.sourceType == .ROUTINE
        proposed = (t.carryPending == true || t.proposed == true) && !done
        solid = !routine && !proposed
        missed = t.missed
        top = (t.taskPriority ?? .fallback).isTop
        tone = Color.goalTone(t.goalColorCode)
    }

    var titleColor: Color { tone.fg }
    var subColor: Color { .textSecondary }
    /// 바탕을 55% 로 흐리게 (Proposal · Incomplete)
    var softBackground: Bool { proposed || missed }
}

/// Event 바탕: surface 위 목표 tint (제안 · 놓침은 55%) + 왼쪽 accent 막대
///  - block: 캘린더 TimeBlock (2026-10) — 완료 = 목표 색 바탕, 아직 안 한 일 = 바탕 없이 (테두리는 블록이 그려요)
///  - barWidth: 왼쪽 막대 굵기 (주간처럼 좁은 칸은 2.5)
struct TimetableTaskBackground: View {
    let style: TimetableTaskStyle
    var block = false
    var barWidth: CGFloat = 4

    var body: some View {
        // 아래 고정 시간 바탕이 비치지 않도록 surface 위에 tint 를 겹쳐요
        ZStack(alignment: .leading) {
            Color.bgSurface
            if !block || style.done || style.proposed {
                style.tone.bg.opacity(style.softBackground ? 0.55 : 1)
            }
            Rectangle().fill(style.tone.accent).frame(width: barWidth)
        }
    }
}

// MARK: - Task 블록

struct TimetableTaskBlockView: View {
    let placed: TimetableLayout.Placed<TimeBlockTask>
    /// 블록 높이 (pt)
    let heightPx: CGFloat
    /// 주간처럼 칸이 좁을 때 글자를 작게
    var compact = false
    /// 일간 "계획" 칸: 완료했어도 계획한 시각으로 그렸으니 "실제" 표시를 하지 않아요
    var planned = false
    var selected = false
    /// 드래그 중인 원래 자리
    var dimmed = false
    /// 확인을 기다리는 블록에 눈이 가도록 흐리게
    var faded = false
    /// 확인을 기다리는 블록 (테두리 강조)
    var focused = false
    /// 방금 만든 Task (NEW 표시)
    var isNew = false
    /// 묶음 드래그로 같이 옮길 Task (점선 테두리)
    var grouped = false

    var body: some View {
        let t = placed.item
        let style = TimetableTaskStyle(t)
        let short = heightPx < 26
        let shape = TimetableShapes.block(radius: short ? 5 : Radius.event, clippedTop: placed.clippedTop,
                                          clippedBottom: placed.clippedBottom, squareLeading: true)
        let actual = !planned && (TimetableLayout.shownTime(t)?.actual ?? false)
        let timeText = "\(actual ? "실제 " : "")\(KST.formatMinutes(placed.start)) – \(KST.formatMinutes(placed.end))"

        VStack(alignment: .leading, spacing: 2) {
            TimetableTaskTitleLine(task: t, style: style, compact: compact, isNew: isNew)
            // 시간 줄은 40pt 보다 짧은 블록에선 숨겨요 (주간에서 제목이 보이게 · DESIGN_SPEC 9.8)
            if heightPx >= 40 {
                Text(timeText)
                    .font(.ptMono(compact ? 9 : 11))
                    .foregroundStyle(style.subColor)
                    .lineLimit(1)
            }
        }
        .padding(.leading, compact ? 8 : 12)
        .padding(.trailing, compact ? 4 : 8)
        .padding(.vertical, short ? 0 : (compact ? 3 : 6))
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: short ? .leading : .topLeading)
        .background(TimetableTaskBackground(style: style, block: true, barWidth: compact ? 2.5 : 4))
        .clipShape(shape)
        .overlay {
            // 2026-10: 완료 = 테두리 + 바탕 / 아직 안 한 일 = 테두리만 / 제안 = 1.5pt
            if style.proposed {
                shape.strokeBorder(style.goal, lineWidth: 1.5)
            } else {
                shape.strokeBorder(style.goal, lineWidth: 1)
            }
        }
        .overlay {
            if selected {
                shape.strokeBorder(Color.borderFocus, lineWidth: 2)
            } else if focused {
                shape.strokeBorder(style.goal, lineWidth: 1.5)
            } else if grouped {
                // 묶음: 1.5pt primary 점선 (DESIGN_SPEC 10.5)
                shape.stroke(Color.actionPrimary, style: StrokeStyle(lineWidth: 1.5, dash: [4, 3]))
            }
        }
        .shadow(color: focused ? Color.shadowPrimary : .clear, radius: 6, y: 4)
        .opacity(opacity(done: style.done))
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(style.top ? "중요도 A " : "")\(t.taskName) \(actual ? "실제 " : "")\(KST.formatMinutes(placed.start))~\(KST.formatMinutes(placed.end))")
    }

    private func opacity(done: Bool) -> Double {
        if dimmed { return 0.3 }
        if faded { return 0.5 }
        return done ? 0.75 : 1
    }
}

/// 블록 첫 줄: ✓ / ✕ · 이름 · NEW · 제안 표시 · 📌
private struct TimetableTaskTitleLine: View {
    let task: TimeBlockTask
    let style: TimetableTaskStyle
    let compact: Bool
    let isNew: Bool

    var body: some View {
        let t = task
        HStack(spacing: 2) {
            if style.done {
                TimetableStatusDot(systemName: "checkmark", color: .success, compact: compact, foreground: .onInk)
            }
            if t.missed {
                TimetableStatusDot(systemName: "xmark", color: .actionDanger, compact: compact)
            }
            if style.proposed && !compact {
                NSymbol("sparkles", size: 11, weight: .medium)
                    .foregroundStyle(style.goal)
            }
            if style.top {
                TimetablePriorityMark(priority: .A, color: style.goal, size: compact ? 12 : 14)
                    .padding(.trailing, 1)
            }
            // 주간(compact): Caption/SM 11 Medium · 두 줄까지 · 끝 줄임
            Text(TimetableRules.title(t.emoji, t.taskName))
                .font(.pt(compact ? 11 : 12, compact ? .medium : .semibold))
                .tracking(compact ? -0.22 : -0.24)
                .foregroundStyle(style.titleColor)
                .strikethrough(style.done)
                .lineLimit(compact ? 2 : 1)
                .truncationMode(.tail)
                .frame(maxWidth: .infinity, alignment: .leading)
            if style.routine {
                NSymbol("repeat", size: compact ? 9 : 11, weight: .medium)
                    .foregroundStyle(style.titleColor.opacity(0.8))
                    .accessibilityLabel("루틴")
            }
            if isNew {
                TimetableTinyTag(text: "NEW", background: .actionDanger, foreground: .actionDangerFg, compact: compact)
            }
            if style.proposed {
                TimetableTinyTag(text: t.proposed == true ? "AI 제안" : "옮길까요?", background: .aiTint, foreground: .textBrand, compact: compact)
            }
        }
    }
}

private struct TimetableStatusDot: View {
    let systemName: String
    let color: Color
    let compact: Bool
    var foreground: Color = .white

    var body: some View {
        let size: CGFloat = compact ? 10 : 14
        NSymbol(systemName, size: size * 0.55, weight: .heavy)
            .foregroundStyle(foreground)
            .frame(width: size, height: size)
            .background(color, in: Circle())
    }
}

/// NEW · AI 제안 같은 아주 작은 꼬리표
struct TimetableTinyTag: View {
    let text: String
    let background: Color
    let foreground: Color
    var compact = false

    var body: some View {
        Text(text)
            .font(.pt(compact ? 7 : 9, .bold))
            .foregroundStyle(foreground)
            .lineLimit(1)
            .fixedSize()
            .padding(.horizontal, 3)
            .frame(height: compact ? 11 : 14)
            .background(background, in: RoundedRectangle(cornerRadius: 3))
    }
}

// MARK: - 고정 시간 블록

struct TimetableFixedBlockView: View {
    let placed: TimetableLayout.Placed<FixedBlock>
    let heightPx: CGFloat
    var compact = false
    var selected = false
    var dimmed = false

    var body: some View {
        let b = placed.item
        let shape = TimetableShapes.block(radius: Radius.eventFixed, clippedTop: placed.clippedTop, clippedBottom: placed.clippedBottom)
        // 기본 시간과 다르게 바꾼 날 (지난 날짜는 "앞으로" 변경 때 예전 시간으로 고정해 둔 것이라 표시하지 않아요)
        let changed = b.overridden && b.targetDate >= KST.today()
        ZStack(alignment: .topLeading) {
            // 고정 시간 = 회색 빗금 (bg/sunken + border/soft 빗금 · DESIGN_SPEC 9.5)
            TimetableHatchFill()
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 4) {
                    if !compact {
                        NSymbol("clock", size: 12, weight: .regular).foregroundStyle(Color.textTertiary)
                    }
                    Text(TimetableRules.title(b.emoji, b.title))
                        .font(.pt(compact ? 9.5 : 12))
                        .lineLimit(1)
                    if changed {
                        Text("· 변경").font(.pt(compact ? 9 : 11, .medium)).foregroundStyle(Color.textBrand).lineLimit(1).fixedSize()
                    }
                }
                if heightPx >= 30 {
                    Text("\(KST.formatMinutes(placed.start)) – \(KST.formatMinutes(placed.end))")
                        .font(.ptMono(compact ? 9 : 11))
                        .lineLimit(1)
                }
            }
            .foregroundStyle(Color.textSecondary)
            .padding(.horizontal, compact ? 3 : 8)
            .padding(.vertical, compact ? 3 : 6)
        }
        .clipShape(shape)
        .overlay(shape.strokeBorder(selected ? Color.borderFocus : Color.borderSubtle, lineWidth: selected ? 2 : 1))
        .opacity(dimmed ? 0.4 : 1)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("고정 시간 \(b.title) \(KST.formatMinutes(placed.start))~\(KST.formatMinutes(placed.end))")
    }
}

// MARK: - 수면 블록

/// 수면 블록 배경 — 기상 전(아침) · 취침 후(밤) 2시간만큼 보여요 (고정 시간 빗금보다 진한 남색 톤)
struct TimetableSleepBlockView: View {
    var selected = false
    var dimmed = false

    var body: some View {
        // 수면도 고정 시간과 같은 회색 빗금 (보라색 없음 · DESIGN_SPEC 9.5)
        TimetableHatchFill()
            .overlay {
                if selected {
                    Rectangle().strokeBorder(Color.borderFocus, lineWidth: 2)
                } else {
                    Rectangle().strokeBorder(Color.borderSubtle, lineWidth: 1)
                }
            }
            .opacity(dimmed ? 0.4 : 1)
    }
}

/// 수면 블록의 끌 수 있는 가장자리 — 늘 보이는 손잡이 (아침 블록은 아래 = 기상, 밤 블록은 위 = 취침)
struct TimetableSleepEdgeLabel: View {
    let edge: TimetableLayout.SleepEdge
    /// 기상 · 취침 시각 (그날 0시 기준 분)
    let minutes: Int
    var compact = false
    var draggable = true

    var body: some View {
        let wake = edge == .wake
        let label = "\(wake ? "기상" : "취침") \(KST.minutesToTime(minutes))"
        HStack(spacing: 3) {
            Text("\(wake ? "☀️" : "🌙") \(compact ? KST.minutesToTime(minutes) : label)")
                .font(.pt(compact ? 9 : 11, .semibold))
                .foregroundStyle(Color.sleepInk)
                .lineLimit(1)
            Spacer(minLength: 0)
            if draggable && !compact {
                NSymbol("line.3.horizontal", size: 10, weight: .semibold)
                    .foregroundStyle(Color.sleepInk.opacity(0.7))
            }
        }
        .padding(.horizontal, compact ? 3 : 6)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.sleep)
        .overlay(alignment: wake ? .bottom : .top) {
            Rectangle().fill(Color.borderSubtle).frame(height: 1)
        }
        .accessibilityLabel("수면 · \(label)")
    }
}

// MARK: - 실제로 한 일 블록

/// 계획(Task) 블록과 헷갈리지 않게 흰 바탕 + 초록 왼쪽 선 + ✓ 표시, 목표를 연결했으면 그 색 선
struct TimetableActivityBlockView: View {
    let placed: TimetableLayout.Placed<ActualActivity>
    let heightPx: CGFloat
    var compact = false
    var selected = false

    var body: some View {
        let a = placed.item
        let short = heightPx < 34
        // 목표가 있으면 목표 색, 없으면 초록 — 완료한 Task 와 같은 틀 + 차이: 연한 바탕 · 점선 테두리 · ✓ 글자 (웹 ActivityBlock)
        let tone: GoalTone? = a.goalColorCode.map { Color.goalTone($0) }
        let color: Color = tone?.accent ?? .success
        let tint: Color = tone?.bg ?? .successSoft
        let shape = TimetableShapes.block(radius: Radius.event, clippedTop: placed.clippedTop,
                                          clippedBottom: placed.clippedBottom, squareLeading: true)
        VStack(alignment: .leading, spacing: 2) {
            HStack(spacing: 2) {
                Text("✓").font(.pt(compact ? 8 : 10, .bold)).foregroundStyle(Color.statusSuccessFg)
                Text(TimetableRules.title(a.emoji, a.title))
                    .font(.pt(compact ? 9.5 : 11, .semibold))
                    .foregroundStyle(tone?.fg ?? Color.textPrimary)
                    .lineLimit(1)
            }
            if !short {
                Text("실제 \(KST.formatMinutes(placed.start))–\(KST.formatMinutes(placed.end))")
                    .font(.ptMono(compact ? 9 : 11))
                    .foregroundStyle(Color.textSecondary)
                    .lineLimit(1)
            }
            if heightPx >= 56, let r = a.replacedTaskName {
                Text("대신: \(r)").font(.pt(compact ? 9 : 11)).foregroundStyle(Color.textTertiary).lineLimit(1)
            }
        }
        .padding(.leading, compact ? 6 : 10)
        .padding(.trailing, 3)
        .padding(.vertical, short ? 0 : (compact ? 3 : 6))
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: short ? .leading : .topLeading)
        .background {
            ZStack(alignment: .leading) {
                Color.bgSurface
                tint.opacity(0.55)
                Rectangle().fill(color).frame(width: compact ? 2.5 : 4)
            }
        }
        .clipShape(shape)
        .overlay {
            if selected {
                shape.strokeBorder(Color.borderFocus, lineWidth: 2)
            } else {
                // 흐린 실선 — 점선은 묶어 옮기기 표시와 헷갈려서 쓰지 않아요
                shape.strokeBorder(color.opacity(0.45), lineWidth: 1)
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("실제로 한 일 \(a.title) \(KST.formatMinutes(placed.start))~\(KST.formatMinutes(placed.end))")
    }
}

// MARK: - 드래그 중 놓일 자리 미리보기

struct TimetableDragGhostView: View {
    let title: String
    /// Task 면 그 색, 고정 시간이면 nil (빗금)
    let taskStyle: TimetableTaskStyle?
    let start: Int
    let end: Int
    let invalid: String?
    let heightPx: CGFloat
    var showTime = true

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: Radius.event)
        VStack(alignment: .leading, spacing: 1) {
            Text(title).font(.pt(11, .semibold)).lineLimit(1)
            if showTime && heightPx >= 30 {
                Text("\(KST.formatMinutes(start)) – \(KST.formatMinutes(end))").font(.ptMono(11, .bold)).lineLimit(1)
            }
            if let invalid {
                Spacer(minLength: 0)
                Text(invalid)
                    .font(.pt(10, .semibold))
                    .foregroundStyle(Color.actionDangerFg)
                    .lineLimit(2)
                    .padding(.horizontal, 4)
                    .padding(.vertical, 2)
                    .background(Color.actionDanger, in: RoundedRectangle(cornerRadius: 4))
            }
        }
        .foregroundStyle(taskStyle?.titleColor ?? Color.textSecondary)
        .padding(.leading, taskStyle == nil ? 6 : 10)
        .padding(.trailing, 5)
        .padding(.vertical, 3)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background {
            if let s = taskStyle {
                TimetableTaskBackground(style: s)
            } else {
                Color.bgSubtle
            }
        }
        .clipShape(shape)
        .overlay(shape.strokeBorder(invalid != nil ? Color.statusErrorFg : (taskStyle?.goal ?? Color.borderSubtle),
                                    lineWidth: invalid != nil ? 2 : 1.5))
        .popShadow()
        .allowsHitTesting(false)
    }
}

// MARK: - 범례 (웹 CalendarView Legend)

struct TimetableLegend: View {
    var body: some View {
        FlowLayout(spacing: 12, lineSpacing: 6) {
            item {
                ZStack(alignment: .leading) {
                    Color.bgSurface
                    Rectangle().fill(Color.brand).frame(width: 2)
                }
                .clipShape(RoundedRectangle(cornerRadius: 2))
                .overlay(RoundedRectangle(cornerRadius: 2).strokeBorder(Color.brand, lineWidth: 1))
            } label: { "Task" }
            item {
                ZStack(alignment: .leading) {
                    Color.brandSoft
                    Rectangle().fill(Color.brand).frame(width: 2)
                }
                .clipShape(RoundedRectangle(cornerRadius: 2))
                .overlay(RoundedRectangle(cornerRadius: 2).strokeBorder(Color.brand, lineWidth: 1))
            } label: { "완료" }
            item {
                NSymbol("repeat", size: 10, weight: .medium).foregroundStyle(Color.ink3)
            } label: { "루틴" }
            item {
                ZStack(alignment: .leading) {
                    Color.brandSoft.opacity(0.55)
                    Rectangle().fill(Color.brand).frame(width: 2)
                }
                .clipShape(RoundedRectangle(cornerRadius: 2))
                .overlay(RoundedRectangle(cornerRadius: 2).strokeBorder(Color.brand, lineWidth: 1))
            } label: { "AI 제안" }
            item {
                RoundedRectangle(cornerRadius: 2).fill(Color.bgSubtle)
                    .overlay(RoundedRectangle(cornerRadius: 2).strokeBorder(Color.borderSubtle, lineWidth: 1))
            } label: { "고정 시간" }
            item {
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 2).fill(Color.bgSurface)
                        .overlay(RoundedRectangle(cornerRadius: 2).strokeBorder(Color.borderSubtle, lineWidth: 1))
                    Rectangle().fill(Color.accentMint).frame(width: 2)
                }
            } label: { "실제로 한 일" }
        }
        .accessibilityLabel("범례")
    }

    private func item<S: View>(@ViewBuilder _ swatch: () -> S, label: () -> String) -> some View {
        HStack(spacing: 6) {
            swatch().frame(width: 10, height: 10)
            Text(label()).font(.pt(12)).foregroundStyle(Color.textSecondary)
        }
    }
}

#Preview {
    let d = KST.today()
    let day = TimetableSampleData.day(d, index: 1)
    let tasks = TimetableLayout.placeTasks(day, from: 7 * 60, to: 24 * 60)
    let fixed = TimetableLayout.placeFixed(day, from: 7 * 60, to: 24 * 60)
    let acts = TimetableLayout.placeActivities(d, TimetableSampleData.activities(on: d), from: 7 * 60, to: 24 * 60)
    return ScrollView {
        VStack(alignment: .leading, spacing: 10) {
            ForEach(tasks, id: \.item.taskId) { p in
                TimetableTaskBlockView(placed: p, heightPx: 52, isNew: p.item.taskId == 2001)
                    .frame(width: 220, height: 52)
            }
            if let p = tasks.first {
                TimetableTaskBlockView(placed: p, heightPx: 52, compact: true, focused: true).frame(width: 46, height: 52)
            }
            ForEach(fixed, id: \.item.key) { p in
                TimetableFixedBlockView(placed: p, heightPx: 56).frame(width: 220, height: 56)
            }
            TimetableSleepBlockView()
                .overlay(alignment: .bottom) { TimetableSleepEdgeLabel(edge: .wake, minutes: 420).frame(height: 22) }
                .frame(width: 220, height: 80)
            ForEach(acts, id: \.item.activityId) { p in
                TimetableActivityBlockView(placed: p, heightPx: 56).frame(width: 220, height: 56)
            }
            TimetableDragGhostView(title: "📝 보고서", taskStyle: nil, start: 600, end: 660,
                                   invalid: "이미 Task 2개가 겹쳐 있어요", heightPx: 70)
                .frame(width: 220, height: 70)
            TimetableLegend()
        }
        .padding(16)
    }
    .canvasBackground()
}
