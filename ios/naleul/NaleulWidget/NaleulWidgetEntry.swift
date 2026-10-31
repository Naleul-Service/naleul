//
//  NaleulWidgetEntry.swift
//  NaleulWidget

import WidgetKit
import SwiftUI

struct NaleulEntry: TimelineEntry {
    let date: Date
    let displayType: WidgetDisplayType
    let dailyTasks: [WidgetTask]
    let weeklyTasks: [String: [WidgetTask]]
    let monthlyTasks: [String: [WidgetTask]]
}
