// domain/mission/enums/MissionCategoryItem.java
package com.naleul.naleul.domain.mission.enums;

public enum MissionCategoryItem {
    // HEALTH
    EXERCISE, DIET, DRINK_WATER, ABSTAIN_ALCOHOL, QUIT_SMOKING,
    // STUDY
    LANGUAGE, CERTIFICATE, READING, CODING,
    // LIFESTYLE
    WAKE_UP, SLEEP, MEDITATION, CLEANING, DIARY,
    // 건강/학습/생활습관 공통 "기타"
    ETC;

    public static boolean belongsTo(MissionCategory category, MissionCategoryItem item) {
        return switch (category) {
            case HEALTH -> item == EXERCISE || item == DIET || item == DRINK_WATER
                    || item == ABSTAIN_ALCOHOL || item == QUIT_SMOKING || item == ETC;
            case STUDY -> item == LANGUAGE || item == CERTIFICATE || item == READING
                    || item == CODING || item == ETC;
            case LIFESTYLE -> item == WAKE_UP || item == SLEEP || item == MEDITATION
                    || item == CLEANING || item == DIARY || item == ETC;
            case ETC -> false; // 카테고리가 기타면 하위 항목 자체가 없음
        };
    }
}