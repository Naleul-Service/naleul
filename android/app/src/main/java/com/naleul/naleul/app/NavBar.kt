// app/NavBar.kt
//
// iOS 의 .navigationTitle("…") + 뒤로 가기 버튼 + .toolbar { } 를 대신하는 화면 윗줄.
//
//   // 탭 위에 쌓이는 화면 (뒤로 버튼이 저절로 생겨요)
//   PushedScreen(title = "알림 설정", actions = { NIconButton("plus") { … } }) {
//       ScreenColumn { … }      // 스크롤 내용
//   }
//
//   // 탭 첫 화면처럼 윗줄이 없으면 (iOS .toolbar(.hidden, for: .navigationBar)) PushedScreen 을 쓰지 않고
//   // Column(Modifier.fillMaxSize().canvasBackground().statusBarsPadding()) { … } 로 그려요.

package com.naleul.naleul.app

import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.zIndex
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.naleul.naleul.designsystem.NC
import com.naleul.naleul.designsystem.NType
import com.naleul.naleul.designsystem.NIconButton
import com.naleul.naleul.designsystem.NaleulTheme
import com.naleul.naleul.designsystem.PTWeight
import com.naleul.naleul.designsystem.ScreenColumn
import com.naleul.naleul.designsystem.pt

/** 화면 윗줄: 뒤로 · 가운데 제목 · 오른쪽 버튼들 */
@Composable
fun NavBar(
    title: String = "",
    showBack: Boolean = true,
    onBack: () -> Unit = { AppRouter.pop() },
    background: Color = NC.bgCanvas,
    showDivider: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    /** false 면 제목을 흐리게 숨겨요 (목표 상세: 머리 카드가 스크롤로 사라지면 나타남) */
    titleVisible: Boolean = true,
    titleStyle: androidx.compose.ui.text.TextStyle = NType.h4,
    dividerColor: Color = NC.borderSubtle,
) {
    val titleAlpha by androidx.compose.animation.core.animateFloatAsState(if (titleVisible) 1f else 0f, label = "navTitle")
    Column(Modifier.fillMaxWidth().background(background)) {
        Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp)) {
            if (showBack) {
                NIconButton("chevron.left", size = 22.dp, color = NC.textPrimary, label = "뒤로", modifier = Modifier.align(Alignment.CenterStart)) { onBack() }
            }
            Text(
                title,
                style = titleStyle,
                color = NC.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 56.dp).alpha(titleAlpha),
            )
            Row(
                Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) { actions() }
        }
        if (showDivider) HorizontalDivider(color = dividerColor)
    }
}

/**
 * 탭 위에 쌓이는 화면의 틀 (윗줄 + 내용). 시스템 뒤로 가기도 AppRouter.pop() 으로 이어 줘요.
 * onBack 을 바꾸면 (예: 저장 안 한 내용 확인) 그 동작을 해요.
 */
@Composable
fun PushedScreen(
    title: String,
    onBack: () -> Unit = { AppRouter.pop() },
    background: Color = NC.bgCanvas,
    actions: @Composable RowScope.() -> Unit = {},
    titleVisible: Boolean = true,
    titleStyle: androidx.compose.ui.text.TextStyle = NType.h4,
    showDivider: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    BackHandler { onBack() }
    Column(Modifier.fillMaxSize().background(background).statusBarsPadding()) {
        // 스펙 13.4: 윗줄은 늘 맨 위 (내용 그림자 · 카드가 스크롤로 윗줄을 덮지 않게 위에 그리고, 내용은 자기 칸 안에서만)
        Box(Modifier.zIndex(1f)) {
            NavBar(
                title = title, onBack = onBack, background = background, actions = actions,
                titleVisible = titleVisible, titleStyle = titleStyle, showDivider = showDivider, dividerColor = NC.borderSoft,
            )
        }
        Box(Modifier.fillMaxWidth().weight(1f).clipToBounds(), content = content)
    }
}

@Preview(showBackground = true)
@Composable
private fun PushedScreenPreview() {
    NaleulTheme(dark = false) {
        PushedScreen(title = "알림 설정", actions = { NIconButton("plus") {} }) {
            ScreenColumn {
                Text("내용", style = pt(15))
                Spacer(Modifier.width(1.dp))
            }
        }
    }
}
