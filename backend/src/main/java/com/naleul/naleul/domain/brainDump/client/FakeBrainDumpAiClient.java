package com.naleul.naleul.domain.brainDump.client;

import com.naleul.naleul.domain.brainDump.enums.DateType;
import com.naleul.naleul.domain.brainDump.enums.GoalMatchType;
import com.naleul.naleul.domain.brainDump.support.KoreanTaskTextParser;
import com.naleul.naleul.domain.goalCreation.client.AiCallMeta;
import com.naleul.naleul.domain.task.enums.TaskKind;
import com.naleul.naleul.domain.task.enums.TaskPriority;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 로컬·테스트용 가짜 AI (goal.ai.client=fake). AI 호출 없이 규칙으로 해석한다.
 *  - 날짜·시간: KoreanTaskTextParser
 *  - 목표 연결: 목표/세부 목표 이름의 단어(2글자 이상)가 문장에 들어 있으면 그 목표
 *  - 못 찾으면 전부 임시 목표 "새 목표"(📌) 하나로 묶는다
 */
@Component
@ConditionalOnProperty(prefix = "goal.ai", name = "client", havingValue = "fake")
public class FakeBrainDumpAiClient implements BrainDumpAiClient {

    static final String TEMP_KEY = "t1";

    @Override
    public BrainDumpAiResult parse(BrainDumpAiRequest request) {
        List<BrainDumpAiResult.Item> items = new ArrayList<>();
        boolean usedTemp = false;
        for (BrainDumpAiRequest.Item in : request.items()) {
            KoreanTaskTextParser.Parsed p = KoreanTaskTextParser.parse(in.text(), request.today());
            BrainDumpAiResult.Goal goal = match(in.text(), request.goals());
            if (goal == null) {
                goal = new BrainDumpAiResult.Goal(GoalMatchType.TEMP, null, null, null, TEMP_KEY, "관련 목표가 없어 임시 목표로 묶었어요");
                usedTemp = true;
            }
            items.add(new BrainDumpAiResult.Item(in.clientKey(), p.title(),
                    p.dateType() == DateType.NONE ? DateType.ON : p.dateType(),
                    p.date() != null ? p.date() : request.today(),
                    p.startTime(), p.endTime(), p.minutes() != null ? p.minutes() : 30,
                    p.dateType() == DateType.DUE ? TaskPriority.B : TaskPriority.C,
                    p.kind() != null ? p.kind() : TaskKind.NORMAL, goal));
        }
        List<BrainDumpAiResult.TempGoal> temps = usedTemp
                ? List.of(new BrainDumpAiResult.TempGoal(TEMP_KEY, "새 목표", "📌"))
                : List.of();
        return new BrainDumpAiResult(items, temps, new AiCallMeta("fake", 0, 0, 0));
    }

    private BrainDumpAiResult.Goal match(String text, List<BrainDumpAiRequest.GoalContext> goals) {
        BrainDumpAiRequest.GoalContext best = null;
        Long bestSub = null;
        int bestScore = 0;
        for (BrainDumpAiRequest.GoalContext g : goals) {
            int score = overlap(text, g.name());
            Long sub = null;
            for (BrainDumpAiRequest.SubGoal s : g.subGoals()) {
                int ss = overlap(text, s.name());
                if (ss > 0 && ss + score > score) { score = Math.max(score, 0) + ss; sub = s.generalCategoryId(); }
            }
            if (score > bestScore) { bestScore = score; best = g; bestSub = sub; }
        }
        if (best == null) return null;
        return new BrainDumpAiResult.Goal(GoalMatchType.EXISTING, best.goalCategoryId(), bestSub, null, null,
                "'" + best.name() + "' 목표와 관련 있어요");
    }

    private int overlap(String text, String name) {
        if (name == null) return 0;
        int score = 0;
        for (String w : name.split("[\\s·,]+")) {
            String word = w.replaceAll("[^가-힣A-Za-z0-9]", "");
            if (word.length() >= 2 && text.contains(word)) score += word.length();
        }
        return score;
    }
}
