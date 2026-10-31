package com.naleul.naleul.domain.planner.service;

import com.naleul.naleul.domain.pattern.service.TaskScheduleLogWriter;
import com.naleul.naleul.domain.planner.dto.request.FillApplyRequest;
import com.naleul.naleul.domain.planner.dto.request.TaskScheduleRequest;
import com.naleul.naleul.domain.planner.dto.response.FillApplyResponse;
import com.naleul.naleul.domain.planner.dto.response.FillPreviewResponse;
import com.naleul.naleul.domain.planner.support.PlannerClock;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.enums.TaskStatus;
import com.naleul.naleul.domain.task.service.TaskFinder;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * "AI로 TimeTable 배치하기"를 제안 → 확인 → 확정 2단계로.
 *
 * 왜: 누르자마자 블록이 여기저기 생기면 "어디에 뭐가 들어갔지?" 하고 당황한다.
 *  1) preview : 실제 배치 엔진을 그대로 돌려 결과만 모으고 트랜잭션을 롤백한다 (아무것도 저장 안 됨).
 *               → 엔진 규칙(빈 시간·집중 시간대·간격)을 그대로 쓰므로 미리보기와 확정 결과가 같다.
 *  2) apply   : 화면에서 끌어 고친 최종 자리로 저장.
 *               그대로 둔 블록 = AI 배치(잠그지 않음, 다음 자동 배치가 옮길 수 있음)
 *               고친 블록     = 사용자가 정한 시간(📌 잠금, 드래그와 같은 겹침 규칙)
 */
@Service
@RequiredArgsConstructor
public class FillProposalService {

    private final DayPlacementService dayPlacementService;
    private final TaskScheduleService taskScheduleService;
    private final TaskFinder taskFinder;
    private final TaskScheduleLogWriter scheduleLogWriter;
    private final PlannerClock clock;

    /** 저장하지 않는 미리보기 — 끝나면 트랜잭션을 롤백한다 */
    @Transactional
    public FillPreviewResponse preview(Long userId, LocalDate startDate, LocalDate endDate) {
        LocalDate today = clock.today();
        if (startDate == null || endDate == null || endDate.isBefore(startDate)
                || ChronoUnit.DAYS.between(startDate, endDate) > 6) {
            throw new CustomException(ErrorCode.TIMETABLE_INVALID_RANGE);
        }
        List<FillPreviewResponse.Proposal> proposals = new ArrayList<>();
        List<Long> unscheduled = new ArrayList<>();
        try {
            for (LocalDate d = startDate.isBefore(today) ? today : startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
                PlacementOutcome out = dayPlacementService.placeDay(userId, d, PlacementMode.FILL, null, false);
                for (PlacementOutcome.PlacedBlock b : out.placed()) {
                    proposals.add(new FillPreviewResponse.Proposal(b.taskId(), b.date(), b.start(), b.end(), b.reason()));
                }
                unscheduled.addAll(out.unscheduled());
            }
        } finally {
            // 엔진이 Task 에 써 넣은 시간·배치 기록을 모두 되돌린다
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
        return new FillPreviewResponse(proposals, unscheduled);
    }

    /**
     * 정해진 Task 들만 어디에 놓일지 미리보기 (Brain dump 로 방금 만든 Task 등) — 역시 저장하지 않고 롤백한다.
     * 각 Task 의 날짜(routineDate)에 먼저 넣어보고, 자리가 없으면 마감일까지 다른 날로 넘겨본다.
     * 이미 시간이 있거나 완료한 Task 는 건너뛴다.
     */
    @Transactional
    public FillPreviewResponse previewTasks(Long userId, List<Long> taskIds) {
        if (taskIds == null || taskIds.isEmpty() || taskIds.size() > 100) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        LocalDate today = clock.today();
        Map<LocalDate, Set<Long>> byDate = new TreeMap<>();
        List<Long> unscheduled = new ArrayList<>();
        for (Long id : new LinkedHashSet<>(taskIds)) {
            Task t = taskFinder.findTaskWithDetails(id, userId); // 다른 사람 Task 면 404
            if (t.getTaskStatus() != TaskStatus.TODO || t.isPlaced() || t.isMissionTask()) continue;
            LocalDate d = t.getRoutineDate();
            if (d == null || d.isBefore(today)) {
                unscheduled.add(id);
                continue;
            }
            byDate.computeIfAbsent(d, k -> new LinkedHashSet<>()).add(id);
        }
        List<FillPreviewResponse.Proposal> proposals = new ArrayList<>();
        try {
            byDate.forEach((date, ids) -> {
                PlacementOutcome out = dayPlacementService.placeDay(userId, date, PlacementMode.FILL, ids, true);
                for (PlacementOutcome.PlacedBlock b : out.placed()) {
                    proposals.add(new FillPreviewResponse.Proposal(b.taskId(), b.date(), b.start(), b.end(), b.reason()));
                }
                for (PlacementOutcome.PlacedBlock b : out.movedToOtherDays()) {
                    proposals.add(new FillPreviewResponse.Proposal(b.taskId(), b.date(), b.start(), b.end(), b.reason()));
                }
                unscheduled.addAll(out.unscheduled());
            });
        } finally {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
        proposals.sort(Comparator.comparing(FillPreviewResponse.Proposal::start));
        return new FillPreviewResponse(proposals, unscheduled);
    }

    /** 제안 확정. 사용자가 고친 것부터(의도 우선) 저장하고, 그대로 둔 것은 자리가 아직 비어 있을 때만 */
    @Transactional
    public FillApplyResponse apply(Long userId, FillApplyRequest request) {
        List<FillApplyRequest.Item> edited = request.items().stream().filter(FillApplyRequest.Item::edited).toList();
        List<FillApplyRequest.Item> kept = request.items().stream().filter(i -> !i.edited()).toList();
        int placed = 0;
        List<Long> skipped = new ArrayList<>();

        for (FillApplyRequest.Item i : edited) {
            Task task = placeable(userId, i.taskId());
            if (task == null) {
                skipped.add(i.taskId());
                continue;
            }
            // 나의 패턴 "AI 배치 수정률": AI 가 제안한 자리 → 사용자가 옮긴 자리로 남긴다
            task.placeByEngine(i.proposedStart(), i.proposedEnd(), i.reason(), null);
            scheduleLogWriter.enginePlaced(userId, task, null);
            taskScheduleService.reschedule(userId, i.taskId(), new TaskScheduleRequest(i.start(), i.end()), true); // 겹치면 409 → 전부 롤백
            placed++;
        }
        for (FillApplyRequest.Item i : kept) {
            Task task = placeable(userId, i.taskId());
            if (task == null || !taskScheduleService.isFree(userId, i.taskId(), i.start(), i.end())) {
                skipped.add(i.taskId());
                continue;
            }
            task.placeByEngine(i.start(), i.end(), i.reason(), null);
            scheduleLogWriter.enginePlaced(userId, task, null);
            placed++;
        }
        return new FillApplyResponse(placed, skipped);
    }

    /** 아직 시간 미정인 미완료 Task 만 확정 대상 (그사이 직접 시간을 정했거나 완료했으면 건너뜀) */
    private Task placeable(Long userId, Long taskId) {
        Task t = taskFinder.findTaskWithDetails(taskId, userId);
        if (t.getTaskStatus() != TaskStatus.TODO || t.isPlaced() || t.isMissionTask()) return null;
        return t;
    }
}
