package com.naleul.naleul.domain.pattern.calculator;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** 화면 문구용 시간·요일 이름 */
public final class TimeLabels {

    private TimeLabels() {
    }

    /** 월, 화, ... 일 */
    public static String dayShort(DayOfWeek d) {
        return switch (d) {
            case MONDAY -> "월";
            case TUESDAY -> "화";
            case WEDNESDAY -> "수";
            case THURSDAY -> "목";
            case FRIDAY -> "금";
            case SATURDAY -> "토";
            case SUNDAY -> "일";
        };
    }

    /** 월요일, 화요일 ... */
    public static String dayLong(DayOfWeek d) {
        return dayShort(d) + "요일";
    }

    /** 자주 옮기는 블록 설명용 시간대 이름 (명세 4 · 2-6) */
    public static String slotName(int hour) {
        if (hour < 5) return "새벽";
        if (hour < 9) return "아침";
        if (hour < 12) return "오전";
        if (hour < 14) return "점심";
        if (hour < 18) return "오후";
        return "저녁";
    }

    /** "주로 추가하는 때" 용 시간대 이름 (일요일 밤) */
    public static String partOfDay(int hour) {
        if (hour < 5) return "새벽";
        if (hour < 12) return "아침";
        if (hour < 18) return "오후";
        if (hour < 21) return "저녁";
        return "밤";
    }

    /** 10분 단위로 내린 "HH:mm" */
    public static String hhmm10(LocalDateTime t) {
        LocalTime time = t.toLocalTime();
        int m = time.getMinute() / 10 * 10;
        return String.format("%02d:%02d", time.getHour(), m);
    }

    /** "출근 준비" → "출근 준비와", "회사 업무" → "회사 업무와", "운동" → "운동과" (받침에 따라 과/와) */
    public static String withGwa(String word) {
        if (word == null || word.isEmpty()) return word;
        char last = word.charAt(word.length() - 1);
        if (last < 0xAC00 || last > 0xD7A3) return word + "과(와)"; // 한글이 아니면 둘 다
        boolean hasBatchim = (last - 0xAC00) % 28 != 0;
        return word + (hasBatchim ? "과" : "와");
    }

    /** 을/를 */
    public static String withEulReul(String word) {
        return word + (batchim(word) ? "을" : "를");
    }

    /** 은/는 */
    public static String withEunNeun(String word) {
        return word + (batchim(word) ? "은" : "는");
    }

    /** 으로/로 — 받침이 없거나 ㄹ 받침이면 "로" */
    public static String withRo(String word) {
        Character jong = jongseong(word);
        return word + (jong == null || jong == 8 ? "로" : "으로");
    }

    /** 마지막 글자에 받침이 있는지 (숫자는 읽는 소리로: 0 영, 1 일, 3 삼, 6 육, 7 칠, 8 팔) */
    static boolean batchim(String word) {
        Character j = jongseong(word);
        return j != null;
    }

    /** 마지막 글자의 종성 번호 (없으면 null). ㄹ = 8 */
    private static Character jongseong(String word) {
        if (word == null || word.isEmpty()) return null;
        char last = word.charAt(word.length() - 1);
        if (last >= '0' && last <= '9') {
            return switch (last) {
                case '0', '3', '6' -> (char) 1;     // 영(ㅇ) 삼(ㅁ) 육(ㄱ) — ㄹ 이 아닌 받침
                case '1', '7', '8' -> (char) 8;     // 일 칠 팔 — ㄹ 받침
                default -> null;                    // 이 사 오 구
            };
        }
        if (last < 0xAC00 || last > 0xD7A3) return null;
        int j = (last - 0xAC00) % 28;
        return j == 0 ? null : (char) j;
    }
}
