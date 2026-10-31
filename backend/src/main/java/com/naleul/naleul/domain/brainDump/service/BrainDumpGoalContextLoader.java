package com.naleul.naleul.domain.brainDump.service;

import com.naleul.naleul.domain.brainDump.client.BrainDumpAiRequest;
import com.naleul.naleul.domain.generalCategory.entity.GeneralCategory;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus;
import com.naleul.naleul.domain.goalCategory.repository.GoalCategoryRepository;
import com.naleul.naleul.domain.milestone.entity.Milestone;
import com.naleul.naleul.domain.milestone.enums.MilestoneStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * AI 에 보여줄 "연결 후보 목표" (진행 중·시작 전, 임시 목표 포함, 최근 것부터 최대 10개).
 * 세부 목표는 삭제·"기타 할 일" 버킷 제외, 마일스톤은 아직 달성 전(PENDING)만.
 * AI 호출은 트랜잭션 밖에서 하므로, 지연 로딩이 필요한 조회만 여기서 짧은 읽기 트랜잭션으로 끝낸다.
 */
@Component
@RequiredArgsConstructor
public class BrainDumpGoalContextLoader {

    static final int MAX_GOALS = 10;
    private static final Set<GoalCategoryStatus> ACTIVE = Set.of(GoalCategoryStatus.NOT_STARTED, GoalCategoryStatus.IN_PROGRESS);

    private final GoalCategoryRepository goalCategoryRepository;

    public record Context(List<BrainDumpAiRequest.GoalContext> goals, Map<Long, String> colors) {
    }

    @Transactional(readOnly = true)
    public Context load(Long userId) {
        Map<Long, GoalCategory> unique = new LinkedHashMap<>();
        for (GoalCategory g : goalCategoryRepository.findAllByUserIdWithAll(userId)) {
            if (ACTIVE.contains(g.getGoalCategoryStatus())) unique.putIfAbsent(g.getGoalCategoryId(), g);
        }
        List<GoalCategory> goals = unique.values().stream()
                .sorted(Comparator.comparing(GoalCategory::getGoalCategoryId).reversed())
                .limit(MAX_GOALS)
                .toList();

        List<BrainDumpAiRequest.GoalContext> result = new ArrayList<>();
        Map<Long, String> colors = new HashMap<>();
        for (GoalCategory g : goals) {
            List<BrainDumpAiRequest.SubGoal> subs = new ArrayList<>();
            Set<Long> seen = new HashSet<>();
            for (GeneralCategory s : g.getGeneralCategories()) {
                if (s.getGeneralCategoryStatus() == GoalCategoryStatus.DELETED || s.isDefaultBucket()) continue;
                if (seen.add(s.getGeneralCategoryId())) {
                    subs.add(new BrainDumpAiRequest.SubGoal(s.getGeneralCategoryId(), s.getGeneralCategoryName()));
                }
            }
            List<BrainDumpAiRequest.MilestoneContext> ms = new ArrayList<>();
            for (Milestone m : g.getMilestones()) {
                if (m.getStatus() == MilestoneStatus.PENDING) {
                    ms.add(new BrainDumpAiRequest.MilestoneContext(m.getMilestoneId(), m.getTitle(), m.getDueDate()));
                }
            }
            result.add(new BrainDumpAiRequest.GoalContext(g.getGoalCategoryId(), g.getGoalCategoryName(), g.getEmoji(),
                    g.isTemporary(), subs, ms));
            if (g.getUserColor() != null) colors.put(g.getGoalCategoryId(), g.getUserColor().getColorCode());
        }
        return new Context(result, colors);
    }
}
