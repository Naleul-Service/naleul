package com.naleul.naleul.domain.externalCalendar.service;

import com.naleul.naleul.domain.externalCalendar.dto.request.ExternalEventImportRequest.Event;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 외부 일정 → 만들 Task 목록 (DB 없이 순수 계산).
 *
 * 건너뛰는 일정:
 *  - 이미 가져온 일정 (같은 출처 · 같은 externalId)
 *  - 같은 이름 · 같은 시작 시각 Task 가 이미 있음 — 같은 구글 일정을 웹(구글)과 iOS(아이폰 캘린더)로
 *    두 번 가져오면 externalId 가 달라서 id 로는 못 막기 때문
 *  - 오늘 이전 일정 — 지난 일정을 Task 로 만들면 "못 한 일"로 잡혀 실행률 · 패턴 통계가 틀어진다
 *  - 날짜 · 시간이 비었거나 끝이 시작보다 빠름, 1년 넘게 뒤
 * 24시간이 넘는 시간 일정(여러 날 걸친 일정)은 시작일의 하루 종일 일정으로 만든다.
 */
public final class ExternalEventImportPlanner {

    static final int TITLE_MAX = 100;
    static final int MAX_DAYS_AHEAD = 366;

    private ExternalEventImportPlanner() {
    }

    /** 만들 Task 하나. allDay 면 start/end 는 null */
    public record Draft(String externalId, String title, boolean allDay,
                        LocalDate date, LocalDateTime start, LocalDateTime end) {
        public long minutes() {
            return allDay ? 0 : Duration.between(start, end).toMinutes();
        }
    }

    public record Plan(List<Draft> drafts, int skippedDuplicate, int skippedPast, int skippedInvalid) {
    }

    /**
     * @param existingIds        이 출처로 이미 가져온 externalId
     * @param existingNameStarts 이미 있는 Task 의 "이름|시작 시각" (nameStartKey)
     */
    public static Plan plan(List<Event> events, LocalDate today,
                            Collection<String> existingIds, Collection<String> existingNameStarts) {
        Set<String> seenIds = new HashSet<>(existingIds);
        Set<String> seenNameStarts = new HashSet<>(existingNameStarts);
        List<Draft> drafts = new ArrayList<>();
        int dup = 0, past = 0, invalid = 0;

        for (Event e : events) {
            Draft d = toDraft(e);
            if (d == null || d.date().isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
                invalid++;
                continue;
            }
            if (d.date().isBefore(today)) {
                past++;
                continue;
            }
            String nameStart = d.allDay() ? null : nameStartKey(d.title(), d.start());
            if (!seenIds.add(d.externalId()) || (nameStart != null && !seenNameStarts.add(nameStart))) {
                dup++;
                continue;
            }
            drafts.add(d);
        }
        return new Plan(drafts, dup, past, invalid);
    }

    public static String nameStartKey(String title, LocalDateTime start) {
        return title.strip() + "|" + start.withSecond(0).withNano(0);
    }

    static Draft toDraft(Event e) {
        if (e == null || e.externalId() == null || e.externalId().isBlank()) return null;
        String title = e.title() == null ? "" : e.title().strip();
        if (title.isEmpty()) return null;
        if (title.length() > TITLE_MAX) title = title.substring(0, TITLE_MAX);
        String id = e.externalId().strip();

        if (e.allDay()) {
            LocalDate date = e.date() != null ? e.date() : e.start() != null ? e.start().toLocalDate() : null;
            return date == null ? null : new Draft(id, title, true, date, null, null);
        }
        if (e.start() == null || e.end() == null || !e.end().isAfter(e.start())) return null;
        LocalDateTime start = e.start().withSecond(0).withNano(0);
        LocalDateTime end = e.end().withSecond(0).withNano(0);
        if (!end.isAfter(start)) return null;
        if (Duration.between(start, end).toHours() >= 24) {
            return new Draft(id, title, true, start.toLocalDate(), null, null);
        }
        return new Draft(id, title, false, start.toLocalDate(), start, end);
    }
}
