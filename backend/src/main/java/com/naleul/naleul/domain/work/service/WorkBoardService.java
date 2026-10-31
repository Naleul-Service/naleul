package com.naleul.naleul.domain.work.service;

import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.planner.repository.TimetableQueryRepository;
import com.naleul.naleul.domain.planner.support.PlannerClock;
import com.naleul.naleul.domain.retrospective.entity.Retrospective;
import com.naleul.naleul.domain.retrospective.repository.RetrospectiveRepository;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.enums.TaskStatus;
import com.naleul.naleul.domain.user.service.UserFinder;
import com.naleul.naleul.domain.work.dto.response.WorkBoardResponse;
import com.naleul.naleul.domain.work.entity.WorkTopic;
import com.naleul.naleul.domain.work.repository.WorkTopicRepository;
import com.naleul.naleul.domain.work.support.WorkBoardCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 업무형 목표 상세 (V52) — 보드 · 오늘 할 일 · 마감 · 아카이브 · 회고를 한 번에.
 * 계산은 WorkBoardCalculator (DB 없이 테스트 가능), 여기서는 데이터만 모은다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkBoardService {

    static final int DEFAULT_ARCHIVE_DAYS = 30;
    static final int MAX_ARCHIVE_DAYS = 365;
    static final int RETRO_LIMIT = 5;

    private final TimetableQueryRepository timetableQueryRepository;
    private final WorkTopicRepository topicRepository;
    private final RetrospectiveRepository retrospectiveRepository;
    private final WorkGoalFinder workGoalFinder;
    private final UserFinder userFinder;
    private final PlannerClock clock;

    public WorkBoardResponse board(Long userId, Long goalCategoryId, Integer archiveDays) {
        GoalCategory goal = workGoalFinder.find(goalCategoryId, userFinder.findUser(userId));
        // 주제를 먼저 읽어 두면 Task 의 주제 이름이 1차 캐시에서 바로 풀린다 (N+1 방지)
        List<WorkTopic> topics = topicRepository.findAllOfGoal(goalCategoryId);
        List<Task> tasks = timetableQueryRepository.findAllOfGoal(userId, goalCategoryId).stream()
                .filter(t -> t.getTaskStatus() != TaskStatus.SKIPPED)
                .toList();
        int days = archiveDays == null ? DEFAULT_ARCHIVE_DAYS : Math.max(1, Math.min(archiveDays, MAX_ARCHIVE_DAYS));

        List<WorkBoardResponse.Retro> retros = retrospectiveRepository.findAllByFilter(userId, null, null, null,
                        goalCategoryId, null,
                        PageRequest.of(0, RETRO_LIMIT, Sort.by(Sort.Direction.DESC, "reviewDate", "retrospectiveId")))
                .stream()
                .map(WorkBoardService::toRetro)
                .toList();

        return WorkBoardCalculator.build(goal, topics, tasks, retros, clock.today(), days);
    }

    /** 공유용 텍스트 (카톡 · 슬랙에 붙여 넣기). scope: TODAY · REMAINING(기본) · ALL */
    public String shareText(Long userId, Long goalCategoryId, String scope) {
        WorkBoardResponse board = board(userId, goalCategoryId, 7);
        return WorkBoardCalculator.shareText(board, scope);
    }

    private static WorkBoardResponse.Retro toRetro(Retrospective r) {
        return new WorkBoardResponse.Retro(r.getRetrospectiveId(), r.getReviewDate(),
                r.getReviewType() != null ? r.getReviewType().name() : null, r.getContent());
    }

}
