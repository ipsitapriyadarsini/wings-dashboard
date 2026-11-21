package com.hungerbox.wings.scoring;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pure score maths. Weights are passed in (they are configurable at runtime), and are always redistributed
 * proportionally across the parameters that actually have data.
 */
public final class Scoring {
    private Scoring() {}

    record Part(String key, Double value, double weight) {}

    /** Cafe Score: Rating 50 / Fill 25 / Quality 25. Needs checklist data; "rating only" and "neither" = No Score. */
    public static ScoreResult cafe(CafeAgg a, Map<String, Double> w) {
        Double rating = a.ratingCount() > 0 ? a.ratingSum() * 100.0 / (5.0 * a.ratingCount()) : null;
        Double fill = ScoreMath.pct(a.fillFilled(), a.fillTotal());
        Double quality = ScoreMath.pct(a.qualCompliant(), a.qualEligible());
        List<Part> parts = List.of(
                new Part("rating", rating, w.getOrDefault("rating", 50.0)),
                new Part("fillRate", fill, w.getOrDefault("fillRate", 25.0)),
                new Part("quality", quality, w.getOrDefault("quality", 25.0)));
        if (fill == null) return noScore(parts, NoScoreReason.NO_DATA);
        return weigh(parts);
    }

    /** SM Score: Attendance / Punctuality / Fill / Quality (25 each by default). */
    public static ScoreResult sm(RosterAgg a, Map<String, Double> w) {
        List<Part> parts = List.of(
                new Part("attendance", a.attendancePct(), w.getOrDefault("attendance", 25.0)),
                new Part("punctuality", a.componentPunctualityPct(), w.getOrDefault("punctuality", 25.0)),
                new Part("fillRate", a.fillRate(), w.getOrDefault("fillRate", 25.0)),
                new Part("quality", a.quality(), w.getOrDefault("quality", 25.0)));
        if (a.rostered == 0) return noScore(parts, NoScoreReason.NO_ROSTER);
        if (a.expected() == 0) return noScore(parts, NoScoreReason.ON_LEAVE);

        ScoreResult base = weigh(parts);
        List<ScoreComponent> sub = new ArrayList<>();
        ScoreComponent pc = base.components().stream().filter(c -> c.key().equals("punctuality")).findFirst().orElse(null);
        if (pc != null && pc.weightage() != null) {
            double each = pc.weightage() / 4.0; // four equally weighted checks share the punctuality weight
            long[] counts = {a.ciOnTime, a.coOnTime, a.ciLoc, a.coLoc};
            String[] keys = {"checkInOnTime", "checkOutOnTime", "checkInFromLocation", "checkOutFromLocation"};
            for (int i = 0; i < 4; i++) {
                Double pct = ScoreMath.pct(counts[i], a.punched);
                sub.add(new ScoreComponent(keys[i], pct, each, pct == null ? null : each * pct / 100.0));
            }
        }
        return new ScoreResult(base.score(), null, base.components(), sub);
    }

    private static ScoreResult weigh(List<Part> parts) {
        double total = parts.stream().filter(p -> p.value() != null).mapToDouble(Part::weight).sum();
        if (total <= 0) return noScore(parts, NoScoreReason.NO_DATA);
        List<ScoreComponent> comps = new ArrayList<>();
        double score = 0;
        for (Part p : parts) {
            if (p.value() == null) {
                comps.add(new ScoreComponent(p.key(), null, null, null)); // N/A
            } else {
                double eff = p.weight() / total * 100.0;
                double pts = p.value() * eff / 100.0;
                score += pts;
                comps.add(new ScoreComponent(p.key(), ScoreMath.r2(p.value()), ScoreMath.r2(eff), ScoreMath.r2(pts)));
            }
        }
        return new ScoreResult(ScoreMath.r2(score), null, comps, List.of());
    }

    private static ScoreResult noScore(List<Part> parts, NoScoreReason reason) {
        List<ScoreComponent> comps = parts.stream()
                .map(p -> new ScoreComponent(p.key(), ScoreMath.r2(p.value()), null, null)).toList();
        return new ScoreResult(null, reason, comps, List.of());
    }
}
