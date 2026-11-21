package com.hungerbox.wings.service;

import com.hungerbox.wings.repo.ChecklistDay;
import com.hungerbox.wings.repo.RosterRow;
import com.hungerbox.wings.scoring.RosterAgg;
import com.hungerbox.wings.scoring.ScoreMath;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class HomeService {

    public record AttendanceChecklistSummary(Double smAttendancePct, Double punctualityPct, Double fillRatePct, Double qualityPct) {}
    public record HomeResponse(DateRange range, CafeScoreService.HomeCafeScore cafeScore,
                               AttendanceChecklistSummary attendanceAndChecklist, RatingService.Summary rating,
                               List<InsightsService.Insight> insights) {}

    private final ScopeService scopes;
    private final DateRangeService dates;
    private final ScorePeriodService periods;
    private final CafeScoreService cafeScores;
    private final RosterMetricsService metrics;
    private final RatingService ratings;
    private final InsightsService insights;

    public HomeService(ScopeService scopes, DateRangeService dates, ScorePeriodService periods, CafeScoreService cafeScores,
                       RosterMetricsService metrics, RatingService ratings, InsightsService insights) {
        this.scopes = scopes; this.dates = dates; this.periods = periods; this.cafeScores = cafeScores;
        this.metrics = metrics; this.ratings = ratings; this.insights = insights;
    }

    public HomeResponse home(Long userId, DashboardFilter f) {
        Scope scope = scopes.resolve(userId, f);
        DateRange r = dates.general(f);

        RosterAgg att = new RosterAgg();
        for (RosterRow row : metrics.rows(scope.cafeIds(), r.from(), r.to())) att.add(row);
        RosterAgg cl = new RosterAgg();
        for (ChecklistDay d : metrics.checklistDays(scope.cafeIds(), r.from(), r.to(), scope.vendorIds())) cl.addChecklist(d);

        return new HomeResponse(r, cafeScores.home(scope, periods.window(f)),
                new AttendanceChecklistSummary(ScoreMath.r2(att.attendancePct()), ScoreMath.r2(att.validPunctualityPct()),
                        ScoreMath.r2(cl.fillRate()), ScoreMath.r2(cl.quality())),
                ratings.summary(scope.cafeIds(), r), insights.insights(scope, r));
    }
}
