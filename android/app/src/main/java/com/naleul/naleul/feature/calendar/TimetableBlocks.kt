// feature/calendar/TimetableBlocks.kt
//
// iOS Features/Calendar/TimetableBlocks.swift 를 옮긴 파일.
// TimeTable 격자 위에 그리는 블록들 (Task · 고정 시간 · 수면 · 실제로 한 일 · 드래그 미리보기).
// 웹 원본: features/timetable/ui/Blocks.tsx (+ TimeGrid.tsx 의 DragGhost · GroupGhost)
//
// 블록 스타일 (명세 8)
//  - 루틴: 연한 목표 색 + 왼쪽 진한 선
//  - 일반 Task: 진한 목표 색 + 흰 글씨
//  - 완료: ✓ 초록, 흐리게 / 놓침: ✕ 빨강 / 직접 정한 시간: 📌
//  - 22시 이월 제안 · AI 제안(아직 저장 전): 점선 + 연한 바탕
// 위치(top/height)는 격자(TimetableGridView)가 정해서 Modifier.offset + size 로 놓아요. 여기서는 모양만 그려요.
// 블록 컴포저블은 받은 modifier 크기를 꽉 채워 그려요 (iOS .frame(maxWidth: .infinity, maxHeight: .infinity)).

package com.naleul.naleul.feature.calendar

import com.naleul.naleul.app.*
import com.naleul.naleul.core.analytics.*
import com.naleul.naleul.core.network.*
import com.naleul.naleul.core.state.*
import com.naleul.naleul.core.util.*
import com.naleul.naleul.designsystem.*
import com.naleul.naleul.model.*
import com.naleul.naleul.feature.auth.*
import com.naleul.naleul.feature.calendar.*
import com.naleul.naleul.feature.calendarimport.*
import com.naleul.naleul.feature.goal.*
import com.naleul.naleul.feature.goalcreation.*
import com.naleul.naleul.feature.habit.*
import com.naleul.naleul.feature.home.*
import com.naleul.naleul.feature.notifications.*
import com.naleul.naleul.feature.pattern.*
import com.naleul.naleul.feature.record.*
import com.naleul.naleul.feature.settings.*
import com.naleul.naleul.feature.taskadd.*
import com.naleul.naleul.feature.work.*

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// MARK: - 공통 모양

object TimetableShapes {
    /** 화면 범위 밖으로 이어지는 쪽은 모서리를 둥글리지 않아요 */
    /** squareLeading: 왼쪽은 각지게 — 왼쪽 목표 색 막대가 위아래 끝까지 곧게 (Task · 실제로 한 일 블록, 2026-10 웹과 같게) */
    fun block(radius: Dp, clippedTop: Boolean = false, clippedBottom: Boolean = false, squareLeading: Boolean = false): Shape =
        RoundedCornerShape(
            topStart = if (clippedTop || squareLeading) 0.dp else radius,
            topEnd = if (clippedTop) 0.dp else radius,
            bottomEnd = if (clippedBottom) 0.dp else radius,
            bottomStart = if (clippedBottom || squareLeading) 0.dp else radius,
        )

    /** 얇은 점선 (선 1 · 간격 3/2) */
    val dashWidth: Dp = 1.dp
    val dashOn: Dp = 3.dp
    val dashOff: Dp = 2.dp

    /** 두꺼운 점선 (선 2 · 간격 4/3) */
    val thickDashWidth: Dp = 2.dp
    val thickDashOn: Dp = 4.dp
    val thickDashOff: Dp = 3.dp
}

/** 모양을 따라 점선 테두리 (iOS shape.stroke(style: StrokeStyle(dash:))) */
fun Modifier.timetableDashedBorder(width: Dp, color: Color, shape: Shape, on: Dp, off: Dp): Modifier =
    this.drawWithContent {
        drawContent()
        val outline = shape.createOutline(size, layoutDirection, this)
        drawOutline(
            outline = outline,
            color = color,
            style = Stroke(width = width.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(on.toPx(), off.toPx()))),
        )
    }

/** 빗금 배경 (고정 시간 · 수면) — 웹 HATCH (repeating-linear-gradient 135deg) */
@Composable
fun TimetableHatchFill(
    background: Color = NC.hatch,
    line: Color = NC.hatchLine,
    spacing: Float = 6f,
    modifier: Modifier = Modifier,
) {
    // 색은 그리기 블록 밖에서 꺼내 둬요 (파라미터로 받은 값이라 그대로 써도 돼요)
    val bg = background
    val ln = line
    Canvas(modifier.fillMaxSize()) {
        drawRect(bg)
        val step = spacing.dp.toPx()
        val h = size.height
        var x = -h
        while (x < size.width) {
            drawLine(ln, start = Offset(x, h), end = Offset(x + h, 0f), strokeWidth = 1.dp.toPx())
            x += step
        }
    }
}

// MARK: - Task 색

/**
 * Task 블록 색 계산 — Component_ver.2 Event: 목표 색 tint(goal/X/bg) + 왼쪽 4px accent 막대 + goal/X/fg 글자.
 * 상태: 완료(accent 원 ✓) · 놓침(bg 55% + 1px accent 테두리) · 제안(bg 55% + 1.5px accent 테두리 + ✦)
 */
class TimetableTaskStyle(t: TimeBlockTask) {
    val done: Boolean = t.taskStatus == TaskStatus.COMPLETED
    val routine: Boolean = t.sourceType == TaskSourceType.ROUTINE
    /** 22시 이월 제안 · AI 제안 (확정 전) */
    val proposed: Boolean = (t.carryPending == true || t.proposed == true) && !done
    val missed: Boolean = t.missed && !done
    /** 중요도 A — 제목 앞 'A' 배지 + 1dp 목표 색 테두리 (웹 Blocks.tsx 와 같게) */
    val top: Boolean = (t.taskPriority ?: TaskPriority.fallback).isTop
    /** 예전 "진한 목표 색 바탕 + 흰 글씨" — 새 디자인에선 쓰지 않아요 (늘 tint) */
    val solid: Boolean = false
    private val colorCode: String? = t.goalColorCode
    /** 목표 accent (막대 · 점 · 테두리) */
    val goal: Color = goalColor(colorCode)

    /** bg / fg / accent (지금 라이트/다크 기준) */
    val tone: GoalTone @Composable get() = goalTone(colorCode)
    val titleColor: Color @Composable get() = tone.fg
    val subColor: Color @Composable get() = NC.textSecondary

    companion object {}
}

/**
 * Task 바탕 (Event: bg/surface 위 goal tint · 놓침/제안은 55% · 왼쪽 accent 막대)
 *  - block: 캘린더 TimeBlock (2026-10) — 완료 = 목표 색 바탕, 아직 안 한 일 = 바탕 없이 (테두리는 블록이 그려요)
 *  - barWidth: 왼쪽 막대 굵기 (주간처럼 좁은 칸은 2.5dp)
 */
@Composable
fun TimetableTaskBackground(
    style: TimetableTaskStyle,
    modifier: Modifier = Modifier,
    block: Boolean = false,
    barWidth: Dp = 4.dp,
) {
    val tone = style.tone
    val faint = style.proposed || style.missed
    val tinted = !block || style.done || style.proposed
    // 아래 빗금(고정 시간)이 비치지 않도록 surface 위에 tint 를 겹쳐요
    Box(
        modifier
            .background(NC.bgSurface)
            .then(if (tinted) Modifier.background(if (faint) tone.bg.copy(alpha = 0.55f) else tone.bg) else Modifier)
    ) {
        Box(Modifier.fillMaxHeight().width(barWidth).background(tone.accent))
    }
}

// MARK: - Task 블록

@Composable
fun TimetableTaskBlockView(
    placed: TimetableLayout.Placed<TimeBlockTask>,
    /** 블록 높이 (dp) */
    heightPx: Float,
    /** 주간처럼 칸이 좁을 때 글자를 작게 */
    compact: Boolean = false,
    /** 일간 "계획" 칸: 완료했어도 계획한 시각으로 그렸으니 "실제" 표시를 하지 않아요 */
    planned: Boolean = false,
    selected: Boolean = false,
    /** 드래그 중인 원래 자리 */
    dimmed: Boolean = false,
    /** 확인을 기다리는 블록에 눈이 가도록 흐리게 */
    faded: Boolean = false,
    /** 확인을 기다리는 블록 (테두리 강조) */
    focused: Boolean = false,
    /** 방금 만든 Task (NEW 표시) */
    isNew: Boolean = false,
    /** 묶음 드래그로 같이 옮길 Task (점선 테두리) */
    grouped: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val t = placed.item
    val style = TimetableTaskStyle(t)
    val short = heightPx < 26f
    val shape = TimetableShapes.block(if (short) 5.dp else Radius.event, placed.clippedTop, placed.clippedBottom, squareLeading = true)
    val actual = !planned && (TimetableLayout.shownTime(t)?.actual ?: false)
    val timeText = "${if (actual) "실제 " else ""}${KST.formatMinutes(placed.start)} – ${KST.formatMinutes(placed.end)}"
    val opacity = when {
        dimmed -> 0.3f
        faded -> 0.5f
        else -> 1f
    }
    val tone = style.tone
    val accent = tone.accent
    val ink = NC.textPrimary
    val shadowColor = NC.shadowPrimary

    Box(
        modifier
            .fillMaxSize()
            .shadow(if (focused) 4.dp else 0.dp, shape, ambientColor = shadowColor, spotColor = shadowColor)
            .alpha(opacity)
            .clip(shape),
    ) {
        TimetableTaskBackground(style, Modifier.matchParentSize(), block = true, barWidth = if (compact) 2.5.dp else 4.dp)
        Column(
            Modifier
                .fillMaxSize()
                .padding(
                    // 주간(좁은 칸): 막대 4 + 여백 4 · 위아래 3
                    start = if (compact) 8.dp else 12.dp,
                    end = if (compact) 4.dp else 8.dp,
                    top = if (short) 0.dp else (if (compact) 3.dp else 6.dp),
                    bottom = if (short) 0.dp else (if (compact) 3.dp else 6.dp),
                ),
            verticalArrangement = if (short) Arrangement.spacedBy(2.dp, Alignment.CenterVertically) else Arrangement.spacedBy(2.dp),
        ) {
            TimetableTaskTitleLine(task = t, style = style, compact = compact, isNew = isNew)
            // 시간 줄은 40 이상일 때만 (짧은 블록은 제목이 먼저)
            if (heightPx >= 40f) {
                Text(
                    timeText,
                    style = if (compact) ptMono(9) else NType.captionSM.copy(fontFeatureSettings = "tnum"),
                    color = style.subColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // 2026-10: 완료 = 테두리 + 바탕 / 아직 안 한 일 = 테두리만 (제안 · 놓침은 아래에서 따로)
        if (!style.proposed && !style.missed) {
            Box(Modifier.matchParentSize().border(1.dp, accent, shape))
        }
        when {
            selected -> Box(Modifier.matchParentSize().border(2.dp, ink, shape))
            focused -> Box(Modifier.matchParentSize().border(1.5.dp, accent, shape))
            grouped -> Box(
                Modifier.matchParentSize().timetableDashedBorder(
                    1.5.dp, NC.actionPrimary, shape, TimetableShapes.thickDashOn, TimetableShapes.thickDashOff,  // 스펙 10.5: 1.5 primary 점선
                )
            )
            style.proposed -> Box(Modifier.matchParentSize().border(1.5.dp, accent, shape))
            style.missed -> Box(Modifier.matchParentSize().border(1.dp, accent, shape))
        }
    }
}

/** 블록 첫 줄: ✓ / ✕ · 이름 · NEW · 제안 표시 · 📌 */
@Composable
private fun TimetableTaskTitleLine(task: TimeBlockTask, style: TimetableTaskStyle, compact: Boolean, isNew: Boolean) {
    val t = task
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        val tone = style.tone
        if (style.done) TimetableStatusDot(systemName = "checkmark", color = NC.success, compact = compact, foreground = NC.onInk)
        if (t.missed) TimetableStatusDot(systemName = "xmark", color = NC.actionDanger, compact = compact)
        if (style.proposed && t.proposed == true) SfImage("sparkles", size = if (compact) 9.dp else 14.dp, tint = tone.accent)
        if (style.top) PriorityMark(priority = TaskPriority.A, color = tone.accent, size = if (compact) 12.dp else 14.dp)
        Text(
            TimetableRules.title(t.emoji, t.taskName),
            // 주간: Caption/SM 11 Medium · 2줄까지 (끝 말줄임)
            style = if (compact) NType.captionSM.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium) else NType.labelSM,
            color = style.titleColor,
            maxLines = if (compact) 2 else 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (style.routine) SfImage("repeat", size = if (compact) 9.dp else 11.dp, tint = style.titleColor.copy(alpha = 0.8f), contentDescription = "루틴")
        if (isNew) TimetableTinyTag(text = "NEW", background = NC.actionDanger, foreground = NC.actionDangerFg, compact = compact)
        if (style.proposed && !compact) {
            TimetableTinyTag(
                text = if (t.proposed == true) "AI 제안" else "옮길까요?",
                background = NC.bgInverse, foreground = NC.textInverse, compact = compact,
            )
        }
    }
}

@Composable
private fun TimetableStatusDot(systemName: String, color: Color, compact: Boolean, foreground: Color = Color.White) {
    val size = if (compact) 10f else 14f
    Box(Modifier.size(size.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
        SfImage(systemName, size = (size * 0.7f).dp, tint = foreground)
    }
}

/** NEW · AI 제안 같은 아주 작은 꼬리표 */
@Composable
fun TimetableTinyTag(text: String, background: Color, foreground: Color, compact: Boolean = false) {
    Box(
        Modifier
            .height(if (compact) 11.dp else 14.dp)
            .background(background, RoundedCornerShape(3.dp))
            .padding(horizontal = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = pt(if (compact) 7 else 9, PTWeight.bold), color = foreground, maxLines = 1, softWrap = false)
    }
}

// MARK: - 고정 시간 블록

@Composable
fun TimetableFixedBlockView(
    placed: TimetableLayout.Placed<FixedBlock>,
    heightPx: Float,
    compact: Boolean = false,
    selected: Boolean = false,
    dimmed: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val b = placed.item
    val shape = TimetableShapes.block(Radius.eventFixed, placed.clippedTop, placed.clippedBottom)
    // 기본 시간과 다르게 바꾼 날 (지난 날짜는 "앞으로" 변경 때 예전 시간으로 고정해 둔 것이라 표시하지 않아요)
    val changed = b.overridden && b.targetDate >= KST.today()
    // 스펙 9.5 고정 시간: 회색 빗금 (bg/sunken + 45° 1px border/soft 6 간격) · 1px border/subtle · 아이콘 text/tertiary · 글자 text/secondary
    val strokeColor = if (selected) NC.textTertiary else NC.borderSubtle
    Box(
        modifier
            .fillMaxSize()
            .alpha(if (dimmed) 0.4f else 1f)
            .clip(shape)
            .border(if (selected) 2.dp else 1.dp, strokeColor, shape),
    ) {
        TimetableHatchFill(modifier = Modifier.matchParentSize())
        Column(
            Modifier.padding(horizontal = if (compact) 3.dp else 8.dp, vertical = if (compact) 3.dp else 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!compact) SfImage("clock", size = 14.dp, tint = NC.textTertiary)
                Text(
                    TimetableRules.title(b.emoji, b.title),
                    style = if (compact) timetableFont(9.5, PTWeight.semibold) else NType.captionMD,
                    color = NC.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (changed) {
                    Text("· 변경", style = pt(if (compact) 9 else 11, PTWeight.medium), color = NC.textBrand, maxLines = 1, softWrap = false)
                }
            }
            if (heightPx >= 30f) {
                Text(
                    "${KST.formatMinutes(placed.start)} – ${KST.formatMinutes(placed.end)}",
                    style = ptMono(if (compact) 9 else 11),
                    color = NC.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// MARK: - 수면 블록

/** 수면 블록 배경 — 기상 전(아침) · 취침 후(밤) 2시간만큼 보여요 (고정 시간 빗금보다 진한 남색 톤) */
@Composable
fun TimetableSleepBlockView(selected: Boolean = false, dimmed: Boolean = false, modifier: Modifier = Modifier) {
    val stroke = NC.ink3
    Box(
        modifier
            .fillMaxSize()
            .alpha(if (dimmed) 0.4f else 1f)
            .then(if (selected) Modifier.border(2.dp, stroke) else Modifier),
    ) {
        // 스펙 9.5: 수면도 다른 고정 시간과 똑같은 회색 빗금 (보라색 없음)
        TimetableHatchFill(modifier = Modifier.matchParentSize().border(1.dp, NC.borderSubtle))
    }
}

/** 수면 블록의 끌 수 있는 가장자리 — 늘 보이는 손잡이 (아침 블록은 아래 = 기상, 밤 블록은 위 = 취침) */
@Composable
fun TimetableSleepEdgeLabel(
    edge: TimetableLayout.SleepEdge,
    /** 기상 · 취침 시각 (그날 0시 기준 분) */
    minutes: Int,
    compact: Boolean = false,
    draggable: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val wake = edge == TimetableLayout.SleepEdge.wake
    val label = "${if (wake) "기상" else "취침"} ${KST.minutesToTime(minutes)}"
    val ink = NC.textSecondary
    Box(modifier.fillMaxSize().background(NC.hatch)) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = if (compact) 3.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                "${if (wake) "☀️" else "🌙"} ${if (compact) KST.minutesToTime(minutes) else label}",
                style = pt(if (compact) 9 else 11, PTWeight.semibold),
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (draggable && !compact) {
                SfImage("line.3.horizontal", size = 10.dp, tint = NC.textTertiary)
            }
        }
        // 끌 수 있는 가장자리 표시 — 색 없는 1dp border/subtle
        Box(
            Modifier
                .align(if (wake) Alignment.BottomStart else Alignment.TopStart)
                .fillMaxWidth()
                .height(1.dp)
                .background(NC.borderSubtle)
        )
    }
}

// MARK: - 실제로 한 일 블록

/** 계획(Task) 블록과 헷갈리지 않게 흰 바탕 + 초록 왼쪽 선 + ✓ 표시, 목표를 연결했으면 그 색 선 */
@Composable
fun TimetableActivityBlockView(
    placed: TimetableLayout.Placed<ActualActivity>,
    heightPx: Float,
    compact: Boolean = false,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val a = placed.item
    val short = heightPx < 34f
    // 목표가 있으면 목표 색, 없으면 초록 — 완료한 Task 와 같은 틀 + 차이: 연한 바탕 · 점선 테두리 · ✓ 글자 (웹 ActivityBlock)
    val tone = a.goalColorCode?.let { goalTone(it) }
    val color = tone?.accent ?: NC.success
    val tint = tone?.bg ?: NC.successSoft
    val shape = TimetableShapes.block(Radius.event, placed.clippedTop, placed.clippedBottom, squareLeading = true)
    Box(
        modifier
            .fillMaxSize()
            .clip(shape)
            .background(NC.bgSurface)
            .background(tint.copy(alpha = 0.55f))
            .then(
                if (selected) Modifier.border(2.dp, NC.textPrimary, shape)
                // 흐린 실선 — 점선은 묶어 옮기기 표시와 헷갈려서 쓰지 않아요
                else Modifier.border(1.dp, color.copy(alpha = 0.45f), shape)
            ),
    ) {
        Box(Modifier.fillMaxHeight().width(if (compact) 2.5.dp else 4.dp).background(color))
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = 10.dp, end = 3.dp, top = if (short) 0.dp else 3.dp, bottom = if (short) 0.dp else 3.dp),
            verticalArrangement = if (short) Arrangement.spacedBy(1.dp, Alignment.CenterVertically) else Arrangement.spacedBy(1.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("✓", style = pt(if (compact) 8 else 10, PTWeight.bold), color = NC.statusSuccessFg)
                Text(
                    TimetableRules.title(a.emoji, a.title),
                    style = if (compact) timetableFont(9.5, PTWeight.semibold) else pt(11, PTWeight.semibold),
                    color = tone?.fg ?: NC.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!short) {
                Text(
                    "실제 ${KST.formatMinutes(placed.start)}–${KST.formatMinutes(placed.end)}",
                    style = ptMono(if (compact) 9 else 11),
                    color = NC.ink3,
                    maxLines = 1,
                )
            }
            val r = a.replacedTaskName
            if (heightPx >= 56f && r != null) {
                Text("대신: $r", style = pt(if (compact) 9 else 11), color = NC.ink4, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// MARK: - 드래그 중 놓일 자리 미리보기

@Composable
fun TimetableDragGhostView(
    title: String,
    /** Task 면 그 색, 고정 시간이면 null (빗금) */
    taskStyle: TimetableTaskStyle?,
    start: Int,
    end: Int,
    invalid: String?,
    heightPx: Float,
    showTime: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Radius.event)
    val fg = taskStyle?.titleColor ?: NC.textSecondary
    val border = taskStyle?.tone?.accent ?: NC.borderSubtle
    Box(
        modifier
            .fillMaxSize()
            .popShadow(shape)
            .clip(shape)
            .border(if (invalid != null) 2.dp else 1.5.dp, if (invalid != null) NC.actionDanger else border, shape),
    ) {
        if (taskStyle != null) {
            TimetableTaskBackground(taskStyle, Modifier.matchParentSize())
        } else {
            Box(Modifier.matchParentSize().background(NC.bgSubtle))
        }
        Column(
            Modifier.fillMaxSize().padding(start = if (taskStyle != null) 10.dp else 6.dp, end = 5.dp, top = 3.dp, bottom = 3.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(title, style = pt(11, PTWeight.semibold), color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (showTime && heightPx >= 30f) {
                Text("${KST.formatMinutes(start)} – ${KST.formatMinutes(end)}", style = ptMono(11, PTWeight.bold), color = fg, maxLines = 1)
            }
            if (invalid != null) {
                Spacer(Modifier.weight(1f))
                Text(
                    invalid,
                    style = pt(10, PTWeight.semibold),
                    color = NC.actionDangerFg,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .background(NC.actionDanger, RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
    }
}

// MARK: - 범례 (웹 CalendarView Legend)

@Composable
fun TimetableLegend(modifier: Modifier = Modifier) {
    val tone = GoalPalette.sky.tone(LocalIsDark.current)
    FlowLayout(spacing = 12.dp, lineSpacing = 6.dp, modifier = modifier) {
        TimetableLegendItem("Task") {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(2.dp)).background(NC.bgSurface).border(1.dp, tone.accent, RoundedCornerShape(2.dp))) {
                Box(Modifier.fillMaxHeight().width(2.dp).background(tone.accent))
            }
        }
        TimetableLegendItem("완료") {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(2.dp)).background(tone.bg).border(1.dp, tone.accent, RoundedCornerShape(2.dp))) {
                Box(Modifier.fillMaxHeight().width(2.dp).background(tone.accent))
            }
        }
        TimetableLegendItem("루틴") {
            SfImage("repeat", size = 10.dp, tint = NC.ink3)
        }
        TimetableLegendItem("고정 시간") {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(2.dp)).background(NC.bgSubtle).border(1.dp, NC.borderSubtle, RoundedCornerShape(2.dp)))
        }
        TimetableLegendItem("실제로 한 일") {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(2.dp))
                    .background(NC.surface)
                    .border(1.dp, NC.borderSubtle, RoundedCornerShape(2.dp))
            ) {
                Box(Modifier.fillMaxHeight().width(2.dp).background(NC.statusSuccessFg))
            }
        }
    }
}

@Composable
private fun TimetableLegendItem(label: String, swatch: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp)) { swatch() }
        Text(label, style = pt(12), color = NC.ink3)
    }
}

@Preview(showBackground = true, heightDp = 1100)
@Composable
private fun TimetableBlocksPreview() {
    NaleulTheme(dark = false) {
        val d = KST.today()
        val day = TimetableSampleData.day(d, index = 1)
        val tasks = TimetableLayout.placeTasks(day, from = 7 * 60, to = 24 * 60)
        val fixed = TimetableLayout.placeFixed(day, from = 7 * 60, to = 24 * 60)
        val acts = TimetableLayout.placeActivities(d, TimetableSampleData.activities(on = d), from = 7 * 60, to = 24 * 60)
        Column(
            Modifier.canvasBackground().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            for (p in tasks) {
                TimetableTaskBlockView(placed = p, heightPx = 52f, isNew = p.item.taskId == 2001, modifier = Modifier.size(220.dp, 52.dp))
            }
            tasks.firstOrNull()?.let { p ->
                TimetableTaskBlockView(placed = p, heightPx = 52f, compact = true, focused = true, modifier = Modifier.size(46.dp, 52.dp))
            }
            for (p in fixed) {
                TimetableFixedBlockView(placed = p, heightPx = 56f, modifier = Modifier.size(220.dp, 56.dp))
            }
            Box(Modifier.size(220.dp, 80.dp)) {
                TimetableSleepBlockView()
                TimetableSleepEdgeLabel(
                    edge = TimetableLayout.SleepEdge.wake, minutes = 420,
                    modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(22.dp),
                )
            }
            for (p in acts) {
                TimetableActivityBlockView(placed = p, heightPx = 56f, modifier = Modifier.size(220.dp, 56.dp))
            }
            TimetableDragGhostView(
                title = "📝 보고서", taskStyle = null, start = 600, end = 660,
                invalid = "이미 Task 2개가 겹쳐 있어요", heightPx = 70f, modifier = Modifier.size(220.dp, 70.dp),
            )
            TimetableLegend()
        }
    }
}
