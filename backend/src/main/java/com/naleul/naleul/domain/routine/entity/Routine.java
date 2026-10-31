package com.naleul.naleul.domain.routine.entity;

import com.naleul.naleul.domain.generalCategory.entity.GeneralCategory;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.routine.dto.request.RoutineDayTime;
import com.naleul.naleul.domain.routine.enums.RoutineStatus;
import com.naleul.naleul.domain.routineRepeatDay.entity.RoutineRepeatDay;
import com.naleul.naleul.domain.task.enums.TaskSource;
import com.naleul.naleul.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "routine")
public class Routine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long routineId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_category_id", nullable = false)
    private GoalCategory goalCategory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "general_category_id", nullable = false)
    private GeneralCategory generalCategory;

    @Column(nullable = false)
    private String routineName;

    // 반복 요일 (MON,WED,FRI 형태로 저장)
    // ✅ 양방향 관계 설정 - Routine이 자신의 반복요일 목록을 가짐
    @OneToMany(mappedBy = "routine", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RoutineRepeatDay> repeatDays = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoutineStatus routineStatus;

    // 반복 시간
    private LocalTime repeatStartTime;
    private LocalTime repeatEndTime;

    // 반복 기간
    @Column(nullable = false)
    private LocalDate repeatStartDate;

    @Column(nullable = false)
    private LocalDate repeatEndDate;
    private LocalDate actualEndDate;

    // ── 필드 추가 ──
    @Column(name = "notification_enabled", nullable = false)
    private boolean notificationEnabled;

    // ── AI 목표 생성용 컬럼 (BE-1) ─────────────────────────
    private String emoji;

    /**
     * AI 루틴 소요 시간(분). 시작 시각(repeatStartTime)이 없을 때 스케줄러가 사용
     */
    private Integer durationMinutes;

    /** 구체적인 방법 (AI 목표 루틴: "스쿼트 4x10, 런지 3x12…", V37). 직접 만든 루틴은 null */
    @Column(length = 300)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskSource source = TaskSource.USER_INPUT;

    // goalCategory 이동(재배정) 전용 — generalCategory가 다른 goalCategory로 옮겨질 때 사용
    public void reassignGoalCategory(GoalCategory goalCategory) {
        this.goalCategory = goalCategory;
    }

    // ========== 생성 메서드 ==========

    // @Builder 제거
    public static Routine create(
            User user,
            GoalCategory goalCategory,
            GeneralCategory generalCategory,
            String routineName,
            LocalTime repeatStartTime,
            LocalTime repeatEndTime,
            LocalDate repeatStartDate,
            LocalDate repeatEndDate,
            List<DayOfWeek> dayOfWeeks,
            boolean notificationEnabled
    ) {
        Routine routine = new Routine();
        routine.user = user;
        routine.goalCategory = goalCategory;
        routine.generalCategory = generalCategory;
        routine.routineName = routineName;
        routine.repeatStartTime = repeatStartTime;
        routine.repeatEndTime = repeatEndTime;
        routine.repeatStartDate = repeatStartDate;
        routine.repeatEndDate = repeatEndDate;
        routine.routineStatus = calculateStatusByStartDate(repeatStartDate);
        routine.notificationEnabled = notificationEnabled;

        for (DayOfWeek day : (dayOfWeeks != null ? dayOfWeeks : List.<DayOfWeek>of())) {
            routine.repeatDays.add(RoutineRepeatDay.of(routine, day));
        }

        return routine;
    }

    /**
     * AI 초안의 ROUTINE Task로 루틴 생성.
     * - preferredStartTime이 있으면 종료 시각 = 시작 + durationMinutes
     * - 없으면 시간 null (Task.fromRoutine이 시간 없는 Task로 만듦 → 이후 스케줄러가 배치)
     * - endDate가 null이면 호출하는 쪽에서 목표 종료일을 넘겨야 함 (repeat_end_date NOT NULL)
     * - notificationAllowed: 푸시 알림은 Pro 전용이라 호출하는 쪽에서 요금제를 보고 넘김
     */
    public static Routine createFromAi(User user, GoalCategory goalCategory, GeneralCategory generalCategory,
                                       String title, String emoji, int durationMinutes,
                                       List<DayOfWeek> routineDays, LocalTime preferredStartTime,
                                       LocalDate startDate, LocalDate endDate, boolean notificationAllowed) {
        return createFromAi(user, goalCategory, generalCategory, title, emoji, durationMinutes, routineDays,
                preferredStartTime, startDate, endDate, notificationAllowed, null);
    }

    public static Routine createFromAi(User user, GoalCategory goalCategory, GeneralCategory generalCategory,
                                       String title, String emoji, int durationMinutes,
                                       List<DayOfWeek> routineDays, LocalTime preferredStartTime,
                                       LocalDate startDate, LocalDate endDate, boolean notificationAllowed,
                                       String description) {
        LocalTime endTime = preferredStartTime != null
                ? preferredStartTime.plusMinutes(durationMinutes)
                : null;

        Routine routine = create(user, goalCategory, generalCategory, title,
                preferredStartTime, endTime, startDate, endDate, routineDays,
                notificationAllowed && preferredStartTime != null);   // Pro + 시각이 정해진 루틴만 알림 기본 ON
        routine.emoji = emoji;
        routine.durationMinutes = durationMinutes;
        routine.source = TaskSource.AI_GOAL;
        routine.description = description == null || description.isBlank() ? null : description.strip();
        return routine;
    }

    /** 목표 상세에서 루틴 방법 고치기. 빈 값이면 지운다 */
    public void updateDescription(String description) {
        this.description = description == null || description.isBlank() ? null : description.strip();
    }

    private static RoutineStatus calculateStatusByStartDate(LocalDate startDate) {
        if (startDate != null && startDate.isAfter(LocalDate.now())) {
            return RoutineStatus.NOT_STARTED;
        }
        return RoutineStatus.IN_PROGRESS;
    }

    public void update(GoalCategory goalCategory, GeneralCategory generalCategory,
                       String routineName, LocalDate startDate, LocalDate endDate,
                       LocalTime startTime, LocalTime endTime,
                       Boolean notificationEnabled) {
        if (goalCategory != null) {
            this.goalCategory = goalCategory;
        }
        if (generalCategory != null) {
            this.generalCategory = generalCategory;
        }
        if (routineName != null) {
            this.routineName = routineName;
        }

        LocalDate newStartDate = startDate != null ? startDate : this.repeatStartDate;

        if (startDate != null) {
            this.repeatStartDate = startDate;
        }
        if (endDate != null) {
            this.repeatEndDate = endDate;
        }
        if (startTime != null) {
            this.repeatStartTime = startTime;
        }
        if (endTime != null) {
            this.repeatEndTime = endTime;
        }
        if (notificationEnabled != null) {
            this.notificationEnabled = notificationEnabled;
        }

        if (this.routineStatus == RoutineStatus.NOT_STARTED
                || this.routineStatus == RoutineStatus.IN_PROGRESS) {
            this.routineStatus = calculateStatusByStartDate(newStartDate);
        }
    }

    /**
     * 시작일이 도래해 자동으로 진행 상태로 전환. NOT_STARTED가 아니면 아무 일도 하지 않음.
     */
    public void start() {
        if (this.routineStatus == RoutineStatus.NOT_STARTED) {
            this.routineStatus = RoutineStatus.IN_PROGRESS;
        }
    }

    // 루틴 수정 시 요일 교체
    // Routine.java

    public void updateRepeatDays(List<DayOfWeek> newDays) {
        Set<DayOfWeek> newDaySet = new HashSet<>(newDays);
        Set<DayOfWeek> existingDaySet = this.repeatDays.stream()
                .map(RoutineRepeatDay::getDayOfWeek)
                .collect(Collectors.toSet());

        // 더 이상 선택되지 않은 요일만 제거
        this.repeatDays.removeIf(rd -> !newDaySet.contains(rd.getDayOfWeek()));

        // 기존에 없던 요일만 추가
        for (DayOfWeek day : newDays) {
            if (!existingDaySet.contains(day)) {
                this.repeatDays.add(RoutineRepeatDay.of(this, day));
            }
        }
    }

    /**
     * 요일별 시간 바꾸기 (V41) — 목록에 있는 요일만 그 시간으로, 나머지 요일은 기본 시간으로 되돌린다.
     * 반복 요일이 아닌 요일은 무시한다. 빈 목록이면 모든 요일이 기본 시간.
     */
    public void applyDayTimes(List<RoutineDayTime> dayTimes) {
        for (RoutineRepeatDay rd : this.repeatDays) {
            RoutineDayTime match = dayTimes == null ? null : dayTimes.stream()
                    .filter(dt -> dt != null && dt.dayOfWeek() == rd.getDayOfWeek())
                    .findFirst().orElse(null);
            if (match != null && match.startTime() != null && match.endTime() != null) {
                rd.changeTime(match.startTime(), match.endTime());
            } else {
                rd.clearTime();
            }
        }
    }

    /** 그 요일의 시작 시각 — 요일별 시간이 있으면 그것, 없으면 기본 시간 (null 이면 시간 미정) */
    public LocalTime startTimeOn(DayOfWeek day) {
        RoutineRepeatDay rd = repeatDayOf(day);
        return rd != null && rd.hasOwnTime() ? rd.getStartTime() : this.repeatStartTime;
    }

    public LocalTime endTimeOn(DayOfWeek day) {
        RoutineRepeatDay rd = repeatDayOf(day);
        return rd != null && rd.hasOwnTime() ? rd.getEndTime() : this.repeatEndTime;
    }

    private RoutineRepeatDay repeatDayOf(DayOfWeek day) {
        return this.repeatDays.stream().filter(rd -> rd.getDayOfWeek() == day).findFirst().orElse(null);
    }

    public void complete(LocalDate actualEndDate) {
        this.routineStatus = RoutineStatus.COMPLETED;
        this.actualEndDate = actualEndDate;
    }

    /**
     * 시스템에 의한 자동 만료 처리. actualEndDate를 repeatEndDate로 채우고 상태만 전환한다.
     */
    public void expire() {
        if (this.routineStatus != RoutineStatus.DELETED
                && this.routineStatus != RoutineStatus.COMPLETED) {
            this.routineStatus = RoutineStatus.COMPLETED;
            this.actualEndDate = this.repeatEndDate;
        }
    }

    /**
     * 소프트 삭제 처리.
     */
    public void delete() {
        this.routineStatus = RoutineStatus.DELETED;
    }

}