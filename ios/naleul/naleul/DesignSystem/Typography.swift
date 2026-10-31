// DesignSystem/Typography.swift
//
// Pretendard (웹과 같은 폰트) · Component_ver.2 타입 스케일. 모든 스타일 자간 −2%.
//
//   새 스타일 (권장)                         직접 크기 지정 (기존)
//   Text("제목").textStyle(.h2)              .font(.pt(20, .semibold))
//   Text("본문").textStyle(.bodyMD)          .font(.pt(14))
//   .font(.h3) (폰트만 · 줄간격/자간 없이)
//
//   style        size  weight    line-height
//   displayXL    32    Bold      130%
//   displayLG    28    Bold      130%
//   h1           24    SemiBold  140%
//   h2           20    SemiBold  140%
//   h3           18    SemiBold  140%
//   h4           16    SemiBold  150%
//   bodyLG(M)    16    Reg/Med   150%
//   bodyMD(M)    14    Reg/Med   132%
//   bodySM(M)    13    Reg/Med   132%
//   labelLG      14    SemiBold  140%
//   labelMD      13    SemiBold  140%
//   labelSM      12    SemiBold  130%
//   labelXS      11    Medium    130%
//   label2XS      9    Medium    100%
//   captionMD    12    Regular   132%
//   captionSM    11    Regular   132%
//   input        14    Medium    132%   (placeholder: Regular)

import SwiftUI

enum PTWeight: String {
    case regular = "Regular"
    case medium = "Medium"
    case semibold = "SemiBold"
    case bold = "Bold"
    case extrabold = "ExtraBold"
}

extension Font {
    /// Pretendard 폰트. relativeTo 를 줘서 시스템 글자 크기 설정도 따라가요
    static func pt(_ size: CGFloat, _ weight: PTWeight = .regular) -> Font {
        .custom("PretendardVariable-\(weight.rawValue)", size: size, relativeTo: .body)
    }

    /// 숫자 폭을 고정한 Pretendard (시간 · 퍼센트처럼 바뀌는 숫자에)
    static func ptMono(_ size: CGFloat, _ weight: PTWeight = .regular) -> Font {
        pt(size, weight).monospacedDigit()
    }

    // MARK: 타입 스케일 (폰트만 — 줄간격 · 자간까지는 .textStyle(_:))
    static var displayXL: Font { NTextStyle.displayXL.font }
    static var displayLG: Font { NTextStyle.displayLG.font }
    static var h1: Font { NTextStyle.h1.font }
    static var h2: Font { NTextStyle.h2.font }
    static var h3: Font { NTextStyle.h3.font }
    static var h4: Font { NTextStyle.h4.font }
    static var bodyLG: Font { NTextStyle.bodyLG.font }
    static var bodyLGMedium: Font { NTextStyle.bodyLGMedium.font }
    static var bodyMD: Font { NTextStyle.bodyMD.font }
    static var bodyMDMedium: Font { NTextStyle.bodyMDMedium.font }
    static var bodySM: Font { NTextStyle.bodySM.font }
    static var bodySMMedium: Font { NTextStyle.bodySMMedium.font }
    static var labelLG: Font { NTextStyle.labelLG.font }
    static var labelMD: Font { NTextStyle.labelMD.font }
    static var labelSM: Font { NTextStyle.labelSM.font }
    static var labelXS: Font { NTextStyle.labelXS.font }
    static var label2XS: Font { NTextStyle.label2XS.font }
    static var captionMD: Font { NTextStyle.captionMD.font }
    static var captionSM: Font { NTextStyle.captionSM.font }
    static var inputText: Font { NTextStyle.input.font }
    static var inputPlaceholder: Font { NTextStyle.inputPlaceholder.font }
}

/// 디자인 시스템 글자 스타일 (크기 · 굵기 · 줄높이)
enum NTextStyle: CaseIterable {
    case displayXL, displayLG
    case h1, h2, h3, h4
    case bodyLG, bodyLGMedium, bodyMD, bodyMDMedium, bodySM, bodySMMedium
    case labelLG, labelMD, labelSM, labelXS, label2XS
    case captionMD, captionSM
    case input, inputPlaceholder

    var size: CGFloat {
        switch self {
        case .displayXL: return 32
        case .displayLG: return 28
        case .h1: return 24
        case .h2: return 20
        case .h3: return 18
        case .h4, .bodyLG, .bodyLGMedium: return 16
        case .bodyMD, .bodyMDMedium, .labelLG, .input, .inputPlaceholder: return 14
        case .bodySM, .bodySMMedium, .labelMD: return 13
        case .labelSM, .captionMD: return 12
        case .labelXS, .captionSM: return 11
        case .label2XS: return 9
        }
    }

    var weight: PTWeight {
        switch self {
        case .displayXL, .displayLG: return .bold
        case .h1, .h2, .h3, .h4, .labelLG, .labelMD, .labelSM: return .semibold
        case .bodyLGMedium, .bodyMDMedium, .bodySMMedium, .labelXS, .label2XS, .input: return .medium
        case .bodyLG, .bodyMD, .bodySM, .captionMD, .captionSM, .inputPlaceholder: return .regular
        }
    }

    /// 줄높이 배수 (1.3 = 130%)
    var lineHeight: CGFloat {
        switch self {
        case .displayXL, .displayLG, .labelSM, .labelXS: return 1.3
        case .h1, .h2, .h3, .labelLG, .labelMD: return 1.4
        case .h4, .bodyLG, .bodyLGMedium: return 1.5
        case .bodyMD, .bodyMDMedium, .bodySM, .bodySMMedium, .captionMD, .captionSM, .input, .inputPlaceholder: return 1.32
        case .label2XS: return 1.0
        }
    }

    var font: Font { .pt(size, weight) }

    /// 자간 −2%
    var tracking: CGFloat { -0.02 * size }

    /// SwiftUI lineSpacing 근사값 (Pretendard 기본 줄높이 ≈ 1.19em 을 뺀 나머지)
    var lineSpacing: CGFloat { max(0, (lineHeight - 1.19) * size) }
}

extension View {
    /// 디자인 시스템 글자 스타일 (폰트 + 자간 −2% + 줄간격)
    func textStyle(_ style: NTextStyle) -> some View {
        font(style.font)
            .tracking(style.tracking)
            .lineSpacing(style.lineSpacing)
    }

    /// 자간 −2% (웹 letter-spacing: -0.02em)
    func ptTracking(_ size: CGFloat = 15) -> some View { tracking(-0.02 * size) }
}
