package com.naleul.naleul.domain.admin.service;

import com.naleul.naleul.domain.admin.dto.ChatReviewItemsResponse;
import com.naleul.naleul.domain.admin.dto.ChatReviewResponse;
import com.naleul.naleul.domain.admin.dto.ChatReviewResponse.*;
import com.naleul.naleul.domain.admin.repository.ChatReviewQueryRepository;
import com.naleul.naleul.domain.admin.repository.ChatReviewQueryRepository.*;
import com.naleul.naleul.domain.goalCreation.enums.ReviewField;
import com.naleul.naleul.domain.goalCreation.support.GoalJsonConverter;
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
 * 운영 대시보드 "AI 채팅 정리 정확도" (V67). 지표 정의는 노션 「리텐션 향상 › 지표 체계 › 8」과 같다.
 * 수정률 · 누락률 · 진행률을 항목별(7개)과 전체(세션 단위) 두 단계로.
 */
@Service
@RequiredArgsConstructor
public class ChatReviewService {

    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    static final int MIN_SAMPLE = 30;
    static final int PAGE_SIZE = 20;

    private final ChatReviewQueryRepository query;
    private final GoalJsonConverter jsonConverter;

    public ChatReviewResponse summary(int requestedDays, String goalType, String promptVersion, String answerSource) {
        int days = clampDays(requestedDays);
        LocalDate today = LocalDate.now(KST);
        LocalDate from = today.minusDays(days - 1L);
        Filter f = filter(from, today, goalType, promptVersion, answerSource);

        Map<String, FieldStat> stats = query.fieldStats(f).stream()
                .collect(Collectors.toMap(FieldStat::field, Function.identity()));
        List<FieldRow> rows = Arrays.stream(ReviewField.values()).map(field -> row(field, stats.get(field.name()))).toList();

        SessionStat s = query.sessionStats(f);
        Overall overall = new Overall(
                percent(s.untouched(), s.sessions()), percent(s.editedSessions(), s.sessions()),
                percent(s.addedSessions(), s.sessions()),
                s.untouched(), s.editedSessions(), s.addedSessions(),
                s.editedSessions() == 0 ? null
                        : BigDecimal.valueOf(s.editedItems()).divide(BigDecimal.valueOf(s.editedSessions()), 1, RoundingMode.HALF_UP));

        ExitStat e = query.exit(f);
        Exit exit = new Exit(e.entered(), e.reviewed(), e.exited(), e.pending(),
                percent(e.exited(), e.entered() - e.pending()));

        return new ChatReviewResponse(new Period(from, today, days, LocalDateTime.now(KST)),
                new Filters(f.goalType(), f.promptVersion(), f.answerSource()),
                s.sessions(), overall, rows, exit, query.promptVersions(), MIN_SAMPLE);
    }

    /**
     * @param result CHANGED(고침 + 직접 채움, 기본) · EDITED · ADDED
     */
    public ChatReviewItemsResponse items(String fieldKey, int requestedDays, String result, String goalType,
                                         String promptVersion, String answerSource, int page) {
        ReviewField field = parseField(fieldKey);
        int days = clampDays(requestedDays);
        LocalDate today = LocalDate.now(KST);
        Filter f = filter(today.minusDays(days - 1L), today, goalType, promptVersion, answerSource);
        List<String> results = switch (result == null ? "CHANGED" : result.toUpperCase(Locale.ROOT)) {
            case "EDITED" -> List.of("EDITED");
            case "ADDED" -> List.of("ADDED");
            default -> List.of("EDITED", "ADDED");
        };
        int p = Math.max(0, page);
        FieldStat stat = query.fieldStats(f).stream().filter(x -> x.field().equals(field.name())).findFirst().orElse(null);
        List<ChatReviewItemsResponse.Item> items = query.items(f, field.name(), results, PAGE_SIZE, p * PAGE_SIZE).stream()
                .map(r -> new ChatReviewItemsResponse.Item(kst(r.at()), r.result(), r.question(),
                        jsonConverter.toStringList(r.chipsJson()), r.answer(), r.answerSource(),
                        r.aiValue(), r.finalValue(), r.aiValue() != null || r.finalValue() != null))
                .toList();
        return new ChatReviewItemsResponse(row(field, stat), p, PAGE_SIZE, query.itemCount(f, field.name(), results), items);
    }

    static FieldRow row(ReviewField field, FieldStat s) {
        long aiFilled = s == null ? 0 : s.aiFilled();
        long edited = s == null ? 0 : s.edited();
        long chip = s == null ? 0 : s.chip();
        long typed = s == null ? 0 : s.typed();
        long emptyBase = s == null ? 0 : s.emptyBase();
        long added = s == null ? 0 : s.added();
        boolean askedSeparately = field != ReviewField.PLANNING_STYLE;   // 실행 성향은 실천 시간 질문에서 함께 물어
        return new FieldRow(field.name(), field.label(), aiFilled, edited, percent(edited, aiFilled),
                askedSeparately ? chip : 0, askedSeparately ? typed : 0,
                askedSeparately ? percent(chip, chip + typed) : null,
                askedSeparately ? percent(typed, chip + typed) : null,
                field.missMeasured() ? emptyBase : 0, field.missMeasured() ? added : 0,
                field.missMeasured() ? percent(added, emptyBase) : null,
                percent(aiFilled - edited, aiFilled));
    }

    private static Filter filter(LocalDate from, LocalDate today, String goalType, String promptVersion, String answerSource) {
        String src = answerSource == null || answerSource.isBlank() ? null : answerSource.toUpperCase(Locale.ROOT);
        if (src != null && !src.equals("CHIP") && !src.equals("TYPED")) src = null;
        return new Filter(serverTime(from), serverTime(today.plusDays(1)), blankToNull(goalType), blankToNull(promptVersion), src);
    }

    private static ReviewField parseField(String key) {
        try {
            return ReviewField.valueOf(key.toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private static int clampDays(int days) {
        return Math.max(1, Math.min(AdminDashboardService.MAX_DAYS, days));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    /** 서버 시각 → 한국 시각 (화면은 받은 시각을 그대로 보여줘요) */
    private static LocalDateTime kst(LocalDateTime serverTime) {
        return serverTime.atZone(ZoneId.systemDefault()).withZoneSameInstant(KST).toLocalDateTime();
    }

    private static LocalDateTime serverTime(LocalDate kstDate) {
        return kstDate.atStartOfDay(KST).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }
}
