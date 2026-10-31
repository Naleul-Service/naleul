package com.naleul.naleul.domain.habit.entity;

import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.habit.enums.HabitStatus;
import com.naleul.naleul.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 기본 생활 습관 하나 (V52). 예) 영양제 먹기 · 샤워하기 · 물 마시기 · 금연
 *
 * 왜 루틴(Routine)이 아닌 별도 테이블인가
 *  - 루틴은 기간 전체의 Task 를 미리 만들어 두는 구조라 "종료일 없는 습관"과 맞지 않는다.
 *  - 루틴 Task 는 자동 배치 엔진이 시간표에 30분 블록으로 끼워 넣고, 실행률 · 나의 패턴 · AI 계획 상한 계산에도 들어간다.
 *    "샤워하기" 같은 체크 항목이 이런 통계를 흐리지 않도록, 습관은 "그날 했는지"만 habit_check 에 남긴다.
 *
 * repeatDays 가 비어 있으면 매일. remindTime 이 있으면 그 시각에 아직 안 했을 때 푸시를 보낸다 (HabitReminderScheduler).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "habit")
public class Habit {

    public static final int NAME_MAX = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long habitId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 기본 생활 습관 그릇 (goal_mode = HABIT) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_category_id", nullable = false)
    private GoalCategory goalCategory;

    @Column(nullable = false, length = NAME_MAX)
    private String habitName;

    @Column(length = 16)
    private String emoji;

    /** "MONDAY,WEDNESDAY,FRIDAY" — 비어 있으면 매일 */
    @Column(nullable = false, length = 80)
    private String repeatDays = "";

    /** 알림 시각 (선택) */
    private LocalTime remindTime;

    /** 하는 시간대 (선택, V56) — 있으면 캘린더에 블록으로 보이고 자동 배치가 피한다. 둘 다 있거나 둘 다 없음 */
    private LocalTime startTime;
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HabitStatus habitStatus = HabitStatus.ACTIVE;

    /** 화면 순서 (작을수록 위) */
    @Column(nullable = false)
    private int sortOrder;

    /** 이 날부터 체크 대상 (잔디맵에서 이전 날은 BEFORE_START) */
    @Column(nullable = false)
    private LocalDate startDate;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static Habit create(User user, GoalCategory home, String name, String emoji,
                               Collection<DayOfWeek> repeatDays, LocalTime remindTime, int sortOrder, LocalDate today) {
        Habit h = new Habit();
        h.user = user;
        h.goalCategory = home;
        h.habitName = name.strip();
        h.emoji = blankToNull(emoji);
        h.repeatDays = encode(repeatDays);
        h.remindTime = truncate(remindTime);
        h.sortOrder = sortOrder;
        h.startDate = today;
        h.habitStatus = HabitStatus.ACTIVE;
        return h;
    }

    // ── 조회 ──────────────────────────────────────────────

    public Set<DayOfWeek> repeatDaySet() {
        return decode(repeatDays);
    }

    /** 반복 요일 순서대로 (비어 있으면 매일) */
    public List<DayOfWeek> repeatDayList() {
        return repeatDaySet().stream().sorted().toList();
    }

    public boolean isDaily() {
        return repeatDaySet().isEmpty();
    }

    /** 이 요일이 해야 하는 날인지 (매일이면 항상 true) */
    public boolean isRepeatDay(DayOfWeek day) {
        Set<DayOfWeek> days = repeatDaySet();
        return days.isEmpty() || days.contains(day);
    }

    public boolean hasTimeRange() {
        return startTime != null && endTime != null;
    }

    /** 시간대 정하기 (start · end 둘 다 null 이면 지움). 끝이 시작보다 뒤여야 한다 — 호출 전에 검사 */
    public void changeTimeRange(LocalTime start, LocalTime end) {
        if (start == null || end == null) {
            this.startTime = null;
            this.endTime = null;
        } else {
            this.startTime = truncate(start);
            this.endTime = truncate(end);
        }
    }

    public static boolean isValidTimeRange(LocalTime start, LocalTime end) {
        if (start == null && end == null) return true;
        return start != null && end != null && end.isAfter(start);
    }

    public boolean isActive() {
        return habitStatus == HabitStatus.ACTIVE;
    }

    public boolean isPaused() {
        return habitStatus == HabitStatus.PAUSED;
    }

    // ── 변경 ──────────────────────────────────────────────

    /** null 인 값은 그대로. emoji 는 "" 이면 지운다. clearRemindTime 이면 알림 시각을 지운다 */
    public void update(String name, String emoji, Collection<DayOfWeek> repeatDays,
                       LocalTime remindTime, boolean clearRemindTime) {
        if (name != null && !name.isBlank()) this.habitName = name.strip();
        if (emoji != null) this.emoji = blankToNull(emoji);
        if (repeatDays != null) this.repeatDays = encode(repeatDays);
        if (clearRemindTime) this.remindTime = null;
        else if (remindTime != null) this.remindTime = truncate(remindTime);
    }

    public void pause() {
        if (habitStatus == HabitStatus.ACTIVE) habitStatus = HabitStatus.PAUSED;
    }

    public void resume() {
        if (habitStatus == HabitStatus.PAUSED) habitStatus = HabitStatus.ACTIVE;
    }

    public void delete() {
        habitStatus = HabitStatus.DELETED;
    }

    public void changeSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    /** 만든 날보다 이전 날짜를 체크하면 시작일을 그 날로 당긴다 (어제 한 걸 오늘 습관을 만들며 체크하는 경우) */
    public void extendStartTo(LocalDate date) {
        if (date != null && date.isBefore(startDate)) this.startDate = date;
    }

    // ── 내부 ──────────────────────────────────────────────

    static String encode(Collection<DayOfWeek> days) {
        if (days == null || days.isEmpty()) return "";
        Set<DayOfWeek> set = EnumSet.copyOf(days);
        if (set.size() == 7) return "";   // 7일 전부 = 매일
        return set.stream().sorted().map(DayOfWeek::name).collect(Collectors.joining(","));
    }

    static Set<DayOfWeek> decode(String s) {
        if (s == null || s.isBlank()) return EnumSet.noneOf(DayOfWeek.class);
        return Arrays.stream(s.split(","))
                .map(String::strip)
                .filter(x -> !x.isEmpty())
                .map(DayOfWeek::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(DayOfWeek.class)));
    }

    private static LocalTime truncate(LocalTime t) {
        return t == null ? null : t.withSecond(0).withNano(0);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
