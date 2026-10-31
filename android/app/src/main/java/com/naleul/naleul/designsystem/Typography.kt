// designsystem/Typography.kt
//
// iOS DesignSystem/Typography.swift — Pretendard (웹과 같은 폰트).
//   iOS                          →  Android
//   .font(.pt(15, .bold))        →  Text(..., style = pt(15, PTWeight.bold))
//   .font(.ptMono(13))           →  style = ptMono(13)   (숫자 폭 고정)
//
// res/font/pretendard_variable.ttf 는 "가변 폰트"라 한 파일에 모든 굵기가 들어 있어요.
// FontVariation.weight(600) 처럼 굵기 값을 골라서 꺼내 써요.

@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.naleul.naleul.designsystem

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.naleul.naleul.R

enum class PTWeight(val weight: FontWeight) {
    regular(FontWeight.Normal),
    medium(FontWeight.Medium),
    semibold(FontWeight.SemiBold),
    bold(FontWeight.Bold),
    extrabold(FontWeight.ExtraBold),
}

private fun pretendard(weight: FontWeight) = Font(
    resId = R.font.pretendard_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val Pretendard: FontFamily = FontFamily(
    pretendard(FontWeight.Normal),
    pretendard(FontWeight.Medium),
    pretendard(FontWeight.SemiBold),
    pretendard(FontWeight.Bold),
    pretendard(FontWeight.ExtraBold),
)

/** Pretendard 글자 모양. 디자인 시스템의 letter-spacing −2% (−0.02em) 를 같이 넣어요 */
fun pt(size: Int, weight: PTWeight = PTWeight.regular): TextStyle = TextStyle(
    fontFamily = Pretendard,
    fontWeight = weight.weight,
    fontSize = size.sp,
    letterSpacing = (-0.02).em,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

/** pt + 줄 높이(배수). 예: ptLH(16, PTWeight.regular, 1.5f) = 16 / 150% */
fun ptLH(size: Int, weight: PTWeight, lineHeight: Float): TextStyle =
    pt(size, weight).copy(lineHeight = (size * lineHeight).sp)

/** 숫자 폭을 고정한 Pretendard (시간 · 퍼센트처럼 바뀌는 숫자에) */
fun ptMono(size: Int, weight: PTWeight = PTWeight.regular): TextStyle =
    pt(size, weight).copy(fontFeatureSettings = "tnum")

/**
 * Component_ver.2 타입 스케일 (Pretendard · −0.02em).
 *   Text("제목", style = NType.h2, color = NC.textPrimary)
 */
object NType {
    val displayXL: TextStyle = ptLH(32, PTWeight.bold, 1.3f)
    val displayLG: TextStyle = ptLH(28, PTWeight.bold, 1.3f)
    val h1: TextStyle = ptLH(24, PTWeight.semibold, 1.4f)
    val h2: TextStyle = ptLH(20, PTWeight.semibold, 1.4f)
    val h3: TextStyle = ptLH(18, PTWeight.semibold, 1.4f)
    val h4: TextStyle = ptLH(16, PTWeight.semibold, 1.5f)
    val bodyLG: TextStyle = ptLH(16, PTWeight.regular, 1.5f)
    val bodyLGMedium: TextStyle = ptLH(16, PTWeight.medium, 1.5f)
    val bodyMD: TextStyle = ptLH(14, PTWeight.regular, 1.32f)
    val bodyMDMedium: TextStyle = ptLH(14, PTWeight.medium, 1.32f)
    val bodySM: TextStyle = ptLH(13, PTWeight.regular, 1.32f)
    val bodySMMedium: TextStyle = ptLH(13, PTWeight.medium, 1.32f)
    val labelLG: TextStyle = ptLH(14, PTWeight.semibold, 1.4f)
    val labelMD: TextStyle = ptLH(13, PTWeight.semibold, 1.4f)
    val labelSM: TextStyle = ptLH(12, PTWeight.semibold, 1.3f)
    val labelXS: TextStyle = ptLH(11, PTWeight.medium, 1.3f)
    val label2XS: TextStyle = ptLH(9, PTWeight.medium, 1.0f)
    val captionMD: TextStyle = ptLH(12, PTWeight.regular, 1.32f)
    val captionSM: TextStyle = ptLH(11, PTWeight.regular, 1.32f)
    val input: TextStyle = ptLH(14, PTWeight.medium, 1.32f)
    val inputPlaceholder: TextStyle = ptLH(14, PTWeight.regular, 1.32f)
}

// 최상위 별칭 (웹/iOS 이름과 맞춤)
val displayXL: TextStyle get() = NType.displayXL
val displayLG: TextStyle get() = NType.displayLG
val h1: TextStyle get() = NType.h1
val h2: TextStyle get() = NType.h2
val h3: TextStyle get() = NType.h3
val h4: TextStyle get() = NType.h4
val bodyLG: TextStyle get() = NType.bodyLG
val bodyLGMedium: TextStyle get() = NType.bodyLGMedium
val bodyMD: TextStyle get() = NType.bodyMD
val bodyMDMedium: TextStyle get() = NType.bodyMDMedium
val bodySM: TextStyle get() = NType.bodySM
val bodySMMedium: TextStyle get() = NType.bodySMMedium
val labelLG: TextStyle get() = NType.labelLG
val labelMD: TextStyle get() = NType.labelMD
val labelSM: TextStyle get() = NType.labelSM
val labelXS: TextStyle get() = NType.labelXS
val captionMD: TextStyle get() = NType.captionMD
val captionSM: TextStyle get() = NType.captionSM
