// feature/settings/terms/TermsScreens.kt
//
// iOS Features/Settings/Terms/TermsScreens.swift
// 웹 원본: app/(main)/settings/terms/page.tsx (약관 목록)
//          app/(main)/settings/terms/[type]/page.tsx (약관 상세)

package com.naleul.naleul.feature.settings

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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 약관 목록 (설정 > 약관 동의) */
@Composable
fun TermsListScreen() {
    val preview = androidx.compose.ui.platform.LocalInspectionMode.current
    androidx.compose.runtime.LaunchedEffect(Unit) { ST.track("terms.pageview", preview = preview) }
    PushedScreen(title = "약관 동의") {
        ScreenColumn {
            Column(Modifier.fillMaxWidth().card()) {
                TermsType.entries.forEachIndexed { i, type ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(NC.line))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) {
                                ST.track("terms.item_click", mapOf("terms_type" to type.rawValue, "entry_point" to "settings"))
                                AppRouter.push(AppRoute.Settings(SettingsRoute.TermsDetail(type.rawValue)))
                            }
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(type.listTitle, style = pt(15, PTWeight.semibold), color = NC.ink, modifier = Modifier.weight(1f))
                        SfImage("chevron.right", size = 14.dp, tint = NC.ink4)
                    }
                }
            }
        }
    }
}

/** 약관 상세 — "service" | "privacy" | "marketing" */
@Composable
fun TermsDetailScreen(type: String) {
    val termsType = TermsType.fromRaw(type)
    val preview = androidx.compose.ui.platform.LocalInspectionMode.current
    // [UX] 약관 본문을 25 · 50 · 75 · 100% 까지 내렸을 때 한 번씩 (terms.detail_scroll_depth)
    val scroll = androidx.compose.foundation.rememberScrollState()
    val sent = androidx.compose.runtime.remember { mutableSetOf<Int>() }
    androidx.compose.runtime.LaunchedEffect(scroll.value, scroll.maxValue) {
        if (termsType == null || preview) return@LaunchedEffect
        // 한 화면에 다 들어오면 maxValue 가 0 → 100%
        val pct = if (scroll.maxValue <= 0) 100.0 else scroll.value * 100.0 / scroll.maxValue
        for (mark in listOf(25, 50, 75, 100)) {
            if (pct >= mark - 1 && sent.add(mark)) {
                ST.track("terms.detail_scroll_depth", mapOf("terms_type" to termsType.rawValue, "scroll_percent" to mark))
            }
        }
    }
    PushedScreen(title = termsType?.detailTitle ?: "약관") {
        // ScreenColumn 과 같은 모양 — 스크롤 위치를 읽으려고 직접 그려요
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (termsType != null) {
                Column(Modifier.fillMaxWidth().card().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // 본문은 줄바꿈이 들어간 평문이라 그대로 보여줘요 (길게 눌러 복사할 수 있어요)
                    SelectionContainer {
                        Text(
                            termsType.body,
                            style = pt(14).copy(lineHeight = 22.sp),
                            color = NC.ink2,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (termsType == TermsType.marketing) {
                        TermsInfoTable(rows = TermsText.marketingRows)
                    }
                }
            } else {
                EmptyStateCard(title = "약관을 찾을 수 없어요", icon = "doc.text")
            }
        }
    }
}

/** 마케팅 동의의 "수집 정보" 표 (항목 | 설명) */
@Composable
private fun TermsInfoTable(rows: List<TermsInfoRow>) {
    val shape = RoundedCornerShape(Radius.xl)
    Column(Modifier.fillMaxWidth().clip(shape).border(1.dp, NC.borderSubtle, shape)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(NC.subtle)) {
            TermsInfoCell("항목", header = true, modifier = Modifier.width(96.dp))
            Box(Modifier.width(1.dp).fillMaxHeight().background(NC.line))
            TermsInfoCell("설명", header = true, modifier = Modifier.weight(1f))
        }
        for (row in rows) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(NC.line))
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                TermsInfoCell(row.label, header = false, modifier = Modifier.width(96.dp))
                Box(Modifier.width(1.dp).fillMaxHeight().background(NC.line))
                TermsInfoCell(row.content, header = false, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TermsInfoCell(text: String, header: Boolean, modifier: Modifier) {
    Text(
        text,
        style = pt(14, if (header) PTWeight.semibold else PTWeight.regular),
        color = if (header) NC.ink else NC.ink2,
        modifier = modifier.padding(12.dp),
    )
}

@Preview(showBackground = true, name = "목록")
@Composable
private fun TermsListScreenPreview() {
    NaleulTheme(dark = false) { TermsListScreen() }
}

@Preview(showBackground = true, heightDp = 1000, name = "마케팅")
@Composable
private fun TermsDetailMarketingPreview() {
    NaleulTheme(dark = false) { TermsDetailScreen(type = "marketing") }
}

@Preview(showBackground = true, heightDp = 1600, name = "서비스")
@Composable
private fun TermsDetailServicePreview() {
    NaleulTheme(dark = false) { TermsDetailScreen(type = "service") }
}
