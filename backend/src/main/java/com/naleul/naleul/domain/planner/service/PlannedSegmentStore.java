package com.naleul.naleul.domain.planner.service;

import com.naleul.naleul.domain.planner.entity.TaskPlannedSegment;
import com.naleul.naleul.domain.planner.repository.TaskPlannedSegmentRepository;
import com.naleul.naleul.domain.planner.support.DateTimeRange;
import com.naleul.naleul.domain.task.entity.Task;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 나눠 넣은 Task 의 계획 구간 (V65) 읽기 · 쓰기.
 *
 * 구간은 Task 의 planned_start_at ~ planned_end_at 과 맞을 때만 쓴다 (첫 시작 = 계획 시작, 마지막 끝 = 계획 끝).
 * 사용자가 블록을 옮기면 계획 시각이 바뀌므로, 지우는 걸 놓친 경로가 있어도 낡은 구간은 저절로 무시된다.
 */
@Component
@RequiredArgsConstructor
public class PlannedSegmentStore {

    private final TaskPlannedSegmentRepository repository;

    /** taskId → 계획 구간 (2개 이상이고 계획 시각과 맞는 것만) */
    public Map<Long, List<DateTimeRange>> segmentsOf(Collection<Task> tasks) {
        Map<Long, Task> byId = new HashMap<>();
        for (Task t : tasks) {
            if (t.getTaskId() != null && t.getPlannedStartAt() != null && t.isPlannedSplit()) byId.put(t.getTaskId(), t);
        }
        if (byId.isEmpty()) return Map.of();
        Map<Long, List<DateTimeRange>> raw = new LinkedHashMap<>();
        for (TaskPlannedSegment s : repository.findAllByTaskIdInOrderByTaskIdAscSortOrderAsc(byId.keySet())) {
            raw.computeIfAbsent(s.getTaskId(), k -> new ArrayList<>()).add(new DateTimeRange(s.getStartAt(), s.getEndAt()));
        }
        Map<Long, List<DateTimeRange>> out = new HashMap<>();
        raw.forEach((id, list) -> {
            if (matches(byId.get(id), list)) out.put(id, List.copyOf(list));
        });
        return out;
    }

    /** 구간이 이 Task 의 계획 시각과 맞는지 */
    public static boolean matches(Task t, List<DateTimeRange> segments) {
        if (t == null || !t.isPlannedSplit() || segments == null || segments.size() < 2) return false;
        return segments.get(0).start().equals(t.getPlannedStartAt())
                && segments.get(segments.size() - 1).end().equals(t.getPlannedEndAt());
    }

    /** 구간을 새로 저장 (1개 이하면 지우기만) */
    @Transactional
    public void replace(Long taskId, List<DateTimeRange> segments) {
        repository.deleteAllOfTask(taskId);
        if (segments == null || segments.size() < 2) return;
        for (int i = 0; i < segments.size(); i++) {
            repository.save(TaskPlannedSegment.of(taskId, segments.get(i).start(), segments.get(i).end(), i));
        }
    }

    /** 블록을 옮기거나 비웠을 때 */
    @Transactional
    public void clear(Long taskId) {
        if (taskId != null) repository.deleteAllOfTask(taskId);
    }
}
