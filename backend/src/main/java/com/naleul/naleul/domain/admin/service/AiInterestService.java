package com.naleul.naleul.domain.admin.service;

import com.naleul.naleul.domain.admin.dto.AiInterestResponse;
import com.naleul.naleul.domain.admin.dto.AiInterestResponse.*;
import com.naleul.naleul.domain.admin.repository.AiInterestQueryRepository;
import com.naleul.naleul.domain.admin.repository.AiInterestQueryRepository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Predicate;

import static com.naleul.naleul.domain.admin.service.AdminDashboardService.percent;

/**
 * 운영 대시보드 "AI 관심도" (V70). 정의는 노션 「Tracking Plan › AI 관심도」와 같다.
 *
 * AI 기능마다 한 번의 "시도"를 완료 · 중단 · 진행 중 셋 중 하나로 나누고, 중단은 어느 화면 · 단계에서 멈췄는지까지 센다.
 *  - AI 목표 만들기: 대화 세션 1개 = 시도 1번 (+ 한도에 막혀 시작 못 한 것). 확정 = 완료, 그만둠 · 7일 방치 = 중단
 *  - 말로 고치기 · AI 일정 조정 · 브레인 덤프: 미리보기(제안) 1번 = 시도 1번 (+ 한도에 막힌 것). 적용 = 완료
 *    제안 후 PENDING_MINUTES 안이면 아직 진행 중으로 본다 (적용과 짝짓는 시간과 같다)
 *  - 나의 패턴 제안: 반영 = 완료, 넘김 = 중단 (보여준 횟수는 기록이 없다)
 */
@Service
@RequiredArgsConstructor
public class AiInterestService {

    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    static final long PENDING_MINUTES = 120;
    static final String FREE = "FREE", PRO = "PRO";

    enum Outcome { COMPLETED, DROPPED, IN_PROGRESS }

    /** 시도 한 번 */
    record Attempt(String feature, long userId, String plan, Outcome outcome, String step, String detail) {
        boolean done() { return outcome == Outcome.COMPLETED; }
    }

    record Meta(String key, String label, String page, String completeLabel, String note) {
    }

    static final List<Meta> FEATURES = List.of(
            new Meta("GOAL_CREATE", "AI 목표 만들기", "목표 만들기 (AI 채팅 → 정리 → 초안)", "목표 확정",
                    "대화 1번 = 시도 1번. 한도에 막혀 시작 못 한 것도 시도로 셉니다."),
            new Meta("GOAL_EDIT", "말로 고치기 (목표 수정)", "목표 상세", "제안 적용",
                    "미리보기 1번 = 시도 1번. 한도에 막힌 것 포함."),
            new Meta("SCHEDULE_EDIT", "AI 일정 조정", "캘린더 (일간 · 주간)", "제안 적용",
                    "미리보기 1번 = 시도 1번. 한도에 막힌 것 포함."),
            new Meta("BRAIN_DUMP", "브레인 덤프", "Task 추가", "할 일 확정",
                    "해석 1번 = 시도 1번. 한도를 넘기면 AI 대신 규칙으로 해석해서 막히지 않아요."),
            new Meta("PATTERN", "나의 패턴 제안", "나의 패턴", "반영",
                    "보여준 횟수는 기록이 없어 반응(반영 · 넘김)만 셉니다. 플랜은 지금 플랜 기준."));

    static final Map<String, String> LIMIT_FEATURE = Map.of(
            "GOAL_INTERVIEW", "AI 목표 만들기 · 시작",
            "GOAL_PLAN", "AI 목표 만들기 · 초안 만들기",
            "GOAL_EDIT", "말로 고치기",
            "SCHEDULE_EDIT", "AI 일정 조정");
    static final Map<String, String> LIMIT_REASON = Map.of(
            "DAILY", "하루 한도",
            "WEEKLY", "주간 한도",
            "REGENERATE", "한 대화 다시 만들기 한도");

    private final AiInterestQueryRepository query;

    public AiInterestResponse summary(int requestedDays) {
        int days = Math.max(1, Math.min(AdminDashboardService.MAX_DAYS, requestedDays));
        LocalDate today = LocalDate.now(KST);
        LocalDate from = today.minusDays(days - 1L);
        Range range = new Range(serverTime(from), serverTime(today.plusDays(1)), from.atStartOfDay(),
                today.plusDays(1).atStartOfDay(), from, today);

        List<LimitRow> limits = query.limitHits(range);
        List<Attempt> attempts = new ArrayList<>();
        attempts.addAll(goalCreate(query.goalSessions(range), limits));
        attempts.addAll(suggestions(query.suggestions(range), limits, LocalDateTime.now()));
        attempts.addAll(pattern(query.patternActions(range)));
        Map<Long, String> active = query.activeUsers(range);

        return new AiInterestResponse(
                new Period(from, today, days, LocalDateTime.now(KST)),
                List.of(planSummary(FREE, attempts, active), planSummary(PRO, attempts, active)),
                FEATURES.stream().map(m -> feature(m, attempts, active)).toList(),
                drops(attempts),
                limitTable(limits),
                query.limitTrackingSince());
    }

    // ───────────── 시도 만들기 ─────────────

    static List<Attempt> goalCreate(List<SessionRow> sessions, List<LimitRow> limits) {
        List<Attempt> out = new ArrayList<>();
        for (SessionRow s : sessions) {
            String plan = plan(s.plan());
            if ("CONFIRMED".equals(s.status())) {
                out.add(new Attempt("GOAL_CREATE", s.userId(), plan, Outcome.COMPLETED, null, null));
            } else if ("ABANDONED".equals(s.status())) {
                out.add(new Attempt("GOAL_CREATE", s.userId(), plan, Outcome.DROPPED, sessionStep(s), sessionDetail(s)));
            } else {
                out.add(new Attempt("GOAL_CREATE", s.userId(), plan, Outcome.IN_PROGRESS, null, null));
            }
        }
        for (LimitRow l : limits) {
            if (!"GOAL_INTERVIEW".equals(l.feature())) continue;   // 초안 한도는 세션 안에서 막힌 것 → 한도 표에만
            out.add(new Attempt("GOAL_CREATE", l.userId(), plan(l.plan()), Outcome.DROPPED, "시작 전",
                    "WEEKLY".equals(l.reason()) ? "주간 한도에 막힘" : "하루 한도에 막힘"));
        }
        return out;
    }

    static String sessionStep(SessionRow s) {
        if ("AI_ERROR".equals(s.abandonReason()) || "AI_REJECT".equals(s.abandonReason())) return "AI 채팅 중";
        if (s.readyDrafts() > 0) return "초안 화면 (확정 안 함)";
        if (s.drafts() > 0) return "초안 만드는 중 (실패)";
        if (s.reviewEntered()) return "정리 화면";
        return "AI 채팅 중";
    }

    static String sessionDetail(SessionRow s) {
        if (s.abandonReason() == null) return s.timedOut() ? "7일 동안 이어서 안 함" : "직접 그만둠 · 기타";
        return switch (s.abandonReason()) {
            case "USER_CANCEL" -> "직접 그만둠";
            case "EXPIRED" -> "7일 동안 이어서 안 함";
            case "AI_REJECT" -> "AI가 목표로 보기 어렵다고 판단";
            case "AI_ERROR" -> "시작하자마자 AI 오류";
            default -> "기타";
        };
    }

    static List<Attempt> suggestions(List<SuggestionRow> rows, List<LimitRow> limits, LocalDateTime now) {
        List<Attempt> out = new ArrayList<>();
        LocalDateTime pendingSince = now.minusMinutes(PENDING_MINUTES);
        for (SuggestionRow r : rows) {
            String plan = plan(r.plan());
            if ("APPLIED".equals(r.status())) {
                out.add(new Attempt(r.feature(), r.userId(), plan, Outcome.COMPLETED, null, null));
            } else if (r.createdAt() != null && r.createdAt().isAfter(pendingSince)) {
                out.add(new Attempt(r.feature(), r.userId(), plan, Outcome.IN_PROGRESS, null, null));
            } else if ("BRAIN_DUMP".equals(r.feature())) {
                out.add(new Attempt(r.feature(), r.userId(), plan, Outcome.DROPPED, "해석 결과 확인",
                        r.validCount() == 0 ? "해석된 할 일이 없음"
                                : !r.aiUsed() ? "AI 대신 규칙으로 해석 → 확정 안 함" : "확정 안 하고 나감"));
            } else {
                out.add(new Attempt(r.feature(), r.userId(), plan, Outcome.DROPPED, "미리보기",
                        r.validCount() == 0 ? "쓸 수 있는 제안이 없음" : "제안을 보고 적용 안 함"));
            }
        }
        for (LimitRow l : limits) {
            if (!"GOAL_EDIT".equals(l.feature()) && !"SCHEDULE_EDIT".equals(l.feature())) continue;
            out.add(new Attempt(l.feature(), l.userId(), plan(l.plan()), Outcome.DROPPED, "요청", "하루 한도에 막힘"));
        }
        return out;
    }

    static List<Attempt> pattern(List<PatternRow> rows) {
        return rows.stream().map(r -> "APPLIED".equals(r.action())
                ? new Attempt("PATTERN", r.userId(), plan(r.plan()), Outcome.COMPLETED, null, null)
                : new Attempt("PATTERN", r.userId(), plan(r.plan()), Outcome.DROPPED, "제안 카드", "넘김")).toList();
    }

    // ───────────── 집계 ─────────────

    static PlanSummary planSummary(String plan, List<Attempt> attempts, Map<Long, String> active) {
        Set<Long> base = new HashSet<>();
        active.forEach((uid, p) -> { if (plan.equals(plan(p))) base.add(uid); });
        Map<Long, Set<String>> tried = new HashMap<>();
        Set<Long> completed = new HashSet<>();
        for (Attempt a : attempts) {
            if (!base.contains(a.userId())) continue;   // 요약은 지금 플랜 기준
            tried.computeIfAbsent(a.userId(), k -> new HashSet<>()).add(a.feature());
            if (a.done()) completed.add(a.userId());
        }
        Double avg = tried.isEmpty() ? null
                : Math.round(10.0 * tried.values().stream().mapToInt(Set::size).sum() / tried.size()) / 10.0;
        return new PlanSummary(plan, base.size(), tried.size(), percent(tried.size(), base.size()), completed.size(),
                percent(completed.size(), base.size()), avg);
    }

    static Feature feature(Meta m, List<Attempt> attempts, Map<Long, String> active) {
        List<Attempt> mine = attempts.stream().filter(a -> a.feature().equals(m.key())).toList();
        return new Feature(m.key(), m.label(), m.page(), m.completeLabel(),
                planStat(mine, FREE, active), planStat(mine, PRO, active), m.note());
    }

    static PlanStat planStat(List<Attempt> featureAttempts, String plan, Map<Long, String> active) {
        List<Attempt> list = featureAttempts.stream().filter(a -> plan.equals(a.plan())).toList();
        long activeOfPlan = active.values().stream().filter(p -> plan.equals(plan(p))).count();
        long users = list.stream().map(Attempt::userId).distinct().count();
        long n = list.size();
        long done = count(list, Attempt::done);
        long dropped = count(list, a -> a.outcome() == Outcome.DROPPED);
        long pending = count(list, a -> a.outcome() == Outcome.IN_PROGRESS);
        Map<Long, Long> doneByUser = new HashMap<>();
        list.stream().filter(Attempt::done).forEach(a -> doneByUser.merge(a.userId(), 1L, Long::sum));
        long repeat = doneByUser.values().stream().filter(c -> c >= 2).count();
        return new PlanStat(users, percent(users, activeOfPlan), n, done, percent(done, n), dropped, percent(dropped, n),
                pending, doneByUser.size(), repeat);
    }

    static List<Drop> drops(List<Attempt> attempts) {
        List<Drop> out = new ArrayList<>();
        for (Meta m : FEATURES) {
            List<Attempt> mine = attempts.stream().filter(a -> a.feature().equals(m.key())).toList();
            Map<String, long[]> byStep = new LinkedHashMap<>();   // [free, pro]
            for (Attempt a : mine) {
                if (a.outcome() != Outcome.DROPPED) continue;
                long[] c = byStep.computeIfAbsent(a.step() + "\u0000" + a.detail(), k -> new long[2]);
                c[PRO.equals(a.plan()) ? 1 : 0]++;
            }
            long freeN = count(mine, a -> FREE.equals(a.plan())), proN = count(mine, a -> PRO.equals(a.plan()));
            long freeD = count(mine, a -> FREE.equals(a.plan()) && a.outcome() == Outcome.DROPPED);
            long proD = count(mine, a -> PRO.equals(a.plan()) && a.outcome() == Outcome.DROPPED);
            byStep.entrySet().stream()
                    .sorted((x, y) -> Long.compare(y.getValue()[0] + y.getValue()[1], x.getValue()[0] + x.getValue()[1]))
                    .forEach(e -> {
                        String[] k = e.getKey().split("\u0000", 2);
                        long f = e.getValue()[0], p = e.getValue()[1];
                        out.add(new Drop(m.key(), m.label(), m.page(), k[0], k[1],
                                new DropStat(f, percent(f, freeD), percent(f, freeN)),
                                new DropStat(p, percent(p, proD), percent(p, proN))));
                    });
        }
        return out;
    }

    static List<Limit> limitTable(List<LimitRow> limits) {
        Map<String, List<LimitRow>> by = new LinkedHashMap<>();
        for (String f : List.of("GOAL_INTERVIEW", "GOAL_PLAN", "GOAL_EDIT", "SCHEDULE_EDIT")) {
            for (String r : List.of("DAILY", "WEEKLY", "REGENERATE")) by.put(f + ":" + r, new ArrayList<>());
        }
        for (LimitRow l : limits) by.computeIfAbsent(l.feature() + ":" + l.reason(), k -> new ArrayList<>()).add(l);
        List<Limit> out = new ArrayList<>();
        by.forEach((key, rows) -> {
            if (rows.isEmpty()) return;
            String[] k = key.split(":", 2);
            List<LimitRow> free = rows.stream().filter(l -> FREE.equals(plan(l.plan()))).toList();
            List<LimitRow> pro = rows.stream().filter(l -> PRO.equals(plan(l.plan()))).toList();
            out.add(new Limit(k[0], LIMIT_FEATURE.getOrDefault(k[0], k[0]), k[1], LIMIT_REASON.getOrDefault(k[1], k[1]),
                    free.size(), free.stream().map(LimitRow::userId).distinct().count(),
                    pro.size(), pro.stream().map(LimitRow::userId).distinct().count()));
        });
        return out;
    }

    // ───────────── 공통 ─────────────

    static String plan(String p) {
        return PRO.equals(p) ? PRO : FREE;
    }

    private static <T> long count(List<T> list, Predicate<T> p) {
        return list.stream().filter(p).count();
    }

    private static LocalDateTime serverTime(LocalDate kstDate) {
        return kstDate.atStartOfDay(KST).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }
}
