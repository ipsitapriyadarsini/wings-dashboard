package com.hungerbox.wings.scoring;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ScoringTest {
    private static final Map<String, Double> CAFE = Map.of("rating", 50.0, "fillRate", 25.0, "quality", 25.0);
    private static final Map<String, Double> SM = Map.of("attendance", 25.0, "punctuality", 25.0, "fillRate", 25.0, "quality", 25.0);

    @Test
    void cafeScoreUsesFullWeightsWhenAllDataPresent() {
        // avg rating 4.0 -> 80, fill 100, quality 60  => 40 + 25 + 15 = 80
        ScoreResult r = Scoring.cafe(new CafeAgg(40, 10, 10, 10, 6, 10), CAFE);
        assertEquals(80.0, r.score(), 0.01);
    }

    @Test
    void cafeScoreChecklistOnlyUsesFiftyFifty() {
        ScoreResult r = Scoring.cafe(new CafeAgg(0, 0, 10, 10, 5, 10), CAFE); // fill 100, quality 50
        assertEquals(75.0, r.score(), 0.01);
    }

    @Test
    void cafeScoreRatingOnlyOrNothingIsNoScoreNotZero() {
        assertNull(Scoring.cafe(new CafeAgg(40, 10, 0, 0, 0, 0), CAFE).score());
        assertNull(Scoring.cafe(CafeAgg.ZERO, CAFE).score());
    }

    @Test
    void multiCafeCombinesRawDataInsteadOfAveragingPercentages() {
        // PRD example: 100/100 and 1/2 -> 101/102 = 99.02%
        CafeAgg total = new CafeAgg(0, 0, 100, 100, 0, 0).plus(new CafeAgg(0, 0, 1, 2, 0, 0));
        assertEquals(99.02, ScoreMath.pct(total.fillFilled(), total.fillTotal()), 0.01);
    }

    @Test
    void smScoreRedistributesChecklistWeightWhenNoChecklistMapped() {
        RosterAgg a = new RosterAgg();
        a.rostered = 10; a.punched = 10;
        a.ciOnTime = 10; a.coOnTime = 10; a.ciLoc = 10; a.coLoc = 10;
        ScoreResult r = Scoring.sm(a, SM);
        assertEquals(100.0, r.score(), 0.01);
        // attendance and punctuality now carry 50 each, so each punctuality check is worth 12.5
        assertEquals(12.5, r.punctualityBreakdown().get(0).weightage(), 0.01);
    }

    @Test
    void smScorePunctualityChecksSplitTwentyFiveEquallyWhenChecklistPresent() {
        RosterAgg a = new RosterAgg();
        a.rostered = 10; a.punched = 10; a.ciOnTime = 10; a.coOnTime = 10; a.ciLoc = 10; a.coLoc = 10;
        a.fillFilled = 10; a.fillTotal = 10; a.qualCompliant = 10; a.qualEligible = 10;
        ScoreResult r = Scoring.sm(a, SM);
        assertEquals(6.25, r.punctualityBreakdown().get(0).weightage(), 0.001);
    }

    @Test
    void smNoRosterAndLeaveHaveDistinctMessages() {
        assertEquals(NoScoreReason.NO_ROSTER, Scoring.sm(new RosterAgg(), SM).reason());
        RosterAgg leave = new RosterAgg();
        leave.rostered = 5; leave.leave = 5;
        ScoreResult r = Scoring.sm(leave, SM);
        assertNull(r.score());
        assertEquals(NoScoreReason.ON_LEAVE, r.reason());
    }

    @Test
    void gradesUseAbsoluteBands() {
        assertEquals("A+", Grades.of(95.0));
        assertEquals("A", Grades.of(92.0));
        assertEquals("B", Grades.of(80.0));
        assertEquals("C", Grades.of(60.0));
        assertEquals("D", Grades.of(54.9));
        assertNull(Grades.of(null));
    }
}
