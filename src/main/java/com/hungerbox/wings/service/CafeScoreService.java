package com.hungerbox.wings.service;

import com.hungerbox.wings.common.PageResult;
import com.hungerbox.wings.domain.Cafe;
import com.hungerbox.wings.domain.CafeWeeklyScore;
import com.hungerbox.wings.repo.CafeWeeklyScoreRepository;
import com.hungerbox.wings.scoring.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class CafeScoreService {

    public record TrendPoint(String label, LocalDate periodStart, Double score) {}
    public record RankedCafe(Long cafeId, String name, String city, Double score) {}
    public record HomeCafeScore(Double score, String message, DateRange period, List<LocalDate> weeksIncluded,
                                List<TrendPoint> trend, List<RankedCafe> best, List<RankedCafe> worst,
                                String rankingMode, LocalDate lastUpdatedOn, LocalDate nextUpdateOn) {}
    public record CafeScorecard(Long cafeId, String cafeName, Double score, String company, String city,
                                String areaManager, List<ScoreComponent> breakdown, List<TrendPoint> trend) {}
    public record ScorecardPage(PageResult<CafeScorecard> page, List<TrendPoint> overallTrend, String message,
                                LocalDate lastUpdatedOn, LocalDate nextUpdateOn) {}

    private static final String NO_SCORE = NoScoreReason.NO_DATA.message();

    private final CafeWeeklyScoreRepository weekly;
    private final ScorePeriodService periods;
    private final ScoreConfigService config;

    public CafeScoreService(CafeWeeklyScoreRepository weekly, ScorePeriodService periods, ScoreConfigService config) {
        this.weekly = weekly; this.periods = periods; this.config = config;
    }

    // ------------------------------------------------------------------ home

    public HomeCafeScore home(Scope scope, ScorePeriodService.ScoreWindow w) {
        Map<String, Double> weights = config.weights("cafe");
        Map<Long, Map<LocalDate, CafeAgg>> data = load(scope.cafeIds(), w);

        List<RankedCafe> valid = new ArrayList<>();
        CafeAgg total = CafeAgg.ZERO;
        for (Long id : scope.cafeIds()) {
            CafeAgg agg = combine(data.get(id), w.weeks());
            Double s = Scoring.cafe(agg, weights).score();
            if (s == null) continue; // cafes without a valid score are excluded, never treated as zero
            total = total.plus(agg);
            Cafe c = scope.cafes().get(id);
            valid.add(new RankedCafe(id, c.getName(), c.getCity().getName(), s));
        }
        Double overall = valid.isEmpty() ? null : Scoring.cafe(total, weights).score();
        Rankings rk = rank(valid);
        ScorePeriodService.PublishInfo info = periods.publishInfo();
        return new HomeCafeScore(overall, overall == null ? NO_SCORE : null, w.range(), w.weeks(),
                trend(scope.cafeIds(), data, w, weights), rk.best, rk.worst, rk.mode,
                info.lastUpdatedOn(), info.nextUpdateOn());
    }

    record Rankings(List<RankedCafe> best, List<RankedCafe> worst, String mode) {}

    /** Best/Worst rules from PRD 6.1 (ties, <6 cafes, single cafe, no-score cafes). */
    static Rankings rank(List<RankedCafe> valid) {
        int n = valid.size();
        if (n == 0) return new Rankings(List.of(), List.of(), "NONE");
        if (n == 1) return new Rankings(List.of(), List.of(), "SINGLE"); // show score + "View Individual Scorecards"
        Comparator<RankedCafe> byBest = Comparator.comparing(RankedCafe::score, Comparator.reverseOrder())
                .thenComparing(RankedCafe::name);
        Comparator<RankedCafe> byWorst = Comparator.comparing(RankedCafe::score).thenComparing(RankedCafe::name);
        List<RankedCafe> desc = valid.stream().sorted(byBest).toList();
        List<RankedCafe> asc = valid.stream().sorted(byWorst).toList();
        if (n >= 6) {
            double bestCut = desc.get(2).score();   // a tie on the 3rd place pulls in every tied cafe
            double worstCut = asc.get(2).score();
            List<RankedCafe> best = desc.stream().filter(c -> c.score() >= bestCut).toList();
            Set<Long> inBest = new HashSet<>();
            best.forEach(c -> inBest.add(c.cafeId()));
            List<RankedCafe> worst = asc.stream().filter(c -> c.score() <= worstCut && !inBest.contains(c.cafeId())).toList();
            return new Rankings(best, worst, "TOP_BOTTOM_3");
        }
        int k = (n + 1) / 2; // fewer than 6: split what we have, no cafe in both lists
        List<RankedCafe> worst = new ArrayList<>(desc.subList(k, n));
        Collections.reverse(worst);
        return new Rankings(desc.subList(0, k), worst, "SPLIT");
    }

    // ------------------------------------------------------------ scorecards

    public ScorecardPage scorecards(Scope scope, ScorePeriodService.ScoreWindow w, int page, int size, boolean ascending) {
        Map<String, Double> weights = config.weights("cafe");
        Map<Long, Map<LocalDate, CafeAgg>> data = load(scope.cafeIds(), w);

        List<CafeScorecard> cards = new ArrayList<>();
        for (Long id : scope.cafeIds()) {
            CafeAgg agg = combine(data.get(id), w.weeks());
            ScoreResult res = Scoring.cafe(agg, weights);
            if (res.score() == null) continue; // no scorecard for cafes without a score
            Cafe c = scope.cafes().get(id);
            cards.add(new CafeScorecard(id, c.getName(), res.score(),
                    c.getCompany() == null ? null : c.getCompany().getName(), c.getCity().getName(),
                    c.getAreaManager() == null ? null : c.getAreaManager().getName(), res.components(),
                    trend(List.of(id), data, w, weights)));
        }
        boolean toggle = cards.size() >= 10; // sort control only appears for 10+ cafes
        boolean asc = toggle && ascending;
        Comparator<CafeScorecard> cmp = Comparator.comparing(CafeScorecard::score).thenComparing(CafeScorecard::cafeName);
        cards.sort(asc ? cmp : cmp.reversed());

        ScorePeriodService.PublishInfo info = periods.publishInfo();
        return new ScorecardPage(PageResult.of(cards, page, size, toggle, asc ? "asc" : "desc"),
                trend(scope.cafeIds(), data, w, weights), cards.isEmpty() ? NO_SCORE : null,
                info.lastUpdatedOn(), info.nextUpdateOn());
    }

    // --------------------------------------------------------------- helpers

    private Map<Long, Map<LocalDate, CafeAgg>> load(List<Long> cafeIds, ScorePeriodService.ScoreWindow w) {
        Set<LocalDate> need = new HashSet<>(w.weeks());
        need.addAll(w.trendWeeks());
        Map<Long, Map<LocalDate, CafeAgg>> m = new HashMap<>();
        if (cafeIds.isEmpty() || need.isEmpty()) return m;
        for (CafeWeeklyScore s : weekly.find(cafeIds, need)) {
            m.computeIfAbsent(s.getCafeId(), k -> new HashMap<>()).put(s.getWeekStart(), s.toAgg());
        }
        return m;
    }

    private static CafeAgg combine(Map<LocalDate, CafeAgg> byWeek, Collection<LocalDate> weeks) {
        CafeAgg t = CafeAgg.ZERO;
        if (byWeek == null) return t;
        for (LocalDate wk : weeks) {
            CafeAgg a = byWeek.get(wk);
            if (a != null) t = t.plus(a);
        }
        return t;
    }

    /** Trend over the window's trend weeks for the given cafes, combining raw data (never averaging percentages). */
    List<TrendPoint> trend(Collection<Long> cafeIds, Map<Long, Map<LocalDate, CafeAgg>> data,
                           ScorePeriodService.ScoreWindow w, Map<String, Double> weights) {
        Map<String, List<LocalDate>> buckets = new LinkedHashMap<>();
        for (LocalDate wk : w.trendWeeks()) {
            String label = w.monthlyTrend() ? YearMonth.from(wk).toString() : wk.toString();
            buckets.computeIfAbsent(label, k -> new ArrayList<>()).add(wk);
        }
        List<TrendPoint> out = new ArrayList<>();
        buckets.forEach((label, weeks) -> {
            CafeAgg total = CafeAgg.ZERO;
            for (Long id : cafeIds) {
                CafeAgg a = combine(data.get(id), weeks);
                if (Scoring.cafe(a, weights).score() != null) total = total.plus(a);
            }
            out.add(new TrendPoint(label, weeks.get(0), Scoring.cafe(total, weights).score()));
        });
        return out;
    }
}
