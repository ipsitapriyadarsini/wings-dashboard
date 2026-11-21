package com.hungerbox.wings.service;

import com.hungerbox.wings.common.ApiException;
import com.hungerbox.wings.domain.Role;
import com.hungerbox.wings.domain.SmWeeklyScore;
import com.hungerbox.wings.repo.SmWeeklyScoreRepository;
import com.hungerbox.wings.scoring.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/**
 * Site Manager view in the Wings app (PRD section 8). No date filter: shows the latest published weekly score.
 * An SM never sees other SMs' scores; the leaderboard exposes grades only.
 */
@Service
@Transactional(readOnly = true)
public class SmAppService {
    private static final int TREND_WEEKS = 8;

    public record TrendPoint(LocalDate weekStart, Double score) {}
    public record ScoreCard(LocalDate weekStart, Double score, String grade, String message, List<ScoreComponent> breakdown,
                            List<ScoreComponent> punctualityBreakdown, List<TrendPoint> trend,
                            LocalDate lastUpdatedOn, LocalDate nextUpdateOn) {}
    public record SmHome(ScoreCard score, LeaderboardService.Leaderboard leaderboard) {}
    public record Points(Double earned, Double max, Double pct) {}
    public record ChecklistBreakdown(LocalDate weekStart, Points fillRate, Points quality, Double totalEarned, Double totalMax,
                                     long questionsMapped, long questionsFilled, long eligibleAnswers, long compliantAnswers,
                                     String message) {}

    private final AccessGuard guard;
    private final SmWeeklyScoreRepository weekly;
    private final ScoreConfigService config;
    private final LeaderboardService leaderboard;
    private final ScorePeriodService periods;

    public SmAppService(AccessGuard guard, SmWeeklyScoreRepository weekly, ScoreConfigService config,
                        LeaderboardService leaderboard, ScorePeriodService periods) {
        this.guard = guard; this.weekly = weekly; this.config = config; this.leaderboard = leaderboard; this.periods = periods;
    }

    public SmHome home(Long userId) {
        guard.require(userId, Role.SITE_MANAGER);
        return new SmHome(score(userId), leaderboard.current(userId));
    }

    public LeaderboardService.Leaderboard leaderboard(Long userId) {
        guard.require(userId, Role.SITE_MANAGER);
        return leaderboard.current(userId);
    }

    public ScoreCard score(Long userId) {
        guard.require(userId, Role.SITE_MANAGER);
        LocalDate latest = weekly.latestWeek();
        ScorePeriodService.PublishInfo info = periods.publishInfo();
        if (latest == null) return new ScoreCard(null, null, null, "No score available yet.", List.of(), List.of(), List.of(),
                info.lastUpdatedOn(), info.nextUpdateOn());

        Map<LocalDate, SmWeeklyScore> byWeek = history(userId, latest);
        Map<String, Double> weights = config.weights("sm");
        List<TrendPoint> trend = new ArrayList<>();
        for (int i = TREND_WEEKS - 1; i >= 0; i--) {
            LocalDate wk = latest.minusWeeks(i);
            SmWeeklyScore s = byWeek.get(wk);
            trend.add(new TrendPoint(wk, s == null ? null : Scoring.sm(s.toAgg(), weights).score()));
        }
        SmWeeklyScore cur = byWeek.get(latest);
        if (cur == null) return new ScoreCard(latest, null, null, NoScoreReason.NO_ROSTER.message(), List.of(), List.of(),
                trend, info.lastUpdatedOn(), info.nextUpdateOn());
        ScoreResult res = Scoring.sm(cur.toAgg(), weights);
        return new ScoreCard(latest, res.score(), Grades.of(res.score()), res.noScoreMessage(), res.components(),
                res.punctualityBreakdown(), trend, info.lastUpdatedOn(), info.nextUpdateOn());
    }

    /** Detailed checklist score breakdown, shown as earned vs. maximum points (e.g. 21 / 25). */
    public ChecklistBreakdown checklistBreakdown(Long userId) {
        guard.require(userId, Role.SITE_MANAGER);
        LocalDate latest = weekly.latestWeek();
        SmWeeklyScore cur = latest == null ? null : history(userId, latest).get(latest);
        if (cur == null) return new ChecklistBreakdown(latest, null, null, null, null, 0, 0, 0, 0, "No score available yet.");
        RosterAgg a = cur.toAgg();
        ScoreResult res = Scoring.sm(a, config.weights("sm"));
        ScoreComponent fill = component(res, "fillRate"), q = component(res, "quality");
        if (fill == null || fill.weightage() == null) {
            return new ChecklistBreakdown(latest, null, null, null, null, a.fillTotal, a.fillFilled, a.qualEligible,
                    a.qualCompliant, "No checklist was mapped for your rosters this week.");
        }
        double earned = fill.points() + (q == null || q.points() == null ? 0 : q.points());
        double max = fill.weightage() + (q == null || q.weightage() == null ? 0 : q.weightage());
        return new ChecklistBreakdown(latest, points(fill), points(q), ScoreMath.r2(earned), ScoreMath.r2(max),
                a.fillTotal, a.fillFilled, a.qualEligible, a.qualCompliant, null);
    }

    private Map<LocalDate, SmWeeklyScore> history(Long smId, LocalDate latest) {
        List<LocalDate> weeks = new ArrayList<>();
        for (int i = 0; i < TREND_WEEKS; i++) weeks.add(latest.minusWeeks(i));
        Map<LocalDate, SmWeeklyScore> m = new HashMap<>();
        weekly.find(List.of(smId), weeks).forEach(s -> m.put(s.getWeekStart(), s));
        return m;
    }

    private static ScoreComponent component(ScoreResult r, String key) {
        return r.components().stream().filter(c -> c.key().equals(key)).findFirst().orElse(null);
    }

    private static Points points(ScoreComponent c) {
        return c == null || c.weightage() == null ? null : new Points(c.points(), c.weightage(), c.valuePct());
    }
}
