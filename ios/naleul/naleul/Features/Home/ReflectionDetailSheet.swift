// Features/Home/ReflectionDetailSheet.swift
//
// 회고 한 줄을 누르면 뜨는 상세 시트 (2026-10) — 웹 ReflectionsView 의 ReflectionDetailModal 과 같아요.
//  - 전체 내용 · 날짜 · 목표 · 수치 · 기분
//  - 수정: 회고 글(RETRO)은 PATCH /retrospectives/{id}, 오늘 기록(CHECK_IN)은 PUT /check-in 으로 한 줄 회고만 바꿔요
//  - 삭제: 회고 글은 DELETE /retrospectives/{id}, 오늘 기록은 그날 기록(수치 · 기분 · 회고)을 지워요
// 저장 · 삭제 후 .home · .goals · .work 를 다시 불러와 목록이 따라와요.

import SwiftUI

struct ReflectionDetailSheet: View {
    let entry: ReflectionEntry
    /// Amplitude surface (reflections · goal_detail)
    var surface = "reflections"

    @Environment(\.dismiss) private var dismiss
    @Environment(AppRouter.self) private var router
    @State private var editing = false
    @State private var text = ""
    @State private var saving = false
    @State private var confirmDelete = false
    @FocusState private var focused: Bool

    private var isRetro: Bool { entry.kind == .RETRO }
    private var baseProps: EventProps {
        [
            "surface": surface, "entry_kind": entry.kind.rawValue, "goal_category_id": entry.goalCategoryId,
            "goal_mode": entry.goalMode?.rawValue, "days_ago": KST.diffDays(entry.date, KST.today()),
        ]
    }

    var body: some View {
        SheetScaffold(title: isRetro ? "회고" : "오늘 기록",
                      description: KST.formatMonthDay(entry.date) + " · " + entry.goalTitle,
                      dismissible: !saving) {
            VStack(alignment: .leading, spacing: 16) {
                badges
                if editing {
                    TextField(isRetro ? "회고 내용" : "한 줄 회고", text: $text, axis: .vertical)
                        .font(.pt(15))
                        .lineLimit(4...12)
                        .focused($focused)
                        .inputStyle(minHeight: 120)
                } else if let note = entry.note, !note.isEmpty {
                    Text(note)
                        .font(.pt(15))
                        .foregroundStyle(Color.ink)
                        .lineSpacing(4)
                        .textSelection(.enabled)
                        .frame(maxWidth: .infinity, alignment: .leading)
                } else {
                    Text(isRetro ? "내용이 없어요" : "수치만 남겼어요")
                        .font(.pt(14))
                        .foregroundStyle(Color.ink3)
                }
                Button {
                    RF.track("reflection_detail.goal_link_click", baseProps)
                    dismiss()
                    router.openGoal(entry.goalCategoryId)
                } label: {
                    HStack(spacing: 6) {
                        Circle().fill(Color.goal(entry.colorCode)).frame(width: 8, height: 8)
                        Text(entry.goalTitle + " 보러 가기").font(.labelSM).foregroundStyle(Color.textSecondary)
                        Image(systemName: "chevron.right").font(.system(size: 11)).foregroundStyle(Color.textTertiary)
                    }
                }
                .buttonStyle(.plain)
            }
        } footer: {
            HStack(spacing: 8) {
                if editing {
                    NButton("취소", variant: .secondary, size: .lg, fullWidth: true) {
                        editing = false
                        RF.track("reflection_detail.edit_cancel", baseProps)
                    }
                    NButton("저장", size: .lg, loading: saving, fullWidth: true) { Task { await save() } }
                        .disabled(saving || (isRetro && text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty))
                } else {
                    NButton("삭제", variant: .danger, size: .lg, fullWidth: true) {
                        RF.track("reflection_detail.delete_click", baseProps)
                        confirmDelete = true
                    }
                    NButton("수정", variant: .secondary, size: .lg, fullWidth: true) {
                        text = entry.note ?? ""
                        editing = true
                        focused = true
                        RF.track("reflection_detail.edit_click", baseProps)
                    }
                }
            }
        }
        .confirmDialog(isPresented: $confirmDelete,
                       title: isRetro ? "이 회고를 지울까요?" : "이날 기록을 지울까요?",
                       message: isRetro ? "지운 회고는 되돌릴 수 없어요." : "이날 남긴 수치 · 기분 · 한 줄 회고가 모두 지워져요.",
                       confirmLabel: "삭제", tone: .danger,
                       onCancel: { RF.track("reflection_detail.delete_cancel", baseProps) }) {
            Task { await remove() }
        }
        .onAppear { RF.track("reflection_detail.view", baseProps) }
    }

    @ViewBuilder
    private var badges: some View {
        let mood = GoalMoodOption.of(entry.mood)
        if entry.value != nil || mood != nil || (isRetro && entry.reviewType != nil && entry.reviewType != "DAILY") {
            HStack(spacing: 6) {
                if let v = entry.value {
                    Text("\(entry.metricName ?? "수치") \(GoalDetailLogic.fmtNum(v))\(entry.unit ?? "")")
                        .font(.pt(13, .semibold))
                        .foregroundStyle(Color.textPrimary)
                        .padding(.horizontal, 8).padding(.vertical, 4)
                        .background(Color.badgeBg, in: Capsule())
                }
                if let mood {
                    Text("\(mood.emoji) \(mood.label)")
                        .font(.pt(13, .medium))
                        .foregroundStyle(Color.textPrimary)
                        .padding(.horizontal, 8).padding(.vertical, 4)
                        .background(Color.badgeBg, in: Capsule())
                }
                if isRetro, let t = entry.reviewType, t != "DAILY" {
                    Text(t == "WEEKLY" ? "주간 회고" : "월간 회고")
                        .font(.labelXS)
                        .foregroundStyle(Color.textTertiary)
                }
            }
        }
    }

    private func save() async {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        saving = true
        defer { saving = false }
        do {
            if isRetro, let id = entry.retrospectiveId {
                try await API.send("PATCH", "/v1/retrospectives/\(id)", body: .compact([
                    "content": .string(trimmed),
                    "goalCategoryId": .int(entry.goalCategoryId),
                ]))
            } else {
                let _: GoalProgress? = try await GoalEditAPI.checkIn(goalId: entry.goalCategoryId, date: entry.date,
                                                                     value: entry.value, mood: entry.mood,
                                                                     note: trimmed.isEmpty ? nil : trimmed)
            }
            RF.track("reflection_detail.edit_save", baseProps.merging(["content_length": trimmed.count]) { $1 })
            Toast.success("회고를 고쳤어요.")
            Invalidation.shared.invalidate(.home, .goals, .work)
            dismiss()
        } catch {
            RF.track("reflection_detail.edit_fail", baseProps.merging(["http_status": RF.status(error)]) { $1 })
            Toast.error(errorMessage(error))
        }
    }

    private func remove() async {
        saving = true
        defer { saving = false }
        do {
            if isRetro, let id = entry.retrospectiveId {
                try await API.send("DELETE", "/v1/retrospectives/\(id)")
            } else {
                let _: GoalProgress? = try await GoalEditAPI.deleteMetricLog(goalId: entry.goalCategoryId, date: entry.date)
            }
            RF.track("reflection_detail.delete_confirm", baseProps)
            Toast.success("회고를 지웠어요.")
            Invalidation.shared.invalidate(.home, .goals, .work)
            dismiss()
        } catch {
            RF.track("reflection_detail.delete_fail", baseProps.merging(["http_status": RF.status(error)]) { $1 })
            Toast.error(errorMessage(error))
        }
    }
}
