package com.naleul.naleul.domain.planner.service;

import com.naleul.naleul.domain.planner.dto.response.TimeBlockTaskResponse;
import com.naleul.naleul.domain.planner.repository.TimetableQueryRepository;
import com.naleul.naleul.domain.planner.support.DateTimeRange;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.enums.TaskStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 완료한 Task 의 "실제로 한 시간" 읽기.
 *
 * 완료한 Task 는 화면에서 실제 시각에 그려진다. 그런데 배치·드래그 판정이 계획 시각을 쓰면
 * "화면에선 비어 있는 계획 자리"가 계속 막혀 있게 된다. 그래서 자리 차지 판정도 이 클래스로 실제 시각을 쓴다.
 */
@Component
@RequiredArgsConstructor
public class ActualRangeReader {

    private final TimetableQueryRepository queryRepository;
    /** 나눠서 한 구간 (V57). 단위 테스트처럼 없으면 구간 없이 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.naleul.naleul.domain.actualTask.repository.TaskActualSegmentRepository segmentRepository;

    /**
     * 완료한 Task 의 실제 수행 구간.
     * task_actual(일반·루틴, "계획과 다른 시간에 했어요")이 있으면 그걸, 없으면 Task 자체 컬럼(미션)을 쓴다.
     * 실제 시각 없이 완료만 누른 Task 는 "계획한 시각에 했다"고 보고 넣지 않는다.
     */
    public Map<Long, DateTimeRange> actualRanges(Collection<Task> tasks) {
        Map<Long, DateTimeRange> map = new HashMap<>();
        List<Long> completedIds = new ArrayList<>();
        for (Task t : tasks) {
            if (t.getTaskStatus() != TaskStatus.COMPLETED || t.getTaskId() == null) continue;
            completedIds.add(t.getTaskId());
            DateTimeRange own = TimeBlockTaskResponse.ownActualRange(t);
            if (own != null) map.put(t.getTaskId(), own);
        }
        if (completedIds.isEmpty()) return map;
        for (Object[] row : queryRepository.findActualRanges(completedIds)) {
            LocalDateTime s = (LocalDateTime) row[1];
            LocalDateTime e = (LocalDateTime) row[2];
            if (s != null && e != null && e.isAfter(s)) map.put((Long) row[0], new DateTimeRange(s, e));
        }
        return map;
    }

    /** 여러 번 나눠서 한 완료 Task 의 구간들 (2개 이상인 Task 만 담는다) */
    public Map<Long, List<DateTimeRange>> actualSegments(Collection<Task> tasks) {
        List<Long> ids = tasks.stream()
                .filter(t -> t.getTaskStatus() == TaskStatus.COMPLETED && t.getTaskId() != null)
                .map(Task::getTaskId).toList();
        Map<Long, List<DateTimeRange>> map = new HashMap<>();
        if (ids.isEmpty()) return map;
        if (segmentRepository == null) return map;
        for (Object[] row : segmentRepository.findByTaskIds(ids)) {
            LocalDateTime s = (LocalDateTime) row[1];
            LocalDateTime e = (LocalDateTime) row[2];
            if (s != null && e != null && e.isAfter(s)) {
                map.computeIfAbsent((Long) row[0], k -> new ArrayList<>()).add(new DateTimeRange(s, e));
            }
        }
        map.values().removeIf(list -> list.size() < 2);
        return map;
    }

    /** 실제 수행 시간이 [from, to) 와 겹치는 완료 Task (계획은 다른 시간이어도) */
    public List<Task> completedWithActualIn(Long userId, LocalDateTime from, LocalDateTime to) {
        return queryRepository.findCompletedWithActualInRange(userId, from, to);
    }

    /** 자리를 차지하는 구간: 완료 + 실제 시각 있음 → 실제, 아니면 계획 (시간 없으면 null) */
    public static DateTimeRange occupied(Task t, DateTimeRange actual, int defaultMinutes) {
        if (t.getTaskStatus() == TaskStatus.COMPLETED && actual != null) return actual;
        LocalDateTime s = t.getPlannedStartAt();
        if (s == null) return null;
        LocalDateTime e = t.getPlannedEndAt();
        if (e == null || !e.isAfter(s)) e = s.plusMinutes(t.durationMinutesOr(defaultMinutes));
        return new DateTimeRange(s, e);
    }
}
