package com.naleul.naleul.domain.brainDump.support;

import com.naleul.naleul.domain.brainDump.enums.DateType;
import com.naleul.naleul.domain.task.enums.TaskKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class KoreanTaskTextParserTest {

    private static final LocalDate WED = LocalDate.of(2026, 11, 18); // 수요일

    private static KoreanTaskTextParser.Parsed p(String s) {
        return KoreanTaskTextParser.parse(s, WED);
    }

    @Test
    @DisplayName("디자인 예시 5줄: 오늘 / 내일까지 / 날짜 없음 / 금요일까지 / 이번 주")
    void designExamples() {
        assertThat(p("인바디 측정 예약하기 오늘").dateType()).isEqualTo(DateType.ON);
        assertThat(p("인바디 측정 예약하기 오늘").title()).isEqualTo("인바디 측정 예약하기");

        KoreanTaskTextParser.Parsed lc = p("토익 LC 모의고사 1회 풀기 내일까지");
        assertThat(lc.dateType()).isEqualTo(DateType.DUE);
        assertThat(lc.date()).isEqualTo(WED.plusDays(1));
        assertThat(lc.title()).isEqualTo("토익 LC 모의고사 1회 풀기");

        assertThat(p("아주 작은 습관의 힘 3장 읽기").dateType()).isEqualTo(DateType.NONE);

        KoreanTaskTextParser.Parsed house = p("이사할 집 매물 3곳 알아보기 금요일까지");
        assertThat(house.dateType()).isEqualTo(DateType.DUE);
        assertThat(house.date()).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(house.startTime()).isNull(); // "3곳"을 3시로 읽지 않음

        KoreanTaskTextParser.Parsed week = p("부동산 방문 예약 전화 이번 주");
        assertThat(week.dateType()).isEqualTo(DateType.DUE);
        assertThat(week.date()).isEqualTo(LocalDate.of(2026, 11, 22)); // 이번 주 일요일
        assertThat(week.kind()).isEqualTo(TaskKind.NORMAL);              // 시간 없는 "방문 예약 전화"는 약속 아님
    }

    @Test
    @DisplayName("시간: 오후 3시~4시 반, 14:00, 저녁 약속 7시 → 19시, 숫자만 있는 3시 → 15시")
    void times() {
        KoreanTaskTextParser.Parsed m = p("팀 미팅 내일 오후 3시~4시 반");
        assertThat(m.startTime()).isEqualTo(LocalTime.of(15, 0));
        assertThat(m.endTime()).isEqualTo(LocalTime.of(16, 30));
        assertThat(m.kind()).isEqualTo(TaskKind.APPOINTMENT);
        assertThat(m.title()).isEqualTo("팀 미팅");

        assertThat(p("치과 진료 11/20 14:00").startTime()).isEqualTo(LocalTime.of(14, 0));
        assertThat(p("저녁 약속 토요일 7시").startTime()).isEqualTo(LocalTime.of(19, 0));
        assertThat(p("3시 미팅").startTime()).isEqualTo(LocalTime.of(15, 0));
        assertThat(p("다음 주 화요일 PT 상담 10:30-11:30").date()).isEqualTo(LocalDate.of(2026, 11, 24));
    }

    @Test
    @DisplayName("저녁 8시 정기 회의 → 오늘 20:00 약속, 인강 듣기 3시간 → 시각 없이 180분")
    void eveningMeetingAndLongTask() {
        KoreanTaskTextParser.Parsed meeting = p("저녁 8시 정기 회의");
        assertThat(meeting.startTime()).isEqualTo(LocalTime.of(20, 0));
        assertThat(meeting.dateType()).isEqualTo(DateType.ON);
        assertThat(meeting.kind()).isEqualTo(TaskKind.APPOINTMENT);
        assertThat(meeting.title()).isEqualTo("정기 회의");

        KoreanTaskTextParser.Parsed jog = p("아침 7시 30분 조깅 40분");   // 시각의 30분을 소요 시간으로 가져가지 않음
        assertThat(jog.startTime()).isEqualTo(LocalTime.of(7, 30));
        assertThat(jog.minutes()).isEqualTo(40);

        KoreanTaskTextParser.Parsed lecture = p("인강 듣기 3시간");
        assertThat(lecture.startTime()).isNull();
        assertThat(lecture.minutes()).isEqualTo(180);
        assertThat(lecture.title()).isEqualTo("인강 듣기");
    }

    @Test
    @DisplayName("소요 시간: '운동 1시간'은 1시가 아니라 60분")
    void duration() {
        KoreanTaskTextParser.Parsed r = p("운동 1시간");
        assertThat(r.startTime()).isNull();
        assertThat(r.minutes()).isEqualTo(60);
        assertThat(r.title()).isEqualTo("운동");
    }

    @Test
    @DisplayName("12월 3일까지 → DUE, 올해 날짜")
    void monthDay() {
        KoreanTaskTextParser.Parsed r = p("보고서 제출 12월 3일까지");
        assertThat(r.dateType()).isEqualTo(DateType.DUE);
        assertThat(r.date()).isEqualTo(LocalDate.of(2026, 12, 3));
    }
}
