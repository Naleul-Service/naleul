package com.naleul.naleul.domain.task.schedular;

import com.naleul.naleul.domain.fcm.dto.request.PushNotificationRequest;
import com.naleul.naleul.domain.fcm.service.PushNotificationService;
import com.naleul.naleul.domain.notification.entity.NotificationSettings;
import com.naleul.naleul.domain.notification.repository.NotificationSettingsRepository;
import com.naleul.naleul.domain.notification.nudge.RoutineNudgeMessages;
import com.naleul.naleul.domain.routine.util.RoutinePracticeStats;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.enums.PlacedBy;
import com.naleul.naleul.domain.task.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class TaskNotificationScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final TaskRepository taskRepository;
    private final PushNotificationService pushNotificationService;
    private final NotificationSettingsRepository notificationSettingsRepository; // ✅ 추가

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    @Transactional
    public void notifyTasksStarting() {
        LocalDateTime now = LocalDateTime.now(KST);
        LocalDateTime windowStart = now.withSecond(0).withNano(0);
        LocalDateTime windowEnd = windowStart.plusMinutes(1);

        List<Task> tasks = taskRepository.findTasksStartingInWindow(windowStart, windowEnd);

        if (tasks.isEmpty()) {
            return;
        }

        log.info("시작 알림 대상 Task {}건 발견", tasks.size());

        // ✅ 이번 배치에 걸린 유저들의 알림 설정을 한 번에 조회 (N+1 방지)
        Set<Long> userIds = tasks.stream()
                .map(task -> task.getUser().getUserId())
                .collect(Collectors.toSet());

        Map<Long, Boolean> taskAlertEnabledByUserId = notificationSettingsRepository.findAllByUser_UserIdIn(userIds)
                .stream()
                .collect(Collectors.toMap(
                        settings -> settings.getUser().getUserId(),
                        NotificationSettings::isTaskAlertEnabled
                ));

        Map<Long, Map<Long, RoutinePracticeStats.Stats>> statsCache = new HashMap<>();
        for (Task task : tasks) {
            Long userId = task.getUser().getUserId();

            // ✅ 설정 row가 아직 없는 유저는 기본값(ON)으로 간주
            boolean taskAlertEnabled = taskAlertEnabledByUserId.getOrDefault(userId, true);

            if (!taskAlertEnabled) {
                log.info("유저 {} - Task 알림 설정 꺼져있어 스킵 (taskId: {})", userId, task.getTaskId());
                continue;
            }

            // 2026-10 알림 줄이기: 배치 엔진이 자동으로 잡은 일반 Task 는 보내지 않는다 (직접 정한 시간 · 루틴만)
            if (task.getRoutine() == null && task.getPlacedBy() == PlacedBy.ENGINE) {
                task.markStartNotified();
                continue;
            }

            try {
                RoutineNudgeMessages.Message msg = message(task, statsCache, now.toLocalDate());
                pushNotificationService.sendToUser(
                        PushNotificationRequest.builder()
                                .userId(userId)
                                .title(msg.title())
                                .body(msg.body())
                                .data(Map.of(
                                        "type", "TASK_START",
                                        "taskId", String.valueOf(task.getTaskId())
                                ))
                                .build()
                );
                task.markStartNotified();
            } catch (Exception e) {
                log.error("Task {} 시작 알림 발송 실패: {}", task.getTaskId(), e.getMessage());
            }
        }
    }

    /**
     * 시작 알림 문구 (2026-10). 루틴은 "지금은 실천률이 85%로 가장 높은 '독서' 루틴을 할 시간이에요"처럼 숫자로,
     * 일반 Task 는 기존처럼 이름만.
     */
    private RoutineNudgeMessages.Message message(Task task, Map<Long, Map<Long, RoutinePracticeStats.Stats>> cache, LocalDate today) {
        if (task.getRoutine() == null) {
            return new RoutineNudgeMessages.Message("⏰ Task 시작 시간",
                    "지금은 '" + task.getTaskName() + "'를 해야 하는 시간이에요");
        }
        Long userId = task.getUser().getUserId();
        Map<Long, RoutinePracticeStats.Stats> stats = cache.computeIfAbsent(userId, id -> RoutinePracticeStats.of(
                taskRepository.findRoutineHistoryOfUser(id, today.minusDays(30), today)));
        Long routineId = task.getRoutine().getRoutineId();
        String name = RoutineNudgeMessages.quoted(task.getRoutine().getEmoji(), task.getRoutine().getRoutineName());
        return RoutineNudgeMessages.routineStart(name, stats.get(routineId), RoutinePracticeStats.isTop(routineId, stats));
    }
}
