//
//  WidgetSupport.swift
//  NaleulWidget (위젯 타겟 전용)
//
//  위젯은 앱 코드를 못 보니까, 위젯 화면에 필요한 색 · 글꼴 · 타입 이름만 여기 따로 둬요.
//  (값은 앱 DesignSystem 과 같아요)

import SwiftUI

typealias TaskSourceType = WidgetTaskSourceType
typealias TaskPriority = WidgetTaskPriority

extension Color {
    init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        self.init(red: Double((int >> 16) & 0xFF) / 255,
                  green: Double((int >> 8) & 0xFF) / 255,
                  blue: Double(int & 0xFF) / 255)
    }

    static let primary50  = Color(hex: "#EBF3F5")
    static let primary400 = Color(hex: "#1E7A90")
    static let primary500 = Color(hex: "#145E72")
    static let primary600 = Color(hex: "#0D4556")

    static let gray50  = Color(hex: "#F4F7F8")
    static let gray100 = Color(hex: "#E0E7EA")
    static let gray200 = Color(hex: "#BDC9CE")
    static let gray300 = Color(hex: "#8FA0A8")
    static let gray400 = Color(hex: "#637580")
    static let gray500 = Color(hex: "#475660")
    static let gray800 = Color(hex: "#111A1F")
    static let gray900 = Color(hex: "#050A0D")
}

enum WidgetTextStyle {
    case h4, labelXS, bodyMDMedium, bodySM

    var font: Font {
        switch self {
        case .h4:           return .custom("PretendardVariable-SemiBold", size: 16)
        case .labelXS:      return .custom("PretendardVariable-Medium", size: 11)
        case .bodyMDMedium: return .custom("PretendardVariable-Medium", size: 14)
        case .bodySM:       return .custom("PretendardVariable-Regular", size: 13)
        }
    }
}

extension View {
    func textStyle(_ style: WidgetTextStyle, color: Color) -> some View {
        font(style.font).foregroundColor(color)
    }
}
