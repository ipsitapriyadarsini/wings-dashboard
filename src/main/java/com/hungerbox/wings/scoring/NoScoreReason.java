package com.hungerbox.wings.scoring;

public enum NoScoreReason {
    NO_ROSTER("No score available as no rosters were assigned this week."),
    ON_LEAVE("No score available as you were on planned leave this week."),
    NO_DATA("Cafeteria Score not available. Please update the filters and try again");

    private final String message;
    NoScoreReason(String message) { this.message = message; }
    public String message() { return message; }
}
