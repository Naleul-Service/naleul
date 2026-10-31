// Features/Settings/LifePattern/LifePatternScreen.swift
//
// 웹 원본: features/life-pattern/ui/LifePatternView.tsx (/settings/life-pattern)
//          features/life-pattern/api.ts (추가·수정·삭제 → .lifePatterns + .timetable 다시 불러오기)
//
// 기본 생활 패턴(고정 시간) 한 화면에서 추가·수정·삭제.
// 여기서 바꾸면 "오늘부터 모든 날짜"의 기본값이 바뀌어요. (그날만 바꾸기는 캘린더에서)

import SwiftUI
import Observation

/// 시트로 여는 폼 요청 (열 때마다 새 id → 시트가 초기값으로 새로 그려져요)
struct LifePatternFormRequest: Identifiable {
    let id = UUID()
    /// nil 이면 추가
    var editing: LifePattern?
    /// 추가할 때 처음 채울 값 (추천 칩에서 열었을 때)
    var initial: LifePatternFormValue?
}

@MainActor
@Observable
final class LifePatternViewModel {
    var phase: LoadPhase<[LifePattern]> = .loading

    /// 미리보기용: 샘플을 넣으면 네트워크를 쓰지 않아요
    private let sample: [LifePattern]?

    init(sample: [LifePattern]? = nil) {
        self.sample = sample
        if let sample { phase = .loaded(sample) }
    }

    var patterns: [LifePattern] { phase.value ?? [] }

    // MARK: Amplitude
    private var pageviewSent = false
    private(set) var lastErrorStatus: Int?

    func load() async {
        if let sample { phase = .loaded(sample); return }
        // 이미 보여주고 있으면 로딩 화면으로 깜빡이지 않게 그대로 두고 바꿔요
        do {
            let list = try await TimetableAPI.lifePatterns()
            phase = .loaded(list)
            if !pageviewSent {
                pageviewSent = true
                ST.track("life_pattern.pageview", [
                    "pattern_count": list.count,
                    "has_sleep": list.contains { $0.patternType == .SLEEP },
                    "entry_source": ST.lifePatternEntry ?? "settings",
                ])
                ST.lifePatternEntry = nil
            }
        } catch is CancellationError {
        } catch {
            if phase.value == nil {
                phase = .failed(errorMessage(error))
                lastErrorStatus = ST.status(error)
                ST.track("life_pattern.load_fail", ["error_status": lastErrorStatus])
            }
        }
    }

    /// 성공하면 true
    func submit(_ input: LifePatternInput, editing: LifePattern?) async -> Bool {
        if sample != nil { return true }
        let keys: [DataKey] = [.lifePatterns, .timetable]
        let r: Void?
        if let editing {
            r = await Mutation.run(invalidate: keys, successMessage: "고정 시간을 수정했어요.") {
                try await LifePatternAPI.update(lifePatternId: editing.lifePatternId, input)
            }
        } else {
            r = await Mutation.run(invalidate: keys, successMessage: "고정 시간을 추가했어요.") {
                try await LifePatternAPI.create(input)
            }
        }
        ST.track("life_pattern.save_result", GD.result(r != nil).merging([
            "is_new": editing == nil, "pattern_type": input.patternType.rawValue,
        ]) { _, new in new })
        return r != nil
    }

    func delete(_ p: LifePattern) async {
        if sample != nil { return }
        let r: Void? = await Mutation.run(invalidate: [.lifePatterns, .timetable], successMessage: "고정 시간을 삭제했어요.") {
            try await LifePatternAPI.delete(lifePatternId: p.lifePatternId)
        }
        ST.track("life_pattern.delete_result", GD.result(r != nil).merging(["pattern_type": p.patternType.rawValue]) { _, new in new })
    }
}

struct LifePatternScreen: View {
    @State private var vm: LifePatternViewModel
    @State private var form: LifePatternFormRequest?
    @State private var deleting: LifePattern?

    init() { _vm = State(initialValue: LifePatternViewModel()) }

    /// 미리보기용
    init(sample: [LifePattern]) { _vm = State(initialValue: LifePatternViewModel(sample: sample)) }

    private var patterns: [LifePattern] { vm.patterns }
    private var full: Bool { patterns.count >= LifePatternRules.maxPatterns }
    private var isError: Bool {
        if case .failed = vm.phase { return true }
        return false
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                header
                switch vm.phase {
                case .loading:
                    LoadingView(minHeight: 420)
                case .failed:
                    EmptyStateCard(title: "고정 시간을 불러오지 못했어요") {
                        NButton("다시 시도", variant: .secondary) {
                            ST.track("life_pattern.retry_click", ["error_status": vm.lastErrorStatus])
                            Task { await vm.load() }
                        }
                    }
                case .loaded:
                    presetCard
                    listCard
                    if !patterns.isEmpty {
                        LifePatternWeeklyOverview(patterns: patterns) { p in openEdit(p, entry: "overview") }
                    }
                    Text("특정 날짜만 바꾸거나 비우려면 캘린더에서 고정 시간 블록을 눌러 주세요.")
                        .font(.pt(12))
                        .foregroundStyle(Color.ink4)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity)
                }
            }
            .padding(16)
        }
        .canvasBackground()
        .navigationTitle("기본 생활 패턴")
        .navigationBarTitleDisplayMode(.inline)
        .reloadOn([.lifePatterns]) { await vm.load() }
        // 10개 한도 — 추가 버튼 · 추천 칩이 꺼질 때 (화면이 열릴 때마다 한 번)
        .onChange(of: full) { _, isFull in
            if isFull { ST.track("life_pattern.limit_view", ["pattern_count": patterns.count]) }
        }
        .onAppear {
            if full { ST.track("life_pattern.limit_view", ["pattern_count": patterns.count]) }
        }
        .sheet(item: $form) { req in
            LifePatternFormSheet(
                editing: req.editing,
                initial: req.initial,
                patterns: patterns,
                onSubmit: { input in
                    let ok = await vm.submit(input, editing: req.editing)
                    if ok { form = nil }
                    return ok
                },
                onEditExisting: { p in openEdit(p, entry: "form_suggestion") }
            )
        }
        .modifier(deleteDialog)
    }

    // MARK: 조각

    private var header: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("매주 반복되는 고정 시간이에요. 이 시간은 비워두고 Task를 배치해요.")
                .font(.pt(14))
                .foregroundStyle(Color.ink3)
            NButton("고정 시간 추가", icon: "plus") {
                ST.track("life_pattern.add_click", ["pattern_count": patterns.count])
                openCreate(nil)
            }
                .disabled(full || isError || vm.phase.isLoading)
            if full {
                FieldMessage(text: "고정 시간은 최대 \(LifePatternRules.maxPatterns)개까지 만들 수 있어요")
            }
        }
    }

    /// 추천
    private var presetCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 6) {
                NSymbol("sparkles", size: 13, weight: .semibold).foregroundStyle(Color.textBrand)
                Text("추천으로 빠르게 추가").font(.pt(14, .bold)).foregroundStyle(Color.ink)
            }
            FlowLayout(spacing: 6) {
                ForEach(LifePatternRules.presets) { p in
                    let existing = LifePatternRules.existingOfType(patterns, p)
                    let disabled = existing == nil && full
                    Chip(LifePatternRules.presetChipTitle(p, existing: existing), size: .sm) { openPreset(p) }
                        .disabled(disabled)
                        .opacity(disabled ? 0.4 : 1)
                }
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card()
    }

    /// 목록
    private var listCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            CardHeader("고정 시간", aside: "\(patterns.count) / \(LifePatternRules.maxPatterns)")
            if patterns.isEmpty {
                (Text("등록된 고정 시간이 없어요. 위 추천이나 ")
                 + Text("고정 시간 추가").font(.pt(14, .bold)).foregroundColor(Color.ink2)
                 + Text("로 만들어 보세요."))
                    .font(.pt(14))
                    .foregroundStyle(Color.ink3)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 40)
            } else {
                VStack(spacing: 0) {
                    ForEach(Array(patterns.enumerated()), id: \.element.lifePatternId) { i, p in
                        if i > 0 { Rectangle().fill(Color.borderSoft).frame(height: 1) }
                        LifePatternRow(pattern: p, onEdit: { openEdit(p, entry: "list") }, onDelete: {
                            ST.track("life_pattern.delete_click", ["pattern_type": p.patternType.rawValue])
                            deleting = p
                        })
                    }
                }
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card()
    }

    /// 삭제 확인창 (보여줄 패턴을 지금 값으로 붙잡아 둬요 — 확인을 누르는 순간 deleting 이 nil 이 될 수 있어서)
    private var deleteDialog: LifePatternDeleteDialog {
        LifePatternDeleteDialog(target: deleting, onClose: { deleting = nil }, onCancel: { p in
            ST.track("life_pattern.delete_cancel", ["pattern_type": p.patternType.rawValue, "close_method": "cancel_button"])
        }) { p in
            ST.track("life_pattern.delete_confirm", ["pattern_type": p.patternType.rawValue])
            Task { await vm.delete(p) }
        }
    }

    // MARK: 동작

    private func openCreate(_ initial: LifePatternFormValue?) {
        form = LifePatternFormRequest(editing: nil, initial: initial)
    }

    private func openEdit(_ p: LifePattern, entry: String) {
        ST.track("life_pattern.edit_click", ["pattern_type": p.patternType.rawValue, "entry_point": entry])
        form = LifePatternFormRequest(editing: p, initial: nil)
    }

    private func openPreset(_ preset: LifePatternPreset) {
        let existing = LifePatternRules.existingOfType(patterns, preset)
        ST.track("life_pattern.preset_click", [
            "pattern_type": preset.patternType.rawValue, "is_new": existing == nil, "pattern_count": patterns.count,
        ])
        if let existing {
            openEdit(existing, entry: "preset")
        } else {
            openCreate(.from(preset: preset))
        }
    }
}

/// 목록 한 줄
private struct LifePatternRow: View {
    let pattern: LifePattern
    let onEdit: () -> Void
    let onDelete: () -> Void

    private var detail: Text {
        let p = pattern
        var t = Text("\(LifePatternRules.toHm(p.startTime)) – \(LifePatternRules.toHm(p.endTime))")
        if p.crossesMidnight { t = t + Text(" (다음 날)") }
        t = t + Text(" · \(LifePatternRules.duration(p)) · ")
        t = t + Text(LifePatternRules.formatDays(p.days)).font(.pt(13, .medium)).foregroundColor(Color.ink2)
        let n = p.dayTimes?.count ?? 0
        if n > 0 {
            t = t + Text(" · 요일별 시간 \(n)일").font(.pt(13, .medium)).foregroundColor(Color.textBrand)
        }
        return t
    }

    var body: some View {
        HStack(spacing: 12) {
            Text((pattern.emoji ?? "").isEmpty ? "📌" : (pattern.emoji ?? "📌"))
                .font(.system(size: 18))
                .frame(width: 40, height: 40)
                .background(Color.subtle, in: RoundedRectangle(cornerRadius: Radius.xl))
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text(pattern.title).font(.pt(15, .bold)).foregroundStyle(Color.ink).lineLimit(1)
                    if pattern.patternType != .CUSTOM {
                        Badge(LifePatternRules.typeLabel(pattern.patternType))
                    }
                }
                detail.font(.pt(13)).foregroundStyle(Color.ink3)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            IconButton(systemName: "pencil", size: 15, label: "\(pattern.title) 수정", action: onEdit)
            IconButton(systemName: "trash", size: 15, label: "\(pattern.title) 삭제", action: onDelete)
        }
        .padding(.vertical, 12)
    }
}

/// 삭제 확인창
private struct LifePatternDeleteDialog: ViewModifier {
    let target: LifePattern?
    let onClose: () -> Void
    var onCancel: (LifePattern) -> Void = { _ in }
    let onConfirm: (LifePattern) -> Void

    func body(content: Content) -> some View {
        let t = target
        let message = t?.patternType == .SLEEP
            ? "수면 시간이 없으면 하루 범위를 00:00~24:00으로 보고 Task를 배치해요."
            : "오늘부터 이 시간에도 Task가 배치될 수 있어요."
        return content.confirmDialog(
            isPresented: Binding(get: { t != nil }, set: { if !$0 { onClose() } }),
            title: "'\(t?.title ?? "")'을 삭제할까요?",
            message: message,
            confirmLabel: "삭제",
            tone: .danger,
            onCancel: { if let t { onCancel(t) } }
        ) {
            if let t { onConfirm(t) }
        }
    }
}

#Preview {
    NavigationStack {
        LifePatternScreen(sample: LifePatternSamples.patterns)
    }
}

#Preview("비어 있을 때") {
    NavigationStack {
        LifePatternScreen(sample: [])
    }
}
