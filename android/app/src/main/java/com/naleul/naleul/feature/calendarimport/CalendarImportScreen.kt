// feature/calendarimport/CalendarImportScreen.kt
//
// iOS Features/CalendarImport/CalendarImportScreen.swift
// 웹 원본: app/(main)/settings/calendar-import/page.tsx · features/external-calendar/ui/CalendarImportView.tsx
//
// 설정 > 외부 캘린더 가져오기 (쌓이는 하위 화면)
// "어디서 가져올까요?"에서 고른 곳의 일정을 골라 Task 로 가져와요 (웹과 같은 흐름).
//  - 휴대폰 캘린더: 휴대폰에 동기화된 구글 · 삼성 · Outlook 계정 일정. READ_CALENDAR 권한 (거절했으면 설정 앱 열기)
//  - 구글 캘린더: 구글 계정을 연결해서 바로 읽어요 (휴대폰에 계정을 추가하지 않아도 돼요). Google Play 서비스 권한 창
//  - 캘린더 · 기간 · 목표 고르기, 일정 고르기, 결과 카드의 문구는 웹과 같아요.

package com.naleul.naleul.feature.calendarimport

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

import android.Manifest
import android.app.Activity
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch

/**
 * 외부 캘린더 가져오기
 * @param previewModel 미리보기 전용 (null 이면 진짜 휴대폰 캘린더를 읽어요)
 */
@Composable
fun CalendarImportScreen(previewModel: CalendarImportViewModel? = null) {
    val vm = rememberScreenModel { previewModel ?: CalendarImportViewModel() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        vm.quiet = previewModel != null
        vm.onAppear()
        vm.trackPageview()
    }

    // 휴대폰 설정에서 권한을 켜고 돌아오면 다시 확인해요 (iOS scenePhase == .active)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refreshAccess()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 캘린더 읽기 권한 창 (iOS store.requestFullAccessToEvents())
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.onAccessResult(granted)
    }

    // 구글 창에서 체크를 하나 빼면 일부 범위만 와요 → 둘 다 있어야 가져올 수 있어요
    fun onGoogleResult(r: AuthorizationResult) {
        val token = r.accessToken
        when {
            token == null -> vm.onGoogleError("failed", "구글 연결에 실패했어요.")
            !r.grantedScopes.containsAll(GoogleCalendarReader.SCOPES) ->
                vm.onGoogleError("access_denied", "구글 창에서 캘린더 권한을 모두 체크해 주세요.")
            else -> vm.onGoogleToken(token)
        }
    }

    // 구글 캘린더 권한 창 (Google Play 서비스) → access token
    val googleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            try {
                onGoogleResult(Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(res.data))
            } catch (e: ApiException) {
                vm.onGoogleError("access_denied", "캘린더 읽기 권한을 허용해 주세요.")
            }
        } else {
            vm.onGoogleError("popup_closed", "구글 연결 창을 닫았어요.")
        }
    }

    fun connectGoogle() {
        vm.trackGoogleConnect()
        vm.onGoogleConnecting()
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(GoogleCalendarReader.SCOPES.map { Scope(it) })
            .build()
        Identity.getAuthorizationClient(context).authorize(request)
            .addOnSuccessListener { r ->
                val pending = r.pendingIntent
                if (r.hasResolution() && pending != null) {
                    // 계정 고르기 · 권한 동의 화면
                    googleLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                } else {
                    // 이미 허용한 계정 → 바로 토큰
                    onGoogleResult(r)
                }
            }
            .addOnFailureListener {
                vm.onGoogleError("failed", "구글 연결에 실패했어요. Google Play 서비스를 확인해 주세요.")
            }
    }

    PushedScreen(title = "외부 캘린더 가져오기") {
        ScreenColumn {
            CalendarImportSourceCard(selected = vm.source) { vm.selectSource(it) }

            if (vm.source == CalendarImportViewModel.Source.google) {
                if (vm.access == CalendarImportViewModel.Access.granted) {
                    CalendarImportOptionsCard(vm = vm)
                } else {
                    CalendarImportGoogleConnectCard(connecting = vm.googleConnecting) {
                        if (previewModel != null) vm.onGoogleToken("preview") else connectGoogle()
                    }
                }
            } else if (vm.source == CalendarImportViewModel.Source.device) when (vm.access) {
                CalendarImportViewModel.Access.unknown -> CalendarImportAccessRequestCard {
                    vm.trackAccessRequest()
                    if (previewModel != null) {
                        vm.onAccessResult(true)
                    } else {
                        permissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                    }
                }
                CalendarImportViewModel.Access.denied -> CalendarImportAccessDeniedCard {
                    // 앱 정보 화면 → 권한 → 캘린더
                    vm.trackOpenSettings()
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(intent) }
                }
                CalendarImportViewModel.Access.granted -> CalendarImportOptionsCard(vm = vm)
            }

            val error = vm.errorText
            if (error != null) {
                Text(error, style = pt(13, PTWeight.medium), color = NC.danger)
            }

            val events = vm.events
            if (events != null) {
                CalendarImportEventPicker(vm = vm, events = events, onImport = { scope.launch { vm.importPicked() } })
            }

            val result = vm.result
            if (result != null) {
                CalendarImportResultCard(r = result)
            }
        }
    }
}

// MARK: - 안내

/** 어디서 가져올까요? (웹 SOURCES 에서 노션 캘린더 · 아이폰 안내를 뺀 앱 버전) */
@Composable
private fun CalendarImportSourceCard(
    selected: CalendarImportViewModel.Source?,
    onSelect: (CalendarImportViewModel.Source) -> Unit,
) {
    Column(Modifier.fillMaxWidth().card().padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("어디서 가져올까요?", style = pt(15, PTWeight.bold), color = NC.ink)
        Text(
            "고른 일정은 나를의 Task가 돼요. 한 번 가져온 뒤에는 원래 캘린더에서 바꿔도 다시 반영되지 않아요.",
            style = pt(13),
            color = NC.ink3,
        )
        Row(Modifier.padding(top = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CalendarImportSourceOption(
                title = "구글 캘린더",
                hint = "구글 계정을 연결해서 가져와요",
                mark = "G",
                on = selected == CalendarImportViewModel.Source.google,
                modifier = Modifier.weight(1f),
            ) { onSelect(CalendarImportViewModel.Source.google) }
            CalendarImportSourceOption(
                title = "휴대폰 캘린더",
                hint = "휴대폰에 추가한 구글 · 삼성 · Outlook 계정",
                mark = null,
                on = selected == CalendarImportViewModel.Source.device,
                modifier = Modifier.weight(1f),
            ) { onSelect(CalendarImportViewModel.Source.device) }
        }
    }
}

@Composable
private fun CalendarImportSourceOption(
    title: String,
    hint: String,
    mark: String?,
    on: Boolean,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
) {
    val shape = RoundedCornerShape(Radius.xl)
    Column(
        modifier
            .clip(shape)
            .background(if (on) NC.brandSoft else NC.surface, shape)
            .border(1.dp, if (on) NC.brand else NC.borderSubtle, shape)
            .clickable(role = Role.RadioButton) { onTap() }
            .semantics { selected = on }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.size(32.dp).background(NC.subtle, RoundedCornerShape(Radius.lg)),
            contentAlignment = Alignment.Center,
        ) {
            if (mark != null) Text(mark, style = pt(14, PTWeight.bold), color = NC.ink)
            else SfImage("iphone", size = 14.dp, tint = NC.ink)
        }
        Text(title, style = pt(14, PTWeight.bold), color = NC.ink)
        Text(hint, style = pt(12), color = NC.ink3)
    }
}

/** 웹 GoogleImport 의 "구글 계정 연결하기" 카드 */
@Composable
private fun CalendarImportGoogleConnectCard(connecting: Boolean, onConnect: () -> Unit) {
    Column(Modifier.fillMaxWidth().card().padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("구글 계정을 연결해 주세요", style = pt(15, PTWeight.bold), color = NC.ink)
        Text(
            "캘린더 보기 권한만 받아요. 나를은 구글 캘린더의 일정을 바꾸거나 지우지 않고, 연결은 1시간 뒤 자동으로 끊겨요.",
            style = pt(13),
            color = NC.ink3,
        )
        NButton(
            "구글 계정 연결하기",
            fullWidth = true,
            loading = connecting,
            modifier = Modifier.padding(top = 12.dp),
        ) { onConnect() }
    }
}

@Composable
private fun CalendarImportAccessRequestCard(onRequest: () -> Unit) {
    Column(Modifier.fillMaxWidth().card().padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("캘린더 접근 허용이 필요해요", style = pt(15, PTWeight.bold), color = NC.ink)
        Text(
            "캘린더 읽기 권한만 받아요. 나를은 휴대폰 캘린더의 일정을 바꾸거나 지우지 않아요.",
            style = pt(13),
            color = NC.ink3,
        )
        NButton("캘린더 접근 허용하기", fullWidth = true, modifier = Modifier.padding(top = 12.dp)) { onRequest() }
    }
}

@Composable
private fun CalendarImportAccessDeniedCard(onOpenSettings: () -> Unit) {
    Column(Modifier.fillMaxWidth().card().padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("캘린더 접근이 꺼져 있어요", style = pt(15, PTWeight.bold), color = NC.ink)
        Text(
            "설정 앱 → 애플리케이션 → 나를 → 권한에서 \"캘린더\"를 허용해 주세요.",
            style = pt(13),
            color = NC.ink3,
        )
        NButton("설정 앱 열기", variant = NButtonVariant.secondary, fullWidth = true, modifier = Modifier.padding(top = 12.dp)) {
            onOpenSettings()
        }
    }
}

// MARK: - 캘린더 · 기간 · 목표 (웹 GoogleImport 의 연결 후 카드)

@Composable
private fun CalendarImportOptionsCard(vm: CalendarImportViewModel) {
    Column(Modifier.fillMaxWidth().card().padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("가져올 캘린더", style = pt(15, PTWeight.bold), color = NC.ink)
            if (vm.calendars.isEmpty()) {
                Text(
                    if (vm.source == CalendarImportViewModel.Source.google) "이 구글 계정에 캘린더가 없어요." else "이 휴대폰에 캘린더가 없어요.",
                    style = pt(13),
                    color = NC.ink3,
                )
            } else {
                FlowLayout(spacing = 8.dp) {
                    for (c in vm.calendars) {
                        CalendarImportCalendarChip(c = c, on = vm.selectedCalendarIds.contains(c.id)) { vm.toggleCalendar(c.id) }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CalendarImportDateRow(
                title = "시작일",
                value = vm.from,
                minDate = vm.today,
                maxDate = maxOf(vm.to, vm.today),
                onSet = { vm.setFrom(it) },
            )
            CalendarImportDateRow(
                title = "종료일",
                value = vm.to,
                minDate = vm.from,
                maxDate = vm.maxDate,
                onSet = { vm.setTo(it) },
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("연결할 목표 (선택)", style = pt(13, PTWeight.semibold), color = NC.ink3)
                // iOS BrainDumpMenuField(Menu) → 같은 모양의 고르기 칸 (NMenuPicker)
                val options: List<Pair<Int?, String>> = listOf<Pair<Int?, String>>(null to "목표 없이") +
                    vm.goals.map { g -> g.goalCategoryId to ((g.emoji?.let { "$it " } ?: "") + g.goalCategoryName) }
                NMenuPicker(
                    selection = vm.selectedGoalId,
                    options = options,
                    onSelect = { vm.selectGoal(it) },
                    placeholder = "목표 없이",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                "지난 일정은 “못 한 일”로 잡혀 실행률이 틀어져서 오늘부터 가져와요.",
                style = pt(12),
                color = NC.ink3,
            )
        }

        NButton(
            "일정 불러오기",
            variant = NButtonVariant.brand,
            icon = "calendar",
            loading = vm.isLoading,
            enabled = vm.selectedCalendarIds.isNotEmpty(),
        ) { vm.loadEvents() }
    }
}

@Composable
private fun CalendarImportCalendarChip(c: CalendarImportDeviceCalendar, on: Boolean, onTap: () -> Unit) {
    Row(
        Modifier
            .height(36.dp)
            .clip(CircleShape)
            .background(if (on) NC.subtle else NC.surface, CircleShape)
            .border(if (on) 1.5.dp else 1.dp, if (on) NC.borderFocus else NC.borderSubtle, CircleShape)
            .clickable(role = Role.Checkbox) { onTap() }
            .semantics { selected = on }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(10.dp).background(c.color, CircleShape))
        Text(c.title, style = pt(14, PTWeight.medium), color = if (on) NC.ink else NC.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
        // 휴대폰은 계정마다 이름이 같은 캘린더가 있을 수 있어서 계정 이름을 작게 붙여요
        if (c.accountName.isNotEmpty()) {
            Text(c.accountName, style = pt(11), color = NC.ink4, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (on) SfImage("checkmark", size = 12.dp, tint = NC.ink)
    }
}

@Composable
private fun CalendarImportDateRow(
    title: String,
    value: String,
    minDate: String,
    maxDate: String,
    onSet: (String) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = pt(13, PTWeight.semibold), color = NC.ink3)
        Spacer(Modifier.weight(1f))
        NDateField(
            ymd = value,
            onYmdChange = onSet,
            minDate = minDate,
            maxDate = maxDate,
            modifier = Modifier.widthIn(min = 170.dp),
        )
    }
}

// MARK: - 일정 고르기 (웹 EventPicker)

@Composable
private fun CalendarImportEventPicker(vm: CalendarImportViewModel, events: List<CalendarImportEvent>, onImport: () -> Unit) {
    if (events.isEmpty()) {
        Box(Modifier.fillMaxWidth().card().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("이 기간에 가져올 일정이 없어요.", style = pt(14), color = NC.ink3, textAlign = TextAlign.Center)
        }
        return
    }
    Column(Modifier.fillMaxWidth().card()) {
        // 머리줄
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val ink = NC.ink
            val ink3 = NC.ink3
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ink)) { append("일정 ${events.size}개") }
                    withStyle(SpanStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ink3)) {
                        append("  ${vm.pickedEventIds.size}개 선택")
                    }
                },
                style = pt(15),
                modifier = Modifier.weight(1f),
            )
            Text(
                if (vm.allPicked) "모두 해제" else "모두 선택",
                style = pt(13, PTWeight.semibold),
                color = NC.ink2,
                modifier = Modifier.clickable(role = Role.Button) { vm.toggleAll() },
            )
        }
        HorizontalDivider(color = NC.borderSoft)
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            for (day in vm.eventsByDay) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text(
                        if (day.date.isEmpty()) "날짜 없음" else KST.formatMonthDay(day.date),
                        style = pt(12, PTWeight.bold),
                        color = NC.ink3,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                    for (e in day.events) {
                        CalendarImportEventRow(e = e, on = vm.pickedEventIds.contains(e.externalId)) { vm.toggleEvent(e.externalId) }
                    }
                }
            }
        }
        HorizontalDivider(color = NC.borderSoft)
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "하루 종일 일정은 처음엔 빼 뒀어요. 시간 일정은 그 시각에, 하루 종일 일정은 그날의 “시간 미정”에 들어가요.",
                style = pt(12),
                color = NC.ink3,
            )
            NButton(
                "${vm.pickedEventIds.size}개 Task로 가져오기",
                loading = vm.isImporting,
                fullWidth = true,
                enabled = vm.pickedEventIds.isNotEmpty(),
            ) { onImport() }
        }
    }
}

@Composable
private fun CalendarImportEventRow(e: CalendarImportEvent, on: Boolean, onTap: () -> Unit) {
    val time = if (e.allDay) "하루 종일" else "${KST.hm(e.start)}–${KST.hm(e.end)}"
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Checkbox) { onTap() }
            .semantics { selected = on }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SfImage(if (on) "checkmark.square.fill" else "square", size = 18.dp, tint = if (on) NC.textBrand else NC.ink4)
        Text(time, style = ptMono(13), color = NC.ink3, modifier = Modifier.width(92.dp), maxLines = 1)
        Text(
            e.title,
            style = pt(14),
            color = if (on) NC.ink else NC.ink3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        val name = e.calendarName
        if (!name.isNullOrEmpty()) {
            Text(name, style = pt(12), color = NC.ink4, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// MARK: - 결과 (웹 ResultCard)

@Composable
private fun CalendarImportResultCard(r: CalendarImportResult) {
    val skipped = listOfNotNull(
        if (r.skippedDuplicate > 0) "이미 있는 일정 ${r.skippedDuplicate}개" else null,
        if (r.skippedPast > 0) "지난 일정 ${r.skippedPast}개" else null,
        if (r.skippedInvalid > 0) "시간 정보가 맞지 않는 일정 ${r.skippedInvalid}개" else null,
    )
    Column(Modifier.fillMaxWidth().card().padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            if (r.created > 0) "${r.created}개를 Task로 만들었어요" else "새로 만든 Task가 없어요",
            style = NType.h4,
            color = NC.ink,
        )
        if (skipped.isNotEmpty()) {
            Text("건너뜀: " + skipped.joinToString(" · "), style = pt(13), color = NC.ink3)
        }
        NButton("캘린더에서 보기", variant = NButtonVariant.secondary, icon = "arrow.right", modifier = Modifier.padding(top = 12.dp)) {
            ST.track("calendar_import.result_calendar_click", mapOf("created_count" to r.created))
            AppRouter.openCalendar()
        }
    }
}

// MARK: - 미리보기

@Preview(showBackground = true, heightDp = 800, name = "허용 전")
@Composable
private fun CalendarImportScreenBeforePreview() {
    NaleulTheme(dark = false) {
        CalendarImportScreen(
            previewModel = CalendarImportViewModel(
                reader = CalendarImportMockReader(access = null),
                importer = { _, _, _ -> CalendarImportResult.zero },
                goalLoader = { emptyList() },
            ),
        )
    }
}

@Preview(showBackground = true, heightDp = 1300, name = "일정 고르기")
@Composable
private fun CalendarImportScreenPickPreview() {
    val vm = remember {
        CalendarImportViewModel(
            reader = CalendarImportMockReader(access = true),
            importer = { _, _, events ->
                CalendarImportResult(created = events.size, skippedDuplicate = 1, skippedPast = 0, skippedInvalid = 0)
            },
            goalLoader = { emptyList() },
        ).also { it.quiet = true }
    }
    LaunchedEffect(Unit) {
        vm.selectSource(CalendarImportViewModel.Source.device)
        vm.onAppear()
        vm.loadEvents()
    }
    NaleulTheme(dark = false) {
        CalendarImportScreen(previewModel = vm)
    }
}

@Preview(showBackground = true, name = "결과")
@Composable
private fun CalendarImportResultCardPreview() {
    NaleulTheme(dark = false) {
        Column(Modifier.canvasBackground().padding(16.dp)) {
            CalendarImportResultCard(r = CalendarImportResult(created = 5, skippedDuplicate = 2, skippedPast = 1, skippedInvalid = 0))
        }
    }
}
