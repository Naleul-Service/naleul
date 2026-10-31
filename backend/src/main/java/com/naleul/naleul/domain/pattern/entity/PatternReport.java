package com.naleul.naleul.domain.pattern.entity;

import com.naleul.naleul.domain.pattern.enums.PatternAiStatus;
import com.naleul.naleul.domain.pattern.enums.PatternPeriod;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 패턴 리포트 캐시 (사용자, 기간, 기준일) UNIQUE (명세 4 · 7-1).
 *
 * 분석 범위가 "어제까지"라서 같은 날에는 결과가 바뀌지 않는다.
 * → 하루 한 번만 계산하고 AI 도 하루 한 번만 호출한다 (1GB 서버 · AI 비용 보호).
 * 제안을 반영하면 루틴이 바뀌므로 그 사용자의 캐시를 지운다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "pattern_report")
public class PatternReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long patternReportId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PatternPeriod period;

    @Column(nullable = false)
    private LocalDate baseDate;

    /** API 응답 본문 JSON. MEDIUMTEXT 라서 columnDefinition 을 맞춰야 ddl-auto: validate 를 통과한다 (WeeklyReport 의 TEXT 컬럼과 같은 방식) */
    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PatternAiStatus aiStatus;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static PatternReport of(Long userId, PatternPeriod period, LocalDate baseDate,
                                   String payloadJson, PatternAiStatus aiStatus, LocalDateTime now) {
        PatternReport r = new PatternReport();
        r.userId = userId;
        r.period = period;
        r.baseDate = baseDate;
        r.payloadJson = payloadJson;
        r.aiStatus = aiStatus;
        r.createdAt = now;
        return r;
    }
}
