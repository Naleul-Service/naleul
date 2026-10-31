// feature/home/HomeSectionNav.kt
//
// 홈 섹션 칩 바 — "나의 패턴"의 PatternSectionNav 와 같은 모양 (iOS HomeScreen.swift HomeSectionNav)
//   오늘 일정 · 할 일 · 루틴/습관 · 다가오는 일정 · 마감 로드맵
//   칩을 누르면 그 카드로 스크롤하고, 스크롤하면 지금 보고 있는 카드 칩이 켜져요.
//
// 홈은 격자 끌기 때문에 LazyColumn 이 아니라 Column + verticalScroll 이라 stickyHeader 가 없어요.
// → 요약 카드 아래에 칩 바를 하나 두고, 그 자리를 지나 스크롤하면 화면 맨 위에 같은 칩 바를 겹쳐 그려요.

package com.naleul.naleul.feature.home

import com.naleul.naleul.designsystem.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

enum class HomeSection(val rawValue: String, val label: String, val icon: String) {
    calendar("calendar", "오늘 일정", "calendar"),
    tasks("tasks", "할 일", "checklist"),
    routines("routines", "루틴/습관", "repeat"),
    upcoming("upcoming", "다가오는 일정", "calendar.badge.clock"),
    roadmap("roadmap", "마감 로드맵", "flag.checkered"),
}

object HomeGeo {
    /** 칩 바 높이 — 카드로 스크롤할 때 칩 바 아래에 오도록 */
    const val navHeight = 52
}

/**
 * 스크롤 내용 안에서 칩 바 · 카드가 어디 있는지 (px, 스크롤 0 기준).
 * viewportTop: 스크롤 영역 맨 위의 화면 좌표 — 화면 좌표 - viewportTop + scroll = 내용 좌표
 */
@Stable
class HomeSectionAnchors {
    var viewportTop by mutableFloatStateOf(0f)
    var navTop by mutableIntStateOf(-1)
    val tops = mutableStateMapOf<HomeSection, Int>()
}

/** 카드 · 칩 바 위치를 재요 */
fun Modifier.homeAnchor(anchors: HomeSectionAnchors, scroll: () -> Int, section: HomeSection?): Modifier =
    onGloballyPositioned { c ->
        val y = (c.positionInRoot().y - anchors.viewportTop + scroll()).toInt()
        if (section == null) anchors.navTop = y else anchors.tops[section] = y
    }

/** 지금 보는 카드 — 칩 바 바로 아래(약간 여유)를 지난 마지막 카드 */
fun homeCurrentSection(anchors: HomeSectionAnchors, scroll: Int, navHeightPx: Float, extraPx: Float): HomeSection {
    val line = scroll + navHeightPx + extraPx
    var passed = HomeSection.calendar
    for (s in HomeSection.entries) {
        val top = anchors.tops[s] ?: continue
        if (top <= line) passed = s
    }
    return passed
}

@Composable
fun HomeSectionNav(current: HomeSection, onTap: (HomeSection) -> Unit, modifier: Modifier = Modifier) {
    val rowState = rememberLazyListState()
    // 켜진 칩이 보이게 가로로 따라가요
    LaunchedEffect(current) {
        rowState.animateScrollToItem(maxOf(0, current.ordinal - 1))
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(HomeGeo.navHeight.dp)
            .background(NC.canvas.copy(alpha = 0.96f)),
    ) {
        LazyRow(
            state = rowState,
            modifier = Modifier.align(Alignment.CenterStart).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(HomeSection.entries, key = { it.rawValue }) { s ->
                val on = s == current
                val fg = if (on) NC.actionPrimaryFg else NC.ink2
                Row(
                    Modifier
                        .height(34.dp)
                        .clip(CircleShape)
                        .background(if (on) NC.actionPrimary else NC.surface, CircleShape)
                        .border(1.dp, if (on) Color.Transparent else NC.borderSubtle, CircleShape)
                        .semantics { selected = on }
                        .clickable(role = Role.Tab) { onTap(s) }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    SfImage(s.icon, size = 11.dp, tint = fg)
                    Text(s.label, style = pt(13, if (on) PTWeight.bold else PTWeight.medium), color = fg, maxLines = 1)
                }
            }
        }
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(0.5.dp).background(NC.line))
    }
}
