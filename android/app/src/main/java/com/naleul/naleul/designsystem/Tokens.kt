// designsystem/Tokens.kt
//
// Naleul Component_ver.2 디자인 토큰 (Figma "Semantic Color" Light/Dark). 웹 · iOS 와 같은 값이에요.
//   iOS                          →  Android
//   .foregroundStyle(Color.ink)  →  Text(..., color = NC.ink)            // 레거시 이름 (새 값으로 다시 연결됨)
//   Color.textPrimary            →  NC.textPrimary                       // 새 시맨틱 이름
//   Radius.card (16)             →  Radius.card (16.dp)
//   .cardShadow()                →  Modifier.cardShadow()
//
// ⚠️ NC.xxx 는 @Composable 안에서만 읽을 수 있어요 (지금 다크 모드인지 알아야 해서).
//    Canvas { } 같은 그리기 블록 안에서는 못 읽으니, 바깥에서 val c = NC.brand 로 꺼내 두고 써요.
// ⚠️ NC.inverse 는 이제 다크 모드에서 "밝은 색"으로 뒤집혀요. inverse 위 글자/아이콘은 NC.onInk(= textInverse) 를 써요.
// ⚠️ 브랜드 색을 "글자"로 쓸 땐 NC.textBrand 를 써요 (NC.brand 는 채우기 색).

package com.naleul.naleul.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.naleul.naleul.core.util.AppContext

/** 지금 다크 모드인지 (NaleulTheme 이 채워요) */
val LocalIsDark = staticCompositionLocalOf { false }

/** "#3D5AFE" / "3D5AFE" / "#3D5AFE80"(RRGGBBAA) / "#FFF" → Color. 잘못된 값이면 회색 */
fun colorHex(hex: String): Color {
    var h = hex.trim().removePrefix("#")
    if (h.length == 3) h = h.map { "$it$it" }.joinToString("")
    val n = h.toLongOrNull(16)
    if (n == null || (h.length != 6 && h.length != 8)) return Color(0xFF7D8898)
    return if (h.length == 8) {
        Color(
            red = ((n shr 24) and 0xFF).toInt(),
            green = ((n shr 16) and 0xFF).toInt(),
            blue = ((n shr 8) and 0xFF).toInt(),
            alpha = (n and 0xFF).toInt(),
        )
    } else {
        Color(red = ((n shr 16) and 0xFF).toInt(), green = ((n shr 8) and 0xFF).toInt(), blue = (n and 0xFF).toInt())
    }
}

/** 웹 hexOf: "#3D5AFE" / "3D5AFE" / null → "#3D5AFE" (없으면 회색 = text/tertiary) */
fun hexOf(code: String?): String {
    if (code.isNullOrEmpty()) return "#7D8898"
    return if (code.startsWith("#")) code else "#$code"
}

/** 목표 색 코드("#3D5AFE" | "3D5AFE" | null) → 색 (팔레트 accent). 없으면 회색 (iOS Color.goal) */
fun goalColor(code: String?): Color = goalToneOf(code, dark = false).accent

/** Color → "#RRGGBB" */
fun Color.toHex(): String {
    fun c(v: Float) = (v * 255).toInt().coerceIn(0, 255).toString(16).padStart(2, '0').uppercase()
    return "#${c(red)}${c(green)}${c(blue)}"
}

/** a 를 b 위에 t 만큼 섞어요 (t=0 → b, t=1 → a) */
fun mixColor(a: Color, b: Color, t: Float): Color = Color(
    red = b.red + (a.red - b.red) * t,
    green = b.green + (a.green - b.green) * t,
    blue = b.blue + (a.blue - b.blue) * t,
    alpha = 1f,
)

/** NC = Naleul Colors. 레거시 이름(ink · brand …)은 디자인 스펙 5장 매핑대로 새 값에 연결돼 있어요 */
object NC {
    @Composable @ReadOnlyComposable
    private fun pick(light: Long, dark: Long): Color = Color(if (LocalIsDark.current) dark else light)

    // MARK: - 새 시맨틱 토큰 (Figma Semantic Color)

    // 배경
    val bgCanvas: Color @Composable @ReadOnlyComposable get() = pick(0xFFFFFFFF, 0xFF121820)
    val bgSurface: Color @Composable @ReadOnlyComposable get() = pick(0xFFFFFFFF, 0xFF1C232C)
    val bgElevated: Color @Composable @ReadOnlyComposable get() = pick(0xFFFFFFFF, 0xFF252D38)
    val bgSubtle: Color @Composable @ReadOnlyComposable get() = pick(0xFFF1F4F8, 0xFF252D38)
    val bgSunken: Color @Composable @ReadOnlyComposable get() = pick(0xFFF6F8FB, 0xFF121820)
    val bgInverse: Color @Composable @ReadOnlyComposable get() = pick(0xFF121820, 0xFFF2F5F9)
    val bgOverlay: Color @Composable @ReadOnlyComposable get() = pick(0x66000000, 0x99000000)

    // 글자
    val textPrimary: Color @Composable @ReadOnlyComposable get() = pick(0xFF121820, 0xFFF2F5F9)
    val textSecondary: Color @Composable @ReadOnlyComposable get() = pick(0xFF6B7685, 0xFFA3AEBD)
    val textTertiary: Color @Composable @ReadOnlyComposable get() = pick(0xFF98A2B0, 0xFF7D8898)
    val textPlaceholder: Color @Composable @ReadOnlyComposable get() = pick(0xFFA3ACB9, 0xFF7D8898)
    val textBrand: Color @Composable @ReadOnlyComposable get() = pick(0xFF0D6B89, 0xFFA3D2E8)
    val textInverse: Color @Composable @ReadOnlyComposable get() = pick(0xFFFFFFFF, 0xFF121820)

    // 선
    val borderSubtle: Color @Composable @ReadOnlyComposable get() = pick(0xFFE7ECF2, 0xFF2F3845)
    val borderSoft: Color @Composable @ReadOnlyComposable get() = pick(0xFFEEF2F6, 0xB22F3845)
    val borderControl: Color @Composable @ReadOnlyComposable get() = pick(0xFFB9C2CD, 0xFF7D8898)
    val borderFocus: Color @Composable @ReadOnlyComposable get() = pick(0xFF0F7A9C, 0xFF6FB1CF)

    // 액션
    val actionPrimary: Color @Composable @ReadOnlyComposable get() = pick(0xFF0F7A9C, 0xFF167A9C)
    val actionPrimaryHover: Color @Composable @ReadOnlyComposable get() = pick(0xFF0D6B89, 0xFF126B88)
    val actionPrimaryPressed: Color @Composable @ReadOnlyComposable get() = pick(0xFF0B5D77, 0xFF0E5B75)
    val actionPrimaryFg: Color @Composable @ReadOnlyComposable get() = pick(0xFFFFFFFF, 0xFFFFFFFF)
    val actionSecondaryBg: Color @Composable @ReadOnlyComposable get() = pick(0xFFE8F4F5, 0xFF2B3646)
    val actionSecondaryBgHover: Color @Composable @ReadOnlyComposable get() = pick(0xFFDCEBEE, 0xFF354258)
    val actionSecondaryBgPressed: Color @Composable @ReadOnlyComposable get() = pick(0xFFCCE1E5, 0xFF3F4D63)
    val actionSecondaryFg: Color @Composable @ReadOnlyComposable get() = pick(0xFF0D6B89, 0xFFF2F5F9)
    val actionSecondaryBorder: Color @Composable @ReadOnlyComposable get() = pick(0xFFD3E6EA, 0xFF3F4A5A)
    val actionOutlineBorder: Color @Composable @ReadOnlyComposable get() = pick(0xFF0F7A9C, 0xFF6FB1CF)
    val ghostHover: Color @Composable @ReadOnlyComposable get() = pick(0xFFE8F4F5, 0xFF252D38)
    val ghostPressed: Color @Composable @ReadOnlyComposable get() = pick(0xFFDCEBEE, 0xFF2F3845)
    val actionDanger: Color @Composable @ReadOnlyComposable get() = pick(0xFFD13F49, 0xFFA33139)
    val actionDangerHover: Color @Composable @ReadOnlyComposable get() = pick(0xFFB83740, 0xFFAC464D)
    val actionDangerPressed: Color @Composable @ReadOnlyComposable get() = pick(0xFF9F3037, 0xFF8B2A30)
    val actionDangerFg: Color @Composable @ReadOnlyComposable get() = pick(0xFFFFFFFF, 0xFFFFFFFF)
    val disabledBg: Color @Composable @ReadOnlyComposable get() = pick(0xFFF1F4F8, 0xFF252D38)
    val disabledFg: Color @Composable @ReadOnlyComposable get() = pick(0xFFA3ACB9, 0xFF566273)
    val selectionBg: Color @Composable @ReadOnlyComposable get() = pick(0xFFF1F4F8, 0xFF2F3845)
    val selectionFg: Color @Composable @ReadOnlyComposable get() = pick(0xFF121820, 0xFFF2F5F9)

    // 상태
    val statusSuccessBg: Color @Composable @ReadOnlyComposable get() = pick(0xFFEDF7F0, 0xFF334745)
    val statusSuccessFg: Color @Composable @ReadOnlyComposable get() = pick(0xFF246B42, 0xFF8ED6AA)
    val statusWarningBg: Color @Composable @ReadOnlyComposable get() = pick(0xFFFFF6DF, 0xFF45443B)
    val statusWarningFg: Color @Composable @ReadOnlyComposable get() = pick(0xFF805A12, 0xFFE8C878)
    val statusErrorBg: Color @Composable @ReadOnlyComposable get() = pick(0xFFFBF0F0, 0xFF443740)
    val statusErrorFg: Color @Composable @ReadOnlyComposable get() = pick(0xFFBC3942, 0xFFE2888E)
    val statusInfoBg: Color @Composable @ReadOnlyComposable get() = pick(0xFFF1F4F8, 0xFF252D38)
    val statusInfoFg: Color @Composable @ReadOnlyComposable get() = pick(0xFF3F4A5A, 0xFFA3AEBD)

    // 강조 · 진행 · 달력
    val accentMint: Color @Composable @ReadOnlyComposable get() = pick(0xFF00CBA8, 0xFF00CBA8)
    val onAccentMint: Color @Composable @ReadOnlyComposable get() = pick(0xFF121820, 0xFF121820)
    val progressTrack: Color @Composable @ReadOnlyComposable get() = pick(0xFFE3F1F4, 0xFF22343D)
    val progressFill: Color @Composable @ReadOnlyComposable get() = pick(0xFF00CBA8, 0xFF00CBA8)
    val calendarToday: Color @Composable @ReadOnlyComposable get() = pick(0xFF0F7A9C, 0xFF167A9C)
    val calendarTodayFg: Color @Composable @ReadOnlyComposable get() = pick(0xFFFFFFFF, 0xFFFFFFFF)
    val calendarHoliday: Color @Composable @ReadOnlyComposable get() = pick(0xFFBC3942, 0xFFE2888E)
    val calendarSaturday: Color @Composable @ReadOnlyComposable get() = pick(0xFF3C5EA8, 0xFF6F98F2)

    // AI
    val aiTint: Color @Composable @ReadOnlyComposable get() = pick(0xFFD9F7F2, 0xFF1C2B3D)
    val aiTintSoft: Color @Composable @ReadOnlyComposable get() = pick(0x99D9F7F2, 0x991C2B3D)
    val aiAccentStart: Color @Composable @ReadOnlyComposable get() = pick(0xFF00CBA8, 0xFF00CBA8)
    val aiAccentEnd: Color @Composable @ReadOnlyComposable get() = pick(0xFF2AA7DF, 0xFF2AA7DF)
    val aiAccentViolet: Color @Composable @ReadOnlyComposable get() = pick(0xFFA18BFA, 0xFFA18BFA)
    val aiGradientStart: Color @Composable @ReadOnlyComposable get() = pick(0xFF1488AB, 0xFF2AA0C6)
    val aiGradientMid: Color @Composable @ReadOnlyComposable get() = pick(0xFF3487C3, 0xFF4D92DC)
    val aiGradientEnd: Color @Composable @ReadOnlyComposable get() = pick(0xFF6F86F0, 0xFF8EA3FF)
    val aiGradientHoverStart: Color @Composable @ReadOnlyComposable get() = pick(0xFF127896, 0xFF3BB0D4)
    val aiGradientHoverMid: Color @Composable @ReadOnlyComposable get() = pick(0xFF2E77AB, 0xFF62A2E6)
    val aiGradientHoverEnd: Color @Composable @ReadOnlyComposable get() = pick(0xFF6276D3, 0xFFA2B4FF)
    val aiGradientPressedStart: Color @Composable @ReadOnlyComposable get() = pick(0xFF0F6782, 0xFF1F86AA)
    val aiGradientPressedMid: Color @Composable @ReadOnlyComposable get() = pick(0xFF276794, 0xFF3D7FC4)
    val aiGradientPressedEnd: Color @Composable @ReadOnlyComposable get() = pick(0xFF5466B6, 0xFF7A8FE8)

    /**
     * AI 그라데이션 Brush (스펙 6: 115° · stops 0 / 0.65 / 1). pressed / hover 면 그 색 세트.
     * 반환값은 일반 Brush 라 Canvas · drawBehind 안에서도 써도 돼요 (밖에서 val g = NC.aiGradient() 로 꺼내 두기).
     */
    @Composable @ReadOnlyComposable
    fun aiGradient(pressed: Boolean = false, hover: Boolean = false): Brush = AiGradientBrush(
        when {
            pressed -> listOf(aiGradientPressedStart, aiGradientPressedMid, aiGradientPressedEnd)
            hover -> listOf(aiGradientHoverStart, aiGradientHoverMid, aiGradientHoverEnd)
            else -> listOf(aiGradientStart, aiGradientMid, aiGradientEnd)
        },
    )

    // 무드 · 유리
    val moodMint: Color @Composable @ReadOnlyComposable get() = pick(0xFFDDF7F1, 0xFF16302D)
    val moodSky: Color @Composable @ReadOnlyComposable get() = pick(0xFFE2F0FC, 0xFF172A3B)
    val moodLavender: Color @Composable @ReadOnlyComposable get() = pick(0xFFEFEAFD, 0xFF25213D)
    val glassBg: Color @Composable @ReadOnlyComposable get() = pick(0xE0FFFFFF, 0xE5141D29)
    val glassBorder: Color @Composable @ReadOnlyComposable get() = pick(0x0D121820, 0x24FFFFFF)
    val glassShadow: Color @Composable @ReadOnlyComposable get() = pick(0x1F121820, 0x80000000)
    val glassShadowSoft: Color @Composable @ReadOnlyComposable get() = pick(0x0F121820, 0x4D000000)
    val shadowPrimary: Color @Composable @ReadOnlyComposable get() = pick(0x40087F8E, 0x59000000)
    val shadowDanger: Color @Composable @ReadOnlyComposable get() = pick(0x38D13F49, 0x59000000)
    /** bg/inverse 위 브랜드 강조 글자 (inverse 가 뒤집히므로 text/brand 의 반대 모드 값) */
    val textBrandOnInverse: Color @Composable @ReadOnlyComposable get() = pick(0xFFA3D2E8, 0xFF0D6B89)
    /** 로고(워드마크 · 심볼) 색 = brand/logo/base */
    val brandLogoBase: Color @Composable @ReadOnlyComposable get() = pick(0xFF0B4F65, 0xFF7FCBE6)
    /** 회고 등 작은 정보 배지 바탕 (bg/subtle 보다 살짝 진하게) */
    val badgeBg: Color @Composable @ReadOnlyComposable get() = pick(0xFFEAEEF3, 0xFF2F3845)
    /** 하루 카드 안 기록 사이 구분선 */
    val entryDivider: Color @Composable @ReadOnlyComposable get() = pick(0xFFE1E6ED, 0xFF2F3845)
    val logoBase: Color @Composable @ReadOnlyComposable get() = pick(0xFF0B4F65, 0xFF7FCBE6)
    val logoAccent: Color @Composable @ReadOnlyComposable get() = pick(0xFF4A90E2, 0xFF5F9CF5)

    // MARK: - 레거시 이름 (스펙 5장 매핑 · 웹/iOS 와 동일)

    // 글자
    val ink: Color @Composable @ReadOnlyComposable get() = pick(0xFF121820, 0xFFF2F5F9)
    val ink2: Color @Composable @ReadOnlyComposable get() = pick(0xFF6B7685, 0xFFA3AEBD)
    val ink3: Color @Composable @ReadOnlyComposable get() = pick(0xFF98A2B0, 0xFF7D8898)
    val ink4: Color @Composable @ReadOnlyComposable get() = pick(0xFFC3CAD3, 0xFF566273)

    // 면 / 선
    val canvas: Color @Composable @ReadOnlyComposable get() = pick(0xFFFFFFFF, 0xFF121820)
    val surface: Color @Composable @ReadOnlyComposable get() = pick(0xFFFFFFFF, 0xFF1C232C)
    val subtle: Color @Composable @ReadOnlyComposable get() = pick(0xFFF1F4F8, 0xFF252D38)
    val line: Color @Composable @ReadOnlyComposable get() = pick(0xFFE7ECF2, 0xFF2F3845)
    val lineStrong: Color @Composable @ReadOnlyComposable get() = pick(0xFFD5DCE4, 0xFF3F4A5A)

    // 포인트 (brand = 채우기 색. 글자로 쓸 땐 textBrand)
    val brand: Color @Composable @ReadOnlyComposable get() = pick(0xFF0F7A9C, 0xFF167A9C)
    val brandStrong: Color @Composable @ReadOnlyComposable get() = pick(0xFF0D6B89, 0xFF126B88)
    val brandSoft: Color @Composable @ReadOnlyComposable get() = pick(0xFFE8F4F5, 0xFF2B3646)

    // 상태
    val success: Color @Composable @ReadOnlyComposable get() = pick(0xFF246B42, 0xFF8ED6AA)
    val successSoft: Color @Composable @ReadOnlyComposable get() = pick(0xFFEDF7F0, 0xFF334745)
    val warning: Color @Composable @ReadOnlyComposable get() = pick(0xFFF5B63A, 0xFFF6C158)
    val warningSoft: Color @Composable @ReadOnlyComposable get() = pick(0xFFFFF6DF, 0xFF45443B)
    val danger: Color @Composable @ReadOnlyComposable get() = pick(0xFFBC3942, 0xFFE2888E)
    val dangerSoft: Color @Composable @ReadOnlyComposable get() = pick(0xFFFBF0F0, 0xFF443740)

    /** inverse(bg/inverse) 위 글자 (= text/inverse. 라이트: 흰색 · 다크: 진한 남색) */
    val onInk: Color @Composable @ReadOnlyComposable get() = pick(0xFFFFFFFF, 0xFF121820)
    /** bg/inverse — 다크 모드에선 밝게 뒤집혀요. 위 글자는 꼭 onInk */
    val inverse: Color @Composable @ReadOnlyComposable get() = pick(0xFF121820, 0xFFF2F5F9)
    /** 연한 노랑 바탕 위 경고 글자 */
    val warningInk: Color @Composable @ReadOnlyComposable get() = pick(0xFF805A12, 0xFFE8C878)
    val warningInkStrong: Color @Composable @ReadOnlyComposable get() = pick(0xFF6B4A0E, 0xFFF0D89A)

    // 타임테이블 빗금 (고정 시간 · 수면)
    val hatch: Color @Composable @ReadOnlyComposable get() = pick(0xFFF6F8FB, 0xFF1A2129)
    val hatchLine: Color @Composable @ReadOnlyComposable get() = pick(0xFFE3E8EE, 0xFF2A323D)
    val sleep: Color @Composable @ReadOnlyComposable get() = pick(0xFFEFEAFD, 0xFF25213D)
    val sleepLine: Color @Composable @ReadOnlyComposable get() = pick(0xFFDED5FA, 0xFF352F55)
    val sleepInk: Color @Composable @ReadOnlyComposable get() = pick(0xFF5C49A8, 0xFFD0C5F9)

    // 히트맵 단계 (1 연함 ~ 5 진함, 틸 계열)
    val heat1: Color @Composable @ReadOnlyComposable get() = pick(0xFFDCEBEE, 0xFF1A3540)
    val heat2: Color @Composable @ReadOnlyComposable get() = pick(0xFFA9D1DC, 0xFF1F4F60)
    val heat3: Color @Composable @ReadOnlyComposable get() = pick(0xFF5FB0C6, 0xFF22708A)
    val heat4: Color @Composable @ReadOnlyComposable get() = pick(0xFF1F8FAE, 0xFF2A93B5)
    val heat5: Color @Composable @ReadOnlyComposable get() = pick(0xFF0B5D77, 0xFF6FB1CF)

    /** 히트맵 빈 칸 · 안 한 칸 (스펙 8.1) */
    val heatEmpty: Color @Composable @ReadOnlyComposable get() = pick(0xFFF1F4F8, 0xFF252D38)

    /** 단계(0~5) → 히트맵 색. 0 은 heatEmpty */
    @Composable @ReadOnlyComposable
    fun heat(level: Int): Color = when {
        level <= 0 -> heatEmpty
        level == 1 -> heat1
        level == 2 -> heat2
        level == 3 -> heat3
        level == 4 -> heat4
        else -> heat5
    }

    /** 카카오 버튼 노랑 */
    val kakao: Color = Color(0xFFFEE500)
    val white: Color = Color.White
    val black: Color = Color.Black
    val clear: Color = Color.Transparent
}

// MARK: - AI 그라데이션 Brush (CSS linear-gradient(115deg, start 0%, mid 65%, end 100%) 과 같은 방향 · 길이)

class AiGradientBrush(
    private val colors: List<Color>,
    private val stops: List<Float> = listOf(0f, 0.65f, 1f),
    private val angleDeg: Float = 115f,
) : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val rad = Math.toRadians(angleDeg.toDouble())
        // CSS 각도: 0deg = 위쪽, 시계 방향 → 화면 좌표(y 아래) 방향 벡터
        val dx = kotlin.math.sin(rad).toFloat()
        val dy = (-kotlin.math.cos(rad)).toFloat()
        val len = kotlin.math.abs(size.width * dx) + kotlin.math.abs(size.height * dy)
        val cx = size.width / 2f
        val cy = size.height / 2f
        return LinearGradientShader(
            from = Offset(cx - dx * len / 2f, cy - dy * len / 2f),
            to = Offset(cx + dx * len / 2f, cy + dy * len / 2f),
            colors = colors,
            colorStops = stops,
        )
    }

    override fun equals(other: Any?): Boolean =
        other is AiGradientBrush && other.colors == colors && other.stops == stops && other.angleDeg == angleDeg

    override fun hashCode(): Int = (colors.hashCode() * 31 + stops.hashCode()) * 31 + angleDeg.hashCode()
}

// MARK: - 목표 색 팔레트 (21색 · bg / fg / accent)

/** 목표 색 한 벌: bg(연한 바탕) · fg(바탕 위 글자) · accent(막대 · 점) */
data class GoalTone(val bg: Color, val fg: Color, val accent: Color)

enum class GoalPalette(
    val lightBg: Long, val lightFg: Long, val lightAccent: Long,
    val darkBg: Long, val darkFg: Long, val darkAccent: Long,
) {
    coral(0xFFFDECEC, 0xFF9D4B4B, 0xFFF27474, 0xFF47373F, 0xFFFACACA, 0xFFF48989),
    orange(0xFFFEF0E7, 0xFF935730, 0xFFF59150, 0xFF483C38, 0xFFFBD5BC, 0xFFF6A16A),
    amber(0xFFFEF5E3, 0xFF876420, 0xFFF5B63A, 0xFF484335, 0xFFFBE3B4, 0xFFF6C158),
    yellow(0xFFFEFAE8, 0xFF7B6E2F, 0xFFF5DC5E, 0xFF48493B, 0xFFFBF2C1, 0xFFF6E176),
    lime(0xFFF2FAE8, 0xFF526D2F, 0xFFA3D95E, 0xFF3A493B, 0xFFDCF0C1, 0xFFB1DF76),
    green(0xFFE6F7ED, 0xFF2D764A, 0xFF4BC47C, 0xFF2B4540, 0xFFBAE8CD, 0xFF66CD90),
    mint(0xFFE0F7F3, 0xFF137664, 0xFF1FC4A6, 0xFF234547, 0xFFA9E8DD, 0xFF41CDB3),
    sky(0xFFE4F6FC, 0xFF25738A, 0xFF3DBFE6, 0xFF284452, 0xFFB5E7F5, 0xFF5AC9EA),
    blue(0xFFE7EEFD, 0xFF3C5EA8, 0xFF5586F0, 0xFF2D3A54, 0xFFBED1F9, 0xFF6F98F2),
    purple(0xFFEEEAFD, 0xFF5C49A8, 0xFF8468F0, 0xFF343554, 0xFFD0C5F9, 0xFF967FF2),
    violet(0xFFF5EBFC, 0xFF7F51A2, 0xFFB573E8, 0xFF3D3752, 0xFFE3C9F6, 0xFFC088EB),
    pink(0xFFFDEFFA, 0xFF915383, 0xFFF28BDB, 0xFF473B50, 0xFFFAD3F1, 0xFFF49CE0),
    rose(0xFFFDEBF1, 0xFF9C4967, 0xFFF0709E, 0xFF473746, 0xFFF9C8DA, 0xFFF285AD),
    teal(0xFFE2F5F7, 0xFF1B7078, 0xFF2DBBC8, 0xFF26434D, 0xFFAFE5EA, 0xFF4DC5D0),
    slate(0xFFEAEEF1, 0xFF4A5C6B, 0xFF6A8499, 0xFF303A45, 0xFFC6D0D8, 0xFF8096A8),
    brown(0xFFF4EFEA, 0xFF7B6147, 0xFFB08A66, 0xFF3C3B3C, 0xFFE1D2C4, 0xFFBC9C7D),
    sand(0xFFF8F5EF, 0xFF71654C, 0xFFCDB88B, 0xFF414342, 0xFFECE4D3, 0xFFD4C39C),
    sage(0xFFEFF4F0, 0xFF56685A, 0xFF8FAE96, 0xFF364144, 0xFFD4E0D7, 0xFFA0BAA6),
    navy(0xFFE6E9F3, 0xFF374576, 0xFF4F63A8, 0xFF2B3447, 0xFFBCC3DE, 0xFF697AB5),
    plum(0xFFF2EAF1, 0xFF744C6D, 0xFFA56C9C, 0xFF3A3645, 0xFFDDC7D9, 0xFFB282AB),
    /** 색 없음 = bg/subtle · text/secondary · text/tertiary */
    neutral(0xFFF1F4F8, 0xFF6B7685, 0xFF98A2B0, 0xFF252D38, 0xFFA3AEBD, 0xFF7D8898);

    fun tone(dark: Boolean): GoalTone =
        if (dark) GoalTone(Color(darkBg), Color(darkFg), Color(darkAccent))
        else GoalTone(Color(lightBg), Color(lightFg), Color(lightAccent))

    companion object {
        /** 백엔드 색(아무 hex)과 가장 가까운 팔레트 색 + RGB 거리(0~441) */
        fun nearest(color: Color): Pair<GoalPalette, Float> {
            var best = neutral
            var bestD = Float.MAX_VALUE
            for (p in values()) {
                if (p == neutral) continue
                val a = Color(p.lightAccent)
                val dr = (a.red - color.red) * 255f
                val dg = (a.green - color.green) * 255f
                val db = (a.blue - color.blue) * 255f
                val d = kotlin.math.sqrt(dr * dr + dg * dg + db * db)
                if (d < bestD) { bestD = d; best = p }
            }
            return best to bestD
        }
    }
}

/**
 * 목표 색 코드 → bg/fg/accent (Composable 아닌 버전. Canvas 안 등에서 dark 값을 직접 넘겨요).
 * 팔레트에 가까운 색(RGB 거리 ≤ 60)이 있으면 그 색, 아니면 hex 로 직접 만들어요. null/빈 값 → neutral.
 */
fun goalToneOf(hex: String?, dark: Boolean): GoalTone {
    if (hex.isNullOrBlank()) return GoalPalette.neutral.tone(dark)
    val c = colorHex(hexOf(hex))
    val (p, d) = GoalPalette.nearest(c)
    if (d <= 60f) return p.tone(dark)
    val surface = if (dark) Color(0xFF1C232C) else Color(0xFFFFFFFF)
    val bg = mixColor(c, surface, if (dark) 0.22f else 0.12f)
    val fg = if (dark) mixColor(Color.White, c, 0.6f) else mixColor(Color.Black, c, 0.45f)
    return GoalTone(bg = bg, fg = fg, accent = c)
}

/** 목표 색 코드 → bg/fg/accent (지금 라이트/다크 모드 기준) */
@Composable @ReadOnlyComposable
fun goalTone(hex: String?): GoalTone = goalToneOf(hex, LocalIsDark.current)

// MARK: - 모서리 · 그림자

object Radius {
    /** Card · GoalSummary · Dialog */
    val card = 16.dp
    /** rounded-xl */
    val xl = 12.dp
    /** rounded-2xl */
    val xxl = 16.dp
    /** rounded-lg */
    val lg = 8.dp
    /** 시트 위 모서리 */
    val sheet = 24.dp

    val btn = 12.dp
    val btnSm = 10.dp
    val input = 10.dp
    val chip = 8.dp
    val chipSm = 6.dp
    val event = 8.dp
    val eventFixed = 10.dp
    val nav = 8.dp
    val iconBtn = 6.dp
    val toast = 12.dp
    val dialog = 16.dp
    val checkbox = 6.dp
    val segment = 12.dp
    val segmentItem = 10.dp
    val quickReply = 20.dp
    val composer = 26.dp
}

/** shadow-card — 스펙 12.3: 더 옅게 (0 2 8, 라이트 rgba(18,24,32,.04) · 다크 25%) */
fun Modifier.cardShadow(shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(Radius.card)): Modifier = composed {
    val c = if (LocalIsDark.current) Color.Black.copy(alpha = 0.25f) else Color(0xFF121820).copy(alpha = 0.04f)
    this.shadow(elevation = 2.dp, shape = shape, ambientColor = c, spotColor = c)
}

/** shadow-pop (glass/shadow: 0 12 40, 라이트 12% · 다크 50%) */
fun Modifier.popShadow(shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(Radius.card)): Modifier = composed {
    val c = if (LocalIsDark.current) Color.Black.copy(alpha = 0.50f) else Color(0xFF121820).copy(alpha = 0.12f)
    this.shadow(elevation = 12.dp, shape = shape, ambientColor = c, spotColor = c)
}

/** primary 버튼 그림자 (0 4 12 rgba(8,127,142,.25) · 다크 검정 35%) */
fun Modifier.primaryShadow(shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(Radius.btn)): Modifier = composed {
    val c = if (LocalIsDark.current) Color.Black.copy(alpha = 0.35f) else Color(0xFF087F8E).copy(alpha = 0.25f)
    this.shadow(elevation = 4.dp, shape = shape, ambientColor = c, spotColor = c)
}

/** 세그먼트 선택 칸 그림자 (0 1 3 8%) */
fun Modifier.segShadow(shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(Radius.segmentItem)): Modifier =
    this.shadow(elevation = 1.dp, shape = shape, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.08f))

/**
 * AI 결과 컨테이너 (스펙 6B): glass/bg(또는 surface) 바탕 + 1.5dp 그라데이션 테두리(안쪽) + 그림자 (0 12 36 8% · 0 2 8 4%).
 * padding 보다 먼저 써요:  Column(Modifier.aiBorder().padding(14.dp)) { … }
 */
fun Modifier.aiBorder(
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(Radius.card),
    glass: Boolean = true,
    /** surface 위에 겹칠 연한 색 (예: NC.aiTintSoft — AI 분석 카드) */
    tint: Color? = null,
): Modifier = composed {
    val dark = LocalIsDark.current
    val gradient = NC.aiGradient()
    val surface = NC.bgSurface
    val glassBg = NC.glassBg
    val c1 = Color.Black.copy(alpha = if (dark) 0.30f else 0.08f)
    val c2 = Color.Black.copy(alpha = if (dark) 0.20f else 0.04f)
    this
        .shadow(elevation = 12.dp, shape = shape, ambientColor = c1, spotColor = c1)
        .shadow(elevation = 2.dp, shape = shape, ambientColor = c2, spotColor = c2)
        .clip(shape)
        .background(surface, shape)
        .then(if (glass) Modifier.background(glassBg, shape) else Modifier)
        .then(if (tint != null) Modifier.background(tint, shape) else Modifier)
        .border(1.5.dp, gradient, shape)
}

// MARK: - 화면 모드 (설정 > 화면 모드: 시스템 / 라이트 / 다크)

enum class ThemePref {
    system, light, dark;

    val id: String get() = name

    val label: String
        get() = when (this) {
            system -> "시스템"
            light -> "라이트"
            dark -> "다크"
        }

    /** SF Symbol 이름 (SfIcon 으로 그려요) */
    val icon: String
        get() = when (this) {
            system -> "iphone"
            light -> "sun.max"
            dark -> "moon"
        }

    companion object {
        /** SharedPreferences 키 (웹 localStorage 'naleul-theme' 와 같은 이름) */
        const val storageKey = "naleul-theme"
    }
}

/** 화면 모드 저장소 (iOS @AppStorage(ThemePref.storageKey)) */
object ThemeStore {
    private val prefs by lazy { AppContext.app.getSharedPreferences("naleul_prefs", android.content.Context.MODE_PRIVATE) }

    var pref: ThemePref by mutableStateOf(ThemePref.system)
        private set

    /** 앱 시작 때 한 번 (NaleulApplication) */
    fun load() {
        pref = runCatching { ThemePref.valueOf(prefs.getString(ThemePref.storageKey, "system") ?: "system") }
            .getOrDefault(ThemePref.system)
    }

    fun set(value: ThemePref) {
        pref = value
        prefs.edit().putString(ThemePref.storageKey, value.name).apply()
    }

    /** 지금 다크로 그려야 하는지 */
    @Composable
    fun isDark(): Boolean = when (pref) {
        ThemePref.system -> isSystemInDarkTheme()
        ThemePref.light -> false
        ThemePref.dark -> true
    }
}
