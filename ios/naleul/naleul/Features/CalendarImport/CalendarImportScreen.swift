// Features/CalendarImport/CalendarImportScreen.swift
//
// 웹 원본: app/(main)/settings/calendar-import/page.tsx · features/external-calendar/ui/CalendarImportView.tsx
// 예전 iOS: Features/Setting/View/ExternalCalendarImportView.swift
//
// 설정 > 외부 캘린더 가져오기 (push 되는 하위 화면)
// "어디서 가져올까요?"에서 고른 곳의 일정을 골라 Task 로 가져와요 (웹과 같은 흐름).
//  - 구글 캘린더: 구글 계정을 연결해서 바로 읽어요 (아이폰에 계정을 추가하지 않아도 돼요) — GoogleCalendarClient.swift
//  - 아이폰 캘린더: 아이폰에 추가한 iCloud · 구글 · Outlook 계정 (EventKit, 거절했으면 설정 앱 열기)
//    (웹 AppleGuide 가 "나를 iOS 앱에서 가져와요"라고 안내하는 바로 그 화면이에요)
//  - 캘린더 · 기간 · 목표 고르기, 일정 고르기, 결과 카드의 문구는 웹과 같아요.

import SwiftUI
import UIKit

@MainActor
struct CalendarImportScreen: View {
    @State private var vm: CalendarImportViewModel
    @Environment(\.openURL) private var openURL
    @Environment(\.scenePhase) private var scenePhase
    @State private var didTrack = false
    @State private var googleAuth = GoogleCalendarAuth()

    init() {
        _vm = State(initialValue: CalendarImportViewModel())
    }

    /// 미리보기 전용
    init(previewModel: CalendarImportViewModel) {
        _vm = State(initialValue: previewModel)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                sourceCard

                switch vm.source {
                case .google:
                    if vm.access == .granted {
                        CalendarImportOptionsCard(vm: vm)
                    } else {
                        googleConnectCard
                    }
                case .device:
                    switch vm.access {
                    case .unknown: accessRequestCard
                    case .denied: accessDeniedCard
                    case .granted: CalendarImportOptionsCard(vm: vm)
                    }
                case nil:
                    EmptyView()
                }

                if let error = vm.errorText {
                    Text(error)
                        .font(.pt(13, .medium))
                        .foregroundStyle(Color.danger)
                }

                if let events = vm.events {
                    CalendarImportEventPicker(vm: vm, events: events)
                }

                if let result = vm.result {
                    CalendarImportResultCard(r: result)
                }
            }
            .padding(16)
        }
        .canvasBackground()
        .navigationTitle("외부 캘린더 가져오기")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            await vm.onAppear()
            if !didTrack {
                didTrack = true
                ST.track("calendar_import.pageview", ["calendar_access": vm.accessKey])
            }
        }
        // [UX] 권한을 거부해 '설정 앱 열기' 카드만 보일 때 — 앱 안에서 다시 물어볼 수 없어요
        .onChange(of: vm.accessKey) { _, key in
            if key == "denied" { ST.track("calendar_import.denied_view", ["calendar_access": key]) }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { vm.refreshAccess() }
        }
    }

    // MARK: 안내

    /// 어디서 가져올까요? (웹 SOURCES 에서 노션 캘린더를 뺀 앱 버전)
    private var sourceCard: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("어디서 가져올까요?")
                .font(.pt(15, .bold))
                .foregroundStyle(Color.ink)
            Text("고른 일정은 나를의 Task가 돼요. 한 번 가져온 뒤에는 원래 캘린더에서 바꿔도 다시 반영되지 않아요.")
                .font(.pt(13))
                .foregroundStyle(Color.ink3)
                .fixedSize(horizontal: false, vertical: true)
            HStack(alignment: .top, spacing: 10) {
                sourceOption(.google, title: "구글 캘린더", hint: "구글 계정을 연결해서 가져와요", mark: "G")
                sourceOption(.device, title: "아이폰 캘린더", hint: "아이폰에 추가한 iCloud · 구글 · Outlook 계정", mark: nil)
            }
            .padding(.top, 12)
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card()
    }

    private func sourceOption(_ s: CalendarImportViewModel.Source, title: String, hint: String, mark: String?) -> some View {
        let on = vm.source == s
        return Button {
            vm.selectSource(s)
        } label: {
            VStack(alignment: .leading, spacing: 6) {
                Group {
                    if let mark {
                        Text(mark).font(.pt(14, .bold))
                    } else {
                        Image(systemName: "iphone").font(.system(size: 14, weight: .semibold))
                    }
                }
                .foregroundStyle(Color.ink)
                .frame(width: 32, height: 32)
                .background(Color.subtle, in: RoundedRectangle(cornerRadius: Radius.lg))
                Text(title)
                    .font(.pt(14, .bold))
                    .foregroundStyle(Color.ink)
                Text(hint)
                    .font(.pt(12))
                    .foregroundStyle(Color.ink3)
                    .multilineTextAlignment(.leading)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(on ? Color.brandSoft : Color.surface, in: RoundedRectangle(cornerRadius: Radius.xl))
            .overlay(RoundedRectangle(cornerRadius: Radius.xl).stroke(on ? Color.brand : Color.borderSubtle, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(on ? .isSelected : [])
    }

    /// 웹 GoogleImport 의 "구글 계정 연결하기" 카드
    private var googleConnectCard: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(GoogleCalendarAuth.isConfigured ? "구글 계정을 연결해 주세요" : "구글 연동 준비 중이에요")
                .font(.pt(15, .bold))
                .foregroundStyle(Color.ink)
            Text("캘린더 보기 권한만 받아요. 나를은 구글 캘린더의 일정을 바꾸거나 지우지 않고, 연결은 1시간 뒤 자동으로 끊겨요.")
                .font(.pt(13))
                .foregroundStyle(Color.ink3)
                .fixedSize(horizontal: false, vertical: true)
            NButton("구글 계정 연결하기", loading: vm.googleConnecting, fullWidth: true) {
                Task { await vm.connectGoogle(auth: googleAuth) }
            }
            .disabled(!GoogleCalendarAuth.isConfigured)
            .padding(.top, 12)
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card()
    }

    private var accessRequestCard: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("캘린더 접근 허용이 필요해요")
                .font(.pt(15, .bold))
                .foregroundStyle(Color.ink)
            Text("캘린더 읽기 권한만 받아요. 나를은 아이폰 캘린더의 일정을 바꾸거나 지우지 않아요.")
                .font(.pt(13))
                .foregroundStyle(Color.ink3)
                .fixedSize(horizontal: false, vertical: true)
            NButton("캘린더 접근 허용하기", fullWidth: true) {
                Task { await vm.requestAccess() }
            }
            .padding(.top, 12)
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card()
    }

    private var accessDeniedCard: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("캘린더 접근이 꺼져 있어요")
                .font(.pt(15, .bold))
                .foregroundStyle(Color.ink)
            Text("설정 앱 → 나를 → 캘린더에서 \"전체 접근\"을 켜 주세요.")
                .font(.pt(13))
                .foregroundStyle(Color.ink3)
                .fixedSize(horizontal: false, vertical: true)
            NButton("설정 앱 열기", variant: .secondary, fullWidth: true) {
                ST.track("calendar_import.open_settings_click", ["calendar_access": vm.accessKey])
                if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
            }
            .padding(.top, 12)
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card()
    }
}

// MARK: - 캘린더 · 기간 · 목표 (웹 GoogleImport 의 연결 후 카드)

@MainActor
private struct CalendarImportOptionsCard: View {
    let vm: CalendarImportViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            VStack(alignment: .leading, spacing: 10) {
                Text("가져올 캘린더")
                    .font(.pt(15, .bold))
                    .foregroundStyle(Color.ink)
                if vm.calendars.isEmpty {
                    Text(vm.source == .google ? "이 구글 계정에 캘린더가 없어요." : "이 아이폰에 캘린더가 없어요.")
                        .font(.pt(13))
                        .foregroundStyle(Color.ink3)
                } else {
                    FlowLayout(spacing: 8) {
                        ForEach(vm.calendars) { c in
                            calendarChip(c)
                        }
                    }
                }
            }

            VStack(alignment: .leading, spacing: 12) {
                dateRow("시작일",
                        value: vm.from,
                        range: KST.date(from: vm.today)...KST.date(from: max(vm.to, vm.today)),
                        onSet: { vm.setFrom($0) })
                dateRow("종료일",
                        value: vm.to,
                        range: KST.date(from: vm.from)...KST.date(from: vm.maxDate),
                        onSet: { vm.setTo($0) })
                VStack(alignment: .leading, spacing: 6) {
                    Text("연결할 목표 (선택)")
                        .font(.pt(13, .semibold))
                        .foregroundStyle(Color.ink3)
                    BrainDumpMenuField(text: selectedGoalText, isPlaceholder: false) {
                        Button("목표 없이") { vm.setGoal(nil) }
                        ForEach(vm.goals) { g in
                            Button((g.emoji.map { "\($0) " } ?? "") + g.goalCategoryName) { vm.setGoal(g.goalCategoryId) }
                        }
                    }
                }
                Text("지난 일정은 “못 한 일”로 잡혀 실행률이 틀어져서 오늘부터 가져와요.")
                    .font(.pt(12))
                    .foregroundStyle(Color.ink3)
                    .fixedSize(horizontal: false, vertical: true)
            }

            NButton("일정 불러오기", variant: .brand, icon: "calendar", loading: vm.isLoading) {
                vm.loadEvents()
            }
            .disabled(vm.selectedCalendarIds.isEmpty)
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card()
    }

    private var selectedGoalText: String {
        guard let id = vm.selectedGoalId, let g = vm.goals.first(where: { $0.goalCategoryId == id }) else { return "목표 없이" }
        return (g.emoji.map { "\($0) " } ?? "") + g.goalCategoryName
    }

    private func calendarChip(_ c: CalendarImportDeviceCalendar) -> some View {
        let on = vm.selectedCalendarIds.contains(c.id)
        return Button {
            vm.toggleCalendar(c.id)
        } label: {
            HStack(spacing: 8) {
                Circle().fill(c.color).frame(width: 10, height: 10)
                Text(c.title)
                    .font(.pt(14, .medium))
                    .foregroundStyle(on ? Color.ink : Color.ink3)
                    .lineLimit(1)
                // 아이폰은 계정마다 이름이 같은 캘린더("캘린더")가 있어서 계정 이름을 작게 붙여요
                if !c.accountName.isEmpty {
                    Text(c.accountName)
                        .font(.pt(11))
                        .foregroundStyle(Color.ink4)
                        .lineLimit(1)
                }
                if on {
                    NSymbol("checkmark", size: 11, weight: .bold)
                        .foregroundStyle(Color.ink)
                }
            }
            .padding(.horizontal, 14)
            .frame(height: 36)
            .background(on ? Color.subtle : Color.surface, in: Capsule())
            .overlay(Capsule().stroke(on ? Color.actionPrimary : Color.borderSubtle, lineWidth: 1))
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(on ? .isSelected : [])
    }

    private func dateRow(_ title: String, value: String, range: ClosedRange<Date>,
                         onSet: @escaping (String) -> Void) -> some View {
        HStack {
            Text(title)
                .font(.pt(13, .semibold))
                .foregroundStyle(Color.ink3)
            Spacer(minLength: 0)
            DatePicker(title,
                       selection: Binding(get: { KST.date(from: value) }, set: { onSet(KST.ymd(from: $0)) }),
                       in: range,
                       displayedComponents: .date)
                .labelsHidden()
                .environment(\.timeZone, KST.timeZone)
                .tint(Color.brand)
        }
    }
}

// MARK: - 일정 고르기 (웹 EventPicker)

@MainActor
private struct CalendarImportEventPicker: View {
    let vm: CalendarImportViewModel
    let events: [CalendarImportEvent]

    var body: some View {
        if events.isEmpty {
            Text("이 기간에 가져올 일정이 없어요.")
                .font(.pt(14))
                .foregroundStyle(Color.ink3)
                .frame(maxWidth: .infinity)
                .padding(32)
                .card()
        } else {
            VStack(alignment: .leading, spacing: 0) {
                header
                Divider().overlay(Color.borderSoft)
                VStack(alignment: .leading, spacing: 0) {
                    ForEach(vm.eventsByDay) { day in
                        VStack(alignment: .leading, spacing: 0) {
                            Text(day.date.isEmpty ? "날짜 없음" : KST.formatMonthDay(day.date))
                                .font(.pt(12, .bold))
                                .foregroundStyle(Color.ink3)
                                .padding(.vertical, 6)
                            ForEach(day.events) { e in
                                row(e)
                            }
                        }
                        .padding(.vertical, 8)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 8)
                Divider().overlay(Color.borderSoft)
                footer
            }
            .card()
        }
    }

    private var header: some View {
        HStack(spacing: 12) {
            (Text("일정 \(events.count)개").font(.pt(15, .bold)).foregroundColor(Color.ink)
             + Text("  \(vm.pickedEventIds.count)개 선택").font(.pt(13, .semibold)).foregroundColor(Color.ink3))
            Spacer(minLength: 0)
            Button(vm.allPicked ? "모두 해제" : "모두 선택") { vm.toggleAll() }
                .font(.pt(13, .semibold))
                .foregroundStyle(Color.ink2)
                .buttonStyle(.plain)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 16)
    }

    private func row(_ e: CalendarImportEvent) -> some View {
        let on = vm.pickedEventIds.contains(e.externalId)
        let time = e.allDay ? "하루 종일" : "\(KST.hm(e.start))–\(KST.hm(e.end))"
        return Button {
            vm.toggleEvent(e.externalId)
        } label: {
            HStack(spacing: 12) {
                NSymbol(on ? "checkmark.square.fill" : "square", size: 17)
                    .foregroundStyle(on ? Color.textBrand : Color.ink4)
                Text(time)
                    .font(.ptMono(13))
                    .foregroundStyle(Color.ink3)
                    .frame(width: 92, alignment: .leading)
                Text(e.title)
                    .font(.pt(14))
                    .foregroundStyle(on ? Color.ink : Color.ink3)
                    .lineLimit(1)
                    .frame(maxWidth: .infinity, alignment: .leading)
                if let name = e.calendarName, !name.isEmpty {
                    Text(name)
                        .font(.pt(12))
                        .foregroundStyle(Color.ink4)
                        .lineLimit(1)
                }
            }
            .padding(.vertical, 8)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(on ? .isSelected : [])
    }

    private var footer: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("하루 종일 일정은 처음엔 빼 뒀어요. 시간 일정은 그 시각에, 하루 종일 일정은 그날의 “시간 미정”에 들어가요.")
                .font(.pt(12))
                .foregroundStyle(Color.ink3)
                .fixedSize(horizontal: false, vertical: true)
            NButton("\(vm.pickedEventIds.count)개 Task로 가져오기", loading: vm.isImporting, fullWidth: true) {
                Task { await vm.importPicked() }
            }
            .disabled(vm.pickedEventIds.isEmpty)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 16)
    }
}

// MARK: - 결과 (웹 ResultCard)

@MainActor
private struct CalendarImportResultCard: View {
    let r: CalendarImportResult

    private var skipped: [String] {
        [
            r.skippedDuplicate > 0 ? "이미 있는 일정 \(r.skippedDuplicate)개" : nil,
            r.skippedPast > 0 ? "지난 일정 \(r.skippedPast)개" : nil,
            r.skippedInvalid > 0 ? "시간 정보가 맞지 않는 일정 \(r.skippedInvalid)개" : nil,
        ].compactMap { $0 }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(r.created > 0 ? "\(r.created)개를 Task로 만들었어요" : "새로 만든 Task가 없어요")
                .font(.pt(16, .semibold))
                .foregroundStyle(Color.ink)
            if !skipped.isEmpty {
                Text("건너뜀: " + skipped.joined(separator: " · "))
                    .font(.pt(13))
                    .foregroundStyle(Color.ink3)
                    .fixedSize(horizontal: false, vertical: true)
            }
            NButton("캘린더에서 보기", variant: .secondary, icon: "arrow.right") {
                ST.track("calendar_import.result_calendar_click", ["created_count": r.created])
                AppRouter.shared.openCalendar()
            }
            .padding(.top, 12)
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card()
    }
}

#Preview("허용 전") {
    NavigationStack {
        CalendarImportScreen(previewModel: CalendarImportViewModel(
            reader: CalendarImportMockReader(access: nil),
            importer: { _, _, _ in .zero },
            goalLoader: { [] }
        ))
    }
}

#Preview("일정 고르기") {
    let vm = CalendarImportViewModel(
        reader: CalendarImportMockReader(access: true),
        importer: { _, _, events in
            CalendarImportResult(created: events.count, skippedDuplicate: 1, skippedPast: 0, skippedInvalid: 0)
        },
        goalLoader: { [] }
    )
    return NavigationStack {
        CalendarImportScreen(previewModel: vm)
            .task {
                vm.selectSource(.device)
                await vm.onAppear()
                vm.loadEvents()
            }
    }
}

#Preview("결과") {
    ScrollView {
        CalendarImportResultCard(r: CalendarImportResult(created: 5, skippedDuplicate: 2, skippedPast: 1, skippedInvalid: 0))
            .padding(16)
    }
    .canvasBackground()
}
