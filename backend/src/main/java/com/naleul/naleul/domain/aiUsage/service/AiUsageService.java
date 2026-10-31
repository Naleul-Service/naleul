package com.naleul.naleul.domain.aiUsage.service;

import com.naleul.naleul.domain.aiUsage.dto.AiUsageResponse;
import com.naleul.naleul.domain.aiUsage.dto.AiUsageResponse.Item;
import com.naleul.naleul.domain.aiUsage.enums.AiFeature;
import com.naleul.naleul.domain.aiUsage.quota.AiQuotaPolicy;
import com.naleul.naleul.domain.aiUsage.quota.AiQuotaService;
import com.naleul.naleul.domain.goalCreation.support.GoalClock;
import com.naleul.naleul.domain.goalCreation.config.GoalAiProperties;
import com.naleul.naleul.domain.goalCreation.config.GoalAiQuota;
import com.naleul.naleul.domain.goalCreation.repository.GoalCreationSessionRepository;
import com.naleul.naleul.domain.goalCreation.repository.GoalDraftRepository;
import com.naleul.naleul.domain.task.policy.TaskQuotaValidator;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.service.UserFinder;
import com.naleul.naleul.global.policy.PlanLimits;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 하루 횟수가 정해진 기능의 오늘 사용량.
 *
 * 왜: 한도에 걸리면 그때서야 "오늘은 더 할 수 없어요"가 떠서, 쓰던 중에 막힌 느낌을 준다.
 *     시작 전에 "오늘 3/5회 남음"을 보여주면 아껴 쓰거나 Pro 를 고를 수 있다.
 * 집계 기준은 각 기능이 실제로 막을 때 쓰는 것과 같다 (같은 리포지토리 · 같은 "오늘 0시").
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiUsageService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final UserFinder userFinder;
    private final GoalAiProperties goalAiProperties;
    private final GoalCreationSessionRepository sessionRepository;
    private final GoalDraftRepository draftRepository;
    private final TaskQuotaValidator taskQuotaValidator;
    private final AiQuotaService aiQuotaService;
    private final GoalClock goalClock;
    private final GoalAiQuota quota;

    public AiUsageResponse get(Long userId, Long sessionId) {
        User user = userFinder.findUser(userId);
        boolean premium = user.isCurrentlyPremium();
        boolean pro = goalAiProperties.proLimitFor(userId, premium);
        LocalDateTime start = startOfUserToday();

        List<Item> items = new ArrayList<>();
        // 2026-10: AI 목표 만들기 · 계획 초안은 하루 · 주 · 월 한도 (GoalAiQuota)
        LocalDateTime week = goalClock.startOfUserWeek();
        LocalDateTime month = goalClock.startOfUserMonth();
        GoalAiQuota.Limits sessions = quota.sessions(pro);
        GoalAiQuota.Limits drafts = quota.drafts(pro);
        items.add(Item.of("GOAL_SESSION", "AI 목표 만들기",
                "AI와 대화하며 목표를 새로 만드는 횟수",
                sessionRepository.countByUser_UserIdAndCreatedAtAfter(userId, start), sessions.day()));
        items.add(Item.of("GOAL_SESSION_WEEK", "AI 목표 만들기 (이번 주)",
                "이번 주(월~일)에 AI로 목표를 만드는 횟수 · 월요일 0시에 다시 채워져요",
                sessionRepository.countByUser_UserIdAndCreatedAtAfter(userId, week), sessions.week()));
        items.add(Item.of("GOAL_SESSION_MONTH", "AI 목표 만들기 (이번 달)",
                "이번 달에 AI로 목표를 만드는 횟수 · 1일 0시에 다시 채워져요",
                sessionRepository.countByUser_UserIdAndCreatedAtAfter(userId, month), sessions.month()));
        items.add(Item.of("GOAL_DRAFT", "AI 계획 초안",
                "목표 계획 초안을 만들거나 다시 만드는 횟수 (모든 대화 합계)",
                draftRepository.countBySession_User_UserIdAndCreatedAtAfter(userId, start), drafts.day()));
        items.add(Item.of("GOAL_DRAFT_WEEK", "AI 계획 초안 (이번 주)",
                "이번 주에 계획 초안을 만들거나 다시 만드는 횟수 · 월요일 0시에 다시 채워져요",
                draftRepository.countBySession_User_UserIdAndCreatedAtAfter(userId, week), drafts.week()));
        items.add(Item.of("GOAL_DRAFT_MONTH", "AI 계획 초안 (이번 달)",
                "이번 달에 계획 초안을 만들거나 다시 만드는 횟수 · 1일 0시에 다시 채워져요",
                draftRepository.countBySession_User_UserIdAndCreatedAtAfter(userId, month), drafts.month()));
        items.add(Item.of("GOAL_EDIT", "AI로 목표 고치기",
                "말로 계획을 고치는 횟수 (직접 고치기는 제한 없어요)",
                aiQuotaService.used(userId, AiFeature.GOAL_EDIT),
                AiQuotaPolicy.dailyLimit(AiFeature.GOAL_EDIT, pro)));
        items.add(Item.of("SCHEDULE_EDIT", "AI로 일정 조정",
                "캘린더에서 말로 루틴 · 일정을 한꺼번에 옮기는 횟수",
                aiQuotaService.used(userId, AiFeature.SCHEDULE_EDIT),
                AiQuotaPolicy.dailyLimit(AiFeature.SCHEDULE_EDIT, pro)));
        items.add(Item.of("BRAIN_DUMP", "브레인 덤프 AI 정리",
                "AI가 할 일을 목표에 연결해 주는 횟수 (넘으면 기본 정리로 계속 쓸 수 있어요)",
                aiQuotaService.used(userId, AiFeature.BRAIN_DUMP),
                AiQuotaPolicy.dailyLimit(AiFeature.BRAIN_DUMP, pro)));
        Integer taskLimit = premium ? null : PlanLimits.display(PlanLimits.TASK_PER_DAY_FREE_LIMIT);
        if (taskLimit != null) {
            items.add(Item.of("TASK_PER_DAY", "Task 추가",
                    "하루에 새로 만들 수 있는 Task 수",
                    taskQuotaValidator.used(user, LocalDate.now(KST)), taskLimit));
        }

        Item session = null;
        if (sessionId != null && sessionRepository.findByGoalCreationSessionIdAndUser_UserId(sessionId, userId).isPresent()) {
            session = Item.of("SESSION_REGENERATE", "이 목표의 계획 만들기",
                    "한 목표 대화에서 계획을 만들 수 있는 횟수 (처음 만들기 포함)",
                    draftRepository.countBySession_GoalCreationSessionId(sessionId),
                    goalAiProperties.maxRegenerationsPerSession());
        }
        return new AiUsageResponse(pro, LocalDate.now(KST).plusDays(1).atStartOfDay(), items, session);
    }

    /** GoalClock.startOfUserToday 와 같은 값: 한국 오늘 0시를 서버 시간대로 */
    private static LocalDateTime startOfUserToday() {
        return java.time.ZonedDateTime.now(KST).truncatedTo(java.time.temporal.ChronoUnit.DAYS)
                .withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }
}
