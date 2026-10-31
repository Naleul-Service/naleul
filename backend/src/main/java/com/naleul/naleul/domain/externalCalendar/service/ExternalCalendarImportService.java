package com.naleul.naleul.domain.externalCalendar.service;

import com.naleul.naleul.domain.category.service.CategoryFinder;
import com.naleul.naleul.domain.externalCalendar.dto.request.ExternalEventImportRequest;
import com.naleul.naleul.domain.externalCalendar.dto.response.ExternalEventImportResponse;
import com.naleul.naleul.domain.externalCalendar.service.ExternalEventImportPlanner.Draft;
import com.naleul.naleul.domain.externalCalendar.service.ExternalEventImportPlanner.Plan;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.planner.support.PlannerClock;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.repository.TaskRepository;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.service.UserFinder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 외부 캘린더(구글 · 아이폰) 일정 가져오기 — 버튼을 누를 때 한 번 가져온다 (계속 동기화하지 않음).
 * 가져온 일정은 일반 Task 가 되어 완료 체크 · 통계에 들어간다. 원래 캘린더에서 바꿔도 다시 반영되지 않는다.
 *
 * Free 일일 직접 생성 한도(TaskQuotaValidator)에는 세지 않는다 (source = EXTERNAL_CALENDAR).
 * 한 번에 최대 300개 (요청 DTO 검증).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ExternalCalendarImportService {

    private final UserFinder userFinder;
    private final CategoryFinder categoryFinder;
    private final TaskRepository taskRepository;
    private final PlannerClock clock;

    public ExternalEventImportResponse importEvents(Long userId, ExternalEventImportRequest request) {
        User user = userFinder.findUser(userId);
        GoalCategory goal = categoryFinder.findGoalCategory(request.goalCategoryId(), user);   // 소유권 확인 (없으면 null)
        LocalDate today = clock.today();

        List<String> ids = request.events().stream()
                .map(ExternalEventImportRequest.Event::externalId)
                .filter(Objects::nonNull).map(String::strip).distinct().toList();
        List<String> imported = ids.isEmpty() ? List.of()
                : taskRepository.findImportedExternalIds(userId, request.source(), ids);

        // 같은 이름 · 같은 시작 시각 Task — 요청에 들어 있는 시간 범위만 조회
        List<LocalDateTime> starts = request.events().stream()
                .filter(e -> !e.allDay() && e.start() != null).map(ExternalEventImportRequest.Event::start).toList();
        List<String> nameStarts = starts.isEmpty() ? List.of()
                : taskRepository.findTimedStartingBetween(userId,
                                starts.stream().min(Comparator.naturalOrder()).get().withSecond(0).withNano(0),
                                starts.stream().max(Comparator.naturalOrder()).get().plusMinutes(1))
                        .stream()
                        .map(t -> ExternalEventImportPlanner.nameStartKey(t.getTaskName(), t.getPlannedStartAt()))
                        .toList();

        Plan plan = ExternalEventImportPlanner.plan(request.events(), today, imported, nameStarts);
        List<Task> tasks = plan.drafts().stream()
                .map(d -> toTask(user, goal, request, d))
                .toList();
        taskRepository.saveAll(tasks);   // @PrePersist: 시간 일정은 잠금(USER), created_at 기록

        log.info("[ExternalCalendar] import userId={} source={} requested={} created={} dup={} past={} invalid={}",
                userId, request.source(), request.events().size(), tasks.size(),
                plan.skippedDuplicate(), plan.skippedPast(), plan.skippedInvalid());
        return new ExternalEventImportResponse(tasks.size(), plan.skippedDuplicate(), plan.skippedPast(),
                plan.skippedInvalid());
    }

    private static Task toTask(User user, GoalCategory goal, ExternalEventImportRequest request, Draft d) {
        return Task.fromExternalEvent(user, goal, request.source(), d.externalId(), d.title(), d.allDay(),
                d.date(), d.start(), d.end());
    }
}
