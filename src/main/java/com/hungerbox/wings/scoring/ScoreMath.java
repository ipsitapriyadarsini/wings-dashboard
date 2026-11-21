package com.hungerbox.wings.scoring;

public final class ScoreMath {
    private ScoreMath() {}

    /** Percentage or null when the denominator has no data (N/A, never zero). */
    public static Double pct(long numerator, long denominator) {
        return denominator <= 0 ? null : numerator * 100.0 / denominator;
    }

    public static Double r2(Double v) {
        return v == null ? null : Math.round(v * 100.0) / 100.0;
    }
}
