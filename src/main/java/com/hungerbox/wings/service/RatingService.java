package com.hungerbox.wings.service;

import com.hungerbox.wings.repo.CafeDailyOrdersRepository;
import com.hungerbox.wings.repo.RatingRepository;
import com.hungerbox.wings.scoring.ScoreMath;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class RatingService {

    public record Summary(Double averageRating, long totalRatings) {}
    public record StarShare(int stars, long count, Double pct) {}
    public record TrendPoint(LocalDate periodStart, Double averageRating, long ratings) {}
    public record SubIssue(String subIssue, long negativeRatings, long instances) {}
    public record IssueStat(String issue, long negativeRatings, long instances, List<SubIssue> subIssues) {}
    public record AvgBar(Long id, String name, Double averageRating, long ratings) {}
    public record RatingReport(DateRange range, Double averageRating, Double medianRating, long totalRatings,
                               Double captureRatePct, List<StarShare> distribution, List<TrendPoint> trend,
                               Double negativeRatingPct, List<IssueStat> negativeIssues,
                               List<AvgBar> cafeAverages, List<AvgBar> vendorAverages) {}

    private final ScopeService scopes;
    private final DateRangeService dates;
    private final RatingRepository ratings;
    private final CafeDailyOrdersRepository orders;

    public RatingService(ScopeService scopes, DateRangeService dates, RatingRepository ratings, CafeDailyOrdersRepository orders) {
        this.scopes = scopes; this.dates = dates; this.ratings = ratings; this.orders = orders;
    }

    /** Headline numbers for the home page widget. */
    public Summary summary(List<Long> cafeIds, DateRange r) {
        long count = 0, sum = 0;
        if (!cafeIds.isEmpty()) {
            for (Object[] row : ratings.byCafe(cafeIds, r.from(), r.to())) {
                count += ((Number) row[1]).longValue();
                sum += ((Number) row[2]).longValue();
            }
        }
        return new Summary(count == 0 ? null : ScoreMath.r2((double) sum / count), count);
    }

    public RatingReport report(Long userId, DashboardFilter f) {
        Scope scope = scopes.resolve(userId, f);
        DateRange r = dates.general(f);
        List<Long> ids = scope.cafeIds();
        if (ids.isEmpty()) {
            return new RatingReport(r, null, null, 0, null, List.of(), List.of(), null, List.of(), List.of(), List.of());
        }

        long[] dist = new long[6]; // index = stars
        for (Object[] row : ratings.distribution(ids, r.from(), r.to())) {
            dist[((Number) row[0]).intValue()] = ((Number) row[1]).longValue();
        }
        long total = Arrays.stream(dist).sum(), starSum = 0, negative = dist[1] + dist[2] + dist[3];
        for (int s = 1; s <= 5; s++) starSum += s * dist[s];
        List<StarShare> distribution = new ArrayList<>();
        for (int s = 5; s >= 1; s--) distribution.add(new StarShare(s, dist[s], ScoreMath.r2(ScoreMath.pct(dist[s], total))));

        Long eligible = orders.totalEligible(ids, r.from(), r.to());
        Double capture = eligible == null ? null : ScoreMath.r2(ScoreMath.pct(total, eligible));

        return new RatingReport(r, total == 0 ? null : ScoreMath.r2((double) starSum / total), median(dist, total), total,
                capture, distribution, trend(ids, r), ScoreMath.r2(ScoreMath.pct(negative, total)), issues(ids, r),
                bars(ratings.byCafeNamed(ids, r.from(), r.to())), bars(ratings.byVendor(ids, r.from(), r.to())));
    }

    private static Double median(long[] dist, long total) {
        if (total == 0) return null;
        long lowPos = (total + 1) / 2, highPos = total / 2 + 1;
        double low = 0, high = 0;
        long cum = 0;
        for (int s = 1; s <= 5; s++) {
            long before = cum;
            cum += dist[s];
            if (low == 0 && lowPos > before && lowPos <= cum) low = s;
            if (high == 0 && highPos > before && highPos <= cum) high = s;
        }
        return (low + high) / 2.0;
    }

    /** Daily points for ranges up to 31 days, weekly (Monday-based) points beyond that. */
    private List<TrendPoint> trend(List<Long> ids, DateRange r) {
        boolean weekly = r.from().plusDays(31).isBefore(r.to());
        Map<LocalDate, long[]> buckets = new TreeMap<>();
        for (Object[] row : ratings.byDay(ids, r.from(), r.to())) {
            LocalDate d = (LocalDate) row[0];
            LocalDate key = weekly ? DateRangeService.mondayOf(d) : d;
            long[] b = buckets.computeIfAbsent(key, k -> new long[2]);
            b[0] += ((Number) row[1]).longValue();
            b[1] += ((Number) row[2]).longValue();
        }
        List<TrendPoint> out = new ArrayList<>();
        buckets.forEach((d, b) -> out.add(new TrendPoint(d, ScoreMath.r2((double) b[1] / b[0]), b[0])));
        return out;
    }

    /** Issues ranked by negative ratings (ties: alphabetical). Instances = all ratings carrying that issue. */
    private List<IssueStat> issues(List<Long> ids, DateRange r) {
        Map<String, List<SubIssue>> subs = new TreeMap<>();
        for (Object[] row : ratings.issues(ids, r.from(), r.to())) {
            long neg = ((Number) row[2]).longValue();
            if (neg == 0) continue;
            subs.computeIfAbsent((String) row[0], k -> new ArrayList<>())
                    .add(new SubIssue(row[1] == null ? "Other" : (String) row[1], neg, ((Number) row[3]).longValue()));
        }
        List<IssueStat> out = new ArrayList<>();
        subs.forEach((issue, list) -> {
            list.sort(Comparator.comparing(SubIssue::negativeRatings).reversed().thenComparing(SubIssue::subIssue));
            out.add(new IssueStat(issue, list.stream().mapToLong(SubIssue::negativeRatings).sum(),
                    list.stream().mapToLong(SubIssue::instances).sum(), list));
        });
        out.sort(Comparator.comparing(IssueStat::negativeRatings).reversed().thenComparing(IssueStat::issue));
        return out;
    }

    private List<AvgBar> bars(List<Object[]> rows) {
        List<AvgBar> out = new ArrayList<>();
        for (Object[] row : rows) {
            long n = ((Number) row[2]).longValue();
            out.add(new AvgBar((Long) row[0], (String) row[1], ScoreMath.r2(((Number) row[3]).doubleValue() / n), n));
        }
        out.sort(Comparator.comparing(AvgBar::averageRating).reversed().thenComparing(AvgBar::name));
        return out;
    }
}
