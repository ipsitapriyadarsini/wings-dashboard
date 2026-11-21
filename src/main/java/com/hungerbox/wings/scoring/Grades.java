package com.hungerbox.wings.scoring;

/**
 * Absolute grade bands (not relative to other SMs).
 * NOTE: the PRD lists 95-100 A+, 90-<95 A, 75-<85 B, 55-<75 C, <55 D, leaving 85-<90 undefined.
 * We extend B to 75-<90 so the bands are contiguous. Confirm with product.
 */
public final class Grades {
    private Grades() {}

    public static String of(Double score) {
        if (score == null) return null;
        if (score >= 95) return "A+";
        if (score >= 90) return "A";
        if (score >= 75) return "B";
        if (score >= 55) return "C";
        return "D";
    }
}
