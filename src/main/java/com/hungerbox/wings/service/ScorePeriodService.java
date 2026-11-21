package com.hungerbox.wings.service;

import com.hungerbox.wings.repo.CafeWeeklyScoreRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves a score date filter into published weeks. Scores are calculated once per completed Mon-Sun week, so the
 * in-progress week is never scored: "This Week" shows the latest published (previous completed) week.
 */
@Service
@Transactional(readOnly = true)
public class ScorePeriodService {

    /**
     * @param weeks      published weeks whose scores are combined for the headline score
     * @param trendWeeks weeks plotted on the trendline (monthly buckets when {@code monthlyTrend})
     */
    public record ScoreWindow(DateRange range, List<LocalDate> weeks, List<LocalDate> trendWeeks,
                              boolean monthlyTrend, LocalDate latestWeek) {}

    public record PublishInfo(LocalDate lastUpdatedOn, LocalDate nextUpdateOn) {}

    private final DateRangeService dates;
    private final CafeWeeklyScoreRepository cafeWeeks;
    private final ScoreConfigService config;
    private final ZoneId zone;

    public ScorePeriodService(DateRangeService dates, CafeWeeklyScoreRepository cafeWeeks, ScoreConfigService config,
                              @Value("${wings.timezone}") String zone) {
        this.dates = dates; this.cafeWeeks = cafeWeeks; this.config = config; this.zone = ZoneId.of(zone);
    }

    public ScoreWindow window(DashboardFilter f) {
        DateRange r = dates.score(f);
        LocalDate latest = cafeWeeks.latestWeek();
        List<LocalDate> weeks = new ArrayList<>();
        if (latest != null) {
            if (r.preset() == RangePreset.THIS_WEEK) {
                weeks.add(latest);
            } else {
                for (LocalDate m = r.from().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
                     !m.isAfter(r.to()) && !m.isAfter(latest); m = m.plusDays(7)) {
                    weeks.add(m);
                }
            }
        }

        List<LocalDate> trend = new ArrayList<>();
        boolean monthly = false;
        if (latest != null) {
            switch (r.preset()) {
                case THIS_WEEK, LAST_WEEK -> {
                    LocalDate anchor = r.preset() == RangePreset.THIS_WEEK || r.from().isAfter(latest) ? latest : r.from();
                    for (int i = 3; i >= 0; i--) trend.add(anchor.minusWeeks(i)); // last 4 weeks
                }
                case THIS_MONTH, LAST_MONTH -> trend.addAll(weeks);
                case LAST_3_MONTHS -> { trend.addAll(weeks); monthly = true; }
                default -> { // CUSTOM: weekly for short ranges, monthly for long ones
                    trend.addAll(weeks);
                    monthly = weeks.size() > config.get("trend.monthlyAfterWeeks");
                }
            }
        }
        return new ScoreWindow(r, weeks, trend, monthly, latest);
    }

    public PublishInfo publishInfo() {
        Instant last = cafeWeeks.lastComputedAt();
        LocalDate lastOn = last == null ? null : last.atZone(zone).toLocalDate();
        return new PublishInfo(lastOn, dates.today().with(TemporalAdjusters.next(DayOfWeek.MONDAY)));
    }
}
