package com.naleul.naleul.domain.lifePattern.service;

import com.naleul.naleul.domain.lifePattern.dto.request.LifePatternCreateRequest;
import com.naleul.naleul.domain.lifePattern.dto.request.LifePatternDayTimeRequest;
import com.naleul.naleul.domain.lifePattern.dto.request.LifePatternOverrideRequest;
import com.naleul.naleul.domain.lifePattern.dto.request.LifePatternScopedChangeRequest;
import com.naleul.naleul.domain.lifePattern.dto.request.LifePatternUpdateRequest;
import com.naleul.naleul.domain.lifePattern.dto.response.DayFixedBlocksResponse;
import com.naleul.naleul.domain.lifePattern.dto.response.LifePatternResponse;
import com.naleul.naleul.domain.lifePattern.dto.response.LifePatternScopedChangeResponse;
import com.naleul.naleul.domain.lifePattern.entity.LifePattern;
import com.naleul.naleul.domain.lifePattern.entity.LifePatternDayTime;
import com.naleul.naleul.domain.lifePattern.entity.LifePatternOverride;
import com.naleul.naleul.domain.lifePattern.event.LifePatternChangedEvent;
import com.naleul.naleul.domain.lifePattern.repository.LifePatternOverrideRepository;
import com.naleul.naleul.domain.lifePattern.repository.LifePatternRepository;
import com.naleul.naleul.domain.lifePattern.support.FixedBlockCalculator;
import com.naleul.naleul.domain.planner.entity.PlannerSetting;
import com.naleul.naleul.domain.planner.enums.ChangeScope;
import com.naleul.naleul.domain.planner.service.PlannerSettingService;
import com.naleul.naleul.domain.planner.support.DateTimeRange;
import com.naleul.naleul.domain.planner.support.PlannerClock;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.service.UserFinder;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Collection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 기본 생활 패턴 (명세 3 · 3-1, 5-1).
 *
 * - 처음 조회하면 기본 패턴(수면·점심·저녁)을 만든다. 모두 지워도 다시 만들지 않는다 (planner_setting.pattern_initialized).
 * - 설정 화면에서 수정 = 이후 모든 날짜의 기본값. 캘린더 드래그 = 그날만 (Override).
 * - 같은 요일끼리 겹치는 패턴은 저장할 수 없다.
 * - 요일별로 시간을 다르게 할 수 있다 (dayTimes). 캘린더에서는 "앞으로 ○요일마다"(WEEKDAY)로 한 요일만 바꾼다.
 * - 변경 후 LifePatternChangedEvent 발행 → 배치 엔진(BE-3)이 겹치는 자동 배치 블록을 다시 배치.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class LifePatternService {

    public static final int MAX_PATTERNS = 10;
    /** 패턴 변경 시 다시 배치를 검토할 기간 (오늘부터) */
    private static final int REPLAN_HORIZON_DAYS = 7;
    /** 예외 날짜 허용 범위 (너무 먼 과거/미래 방지) */
    private static final int OVERRIDE_RANGE_DAYS = 366;
    /** 기본값을 "앞으로" 바꿀 때 예전 시간으로 고정해 둘 지난 날짜 수 (나의 패턴 최근 4주 분석 기간) */
    private static final int FREEZE_DAYS = 28;

    private final LifePatternRepository lifePatternRepository;
    private final LifePatternOverrideRepository overrideRepository;
    private final PlannerSettingService plannerSettingService;
    private final FixedBlockResolver fixedBlockResolver;
    private final UserFinder userFinder;
    private final PlannerClock clock;
    private final ApplicationEventPublisher eventPublisher;

    // ── 조회 ─────────────────────────────────────────────

    public List<LifePatternResponse> getPatterns(Long userId) {
        ensureInitialized(userId);
        LocalDate today = clock.today();
        // 지난 이날만 패턴은 목록에서 뺀다 (캘린더 그날 블록으로는 그대로 남아요)
        return lifePatternRepository.findAllByUserId(userId).stream()
                .filter(p -> !p.isOneOff() || !p.getOnlyDate().isBefore(today))
                .map(LifePatternResponse::from)
                .toList();
    }

    /** 기간의 고정 블록 + 하루 범위 (최대 7일). 캘린더가 BE-4 /timetable 전에 쓰는 미리보기용 */
    public List<DayFixedBlocksResponse> getFixedBlocks(Long userId, LocalDate startDate, LocalDate endDate) {
        validateRange(startDate, endDate);
        ensureInitialized(userId);
        return fixedBlockResolver.resolve(userId, startDate, endDate).values().stream()
                .map(DayFixedBlocksResponse::from)
                .toList();
    }

    /**
     * 기본 패턴을 한 번만 만든다.
     * 설정 행을 잠그고 확인하므로 웹이 첫 화면에서 동시에 두 번 불러도 중복 생성되지 않는다.
     */
    public void ensureInitialized(Long userId) {
        PlannerSetting setting = plannerSettingService.getOrCreateForUpdate(userId);
        if (setting.isPatternInitialized()) return;

        if (lifePatternRepository.countByUserId(userId) == 0) {
            User user = userFinder.findUser(userId);
            lifePatternRepository.saveAll(LifePattern.defaults(user));
        }
        setting.markPatternInitialized();
    }

    // ── 설정 화면: 추가 · 수정 · 삭제 ────────────────────────

    public LifePatternResponse create(Long userId, LifePatternCreateRequest request) {
        ensureInitialized(userId);
        validateTimes(request.startTime(), request.endTime());

        List<LifePattern> existing = lifePatternRepository.findAllByUserId(userId);
        LocalDate onlyDate = request.onlyDate();
        // 개수 제한은 매주 반복하는 패턴만 센다 (이날만 패턴은 하루 지나면 끝)
        if (onlyDate == null && existing.stream().filter(p -> !p.isOneOff()).count() >= MAX_PATTERNS) {
            throw new CustomException(ErrorCode.LIFE_PATTERN_LIMIT_EXCEEDED);
        }
        if (onlyDate != null) validateOverrideDate(onlyDate);

        int nextOrder = existing.stream().mapToInt(LifePattern::getSortOrder).max().orElse(-1) + 1;
        LifePattern pattern = LifePattern.create(userFinder.findUser(userId), request.patternType(),
                request.title().strip(), request.emoji(), request.startTime(), request.endTime(),
                request.days(), nextOrder);
        if (onlyDate != null) {
            pattern.restrictTo(onlyDate);
        } else if (request.dayTimes() != null) {
            pattern.replaceDayTimes(toDayTimes(request.dayTimes()));
        }
        // 지난 이날만 패턴과는 겹침을 보지 않는다
        LocalDate today = clock.today();
        validateNoOverlap(pattern, existing.stream()
                .filter(p -> !p.isOneOff() || !p.getOnlyDate().isBefore(today.minusDays(1)))
                .toList());

        lifePatternRepository.save(pattern);
        publishChanged(userId);
        return LifePatternResponse.from(pattern);
    }

    public LifePatternResponse update(Long userId, Long lifePatternId, LifePatternUpdateRequest request) {
        LifePattern pattern = findOwned(userId, lifePatternId);

        LocalTime newStart = request.startTime() != null ? request.startTime() : pattern.getStartTime();
        LocalTime newEnd = request.endTime() != null ? request.endTime() : pattern.getEndTime();
        validateTimes(newStart, newEnd);

        // 이날만 패턴은 날짜(요일)를 바꾸지 않는다 — 시간 · 이름만
        boolean oneOff = pattern.isOneOff();
        pattern.update(request.title() != null ? request.title().strip() : null, request.emoji(),
                request.startTime(), request.endTime(), oneOff ? null : request.days());
        if (!oneOff && request.dayTimes() != null) pattern.replaceDayTimes(toDayTimes(request.dayTimes()));

        LocalDate today = clock.today();
        List<LifePattern> others = lifePatternRepository.findAllByUserId(userId).stream()
                .filter(p -> !p.getLifePatternId().equals(lifePatternId))
                .filter(p -> !p.isOneOff() || !p.getOnlyDate().isBefore(today.minusDays(1)))
                .toList();
        // 겹치면 예외 → 트랜잭션 롤백으로 위 update 도 취소된다
        validateNoOverlap(pattern, others);

        publishChanged(userId);
        return LifePatternResponse.from(pattern);
    }

    public void delete(Long userId, Long lifePatternId) {
        LifePattern pattern = findOwned(userId, lifePatternId);
        // 예외 행은 DB FK ON DELETE CASCADE 로 함께 삭제된다
        lifePatternRepository.delete(pattern);
        publishChanged(userId);
    }

    // ── 캘린더: 범위 골라 바꾸기 (이날만 / 이번 주 / 앞으로) ─────────

    /**
     * DAY       : 이날만 (putOverride 와 같음)
     * WEEK      : 이날 ~ 그 주 일요일, 이 고정 시간이 있는 날마다 같은 시간으로 (이번 주에 "비운" 날은 그대로)
     * WEEKDAY   : 앞으로 이 요일마다 (요일별 시간). 이 요일의 지난 FREEZE_DAYS 일은 예전 시간으로 고정.
     * FOLLOWING : 앞으로 매일 — 기본값을 바꾸고 요일별 시간도 모두 이 시간으로 맞춘다.
     *             기본값은 날짜마다 저장되지 않고 계산되므로 그대로 바꾸면 지난 날짜까지 바뀌어 보인다
     *             → 이날 전 FREEZE_DAYS 일은 예전 시간으로 예외를 만들어 고정하고, 이날 이후 "시간만 바꾼" 예외는 지운다.
     *
     * startDayOffset(-1/0/+1): 기상·취침 드래그로 블록 시작이 소속 날짜의 전날/다음 날로 넘어간 경우.
     *   기본값에는 날짜 차이를 담을 수 없으므로, FOLLOWING 인데 차이가 있으면 모든 요일을 요일별 시간으로 저장한다.
     */
    public LifePatternScopedChangeResponse changeWithScope(Long userId, Long lifePatternId,
                                                           LifePatternScopedChangeRequest request) {
        ChangeScope scope = request.scope() != null ? request.scope() : ChangeScope.DAY;
        LocalDate date = request.targetDate();
        LocalTime start = request.startTime();
        LocalTime end = request.endTime();
        int offset = request.offset();

        if (scope == ChangeScope.DAY) {
            putOverride(userId, lifePatternId, date, new LifePatternOverrideRequest(start, end, false, offset));
            return new LifePatternScopedChangeResponse(scope, List.of(date), null);
        }

        LifePattern pattern = findOwned(userId, lifePatternId);
        validateOverrideDate(date);
        if (!pattern.appliesOnDate(date)) {
            throw new CustomException(ErrorCode.LIFE_PATTERN_NOT_ON_DATE);
        }
        validateTimes(start, end);
        validateOffset(offset);

        if (scope == ChangeScope.WEEK) {
            LocalDate sunday = date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
            List<LocalDate> changed = new ArrayList<>();
            for (LocalDate d = date; !d.isAfter(sunday); d = d.plusDays(1)) {
                if (!pattern.appliesOnDate(d)) continue;
                LifePatternOverride o = overrideRepository.findByPatternAndDate(lifePatternId, d).orElse(null);
                if (o != null && o.isSkipped() && !d.equals(date)) continue;   // 이번 주에 비운 날은 그대로
                if (o == null) {
                    overrideRepository.save(LifePatternOverride.moved(pattern, userId, d, start, end, offset));
                } else {
                    o.move(start, end, offset);
                }
                changed.add(d);
            }
            eventPublisher.publishEvent(new LifePatternChangedEvent(userId, date, sunday.plusDays(1)));
            return new LifePatternScopedChangeResponse(scope, changed, LifePatternResponse.from(pattern));
        }

        if (scope == ChangeScope.WEEKDAY) {
            DayOfWeek dow = date.getDayOfWeek();
            freezePast(pattern, userId, date, dow);
            pattern.setDayTime(dow, start, end, offset);
            validateNoOverlap(pattern, others(userId, lifePatternId)); // 겹치면 409 → 전부 롤백
            deleteMovedOnWeekdayFrom(userId, pattern, date, dow);
            eventPublisher.publishEvent(new LifePatternChangedEvent(userId, date, date.plusDays(REPLAN_HORIZON_DAYS)));
            return new LifePatternScopedChangeResponse(scope, List.of(date), LifePatternResponse.from(pattern));
        }

        // FOLLOWING
        freezePast(pattern, userId, date, null);
        if (offset == 0) {
            pattern.clearDayTimes();
            update(userId, lifePatternId, new LifePatternUpdateRequest(null, null, start, end, null)); // 겹치면 409 → 전부 롤백
        } else {
            for (DayOfWeek d : pattern.getDays()) pattern.setDayTime(d, start, end, offset);
            validateNoOverlap(pattern, others(userId, lifePatternId));
        }
        overrideRepository.deleteMovedFrom(pattern, date);
        eventPublisher.publishEvent(new LifePatternChangedEvent(userId, date, date.plusDays(REPLAN_HORIZON_DAYS)));
        return new LifePatternScopedChangeResponse(scope, List.of(date), LifePatternResponse.from(pattern));
    }

    private List<LifePattern> others(Long userId, Long lifePatternId) {
        return lifePatternRepository.findAllByUserId(userId).stream()
                .filter(p -> !p.getLifePatternId().equals(lifePatternId))
                .toList();
    }

    /** from 이후 이 요일의 "시간만 바꾼" 예외를 지운다 (새 요일별 시간을 따르게, 비운 날은 남김) */
    private void deleteMovedOnWeekdayFrom(Long userId, LifePattern pattern, LocalDate from, DayOfWeek dow) {
        List<LifePatternOverride> targets = overrideRepository
                .findAllInRange(userId, from, from.plusDays(OVERRIDE_RANGE_DAYS * 2L)).stream()
                .filter(o -> o.getLifePattern().getLifePatternId().equals(pattern.getLifePatternId()))
                .filter(o -> !o.isSkipped() && o.getTargetDate().getDayOfWeek() == dow)
                .toList();
        if (!targets.isEmpty()) overrideRepository.deleteAll(targets);
    }

    /**
     * 기본값(또는 요일별 시간)을 바꾸기 전에, 이날 전 FREEZE_DAYS 일은 지금 시간 그대로 보이도록 예외로 남겨 둔다.
     * @param onlyDay null 이 아니면 그 요일만 (WEEKDAY)
     */
    private void freezePast(LifePattern pattern, Long userId, LocalDate date, DayOfWeek onlyDay) {
        LocalDate from = date.minusDays(FREEZE_DAYS);
        LocalDate createdOn = pattern.getCreatedAt() != null ? pattern.getCreatedAt().toLocalDate() : from;
        if (createdOn.isAfter(from)) from = createdOn;     // 패턴을 만들기 전 날짜는 건드리지 않음

        Set<LocalDate> hasOverride = new HashSet<>();
        for (LifePatternOverride o : overrideRepository.findAllInRange(userId, from, date.minusDays(1))) {
            if (o.getLifePattern().getLifePatternId().equals(pattern.getLifePatternId())) hasOverride.add(o.getTargetDate());
        }
        for (LocalDate d = from; d.isBefore(date); d = d.plusDays(1)) {
            if (onlyDay != null && d.getDayOfWeek() != onlyDay) continue;
            if (!pattern.appliesOnDate(d) || hasOverride.contains(d)) continue;
            DateTimeRange r = pattern.occurrenceOn(d);   // 요일별 시간·시작 날짜 차이까지 반영된 지금 시간
            int off = (int) ChronoUnit.DAYS.between(d, r.start().toLocalDate());
            overrideRepository.save(LifePatternOverride.moved(pattern, userId, d,
                    r.start().toLocalTime(), r.end().toLocalTime(), off));
        }
    }

    // ── 캘린더: 그날만 변경 / 되돌리기 ─────────────────────

    public DayFixedBlocksResponse putOverride(Long userId, Long lifePatternId, LocalDate targetDate,
                                              LifePatternOverrideRequest request) {
        LifePattern pattern = findOwned(userId, lifePatternId);
        validateOverrideDate(targetDate);
        if (!pattern.appliesOnDate(targetDate)) {
            throw new CustomException(ErrorCode.LIFE_PATTERN_NOT_ON_DATE);
        }
        if (!request.isSkip()) {
            if (request.startTime() == null || request.endTime() == null) {
                throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
            }
            validateTimes(request.startTime(), request.endTime());
            validateOffset(request.offset());
        }

        LifePatternOverride override = overrideRepository.findByPatternAndDate(lifePatternId, targetDate)
                .orElse(null);
        if (override == null) {
            override = request.isSkip()
                    ? LifePatternOverride.skippedOn(pattern, userId, targetDate)
                    : LifePatternOverride.moved(pattern, userId, targetDate, request.startTime(), request.endTime(),
                    request.offset());
            overrideRepository.save(override);
        } else if (request.isSkip()) {
            override.skip();
        } else {
            override.move(request.startTime(), request.endTime(), request.offset());
        }

        eventPublisher.publishEvent(new LifePatternChangedEvent(userId, targetDate, targetDate.plusDays(1)));
        return dayResponse(userId, targetDate);
    }

    public DayFixedBlocksResponse deleteOverride(Long userId, Long lifePatternId, LocalDate targetDate) {
        findOwned(userId, lifePatternId);
        overrideRepository.findByPatternAndDate(lifePatternId, targetDate)
                .ifPresent(overrideRepository::delete);
        eventPublisher.publishEvent(new LifePatternChangedEvent(userId, targetDate, targetDate.plusDays(1)));
        return dayResponse(userId, targetDate);
    }

    // ── 내부 ─────────────────────────────────────────────

    private DayFixedBlocksResponse dayResponse(Long userId, LocalDate date) {
        overrideRepository.flush(); // 방금 저장/삭제한 예외가 조회에 반영되도록
        return DayFixedBlocksResponse.from(fixedBlockResolver.resolveDay(userId, date));
    }

    private LifePattern findOwned(Long userId, Long lifePatternId) {
        return lifePatternRepository.findOwned(lifePatternId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.LIFE_PATTERN_NOT_FOUND));
    }

    private static void validateOffset(int offset) {
        if (offset < -1 || offset > 1) throw new CustomException(ErrorCode.LIFE_PATTERN_INVALID_TIME);
    }

    private void validateTimes(LocalTime start, LocalTime end) {
        if (start == null || end == null || start.equals(end)) {
            throw new CustomException(ErrorCode.LIFE_PATTERN_INVALID_TIME);
        }
    }

    private void validateNoOverlap(LifePattern target, List<LifePattern> others) {
        if (FixedBlockCalculator.overlapsItself(target)) {
            throw new CustomException(ErrorCode.LIFE_PATTERN_OVERLAP); // 요일별 시간끼리 겹침 (예: 금요일 밤과 토요일 새벽)
        }
        for (LifePattern other : others) {
            if (FixedBlockCalculator.overlaps(target, other)) {
                throw new CustomException(ErrorCode.LIFE_PATTERN_OVERLAP);
            }
        }
    }

    private static List<LifePatternDayTime> toDayTimes(Collection<LifePatternDayTimeRequest> list) {
        try {
            return list.stream().filter(java.util.Objects::nonNull).map(LifePatternDayTimeRequest::toEntity).toList();
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.LIFE_PATTERN_INVALID_TIME);
        }
    }

    private void validateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)
                || ChronoUnit.DAYS.between(startDate, endDate) > 6) {
            throw new CustomException(ErrorCode.TIMETABLE_INVALID_RANGE);
        }
    }

    private void validateOverrideDate(LocalDate date) {
        LocalDate today = clock.today();
        if (date == null || date.isBefore(today.minusDays(OVERRIDE_RANGE_DAYS))
                || date.isAfter(today.plusDays(OVERRIDE_RANGE_DAYS))) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void publishChanged(Long userId) {
        LocalDate today = clock.today();
        eventPublisher.publishEvent(new LifePatternChangedEvent(userId, today, today.plusDays(REPLAN_HORIZON_DAYS)));
    }
}
