// designsystem/NIcon.kt
//
// Figma 디자인 시스템 아이콘 59종 (node 2932:11149 · Lucide 기반 · stroke 1.5 · currentColor).
// res/drawable/ic_n_<name>.xml (24dp, stroke 1.5) + ic_n_<name>_sm.xml (stroke 1.875 → 16dp 에서 1.25px).
//
//   NIcon.plus.Icon()                                  // 20dp · 주변 글자색
//   NIcon.check.Icon(size = 14.dp, tint = NC.textBrand) // 16dp 이하는 _sm(얇은 선) 버전을 자동으로 써요
//   SfImage("checkmark")                               // iOS SF 이름도 sfToNIcon 으로 여기로 와요
//
// bellDot 의 빨간 점(#BC3942)은 틴트를 받지 않게 따로 그려요 (ic_n_bell_dot_badge).

package com.naleul.naleul.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.naleul.naleul.R

enum class NIcon(@DrawableRes val res: Int, @DrawableRes val resSm: Int) {
    arrowLeft(R.drawable.ic_n_arrow_left, R.drawable.ic_n_arrow_left_sm),
    arrowRight(R.drawable.ic_n_arrow_right, R.drawable.ic_n_arrow_right_sm),
    arrowUp(R.drawable.ic_n_arrow_up, R.drawable.ic_n_arrow_up_sm),
    badgeCheckFilled(R.drawable.ic_n_badge_check_filled, R.drawable.ic_n_badge_check_filled_sm),
    bellDot(R.drawable.ic_n_bell_dot, R.drawable.ic_n_bell_dot_sm),
    bell(R.drawable.ic_n_bell, R.drawable.ic_n_bell_sm),
    calendarClock(R.drawable.ic_n_calendar_clock, R.drawable.ic_n_calendar_clock_sm),
    calendar(R.drawable.ic_n_calendar, R.drawable.ic_n_calendar_sm),
    camera(R.drawable.ic_n_camera, R.drawable.ic_n_camera_sm),
    chartBar(R.drawable.ic_n_chart_bar, R.drawable.ic_n_chart_bar_sm),
    check(R.drawable.ic_n_check, R.drawable.ic_n_check_sm),
    chevronDown(R.drawable.ic_n_chevron_down, R.drawable.ic_n_chevron_down_sm),
    chevronLeft(R.drawable.ic_n_chevron_left, R.drawable.ic_n_chevron_left_sm),
    chevronRight(R.drawable.ic_n_chevron_right, R.drawable.ic_n_chevron_right_sm),
    chevronUp(R.drawable.ic_n_chevron_up, R.drawable.ic_n_chevron_up_sm),
    clock(R.drawable.ic_n_clock, R.drawable.ic_n_clock_sm),
    compass(R.drawable.ic_n_compass, R.drawable.ic_n_compass_sm),
    crown(R.drawable.ic_n_crown, R.drawable.ic_n_crown_sm),
    download(R.drawable.ic_n_download, R.drawable.ic_n_download_sm),
    ellipsisVertical(R.drawable.ic_n_ellipsis_vertical, R.drawable.ic_n_ellipsis_vertical_sm),
    eye(R.drawable.ic_n_eye, R.drawable.ic_n_eye_sm),
    gripVertical(R.drawable.ic_n_grip_vertical, R.drawable.ic_n_grip_vertical_sm),
    heartFilled(R.drawable.ic_n_heart_filled, R.drawable.ic_n_heart_filled_sm),
    heart(R.drawable.ic_n_heart, R.drawable.ic_n_heart_sm),
    home(R.drawable.ic_n_home, R.drawable.ic_n_home_sm),
    image(R.drawable.ic_n_image, R.drawable.ic_n_image_sm),
    info(R.drawable.ic_n_info, R.drawable.ic_n_info_sm),
    listChecks(R.drawable.ic_n_list_checks, R.drawable.ic_n_list_checks_sm),
    lock(R.drawable.ic_n_lock, R.drawable.ic_n_lock_sm),
    logOut(R.drawable.ic_n_log_out, R.drawable.ic_n_log_out_sm),
    menu(R.drawable.ic_n_menu, R.drawable.ic_n_menu_sm),
    minus(R.drawable.ic_n_minus, R.drawable.ic_n_minus_sm),
    missionPlay(R.drawable.ic_n_mission_play, R.drawable.ic_n_mission_play_sm),
    notebook(R.drawable.ic_n_notebook, R.drawable.ic_n_notebook_sm),
    palette(R.drawable.ic_n_palette, R.drawable.ic_n_palette_sm),
    panelLeftClose(R.drawable.ic_n_panel_left_close, R.drawable.ic_n_panel_left_close_sm),
    panelLeftOpen(R.drawable.ic_n_panel_left_open, R.drawable.ic_n_panel_left_open_sm),
    panelRightOpen(R.drawable.ic_n_panel_right_open, R.drawable.ic_n_panel_right_open_sm),
    pencil(R.drawable.ic_n_pencil, R.drawable.ic_n_pencil_sm),
    pin(R.drawable.ic_n_pin, R.drawable.ic_n_pin_sm),
    plus(R.drawable.ic_n_plus, R.drawable.ic_n_plus_sm),
    repeat(R.drawable.ic_n_repeat, R.drawable.ic_n_repeat_sm),
    search(R.drawable.ic_n_search, R.drawable.ic_n_search_sm),
    settings(R.drawable.ic_n_settings, R.drawable.ic_n_settings_sm),
    sliders(R.drawable.ic_n_sliders, R.drawable.ic_n_sliders_sm),
    sortAscending(R.drawable.ic_n_sort_ascending, R.drawable.ic_n_sort_ascending_sm),
    sparkles(R.drawable.ic_n_sparkles, R.drawable.ic_n_sparkles_sm),
    squarePen(R.drawable.ic_n_square_pen, R.drawable.ic_n_square_pen_sm),
    tag(R.drawable.ic_n_tag, R.drawable.ic_n_tag_sm),
    target(R.drawable.ic_n_target, R.drawable.ic_n_target_sm),
    trash(R.drawable.ic_n_trash, R.drawable.ic_n_trash_sm),
    trendingDown(R.drawable.ic_n_trending_down, R.drawable.ic_n_trending_down_sm),
    trendingUp(R.drawable.ic_n_trending_up, R.drawable.ic_n_trending_up_sm),
    trophy(R.drawable.ic_n_trophy, R.drawable.ic_n_trophy_sm),
    upload(R.drawable.ic_n_upload, R.drawable.ic_n_upload_sm),
    userFilled(R.drawable.ic_n_user_filled, R.drawable.ic_n_user_filled_sm),
    user(R.drawable.ic_n_user, R.drawable.ic_n_user_sm),
    users(R.drawable.ic_n_users, R.drawable.ic_n_users_sm),
    x(R.drawable.ic_n_x, R.drawable.ic_n_x_sm);

    /** 이 크기에 맞는 drawable (16dp 이하 = 얇은 선) */
    @DrawableRes
    fun resFor(size: Dp): Int = if (size <= 16.dp) resSm else res
}

/** Figma 아이콘 그리기. tint 는 아이콘 선 색 (기본 = 주변 글자색) */
@Composable
fun NIcon.Icon(
    size: Dp = 20.dp,
    tint: Color = LocalContentColor.current,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
) {
    if (this == NIcon.bellDot) {
        Box(modifier.size(size)) {
            androidx.compose.material3.Icon(
                painterResource(resFor(size)), contentDescription = contentDescription, tint = tint, modifier = Modifier.size(size),
            )
            // 빨간 점은 틴트 없이 원래 색 그대로
            Image(painterResource(R.drawable.ic_n_bell_dot_badge), contentDescription = null, modifier = Modifier.size(size))
        }
        return
    }
    androidx.compose.material3.Icon(
        painterResource(resFor(size)), contentDescription = contentDescription, tint = tint, modifier = modifier.size(size),
    )
}

/** iOS SF Symbol 이름 → Figma 아이콘 (없으면 null → SfIcon.kt 의 머티리얼 아이콘) */
fun sfToNIcon(name: String): NIcon? = when (name) {
    "arrow.left" -> NIcon.arrowLeft
    "arrow.right" -> NIcon.arrowRight
    "arrow.up" -> NIcon.arrowUp
    "chevron.left" -> NIcon.chevronLeft
    "chevron.right" -> NIcon.chevronRight
    "chevron.down" -> NIcon.chevronDown
    "chevron.up" -> NIcon.chevronUp
    "repeat" -> NIcon.repeat
    "bell" -> NIcon.bell
    "bell.fill" -> NIcon.bell
    "bell.badge" -> NIcon.bellDot
    "book.closed" -> NIcon.notebook
    "note.text" -> NIcon.notebook
    "pencil" -> NIcon.pencil
    "pencil.line" -> NIcon.pencil
    "square.and.pencil" -> NIcon.squarePen
    "line.3.horizontal" -> NIcon.menu
    "calendar" -> NIcon.calendar
    "calendar.badge.clock" -> NIcon.calendarClock
    "clock" -> NIcon.clock
    "clock.fill" -> NIcon.clock
    "chart.line.uptrend.xyaxis" -> NIcon.trendingUp
    "chart.xyaxis.line" -> NIcon.trendingUp
    "chart.bar.xaxis" -> NIcon.chartBar
    "target" -> NIcon.target
    "checkmark" -> NIcon.check
    "checklist" -> NIcon.listChecks
    "xmark" -> NIcon.x
    "info.circle" -> NIcon.info
    "info.circle.fill" -> NIcon.info
    "lock" -> NIcon.lock
    "lock.fill" -> NIcon.lock
    "eye" -> NIcon.eye
    "plus" -> NIcon.plus
    "trash" -> NIcon.trash
    "ellipsis" -> NIcon.ellipsisVertical
    "ellipsis.circle" -> NIcon.ellipsisVertical
    "square.and.arrow.up" -> NIcon.upload
    "pin.fill" -> NIcon.pin
    "play.fill" -> NIcon.missionPlay
    "rectangle.portrait.and.arrow.right" -> NIcon.logOut
    "sparkles" -> NIcon.sparkles
    "sparkle" -> NIcon.sparkles
    "star" -> NIcon.sparkles
    "star.fill" -> NIcon.sparkles
    "crown" -> NIcon.crown
    "crown.fill" -> NIcon.crown
    "house" -> NIcon.home
    "gearshape" -> NIcon.settings
    "person.crop.circle.badge.checkmark" -> NIcon.badgeCheckFilled
    "minus" -> NIcon.minus
    "magnifyingglass" -> NIcon.search
    "person" -> NIcon.user
    "person.fill" -> NIcon.userFilled
    "person.2" -> NIcon.users
    "heart" -> NIcon.heart
    "heart.fill" -> NIcon.heartFilled
    "tag" -> NIcon.tag
    "trophy" -> NIcon.trophy
    "photo" -> NIcon.image
    "camera" -> NIcon.camera
    "paintpalette" -> NIcon.palette
    "slider.horizontal.3" -> NIcon.sliders
    "arrow.up.arrow.down" -> NIcon.sortAscending
    "square.and.arrow.down" -> NIcon.download
    "arrow.down.right" -> NIcon.trendingDown
    else -> null
}

// MARK: - 로고 (Figma brand/logo — 워드마크 118×32 · 심볼 42×28)

/** "나를" 워드마크. height 만 정하면 폭은 비율대로 */
@Composable
fun Wordmark(height: Dp = 24.dp, tint: Color = NC.brandLogoBase, modifier: Modifier = Modifier) {
    androidx.compose.material3.Icon(
        painterResource(R.drawable.ic_n_wordmark), contentDescription = "나를", tint = tint,
        modifier = modifier.size(width = height * (118f / 32f), height = height),
    )
}

/** 심볼 마크 */
@Composable
fun BrandSymbol(height: Dp = 28.dp, tint: Color = NC.brandLogoBase, modifier: Modifier = Modifier) {
    androidx.compose.material3.Icon(
        painterResource(R.drawable.ic_n_symbol), contentDescription = "나를", tint = tint,
        modifier = modifier.size(width = height * (42f / 28f), height = height),
    )
}
