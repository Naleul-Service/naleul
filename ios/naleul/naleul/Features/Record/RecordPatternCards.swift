// Features/Record/RecordPatternCards.swift
//
// 웹 features/record/ui/pattern/RecordWeeklyCard.tsx · RecordRhythmCard.tsx · RecordWhenCard.tsx · RecordRoutineCard.tsx
// 기록형 목표의 "나의 패턴" 카드들. 카드 틀은 나의 패턴과 같은 SectionCard 를 써요.

import SwiftUI

// MARK: - 주차별로 완료한 Task (막대) + 처음부터의 누적 (선)
//
// 막대는 주마다 오르내려도, 누적 선은 절대 내려가지 않아요 → "계속 쌓이고 있다"는 걸 보여주려고 같이 그렸어요.
// 웹 SVG 계산을 Canvas 로 그대로 옮겼어요.

struct RecordWeeklyCard: View {
    let weeks: [RecordWeekStat]

    var body: some View {
        if let last = weeks.last, last.cumulativeCount > 0 {
            let recentAvg = Self.round1(Self.avg(Array(weeks.suffix(4))))
            let prevAvg = Self.round1(Self.avg(Self.previousFour(weeks)))
            SectionCard("주차별로 완료한 Task", aside: { Text("누적 \(last.cumulativeCount)개") }) {
                VStack(alignment: .leading, spacing: 0) {
                    RecordWeeklyCanvas(weeks: weeks)
                        .frame(height: 210)
                        .accessibilityLabel("주차별 완료한 Task와 누적 그래프")
                    HStack(spacing: 16) {
                        HStack(spacing: 6) {
                            RoundedRectangle(cornerRadius: 3).fill(Color.heat2).frame(width: 12, height: 12)
                            Text("그 주에 완료한 Task")
                        }
                        HStack(spacing: 6) {
                            Capsule().fill(Color.ink).frame(width: 16, height: 2)
                            Text("처음부터 누적")
                        }
                    }
                    .font(.pt(12))
                    .foregroundStyle(Color.ink3)
                    .padding(.top, 8)

                    if recentAvg > 0 {
                        averageNote(recentAvg: recentAvg, prevAvg: prevAvg)
                            .padding(.top, 16)
                    }
                }
            }
        } else {
            SectionCard("주차별로 완료한 Task", aside: { Text("최근 12주") }) {
                NotEnoughView(text: "Task를 완료하면 주마다 얼마나 했는지 보여드려요")
            }
        }
    }

    private func averageNote(recentAvg: Double, prevAvg: Double) -> some View {
        let head = Text("최근 4주는 한 주에 평균 ")
            + Text("\(PatternFormat.hours1(recentAvg))개").bold().foregroundColor(Color.textBrand)
        let tail: Text
        if prevAvg > 0 && recentAvg > prevAvg {
            let diff = Text("\(PatternFormat.hours1(Self.round1(recentAvg - prevAvg)))개 더").bold().foregroundColor(Color.textBrand)
            tail = Text(", 그 전 4주보다 ") + diff + Text(" 완료하고 있어요")
        } else {
            tail = Text(" 완료하고 있어요")
        }
        return (head + tail)
            .font(.pt(14))
            .foregroundStyle(Color.ink2)
            .lineSpacing(3)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.subtle, in: RoundedRectangle(cornerRadius: Radius.xxl))
    }

    private static func avg(_ list: [RecordWeekStat]) -> Double {
        Double(list.reduce(0) { $0 + $1.count }) / Double(max(list.count, 1))
    }

    private static func round1(_ n: Double) -> Double { (n * 10).rounded() / 10 }

    /// 웹 weeks.slice(-8, -4)
    private static func previousFour(_ weeks: [RecordWeekStat]) -> [RecordWeekStat] {
        let end = max(weeks.count - 4, 0)
        let start = max(weeks.count - 8, 0)
        return Array(weeks[start..<end])
    }
}

private struct RecordWeeklyCanvas: View {
    let weeks: [RecordWeekStat]

    var body: some View {
        Canvas { ctx, size in
            draw(&ctx, size: size)
        }
    }

    private func draw(_ ctx: inout GraphicsContext, size: CGSize) {
        guard let last = weeks.last, !weeks.isEmpty else { return }
        let padL: CGFloat = 28, padR: CGFloat = 36, padT: CGFloat = 20, padB: CGFloat = 26
        let w = size.width, h = size.height
        let innerW = w - padL - padR
        let innerH = h - padT - padB
        let slot = innerW / CGFloat(weeks.count)
        let barW = min(24, slot * 0.56)
        let maxWeek = Double(max(4, weeks.map(\.count).max() ?? 0))
        let maxCum = Double(max(4, last.cumulativeCount))
        let lastIdx = weeks.count - 1
        let bottom = padT + innerH

        func cx(_ i: Int) -> CGFloat { padL + slot * CGFloat(i) + slot / 2 }
        func yBar(_ m: Double) -> CGFloat { bottom - CGFloat(m / maxWeek) * innerH }
        func yCum(_ m: Int) -> CGFloat { bottom - CGFloat(Double(m) / maxCum) * innerH }

        // 눈금 (0 · 절반 · 최대)
        for t in [0.0, 0.5, 1.0] {
            let y = yBar(maxWeek * t)
            var line = Path()
            line.move(to: CGPoint(x: padL, y: y))
            line.addLine(to: CGPoint(x: w - padR, y: y))
            ctx.stroke(line, with: .color(Color.line), lineWidth: 1)
            ctx.draw(Text("\(Int((maxWeek * t).rounded()))").font(.pt(11)).foregroundColor(Color.ink3),
                     at: CGPoint(x: padL - 8, y: y), anchor: .trailing)
        }

        // 막대 (이번 주만 진한 파랑)
        for (i, wk) in weeks.enumerated() {
            let barH = max(wk.count > 0 ? 3 : 0, bottom - yBar(Double(wk.count)))
            guard barH > 0 else { continue }
            let rect = CGRect(x: cx(i) - barW / 2, y: bottom - barH, width: barW, height: barH)
            ctx.fill(Path(roundedRect: rect, cornerRadius: 5), with: .color(i == lastIdx ? Color.brand : Color.heat2))
        }

        // 누적 선
        var line = Path()
        for (i, wk) in weeks.enumerated() {
            let pt = CGPoint(x: cx(i), y: yCum(wk.cumulativeCount))
            if i == 0 { line.move(to: pt) } else { line.addLine(to: pt) }
        }
        ctx.stroke(line, with: .color(Color.ink), style: StrokeStyle(lineWidth: 2, lineJoin: .round))
        for (i, wk) in weeks.enumerated() {
            let r: CGFloat = i == lastIdx ? 4.5 : 2.5
            let dot = Path(ellipseIn: CGRect(x: cx(i) - r, y: yCum(wk.cumulativeCount) - r, width: r * 2, height: r * 2))
            ctx.fill(dot, with: .color(Color.ink))
            ctx.stroke(dot, with: .color(Color.surface), lineWidth: 1.5)
        }
        ctx.draw(Text("\(last.cumulativeCount)개").font(.pt(11, .bold)).foregroundColor(Color.ink),
                 at: CGPoint(x: cx(lastIdx) + 8, y: yCum(last.cumulativeCount)), anchor: .leading)

        // 아래 날짜 라벨 (하나 걸러, 마지막은 "이번 주")
        for (i, wk) in weeks.enumerated() where i % 2 == lastIdx % 2 {
            let isLast = i == lastIdx
            let label = isLast ? "이번 주" : "\(KST.monthOf(wk.weekStart))/\(KST.dayOfMonth(wk.weekStart))"
            ctx.draw(Text(label).font(.pt(11, isLast ? .bold : .regular)).foregroundColor(isLast ? Color.ink : Color.ink3),
                     at: CGPoint(x: cx(i), y: h - 4), anchor: .bottom)
        }
    }
}

// MARK: - 완료의 리듬 (최근 16주 잔디)
//
// 기록형 목표의 핵심은 "얼마나"보다 "끊기지 않았는지"라서, 빈칸·채운 칸이 한눈에 보이게 했어요.
// 웹 hover 설명은 칸을 탭하면 아래에 한 줄로 보여줘요.

struct RecordRhythmCard: View {
    let rhythm: RecordRhythm
    @State private var picked: String?
    @State private var tapSent = false
    @Environment(\.recordStatsGoalId) private var statsGoalId

    /// 칸을 처음 눌렀을 때 한 번 (record_stats.cell_tap)
    private func trackCellTap() {
        guard !tapSent, let gid = statsGoalId else { return }
        tapSent = true
        WK.track("record_stats.cell_tap", goalId: gid, ["stats_card": "rhythm"])
    }

    // private @State 가 있으면 자동 생성 init 이 private 이 될 수 있어서, 다른 파일에서 부를 수 있게 직접 만들어요
    init(rhythm: RecordRhythm) {
        self.rhythm = rhythm
    }

    private static let rowLabel = ["월", "", "수", "", "금", "", "일"]

    var body: some View {
        SectionCard("완료의 리듬", aside: { Text("최근 16주") }) {
            if rhythm.days.isEmpty {
                NotEnoughView(text: "Task를 완료하면 하루 한 칸씩 채워져요")
            } else {
                filled
            }
        }
    }

    private var filled: some View {
        let today = KST.today()
        let byDate = Dictionary(rhythm.days.map { ($0.date, $0) }, uniquingKeysWith: { a, _ in a })
        let maxCount = rhythm.days.map(\.count).max() ?? 0
        let weeks = (0..<16).map { KST.addDays(rhythm.heatmapStart, $0 * 7) }
        let recordedWeeks = weeks.filter { w in (0..<7).contains { byDate[KST.addDays(w, $0)] != nil } }.count
        // 같은 개수면 먼저 나온 날 (웹 reduce 와 같게 — max(by:) 도 처음 것을 남겨요)
        let best = rhythm.days.max { $0.count < $1.count }
        // 긴 Text 덧셈 안에 삼항식을 겹쳐 두면 타입 검사가 느려져서 문구를 미리 골라 둬요
        let rhythmNote: String = recordedWeeks >= 12 ? ". 거의 매주 이어오고 있어요"
            : (recordedWeeks >= 6 ? ". 리듬이 생기고 있어요" : "")

        return VStack(alignment: .leading, spacing: 0) {
            VStack(spacing: 3) {
                // 위: 달 표시 (그 주에 달이 바뀌면). 칸 폭에 영향을 주지 않게 overlay 로 얹어요
                HStack(spacing: 3) {
                    Color.clear.frame(width: 14, height: 14)
                    ForEach(Array(weeks.enumerated()), id: \.element) { wi, w in
                        let showMonth = wi == 0 || KST.monthOf(w) != KST.monthOf(weeks[wi - 1])
                        Color.clear
                            .frame(maxWidth: .infinity)
                            .frame(height: 14)
                            .overlay(alignment: .leading) {
                                if showMonth {
                                    Text("\(KST.monthOf(w))월")
                                        .font(.pt(11))
                                        .foregroundStyle(Color.ink3)
                                        .fixedSize()
                                }
                            }
                    }
                }
                // 요일 줄 × 16주 (하루 한 칸)
                ForEach(0..<7, id: \.self) { i in
                    HStack(spacing: 3) {
                        Text(Self.rowLabel[i])
                            .font(.pt(11))
                            .foregroundStyle(Color.ink3)
                            .frame(width: 14, alignment: .leading)
                        ForEach(weeks, id: \.self) { w in
                            let d = KST.addDays(w, i)
                            rhythmCell(date: d, stat: byDate[d], max: maxCount, future: d > today)
                        }
                    }
                }
            }

            if let picked {
                Text(picked).font(.pt(12, .medium)).foregroundStyle(Color.ink2).padding(.top, 8)
            }

            HStack(spacing: 12) {
                HStack(spacing: 6) {
                    Text("적게")
                    RoundedRectangle(cornerRadius: 3).fill(Color.heatEmpty).frame(width: 12, height: 12)
                    ForEach(1...4, id: \.self) { l in
                        RoundedRectangle(cornerRadius: 3).fill(RecordPatternFormat.levelColor(l)).frame(width: 12, height: 12)
                    }
                    Text("많이")
                }
                Spacer(minLength: 0)
                if let best {
                    Text("가장 많이 끝낸 날 · \(KST.formatMonthDay(best.date)) \(best.count)개")
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                }
            }
            .font(.pt(12))
            .foregroundStyle(Color.ink3)
            .padding(.top, 12)

            (Text("16주 중 ") + Text("\(recordedWeeks)주").bold().foregroundColor(Color.textBrand)
             + Text(" Task를 완료했어요")
             + Text(rhythmNote))
                .font(.pt(14))
                .foregroundStyle(Color.ink2)
                .lineSpacing(3)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.subtle, in: RoundedRectangle(cornerRadius: Radius.xxl))
                .padding(.top, 16)
        }
    }

    @ViewBuilder
    private func rhythmCell(date: String, stat: RecordDayStat?, max: Int, future: Bool) -> some View {
        let level = stat.map { RecordPatternFormat.levelOf($0.count, max: max) } ?? 0
        let shape = RoundedRectangle(cornerRadius: 4)
        if future {
            // 아직 오지 않은 날은 빈칸 (색 없음)
            Color.clear.aspectRatio(1, contentMode: .fit)
        } else {
            shape
                .fill(level > 0 ? RecordPatternFormat.levelColor(level) : Color.subtle)
                .aspectRatio(1, contentMode: .fit)
                .onTapGesture {
                    picked = Self.describe(date: date, stat: stat)
                    trackCellTap()
                }
                .accessibilityLabel(Self.describe(date: date, stat: stat))
        }
    }

    private static func describe(date: String, stat: RecordDayStat?) -> String {
        guard let stat else { return "\(KST.formatMonthDay(date)) · 완료 없음" }
        let min = stat.minutes > 0 ? " · \(RecordPatternFormat.shortDuration(stat.minutes))" : ""
        return "\(KST.formatMonthDay(date)) · Task \(stat.count)개\(min)"
    }
}

// MARK: - 언제 이 일을 하나요? (요일 × 시간대)

struct RecordWhenCard: View {
    let when: RecordWhen
    @State private var picked: String?
    @State private var tapSent = false
    @Environment(\.recordStatsGoalId) private var statsGoalId

    /// 칸을 처음 눌렀을 때 한 번 (record_stats.cell_tap)
    private func trackCellTap() {
        guard !tapSent, let gid = statsGoalId else { return }
        tapSent = true
        WK.track("record_stats.cell_tap", goalId: gid, ["stats_card": "when"])
    }

    init(when: RecordWhen) {
        self.when = when
    }

    private var total: Int { when.bands.reduce(0) { $0 + $1.count } }

    var body: some View {
        SectionCard("언제 이 일을 하나요?", aside: { Text("요일 × 시간대") }) {
            if total < 3 {
                NotEnoughView(text: "시간을 정한 Task를 3개 이상 완료하면 주로 언제 하는지 보여드려요")
            } else {
                filled
            }
        }
    }

    private func key(_ d: JavaDayOfWeek, _ b: RecordTimeBand) -> String { "\(d.rawValue)-\(b.rawValue)" }

    private var filled: some View {
        let cells = Dictionary(when.cells.map { (key($0.dayOfWeek, $0.band), $0) }, uniquingKeysWith: { a, _ in a })
        let maxCount = when.cells.map(\.count).max() ?? 0
        let peak: RecordWhenCell? = {
            guard let d = when.peakDay, let b = when.peakBand else { return nil }
            return cells[key(d, b)]
        }()
        let topBand = when.bands.max { $0.count < $1.count }
        let totalCount = max(total, 1)

        return VStack(alignment: .leading, spacing: 0) {
            // 머리줄: 시간대
            HStack(spacing: 3) {
                Color.clear.frame(width: 18)
                ForEach(RecordTimeBand.allCases, id: \.self) { b in
                    VStack(spacing: 0) {
                        Text(b.label).font(.pt(11)).foregroundStyle(Color.ink3)
                        Text(b.range).font(.pt(10)).foregroundStyle(Color.ink4)
                    }
                    .frame(maxWidth: .infinity)
                }
            }
            .padding(.bottom, 4)
            VStack(spacing: 3) {
                ForEach(JavaDayOfWeek.allCases) { d in
                    HStack(spacing: 3) {
                        Text(d.label)
                            .font(.pt(12, .medium))
                            .foregroundStyle(Color.ink2)
                            .frame(width: 18, alignment: .leading)
                        ForEach(RecordTimeBand.allCases, id: \.self) { b in
                            whenCell(day: d, band: b, cell: cells[key(d, b)], max: maxCount)
                        }
                    }
                }
            }

            if let picked {
                Text(picked).font(.pt(12, .medium)).foregroundStyle(Color.ink2).padding(.top, 8)
            }

            if let peak, let pd = when.peakDay, let pb = when.peakBand {
                VStack(spacing: 12) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("🌟 가장 많이 끝내는 때").font(.pt(13, .bold)).foregroundStyle(Color.textBrand)
                        Text("\(pd.label)요일 \(pb.label)").font(.pt(16, .semibold)).foregroundStyle(Color.ink)
                        Text("Task \(peak.count)개 · 전체의 \(Int((Double(peak.count) / Double(totalCount) * 100).rounded()))%")
                            .font(.pt(13)).foregroundStyle(Color.ink3)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 14)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.brandSoft, in: RoundedRectangle(cornerRadius: Radius.xxl))

                    if let topBand, topBand.count > 0 {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("⏰ 주로 하는 시간대").font(.pt(13, .bold)).foregroundStyle(Color.ink2)
                            Text("\(topBand.band.label) \(topBand.band.range)").font(.pt(16, .semibold)).foregroundStyle(Color.ink)
                            Text("완료한 Task의 \(Int((Double(topBand.count) / Double(totalCount) * 100).rounded()))%")
                                .font(.pt(13)).foregroundStyle(Color.ink3)
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 14)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.subtle, in: RoundedRectangle(cornerRadius: Radius.xxl))
                    }
                }
                .padding(.top, 16)
            }

            if when.untimedCount > 0 {
                Text("시간을 정하지 않고 완료한 Task \(when.untimedCount)개는 이 표에서 뺐어요.")
                    .font(.pt(12))
                    .foregroundStyle(Color.ink3)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, 12)
            }
        }
    }

    @ViewBuilder
    private func whenCell(day: JavaDayOfWeek, band: RecordTimeBand, cell: RecordWhenCell?, max: Int) -> some View {
        let count = cell?.count ?? 0
        let level = RecordPatternFormat.levelOf(count, max: max)
        let minutes = cell?.minutes ?? 0
        let desc = count > 0
            ? "\(day.label) \(band.label) · Task \(count)개\(minutes > 0 ? " · \(RecordPatternFormat.shortDuration(minutes))" : "")"
            : "\(day.label) \(band.label) · 완료 없음"
        Text(count > 0 ? "\(count)개" : "")
            .font(.pt(11, .semibold))
            .foregroundStyle(level >= 3 ? Color.white : Color.ink2)
            .lineLimit(1)
            .minimumScaleFactor(0.7)
            .frame(maxWidth: .infinity)
            .frame(height: 34)
            .background(level > 0 ? RecordPatternFormat.levelColor(level) : Color.subtle,
                        in: RoundedRectangle(cornerRadius: 6))
            .onTapGesture {
                picked = desc
                trackCellTap()
            }
            .accessibilityLabel(desc)
    }
}

// MARK: - 무엇에 시간을 썼나요? (루틴별 완료 횟수 · 쌓인 시간)
//
// 루틴이 없는 목표에서는 그리지 않아요 (RecordPatternSectionView 에서 숨김).
// 최근 4주에 그 전보다 눈에 띄게 더 한 루틴에는 "요즘 늘었어요" 표시.

struct RecordRoutineCard: View {
    let routines: RecordRoutines

    var body: some View {
        let items = routines.items
        let totalMinutes = items.reduce(0) { $0 + $1.minutes }
        // 루틴에 시간 정보가 하나도 없으면 막대를 횟수로 그려요
        let byTime = totalMinutes > 0
        let maxValue = max(1, items.map { byTime ? $0.minutes : $0.completedCount }.max() ?? 0)
        SectionCard("무엇에 시간을 썼나요?", aside: { Text("루틴 · 처음부터") }) {
            VStack(alignment: .leading, spacing: 16) {
                ForEach(Array(items.enumerated()), id: \.element.routineId) { i, r in
                    RecordRoutineRow(routine: r, first: i == 0, byTime: byTime, maxValue: maxValue,
                                     rising: r.routineId == routines.risingRoutineId)
                }
                if byTime, let top = items.first, top.minutes > 0, items.count > 1 {
                    (Text("루틴 시간의 ")
                     + Text("\(Int((Double(top.minutes) / Double(totalMinutes) * 100).rounded()))%").bold()
                        .foregroundColor(Color.textBrand)
                     + Text("를 ") + Text(top.routineName).bold() + Text("에 썼어요"))
                        .font(.pt(14))
                        .foregroundStyle(Color.ink2)
                        .lineSpacing(3)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.subtle, in: RoundedRectangle(cornerRadius: Radius.xxl))
                        .padding(.top, 4)
                }
            }
        }
    }
}

private struct RecordRoutineRow: View {
    let routine: RecordRoutineStat
    let first: Bool
    let byTime: Bool
    let maxValue: Int
    let rising: Bool

    var body: some View {
        let r = routine
        let value = byTime ? r.minutes : r.completedCount
        let more = r.recentCount - r.previousCount
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text((r.emoji.map { "\($0) " } ?? "") + r.routineName)
                    .font(.pt(14, .semibold))
                    .foregroundStyle(Color.ink)
                    .lineLimit(1)
                    .frame(maxWidth: .infinity, alignment: .leading)
                if rising {
                    HStack(spacing: 4) {
                        NSymbol("chart.line.uptrend.xyaxis", size: 9, weight: .bold)
                        Text("요즘 늘었어요")
                    }
                    .font(.pt(11, .bold))
                    .foregroundStyle(Color.success)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 2)
                    .background(Color.successSoft, in: Capsule())
                    .fixedSize()
                }
                Group {
                    if byTime {
                        Text(KST.formatDuration(r.minutes)).bold().foregroundColor(Color.ink)
                            + Text(" · \(r.completedCount)번").foregroundColor(Color.ink3)
                    } else {
                        Text("\(r.completedCount)번").bold().foregroundColor(Color.ink)
                    }
                }
                .font(.ptMono(13))
                .fixedSize()
            }
            ProgressBar(value: Double(value) / Double(max(maxValue, 1)),
                        color: first && value > 0 ? Color.brand : Color.heat2, height: 8)
            if r.completedCount == 0 {
                Text("아직 완료한 날이 없어요").font(.pt(12)).foregroundStyle(Color.ink3)
            } else {
                (Text("최근 4주 \(r.recentCount)번")
                 + Text(more > 0 && r.previousCount > 0 ? " · 그 전 4주보다 \(more)번 더" : "")
                    .fontWeight(.semibold).foregroundColor(Color.success))
                    .font(.pt(12))
                    .foregroundStyle(Color.ink3)
            }
        }
    }
}

#Preview {
    let p = RecordSampleData.pattern
    return ScrollView {
        VStack(spacing: 16) {
            RecordWeeklyCard(weeks: p.rhythm.weeks)
            RecordRhythmCard(rhythm: p.rhythm)
            RecordWhenCard(when: p.when)
            RecordRoutineCard(routines: p.routines)
            RecordWeeklyCard(weeks: [])
        }
        .padding(16)
    }
    .canvasBackground()
}
