package com.naleul.naleul.domain.goalCreation.client;

public record PlanningStyleSignal(PlanningStyleGuess style, String evidence) {

    public static PlanningStyleSignal unknown() {
        return new PlanningStyleSignal(PlanningStyleGuess.UNKNOWN, "");
    }
}
