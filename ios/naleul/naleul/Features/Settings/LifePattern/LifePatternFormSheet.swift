// Features/Settings/LifePattern/LifePatternFormSheet.swift
//
// 웹 원본: features/life-pattern/ui/LifePatternFormModal.tsx
//
// 고정 시간 추가·수정 시트. 제목 · 이모지 · 시작/종료 시각 · 반복 요일을 받아요.
// 추가할 때는 제목 아래 추천(+이동시간 +수면시간 +외출준비 …)을 누르면 한 번에 채워져요.
// 웹 <input type="time"> 은 iOS 시간 선택(DatePicker)으로 바꿨어요.
// 시트를 열 때마다 새로 만들어지므로(.sheet(item:)) 초기값은 init 에서 한 번만 채워요.

import SwiftUI

/// 폼 입력값 (웹 LifePatternFormValue)
struct LifePatternFormValue: Hashable {
    var patternType: LifePatternType
    var title: String
    var emoji: String
    /// "HH:mm"
    var startTime: String
    var endTime: String
    var days: [JavaDayOfWeek]

    static let empty = LifePatternFormValue(patternType: .CUSTOM, title: "", emoji: "",
                                            startTime: "09:00", endTime: "10:00", days: LifePatternRules.allDays)

    static func from(preset p: LifePatternPreset) -> LifePatternFormValue {
        LifePatternFormValue(patternType: p.patternType, title: p.title, emoji: p.emoji,
                             startTime: p.startTime, endTime: p.endTime, days: p.days)
    }

    static func from(pattern p: LifePattern) -> LifePatternFormValue {
        LifePatternFormValue(patternType: p.patternType, title: p.title, emoji: p.emoji ?? "",
                             startTime: LifePatternRules.toHm(p.startTime), endTime: LifePatternRules.toHm(p.endTime),
                             days: p.days.isEmpty ? LifePatternRules.allDays : p.days)
    }
}

/// 요일별 시간 한 칸 (일반 고정 시간)
private struct DayTimeEntry: Hashable {
    var start: String
    var end: String
    var offset: Int
}

struct LifePatternFormSheet: View {
    /// 수정할 패턴. nil 이면 추가
    let editing: LifePattern?
    /// 이미 있는 패턴 (수면·점심·저녁 중복 추천 막기)
    let patterns: [LifePattern]
    /// 저장. 성공하면 true (부모가 시트를 닫아요)
    let onSubmit: (LifePatternInput) async -> Bool
    /// 추천을 눌렀는데 같은 종류(수면 등)가 이미 있으면 그걸 수정하러 가요
    let onEditExisting: (LifePattern) -> Void

    @Environment(\.dismiss) private var dismiss

    @State private var form: LifePatternFormValue
    @State private var perDay: Bool
    @State private var dayMap: [JavaDayOfWeek: DayTimeEntry]
    /// 수면은 "밤" 단위 (월요일 밤 취침 → 화요일 아침 기상). 비어 있으면 모든 밤이 기본 시간
    @State private var nights: [LifePatternSleepNight?]
    @State private var saving = false
    /// 시트가 어떻게 끝났는지 — "saved" · "cancel_button" · "next"(다른 패턴 수정으로 넘어감) · nil(X · 쓸어 닫기)
    @State private var outcome: String?
    /// 마지막으로 보낸 입력 오류 (같은 오류는 한 번만)
    @State private var lastErrorKey: String?
    /// 처음 값 — 닫을 때 고친 게 있는지 (is_dirty)
    private let startForm: LifePatternFormValue

    init(editing: LifePattern?, initial: LifePatternFormValue?, patterns: [LifePattern],
         onSubmit: @escaping (LifePatternInput) async -> Bool,
         onEditExisting: @escaping (LifePattern) -> Void) {
        self.editing = editing
        self.patterns = patterns
        self.onSubmit = onSubmit
        self.onEditExisting = onEditExisting

        let start: LifePatternFormValue
        if let editing { start = .from(pattern: editing) } else { start = initial ?? .empty }
        _form = State(initialValue: start)
        startForm = start

        let hasDayTimes = !(editing?.dayTimes ?? []).isEmpty
        _perDay = State(initialValue: hasDayTimes)
        var map: [JavaDayOfWeek: DayTimeEntry] = [:]
        for t in editing?.dayTimes ?? [] {
            map[t.day] = DayTimeEntry(start: LifePatternRules.toHm(t.startTime), end: LifePatternRules.toHm(t.endTime),
                                      offset: t.startDayOffset)
        }
        _dayMap = State(initialValue: map)
        if let editing, hasDayTimes {
            _nights = State(initialValue: LifePatternRules.sleepNights(LifePatternTimes(editing)))
        } else {
            _nights = State(initialValue: [])
        }
    }

    // MARK: 계산 (웹과 같은 규칙)

    private var isEdit: Bool { editing != nil }
    private var isSleep: Bool { form.patternType == .SLEEP }
    private var defaultNight: LifePatternSleepNight { LifePatternSleepNight(bed: form.startTime, wake: form.endTime) }

    /// 기본 취침이 자정 이후면 다음 날 소속이라 그 요일 기준으로 봐요
    private var nightShift: Int { LifePatternRules.nightShift(form.startTime) }
    private func hasNight(_ w: Int) -> Bool { form.days.contains(LifePatternRules.allDays[(w + nightShift) % 7]) }
    private func nightAt(_ w: Int) -> LifePatternSleepNight? {
        guard hasNight(w) else { return nil }
        let stored: LifePatternSleepNight? = w < nights.count ? nights[w] : nil
        return stored ?? defaultNight
    }
    private var allNights: [LifePatternSleepNight?] { (0..<7).map { nightAt($0) } }
    private var sleepConflict: Int { perDay && isSleep ? LifePatternRules.sleepConflictDay(allNights) : -1 }

    private func dayTimeOf(_ d: JavaDayOfWeek) -> DayTimeEntry {
        dayMap[d] ?? DayTimeEntry(start: form.startTime, end: form.endTime, offset: 0)
    }
    private func isCustomDay(_ d: JavaDayOfWeek) -> Bool {
        let t = dayTimeOf(d)
        return t.offset != 0 || t.start != form.startTime || t.end != form.endTime
    }
    private var genericDayTimes: [LifePatternDayTime] {
        form.days.filter { isCustomDay($0) }.map { d in
            let t = dayTimeOf(d)
            return LifePatternDayTime(day: d, startTime: t.start, endTime: t.end, startDayOffset: t.offset)
        }
    }
    private var badGenericDay: JavaDayOfWeek? {
        guard perDay && !isSleep else { return nil }
        return form.days.first { dayTimeOf($0).start == dayTimeOf($0).end }
    }

    /// 시간 계산: 종료가 시작보다 이르면 다음 날까지 (수면 23:00 ~ 07:00)
    private var startMin: Int { KST.timeToMinutes(form.startTime) }
    private var rawEndMin: Int { KST.timeToMinutes(form.endTime) }
    private var timeValid: Bool { startMin != rawEndMin }
    private var crosses: Bool { timeValid && rawEndMin < startMin }
    private var durationMin: Int { (crosses ? rawEndMin + 1440 : rawEndMin) - startMin }

    private var trimmedTitle: String { form.title.trimmingCharacters(in: .whitespacesAndNewlines) }
    private var valid: Bool {
        !trimmedTitle.isEmpty && trimmedTitle.count <= 30 && timeValid && !form.days.isEmpty
            && sleepConflict < 0 && badGenericDay == nil
    }

    /// 추천: 추가할 때만, 입력한 제목과 맞는 것
    private var suggestions: [LifePatternPreset] { isEdit ? [] : LifePatternRules.matchPresets(form.title) }

    // MARK: 화면

    var body: some View {
        SheetScaffold(
            title: isEdit ? "고정 시간 수정" : "고정 시간 추가",
            description: isEdit
                ? "저장하면 오늘부터 모든 날짜의 기본 시간이 바뀌어요."
                : "매주 반복되는 시간을 등록하면 이 시간은 비워두고 Task를 배치해요.",
            dismissible: !saving,
            detents: [.large]
        ) {
            VStack(alignment: .leading, spacing: 20) {
                titleSection
                timeSection
                daysSection
                if timeValid && !form.days.isEmpty {
                    if perDay { perDaySection } else { perDayOpenButton }
                }
            }
        } footer: {
            NButton("취소", variant: .secondary) {
                outcome = "cancel_button"
                dismiss()
            }
                .disabled(saving)
            NButton(isEdit ? "저장" : "추가", loading: saving) { submit() }
                .disabled(!valid)
        }
        .onAppear { Analytics.sheetCloseHint = nil }
        .onChange(of: errorKey) { _, key in
            // [UX] 입력 오류 문구가 보일 때 (제목이 비면 문구 없이 버튼만 회색이라 안 남아요)
            guard let key, key != lastErrorKey else { lastErrorKey = key; return }
            lastErrorKey = key
            ST.track("life_pattern.form_error_view", [
                "validation_error": key, "pattern_type": form.patternType.rawValue, "is_new": !isEdit,
            ])
        }
        .onDisappear {
            if let method = GD.closeMethod(outcome) {
                ST.track("life_pattern.form_close", [
                    "is_new": !isEdit, "close_method": method,
                    "is_dirty": form != startForm || !dayMap.isEmpty || !nights.isEmpty,
                ])
            }
        }
    }

    /// 지금 보이는 입력 오류 (time_same · no_days · sleep_conflict · day_time_same)
    private var errorKey: String? {
        if !timeValid { return "time_same" }
        if form.days.isEmpty { return "no_days" }
        if sleepConflict >= 0 { return "sleep_conflict" }
        if badGenericDay != nil { return "day_time_same" }
        return nil
    }

    // 제목 + 이모지 + 추천
    @ViewBuilder private var titleSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .bottom, spacing: 8) {
                VStack(alignment: .leading, spacing: 6) {
                    FieldLabel("이모지")
                    EmojiField(emoji: $form.emoji)
                }
                VStack(alignment: .leading, spacing: 6) {
                    FieldLabel("제목")
                    TextField("예: 출근, 운동, 외출준비", text: Binding(
                        get: { form.title },
                        set: { onTitleChange(String($0.prefix(30))) }
                    ))
                    .inputStyle(minHeight: 52)
                }
            }

            if !suggestions.isEmpty {
                VStack(alignment: .leading, spacing: 6) {
                    HStack(spacing: 4) {
                        NSymbol("sparkles", size: 11)
                        Text("추천")
                    }
                    .font(.pt(12, .medium))
                    .foregroundStyle(Color.ink3)
                    FlowLayout(spacing: 6) {
                        ForEach(suggestions) { p in
                            let existing = LifePatternRules.existingOfType(patterns, p)
                            Chip(LifePatternRules.presetChipTitle(p, existing: existing),
                                 selected: form.title == p.title && form.patternType == p.patternType,
                                 size: .sm) {
                                applyPreset(p)
                            }
                        }
                    }
                }
            }

            if form.patternType != .CUSTOM {
                (Text("종류: ")
                 + Text(LifePatternRules.typeLabel(form.patternType)).font(.pt(12, .bold)).foregroundColor(Color.ink2)
                 + Text(form.patternType == .SLEEP ? " · 수면 시간은 하루의 시작(기상)과 끝(취침) 기준으로도 쓰여요." : ""))
                    .font(.pt(12))
                    .foregroundStyle(Color.ink3)
            }
        }
    }

    // 시간
    @ViewBuilder private var timeSection: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 8) {
                VStack(alignment: .leading, spacing: 6) {
                    FieldLabel(isSleep ? (perDay ? "기본 취침" : "취침 시간") : (perDay ? "기본 시작 시간" : "시작 시간"))
                    LifePatternTimeField(time: $form.startTime)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                VStack(alignment: .leading, spacing: 6) {
                    FieldLabel(isSleep ? (perDay ? "기본 기상" : "기상 시간") : (perDay ? "기본 종료 시간" : "종료 시간"))
                    LifePatternTimeField(time: $form.endTime)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            FieldMessage(
                text: timeValid
                    ? KST.formatDuration(durationMin) + (crosses ? " · 다음 날까지 이어져요" : "")
                    : "시작과 종료 시간을 다르게 입력해 주세요.",
                isError: !timeValid
            )
        }
    }

    // 반복 요일
    @ViewBuilder private var daysSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("반복 요일").font(.pt(13, .semibold)).foregroundStyle(Color.ink2)
                Spacer(minLength: 0)
                HStack(spacing: 4) {
                    Chip("매일", selected: isSet(LifePatternRules.allDays), size: .sm) { form.days = LifePatternRules.allDays }
                    Chip("평일", selected: isSet(LifePatternRules.weekdays), size: .sm) { form.days = LifePatternRules.weekdays }
                    Chip("주말", selected: isSet(LifePatternRules.weekend), size: .sm) { form.days = LifePatternRules.weekend }
                }
            }
            HStack(spacing: 6) {
                ForEach(LifePatternRules.allDays) { d in
                    DayToggle(label: d.label, selected: form.days.contains(d), weekend: d.index >= 5) {
                        toggleDay(d)
                    }
                }
            }
            if form.days.isEmpty {
                FieldMessage(text: "요일을 하나 이상 골라 주세요.", isError: true)
            }
        }
    }

    private var perDayOpenButton: some View {
        Button {
            ST.track("life_pattern.form_per_day_toggle", ["expanded": true, "pattern_type": form.patternType.rawValue])
            perDay = true
            // 처음 펼치면 지금 기본 시간으로 채워요 (이미 요일별 시간이 있던 패턴은 그대로)
            if (editing?.dayTimes ?? []).isEmpty {
                nights = []
                dayMap = [:]
            }
        } label: {
            Text("+ 요일마다 시간을 다르게 하기" + (isSleep ? " (예: 주말엔 늦게 일어나기)" : ""))
                .font(.pt(13, .semibold))
                .foregroundStyle(Color.textBrand)
                .multilineTextAlignment(.leading)
        }
        .buttonStyle(.plain)
    }

    // 요일별 시간 — 예: 주말에는 늦게 일어나기
    @ViewBuilder private var perDaySection: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Text("요일별 시간").font(.pt(13, .semibold)).foregroundStyle(Color.ink)
                Spacer(minLength: 0)
                Button {
                    ST.track("life_pattern.form_per_day_toggle", ["expanded": false, "pattern_type": form.patternType.rawValue])
                    perDay = false
                    dayMap = [:]
                    nights = []
                } label: {
                    Text("모든 요일 같은 시간으로").font(.pt(12, .semibold)).underline().foregroundStyle(Color.ink3)
                }
                .buttonStyle(.plain)
            }

            if isSleep {
                VStack(spacing: 8) {
                    ForEach(0..<7, id: \.self) { w in sleepRow(w) }
                }
            } else {
                VStack(spacing: 8) {
                    ForEach(LifePatternRules.allDays.filter { form.days.contains($0) }) { d in genericRow(d) }
                }
            }

            FieldMessage(text: perDayMessage, isError: sleepConflict >= 0 || badGenericDay != nil)
        }
        .padding(12)
        .background(Color.subtle.opacity(0.6), in: RoundedRectangle(cornerRadius: Radius.xl))
    }

    private var perDayMessage: String {
        if sleepConflict >= 0 { return "\(LifePatternRules.allDays[sleepConflict].label)요일 기상이 그날 취침보다 늦어요." }
        if let bad = badGenericDay { return "\(bad.label)요일 시작과 종료를 다르게 입력해 주세요." }
        if isSleep { return "파란 요일만 기본과 달라요. 예: 토·일 기상을 늦추면 주말 아침이 그만큼 비워져요." }
        return "파란 요일만 기본 시간(\(form.startTime)~\(form.endTime))과 달라요."
    }

    /// 수면 한 줄: 전날 밤 → 이날 아침 기상 · 이날 밤 취침
    private func sleepRow(_ w: Int) -> some View {
        let d = LifePatternRules.allDays[w]
        let prev = (w + 6) % 7
        let morning = nightAt(prev)
        let night = nightAt(w)
        let custom = (morning != nil && morning?.wake != defaultNight.wake) || (night != nil && night?.bed != defaultNight.bed)
        return HStack(spacing: 8) {
            DayBadge(label: d.label, highlighted: custom, conflict: sleepConflict == w)
            VStack(alignment: .leading, spacing: 2) {
                Text("☀️ 기상").font(.pt(11)).foregroundStyle(Color.ink3)
                if let morning {
                    LifePatternTimeField(time: Binding(get: { morning.wake }, set: { setNight(prev, wake: $0) }), compact: true)
                } else {
                    Text("—").font(.pt(13)).foregroundStyle(Color.ink4).frame(height: 34)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            VStack(alignment: .leading, spacing: 2) {
                Text("🌙 취침").font(.pt(11)).foregroundStyle(Color.ink3)
                if let night {
                    LifePatternTimeField(time: Binding(get: { night.bed }, set: { setNight(w, bed: $0) }), compact: true)
                } else {
                    Text("—").font(.pt(13)).foregroundStyle(Color.ink4).frame(height: 34)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    /// 일반 고정 시간 한 줄
    private func genericRow(_ d: JavaDayOfWeek) -> some View {
        let t = dayTimeOf(d)
        let custom = isCustomDay(d)
        return HStack(spacing: 6) {
            DayBadge(label: d.label, highlighted: custom, conflict: false)
            LifePatternTimeField(time: Binding(get: { t.start }, set: { setDayTime(d, start: $0) }), compact: true)
            Text("~").font(.pt(13)).foregroundStyle(Color.ink4)
            LifePatternTimeField(time: Binding(get: { t.end }, set: { setDayTime(d, end: $0) }), compact: true)
            Spacer(minLength: 0)
            Button { dayMap[d] = nil } label: {
                Text("기본으로").font(.pt(12, .semibold)).foregroundStyle(Color.ink3)
            }
            .buttonStyle(.plain)
            .opacity(custom ? 1 : 0)
            .disabled(!custom)
        }
    }

    // MARK: 동작

    private func setNight(_ w: Int, bed: String? = nil, wake: String? = nil) {
        var next: [LifePatternSleepNight?] = (0..<7).map { i in i < nights.count ? nights[i] : nil }
        var cur = next[w] ?? defaultNight
        if let bed { cur.bed = bed }
        if let wake { cur.wake = wake }
        next[w] = cur
        nights = next
    }

    private func setDayTime(_ d: JavaDayOfWeek, start: String? = nil, end: String? = nil) {
        var t = dayTimeOf(d)
        if let start { t.start = start }
        if let end { t.end = end }
        dayMap[d] = t
    }

    private func applyPreset(_ p: LifePatternPreset) {
        let existing = LifePatternRules.existingOfType(patterns, p)
        ST.track("life_pattern.form_suggestion_click", ["pattern_type": p.patternType.rawValue, "is_new": existing == nil])
        if let existing {
            outcome = "next"
            onEditExisting(existing)
            return
        }
        form = .from(preset: p)
    }

    /// 제목을 직접 바꾸면 수면·점심·저녁 추천에서 벗어난 것으로 보고 일반 고정 일정으로 저장해요
    /// (수정할 때는 종류를 바꿀 수 없어요)
    private func onTitleChange(_ v: String) {
        let keep = isEdit || v.trimmingCharacters(in: .whitespacesAndNewlines) == LifePatternRules.typeLabel(form.patternType)
        form.title = v
        if !keep { form.patternType = .CUSTOM }
    }

    private func toggleDay(_ d: JavaDayOfWeek) {
        if form.days.contains(d) {
            form.days.removeAll { $0 == d }
        } else {
            let current = form.days
            form.days = LifePatternRules.allDays.filter { $0 == d || current.contains($0) }
        }
    }

    private func isSet(_ target: [JavaDayOfWeek]) -> Bool { LifePatternRules.sameSet(form.days, target) }

    private func submit() {
        guard valid, !saving else { return }
        let emoji = form.emoji.trimmingCharacters(in: .whitespacesAndNewlines)
        let dayTimes: [LifePatternDayTime]
        if perDay {
            dayTimes = isSleep
                ? LifePatternRules.nightsToDayTimes(defaultStart: form.startTime, defaultEnd: form.endTime, nights: allNights)
                : genericDayTimes
        } else {
            // 요일별을 끄면 [] → 모든 요일이 기본 시간으로 돌아가요
            dayTimes = []
        }
        let input = LifePatternInput(
            patternType: form.patternType,
            title: trimmedTitle,
            emoji: emoji.isEmpty ? nil : emoji,
            startTime: form.startTime,
            endTime: form.endTime,
            // 7개 다 고르면 매일 (백엔드는 빈 배열도 매일로 봐요)
            days: form.days.count == 7 ? [] : form.days,
            dayTimes: dayTimes
        )
        ST.track("life_pattern.form_save_click", [
            "is_new": !isEdit, "pattern_type": form.patternType.rawValue,
            "repeat_day_count": form.days.count, "per_day_time": perDay,
            "crosses_midnight": crosses, "duration_minutes": durationMin,
        ])
        saving = true
        // 성공하면 부모가 바로 시트를 닫아서 미리 "saved" 로 둬요 (실패하면 되돌려요)
        outcome = "saved"
        Task {
            let ok = await onSubmit(input)
            if !ok { outcome = nil }
            saving = false
        }
    }
}

// MARK: - 작은 조각들

/// "HH:mm" 문자열 ↔ 시간 선택. 기기 시간대와 상관없이 같은 벽시계 값이 되도록 한국 시간 달력으로 바꿔요
struct LifePatternTimeField: View {
    @Binding var time: String
    var compact = false

    private static let calendar: Calendar = {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = KST.timeZone
        return c
    }()

    private var dateBinding: Binding<Date> {
        Binding(
            get: {
                let m = KST.timeToMinutes(time)
                let comps = DateComponents(year: 2000, month: 1, day: 1, hour: m / 60, minute: m % 60)
                return Self.calendar.date(from: comps) ?? Date()
            },
            set: { d in
                let c = Self.calendar.dateComponents([.hour, .minute], from: d)
                time = KST.minutesToTime((c.hour ?? 0) * 60 + (c.minute ?? 0))
            }
        )
    }

    var body: some View {
        DatePicker("", selection: dateBinding, displayedComponents: .hourAndMinute)
            .labelsHidden()
            .datePickerStyle(.compact)
            .environment(\.timeZone, KST.timeZone)
            .tint(Color.brand)
            .scaleEffect(compact ? 0.92 : 1, anchor: .leading)
            .frame(height: compact ? 34 : 44)
    }
}

/// 요일 버튼 (월 화 수 …) — 7개가 한 줄에 같은 폭으로
private struct DayToggle: View {
    let label: String
    let selected: Bool
    let weekend: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.bodyMDMedium)
                .foregroundStyle(selected ? Color.actionPrimaryFg : (weekend ? Color.textTertiary : Color.textSecondary))
                .frame(maxWidth: .infinity)
                .frame(height: 36)
                .background(selected ? Color.actionPrimary : Color.bgSunken, in: Capsule())
                .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

/// 요일별 시간 줄 앞의 요일 칸 (기본과 다르면 파랑, 겹치면 빨간 테두리)
private struct DayBadge: View {
    let label: String
    let highlighted: Bool
    let conflict: Bool

    var body: some View {
        Text(label)
            .font(.pt(13, .bold))
            .foregroundStyle(highlighted ? Color.actionPrimaryFg : Color.textSecondary)
            .frame(width: 32, height: 32)
            .background(highlighted ? Color.actionPrimary : Color.bgSunken, in: RoundedRectangle(cornerRadius: Radius.lg))
            .overlay(RoundedRectangle(cornerRadius: Radius.lg).stroke(Color.danger, lineWidth: conflict ? 2 : 0))
    }
}

#Preview("추가") {
    Color.canvas
        .sheet(isPresented: .constant(true)) {
            LifePatternFormSheet(editing: nil, initial: nil, patterns: LifePatternSamples.patterns,
                                 onSubmit: { _ in true }, onEditExisting: { _ in })
        }
}

#Preview("수면 수정 (요일별)") {
    Color.canvas
        .sheet(isPresented: .constant(true)) {
            LifePatternFormSheet(editing: LifePatternSamples.patterns[0], initial: nil, patterns: LifePatternSamples.patterns,
                                 onSubmit: { _ in true }, onEditExisting: { _ in })
        }
}
