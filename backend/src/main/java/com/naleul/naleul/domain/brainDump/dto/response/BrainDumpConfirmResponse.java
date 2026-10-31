package com.naleul.naleul.domain.brainDump.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 3단계 "TimeBlock 확인" 화면 데이터.
 *
 * created=false 이면 아무것도 저장하지 않은 것 — conflicts 를 보여주고 시간을 고쳐 다시 요청한다
 * (사용자가 직접 정한 시간이 다른 잠긴 일정과 겹칠 때).
 */
public record BrainDumpConfirmResponse(
        boolean created,
        List<Placement> placements,
        List<Placement> movedToOtherDays,
        List<Unscheduled> unscheduled,
        List<Conflict> conflicts,
        List<CreatedTempGoal> tempGoals,
        List<Pending> pending
) {
    /** placeLater 요청 없이 만든 응답 (pending 없음) */
    public BrainDumpConfirmResponse(boolean created, List<Placement> placements, List<Placement> movedToOtherDays,
                                    List<Unscheduled> unscheduled, List<Conflict> conflicts, List<CreatedTempGoal> tempGoals) {
        this(created, placements, movedToOtherDays, unscheduled, conflicts, tempGoals, List.of());
    }

    /**
     * placeLater=true 일 때 저장만 하고 아직 시간을 정하지 않은 Task.
     * 화면이 이 id 들로 배치 미리보기(점선)를 받아 사용자에게 확인을 받는다. date = 처음 배치해볼 날짜
     */
    public record Pending(Long taskId, String clientKey, String title, LocalDate date) {
    }

    /** locked=true: 사용자가 정한 시간 (reason 없음) */
    public record Placement(Long taskId, String clientKey, String title, LocalDate date,
                            LocalDateTime start, LocalDateTime end, String reason, boolean locked) {
    }

    /** 빈 시간이 없어 날짜만 정해진 Task ("시간 미정") */
    public record Unscheduled(Long taskId, String clientKey, String title, LocalDate date) {
    }

    /** conflictTaskId=null 이면 이번 입력끼리 겹친 것 */
    public record Conflict(String clientKey, String title, LocalDateTime start, LocalDateTime end,
                           Long conflictTaskId, String conflictTitle) {
    }

    public record CreatedTempGoal(String tempKey, Long goalCategoryId, String name, String emoji, String colorCode) {
    }

    public static BrainDumpConfirmResponse conflicted(List<Conflict> conflicts) {
        return new BrainDumpConfirmResponse(false, List.of(), List.of(), List.of(), conflicts, List.of());
    }
}
