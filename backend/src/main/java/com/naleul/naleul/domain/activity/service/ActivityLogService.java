package com.naleul.naleul.domain.activity.service;

import com.naleul.naleul.domain.activity.dto.request.ActivityLogRequest;
import com.naleul.naleul.domain.activity.dto.response.ActivityLogResponse;
import com.naleul.naleul.domain.activity.dto.response.ActualActivityResponse;
import com.naleul.naleul.domain.activity.entity.ActualActivity;
import com.naleul.naleul.domain.activity.repository.ActualActivityRepository;
import com.naleul.naleul.domain.brainDump.support.KoreanTaskTextParser;
import com.naleul.naleul.domain.category.service.CategoryFinder;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.goalCategory.repository.GoalCategoryRepository;
import com.naleul.naleul.domain.goalCreation.service.GoalColorAssigner;
import com.naleul.naleul.domain.planner.support.PlannerClock;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.service.UserFinder;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "한 일" 빠른 기록 — Task 추가의 Brain dump 처럼 한 줄씩 적으면 실제로 한 일(ActualActivity)로 저장한다.
 *
 * 쓰는 곳
 *  - 기록형 목표 상세의 입력창: 목표가 이미 정해져 있어 AI 없이 바로 저장 ("저녁 8시 나를 회의 1시간")
 *  - Task 추가 화면의 "한 일 기록": /brain-dump/parse 로 목표를 AI 가 연결해 준 뒤 이 API 로 저장
 *
 * 시간 정하기 (resolveRange)
 *  - 시작~끝 시각이 있으면 그대로
 *  - 시작 시각 + 걸린 시간이면 끝 = 시작 + 걸린 시간 (시작만 있으면 1시간)
 *  - 시각이 없으면 "방금 끝낸 일"로 보고 지금(지난 날짜면 그날 18시)부터 거꾸로, 다른 기록과 겹치지 않는 자리에 넣는다
 *  - 아직 오지 않은 시간은 기록할 수 없다 (끝이 지금 이후면 지금까지로 자르고, 시작부터 미래면 오류)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ActivityLogService {

    static final int DEFAULT_MINUTES = 30;
    static final int DEFAULT_MINUTES_WITH_START = 60;
    static final int MIN_MINUTES = 5;
    static final LocalTime PAST_DAY_END = LocalTime.of(18, 0);
    static final int MAX_TEMP_GOALS_OWNED = 5;   // BrainDumpService 와 같은 한도
    static final int MAX_BACKFILL_STEPS = 50;
    static final int TIMELINE_MAX = 100;

    private static final Pattern PAST_DAY = Pattern.compile("(그저께|그제|어제)");
    private static final Pattern MINUTE_WORD = Pattern.compile("\\s*\\d{1,3}\\s*분\\s*(동안)?");

    private final ActualActivityRepository repository;
    private final GoalCategoryRepository goalCategoryRepository;
    private final CategoryFinder categoryFinder;
    private final GoalColorAssigner colorAssigner;
    private final UserFinder userFinder;
    private final PlannerClock clock;

    // ───────────────────── 저장 ─────────────────────

    public ActivityLogResponse log(Long userId, ActivityLogRequest request) {
        User user = userFinder.findUser(userId);
        LocalDateTime now = clock.now();
        LocalDate today = now.toLocalDate();

        Map<String, ActivityLogRequest.TempGoal> tempInputs = validate(request);
        Map<String, GoalCategory> tempGoals = createTempGoals(user, request, tempInputs, today);

        // 1) 시각이 정해진 것부터 (그래야 시각 없는 기록이 그 자리를 피해 간다)
        List<Planned> planned = new ArrayList<>();
        for (ActivityLogRequest.Item item : request.items()) {
            planned.add(plan(item, today));
        }
        List<Range> taken = new ArrayList<>();
        Map<String, Range> ranges = new LinkedHashMap<>();
        for (Planned p : planned) {
            if (p.start() == null) continue;
            Range r = explicitRange(p, now);
            if (overlapsAny(userId, r, taken)) throw new CustomException(ErrorCode.ACTIVITY_OVERLAP);
            taken.add(r);
            ranges.put(p.clientKey(), r);
        }
        // 2) 시각이 없는 것: 지금부터 거꾸로 빈 자리
        for (Planned p : planned) {
            if (p.start() != null) continue;
            Range r = backfill(userId, p, now, taken);
            taken.add(r);
            ranges.put(p.clientKey(), r);
        }

        // 3) 저장 (요청 순서대로 응답)
        List<ActualActivityResponse> saved = new ArrayList<>();
        for (int i = 0; i < request.items().size(); i++) {
            ActivityLogRequest.Item item = request.items().get(i);
            Planned p = planned.get(i);
            Range r = ranges.get(item.clientKey());
            GoalCategory goal = item.tempGoalKey() != null && !item.tempGoalKey().isBlank()
                    ? tempGoals.get(item.tempGoalKey())
                    : categoryFinder.findGoalCategory(item.goalCategoryId(), user);
            ActualActivity activity = ActualActivity.create(user, p.title(), goal != null ? goal.getEmoji() : null,
                    r.start(), r.end(), goal, null, null, null);
            saved.add(ActualActivityResponse.from(repository.save(activity)));
        }

        List<ActivityLogResponse.CreatedTempGoal> createdTemps = new ArrayList<>();
        tempGoals.forEach((key, g) -> createdTemps.add(new ActivityLogResponse.CreatedTempGoal(key, g.getGoalCategoryId(),
                g.getGoalCategoryName(), g.getEmoji(), g.getUserColor() != null ? g.getUserColor().getColorCode() : null)));
        log.info("[ActivityLog] 기록 userId={} items={} tempGoals={}", userId, saved.size(), createdTemps.size());
        return new ActivityLogResponse(saved, createdTemps);
    }

    // ───────────────────── 목표별 타임라인 ─────────────────────

    @Transactional(readOnly = true)
    public List<ActualActivityResponse> getOfGoal(Long userId, Long goalCategoryId, LocalDateTime before, int size) {
        User user = userFinder.findUser(userId);
        categoryFinder.findGoalCategory(goalCategoryId, user);   // 내 목표인지 확인 (아니면 404)
        LocalDateTime cursor = before != null ? before : clock.now().plusDays(1);
        int limit = Math.min(Math.max(size, 1), TIMELINE_MAX);
        return repository.findRecentOfGoal(userId, goalCategoryId, cursor, PageRequest.of(0, limit)).stream()
                .map(ActualActivityResponse::from).toList();
    }

    // ───────────────────── 문장 → 제목·날짜·시각 ─────────────────────

    /** 한 줄을 읽은 결과. start 가 null 이면 "시각 없음" */
    record Planned(String clientKey, String title, LocalDate date, LocalTime start, LocalTime end, Integer minutes) {
    }

    record Range(LocalDateTime start, LocalDateTime end) {
        boolean overlaps(Range o) {
            return start.isBefore(o.end) && end.isAfter(o.start);
        }
    }

    /** 보낸 값(AI 해석 결과)이 우선, 없으면 문장에서 직접 읽는다 */
    static Planned plan(ActivityLogRequest.Item item, LocalDate today) {
        String text = item.text().strip();

        // "어제/그제" — Task 용 파서는 미래만 보기 때문에 기록에서는 먼저 처리
        LocalDate pastDay = null;
        Matcher past = PAST_DAY.matcher(text);
        if (past.find()) {
            pastDay = past.group(1).equals("어제") ? today.minusDays(1) : today.minusDays(2);
            text = (text.substring(0, past.start()) + " " + text.substring(past.end())).strip();
        }

        KoreanTaskTextParser.Parsed parsed = KoreanTaskTextParser.parse(text, today);
        Integer minutes = item.minutes() != null ? item.minutes() : parsed.minutes();

        String title = item.title() != null && !item.title().isBlank() ? item.title().strip() : parsed.title();
        if (item.title() == null && minutes != null) {
            // "운동 30분" → 제목 "운동" (30분은 걸린 시간으로)
            String cleaned = MINUTE_WORD.matcher(title).replaceAll(" ").replaceAll("\\s{2,}", " ").strip();
            if (!cleaned.isEmpty()) title = cleaned;
        }
        if (title.length() > 60) title = title.substring(0, 60);

        // 문장에 "어제/그제"가 있으면 그게 우선 (AI 해석은 Task 기준이라 지난 날짜를 오늘로 당겨 오기 때문)
        LocalDate date = pastDay != null ? pastDay : item.date() != null ? item.date() : parsed.date();
        date = pastOrToday(date, today);

        LocalTime start = item.startTime() != null ? item.startTime() : parsed.startTime();
        LocalTime end = item.startTime() != null ? item.endTime() : parsed.endTime();
        return new Planned(item.clientKey(), title, date, start, end, minutes);
    }

    /**
     * 기록은 지난 일이라 미래 날짜는 과거로 돌린다.
     * "금요일 회의"처럼 요일만 쓰면 파서가 다가오는 금요일을 주므로 지난 금요일로, 그 밖의 미래 날짜는 오늘로.
     */
    static LocalDate pastOrToday(LocalDate date, LocalDate today) {
        if (date == null) return today;
        if (!date.isAfter(today)) return date;
        LocalDate lastWeek = date.minusWeeks(1);
        return Duration.between(today.atStartOfDay(), date.atStartOfDay()).toDays() <= 6 && !lastWeek.isAfter(today)
                ? lastWeek : today;
    }

    // ───────────────────── 시간 정하기 ─────────────────────

    static Range explicitRange(Planned p, LocalDateTime now) {
        LocalDateTime start = p.date().atTime(p.start());
        LocalDateTime end;
        if (p.end() != null) end = p.date().atTime(p.end());
        else end = start.plusMinutes(p.minutes() != null ? p.minutes() : DEFAULT_MINUTES_WITH_START);

        if (!start.isBefore(now)) throw new CustomException(ErrorCode.ACTIVITY_FUTURE);
        if (end.isAfter(now)) end = now.withSecond(0).withNano(0);   // 하는 중이던 일은 지금까지로
        if (Duration.between(start, end).toMinutes() < 1) throw new CustomException(ErrorCode.ACTIVITY_INVALID_TIME);
        if (Duration.between(start, end).toMinutes() > ActualActivityService.MAX_MINUTES) {
            throw new CustomException(ErrorCode.ACTIVITY_TOO_LONG);
        }
        return new Range(start, end);
    }

    /** 시각 없는 기록: 끝을 지금(지난 날짜면 18시)에 두고, 겹치면 겹친 기록의 시작으로 당겨 가며 빈 자리를 찾는다 */
    private Range backfill(Long userId, Planned p, LocalDateTime now, List<Range> taken) {
        int minutes = Math.max(p.minutes() != null ? p.minutes() : DEFAULT_MINUTES, MIN_MINUTES);
        LocalDateTime end = p.date().equals(now.toLocalDate())
                ? now.withSecond(0).withNano(0)
                : p.date().atTime(PAST_DAY_END);

        for (int step = 0; step < MAX_BACKFILL_STEPS; step++) {
            Range candidate = new Range(end.minusMinutes(minutes), end);
            LocalDateTime earliestClash = earliestClash(userId, candidate, taken);
            if (earliestClash == null) return candidate;
            end = earliestClash;
        }
        throw new CustomException(ErrorCode.ACTIVITY_OVERLAP);
    }

    private LocalDateTime earliestClash(Long userId, Range r, List<Range> taken) {
        LocalDateTime earliest = null;
        for (Range t : taken) {
            if (t.overlaps(r) && (earliest == null || t.start().isBefore(earliest))) earliest = t.start();
        }
        for (ActualActivity a : repository.findOverlapping(userId, r.start(), r.end())) {
            if (earliest == null || a.getStartAt().isBefore(earliest)) earliest = a.getStartAt();
        }
        return earliest;
    }

    private boolean overlapsAny(Long userId, Range r, List<Range> taken) {
        return taken.stream().anyMatch(t -> t.overlaps(r)) || !repository.findOverlapping(userId, r.start(), r.end()).isEmpty();
    }

    // ───────────────────── 검증 · 임시 목표 ─────────────────────

    private Map<String, ActivityLogRequest.TempGoal> validate(ActivityLogRequest request) {
        Map<String, ActivityLogRequest.TempGoal> byKey = new LinkedHashMap<>();
        for (ActivityLogRequest.TempGoal t : request.tempGoals()) {
            if (byKey.putIfAbsent(t.tempKey(), t) != null) throw new CustomException(ErrorCode.BRAIN_DUMP_TEMP_GOAL_UNKNOWN);
        }
        Set<String> keys = new HashSet<>();
        for (ActivityLogRequest.Item i : request.items()) {
            if (!keys.add(i.clientKey())) throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
            boolean hasTemp = i.tempGoalKey() != null && !i.tempGoalKey().isBlank();
            if (hasTemp && i.goalCategoryId() != null) throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
            if (hasTemp && !byKey.containsKey(i.tempGoalKey())) throw new CustomException(ErrorCode.BRAIN_DUMP_TEMP_GOAL_UNKNOWN);
        }
        return byKey;
    }

    private Map<String, GoalCategory> createTempGoals(User user, ActivityLogRequest request,
                                                     Map<String, ActivityLogRequest.TempGoal> inputs, LocalDate today) {
        Set<String> used = new LinkedHashSet<>();
        request.items().forEach(i -> {
            if (i.tempGoalKey() != null && !i.tempGoalKey().isBlank()) used.add(i.tempGoalKey());
        });
        if (used.isEmpty()) return Map.of();

        long owned = goalCategoryRepository.countTemporaryByUserId(user.getUserId());
        if (owned + used.size() > MAX_TEMP_GOALS_OWNED) throw new CustomException(ErrorCode.TEMP_GOAL_LIMIT_EXCEEDED);

        Map<String, GoalCategory> result = new LinkedHashMap<>();
        for (String key : used) {
            ActivityLogRequest.TempGoal in = inputs.get(key);
            GoalCategory goal = GoalCategory.createTemporary(user, colorAssigner.assign(user, in.colorId()),
                    in.name().strip(), in.emoji() != null && !in.emoji().isBlank() ? in.emoji().strip() : "📌", today);
            goalCategoryRepository.save(goal);
            result.put(key, goal);
        }
        return result;
    }
}
