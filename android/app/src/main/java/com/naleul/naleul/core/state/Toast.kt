// core/state/Toast.kt
//
// iOS Core/State/Toast.swift — 웹 toast.success('...') / toast.error('...') 와 같아요.
// 화면 아래에 잠깐 떴다 사라지는 알림. 루트(RootScreen)에 ToastHost() 가 한 번 붙어 있어요.
//
//   Toast.success("완료했어요.")
//   Toast.error(errorMessage(e))
//   Toast.show("배치할 Task가 없었어요.")
//
// ⚠️ android.widget.Toast 와 이름이 같아요. 이 파일의 Toast 를 import 해서 쓰세요
//    (import com.naleul.naleul.core.state.Toast). android.widget.Toast 는 쓰지 않아요.

package com.naleul.naleul.core.state

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.naleul.naleul.core.network.ApiError
import com.naleul.naleul.core.analytics.AnalyticsEvent
import com.naleul.naleul.core.analytics.AnalyticsService
import com.naleul.naleul.designsystem.NC
import com.naleul.naleul.designsystem.NType
import com.naleul.naleul.designsystem.Radius
import com.naleul.naleul.designsystem.popShadow
import com.naleul.naleul.designsystem.PTWeight
import com.naleul.naleul.designsystem.pt
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class ToastTone { default, success, error }

data class ToastItem(
    val id: Int,
    val message: String,
    val tone: ToastTone,
    /** 오른쪽 글자 버튼 ("보러 가기" 등). 없으면 null */
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
)

object ToastCenter {
    val toasts = mutableStateListOf<ToastItem>()

    /**
     * 지금 열려 있는 시트 개수. 시트(ModalBottomSheet)는 앱 화면 위에 "따로 뜬 창"이라
     * 앱 화면에 그린 토스트가 시트에 가려져요. 그래서 가장 위 시트 안에서 토스트를 그려요 (iOS 는 별도 UIWindow 로 해결).
     */
    var sheetDepth by mutableIntStateOf(0)
    private var seq = 0
    private val scope = MainScope()

    fun show(message: String, tone: ToastTone = ToastTone.default, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        AnalyticsService.track(
            AnalyticsEvent.toastView,
            "toast_tone" to tone.name, "toast_message" to message, "current_path" to AnalyticsService.currentScreen,
        )
        scope.launch {
            seq += 1
            val id = seq
            toasts.add(ToastItem(id, message, tone, actionLabel, onAction))
            // 최대 3개
            while (toasts.size > 3) toasts.removeAt(0)
            delay(if (onAction != null) 4_500 else 2_800)
            dismiss(id)
        }
    }

    fun dismiss(id: Int) {
        toasts.removeAll { it.id == id }
    }
}

/** 어디서든 부르는 단축 함수 */
object Toast {
    fun show(message: String) = ToastCenter.show(message)
    fun success(message: String) = ToastCenter.show(message, ToastTone.success)

    /** 성공 토스트 + 오른쪽 글자 버튼 (예: "목표를 추가했어요" · "보러 가기") */
    fun success(message: String, actionLabel: String, onAction: () -> Unit) =
        ToastCenter.show(message, ToastTone.success, actionLabel, onAction)

    /** 둘러보기 중 저장 시도는 로그인 안내 시트가 대신 알려줘서 토스트는 겹치지 않게 생략해요 */
    fun error(message: String) {
        if (message == ApiError.GUEST_MESSAGE) return
        ToastCenter.show(message, ToastTone.error)
    }
}

/**
 * 루트 화면 맨 위에 한 번 그려요 (화면 아래쪽에 쌓여요). level 은 몇 번째 시트 안인지 (앱 화면은 0).
 * 가장 위(level == sheetDepth)에 있는 호스트만 토스트를 그려요.
 */
@Composable
fun ToastHost(level: Int = 0, bottomPadding: Dp = 90.dp, modifier: Modifier = Modifier.fillMaxSize()) {
    if (level != ToastCenter.sheetDepth) return
    Box(modifier, contentAlignment = Alignment.BottomCenter) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = bottomPadding)
                .widthIn(max = 480.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            for (t in ToastCenter.toasts.toList()) {
                AnimatedVisibility(
                    visible = true,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) {
                    // Component_ver.2 Toast: bg/inverse · radius 12 · padding 12/16 · gap 10 · 아이콘 16 + Body/SM text/inverse
                    val shape = RoundedCornerShape(Radius.toast)
                    val bg = NC.bgInverse
                    val fg = NC.textInverse
                    Row(
                        modifier = Modifier
                            .popShadow(shape)
                            .clip(shape)
                            .background(bg)
                            .clickable {
                                AnalyticsService.track(AnalyticsEvent.toastDismissClick, "toast_tone" to t.tone.name)
                                ToastCenter.dismiss(t.id)
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (t.tone == ToastTone.success) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = NC.accentMint, modifier = Modifier.size(16.dp))
                        } else if (t.tone == ToastTone.error) {
                            Icon(Icons.Filled.Error, contentDescription = null, tint = NC.actionDanger, modifier = Modifier.size(16.dp))
                        }
                        Text(t.message, style = NType.bodySM, color = fg, modifier = Modifier.weight(1f, fill = false))
                        val act = t.onAction
                        if (t.actionLabel != null && act != null) {
                            Text(
                                t.actionLabel,
                                style = NType.bodySMMedium,
                                color = NC.accentMint,
                                modifier = Modifier
                                    .clickable {
                                        ToastCenter.dismiss(t.id)
                                        act()
                                    }
                                    .padding(start = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
