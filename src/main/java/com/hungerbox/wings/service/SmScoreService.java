package com.hungerbox.wings.service;

import com.hungerbox.wings.common.PageResult;
import com.hungerbox.wings.domain.SmWeeklyScore;
import com.hungerbox.wings.repo.AppUserRepository;
import com.hungerbox.wings.repo.SmWeeklyScoreRepository;
import com.hungerbox.wings.scoring.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class SmScoreService {

    public record SmTrendPoint(String label, LocalDate periodStart, Double score) {}
    public record SmScorecard(Long smId, String smName, long totalRosteredDays, long presentRosters,
                              String areaManager, Double score, String grade, List<ScoreComponent> breakdown,
                              List<ScoreComponent> punctualityBreakdown, List<SmTrendPoint> trend) {}
    public record SmScorecardPage(PageResult<SmScorecard> page, String message, LocalDate lastUpdatedOn,
                                  LocalDate nextUpdateOn) {}

    private final SmWeeklyScoreRepository weekly;
    private final AppUserRepository users;
    private final ScorePeriodService periods;
    private final ScoreConfigService config;

    public SmScoreService(SmWeeklyScoreRepository weekly, AppUserRepository users, ScorePeriodService periods,
                          ScoreConfigService config) {
        this.weekly = weekly; this.users = users; this.periods = periods; this.config = config;
    }

    public SmScorecardPage scorecards(Scope scope, ScorePeriodService.ScoreWindow w, int page, int size, boolean ascending) {
        Map<String, Double> weights = config.weights("sm");
        Map<Long, String> names = new HashMap<>();
        for (Object[] r : users.smsInCafes(scope.cafeIds().isEmpty() ? List.of(-1L) : scope.cafeIds())) {
            names.put((Long) r[0], (String) r[1]);
        }
        Map<Long, Set<String>> ams = new HashMap<>();
        if (!names.isEmpty()) {
            for (Object[] r : users.smAreaManagers(scope.cafeIds())) {
                ams.computeIfAbsent((Long) r[0], k -> new TreeSet<>()).add((String) r[1]);
            }
        }
        Map<Long, Map<LocalDate, RosterAgg>> data = load(names.keySet(), w);

        List<SmScorecard> cards = new ArrayList<>();
        for (Map.Entry<Long, String> e : names.entrySet()) {
            Map<LocalDate, RosterAgg> byWeek = data.get(e.getKey());
            RosterAgg agg = combine(byWeek, w.weeks());
            ScoreResult res = Scoring.sm(agg, weights);
            if (res.score() == null) continue; // no scorecard for SMs without a score
            cards.add(new SmScorecard(e.getKey(), e.getValue(), agg.rostered, agg.punched,
                    String.join(", ", ams.getOrDefault(e.getKey(), Set.of())), res.score(), Grades.of(res.score()),
                    res.components(), res.punctualityBreakdown(), trend(byWeek, w, weights)));
        }
        boolean toggle = cards.size() >= 10;
        boolean asc = toggle && ascending;
        Comparator<SmScorecard> cmp = Comparator.comparing(SmScorecard::score).thenComparing(SmScorecard::smName);
        cards.sort(asc ? cmp : cmp.reversed());

        ScorePeriodService.PublishInfo info = periods.publishInfo();
        return new SmScorecardPage(PageResult.of(cards, page, size, toggle, asc ? "asc" : "desc"),
                cards.isEmpty() ? "SM Score not available. Please update the filters and try again" : null,
                info.lastUpdatedOn(), info.nextUpdateOn());
    }

    /** Weekly (or monthly) SM trend; also used by the SM app home. */
    public List<SmTrendPoint> trend(Map<LocalDate, RosterAgg> byWeek, ScorePeriodService.ScoreWindow w, Map<String, Double> weights) {
        Map<String, List<LocalDate>> buckets = new LinkedHashMap<>();
        for (LocalDate wk : w.trendWeeks()) {
            buckets.computeIfAbsent(w.monthlyTrend() ? YearMonth.from(wk).toString() : wk.toString(), k -> new ArrayList<>()).add(wk);
        }
        List<SmTrendPoint> out = new ArrayList<>();
        buckets.forEach((label, weeks) ->
                out.add(new SmTrendPoint(label, weeks.get(0), Scoring.sm(combine(byWeek, weeks), weights).score())));
        return out;
    }

    public Map<Long, Map<LocalDate, RosterAgg>> load(Collection<Long> smIds, ScorePeriodService.ScoreWindow w) {
        Set<LocalDate> need = new HashSet<>(w.weeks());
        need.addAll(w.trendWeeks());
        Map<Long, Map<LocalDate, RosterAgg>> m = new HashMap<>();
        if (smIds.isEmpty() || need.isEmpty()) return m;
        for (SmWeeklyScore s : weekly.find(smIds, need)) {
            m.computeIfAbsent(s.getSmId(), k -> new HashMap<>()).put(s.getWeekStart(), s.toAgg());
        }
        return m;
    }

    public static RosterAgg combine(Map<LocalDate, RosterAgg> byWeek, Collection<LocalDate> weeks) {
        RosterAgg t = new RosterAgg();
        if (byWeek == null) return t;
        for (LocalDate wk : weeks) {
            RosterAgg a = byWeek.get(wk);
            if (a != null) t = t.merge(a);
        }
        return t;
    }
}
