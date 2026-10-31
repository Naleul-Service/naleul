package com.naleul.naleul.domain.work.service;

import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.planner.support.PlannerClock;
import com.naleul.naleul.domain.task.repository.TaskRepository;
import com.naleul.naleul.domain.user.service.UserFinder;
import com.naleul.naleul.domain.work.dto.request.WorkTopicOrderRequest;
import com.naleul.naleul.domain.work.dto.request.WorkTopicRequest;
import com.naleul.naleul.domain.work.entity.WorkTopic;
import com.naleul.naleul.domain.work.repository.WorkTopicRepository;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 업무형 목표의 주제 만들기 · 고치기 · 지우기 · 순서 (V52) */
@Service
@RequiredArgsConstructor
@Transactional
public class WorkTopicService {

    static final int MAX_TOPICS = 20;

    private final WorkTopicRepository topicRepository;
    private final TaskRepository taskRepository;
    private final WorkGoalFinder workGoalFinder;
    private final UserFinder userFinder;
    private final PlannerClock clock;

    public WorkTopic create(Long userId, Long goalCategoryId, WorkTopicRequest request) {
        GoalCategory goal = workGoalFinder.find(goalCategoryId, userFinder.findUser(userId));
        if (request.name() == null || request.name().isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        List<WorkTopic> existing = topicRepository.findAllOfGoal(goalCategoryId);
        if (existing.size() >= MAX_TOPICS) throw new CustomException(ErrorCode.WORK_TOPIC_LIMIT_EXCEEDED);
        int order = existing.stream().mapToInt(WorkTopic::getSortOrder).max().orElse(-1) + 1;
        return topicRepository.save(WorkTopic.create(goal, request.name(), request.emoji(), request.dueDate(), order));
    }

    public WorkTopic update(Long userId, Long goalCategoryId, Long topicId, WorkTopicRequest request) {
        workGoalFinder.find(goalCategoryId, userFinder.findUser(userId));
        WorkTopic topic = find(goalCategoryId, topicId);
        topic.update(request.name(), request.emoji(), request.dueDate(), Boolean.TRUE.equals(request.clearDueDate()));
        if (request.completed() != null) topic.markCompleted(request.completed(), clock.now());
        return topic;
    }

    /** 주제를 지우면 그 주제의 Task 는 지우지 않고 "주제 없음"으로 옮긴다 */
    public void delete(Long userId, Long goalCategoryId, Long topicId) {
        workGoalFinder.find(goalCategoryId, userFinder.findUser(userId));
        WorkTopic topic = find(goalCategoryId, topicId);
        topic.delete();                          // 먼저 지움 표시 → 아래 일괄 UPDATE 가 flush 하면서 같이 저장된다
        taskRepository.clearWorkTopic(topicId);
    }

    public void reorder(Long userId, Long goalCategoryId, WorkTopicOrderRequest request) {
        workGoalFinder.find(goalCategoryId, userFinder.findUser(userId));
        List<WorkTopic> topics = topicRepository.findAllOfGoal(goalCategoryId);
        Map<Long, WorkTopic> byId = new HashMap<>();
        topics.forEach(t -> byId.put(t.getWorkTopicId(), t));
        Set<Long> ordered = new LinkedHashSet<>();
        request.topicIds().stream().filter(byId::containsKey).forEach(ordered::add);
        topics.forEach(t -> ordered.add(t.getWorkTopicId()));
        int i = 0;
        for (Long id : ordered) byId.get(id).changeSortOrder(i++);
    }

    WorkTopic find(Long goalCategoryId, Long topicId) {
        return topicRepository.findInGoal(topicId, goalCategoryId)
                .orElseThrow(() -> new CustomException(ErrorCode.WORK_TOPIC_NOT_FOUND));
    }
}
