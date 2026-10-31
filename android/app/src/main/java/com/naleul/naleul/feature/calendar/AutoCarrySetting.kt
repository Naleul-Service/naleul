package com.naleul.naleul.feature.calendar

import com.naleul.naleul.core.analytics.*
import com.naleul.naleul.core.network.*
import com.naleul.naleul.core.state.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

// 못 한 일 자동 이월 켜기/끄기 (2026-10, 웹 AutoCarryToggle · iOS AutoCarrySetting)
//  - 켜짐: 못 한 일을 다음 날 아침(기상 시간)에 오늘로 옮겨요. 새벽까지 하던 일은 그대로 둬요
//  - 꺼짐: 못 한 일은 그날에 '놓침'으로 남아요 → 직접 '다른 날로' 옮겨요
// 캘린더 + 메뉴 · 설정 "일정" 두 곳에서 같은 값을 써요 (GET/PATCH /planner-settings)

@Serializable
data class PlannerSettingDTO(
    val autoGenerateEnabled: Boolean = true,
    val nightlyPushEnabled: Boolean = true,
    val taskGapMinutes: Int? = null,
    val autoCarryEnabled: Boolean? = null,
)

object AutoCarrySetting {
    var enabled by mutableStateOf(true)
        private set
    var loaded by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
        private set

    val help: String
        get() = if (enabled) "못 한 일을 다음 날 아침(기상 시간)에 오늘로 옮겨요. 새벽까지 하던 일은 그대로 둬요."
        else "못 한 일은 그날에 '놓침'으로 남아요. 필요하면 직접 '다른 날로' 옮겨요."

    suspend fun load() {
        val s = try {
            API.get<PlannerSettingDTO>("/v1/planner-settings")
        } catch (c: CancellationException) {
            throw c
        } catch (_: Exception) {
            null
        } ?: return
        enabled = s.autoCarryEnabled ?: true
        loaded = true
    }

    /** surface: calendar · settings (calendar.auto_carry_toggle / settings.auto_carry_toggle) */
    suspend fun set(on: Boolean, surface: String) {
        AnalyticsService.track("$surface.auto_carry_toggle", "enabled" to on)
        val before = enabled
        enabled = on
        saving = true
        try {
            val s = API.patch<PlannerSettingDTO>("/v1/planner-settings", json("autoCarryEnabled" to on))
            if (s != null) enabled = s.autoCarryEnabled ?: on
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            enabled = before
            Toast.error(errorMessage(e))
        } finally {
            saving = false
        }
    }
}
