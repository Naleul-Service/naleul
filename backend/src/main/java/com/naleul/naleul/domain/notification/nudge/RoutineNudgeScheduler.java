package com.naleul.naleul.domain.notification.nudge;

import com.naleul.naleul.domain.fcm.dto.request.PushNotificationRequest;
import com.naleul.naleul.domain.fcm.service.PushNotificationService;
import com.naleul.naleul.domain.notification.entity.NotificationSettings;
import com.naleul.naleul.domain.notification.repository.NotificationSettingsRepository;
import com.naleul.naleul.domain.routine.entity.Routine;
import com.naleul.naleul.domain.routine.util.RoutinePracticeStats;
import com.naleul.naleul.domain.routine.util.RoutinePracticeStats.Stats;
import com.naleul.naleul.domain.routine.util.RoutineTimeStatsCalculator;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 루틴 실천 알림 (2026-10) — "할 시간이에요" 같은 일반 알림 대신, 그 사람의 기록으로 말한다.
 *
 *  ① YESTERDAY_TIME : 어제 10:00 에 한 루틴을 오늘은 아직 안 했으면 → 오늘 10:00 에
 *                     "어제 10:00에 '독서'를 했어요. 연속 7일 기록을 지켜봐요" (연속이 짧으면 실천률로)
 *                     오늘 계획 시각과 30분 안쪽이면 보내지 않는다 (시작 알림이 이미 간다)
 *  ② RATE_RESCHEDULE: 실천률 70% 이상(기록 5번 이상) 루틴을 계획 끝 + 30분이 지나도록 안 했으면
 *                     "실천률 85%로 잘 지켜 온 '독서' 루틴, 오늘은 아직이에요. 계획 시간을 바꿔서 이어가 볼까요?"
 *
 * 피로도: 사용자당 하루 2개까지, 같은 Task 에 같은 알림은 한 번, 07:00~23:00 에만, Task 알림을 끈 사람 · 끈 루틴은 제외.
 * 실천률 · 연속은 최근 30일 기록(오늘 제외)으로 계산한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RoutineNudgeScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    static final int RUN_EVERY_MINUTES = 5;
    static final int MAX_PER_DAY = 2;
    static final int HISTORY_DAYS = 30;
    static final int SAME_TIME_MINUTES = 30;
    static final int RESCHEDULE_AFTER_END_MINUTES = 30;
    static final int RESCHEDULE_MIN_HISTORY = 5;
    static final LocalTime QUIET_END = LocalTime.of(7, 0);
    static final LocalTime QUIET_START = LocalTime.of(23, 0);

    private final TaskRepository taskRepository;
    private final RoutineNudgeLogRepository logRepository;
    private final PushNotificationService pushNotificationService;
    private final NotificationSettingsRepository notificationSettingsRepository;

    // 트랜잭션 없이: 푸시를 보내는 동안 DB 연결을 잡지 않게 (필요한 연관은 쿼리에서 JOIN FETCH)
    @Scheduled(cron = "0 */5 * * * *", zone = "Asia/Seoul")
    public void run() {
        LocalDateTime now = LocalDateTime.now(KST).withSecond(0).withNano(0);
        if (now.toLocalTime().isBefore(QUIET_END) || !now.toLocalTime().isBefore(QUIET_START)) return;
        LocalDate today = now.toLocalDate();

        List<Task> open = taskRepository.findOpenRoutineTasksOn(today);
        if (open.isEmpty()) return;

        Map<Long, List<Task>> byUser = open.stream().collect(Collectors.groupingBy(t -> t.getUser().getUserId()));
        Map<Long, Boolean> alertOn = notificationSettingsRepository.findAllByUser_UserIdIn(byUser.keySet()).stream()
                .collect(Collectors.toMap(s -> s.getUser().getUserId(), NotificationSettings::isTaskAlertEnabled, (a, b) -> a));

        int sent = 0;
        for (Map.Entry<Long, List<Task>> e : byUser.entrySet()) {
            Long userId = e.getKey();
            if (!alertOn.getOrDefault(userId, true)) continue;
            try {
                sent += nudgeUser(userId, e.getValue(), now);
            } catch (RuntimeException ex) {
                log.warn("[RoutineNudge] userId={} 실패: {}", userId, ex.getMessage());
            }
        }
        if (sent > 0) log.info("[RoutineNudge] {} 발송 {}건", now, sent);
    }

    int nudgeUser(Long userId, List<Task> openTasks, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        long already = logRepository.countByUserIdAndSentDate(userId, today);
        if (already >= MAX_PER_DAY) return 0;

        Map<Long, Stats> stats = RoutinePracticeStats.of(
                taskRepository.findRoutineHistoryOfUser(userId, today.minusDays(HISTORY_DAYS), today));

        // 어제 한 시각 (실제 시각 > 계획 시각 > 완료 시각 순)
        LocalDate yesterday = today.minusDays(1);
        Map<Long, Long> yesterdayTaskByRoutine = new HashMap<>();
        for (Task t : openTasks) {
            Long rid = t.getRoutine().getRoutineId();
            Stats s = stats.get(rid);
            if (s == null) continue;
            s.on(yesterday).filter(RoutinePracticeStats.Instance::done)
                    .ifPresent(i -> yesterdayTaskByRoutine.put(rid, i.taskId()));
        }
        Map<Long, Integer> yesterdayStartMinuteByTask = new HashMap<>();
        if (!yesterdayTaskByRoutine.isEmpty()) {
            Map<Long, List<RoutineTimeStatsCalculator.Practice>> practices = RoutineTimeStatsCalculator.practicesByRoutine(
                    taskRepository.findPracticeTimesByTaskIds(new ArrayList<>(yesterdayTaskByRoutine.values())));
            practices.values().forEach(list -> list.forEach(p ->
                    yesterdayStartMinuteByTask.merge(p.taskId(), p.startMinute(), Math::min)));
        }

        int nowMin = now.getHour() * 60 + now.getMinute();
        int sent = 0;
        // 같은 루틴이 하루에 여러 개(쪼갠 블록)면 하나만
        Set<Long> handledRoutines = new HashSet<>();
        List<Task> ordered = new ArrayList<>(openTasks);
        ordered.sort(Comparator.comparing(Task::getPlannedStartAt, Comparator.nullsLast(Comparator.naturalOrder())));
        for (Task t : ordered) {
            if (already + sent >= MAX_PER_DAY) break;
            Routine r = t.getRoutine();
            if (!handledRoutines.add(r.getRoutineId())) continue;
            Stats s = stats.get(r.getRoutineId());
            String name = RoutineNudgeMessages.quoted(r.getEmoji(), r.getRoutineName());

            // ① 어제 이 시각
            Long yTask = yesterdayTaskByRoutine.get(r.getRoutineId());
            Integer yMin = yTask == null ? null : yesterdayStartMinuteByTask.get(yTask);
            if (yMin != null && dueNow(yMin, nowMin) && !nearPlannedStart(t, today, yMin)) {
                String hhmm = String.format("%02d:%02d", yMin / 60, yMin % 60);
                if (send(userId, t, RoutineNudgeKind.YESTERDAY_TIME, RoutineNudgeMessages.yesterdayTime(name, hhmm, s), now)) {
                    sent++;
                    continue;
                }
            }

            // ② 실천률이 높은데 계획 시간에 못 함
            if (s != null && s.total() >= RESCHEDULE_MIN_HISTORY && s.rate() >= RoutineNudgeMessages.HIGH_RATE
                    && t.getPlannedEndAt() != null && t.getPlannedEndAt().toLocalDate().equals(today)) {
                int endMin = t.getPlannedEndAt().getHour() * 60 + t.getPlannedEndAt().getMinute();
                if (dueNow(endMin + RESCHEDULE_AFTER_END_MINUTES, nowMin)
                        && send(userId, t, RoutineNudgeKind.RATE_RESCHEDULE, RoutineNudgeMessages.rateReschedule(name, s), now)) {
                    sent++;
                }
            }
        }
        return sent;
    }

    /** 이번 실행 창(직전 5분)에 그 시각이 들어오는지 */
    static boolean dueNow(int targetMinute, int nowMinute) {
        return targetMinute > nowMinute - RUN_EVERY_MINUTES && targetMinute <= nowMinute;
    }

    /** 오늘 계획 시각이 어제 한 시각과 30분 안쪽이면 시작 알림이 따로 가므로 건너뛴다 */
    static boolean nearPlannedStart(Task t, LocalDate today, int minute) {
        LocalDateTime start = t.getPlannedStartAt();
        if (start == null || !start.toLocalDate().equals(today)) return false;
        int planned = start.getHour() * 60 + start.getMinute();
        return Math.abs(planned - minute) < SAME_TIME_MINUTES && t.isNotificationEnabled();
    }

    private boolean send(Long userId, Task t, RoutineNudgeKind kind, RoutineNudgeMessages.Message msg, LocalDateTime now) {
        if (logRepository.existsByTaskIdAndKind(t.getTaskId(), kind)) return false;
        try {
            logRepository.saveAndFlush(RoutineNudgeLog.of(userId, t.getTaskId(), kind, now));
        } catch (DataIntegrityViolationException dup) {
            return false; // 다른 실행이 먼저 보냄
        }
        Map<String, String> data = new HashMap<>();
        data.put("type", "ROUTINE_NUDGE");
        data.put("kind", kind.name());
        data.put("taskId", String.valueOf(t.getTaskId()));
        data.put("routineId", String.valueOf(t.getRoutine().getRoutineId()));
        if (t.getGoalCategory() != null) data.put("goalCategoryId", String.valueOf(t.getGoalCategory().getGoalCategoryId()));
        data.put("date", now.toLocalDate().toString());
        try {
            pushNotificationService.sendToUser(PushNotificationRequest.builder()
                    .userId(userId).title(msg.title()).body(msg.body()).data(data).build());
            return true;
        } catch (RuntimeException e) {
            log.warn("[RoutineNudge] 푸시 실패 taskId={} kind={} : {}", t.getTaskId(), kind, e.getMessage());
            return true; // 기록은 남았으니 하루 개수에는 포함
        }
    }
}
