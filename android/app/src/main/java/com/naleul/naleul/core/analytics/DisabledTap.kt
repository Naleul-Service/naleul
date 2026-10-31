// core/analytics/DisabledTap.kt
//
// iOS View.disabledTap — 흐린(enabled = false) 버튼을 눌렀을 때를 잡아요 (*.disabled_click 같은 [UX] 이벤트용).
// enabled = false 인 clickable 은 터치를 소비하지 않아서, 바깥쪽 pointerInput 이 그 탭을 받을 수 있어요.
// 막혀 있지 않을 때는 아무것도 붙이지 않아서 원래 클릭에 영향이 없어요.

package com.naleul.naleul.core.analytics

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

fun Modifier.disabledTap(disabled: Boolean, onTap: () -> Unit): Modifier =
    if (!disabled) this else this.pointerInput(Unit) { detectTapGestures { onTap() } }
