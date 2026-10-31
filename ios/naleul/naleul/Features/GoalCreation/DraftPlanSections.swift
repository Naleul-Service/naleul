// Features/GoalCreation/DraftPlanSections.swift
//
// 초안 검토 화면의 카드들: 목표 · 수치 · 루틴 · 일회성 Task · 마일스톤 · 세부 목표.
// 웹 원본: features/goal-creation/ui/draft/PlanSections.tsx
//
// 웹은 항목을 누르면 그 자리에서 편집기가 열려요. 아이폰은 화면이 좁아서 → 아래에서 올라오는 시트로 열어요.
// 각 항목에는 .id("tasks[1]") 처럼 서버 violations 의 path 와 같은 키를 붙여서, 문제 항목으로 스크롤할 수 있게 해요.

import SwiftUI

/// 항목 키 → 고쳐야 할 이유들
typealias DraftIssueMap = [String: [String]]

// MARK: - 공통

/// 검증 실패 표시 틀: 문제가 있으면 빨간 테두리 + 메시지
struct DraftIssueFrame<Content: View>: View {
    let vkey: String
    let issues: DraftIssueMap
    @ViewBuilder var content: () -> Content

    init(_ vkey: String, issues: DraftIssueMap, @ViewBuilder content: @escaping () -> Content) {
        self.vkey = vkey
        self.issues = issues
        self.content = content
    }

    var body: some View {
        let messages = issues[vkey]
        VStack(alignment: .leading, spacing: 0) {
            content()
            if let messages {
                VStack(alignment: .leading, spacing: 2) {
                    ForEach(messages, id: \.self) { m in Text("· \(m)") }
                }
                .font(.pt(13, .medium))
                .foregroundStyle(Color.danger)
                .padding(.horizontal, 12)
                .padding(.bottom, 10)
            }
        }
        .background(messages != nil ? Color.dangerSoft.opacity(0.4) : Color.clear, in: RoundedRectangle(cornerRadius: Radius.xxl))
        .overlay {
            if messages != nil {
                RoundedRectangle(cornerRadius: Radius.xxl).stroke(Color.danger, lineWidth: 2)
            }
        }
        .id(vkey)
    }
}

/// 제목 있는 카드 (웹 PanelCard)
struct DraftPanelCard<Content: View>: View {
    let title: String
    var aside: String? = nil
    let vkey: String
    let issues: DraftIssueMap
    @ViewBuilder var content: () -> Content

    init(_ title: String, aside: String? = nil, vkey: String, issues: DraftIssueMap,
         @ViewBuilder content: @escaping () -> Content) {
        self.title = title
        self.aside = aside
        self.vkey = vkey
        self.issues = issues
        self.content = content
    }

    var body: some View {
        let messages = issues[vkey]
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text(title).font(.pt(16, .semibold)).foregroundStyle(Color.ink)
                Spacer(minLength: 8)
                if let aside { Text(aside).font(.pt(13)).foregroundStyle(Color.ink3) }
            }
            .padding(.bottom, 16)
            if let messages {
                VStack(alignment: .leading, spacing: 2) {
                    ForEach(messages, id: \.self) { m in Text("· \(m)") }
                }
                .font(.pt(13, .medium))
                .foregroundStyle(Color.danger)
                .padding(.bottom, 12)
            }
            content()
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.surface, in: RoundedRectangle(cornerRadius: Radius.card))
        .overlay(RoundedRectangle(cornerRadius: Radius.card).stroke(messages != nil ? Color.danger : Color.line,
                                                            lineWidth: messages != nil ? 2 : 1))
        .id(vkey)
    }
}

/// 점선 "+ 추가" 버튼
struct DraftAddButton: View {
    let label: String
    var disabled = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                NSymbol("plus", size: 14, weight: .semibold)
                Text(label)
            }
            .font(.pt(14, .semibold))
            .foregroundStyle(Color.ink2)
            .frame(maxWidth: .infinity)
            .frame(height: 44)
            .overlay(RoundedRectangle(cornerRadius: Radius.xl)
                .stroke(Color.borderSubtle, style: StrokeStyle(lineWidth: 1, dash: [4, 3])))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(disabled)
        .opacity(disabled ? 0.4 : 1)
        .padding(.top, 12)
    }
}

private struct DraftEmojiBox: View {
    let emoji: String?
    var tone: Color = .canvas

    var body: some View {
        Text((emoji ?? "").isEmpty ? "•" : (emoji ?? ""))
            .font(.system(size: 18))
            .frame(width: 40, height: 40)
            .background(tone, in: RoundedRectangle(cornerRadius: Radius.xl))
            .accessibilityHidden(true)
    }
}

private struct DraftPencil: View {
    var size: CGFloat = 13
    var body: some View {
        NSymbol("pencil", size: size, weight: .medium).foregroundStyle(Color.ink4)
    }
}

// MARK: - 목표 카드

struct DraftGoalHeroCard<StyleChips: View>: View {
    let plan: DraftGoalPlan
    let issues: DraftIssueMap
    let colors: [UserColor]
    @Binding var colorId: Int?
    let onEdit: (DraftEditTarget) -> Void
    @ViewBuilder var styleChips: () -> StyleChips

    init(plan: DraftGoalPlan, issues: DraftIssueMap, colors: [UserColor], colorId: Binding<Int?>,
         onEdit: @escaping (DraftEditTarget) -> Void, @ViewBuilder styleChips: @escaping () -> StyleChips) {
        self.plan = plan
        self.issues = issues
        self.colors = colors
        self._colorId = colorId
        self.onEdit = onEdit
        self.styleChips = styleChips
    }

    private var periodLine: String {
        let goal = plan.goal
        let days = DraftPlanUtils.daysBetween(goal.startDate, goal.endDate)
        let weeks = max(Int(ceil(Double(days) / 7)), 1)
        var line = "\(DraftPlanUtils.formatYmdDot(goal.startDate)) – \(DraftPlanUtils.formatYmdDot(goal.endDate)) · \(weeks)주"
        if let m = goal.metric {
            let perWeek = (m.targetValue - m.startValue) / Double(weeks)
            if perWeek.isFinite {
                line += " · 주 평균 \(perWeek > 0 ? "+" : "")\(String(format: "%.1f", perWeek))\(m.unit ?? "")"
            }
        }
        return line
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            FlowLayout(spacing: 8) {
                Badge("AI가 설계한 목표", tone: .ai, icon: "sparkles")
                styleChips()
            }

            DraftIssueFrame("goal", issues: issues) {
                Button { onEdit(.goal) } label: {
                    VStack(alignment: .leading, spacing: 6) {
                        HStack(alignment: .firstTextBaseline, spacing: 8) {
                            (Text(plan.goal.emoji.map { "\($0) " } ?? "") + Text(plan.goal.title))
                                .font(.pt(20, .semibold))
                                .foregroundStyle(Color.ink)
                                .multilineTextAlignment(.leading)
                            DraftPencil(size: 15)
                        }
                        Text(periodLine).font(.pt(14)).foregroundStyle(Color.ink3)
                    }
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, -8)
            .padding(.top, 12)

            if let note = plan.goal.aiNote, !note.isEmpty {
                Text("💡 \(note)")
                    .font(.pt(14)).foregroundStyle(Color.ink).lineSpacing(3)
                    .padding(.horizontal, 16).padding(.vertical, 14)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.bgSunken, in: RoundedRectangle(cornerRadius: Radius.xxl))
                    .padding(.top, 16)
            }

            // 목표 색상
            VStack(alignment: .leading, spacing: 8) {
                Text("목표 색상").font(.pt(13, .medium)).foregroundStyle(Color.ink3)
                FlowLayout(spacing: 8) {
                    Button { colorId = nil } label: {
                        Text("자동")
                            .font(.pt(12, .semibold))
                            .foregroundStyle(colorId == nil ? Color.actionPrimaryFg : Color.ink2)
                            .padding(.horizontal, 12)
                            .frame(height: 32)
                            .background(colorId == nil ? Color.actionPrimary : Color.clear, in: Capsule())
                            .overlay(Capsule().stroke(colorId == nil ? Color.actionPrimary : Color.borderSubtle, lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(colorId == nil ? .isSelected : [])
                    ForEach(colors) { c in
                        let on = colorId == c.userColorId
                        Button { colorId = c.userColorId } label: {
                            Circle()
                                .fill(c.color)
                                .frame(width: 32, height: 32)
                                .padding(3)
                                .overlay(Circle().stroke(on ? Color.ink : Color.clear, lineWidth: 2))
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("색상 \(c.hex)")
                        .accessibilityAddTraits(on ? .isSelected : [])
                    }
                }
            }
            .padding(.top, 20)
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.surface, in: RoundedRectangle(cornerRadius: Radius.card))
        .overlay(RoundedRectangle(cornerRadius: Radius.card).stroke(Color.line, lineWidth: 1))
    }
}

// MARK: - 마일스톤 타임라인

struct DraftMilestoneTimeline: View {
    let plan: DraftGoalPlan
    let issues: DraftIssueMap
    let onEdit: (DraftEditTarget) -> Void

    var body: some View {
        let milestones = plan.milestones
        let goal = plan.goal
        let unit = goal.metric?.unit ?? ""
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text("마일스톤").font(.pt(16, .semibold)).foregroundStyle(Color.ink)
                Spacer(minLength: 8)
                Text("\(milestones.count)단계\(goal.metric != nil ? " · 수치는 자동 계산" : "")")
                    .font(.pt(13)).foregroundStyle(Color.ink3)
            }
            .padding(.bottom, 16)
            if let messages = issues["milestones"] {
                VStack(alignment: .leading, spacing: 2) {
                    ForEach(messages, id: \.self) { m in Text("· \(m)") }
                }
                .font(.pt(13, .medium)).foregroundStyle(Color.danger)
                .padding(.bottom, 12)
            }

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(alignment: .top, spacing: 4) {
                    ForEach(milestones.indices, id: \.self) { i in
                        milestoneCell(i, last: i == milestones.count - 1, unit: unit)
                    }
                }
                .padding(.bottom, 8)
            }

            DraftAddButton(label: "마일스톤 추가", disabled: milestones.count >= DraftPlanUtils.Limits.milestones.max) {
                onEdit(.milestone(nil))
            }
        }
        .id("milestones")
    }

    private func milestoneCell(_ i: Int, last: Bool, unit: String) -> some View {
        let ms = plan.milestones[i]
        let days = DraftPlanUtils.daysBetween(plan.goal.startDate, ms.dueDate)
        // 한 달 안쪽은 주 단위로 (2주 목표에 "1개월"이라고 나오지 않게)
        let when = days < 28 ? "\(max(Int((Double(days) / 7).rounded()), 1))주" : "\(Int((Double(days) / 30).rounded()))개월"
        let dotFill: Color = i == 0 ? .brandSoft : (last ? .success : .surface)
        let dotStroke: Color = i == 0 ? .brand : (last ? .success : .lineStrong)
        return VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 0) {
                Circle().fill(dotFill)
                    .frame(width: 20, height: 20)
                    .overlay(Circle().stroke(dotStroke, lineWidth: 3))
                if !last { Rectangle().fill(Color.lineStrong).frame(height: 2) }
            }
            .accessibilityHidden(true)
            DraftIssueFrame("milestones[\(i)]", issues: issues) {
                Button { onEdit(.milestone(i)) } label: {
                    VStack(alignment: .leading, spacing: 4) {
                        HStack(spacing: 4) {
                            Text("\(when) · ~\(DraftPlanUtils.formatMd(ms.dueDate, withWeekday: false))")
                            Spacer(minLength: 0)
                            DraftPencil(size: 11)
                        }
                        .font(.pt(12)).foregroundStyle(Color.ink3)
                        HStack(spacing: 6) {
                            Text(ms.title).font(.pt(15, .bold)).foregroundStyle(Color.ink)
                                .multilineTextAlignment(.leading)
                            if let v = ms.targetValue {
                                Badge("\(DraftPlanUtils.num(v))\(unit)", tone: last ? .success : .brand)
                            }
                        }
                        if let d = ms.description, !d.isEmpty {
                            Text(d).font(.pt(13)).foregroundStyle(Color.ink3).lineLimit(2)
                                .multilineTextAlignment(.leading)
                        }
                    }
                    .padding(8)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
            .padding(.trailing, 12)
        }
        .frame(width: 190, alignment: .leading)
    }
}

// MARK: - 수치 카드 (파란 카드)

/// 계획 곡선 (시작값 → 마일스톤 수치) — 웹 SVG 를 Canvas 로
private struct DraftPlanCurve: View {
    let plan: DraftGoalPlan

    private var points: [(date: String, value: Double)] {
        guard let metric = plan.goal.metric else { return [] }
        var list: [(date: String, value: Double)] = [(plan.goal.startDate, metric.startValue)]
        for m in plan.milestones { if let v = m.targetValue { list.append((m.dueDate, v)) } }
        return list
    }

    var body: some View {
        let pts = points
        if pts.count >= 2 {
            Canvas { ctx, size in
                let W = size.width
                let H = size.height
                let pad: CGFloat = 14
                let total = Double(max(DraftPlanUtils.daysBetween(plan.goal.startDate, plan.goal.endDate), 1))
                // 튜플 원소 키패스(\.value) 대신 클로저 — 더 확실하게 컴파일돼요
                let values = pts.map { $0.value }
                let lo = values.min() ?? 0
                let hi = values.max() ?? 0
                let span = (hi - lo) == 0 ? 1 : (hi - lo)
                func x(_ d: String) -> CGFloat {
                    let r = min(max(Double(DraftPlanUtils.daysBetween(plan.goal.startDate, d)), 0), total) / total
                    return pad + CGFloat(r) * (W - pad * 2)
                }
                func y(_ v: Double) -> CGFloat { pad + 12 + CGFloat((hi - v) / span) * (H - pad * 2 - 12) }

                var path = Path()
                for (i, p) in pts.enumerated() {
                    let pt = CGPoint(x: x(p.date), y: y(p.value))
                    if i == 0 { path.move(to: pt) } else { path.addLine(to: pt) }
                }
                ctx.stroke(path, with: .color(Color.textBrand), style: StrokeStyle(lineWidth: 2, dash: [5, 4]))
                for (i, p) in pts.enumerated() {
                    let c = CGPoint(x: x(p.date), y: y(p.value))
                    let r: CGFloat = i == pts.count - 1 ? 5 : 4
                    ctx.fill(Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2)), with: .color(Color.textBrand))
                    if i > 0 {
                        ctx.draw(Text(DraftPlanUtils.num(p.value)).font(.pt(11, .bold)).foregroundColor(Color.textPrimary),
                                 at: CGPoint(x: c.x, y: c.y - 14))
                    }
                }
            }
            .frame(height: 110)
            .padding(.top, 8)
            .accessibilityLabel("계획 곡선")
        }
    }
}

struct DraftMetricCard: View {
    let plan: DraftGoalPlan
    let onEditGoal: () -> Void

    var body: some View {
        let goal = plan.goal
        let routines = plan.tasks.filter(\.isRoutine)
        let oneTimes = plan.tasks.count - routines.count
        let weeklyCount = routines.reduce(0) { $0 + ($1.routineDays?.count ?? 0) }
        let remain = DraftPlanUtils.dDay(goal.endDate)
        // 기간별로 나눈 루틴(이론 → 기출)이 있으면 단계 수를 보여줘요 (V52: 마일스톤 대신)
        let phases = Set(routines.map { "\($0.startDate ?? "")~\($0.endDate ?? "")" }).count
        // body(ViewBuilder) 안에서는 배열을 고칠 수 없어서 클로저로 만들어요
        let milestoneCount = plan.milestones.count
        let stats: [(String, String)] = {
            var s: [(String, String)] = [
                ("루틴", "\(routines.count)개"),
                ("주간 실천", "주 \(weeklyCount)회"),
                ("일회성 Task", "\(oneTimes)개"),
            ]
            if phases > 1 {
                s.append(("단계", "\(phases)단계"))
            } else if milestoneCount > 0 {
                s.append(("마일스톤", "\(milestoneCount)단계"))
            }
            return s
        }()
        VStack(alignment: .leading, spacing: 0) {
            if let m = goal.metric {
                HStack {
                    Text("현재")
                    Spacer()
                    Text("목표")
                }
                .font(.pt(13)).foregroundStyle(Color.textSecondary)
                HStack(spacing: 8) {
                    (Text(DraftPlanUtils.num(m.startValue)) + Text(m.unit ?? "").font(.pt(20, .semibold)))
                        .font(.pt(28, .bold))
                    Spacer(minLength: 0)
                    NSymbol("arrow.right", size: 22).foregroundStyle(Color.textSecondary)
                    Spacer(minLength: 0)
                    (Text(DraftPlanUtils.num(m.targetValue)) + Text(m.unit ?? "").font(.pt(20, .semibold)))
                        .font(.pt(28, .bold))
                }
                .foregroundStyle(Color.textPrimary)
                .padding(.top, 4)
            } else {
                Text("기간").font(.pt(13)).foregroundStyle(Color.textSecondary)
                Text("\(DraftPlanUtils.formatMd(goal.startDate, withWeekday: false)) → \(DraftPlanUtils.formatMd(goal.endDate, withWeekday: false))")
                    .font(.pt(24, .semibold)).foregroundStyle(Color.textPrimary)
                    .padding(.top, 4)
            }

            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule().fill(Color.progressTrack)
                    Capsule().fill(Color.progressFill).frame(width: geo.size.width * 0.03)
                }
            }
            .frame(height: 6)
            .padding(.top, 16)
            HStack {
                Text("시작 \(DraftPlanUtils.formatMd(goal.startDate, withWeekday: false))")
                Spacer()
                Text(remain >= 0 ? "D-\(remain)" : "종료")
            }
            .font(.pt(13)).foregroundStyle(Color.textSecondary)
            .padding(.top, 8)

            if let m = goal.metric {
                Text("계획 \(m.name) 곡선").font(.pt(13)).foregroundStyle(Color.textSecondary).padding(.top, 20)
                DraftPlanCurve(plan: plan)
            }

            Button(action: onEditGoal) {
                HStack(spacing: 4) {
                    NSymbol("pencil", size: 13, weight: .semibold)
                    Text(goal.metric != nil ? "시작값·목표값·종료일 고치기" : "종료일 고치기")
                }
                .font(.pt(13, .semibold))
                .foregroundStyle(Color.textPrimary)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(Color.bgSurface.opacity(0.7), in: Capsule())
            }
            .buttonStyle(.plain)
            .padding(.top, 12)

            LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 10) {
                ForEach(stats.indices, id: \.self) { i in
                    VStack(alignment: .leading, spacing: 2) {
                        Text(stats[i].0).font(.pt(12)).foregroundStyle(Color.textSecondary)
                        Text(stats[i].1).font(.pt(20, .semibold)).foregroundStyle(Color.textPrimary)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.bgSurface.opacity(0.7), in: RoundedRectangle(cornerRadius: Radius.xxl))
                }
            }
            .padding(.top, 20)
        }
        .padding(24)
        .frame(maxWidth: .infinity, alignment: .leading)
        // AI가 설계한 목표 요약: ai/tint-soft + AI 그라데이션 테두리
        .aiBorder(tinted: true)
    }
}

// MARK: - 세부 목표

struct DraftSubGoalsCard: View {
    let plan: DraftGoalPlan
    let issues: DraftIssueMap
    let onEdit: (DraftEditTarget) -> Void

    private static let tones: [Color] = [.warningSoft, .brandSoft, .successSoft, .dangerSoft]

    var body: some View {
        DraftPanelCard("세부 목표", aside: "\(plan.subGoals.count)개", vkey: "subGoals", issues: issues) {
            VStack(spacing: 10) {
                ForEach(plan.subGoals.indices, id: \.self) { i in
                    let sg = plan.subGoals[i]
                    DraftIssueFrame("subGoals[\(i)]", issues: issues) {
                        Button { onEdit(.subGoal(i)) } label: {
                            HStack(alignment: .top, spacing: 12) {
                                DraftEmojiBox(emoji: sg.emoji, tone: Self.tones[i % Self.tones.count])
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(sg.title).font(.pt(15, .bold)).foregroundStyle(Color.ink)
                                    if let d = sg.description, !d.isEmpty {
                                        Text(d).font(.pt(13)).foregroundStyle(Color.ink3).lineSpacing(2)
                                    }
                                }
                                .multilineTextAlignment(.leading)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                DraftPencil().padding(.top, 4)
                            }
                            .padding(16)
                            .overlay(RoundedRectangle(cornerRadius: Radius.xxl).stroke(Color.line, lineWidth: 1))
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            DraftAddButton(label: "세부 목표 추가", disabled: plan.subGoals.count >= DraftPlanUtils.Limits.subGoals.max) {
                onEdit(.subGoal(nil))
            }
        }
    }
}

// MARK: - 생성될 Task (일회성)

struct DraftOneTimeTasksCard: View {
    let plan: DraftGoalPlan
    let issues: DraftIssueMap
    let onEdit: (DraftEditTarget) -> Void

    /// 원래 배열 위치(index)를 유지해야 서버 violations 의 tasks[i] 와 맞아요
    private var items: [(task: DraftPlanTask, index: Int)] {
        plan.tasks.enumerated()
            .filter { !$0.element.isRoutine }
            .map { (task: $0.element, index: $0.offset) }
            .sorted { ($0.task.scheduledDate ?? $0.task.dueDate ?? "") < ($1.task.scheduledDate ?? $1.task.dueDate ?? "") }
    }

    private func subGoalName(_ t: DraftPlanTask) -> String? {
        plan.subGoals.first { $0.tempId == t.subGoalTempId }?.title
    }

    var body: some View {
        let list = items
        DraftPanelCard("생성될 Task", aside: "일회성 · \(list.count)개", vkey: "tasks", issues: issues) {
            if list.isEmpty {
                Text("따로 챙길 일회성 Task는 없어요. 위의 루틴만 꾸준히 하면 돼요.")
                    .font(.pt(13)).foregroundStyle(Color.ink3).lineSpacing(2)
                    .padding(.horizontal, 8).padding(.vertical, 8)
            }
            VStack(spacing: 0) {
                ForEach(list.indices, id: \.self) { n in
                    let task = list[n].task
                    let index = list[n].index
                    if n > 0 { Divider().overlay(Color.borderSoft) }
                    DraftIssueFrame("tasks[\(index)]", issues: issues) {
                        Button { onEdit(.task(index, .ONE_TIME)) } label: {
                            HStack(spacing: 12) {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("\(task.emoji ?? "") \(task.title)")
                                        .font(.pt(15, .semibold)).foregroundStyle(Color.ink).lineLimit(1)
                                    Text(oneTimeLine(task)).font(.pt(13)).foregroundStyle(Color.ink3)
                                }
                                .frame(maxWidth: .infinity, alignment: .leading)
                                if let name = subGoalName(task) {
                                    Badge(name).frame(maxWidth: 96)
                                }
                                DraftPencil()
                            }
                            .padding(.horizontal, 8)
                            .padding(.vertical, 12)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(.vertical, 4)
                }
            }
            DraftAddButton(label: "Task 추가", disabled: list.count >= DraftPlanUtils.Limits.oneTimes.max) {
                onEdit(.task(nil, .ONE_TIME))
            }
        }
    }

    private func oneTimeLine(_ t: DraftPlanTask) -> String {
        var s = t.scheduledDate.map { DraftPlanUtils.formatMd($0) } ?? "날짜 자동"
        if let d = t.dueDate { s += " · 마감 \(DraftPlanUtils.formatMd(d, withWeekday: false))" }
        return s + " · \(t.durationMinutes)분"
    }
}

// MARK: - 필요 루틴

struct DraftRoutinesCard: View {
    let plan: DraftGoalPlan
    let issues: DraftIssueMap
    let onEdit: (DraftEditTarget) -> Void

    private var items: [(task: DraftPlanTask, index: Int)] {
        plan.tasks.enumerated().filter { $0.element.isRoutine }.map { (task: $0.element, index: $0.offset) }
    }

    /// 목표 기간 전체가 아니면 "10.21부터", "~10.20", "10.07~10.20"
    private func periodOf(_ t: DraftPlanTask) -> String? {
        let from = (t.startDate.map { $0 > plan.goal.startDate } ?? false) ? t.startDate : nil
        let to = (t.endDate.map { $0 < plan.goal.endDate } ?? false) ? t.endDate : nil
        let f = { (v: String) in DraftPlanUtils.formatMd(v, withWeekday: false) }
        if let from, let to { return "\(f(from))~\(f(to))" }
        if let from { return "\(f(from))부터" }
        if let to { return "~\(f(to))" }
        return nil
    }

    var body: some View {
        let list = items
        DraftPanelCard("필요 루틴", aside: "반복 · \(list.count)개", vkey: "routines", issues: issues) {
            VStack(spacing: 0) {
                ForEach(list.indices, id: \.self) { n in
                    if n > 0 { Divider().overlay(Color.borderSoft) }
                    routineRow(list[n].task, index: list[n].index)
                        .padding(.vertical, 4)
                }
            }
            DraftAddButton(label: "루틴 추가", disabled: list.count >= DraftPlanUtils.Limits.routines.max) {
                onEdit(.task(nil, .ROUTINE))
            }
        }
    }

    private func routineRow(_ task: DraftPlanTask, index: Int) -> some View {
        let period = periodOf(task)
        let meta = Text("\(task.preferredStartTime ?? "시간 자동") · \(task.durationMinutes)분")
        let line = period.map { meta + Text(" · \($0)").font(.pt(13, .semibold)).foregroundColor(Color.textBrand) } ?? meta
        return DraftIssueFrame("tasks[\(index)]", issues: issues) {
            Button { onEdit(.task(index, .ROUTINE)) } label: {
                HStack(alignment: .top, spacing: 12) {
                    Text(task.emoji ?? "").font(.system(size: 18)).frame(width: 24).accessibilityHidden(true)
                    VStack(alignment: .leading, spacing: 0) {
                        HStack(alignment: .top, spacing: 8) {
                            Text(task.title).font(.pt(15, .semibold)).foregroundStyle(Color.ink)
                                .multilineTextAlignment(.leading)
                                .frame(maxWidth: .infinity, alignment: .leading)
                            DraftPencil().padding(.top, 3)
                        }
                        // 예전 목표에서 하던 루틴 — 확정하면 새로 만들지 않고 이어서 써요 (빼면 오늘까지만 하고 끝나요, 2026-10)
                        if task.tempId.hasPrefix("existing-routine-") {
                            Text("지금 하는 루틴")
                                .font(.pt(11, .semibold)).foregroundStyle(Color.ink2)
                                .padding(.horizontal, 6).padding(.vertical, 2)
                                .background(Color.subtle, in: RoundedRectangle(cornerRadius: 6))
                                .padding(.top, 2)
                        }
                        line.font(.pt(13)).foregroundStyle(Color.ink3).padding(.top, 2)
                        HStack(spacing: 4) {
                            ForEach(GoalCreationWeekday.allCases, id: \.self) { d in
                                let on = task.routineDays?.contains(d) ?? false
                                Text(d.label)
                                    .font(.pt(12, .semibold))
                                    .foregroundStyle(on ? Color.white : Color.ink4)
                                    .frame(width: 28, height: 28)
                                    .background(on ? Color.brand : Color.subtle, in: RoundedRectangle(cornerRadius: Radius.lg))
                            }
                        }
                        .padding(.top, 8)
                        if let d = task.description, !d.isEmpty {
                            Text(d)
                                .font(.pt(13)).foregroundStyle(Color.ink2).lineSpacing(2)
                                .multilineTextAlignment(.leading)
                                .padding(.horizontal, 10).padding(.vertical, 6)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(Color.subtle, in: RoundedRectangle(cornerRadius: Radius.lg))
                                .padding(.top, 8)
                        }
                        if let r = task.reason, !r.isEmpty {
                            Text("💡 \(r)").font(.pt(12)).foregroundStyle(Color.ink3).lineSpacing(2)
                                .multilineTextAlignment(.leading)
                                .padding(.top, 6)
                        }
                    }
                }
                .padding(.horizontal, 8)
                .padding(.vertical, 12)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
    }
}

#Preview {
    let plan = GoalCreationSamples.plan
    let issues: DraftIssueMap = ["tasks[0]": ["루틴은 1~1440분으로 설정해 주세요."]]
    return ScrollView {
        VStack(spacing: 20) {
            DraftGoalHeroCard(plan: plan, issues: [:], colors: [
                UserColor(userColorId: 1, hex: "#3D5AFE", isDefault: true),
                UserColor(userColorId: 2, hex: "#12B76A", isDefault: false),
            ], colorId: .constant(nil), onEdit: { _ in }) {
                Text("차근차근 계획형").font(.pt(12, .semibold))
            }
            DraftMetricCard(plan: plan, onEditGoal: {})
            DraftRoutinesCard(plan: plan, issues: issues, onEdit: { _ in })
            DraftOneTimeTasksCard(plan: plan, issues: [:], onEdit: { _ in })
            DraftMilestoneTimeline(plan: plan, issues: [:], onEdit: { _ in })
            DraftSubGoalsCard(plan: plan, issues: [:], onEdit: { _ in })
        }
        .padding(16)
    }
    .canvasBackground()
}
