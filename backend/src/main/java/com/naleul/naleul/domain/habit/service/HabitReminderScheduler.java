package com.naleul.naleul.domain.habit.service;

import com.naleul.naleul.domain.fcm.dto.request.PushNotificationRequest;
import com.naleul.naleul.domain.fcm.service.PushNotificationService;
import com.naleul.naleul.domain.habit.entity.Habit;
import com.naleul.naleul.domain.habit.repository.HabitCheckRepository;
import com.naleul.naleul.domain.habit.repository.HabitRepository;
import com.naleul.naleul.domain.notification.entity.NotificationSettings;
import com.naleul.naleul.domain.notification.repository.NotificationSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 습관 알림 (V52) — 1분마다, 알림 시각이 지금인 습관 중 "오늘 해야 하는데 아직 안 한" 것만 푸시.
 * 잊고 있던 걸 떠올리게 하는 게 목적이라 이미 체크했으면 보내지 않는다.
 * Task 시작 알림과 같은 설정(taskAlertEnabled)을 따른다 (설정이 없으면 켜짐).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HabitReminderScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final HabitRepository habitRepository;
    private final HabitCheckRepository checkRepository;
    private final PushNotificationService pushNotificationService;
    private final NotificationSettingsRepository notificationSettingsRepository;

    // 트랜잭션 없이: 푸시(네트워크)를 보내는 동안 DB 연결을 잡고 있지 않게. 필요한 값(user)은 쿼리에서 JOIN FETCH 로 미리 읽는다
    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    public void remind() {
        LocalDateTime now = LocalDateTime.now(KST).withSecond(0).withNano(0);
        LocalDate today = now.toLocalDate();

        List<Habit> due = habitRepository.findToRemindAt(now.toLocalTime()).stream()
                .filter(h -> h.isRepeatDay(today.getDayOfWeek()))
                .filter(h -> !h.getStartDate().isAfter(today))
                .toList();
        if (due.isEmpty()) return;

        Set<Long> checked = new HashSet<>(checkRepository.findCheckedHabitIds(
                due.stream().map(Habit::getHabitId).toList(), today));
        Set<Long> userIds = due.stream().map(h -> h.getUser().getUserId()).collect(Collectors.toSet());
        Map<Long, Boolean> alertOn = notificationSettingsRepository.findAllByUser_UserIdIn(userIds).stream()
                .collect(Collectors.toMap(s -> s.getUser().getUserId(), NotificationSettings::isTaskAlertEnabled, (a, b) -> a));

        for (Habit h : due) {
            Long userId = h.getUser().getUserId();
            if (checked.contains(h.getHabitId()) || !alertOn.getOrDefault(userId, true)) continue;
            try {
                String name = (h.getEmoji() != null ? h.getEmoji() + " " : "") + h.getHabitName();
                pushNotificationService.sendToUser(PushNotificationRequest.builder()
                        .userId(userId)
                        .title("🌱 오늘의 생활 습관")
                        .body("'" + name + "' 아직 체크하지 않았어요")
                        .data(Map.of("type", "HABIT_REMIND", "habitId", String.valueOf(h.getHabitId())))
                        .build());
            } catch (Exception e) {
                log.error("습관 {} 알림 발송 실패: {}", h.getHabitId(), e.getMessage());
            }
        }
    }
}
