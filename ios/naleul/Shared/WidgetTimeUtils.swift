//
//  WidgetTimeUtils.swift
//  Shared (앱 + 위젯)

import Foundation

/// "yyyy-MM-dd'T'HH:mm:ss" (KST) → 그날 0시부터 지난 분
func widgetUtcToKstMinutes(_ isoString: String?) -> Int {
    guard let isoString, isoString.count >= 16 else { return 0 }
    let formatter = DateFormatter()
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.dateFormat = "yyyy-MM-dd'T'HH:mm:ss"
    formatter.timeZone = TimeZone(identifier: "Asia/Seoul")
    // 초가 없는 "yyyy-MM-dd'T'HH:mm" 도 받아요
    let normalized = isoString.count == 16 ? isoString + ":00" : String(isoString.prefix(19))
    guard let date = formatter.date(from: normalized) else { return 0 }

    var cal = Calendar(identifier: .gregorian)
    cal.timeZone = TimeZone(identifier: "Asia/Seoul")!
    return cal.component(.hour, from: date) * 60 + cal.component(.minute, from: date)
}

func widgetKstTimeLabel(_ isoString: String) -> String {
    let totalMinutes = widgetUtcToKstMinutes(isoString)
    return String(format: "%02d:%02d", totalMinutes / 60, totalMinutes % 60)
}
