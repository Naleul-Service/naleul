//
//  TaskListEntry.swift
//  NaleulWidget

import WidgetKit

struct TaskListEntry: TimelineEntry {
    let date: Date
    let tasks: [WidgetTask]
}
