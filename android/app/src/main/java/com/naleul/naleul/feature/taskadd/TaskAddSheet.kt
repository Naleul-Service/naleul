// feature/taskadd/TaskAddSheet.kt
//
// iOS Features/TaskAdd/TaskAddSheet.swift
// 웹 원본: features/brain-dump/ui/TaskAddPanel.tsx (머리 · 닫기) + TaskAddView.tsx (variant="panel")
//         app/(main)/tasks/new/page.tsx (?mode=manual|log → initialMode)
//
// 캘린더 위에 시트로 여는 "Task 추가".
//  - 추가 방식: AI로 정리하기 / 직접 추가하기 / 한 일 기록
//  - AI로 정리하기 = 3단계: Task 입력 → 목표 연결 → 확인
//  - 저장하면 onAdded 로 캘린더에 알려요 → 캘린더가 그 날짜로 옮기고 새 블록에 NEW 를 붙여요.
//    시간을 정하지 않은 Task(pending)가 있으면 캘린더가 시트를 닫고 점선 미리보기로 "이대로 진행할까요?"를 물어요.
//
// 앱에서 바꾼 점
//  - 웹 /tasks/new 페이지는 따로 두지 않았어요. 앱에서는 모든 "Task 추가"가 이 시트예요
//    (AppRouter.openTaskAdd(mode) → 캘린더가 이 시트를 띄워요).
//  - 웹 패널은 결과를 옆 캘린더에서 보지만, 폰은 시트가 캘린더를 가리므로
//    요약 아래 "TimeBlock 확인"을 누르면 웹 StepResult(격자 미리보기)를 시트 안에서 볼 수 있게 했어요.
//
// Android 에서 바꾼 점
//  - 시트는 이 컴포저블이 직접 그려요(NSheet). 캘린더는 이렇게만 부르면 돼요:
//      AppRouter.taskAdd?.let { req ->
//          TaskAddSheet(initialMode = req.mode, onAdded = { handleAdded(it) }, onDismiss = { AppRouter.taskAdd = null })
//      }
//  - iOS 는 시트 안에 NavigationStack 을 두고 "TimeBlock 확인"을 쌓았어요. 시트는 탭 화면보다 위에 떠 있어서
//    AppRouter 로 쌓을 수 없으니, 시트 안에서 showDetail 상태로 화면을 바꿔 그려요 (시스템 뒤로가기 = 이전 화면).

package com.naleul.naleul.feature.taskadd

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

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Task 추가 시트 (캘린더가 띄워요).
 * @param initialMode 처음 고른 추가 방식
 * @param onAdded 추가가 끝났을 때 캘린더에 알려요
 * @param onDismiss 시트가 닫힐 때 (보통 AppRouter.taskAdd = null)
 */
@Composable
fun TaskAddSheet(
    initialMode: TaskAddMode,
    onAdded: (TaskAddedInfo) -> Unit,
    onDismiss: () -> Unit,
    /** true 면 방식 탭 없이 그 방식만 · 제목도 그 방식 이름 (캘린더 + 메뉴 · 스펙 11.3) */
    standalone: Boolean = false,
    prefillDate: String? = null,
    prefillStartMinutes: Int? = null,
) {
    val vm = rememberScreenModel(initialMode) { TaskAddViewModel(mode = initialMode) }
    TaskAddSheetContent(
        vm = vm, onAdded = onAdded, onDismiss = onDismiss,
        standalone = standalone, prefillDate = prefillDate, prefillStartMinutes = prefillStartMinutes,
    )
}

/** 캘린더 + 메뉴 이름 (스펙 11.2) — 단독 시트 제목으로도 써요 */
fun taskAddEntryTitle(mode: TaskAddMode): String = when (mode) {
    TaskAddMode.ai -> "AI로 자동 Task 추가"
    TaskAddMode.manual -> "계획 할 일 추가"
    TaskAddMode.log -> "실제 한 일 추가"
}

/** 미리보기 전용 — 서버를 부르지 않는 ViewModel 로 (iOS init(previewModel:)) */
@Composable
fun TaskAddSheet(
    previewModel: TaskAddViewModel,
    onDismiss: () -> Unit = {},
) {
    TaskAddSheetContent(vm = previewModel, onAdded = {}, onDismiss = onDismiss)
}

@Composable
private fun TaskAddSheetContent(
    vm: TaskAddViewModel,
    onAdded: (TaskAddedInfo) -> Unit,
    onDismiss: () -> Unit,
    standalone: Boolean = false,
    prefillDate: String? = null,
    prefillStartMinutes: Int? = null,
) {
    var showDetail by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    /** 코드가 시트를 닫으면 "next" (점선 제안 · 다른 화면으로 이동) — 그 밖엔 X / 쓸어 닫기 · 뒤로 가기 */
    val closeOutcome = remember { mutableStateOf<String?>(null) }

    // 시트가 사라질 때 (캘린더가 점선 제안으로 넘어가며 닫으면 sheetCloseHint = "next")
    DisposableEffect(Unit) {
        onDispose {
            if (vm.isPreview) return@onDispose
            TA.plain("task_add.panel_close", mapOf(
                "add_mode" to vm.mode.rawValue,
                "ai_step" to if (vm.mode == TaskAddMode.ai) TA.aiStep(vm.step) else null,
                "has_input" to vm.hasInput,
                "close_method" to TA.closeMethod(closeOutcome.value),
            ))
        }
    }
    LaunchedEffect(vm.mode) { vm.childHasInput = false }

    ReloadOn(listOf(DataKey.usage)) { vm.loadQuota() }
    ReloadOn(listOf(DataKey.goals)) { vm.loadGoals() }

    NSheet(onDismiss = onDismiss, fullHeight = true) { close ->
        // MARK: 시트 닫기
        // iOS 는 하위 화면(push)에서 dismiss 가 "뒤로 가기"가 돼서 router.taskAdd 를 직접 비웠어요.
        // Android 는 close() 로 시트를 내리면 onDismiss(보통 AppRouter.taskAdd = null)가 불려요.
        val closeSheet: () -> Unit = {
            closeOutcome.value = "next"
            close()
        }

        /** 무료 플랜 한도 안내의 "Pro 알아보기" → 설정 > 멤버십 (웹은 "곧 만나볼 수 있어요" 토스트) */
        val openMembership: () -> Unit = {
            closeSheet()
            com.naleul.naleul.feature.settings.ST.pendingMembershipEntry = "task_add"
            AppRouter.openSettings(SettingsRoute.Membership)
        }

        // "TimeBlock 확인" 화면에서 시스템 뒤로가기 → 요약으로
        BackHandler(enabled = showDetail) { showDetail = false }

        val result = vm.result
        if (showDetail && result != null) {
            // MARK: 자세한 결과 (iOS navigationDestination)
            DetailHeader(onBack = { showDetail = false })
            BrainDumpStepResult(
                result = result,
                today = vm.today,
                onAddMore = {
                    TA.track("task_add_result.add_more_click", mapOf("placed_count" to result.placements.size))
                    showDetail = false
                    vm.reset()
                },
                closeSheet = closeSheet,
                modifier = Modifier.weight(1f),
            )
        } else {
            SheetHeader(title = if (standalone) taskAddEntryTitle(vm.mode) else null, onClose = {
                AnalyticsService.sheetCloseHint = "x_button"
                close()
            })
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(NC.bgSurface)
                    .verticalScroll(rememberScrollState())
                    // 스펙 13.2: 시트 내용 좌우 24 · 위 20
                    .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 24.dp),
            ) {
                // 단독 시트(캘린더 + 메뉴)는 방식 탭 없이 그 방식만
                if (!standalone) TaskAddModeSwitch(vm = vm)

                // MARK: 방식별 내용
                when (vm.mode) {
                    TaskAddMode.log -> {
                        Box(Modifier.padding(top = 16.dp)) {
                            LogDoneView(goals = vm.ongoingGoals, onInputChange = { vm.childHasInput = it })
                        }
                    }
                    TaskAddMode.manual -> {
                        TaskAddManualView(
                            goals = vm.ongoingGoals,
                            loading = vm.goalsLoading,
                            goalsFailed = vm.goalsFailed,
                            onCreated = { t ->
                                val day = (t.plannedStartAt ?: t.date ?: t.scheduledDate ?: "").take(10)
                                onAdded(TaskAddedInfo(taskIds = listOf(t.taskId), date = day.ifEmpty { null }, pendingTaskIds = null))
                            },
                            closeSheet = closeSheet,
                            onInputChange = { vm.childHasInput = it },
                            modifier = Modifier.padding(top = if (standalone) 0.dp else 16.dp),
                            prefillDate = prefillDate,
                            prefillStartMinutes = prefillStartMinutes,
                        )
                    }
                    TaskAddMode.ai -> {
                        TaskAddStepper(step = vm.step, modifier = Modifier.padding(vertical = 16.dp))
                        // MARK: AI 단계
                        if (vm.step == 0) {
                            BrainDumpStepInput(
                                lines = vm.lines,
                                onLinesChange = { vm.lines = it },
                                quota = vm.quota,
                                quotaLoading = vm.quotaLoading,
                                today = vm.today,
                                loading = vm.parsing,
                                onNext = { scope.launch { vm.goParse() } },
                                onPro = openMembership,
                            )
                        } else if (vm.step == 1) {
                            BrainDumpStepGoals(
                                items = vm.items,
                                tempGoals = vm.tempGoals,
                                goals = vm.ongoingGoals,
                                aiFailed = vm.aiFailed,
                                aiLimited = vm.aiLimited,
                                conflicts = vm.conflicts,
                                today = vm.today,
                                loading = vm.confirming,
                                onChange = { items, temps -> vm.applyChange(items = items, tempGoals = temps) },
                                onBack = {
                                    TA.track("task_add_goals.back_click", mapOf("item_count" to vm.items.size, "edited_count" to vm.editedCount))
                                    vm.step = 0
                                },
                                onConfirm = {
                                    scope.launch {
                                        val info = vm.goConfirm()
                                        if (info != null) onAdded(info)
                                    }
                                },
                            )
                        } else if (result != null) {
                            TaskAddPanelResult(
                                result = result,
                                onAddMore = {
                                    TA.track("task_add_result.add_more_click", mapOf("placed_count" to result.placements.size))
                                    vm.reset()
                                },
                                // 점선 확인(pending)이 남아 있으면 캘린더가 맡으므로 자세한 결과 버튼을 숨겨요
                                onShowDetail = if ((result.pending ?: emptyList()).isNotEmpty()) null else ({
                                    val days = ((result.placements + result.movedToOtherDays).map { it.date } +
                                        result.unscheduled.mapNotNull { it.date }).sorted()
                                    val dayCount = days.firstOrNull()?.let { KST.diffDays(it, days.last()) + 1 } ?: 1
                                    TA.track("task_add_result.detail_click", mapOf("placed_count" to result.placements.size, "day_count" to dayCount))
                                    showDetail = true
                                }),
                            )
                        }
                    }
                }
            }
        }
    }
}

// MARK: - 머리 (스펙 13.2: 공용 시트 머리 NSheetHeader)

@Composable
private fun SheetHeader(title: String? = null, onClose: () -> Unit) {
    NSheetHeader(
        title = title ?: "Task 추가",
        subtitle = if (title == null) "캘린더를 보면서 추가해요" else null,
        onClose = onClose, closeLabel = "Task 추가 닫기",
        modifier = Modifier.background(NC.bgSurface),
    )
}

/** "TimeBlock 확인" 화면 위 줄 — 같은 머리에 뒤로 아이콘 + 제목 */
@Composable
private fun DetailHeader(onBack: () -> Unit) {
    NSheetHeader(title = "TimeBlock 확인", onBack = onBack, modifier = Modifier.background(NC.bgSurface))
}

// MARK: - 추가 방식 고르기 (웹 role="tablist", panel 모양: 3칸)

private data class TaskAddModeOption(
    val mode: TaskAddMode,
    val icon: String,
    val title: String,
    val desc: String,
) {
    companion object {}
}

private val taskAddModeOptions: List<TaskAddModeOption> = listOf(
    TaskAddModeOption(
        mode = TaskAddMode.ai, icon = "sparkles", title = "AI로 정리하기",
        desc = "생각나는 일을 쏟아내면 목표에 연결하고 빈 시간에 배치해요.",
    ),
    TaskAddModeOption(
        mode = TaskAddMode.manual, icon = "pencil.line", title = "직접 추가하기",
        desc = "목표·날짜·시간을 정해서 하나씩 바로 추가해요.",
    ),
    TaskAddModeOption(
        mode = TaskAddMode.log, icon = "note.text", title = "한 일 기록",
        desc = "이미 한 일을 한 줄씩 적으면 목표에 연결해 기록으로 쌓아요.",
    ),
)

@Composable
private fun TaskAddModeSwitch(vm: TaskAddViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (opt in taskAddModeOptions) {
                ModeButton(vm = vm, opt = opt, modifier = Modifier.weight(1f))
            }
        }
        // 웹 패널은 설명을 마우스를 올렸을 때(title)만 보여요 — 앱은 고른 방식의 설명을 아래에 적어요
        val current = taskAddModeOptions.firstOrNull { it.mode == vm.mode }
        if (current != null) {
            Text(current.desc, style = pt(12), color = NC.ink3)
        }
    }
}

@Composable
private fun ModeButton(vm: TaskAddViewModel, opt: TaskAddModeOption, modifier: Modifier) {
    val on = vm.mode == opt.mode
    val locked = vm.modeLocked(opt.mode)
    val shape = RoundedCornerShape(Radius.xl)
    Column(
        modifier
            .alpha(if (locked) 0.4f else 1f)
            .background(NC.surface, shape)
            .border(if (on) 1.5.dp else 1.dp, if (on) NC.borderFocus else NC.borderSubtle, shape)
            .clickable(enabled = !locked, role = Role.Tab) {
                if (opt.mode != vm.mode) {
                    TA.track("task_add.mode_click", mapOf(
                        "add_mode" to opt.mode.rawValue, "from_mode" to vm.mode.rawValue,
                        "ai_step" to TA.aiStep(vm.step), "has_input" to vm.hasInput,
                    ))
                }
                vm.mode = opt.mode
            }
            // 흐리게 잠긴 방식을 누름 — 이유 안내가 없어서 [UX] 이벤트로 봐요
            .disabledTap(locked) {
                TA.track("task_add.mode_locked_click", mapOf("add_mode" to opt.mode.rawValue, "ai_step" to TA.aiStep(vm.step)))
            }
            .semantics {
                selected = on
                contentDescription = opt.title + ". " +
                    if (locked) "AI 정리를 마치거나 처음으로 돌아가면 바꿀 수 있어요" else opt.desc
            }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (opt.mode == TaskAddMode.ai) {
            // AI 방식은 AIBadge (그라데이션) 로 표시해요
            AIBadge(icon = opt.icon)
        } else {
            Box(
                Modifier
                    .size(28.dp)
                    .background(if (on) NC.actionPrimary else NC.subtle, RoundedCornerShape(Radius.lg)),
                contentAlignment = Alignment.Center,
            ) {
                SfImage(opt.icon, size = 14.dp, tint = if (on) NC.actionPrimaryFg else NC.ink3)
            }
        }
        Text(opt.title, style = pt(12, PTWeight.bold), color = NC.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// MARK: - 진행 단계 (1 Task 입력 · 2 목표 연결 · 3 확인)

/** 웹 panel 은 3번째를 "확인"으로 짧게 */
private val taskAddStepLabels = listOf("Task 입력", "목표 연결", "확인")

@Composable
private fun TaskAddStepper(step: Int, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .semantics { contentDescription = "진행 단계" },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until 3) {
            Row(
                Modifier.semantics { selected = i == step },
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(24.dp)
                        .background(
                            when {
                                i < step -> NC.success
                                i == step -> NC.ink
                                else -> NC.subtle
                            },
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (i < step) {
                        SfImage("checkmark", size = 10.dp, tint = NC.onInk)
                    } else {
                        Text("${i + 1}", style = pt(12, PTWeight.bold), color = if (i == step) NC.onInk else NC.ink3)
                    }
                }
                Text(
                    taskAddStepLabels[i],
                    style = pt(12, if (i == step) PTWeight.bold else PTWeight.regular),
                    color = if (i == step) NC.ink else NC.ink3,
                    maxLines = 1,
                )
            }
            if (i < 2) {
                Box(
                    Modifier
                        .width(12.dp)
                        .height(1.dp)
                        .background(NC.lineStrong)
                )
            }
        }
    }
}

// MARK: - 미리보기
// 시트(ModalBottomSheet)는 미리보기 화면에서 안 보일 수 있어요 — 그럴 땐 "Interactive mode" 로 확인하세요.

@Preview(showBackground = true, name = "1단계 입력", heightDp = 900)
@Composable
private fun TaskAddSheetStep0Preview() {
    NaleulTheme(dark = false) {
        TaskAddSheet(previewModel = TaskAddSample.viewModel(step = 0))
    }
}

@Preview(showBackground = true, name = "2단계 목표 연결", heightDp = 900)
@Composable
private fun TaskAddSheetStep1Preview() {
    NaleulTheme(dark = false) {
        TaskAddSheet(previewModel = TaskAddSample.viewModel(step = 1))
    }
}

@Preview(showBackground = true, name = "3단계 결과", heightDp = 900)
@Composable
private fun TaskAddSheetStep2Preview() {
    NaleulTheme(dark = false) {
        TaskAddSheet(previewModel = TaskAddSample.viewModel(step = 2))
    }
}

/** 시트 없이 단계 표시만 */
@Preview(showBackground = true, name = "진행 단계")
@Composable
private fun TaskAddStepperPreview() {
    NaleulTheme(dark = false) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TaskAddStepper(step = 0)
            TaskAddStepper(step = 1)
            TaskAddStepper(step = 2)
        }
    }
}
