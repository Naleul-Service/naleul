package com.naleul.naleul.domain.mission.util;

public final class MissionPuzzleCalculator {

    private static final int TOTAL_PUZZLE_PIECES = 5;

    // 반복요일(주3일 등) 설정에 따라 총 예정일 자체가 달라지므로,
    // 절대 일수가 아닌 비율(80%) 기준이어야 어떤 반복 패턴에서도 공정하게 동작함
    private static final double REQUIRED_ACHIEVEMENT_RATE = 0.8; // 80%

    private MissionPuzzleCalculator() {}

    // totalScheduledDays: 그 미션 기간 동안 실제로 생성된 예정일(Task) 총 개수
    public static int calculatePieces(int achievedDays, int totalScheduledDays) {
        int requiredDays = getRequiredDays(totalScheduledDays);
        if (requiredDays <= 0) return 0;
        int pieces = (int) Math.floor((double) achievedDays / requiredDays * TOTAL_PUZZLE_PIECES);
        return Math.min(TOTAL_PUZZLE_PIECES, Math.max(0, pieces));
    }

    public static boolean isSuccess(int achievedDays, int totalScheduledDays) {
        return achievedDays >= getRequiredDays(totalScheduledDays);
    }

    // ✅ private → public 변경 — API 응답에 "필요일수"를 표시해야 하는 서비스 레이어에서도 재사용
    // "80% 이상"을 만족하려면 최소 며칠이 필요한지 (올림 처리 — 내림하면 80% 미만도 통과되는 문제 발생)
    public static int getRequiredDays(int totalScheduledDays) {
        if (totalScheduledDays <= 0) return 0;
        return (int) Math.ceil(totalScheduledDays * REQUIRED_ACHIEVEMENT_RATE);
    }
}