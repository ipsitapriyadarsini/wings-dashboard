package com.hungerbox.wings.scoring;

import java.util.List;

/** score == null means "No Score" (which is not zero). */
public record ScoreResult(Double score, NoScoreReason reason, List<ScoreComponent> components,
                          List<ScoreComponent> punctualityBreakdown) {
    public String noScoreMessage() { return score == null && reason != null ? reason.message() : null; }
}
