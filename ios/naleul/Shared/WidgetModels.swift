//
//  WidgetModels.swift
//  Shared (앱 + 위젯 두 타겟이 같이 써요)
//
//  앱의 TaskPriority / TaskSourceType 과 이름이 겹치지 않게 Widget 접두어를 붙였어요.
//  (위젯 타겟에서는 WidgetSupport.swift 의 typealias 로 예전 이름 그대로 써요)

import Foundation

enum WidgetTaskSourceType: String, Codable {
    case manual  = "MANUAL"
    case routine = "ROUTINE"
    case mission = "MISSION"
}

enum WidgetTaskPriority: String, Codable {
    case a = "A"
    case b = "B"
    case c = "C"
    case d = "D"
    case e = "E"
}

struct WidgetTask: Codable, Identifiable {
    var id: Int { taskId }
    let taskId: Int
    let taskName: String
    /// "yyyy-MM-dd'T'HH:mm:ss" (KST)
    let plannedStartAt: String?
    let plannedEndAt: String?
    let goalCategoryColorCode: String?
    let generalCategoryColorCode: String?
    /// 완료한 Task 만 값이 있어요 (완료 = actual != nil)
    let actual: WidgetTaskActual?
    let sourceType: WidgetTaskSourceType?
    let taskPriority: WidgetTaskPriority?
}

struct WidgetTaskActual: Codable {
    let actualStartAt: String
    let actualEndAt: String
}

extension WidgetTask {
    /// 루틴이 아닌 "일반(MANUAL)" 태스크인지 여부
    var isGeneralTask: Bool {
        sourceType == nil || sourceType == .manual
    }
}

/// 나를 위젯 스타일 (미션 위젯은 미션 기능 종료로 제외)
enum WidgetDisplayType: String, CaseIterable {
    case daily    = "daily"
    case week     = "week"
    case month    = "month"
    case taskList = "taskList"

    var displayName: String {
        switch self {
        case .daily:    return "일간 타임테이블"
        case .week:     return "주간 타임테이블"
        case .month:    return "월간 타임테이블"
        case .taskList: return "일정 태스크"
        }
    }

    var sizeLabel: String { "위젯 크기 4×3" }

    var previewImageName: String {
        switch self {
        case .daily:    return "widget_preview_daily"
        case .week:     return "widget_preview_week"
        case .month:    return "widget_preview_month"
        case .taskList: return "widget_preview_tasklist"
        }
    }
}
