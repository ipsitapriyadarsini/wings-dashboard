package com.hungerbox.wings.scoring;

/**
 * Underlying numerators/denominators for one or more Cafe x Week cells. Multi-cafe / multi-week scores are always
 * computed from summed raw data, never by averaging per-cafe percentages.
 */
public record CafeAgg(long ratingSum, long ratingCount, long fillFilled, long fillTotal,
                      long qualCompliant, long qualEligible) {
    public static final CafeAgg ZERO = new CafeAgg(0, 0, 0, 0, 0, 0);

    public CafeAgg plus(CafeAgg o) {
        return new CafeAgg(ratingSum + o.ratingSum, ratingCount + o.ratingCount, fillFilled + o.fillFilled,
                fillTotal + o.fillTotal, qualCompliant + o.qualCompliant, qualEligible + o.qualEligible);
    }

    public boolean hasData() { return ratingCount > 0 || fillTotal > 0; }
}
