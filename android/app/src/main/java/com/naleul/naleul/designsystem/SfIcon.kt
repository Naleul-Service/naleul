// designsystem/SfIcon.kt
//
// iOS 는 아이콘을 SF Symbols 이름("checkmark.circle.fill")으로 그려요. Android 에는 SF Symbols 가 없어서,
// 같은 이름을 받아 비슷한 머티리얼 아이콘(material-icons-extended)으로 바꿔 줘요.
// 덕분에 iOS 코드의 아이콘 이름을 그대로 옮겨 쓸 수 있어요.
//
//   SfImage("checkmark.circle.fill", size = 16.dp, tint = NC.success)   // iOS Image(systemName:)
//   val v: ImageVector = sfIcon("plus")
//
// 목록에 없는 이름은 회색 동그라미(Circle)로 그려요 → 화면에서 동그라미가 보이면 여기에 추가하세요.
// ⚠️ SfImage 는 먼저 NIcon.kt 의 sfToNIcon 을 봐요 — Figma 아이콘이 있는 이름은 그 아이콘(ic_n_*)으로 그려요.

package com.naleul.naleul.designsystem

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bed
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.EventNote
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.outlined.Lock as LockOutlined
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Drafts
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsScore
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.HowToReg
import androidx.compose.material.icons.rounded.Abc
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.SouthEast
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.LibraryBooks
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** SF Symbol 이름 → 머티리얼 아이콘 */
fun sfIcon(name: String): ImageVector = when (name) {
    // 화살표 · 방향
    "arrow.clockwise" -> Icons.Rounded.Refresh
    "arrow.counterclockwise" -> Icons.Rounded.Replay
    "arrow.uturn.backward" -> Icons.AutoMirrored.Rounded.Undo
    "arrow.left" -> Icons.AutoMirrored.Rounded.ArrowBack
    "arrow.right" -> Icons.AutoMirrored.Rounded.ArrowForward
    "arrow.up" -> Icons.Rounded.ArrowUpward
    "arrow.down" -> Icons.Rounded.ArrowDownward
    "arrow.up.right" -> Icons.Rounded.NorthEast
    "arrow.down.right" -> Icons.Rounded.SouthEast
    "arrow.turn.down.left", "return" -> Icons.AutoMirrored.Rounded.KeyboardReturn
    "chevron.left" -> Icons.Rounded.ChevronLeft
    "chevron.right" -> Icons.Rounded.ChevronRight
    "chevron.down" -> Icons.Rounded.ExpandMore
    "chevron.up" -> Icons.Rounded.ExpandLess
    "chevron.up.chevron.down" -> Icons.Rounded.UnfoldMore
    "repeat" -> Icons.Rounded.Repeat

    // 알림
    "bell" -> Icons.Rounded.NotificationsNone
    "bell.fill" -> Icons.Rounded.Notifications
    "bell.badge" -> Icons.Rounded.NotificationsActive
    "bell.slash" -> Icons.Rounded.NotificationsOff
    "alarm" -> Icons.Rounded.Alarm

    // 문서 · 기록
    "book.closed" -> Icons.Rounded.Book
    "books.vertical" -> Icons.Rounded.LibraryBooks
    "doc.text", "doc.text.fill" -> Icons.Rounded.Description
    "doc.on.doc" -> Icons.Rounded.ContentCopy
    "note.text" -> Icons.AutoMirrored.Rounded.Notes
    "list.bullet.clipboard", "checkmark.rectangle.stack" -> Icons.AutoMirrored.Rounded.ListAlt
    "chart.bar.doc.horizontal" -> Icons.Rounded.Insights
    "pencil", "square.and.pencil" -> Icons.Rounded.Edit
    "pencil.line" -> Icons.Rounded.EditNote
    "envelope.open" -> Icons.Rounded.Drafts
    "tray.full" -> Icons.Rounded.Inbox
    "abc" -> Icons.Rounded.Abc
    "line.3.horizontal" -> Icons.Rounded.DragHandle

    // 대화
    "bubble.left" -> Icons.Rounded.ChatBubbleOutline
    "message", "message.fill" -> Icons.AutoMirrored.Rounded.Chat
    "text.bubble" -> Icons.Rounded.Forum

    // 캘린더 · 시간
    "calendar" -> Icons.Rounded.CalendarMonth
    "calendar.badge.checkmark" -> Icons.Rounded.EventAvailable
    "calendar.badge.clock" -> Icons.Rounded.EventNote
    "calendar.badge.exclamationmark" -> Icons.Rounded.Event
    "calendar.badge.minus" -> Icons.Rounded.EventBusy
    "calendar.badge.plus" -> Icons.Rounded.EditCalendar
    "clock", "clock.fill" -> Icons.Rounded.Schedule
    "timer" -> Icons.Rounded.Timer
    "hourglass" -> Icons.Rounded.HourglassEmpty
    "sunrise" -> Icons.Rounded.WbTwilight
    "sun.max", "sun" -> Icons.Rounded.LightMode
    "moon" -> Icons.Rounded.DarkMode
    "moon.stars" -> Icons.Rounded.Bedtime
    "bed" -> Icons.Rounded.Bed

    // 차트 · 목표
    "chart.line.uptrend.xyaxis" -> Icons.AutoMirrored.Rounded.TrendingUp
    "chart.xyaxis.line" -> Icons.AutoMirrored.Rounded.ShowChart
    "chart.bar.xaxis" -> Icons.Rounded.BarChart
    "gauge.with.dots.needle.33percent" -> Icons.Rounded.Speed
    "target" -> Icons.Rounded.TrackChanges
    "flag", "flag.fill" -> Icons.Rounded.Flag
    "flag.checkered" -> Icons.Rounded.SportsScore
    "flame.fill", "fire" -> Icons.Rounded.LocalFireDepartment
    "leaf" -> Icons.Rounded.Eco
    "figure.walk" -> Icons.AutoMirrored.Rounded.DirectionsWalk
    "briefcase" -> Icons.Rounded.Work
    "infinity" -> Icons.Rounded.AllInclusive

    // 체크 · 상태
    "checkmark" -> Icons.Rounded.Check
    "checkmark.circle" -> Icons.Rounded.CheckCircleOutline
    "checkmark.circle.fill" -> Icons.Rounded.CheckCircle
    "checkmark.square.fill" -> Icons.Rounded.CheckBox
    "checklist" -> Icons.Rounded.Checklist
    "circle" -> Icons.Rounded.RadioButtonUnchecked
    "square" -> Icons.Rounded.CheckBoxOutlineBlank
    "circle.dashed" -> Icons.Rounded.DonutLarge
    "circle.lefthalf.filled" -> Icons.Rounded.Brightness6
    "xmark" -> Icons.Rounded.Close
    "xmark.circle.fill" -> Icons.Rounded.Cancel
    "exclamationmark.circle" -> Icons.Rounded.ErrorOutline
    "exclamationmark.circle.fill" -> Icons.Rounded.Error
    "exclamationmark.triangle" -> Icons.Rounded.WarningAmber
    "exclamationmark.triangle.fill" -> Icons.Rounded.Warning
    "info.circle", "info.circle.fill" -> Icons.Rounded.Info
    "lock" -> Icons.Outlined.LockOutlined
    "lock.fill" -> Icons.Rounded.Lock
    "eye" -> Icons.Rounded.Visibility
    "person.crop.circle.badge.checkmark" -> Icons.Rounded.HowToReg

    // 동작
    "plus" -> Icons.Rounded.Add
    "trash" -> Icons.Rounded.Delete
    "ellipsis", "ellipsis.circle" -> Icons.Rounded.MoreHoriz
    "square.and.arrow.up" -> Icons.Rounded.Share
    "link" -> Icons.Rounded.Link
    "pin.fill" -> Icons.Rounded.PushPin
    "pin.slash" -> Icons.Rounded.Block
    "play.fill" -> Icons.Rounded.PlayArrow
    "pause.fill" -> Icons.Rounded.Pause
    "hand.point.up.left", "hand.tap" -> Icons.Rounded.TouchApp
    "rectangle.portrait.and.arrow.right" -> Icons.AutoMirrored.Rounded.Logout

    // AI · 꾸밈
    "sparkles", "sparkle" -> Icons.Rounded.AutoAwesome
    "wand.and.stars" -> Icons.Rounded.AutoFixHigh
    "crown", "crown.fill" -> Icons.Rounded.WorkspacePremium
    "star", "star.fill" -> Icons.Rounded.AutoAwesome

    // 탭 · 설정
    "house" -> Icons.Rounded.Home
    "gearshape" -> Icons.Rounded.Settings
    "creditcard" -> Icons.Rounded.CreditCard
    "globe" -> Icons.Rounded.Language
    "iphone" -> Icons.Rounded.Smartphone
    "square.stack.3d.up" -> Icons.Rounded.Layers
    "square.grid.2x2" -> Icons.Rounded.GridView
    "apple" -> Icons.Rounded.Smartphone

    else -> Icons.Rounded.Circle
}

/** iOS Image(systemName:) 대신 쓰는 아이콘 (contentDescription 없음 = 장식용) */
@Composable
fun SfImage(
    name: String,
    size: Dp = 16.dp,
    tint: Color = NC.ink,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
) {
    // Figma 디자인 시스템 아이콘이 있으면 그걸로 (NIcon.kt sfToNIcon), 없으면 머티리얼 아이콘
    val n = sfToNIcon(name)
    if (n != null) {
        n.Icon(size = size, tint = tint, contentDescription = contentDescription, modifier = modifier)
    } else {
        Icon(imageVector = sfIcon(name), contentDescription = contentDescription, tint = tint, modifier = modifier.size(size))
    }
}
