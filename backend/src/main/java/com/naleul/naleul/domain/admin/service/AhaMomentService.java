package com.naleul.naleul.domain.admin.service;

import com.naleul.naleul.domain.admin.dto.AhaMomentResponse;
import com.naleul.naleul.domain.admin.dto.AhaMomentResponse.*;
import com.naleul.naleul.domain.admin.repository.AhaMomentQueryRepository;
import com.naleul.naleul.domain.admin.repository.AhaMomentQueryRepository.Signup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.function.Predicate;

import static com.naleul.naleul.domain.admin.service.AdminDashboardService.percent;

/**
 * 운영 대시보드 "Aha-moment" (V69). 정의는 노션 「리텐션 향상 › Aha-moment」와 같다.
 * 가입 후 7일 안에 ① 목표 2개 생성 ② Task 3개 이상 완료(루틴 · 일반) ③ 서로 다른 3일 이상 방문 → 셋 다 = Aha 도달.
 * 검증: 도달자와 미도달자의 D14 리텐션 차이가 크고, 도달률이 10~30% 인지 본다.
 */
@Service
@RequiredArgsConstructor
public class AhaMomentService {

    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    static final int GOALS = 2, TASKS = 3, VISIT_DAYS = 3;
    /** 가입 후 이 날이 지나야 판정 (D0 ~ D6) */
    static final int WINDOW = 7;
    /** D14 리텐션 = 가입 14~20일째 */
    static final int D14_FROM = 14, D14_TO = 20;
    static final int MIN_SAMPLE = 30;

    private final AhaMomentQueryRepository query;

    /** 사용자 한 명의 첫 7일 */
    record UserWeek(LocalDate d0, long goals, long tasks, long days, boolean eligible, Boolean retained) {
        boolean g() { return goals >= GOALS; }
        boolean t() { return tasks >= TASKS; }
        boolean v() { return days >= VISIT_DAYS; }
        boolean aha() { return g() && t() && v(); }
    }

    public AhaMomentResponse summary(int requestedDays) {
        int days = Math.max(1, Math.min(AdminDashboardService.MAX_DAYS, requestedDays));
        LocalDate today = LocalDate.now(KST);
        LocalDate from = today.minusDays(days - 1L);
        LocalDateTime fromAt = serverTime(from), toAt = serverTime(today.plusDays(1));
        int off = kstOffsetMinutes();

        List<Signup> signups = query.signups(fromAt, toAt, off);
        Map<Long, Long> goals = query.goalsWithin7Days(fromAt, toAt, off);
        Map<Long, Long> tasks = query.tasksWithin7Days(fromAt, toAt, off);
        Map<Long, Long> active = query.activeDays(fromAt, toAt, off, 0, WINDOW - 1);
        Map<Long, Long> d14 = query.activeDays(fromAt, toAt, off, D14_FROM, D14_TO);

        List<UserWeek> users = signups.stream().map(s -> {
            boolean eligible = !s.signupDay().plusDays(WINDOW).isAfter(today);          // D0 + 7 ≤ 오늘
            boolean d14Ready = !s.signupDay().plusDays(D14_TO + 1L).isAfter(today);     // D0 + 21 ≤ 오늘
            return new UserWeek(s.signupDay(), goals.getOrDefault(s.userId(), 0L), tasks.getOrDefault(s.userId(), 0L),
                    active.getOrDefault(s.userId(), 0L), eligible,
                    d14Ready ? d14.getOrDefault(s.userId(), 0L) > 0 : null);
        }).toList();
        List<UserWeek> el = users.stream().filter(UserWeek::eligible).toList();
        long n = el.size();

        Rate aha = new Rate(count(el, UserWeek::aha), percent(count(el, UserWeek::aha), n));
        List<Condition> conditions = List.of(
                new Condition("GOALS", "목표 " + GOALS + "개 생성", count(el, UserWeek::g), percent(count(el, UserWeek::g), n)),
                new Condition("TASKS", "Task " + TASKS + "개 이상 완료", count(el, UserWeek::t), percent(count(el, UserWeek::t), n)),
                new Condition("VISITS", "서로 다른 " + VISIT_DAYS + "일 이상 방문", count(el, UserWeek::v),
                        percent(count(el, UserWeek::v), n)));
        long s1 = count(el, UserWeek::g), s2 = count(el, u -> u.g() && u.t()), s3 = aha.users();
        List<Step> funnel = List.of(
                new Step("① 목표 " + GOALS + "개", s1, percent(s1, n)),
                new Step("① + ② Task " + TASKS + "개 완료", s2, percent(s2, n)),
                new Step("① + ② + ③ 3일 방문 = Aha", s3, percent(s3, n)));

        List<UserWeek> d14Base = el.stream().filter(u -> u.retained() != null).toList();
        long rb = count(d14Base, UserWeek::aha), rr = count(d14Base, u -> u.aha() && u.retained());
        long nb = d14Base.size() - rb, nr = count(d14Base, u -> !u.aha() && u.retained());
        Integer rRate = percent(rr, rb), nRate = percent(nr, nb);
        Retention retention = new Retention(rb, rr, rRate, nb, nr, nRate,
                rRate != null && nRate != null ? rRate - nRate : null,
                d14Base.size(), percent(rr + nr, d14Base.size()));

        Distributions dist = new Distributions(
                buckets(el, UserWeek::goals, new long[]{0, 1, 2, 3}, new String[]{"0개", "1개", "2개", "3개+"}),
                buckets(el, UserWeek::tasks, new long[]{0, 1, 3, 6}, new String[]{"0개", "1~2개", "3~5개", "6개+"}),
                buckets(el, UserWeek::days, new long[]{0, 1, 2, 3, 4}, new String[]{"0일", "1일", "2일", "3일", "4일+"}));

        // 가입 주(월요일 시작)별
        TreeMap<LocalDate, List<UserWeek>> byWeek = new TreeMap<>(Comparator.reverseOrder());
        for (UserWeek u : users) {
            byWeek.computeIfAbsent(u.d0().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), k -> new ArrayList<>()).add(u);
        }
        List<Cohort> cohorts = byWeek.entrySet().stream().limit(12).map(en -> {
            List<UserWeek> all = en.getValue();
            List<UserWeek> e = all.stream().filter(UserWeek::eligible).toList();
            List<UserWeek> r = e.stream().filter(u -> u.retained() != null).toList();
            long ra = count(r, UserWeek::aha);
            return new Cohort(en.getKey(), all.size(), e.size(),
                    percent(count(e, UserWeek::g), e.size()), percent(count(e, UserWeek::t), e.size()),
                    percent(count(e, UserWeek::v), e.size()), percent(count(e, UserWeek::aha), e.size()),
                    percent(count(r, u -> u.aha() && u.retained()), ra),
                    percent(count(r, u -> !u.aha() && u.retained()), r.size() - ra));
        }).toList();

        return new AhaMomentResponse(new Period(from, today, days, LocalDateTime.now(KST)),
                users.size(), n, users.size() - n, aha, conditions, funnel, retention, dist, cohorts,
                query.visitTrackingSince(), MIN_SAMPLE);
    }

    private static long count(List<UserWeek> list, Predicate<UserWeek> p) {
        return list.stream().filter(p).count();
    }

    /** lows[i] 이상 lows[i+1] 미만 (마지막은 이상) */
    private static List<Bucket> buckets(List<UserWeek> list, java.util.function.ToLongFunction<UserWeek> value,
                                        long[] lows, String[] labels) {
        long[] counts = new long[lows.length];
        for (UserWeek u : list) {
            long v = value.applyAsLong(u);
            for (int i = lows.length - 1; i >= 0; i--) {
                if (v >= lows[i]) {
                    counts[i]++;
                    break;
                }
            }
        }
        List<Bucket> out = new ArrayList<>();
        for (int i = 0; i < lows.length; i++) out.add(new Bucket(labels[i], counts[i], percent(counts[i], list.size())));
        return out;
    }

    private static LocalDateTime serverTime(LocalDate kstDate) {
        return kstDate.atStartOfDay(KST).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }

    private static int kstOffsetMinutes() {
        Instant now = Instant.now();
        return (KST.getRules().getOffset(now).getTotalSeconds()
                - ZoneId.systemDefault().getRules().getOffset(now).getTotalSeconds()) / 60;
    }
}
