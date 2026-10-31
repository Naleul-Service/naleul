package com.naleul.naleul.domain.task.entity;

import com.naleul.naleul.domain.work.entity.WorkTopic;
import com.naleul.naleul.domain.work.enums.WorkBoardColumn;

import com.naleul.naleul.domain.externalCalendar.enums.ExternalCalendarSource;
import com.naleul.naleul.domain.actualTask.entity.TaskActual;
import com.naleul.naleul.domain.generalCategory.entity.GeneralCategory;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.milestone.entity.Milestone;
import com.naleul.naleul.domain.mission.entity.Mission;
import com.naleul.naleul.domain.mission.entity.MissionParticipant;
import com.naleul.naleul.domain.mission.entity.MissionTimeRange;
import com.naleul.naleul.domain.routine.entity.Routine;
import com.naleul.naleul.domain.task.enums.PlacedBy;
import com.naleul.naleul.domain.task.enums.TaskKind;
import com.naleul.naleul.domain.task.enums.TaskPriority;
import com.naleul.naleul.domain.task.enums.TaskSource;
import com.naleul.naleul.domain.task.enums.TaskStatus;
import com.naleul.naleul.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "task")
public class Task {

    /** planned_start_at 등 Task 시각은 한국 시간 벽시계 값으로 저장한다 (명세 3 · 2장) */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long taskId;

    // ✅ 루틴에서 생성된 Task인지 구분하기 위해 추가
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "routine_id", nullable = true) // 일반 Task는 null
    private Routine routine;

    // ✅ 이 Task가 어느 날짜의 루틴 인스턴스인지
    private LocalDate routineDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus taskStatus; // TODO, COMPLETED, DELETED 등

    @Column(nullable = false)
    private String taskName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskPriority taskPriority;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_category_id")
    private GoalCategory goalCategory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "general_category_id")
    private GeneralCategory generalCategory;

    private LocalDateTime plannedStartAt;
    private LocalDateTime plannedEndAt;
    private Long plannedDurationMinutes;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean defaultSettingStatus;

    @Builder.Default
    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TaskActual> taskActuals = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mission_participant_id", nullable = true)
    private MissionParticipant missionParticipant;  // ← 필드 추가

    // ✅ 이 Task에 대해 시작 시간 알림(푸시)을 보낼지 여부
    @Builder.Default
    @Column(name = "notification_enabled", nullable = false, columnDefinition = "boolean default true")
    private boolean notificationEnabled = true;

    // ✅ 시작 시간 알림을 이미 보냈는지 (중복 발송 방지용)
    @Column(name = "start_notified", nullable = false)
    private boolean startNotified = false;

    public void markStartNotified() {
        this.startNotified = true;
    }

    public void reassignGoalCategory(GoalCategory goalCategory) {
        this.goalCategory = goalCategory;
    }

    // ── AI 목표 생성용 컬럼 (BE-1) — ONE_TIME Task 역할 ─────
    /**
     * ONE_TIME: 어느 마일스톤 구간에 끝내야 하는지
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "milestone_id")
    private Milestone milestone;

    private String emoji;

    /**
     * ONE_TIME 권장 실행일
     */
    private LocalDate scheduledDate;

    /**
     * ONE_TIME 마감일
     */
    private LocalDate dueDate;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskSource source = TaskSource.USER_INPUT;

    // 미션 실제 시간
    private LocalDateTime actualStartAt;
    private LocalDateTime actualEndAt;

    // ── TimeBlock 자동 배치용 컬럼 (명세 3 · V34) ─────────────
    /**
     * 사용자가 시간을 직접 정했거나 직접 옮긴 Task. 자동 배치(22시 생성·다시 배치)가 절대 옮기지 않는다.
     */
    @Column(nullable = false)
    private boolean scheduleLocked;

    /** 시간을 누가 정했는지. 시간이 없으면 null */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private PlacedBy placedBy;

    /** 사용자에게 보여주는 배치 이유 (엔진 배치일 때만). "왜 이 시간" + 내 기록 근거 두 문장이라 500자 */
    @Column(length = 500)
    private String placementReason;

    /** 배치 이유 코드 (쉼표 구분, 예: "DEADLINE_SOON,HIGH_EFFICIENCY") */
    @Column(length = 200)
    private String placementReasonCodes;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskKind taskKind = TaskKind.NORMAL;

    /** 못 해서 다음 날로 넘어간 횟수 */
    @Column(nullable = false)
    private int carryOverCount;

    /**
     * 22시 이월로 다른 날에 놓였지만 사용자가 아직 확인하지 않음.
     * 화면은 점선 블록 + "어제 못한 ○○를 ○시로 옮길까요?" 를 보여주고, 옮기기/안 옮기기를 고르면 false.
     * (말없이 옮기면 "내가 잘못 넣었나?" 하고 헷갈려서, 이월은 제안으로만 둔다)
     */
    @Column(nullable = false)
    private boolean carryPending;

    /**
     * 연속된 빈 시간이 없어 고정 시간 앞뒤로 나눠 배치했는지 (V65). 각 구간은 task_planned_segment.
     * 이때 planned_start_at ~ planned_end_at 은 첫 시작 ~ 마지막 끝이고, planned_duration_minutes 는 구간 합계다.
     * 시간을 바꾸거나 비우면 false 로 돌아간다.
     */
    @Column(nullable = false)
    private boolean plannedSplit;

    /** 이월되기 전 날짜 ("어제 못한" 문구용). 이월된 적 없으면 null */
    private LocalDate carriedFromDate;

    /** 완료 버튼을 누른 시각 (한국 시간). 시간대별 효율 계산에 쓴다 */
    private LocalDateTime completedAt;

    /** 외부 캘린더에서 가져온 Task 의 출처 (V50). 직접 만든 Task 는 null */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private ExternalCalendarSource externalSource;

    /** 외부 캘린더의 일정 id (V50) — 같은 일정을 두 번 가져오지 않게 (user, externalSource, externalEventId) 유니크 */
    @Column(length = 255)
    private String externalEventId;

    // ── 업무형 보드 (V52) ───────────────────────────────────
    /** 업무형 목표 안의 주제 (없으면 "주제 없음") */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_topic_id")
    private WorkTopic workTopic;

    /**
     * 보드에서 "진행 중"으로 옮겼는지. null · NOT_STARTED = 시작 전, IN_PROGRESS = 진행 중.
     * 완료는 taskStatus = COMPLETED 로 판단한다 (boardColumn()).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "work_status", length = 20)
    private WorkBoardColumn workStatus;

    /** 업무 Task 메모 (선택, 최대 500자) — 공유 텍스트에도 들어간다 */
    @Column(name = "work_memo", length = 500)
    private String workMemo;

    /** 생성 시각 (한국 시간). Free 일일 생성 한도 집계용. V34 이전 데이터는 null */
    @Column(updatable = false)
    private LocalDateTime createdAt;

    /**
     * 저장 직전 공통 처리. 생성 경로가 여러 곳(TaskService 빌더, 루틴, 미션, AI 목표)이라 여기서 한 번에 맞춘다.
     * - createdAt 기록
     * - 시간을 가진 채 처음 저장되는데 누가 정했는지 비어 있으면 = 사용자가 정한 시간 → 잠금
     *   (엔진이 배치한 Task 는 placeByEngine 에서 placedBy = ENGINE 이 먼저 들어가므로 해당 없음)
     * - DB NOT NULL 컬럼 안전장치 (@NoArgsConstructor 경로에서 기본값이 빠지는 경우 대비)
     */
    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now(KST);
        }
        if (this.plannedStartAt != null && this.placedBy == null) {
            markScheduledByUser();
        }
        if (this.taskKind == null) {
            this.taskKind = TaskKind.NORMAL;
        }
        if (this.source == null) {
            this.source = TaskSource.USER_INPUT;
        }
    }

    // ── 도메인 메서드 ──────────────────────────────────────

    // ✅ 루틴으로부터 Task 생성하는 팩토리 메서드
    public static Task fromRoutine(Routine routine, LocalDate date) {
        Task task = new Task();
        task.routine = routine;
        task.routineDate = date;
        task.taskName = routine.getRoutineName();
        task.user = routine.getUser();
        task.goalCategory = routine.getGoalCategory();
        task.generalCategory = routine.getGeneralCategory();
        task.taskStatus = TaskStatus.TODO;
        task.taskPriority = TaskPriority.DEFAULT;
        task.notificationEnabled = routine.isNotificationEnabled();
        task.emoji = routine.getEmoji();
        task.source = routine.getSource();
        // 루틴의 시간을 해당 날짜의 LocalDateTime으로 변환 (요일별 시간이 있으면 그 요일 시간, V41)
        LocalTime start = routine.startTimeOn(date.getDayOfWeek());
        LocalTime end = routine.endTimeOn(date.getDayOfWeek());
        if (start != null && end != null) {
            task.plannedStartAt = date.atTime(start);
            // 끝이 시작보다 이르거나 같으면 자정을 넘기는 루틴 (예: 23:00 ~ 00:00, 23:30 ~ 01:00) → 다음 날 그 시각
            task.plannedEndAt = end.isAfter(start) ? date.atTime(end) : date.plusDays(1).atTime(end);
            task.plannedDurationMinutes = Duration.between(
                    task.plannedStartAt, task.plannedEndAt).toMinutes();
            // 루틴에 정해진 시간 = 사용자가 정한 시간 → 잠금
            task.markScheduledByUser();
        } else if (routine.getDurationMinutes() != null) {
            // 시간 없는 AI 루틴: 소요 시간만 넣어두고 배치 엔진이 시간을 정한다
            task.plannedDurationMinutes = routine.getDurationMinutes().longValue();
        }
        task.defaultSettingStatus = false;
        return task;
    }

    // 미션 완료 처리 시 실제 시간도 같이 기록하는 메서드
    public void completeWithActualTime(LocalDateTime actualStartAt, LocalDateTime actualEndAt) {
        this.taskStatus = TaskStatus.COMPLETED;
        this.actualStartAt = actualStartAt;
        this.actualEndAt = actualEndAt;
        this.completedAt = LocalDateTime.now(KST);
    }

    /**
     * 외부 캘린더 일정 → Task.
     *  - 미팅 · 약속 성격이라 APPOINTMENT (자동 배치가 옮기지 않고, 못 해도 다음 날로 이월하지 않음)
     *  - 시간 일정: 그 시각에 잠금 (사용자가 정한 시간과 같게 취급, @PrePersist)
     *  - 하루 종일 일정: 그 날짜의 "시간 미정" 목록 (routineDate = 날짜, 기존 일간 조회 쿼리 그대로)
     *  - 시작 알림은 끈 채로 (Pro 전용 기능이고, 원래 캘린더 앱이 이미 알려줌)
     */
    public static Task fromExternalEvent(User user, GoalCategory goalCategory, ExternalCalendarSource source,
                                         String externalEventId, String title, boolean allDay, LocalDate date,
                                         LocalDateTime start, LocalDateTime end) {
        return Task.builder()
                .user(user)
                .goalCategory(goalCategory)
                .taskName(title)
                .taskPriority(TaskPriority.C)
                .taskStatus(TaskStatus.TODO)
                .taskKind(TaskKind.APPOINTMENT)
                .source(TaskSource.EXTERNAL_CALENDAR)
                .externalSource(source)
                .externalEventId(externalEventId)
                .routineDate(date)
                .scheduledDate(date)
                .plannedStartAt(allDay ? null : start)
                .plannedEndAt(allDay ? null : end)
                .plannedDurationMinutes(allDay ? null : Duration.between(start, end).toMinutes())
                .notificationEnabled(false)
                .defaultSettingStatus(false)
                .build();
    }

    // ✅ 일반 Task 생성 (기존 방식)
    public static Task create(User user, GoalCategory goalCategory,
                              GeneralCategory generalCategory, String taskName,
                              TaskPriority taskPriority,
                              LocalDateTime plannedStartAt, LocalDateTime plannedEndAt) {
        Task task = new Task();
        task.user = user;
        task.goalCategory = goalCategory;
        task.generalCategory = generalCategory;
        task.taskName = taskName;
        task.taskPriority = taskPriority;
        task.plannedStartAt = plannedStartAt;
        task.plannedEndAt = plannedEndAt;
        task.taskStatus = TaskStatus.TODO;
        if (plannedStartAt != null && plannedEndAt != null) {
            task.plannedDurationMinutes = Duration.between(plannedStartAt, plannedEndAt).toMinutes();
        }
        if (plannedStartAt != null) {
            task.markScheduledByUser();
        }
        return task;
    }

    /**
     * AI 초안의 ONE_TIME Task 생성.
     * 실행 시각은 아직 정하지 않음(plannedStartAt = null) — TimeBlock 배치 모듈(3번 명세)이 나중에 채운다.
     * 소요 시간만 plannedDurationMinutes에 미리 넣어둔다.
     * <p>
     * routineDate에 권장 실행일을 넣는 이유:
     * 기존 일간/주간/월간 조회 쿼리가 "plannedStartAt IS NULL AND routineDate = 날짜" 조건으로
     * 시간 없는 Task를 그 날짜에 보여준다. 쿼리를 고치지 않고도 AI 일회성 Task가 해당 날짜 화면에 나온다.
     */
    public static Task createOneTimeFromAi(User user, GoalCategory goalCategory, GeneralCategory generalCategory,
                                           Milestone milestone, String title, String emoji,
                                           int durationMinutes, LocalDate scheduledDate, LocalDate dueDate) {
        Task task = new Task();
        task.user = user;
        task.goalCategory = goalCategory;
        task.generalCategory = generalCategory;
        task.milestone = milestone;
        task.taskName = title;
        task.emoji = emoji;
        task.taskPriority = TaskPriority.DEFAULT;
        task.taskStatus = TaskStatus.TODO;
        task.plannedDurationMinutes = (long) durationMinutes;
        task.scheduledDate = scheduledDate;
        task.dueDate = dueDate;
        task.routineDate = scheduledDate != null ? scheduledDate : dueDate;
        task.source = TaskSource.AI_GOAL;
        task.defaultSettingStatus = false;
        return task;
    }

    /**
     * 목표 상세에서 직접 추가한 Task.
     * AI 일회성 Task(createOneTimeFromAi)와 같은 모양으로 만든다: 날짜만 정하고(routineDate = scheduledDate = date)
     * 시간은 비워 둔 채 소요 시간만 넣어 두면 자동 배치가 시간을 정한다. 시간을 바로 정하려면 저장 후 scheduleByUser.
     */
    public static Task createForGoal(User user, GoalCategory goalCategory, GeneralCategory generalCategory,
                                     Milestone milestone, String title, String emoji,
                                     LocalDate date, Integer durationMinutes, LocalDate dueDate) {
        Task task = new Task();
        task.user = user;
        task.goalCategory = goalCategory;
        task.generalCategory = generalCategory;
        task.milestone = milestone;
        task.taskName = title;
        task.emoji = emoji;
        task.taskPriority = TaskPriority.DEFAULT;
        task.taskStatus = TaskStatus.TODO;
        task.plannedDurationMinutes = durationMinutes != null && durationMinutes > 0 ? durationMinutes.longValue() : null;
        task.scheduledDate = date;
        task.routineDate = date;
        task.dueDate = dueDate;
        task.source = TaskSource.USER_INPUT;
        task.defaultSettingStatus = false;
        task.notificationEnabled = true;
        return task;
    }

    /**
     * 목표 상세에서 Task 내용 수정 (시간은 TaskScheduleService 가 따로 다룬다).
     * 화면이 폼 전체를 보내므로 null 도 그대로 저장한다 (emoji·milestone·dueDate 를 지울 수 있게).
     */
    public void editFromGoal(String title, String emoji, GeneralCategory generalCategory, Milestone milestone,
                             LocalDate dueDate) {
        this.taskName = title;
        this.emoji = emoji;
        this.generalCategory = generalCategory;
        this.milestone = milestone;
        this.dueDate = dueDate;
    }

    /**
     * 업무형 보드에서 추가한 Task (V52).
     * date 가 있으면 createForGoal 과 같은 모양(그날 "시간 미정" → 자동 배치)이고,
     * date 가 없으면 캘린더에 나오지 않는 "할 일 목록" Task 다 (routineDate · scheduledDate · 시간 모두 null).
     * 날짜 없는 Task 는 배치 · 이월 · 일간 조회 쿼리가 모두 날짜 조건이라 자연스럽게 빠진다.
     */
    public static Task createWorkTask(User user, GoalCategory goalCategory, GeneralCategory bucket, WorkTopic topic,
                                      String title, LocalDate date, LocalDate dueDate, String memo) {
        Task task = createForGoal(user, goalCategory, bucket, null, title, null, date, null, dueDate);
        task.workTopic = topic;
        task.workMemo = memo == null || memo.isBlank() ? null : memo.strip();
        task.workStatus = WorkBoardColumn.NOT_STARTED;
        return task;
    }

    /** 보드 칸: 완료 → DONE, 진행 중으로 옮겼으면 IN_PROGRESS, 나머지는 NOT_STARTED */
    public WorkBoardColumn boardColumn() {
        if (this.taskStatus == TaskStatus.COMPLETED) return WorkBoardColumn.DONE;
        return this.workStatus == WorkBoardColumn.IN_PROGRESS ? WorkBoardColumn.IN_PROGRESS : WorkBoardColumn.NOT_STARTED;
    }

    /**
     * 보드에서 칸 옮기기의 "시작 전 / 진행 중" 부분. DONE 은 complete(), DONE 에서 되돌릴 때는 cancelComplete() 를
     * 서비스가 먼저 부른다 (실제 시간 기록 초기화까지 같이 해야 해서).
     */
    public void markWorkStatus(WorkBoardColumn column) {
        this.workStatus = column == WorkBoardColumn.IN_PROGRESS ? WorkBoardColumn.IN_PROGRESS : WorkBoardColumn.NOT_STARTED;
    }

    /** 업무 Task 내용 수정 — null 이면 그대로. clear 플래그로 주제 · 마감일 · 메모를 지운다 */
    public void editWork(String title, WorkTopic topic, boolean clearTopic, LocalDate dueDate, boolean clearDueDate,
                         String memo) {
        if (title != null && !title.isBlank()) this.taskName = title.strip();
        if (clearTopic) this.workTopic = null;
        else if (topic != null) this.workTopic = topic;
        if (clearDueDate) this.dueDate = null;
        else if (dueDate != null) this.dueDate = dueDate;
        if (memo != null) this.workMemo = memo.isBlank() ? null : memo.strip();
    }

    /** 주제를 지울 때 그 주제의 Task 는 "주제 없음"으로 */
    public void clearWorkTopic() {
        this.workTopic = null;
    }

    /** 날짜를 빼서 "할 일 목록"으로 (시간이 정해져 있으면 서비스가 먼저 unschedule 한다) */
    public void moveToBacklog() {
        if (this.plannedStartAt != null) return;
        this.routineDate = null;
        this.scheduledDate = null;
    }

    /** 보드 · 화면에 쓸 이 Task 의 날짜: 시간이 정해졌으면 그 날, 아니면 routineDate → scheduledDate */
    public LocalDate plannedDate() {
        if (this.plannedStartAt != null) return this.plannedStartAt.toLocalDate();
        return this.routineDate != null ? this.routineDate : this.scheduledDate;
    }

    /** 시간 미정 Task 의 날짜 · 소요 시간 바꾸기 (시간이 정해진 Task 는 scheduleByUser 로 옮긴다) */
    public void moveUnscheduledTo(LocalDate date, Integer durationMinutes) {
        if (this.plannedStartAt != null) return;
        this.routineDate = date;
        this.scheduledDate = date;
        if (durationMinutes != null && durationMinutes > 0) this.plannedDurationMinutes = durationMinutes.longValue();
    }

    // ✅ 완료 처리
    public void complete() {
        this.taskStatus = TaskStatus.COMPLETED;
        this.completedAt = LocalDateTime.now(KST);
        this.carryPending = false; // 했으면 더 물어볼 필요 없음
    }

    // ✅ 완료 취소
    public void cancelComplete() {
        this.taskStatus = TaskStatus.TODO;
        this.completedAt = null;
    }

    /**
     * 우선순위만 바꾼다 (TimeBlock 상세 · 2026-10).
     * 루틴 · 미션 Task 도 그날 인스턴스만 바뀌고, 다음 날 새로 만들어지는 Task 는 기본값(C)으로 시작한다.
     * 시간 · 날짜는 건드리지 않으므로 일정 변경 로그(TaskScheduleLog)도 남기지 않는다.
     */
    public void changePriority(TaskPriority priority) {
        if (priority == null) return;
        this.taskPriority = priority;
    }

    public void update(String name, TaskPriority priority,
                       GoalCategory goalCategory, GeneralCategory generalCategory,
                       LocalDateTime plannedStartAt, LocalDateTime plannedEndAt,
                       boolean defaultSettingStatus, boolean notificationEnabled) {

        boolean startTimeChanged = this.plannedStartAt == null
                ? plannedStartAt != null
                : !this.plannedStartAt.equals(plannedStartAt);
        boolean turnedOn = !this.notificationEnabled && notificationEnabled;
        boolean keepSplit = keepsSplit(plannedStartAt, plannedEndAt);

        this.taskName = name;
        this.taskPriority = priority;
        this.goalCategory = goalCategory;
        this.generalCategory = generalCategory;
        this.plannedStartAt = plannedStartAt;
        this.plannedEndAt = plannedEndAt;

        // ✅ 시간이 있으면 재계산, 없으면 null로 명시적 초기화
        //    나눠 넣은 블록인데 시각이 그대로면 구간 합계 길이를 지킨다 (첫 시작~마지막 끝으로 다시 재면 점심 시간만큼 늘어남)
        if (keepSplit) {
            // 그대로 둔다
        } else if (plannedStartAt != null && plannedEndAt != null) {
            this.plannedDurationMinutes = Duration.between(plannedStartAt, plannedEndAt).toMinutes();
        } else {
            this.plannedDurationMinutes = null;
        }

        this.defaultSettingStatus = defaultSettingStatus;
        this.notificationEnabled = notificationEnabled;

        if (startTimeChanged || turnedOn) {
            this.startNotified = false;
        }

        // 사용자가 시간을 정했으면 잠금, 시간을 지웠으면 잠금 해제 (명세 3-4)
        if (startTimeChanged) {
            if (plannedStartAt != null) {
                markScheduledByUser();
            } else {
                clearPlacement();
            }
        }
    }

    // Task.java 안에 추가
    public void updateSchedule(LocalDateTime plannedStartAt, LocalDateTime plannedEndAt) {
        boolean startTimeChanged = this.plannedStartAt == null
                ? plannedStartAt != null
                : !this.plannedStartAt.equals(plannedStartAt);
        boolean keepSplit = keepsSplit(plannedStartAt, plannedEndAt);

        this.plannedStartAt = plannedStartAt;
        this.plannedEndAt = plannedEndAt;

        // ✅ 둘 다 있을 때만 duration 계산, 하나라도 없으면 null로 초기화 (나눠 넣은 블록이 그대로면 구간 합계 유지)
        if (keepSplit) {
            // 그대로 둔다
        } else if (plannedStartAt != null && plannedEndAt != null) {
            this.plannedDurationMinutes = Duration.between(plannedStartAt, plannedEndAt).toMinutes();
        } else {
            this.plannedDurationMinutes = null;
        }

        if (startTimeChanged) {
            this.startNotified = false;
            if (plannedStartAt != null) {
                markScheduledByUser();
            } else {
                clearPlacement();
            }
        }
    }

    // ── TimeBlock 배치 (명세 3 · 6장) ─────────────────────────

    /**
     * 사용자가 드래그·직접 입력으로 시간을 정함 → 잠금.
     * 일반 Task는 routineDate 도 새 날짜로 맞춘다 (시간을 다시 지워도 그 날짜 목록에 남도록).
     * 루틴/미션 Task 의 routineDate 는 "몇 일자 인스턴스인지"라 바꾸지 않는다.
     */
    public void scheduleByUser(LocalDateTime start, LocalDateTime end) {
        requireRange(start, end);
        changeTimes(start, end);
        markScheduledByUser();
        this.carryPending = false; // 직접 시간을 정했으면 이월 제안에 답한 것으로 본다
        if (isOneOff()) {
            this.routineDate = start.toLocalDate();
        }
    }

    /**
     * 배치 엔진이 시간을 정함. 잠긴 Task 는 엔진이 건드리면 안 되므로 예외.
     */
    public void placeByEngine(LocalDateTime start, LocalDateTime end, String reason, String reasonCodes) {
        if (this.scheduleLocked) {
            throw new IllegalStateException("locked task cannot be placed by engine: " + taskId);
        }
        requireRange(start, end);
        changeTimes(start, end);
        this.placedBy = PlacedBy.ENGINE;
        this.placementReason = reason;
        this.placementReasonCodes = reasonCodes;
        if (isOneOff()) {
            this.routineDate = start.toLocalDate();
        }
    }

    /**
     * 시간만 비움 (날짜·소요 시간은 유지) → 그 날짜의 "시간 미정" 목록으로 돌아감.
     * 엔진이 다시 배치하기 전에 호출한다.
     */
    public void unplace() {
        // 나눠 넣은 Task 는 첫 시작 ~ 마지막 끝에 쉬는 구간이 섞여 있어서, 길이는 저장해 둔 구간 합계를 그대로 쓴다
        if (!this.plannedSplit && this.plannedStartAt != null && this.plannedEndAt != null) {
            this.plannedDurationMinutes = Duration.between(this.plannedStartAt, this.plannedEndAt).toMinutes();
        }
        this.plannedSplit = false;
        this.plannedStartAt = null;
        this.plannedEndAt = null;
        this.startNotified = false;
        clearPlacement();
    }

    /** 잠금 해제 → 다음 배치 때 엔진이 옮길 수 있음 */
    public void unlock() {
        this.scheduleLocked = false;
    }

    /**
     * 못 한 Task 를 다른 날로 이월 (22시 작업). 시간은 비우고 엔진이 다시 배치한다.
     */
    public void carryOverTo(LocalDate date) {
        LocalDate from = this.plannedStartAt != null ? this.plannedStartAt.toLocalDate() : this.routineDate;
        unplace();
        this.carriedFromDate = from;
        this.carryPending = true;
        this.routineDate = date;
        if (this.scheduledDate != null) {
            this.scheduledDate = date;
        }
        this.carryOverCount++;
    }

    /**
     * 이월 되돌리기 (2026-10) — 원래 날짜로 돌아간다. 시간은 서비스가 원래 시각으로 다시 정한다 (자리가 없으면 그날 시간 미정).
     * carriedFromDate 는 원래 날짜 그대로 남겨 "되돌린 Task" 표시로 쓴다 (날짜 == carriedFromDate) → 자동 이월이 다시 옮기지 않는다.
     */
    public void revertCarry() {
        if (this.carriedFromDate == null) return;
        revertCarryTo(this.carriedFromDate);
    }

    /**
     * 이월 흔적이 있는 날로 되돌린다 (2026-10). 여러 번 이월됐으면 어느 흔적에서든 그날로 돌아갈 수 있다.
     * carriedFromDate 를 그날로 맞춰 "되돌린 Task" 로 표시한다 → 자동 이월이 다시 옮기지 않는다.
     */
    public void revertCarryTo(LocalDate back) {
        if (back == null) return;
        unplace();
        this.carryPending = false;
        this.carriedFromDate = back;
        this.routineDate = back;
        if (this.scheduledDate != null) this.scheduledDate = back;
        if (this.carryOverCount > 0) this.carryOverCount--;
    }

    /** 지금 있는 날 (시간이 있으면 그 날, 없으면 routineDate) */
    public LocalDate currentDate() {
        return this.plannedStartAt != null ? this.plannedStartAt.toLocalDate() : this.routineDate;
    }

    /** 이월을 되돌려 원래 날짜에 있는 Task (자동 이월이 다시 옮기지 않는다) */
    public boolean isCarryReverted() {
        if (this.carriedFromDate == null) return false;
        LocalDate on = this.plannedStartAt != null ? this.plannedStartAt.toLocalDate() : this.routineDate;
        return this.carriedFromDate.equals(on);
    }

    /** 이월 제안 "옮기기" — 엔진이 잡아 둔 자리 그대로 확정 */
    public void acceptCarry() {
        this.carryPending = false;
    }

    /** 이월 제안 "안 옮기기" — 자동으로 잡은 시간을 비우고 그날 "시간 미정"에 둔다 (사용자가 직접 정하거나 지우게) */
    public void declineCarry() {
        if (!this.scheduleLocked) unplace();
        this.carryPending = false;
    }

    public void changeKind(TaskKind kind) {
        this.taskKind = kind != null ? kind : TaskKind.NORMAL;
    }

    public boolean isPlaced() {
        return this.plannedStartAt != null;
    }

    public boolean isRoutineTask() {
        return this.routine != null;
    }

    public boolean isMissionTask() {
        return this.missionParticipant != null;
    }

    /** 루틴·미션이 아닌 일회성 Task */
    public boolean isOneOff() {
        return this.routine == null && this.missionParticipant == null;
    }

    /** 배치에 쓸 소요 시간. 모르면 기본값 */
    public int durationMinutesOr(int defaultMinutes) {
        if (this.plannedDurationMinutes == null || this.plannedDurationMinutes <= 0) {
            return defaultMinutes;
        }
        return (int) Math.min(this.plannedDurationMinutes, 24 * 60);
    }

    private void changeTimes(LocalDateTime start, LocalDateTime end) {
        if (this.plannedStartAt == null || !this.plannedStartAt.equals(start)) {
            this.startNotified = false;
        }
        this.plannedStartAt = start;
        this.plannedEndAt = end;
        this.plannedDurationMinutes = Duration.between(start, end).toMinutes();
        this.plannedSplit = false;   // 시간을 새로 정하면 한 덩어리 (나눠 넣을 땐 아래 markPlannedSplit 을 이어서 부른다)
    }

    /**
     * 나눠 넣은 블록(plannedSplit)의 시각이 그대로인지. 그대로면 true (구간·길이 유지),
     * 바뀌면 plannedSplit 을 끄고 false — 낡은 구간 행은 segmentsOf 에서 무시된다.
     */
    private boolean keepsSplit(LocalDateTime newStart, LocalDateTime newEnd) {
        if (!this.plannedSplit) return false;
        boolean same = java.util.Objects.equals(this.plannedStartAt, newStart)
                && java.util.Objects.equals(this.plannedEndAt, newEnd);
        if (!same) this.plannedSplit = false;
        return same;
    }

    /**
     * 엔진이 나눠 넣었음을 표시 (placeByEngine 바로 뒤에 부른다).
     * @param totalMinutes 구간 길이의 합 (첫 시작 ~ 마지막 끝이 아니라)
     */
    public void markPlannedSplit(long totalMinutes) {
        this.plannedSplit = true;
        this.plannedDurationMinutes = totalMinutes;
    }

    private void markScheduledByUser() {
        this.scheduleLocked = true;
        this.placedBy = PlacedBy.USER;
        this.placementReason = null;
        this.placementReasonCodes = null;
    }

    private void clearPlacement() {
        this.scheduleLocked = false;
        this.placedBy = null;
        this.placementReason = null;
        this.placementReasonCodes = null;
    }

    private static void requireRange(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new IllegalArgumentException("invalid time range: " + start + " ~ " + end);
        }
    }

    // ✅ 미션으로부터 Task 생성하는 팩토리 메서드
    public static Task fromMission(MissionParticipant participant, LocalDate date) {
        Mission mission = participant.getMission();
        User user = participant.getUser();

        Task task = new Task();
        task.missionParticipant = participant;
        task.routineDate = date;
        task.taskName = mission.getMissionTitle();
        task.user = user;
        task.goalCategory = null;     // 미션은 카테고리 없음
        task.generalCategory = null;
        task.taskStatus = TaskStatus.TODO;
        task.taskPriority = TaskPriority.DEFAULT;

        MissionTimeRange timeRange = mission.getTimeRange();
        if (timeRange != null) {
            LocalTime startTime = timeRange.getStartTime();
            LocalTime endTime = timeRange.getEndTime();

            if (startTime != null) {
                task.plannedStartAt = date.atTime(startTime);
            }
            if (endTime != null) {
                task.plannedEndAt = date.atTime(endTime);
            }
            // 시작/종료 시간이 둘 다 있을 때만 소요시간 계산 가능
            if (task.plannedStartAt != null && task.plannedEndAt != null) {
                task.plannedDurationMinutes = Duration.between(
                        task.plannedStartAt, task.plannedEndAt).toMinutes();
            }
            // 미션 시간은 방장이 정한 고정 시간 → 자동 배치가 옮기지 않도록 잠금
            if (task.plannedStartAt != null) {
                task.markScheduledByUser();
            }
        }

        task.defaultSettingStatus = false;
        return task;
    }
}
