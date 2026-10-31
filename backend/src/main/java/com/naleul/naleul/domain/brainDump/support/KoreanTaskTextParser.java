package com.naleul.naleul.domain.brainDump.support;

import com.naleul.naleul.domain.brainDump.enums.DateType;
import com.naleul.naleul.domain.task.enums.TaskKind;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Brain dump 한 줄에서 날짜·시간·종류를 규칙으로 뽑는다 (AI 없이).
 *
 * 쓰이는 곳
 *  - FakeBrainDumpAiClient (로컬 개발)
 *  - AI 호출이 실패했을 때의 대체 해석 (명세 5-4: aiFailed)
 *
 * 지원 표현 (예)
 *  날짜: 오늘, 내일, 모레, 이번 주, 다음 주, 금요일, 11/20, 11월 20일, "~까지"(= 마감)
 *  시간: 오후 3시, 3시 반, 15:00, 14:00~15:30, 3시~4시
 *  종류: 미팅·회의·약속·진료·면접·상담 + 시간이 있을 때만 APPOINTMENT
 *        (시간 없는 "부동산 방문 예약 전화"를 약속으로 오해해 중요도가 과하게 오르지 않도록)
 */
public final class KoreanTaskTextParser {

    private KoreanTaskTextParser() {
    }

    public record Parsed(String title, DateType dateType, LocalDate date, LocalTime startTime, LocalTime endTime,
                         Integer minutes, TaskKind kind) {
    }

    private static final Pattern MONTH_DAY = Pattern.compile("(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일");
    private static final Pattern SLASH_DATE = Pattern.compile("(?<![\\d:])(\\d{1,2})/(\\d{1,2})(?![\\d/])");
    private static final Pattern WEEKDAY = Pattern.compile("(이번\\s*주\\s*|다음\\s*주\\s*)?([월화수목금토일])요일");
    private static final Pattern THIS_WEEK = Pattern.compile("이번\\s*주");
    private static final Pattern NEXT_WEEK = Pattern.compile("다음\\s*주");
    private static final Pattern RELATIVE = Pattern.compile("(오늘|내일|모레)");
    private static final Pattern UNTIL = Pattern.compile("\\s*까지");

    // "1시간"의 "1시"를 시각으로 읽지 않도록 '시' 뒤에 '간'이 오면 제외
    private static final String HM = "(오전|오후|저녁|밤|아침)?\\s*(\\d{1,2})(?::(\\d{2})|\\s*시(?!간)(?:\\s*(\\d{1,2})\\s*분|\\s*(반))?)";
    private static final Pattern TIME_RANGE = Pattern.compile(HM + "\\s*[~\\-]\\s*" + HM);
    private static final Pattern TIME_ONE = Pattern.compile(HM);
    // "7시 30분"의 "30분"은 시각이라 소요 시간으로 보지 않는다 ('시' 바로 뒤의 분은 제외)
    private static final Pattern DURATION = Pattern.compile("(\\d{1,2})\\s*시간(\\s*반)?|(?<!시)(?<!시\\s)(?<!시\\s\\s)(\\d{2,3})\\s*분\\s*(동안)?");
    private static final Pattern APPOINTMENT = Pattern.compile("미팅|회의|약속|진료|면접|상담|모임|병원");

    public static Parsed parse(String raw, LocalDate today) {
        String text = raw == null ? "" : raw.strip();
        String rest = text;

        // ── 날짜 ──
        DateType type = null;
        LocalDate date = null;
        boolean until = UNTIL.matcher(rest).find();

        Matcher m;
        if ((m = MONTH_DAY.matcher(rest)).find()) {
            date = resolveMonthDay(today, Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
            rest = cut(rest, m);
        } else if ((m = SLASH_DATE.matcher(rest)).find()) {
            date = resolveMonthDay(today, Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
            rest = cut(rest, m);
        } else if ((m = WEEKDAY.matcher(rest)).find()) {
            DayOfWeek dow = dayOf(m.group(2).charAt(0));
            boolean next = m.group(1) != null && m.group(1).startsWith("다음");
            LocalDate base = next ? today.plusWeeks(1).with(DayOfWeek.MONDAY) : today;
            date = base.with(TemporalAdjusters.nextOrSame(dow));
            rest = cut(rest, m);
        } else if ((m = RELATIVE.matcher(rest)).find()) {
            date = switch (m.group(1)) {
                case "내일" -> today.plusDays(1);
                case "모레" -> today.plusDays(2);
                default -> today;
            };
            rest = cut(rest, m);
        } else if ((m = THIS_WEEK.matcher(rest)).find()) {
            date = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
            until = true; // "이번 주" = 이번 주 안에 하면 됨
            rest = cut(rest, m);
        } else if ((m = NEXT_WEEK.matcher(rest)).find()) {
            date = today.plusWeeks(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
            until = true;
            rest = cut(rest, m);
        }
        rest = UNTIL.matcher(rest).replaceAll(" ");

        // ── 소요 시간 (시각보다 먼저: "2시간"을 2시로 읽지 않게) ──
        Integer minutes = null;
        if ((m = DURATION.matcher(rest)).find()) {
            if (m.group(1) != null) minutes = Integer.parseInt(m.group(1)) * 60 + (m.group(2) != null ? 30 : 0);
            else minutes = Integer.parseInt(m.group(3));
            if (m.group(4) != null || m.group(1) != null) rest = cut(rest, m); // "30분 동안", "2시간"은 제목에서 뺀다
        }

        // ── 시간 ──
        boolean evening = text.contains("저녁") || text.contains("밤");
        LocalTime start = null, end = null;
        if ((m = TIME_RANGE.matcher(rest)).find()) {
            start = toTime(m.group(1), m.group(2), m.group(3), m.group(4), m.group(5), null, evening);
            end = toTime(m.group(6) != null ? m.group(6) : m.group(1), m.group(7), m.group(8), m.group(9), m.group(10), start, evening);
            rest = cut(rest, m);
        } else if ((m = TIME_ONE.matcher(rest)).find() && looksLikeClock(m)) {
            start = toTime(m.group(1), m.group(2), m.group(3), m.group(4), m.group(5), null, evening);
            rest = cut(rest, m);
        }
        if (start != null && end != null && !end.isAfter(start)) end = null;

        if (date != null) {
            type = until ? DateType.DUE : DateType.ON;
        } else if (start != null) {
            type = DateType.ON;
            date = today;
        } else {
            type = until ? DateType.DUE : DateType.NONE;
        }

        TaskKind kind = start != null && APPOINTMENT.matcher(text).find() ? TaskKind.APPOINTMENT : TaskKind.NORMAL;
        String title = rest.replaceAll("\\s{2,}", " ").strip();
        if (title.isEmpty()) title = text;
        return new Parsed(title, type, date, start, end, minutes, kind);
    }

    /** "3시"처럼 숫자 하나만 있는 것은 시간으로 보되 "3곳", "3장"처럼 단위가 붙은 숫자는 제외 (패턴상 '시'나 ':'가 있어야 매칭됨) */
    private static boolean looksLikeClock(Matcher m) {
        return m.group(3) != null || m.group(0).contains("시");
    }

    /** @param evening 문장에 "저녁/밤"이 있으면 오전/오후 표시 없는 7~11시를 저녁으로 본다 ("저녁 약속 7시" → 19시) */
    private static LocalTime toTime(String ampm, String h, String colonMin, String min, String half, LocalTime after,
                                    boolean evening) {
        int hour = Integer.parseInt(h);
        int minute = colonMin != null ? Integer.parseInt(colonMin) : min != null ? Integer.parseInt(min) : half != null ? 30 : 0;
        if (hour > 24 || minute > 59) return null;
        if (ampm != null && (ampm.equals("오후") || ampm.equals("저녁") || ampm.equals("밤")) && hour < 12) hour += 12;
        else if (ampm == null && colonMin == null && hour >= 1 && hour <= 6) hour += 12; // "3시 미팅" → 15시
        else if (ampm == null && evening && hour >= 7 && hour < 12) hour += 12;          // "저녁 약속 7시" → 19시
        if (after != null && hour < after.getHour() && hour + 12 < 24) hour += 12;        // "3시~4시" 처럼 뒤 시간 보정
        if (hour == 24) hour = 0;
        return LocalTime.of(hour, minute);
    }

    private static LocalDate resolveMonthDay(LocalDate today, int month, int day) {
        if (month < 1 || month > 12 || day < 1 || day > 31) return null;
        try {
            LocalDate d = LocalDate.of(today.getYear(), month, day);
            return d.isBefore(today.minusDays(30)) ? d.plusYears(1) : d; // 1월에 "12/30"을 쓰면 작년이 아니라 올해 말
        } catch (java.time.DateTimeException e) {
            return null;
        }
    }

    private static DayOfWeek dayOf(char c) {
        return switch (c) {
            case '월' -> DayOfWeek.MONDAY;
            case '화' -> DayOfWeek.TUESDAY;
            case '수' -> DayOfWeek.WEDNESDAY;
            case '목' -> DayOfWeek.THURSDAY;
            case '금' -> DayOfWeek.FRIDAY;
            case '토' -> DayOfWeek.SATURDAY;
            default -> DayOfWeek.SUNDAY;
        };
    }

    private static String cut(String s, Matcher m) {
        return (s.substring(0, m.start()) + " " + s.substring(m.end())).strip();
    }
}
