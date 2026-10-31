package com.naleul.naleul.domain.brainDump.client;

import java.time.LocalDate;
import java.util.List;

/**
 * AI 에 넘기는 입력.
 * goals: 연결 후보 (진행 중·시작 전 목표, 임시 목표 포함, 최대 10개). AI 는 이 id 들 중에서만 고를 수 있다.
 */
public record BrainDumpAiRequest(LocalDate today, List<Item> items, List<GoalContext> goals) {

    public record Item(String clientKey, String text) {
    }

    public record GoalContext(Long goalCategoryId, String name, String emoji, boolean temporary,
                              List<SubGoal> subGoals, List<MilestoneContext> milestones) {
    }

    public record SubGoal(Long generalCategoryId, String name) {
    }

    public record MilestoneContext(Long milestoneId, String title, LocalDate dueDate) {
    }
}
