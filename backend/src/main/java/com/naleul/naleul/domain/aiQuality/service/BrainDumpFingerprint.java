package com.naleul.naleul.domain.aiQuality.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.naleul.naleul.domain.brainDump.dto.request.BrainDumpConfirmRequest;
import com.naleul.naleul.domain.brainDump.dto.response.BrainDumpParseResponse;
import com.naleul.naleul.domain.brainDump.enums.DateType;
import com.naleul.naleul.domain.brainDump.enums.GoalMatchType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 브레인 덤프 "AI 해석 결과"와 "사용자가 확정한 값"을 비교하는 지문.
 * 항목(clientKey)마다 제목 해시 · 날짜 · 시작 시각 · 연결 목표만 남긴다 — 제목 원문은 저장하지 않는다.
 */
public final class BrainDumpFingerprint {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 항목 하나의 지문 — t: 제목 해시, d: 날짜 종류+날짜, s: 시작 시각, g: 목표(G{id} · T{tempKey} · N) */
    public record Item(String t, String d, String s, String g) {
    }

    /** 비교 결과 — edited: 하나라도 바뀐 항목(직접 추가한 항목 포함), goalChanged: 연결 목표가 바뀐 항목 */
    public record Diff(int edited, int goalChanged) {
    }

    private BrainDumpFingerprint() {
    }

    public static String of(BrainDumpParseResponse parsed) {
        Map<String, Item> map = new LinkedHashMap<>();
        for (BrainDumpParseResponse.Item i : parsed.items()) {
            map.put(i.clientKey(), new Item(hash(i.title()), date(i.dateType(), i.scheduledDate(), i.dueDate()),
                    time(i.startTime()), goal(i.goal())));
        }
        try {
            return MAPPER.writeValueAsString(map);
        } catch (Exception e) {
            return null;
        }
    }

    public static Diff diff(String snapshotJson, List<BrainDumpConfirmRequest.Item> confirmed) {
        Map<String, Item> before;
        try {
            before = snapshotJson == null ? Map.of() : MAPPER.readValue(snapshotJson, new TypeReference<Map<String, Item>>() {
            });
        } catch (Exception e) {
            before = Map.of();
        }
        int edited = 0, goalChanged = 0;
        for (BrainDumpConfirmRequest.Item i : confirmed) {
            Item b = before.get(i.clientKey());
            String g = i.goalCategoryId() != null ? "G" + i.goalCategoryId()
                    : i.tempGoalKey() != null && !i.tempGoalKey().isBlank() ? "T" + i.tempGoalKey() : "N";
            if (b == null) {           // AI 해석 뒤에 직접 추가한 항목
                edited++;
                continue;
            }
            boolean goalDiff = !Objects.equals(b.g(), g);
            boolean diff = goalDiff
                    || !Objects.equals(b.t(), hash(i.title()))
                    || !Objects.equals(b.d(), date(i.dateType(), i.scheduledDate(), i.dueDate()))
                    || !Objects.equals(b.s(), time(i.startTime()));
            if (diff) edited++;
            if (goalDiff) goalChanged++;
        }
        return new Diff(edited, goalChanged);
    }

    private static String goal(BrainDumpParseResponse.GoalLink link) {
        if (link == null || link.type() == null || link.type() == GoalMatchType.NONE) return "N";
        if (link.goalCategoryId() != null) return "G" + link.goalCategoryId();
        if (link.tempKey() != null) return "T" + link.tempKey();
        return "N";
    }

    private static String date(DateType type, LocalDate scheduled, LocalDate due) {
        if (type == null) return null;
        LocalDate d = type == DateType.DUE ? due : scheduled;
        return type.name() + ":" + (d != null ? d : "");
    }

    private static String time(LocalTime t) {
        return t == null ? null : String.format("%02d:%02d", t.getHour(), t.getMinute());
    }

    static String hash(String title) {
        if (title == null) return null;
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(title.strip().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(title.strip().hashCode());
        }
    }
}
