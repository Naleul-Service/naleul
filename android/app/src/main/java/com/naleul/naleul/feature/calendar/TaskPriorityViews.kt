package com.naleul.naleul.feature.calendar

import com.naleul.naleul.designsystem.*
import com.naleul.naleul.model.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 웹 원본: features/timetable/ui/Priority.tsx
// Task 우선순위 (A 가 가장 중요) — brain-dump 2단계 · Task 상세 · TimeBlock 이 같이 써요.

/** 작은 'A' 표시 — A 는 color(목표 색)로 채우고, 나머지는 회색 바탕 */
@Composable
fun PriorityMark(priority: TaskPriority, color: Color, size: Dp = 16.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .background(if (priority.isTop) color else NC.subtle, RoundedCornerShape(4.dp))
            .semantics { contentDescription = "중요도 ${priority.rawValue} ${priority.label}" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            priority.rawValue,
            style = TextStyle(fontSize = (size.value * 0.62f).sp, fontWeight = FontWeight.Bold, lineHeight = (size.value * 0.62f).sp),
            color = if (priority.isTop) Color.White else NC.ink3,
        )
    }
}

/** A~E 칩 한 줄 — 누르면 바로 골라져요 */
@Composable
fun PriorityChips(
    value: TaskPriority,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onChange: (TaskPriority) -> Unit,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (p in TaskPriority.entries) {
            NChip(
                p.rawValue,
                selected = value == p,
                size = ChipSize.sm,
                enabled = enabled,
                modifier = Modifier.semantics { contentDescription = "중요도 ${p.rawValue} ${p.label}" },
            ) { onChange(p) }
        }
    }
}
