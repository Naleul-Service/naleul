package com.naleul.naleul.domain.admin.service;

import com.naleul.naleul.domain.admin.dto.DraftReviewItemsResponse;
import com.naleul.naleul.domain.admin.dto.DraftReviewResponse;
import com.naleul.naleul.domain.admin.dto.DraftReviewResponse.*;
import com.naleul.naleul.domain.admin.repository.DraftReviewQueryRepository;
import com.naleul.naleul.domain.admin.repository.DraftReviewQueryRepository.*;
import com.naleul.naleul.domain.goalCreation.enums.DraftField;
import com.naleul.naleul.domain.goalCreation.enums.DraftItemKind;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.naleul.naleul.domain.admin.service.AdminDashboardService.percent;

/**
 * 운영 대시보드 "AI 초안 정확도" (V68). 지표 정의는 노션 「리텐션 향상 › 지표 체계」 · 피그마 "AI 초안 생성 정확도"와 같다.
 *  - 대표: 초안 보존율 · 다시 생성률(실패 신호) · 유지율(첫 행동부터 14 · 30일)
 *  - 세션 경로 4가지 · 말로 고치기 반복 · 수정 정도별 실천률 · 항목별 · 자주 고치는 방향 · 루틴 시간 변화
 */
@Service
@RequiredArgsConstructor
public class DraftReviewService {

    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    static final int MIN_SAMPLE = 30;
    static final int PAGE_SIZE = 20;
    /** 확정 뒤 "아직 안 씀"을 판단하는 기간 */
    static final int NOT_USED_DAYS = 14;
    static final List<String> PATHS = List.of("AS_IS", "MANUAL", "QUICK_FIX", "REGEN");
    static final List<String> BUCKETS = List.of("NEAR", "SOME", "MANY");
    static final Set<String> TRACKED_KINDS = Set.of("ROUTINE", "ONE_TIME");

    private final DraftReviewQueryRepository query;

    public DraftReviewResponse summary(int requestedDays, String goalType, String intensity, String planningStyle,
                                       String promptVersion, int window) {
        int days = clampDays(requestedDays);
        LocalDate today = LocalDate.now(KST);
        LocalDate from = today.minusDays(days - 1L);
        Filter f = filter(from, today, goalType, intensity, planningStyle, promptVersion, window);

        SessionStat s = query.sessions(f, serverNow().minusDays(NOT_USED_DAYS));
        Entered e = query.entered(f);
        Map<String, Retention> retByPath = query.retention(f, "r.path").stream()
                .collect(Collectors.toMap(Retention::key, Function.identity()));
        Map<String, Retention> retByKind = query.retention(f, "i.item_kind").stream()
                .collect(Collectors.toMap(Retention::key, Function.identity()));
        long evaluated = retByKind.values().stream().mapToLong(Retention::evaluated).sum();
        long retained = retByKind.values().stream().mapToLong(Retention::kept).sum();

        Headline headline = new Headline(percent(s.keptFields(), s.aiFields()), s.keptFields(), s.aiFields(),
                percent(e.regenSessions(), e.entered()), e.regenSessions(), e.entered(),
                percent(retained, evaluated), retained, evaluated);
        Funnel funnel = new Funnel(e.entered(), e.confirmed(), e.exited(), e.pending(),
                percent(e.exited(), e.entered() - e.pending()));

        Map<String, PathStat> pathStats = query.paths(f).stream().collect(Collectors.toMap(PathStat::path, Function.identity()));
        List<PathRow> paths = PATHS.stream().map(p -> {
            PathStat ps = pathStats.get(p);
            Retention rt = retByPath.get(p);
            long n = ps == null ? 0 : ps.sessions();
            return new PathRow(p, n, percent(n, s.sessions()),
                    ps == null ? null : percent(ps.done(), ps.planned()), ps == null ? 0 : ps.planned(),
                    rt == null ? null : percent(rt.kept(), rt.evaluated()), rt == null ? 0 : rt.evaluated());
        }).toList();

        QuickFixStat q = query.quickFix(f);
        QuickFix quickFix = new QuickFix(q.sessions(), q.once(), q.twice(), q.threePlus(),
                percent(e.qfSucceeded(), e.qfFinished()), e.qfSucceeded(), e.qfFinished(), e.qfThenRegen(), s.baseEditCount());

        Map<String, BucketStat> bucketStats = query.buckets(f).stream()
                .collect(Collectors.toMap(BucketStat::bucket, Function.identity()));
        List<EditBucket> buckets = BUCKETS.stream().map(b -> {
            BucketStat bs = bucketStats.get(b);
            long n = bs == null ? 0 : bs.sessions();
            return new EditBucket(b, n, percent(n, s.sessions()),
                    bs == null ? null : percent(bs.done(), bs.planned()), bs == null ? 0 : bs.planned(),
                    bs == null ? null : percent(bs.achieved(), bs.ended()), bs == null ? 0 : bs.ended());
        }).toList();

        List<FieldStat> fieldStats = query.fields(f, null);
        Map<String, ItemStat> itemStats = query.items(f).stream().collect(Collectors.toMap(ItemStat::kind, Function.identity()));
        List<ItemRow> items = Arrays.stream(DraftItemKind.values()).map(k -> {
            ItemStat it = itemStats.get(k.name());
            FieldStat top = fieldStats.stream().filter(x -> x.kind().equals(k.name()) && x.edited() > 0)
                    .max(Comparator.comparingLong(FieldStat::edited)).orElse(null);
            Retention rt = retByKind.get(k.name());
            long ai = it == null ? 0 : it.aiItems();
            return new ItemRow(k.name(), kindLabel(k.name()), ai,
                    it == null ? 0 : it.edited(), it == null ? 0 : it.deleted(), it == null ? 0 : it.added(),
                    it == null ? null : percent(it.edited(), ai), it == null ? null : percent(it.deleted(), ai),
                    it == null ? null : percent(it.added(), ai), it == null ? null : percent(it.kept(), ai),
                    top == null ? null : top.field(), top == null ? null : fieldLabel(top.field()),
                    top == null ? null : percent(top.edited(), top.base()),
                    TRACKED_KINDS.contains(k.name()) && rt != null ? percent(rt.kept(), rt.evaluated()) : null,
                    rt == null ? 0 : rt.evaluated());
        }).toList();

        Map<String, FieldStat> fieldByKey = fieldStats.stream()
                .collect(Collectors.toMap(x -> x.kind() + "|" + x.field(), Function.identity(), (a, b) -> a));
        List<DirectionRow> directions = query.directions(f).stream()
                .map(d -> {
                    FieldStat base = fieldByKey.get(d.kind() + "|" + d.field());
                    ItemStat it = itemStats.get(d.kind());
                    return new DirectionRow(d.kind(), d.field(), kindLabel(d.kind()) + " · " + fieldLabel(d.field()),
                            d.direction(), d.count(), base == null ? null : percent(d.count(), base.base()),
                            d.avgDelta() == null ? null : d.avgDelta().setScale(1, RoundingMode.HALF_UP),
                            percent(d.done(), d.planned()), d.planned(),
                            it == null ? null : percent(it.keptDone(), it.keptPlanned()), it == null ? 0 : it.keptPlanned());
                })
                .sorted(Comparator.comparingLong(DirectionRow::count).reversed())
                .limit(12)
                .toList();

        List<WeeklyTimeRow> weekly = query.weekly(f).stream()
                .map(w -> new WeeklyTimeRow(w.intensity(), w.sessions(), w.draftMinutes(), w.confirmedMinutes(),
                        percent(w.confirmedMinutes(), w.draftMinutes())))
                .sorted(Comparator.comparing(WeeklyTimeRow::intensity).reversed())   // STEADY → CHALLENGE
                .toList();

        return new DraftReviewResponse(new Period(from, today, days, LocalDateTime.now(KST)),
                new Filters(f.goalType(), f.intensity(), f.planningStyle(), f.promptVersion()), f.window(),
                s.sessions(), headline, funnel, paths, quickFix, buckets, items, directions, weekly,
                new NotUsed(s.notUsed(), s.notUsedBase(), percent(s.notUsed(), s.notUsedBase())),
                query.promptVersions(), MIN_SAMPLE);
    }

    /**
     * @param result ALL(기본: 확정 전 고침 · 삭제 · 추가 + 확정 뒤) · EDITED · DELETED · ADDED · AFTER(확정 뒤 고침 · 삭제)
     */
    public DraftReviewItemsResponse items(String kindKey, int requestedDays, String result, String goalType, String intensity,
                                          String planningStyle, String promptVersion, int window, int page) {
        String kind = parseKind(kindKey);
        int days = clampDays(requestedDays);
        LocalDate today = LocalDate.now(KST);
        Filter f = filter(today.minusDays(days - 1L), today, goalType, intensity, planningStyle, promptVersion, window);
        String r = result == null ? "ALL" : result.toUpperCase(Locale.ROOT);
        List<String> results = switch (r) {
            case "EDITED" -> List.of("EDITED");
            case "DELETED" -> List.of("DELETED");
            case "ADDED" -> List.of("ADDED");
            case "AFTER" -> List.of();
            default -> List.of("EDITED", "DELETED", "ADDED");
        };
        boolean after = r.equals("AFTER") || r.equals("ALL");
        int p = Math.max(0, page);

        List<DraftReviewItemsResponse.FieldStat> fields = query.fields(f, kind).stream()
                .sorted(Comparator.comparingLong(FieldStat::edited).reversed())
                .map(x -> new DraftReviewItemsResponse.FieldStat(x.field(), fieldLabel(x.field()), x.base(), x.edited(),
                        percent(x.edited(), x.base()), x.afterBase(), x.afterEdited(), percent(x.afterEdited(), x.afterBase())))
                .toList();
        List<DraftReviewItemsResponse.Case> cases = query.cases(f, kind, results, after, PAGE_SIZE, p * PAGE_SIZE).stream()
                .map(c -> new DraftReviewItemsResponse.Case(c.at() == null ? null : kst(c.at()), c.stage(), c.result(),
                        c.label(), c.field(), fieldLabel(c.field()), c.path(), c.request(), c.aiValue(), c.finalValue(),
                        c.direction(), c.delta()))
                .toList();
        return new DraftReviewItemsResponse(kind, kindLabel(kind), fields, p, PAGE_SIZE,
                query.caseCount(f, kind, results, after), cases);
    }

    static String kindLabel(String kind) {
        return switch (kind) {
            case "GOAL" -> "목표";
            case "ROUTINE" -> "루틴";
            case "ONE_TIME" -> "일회성 Task";
            case "SUB_GOAL" -> "세부 목표";
            case "MILESTONE" -> "마일스톤";
            default -> kind;
        };
    }

    static String fieldLabel(String field) {
        try {
            return DraftField.valueOf(field).label();
        } catch (RuntimeException e) {
            return field;
        }
    }

    private static Filter filter(LocalDate from, LocalDate today, String goalType, String intensity, String planningStyle,
                                 String promptVersion, int window) {
        return new Filter(serverTime(from), serverTime(today.plusDays(1)), upperOrNull(goalType),
                oneOf(intensity, "STEADY", "CHALLENGE"), oneOf(planningStyle, "PLANNER", "SPONTANEOUS"),
                blankToNull(promptVersion), window == 30 ? 30 : 14);
    }

    private static String parseKind(String key) {
        try {
            return DraftItemKind.valueOf(key.toUpperCase(Locale.ROOT)).name();
        } catch (RuntimeException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private static int clampDays(int days) {
        return Math.max(1, Math.min(AdminDashboardService.MAX_DAYS, days));
    }

    private static String oneOf(String v, String... allowed) {
        if (v == null || v.isBlank()) return null;
        String u = v.strip().toUpperCase(Locale.ROOT);
        return Arrays.asList(allowed).contains(u) ? u : null;
    }

    private static String upperOrNull(String s) {
        return s == null || s.isBlank() ? null : s.strip().toUpperCase(Locale.ROOT);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static LocalDateTime serverNow() {
        return LocalDateTime.now();
    }

    private static LocalDateTime kst(LocalDateTime serverTime) {
        return serverTime.atZone(ZoneId.systemDefault()).withZoneSameInstant(KST).toLocalDateTime();
    }

    private static LocalDateTime serverTime(LocalDate kstDate) {
        return kstDate.atStartOfDay(KST).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }
}
