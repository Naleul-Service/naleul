package com.naleul.naleul.domain.work.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * 업무형 목표 상세 한 화면 (V52, V53) — GET /goal-categories/{id}/work-board
 * "오늘 뭐 해야 하지 · 다음 마감이 뭐지 · 언제까지 뭘 해야 하지"를 한 번에.
 * V53: 업무형은 목표 전체 마감이 없고 주제마다 마감이 있다 → 주제를 마감 순서대로 잇는 로드맵(timeline)을 준다.
 *
 * summary   : 남은/완료 Task · 다음 마감 주제
 * timeline  : 실제 주제를 마감 순서대로 (마감 없는 주제는 맨 뒤, 만든 순서) — 세로 선 위의 점들
 * todayTasks: 오늘 할 일 (오늘 날짜 · 밀린 것 · 오늘까지 마감 · 오늘 루틴). 위에서부터 할 순서
 * doneToday : 오늘 끝낸 것
 * upcoming  : 앞으로 2주 안에 마감인 남은 Task (마감 가까운 순)
 * topics    : 보드 필터용 주제 목록 (만든 순서 + "주제 없음"(topicId = null, Task 가 있을 때만))
 * board     : 시작 전 · 진행 중 · 완료(최근 50개) 칸 — 루틴 Task 는 넣지 않는다
 * backlog   : 날짜를 안 정한 남은 Task ("할 일 목록")
 * archive   : 완료한 Task 를 끝낸 날짜별로 (최근 archiveDays 일, 최신 날짜 먼저)
 * retrospectives : 이 목표의 최근 회고 5개 (작성 · 수정은 기존 /api/v1/retrospectives)
 */
public record WorkBoardResponse(
        Long goalCategoryId,
        String goalName,
        String emoji,
        String description,
        LocalDate today,
        Summary summary,
        List<Topic> timeline,
        List<WorkTaskItem> todayTasks,
        List<WorkTaskItem> doneToday,
        List<WorkTaskItem> upcoming,
        List<Topic> topics,
        Board board,
        List<WorkTaskItem> backlog,
        List<ArchiveDay> archive,
        List<Retro> retrospectives
) {
    /**
     * @param progressPercent 완료 / 전체 Task (Task 가 없으면 null)
     * @param topicsDone      끝난 주제 수 (마감 있는 주제 기준이 아니라 실제 주제 전체)
     * @param nextTopic       아직 안 끝난 주제 중 마감이 가장 가까운 것 (지난 것 포함, 없으면 null)
     */
    public record Summary(LocalDate startDate,
                          int total, int notStarted, int inProgress, int done, int remaining,
                          int overdue, int dueThisWeek, int backlog,
                          Integer progressPercent,
                          int topicsDone, int topicsTotal,
                          Topic nextTopic) {
    }

    /**
     * 주제 하나 (로드맵의 점 · 보드 필터 칩)
     *
     * @param dDay            마감까지 남은 날 (오늘 0, 지났으면 음수, 마감 없으면 null)
     * @param status          DONE 끝남 · OVERDUE 마감 지남 · NEXT 다음 마감 · UPCOMING 그 뒤 · NO_DUE 마감 없음
     * @param completed       사용자가 "끝냈어요"로 표시했는지 (Task 가 다 끝나도 DONE 이지만 completed 는 false 일 수 있음)
     * @param remainingPerDay 마감까지 하루에 끝내야 하는 Task 수 (안 끝났고 마감이 오늘 이후일 때만)
     */
    public record Topic(Long topicId, String name, String emoji, int sortOrder,
                        LocalDate dueDate, Integer dDay, TopicStatus status, boolean completed,
                        int total, int done, int remaining, Integer progressPercent, Double remainingPerDay) {
    }

    public enum TopicStatus { DONE, OVERDUE, NEXT, UPCOMING, NO_DUE }

    public record Board(List<WorkTaskItem> notStarted, List<WorkTaskItem> inProgress, List<WorkTaskItem> done,
                        int doneTotal) {
    }

    public record ArchiveDay(LocalDate date, int count, List<WorkTaskItem> tasks) {
    }

    public record Retro(Long retrospectiveId, LocalDate reviewDate, String reviewType, String content) {
    }
}
