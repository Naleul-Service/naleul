package com.naleul.naleul.domain.planner.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사용자별 플래너 설정 (1인 1행, PK = user_id).
 * 행이 없으면 서비스가 createDefault 로 만든다 (기존 사용자 마이그레이션 불필요).
 *
 * 22시 작업과 Brain dump 확정이 같은 사용자에 대해 동시에 돌 때,
 * 이 행을 SELECT ... FOR UPDATE 로 잡아 사용자 단위로 순서를 맞춘다 (명세 9장).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "planner_setting")
public class PlannerSetting {

    /** Task 사이 쉬는 시간 (2026-10: 10 → 20분, 20~30분이 적당하다는 피드백) */
    public static final int DEFAULT_GAP_MINUTES = 20;

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private boolean autoGenerateEnabled = true;

    @Column(nullable = false)
    private boolean nightlyPushEnabled = true;

    /** 못 한 일을 다음 날 아침(기상 시간)에 자동으로 옮길지 (V71, 2026-10). 끄면 그날에 '놓침'으로 남는다 */
    @Column(nullable = false)
    private boolean autoCarryEnabled = true;

    @Column(nullable = false)
    private int taskGapMinutes = DEFAULT_GAP_MINUTES;

    @Column(nullable = false)
    private boolean patternInitialized = false;

    @Column(insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public static PlannerSetting createDefault(Long userId) {
        PlannerSetting s = new PlannerSetting();
        s.userId = userId;
        return s;
    }

    public void update(Boolean autoGenerateEnabled, Boolean nightlyPushEnabled) {
        update(autoGenerateEnabled, nightlyPushEnabled, null);
    }

    public void update(Boolean autoGenerateEnabled, Boolean nightlyPushEnabled, Boolean autoCarryEnabled) {
        if (autoGenerateEnabled != null) this.autoGenerateEnabled = autoGenerateEnabled;
        if (nightlyPushEnabled != null) this.nightlyPushEnabled = nightlyPushEnabled;
        if (autoCarryEnabled != null) this.autoCarryEnabled = autoCarryEnabled;
    }

    /** 기본 패턴을 한 번 만들었음을 기록 → 사용자가 모두 지워도 다시 만들지 않는다 */
    public void markPatternInitialized() {
        this.patternInitialized = true;
    }
}
