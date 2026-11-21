package com.hungerbox.wings.service;

import com.hungerbox.wings.domain.CafeWeeklyScore;
import com.hungerbox.wings.domain.SmWeeklyScore;
import com.hungerbox.wings.repo.*;
import com.hungerbox.wings.scoring.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/**
 * Computes and publishes the weekly Cafe x Week and SM x Week snapshots. Excluded cafes never contribute.
 * Re-running a week replaces its snapshot (idempotent).
 */
@Service
public class ScoreCalculationService {
    private final CafeRepository cafes;
    private final RatingRepository ratings;
    private final RosterMetricsService metrics;
    private final CafeWeeklyScoreRepository cafeWeeks;
    private final SmWeeklyScoreRepository smWeeks;
    private final ScoreConfigService config;

    public ScoreCalculationService(CafeRepository cafes, RatingRepository ratings, RosterMetricsService metrics,
                                   CafeWeeklyScoreRepository cafeWeeks, SmWeeklyScoreRepository smWeeks,
                                   ScoreConfigService config) {
        this.cafes = cafes; this.ratings = ratings; this.metrics = metrics;
        this.cafeWeeks = cafeWeeks; this.smWeeks = smWeeks; this.config = config;
    }

    @Transactional
    public void calculateWeek(LocalDate weekStart) {
        LocalDate from = weekStart, to = weekStart.plusDays(6);
        List<Long> cafeIds = cafes.scorableIds();
        List<ChecklistDay> days = metrics.checklistDays(cafeIds, from, to, List.of());
        Map<CafeDay, ChecklistDay> clIndex = metrics.index(days);

        cafeWeeks.deleteWeek(weekStart);
        smWeeks.deleteWeek(weekStart);
        if (cafeIds.isEmpty()) return;

        // ---- Cafe x Week ----
        Map<Long, long[]> rating = new HashMap<>(); // [count, starSum]
        for (Object[] r : ratings.byCafe(cafeIds, from, to)) {
            rating.put((Long) r[0], new long[]{((Number) r[1]).longValue(), ((Number) r[2]).longValue()});
        }
        Map<Long, RosterAgg> checklist = metrics.checklistByCafe(days);
        Map<String, Double> cafeWeights = config.weights("cafe");
        for (Long cafeId : cafeIds) {
            long[] rt = rating.getOrDefault(cafeId, new long[]{0, 0});
            RosterAgg cl = checklist.getOrDefault(cafeId, new RosterAgg());
            CafeAgg agg = new CafeAgg(rt[1], rt[0], cl.fillFilled, cl.fillTotal, cl.qualCompliant, cl.qualEligible);
            if (!agg.hasData()) continue; // no data this week: no row (never a zero)
            cafeWeeks.save(new CafeWeeklyScore(cafeId, weekStart, agg, Scoring.cafe(agg, cafeWeights).score()));
        }

        // ---- SM x Week (across every cafe the SM was rostered at) ----
        Map<Long, RosterAgg> bySm = metrics.aggregate(metrics.rows(cafeIds, from, to), RosterRow::smId, clIndex);
        Map<String, Double> smWeights = config.weights("sm");
        bySm.forEach((smId, agg) -> {
            ScoreResult res = Scoring.sm(agg, smWeights);
            smWeeks.save(new SmWeeklyScore(smId, weekStart, agg, res.score(), res.reason()));
        });
    }
}
