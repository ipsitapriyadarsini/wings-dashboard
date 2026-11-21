package com.hungerbox.wings.service;

import com.hungerbox.wings.domain.Cafe;
import com.hungerbox.wings.repo.AppUserRepository;
import com.hungerbox.wings.repo.ChecklistDay;
import com.hungerbox.wings.repo.RosterRow;
import com.hungerbox.wings.scoring.RosterAgg;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** "Top Insights" (PRD 6.6): cafeterias needing attention, driven by configurable benchmarks. */
@Service
@Transactional(readOnly = true)
public class InsightsService {

    public record Insight(Long cafeId, String cafe, String city, String content, String gapName, String smOrAm) {}

    private enum Gap { ATTENDANCE, PUNCTUALITY, FILL, QUALITY } // fixed display order

    private final RosterMetricsService metrics;
    private final AppUserRepository users;
    private final ScoreConfigService config;
    private final DateRangeService dates;

    public InsightsService(RosterMetricsService metrics, AppUserRepository users, ScoreConfigService config, DateRangeService dates) {
        this.metrics = metrics; this.users = users; this.config = config; this.dates = dates;
    }

    public List<Insight> insights(Scope scope, DateRange r) {
        // Hidden when Today is the only selected date, or when there is nothing to show.
        if (scope.cafeIds().isEmpty() || (r.from().equals(r.to()) && r.from().equals(dates.today()))) return List.of();

        double fillMin = config.get("insight.fillRate.min"), qualMin = config.get("insight.quality.min");
        double punctMin = config.get("insight.punctuality.min");
        int perGap = (int) config.get("insight.maxCafesPerGap");

        Map<Long, RosterAgg> att = metrics.aggregate(metrics.rows(scope.cafeIds(), r.from(), r.to()), RosterRow::cafeId, null);
        List<ChecklistDay> days = metrics.checklistDays(scope.cafeIds(), r.from(), r.to(), scope.vendorIds());
        Map<Long, RosterAgg> cl = metrics.checklistByCafe(days);

        Map<Long, EnumMap<Gap, String>> gaps = new HashMap<>();
        for (Long id : scope.cafeIds()) {
            EnumMap<Gap, String> g = new EnumMap<>(Gap.class);
            RosterAgg a = att.get(id), c = cl.get(id);
            if (a != null && a.noShow > 0) g.put(Gap.ATTENDANCE, "Roster absent without approval");
            Double punct = a == null ? null : a.validPunctualityPct();
            if (punct != null && punct < punctMin) g.put(Gap.PUNCTUALITY, "Low roster punctuality: " + fmt(punct) + "%");
            Double fill = c == null ? null : c.fillRate();
            if (fill != null && fill < fillMin) g.put(Gap.FILL, "Low checklist fill rate: " + fmt(fill) + "%");
            Double q = c == null ? null : c.quality();
            if (q != null && q < qualMin) g.put(Gap.QUALITY, "Low checklist quality: " + fmt(q) + "%");
            if (!g.isEmpty()) gaps.put(id, g);
        }
        if (gaps.isEmpty()) return List.of();

        // Cafes with the most gaps first, then A-Z. Keep at most N cafes per gap type.
        List<Long> order = new ArrayList<>(gaps.keySet());
        order.sort(Comparator.<Long, Integer>comparing(id -> gaps.get(id).size()).reversed()
                .thenComparing(id -> scope.cafes().get(id).getName()));
        Map<Gap, Integer> used = new EnumMap<>(Gap.class);
        Map<Long, EnumMap<Gap, String>> kept = new LinkedHashMap<>();
        for (Long id : order) {
            for (Map.Entry<Gap, String> e : gaps.get(id).entrySet()) {
                if (used.merge(e.getKey(), 1, Integer::sum) <= perGap)
                    kept.computeIfAbsent(id, k -> new EnumMap<>(Gap.class)).put(e.getKey(), e.getValue());
            }
        }

        // SM if mapped, otherwise the AM.
        Map<Long, Set<String>> sms = new HashMap<>();
        for (Object[] row : users.cafeSms(kept.keySet())) {
            sms.computeIfAbsent((Long) row[0], k -> new TreeSet<>()).add((String) row[1]);
        }
        List<Insight> out = new ArrayList<>();
        kept.forEach((id, g) -> {
            Cafe c = scope.cafes().get(id);
            String owner = sms.containsKey(id) ? String.join(", ", sms.get(id))
                    : c.getAreaManager() == null ? null : c.getAreaManager().getName();
            g.forEach((gap, text) -> out.add(new Insight(id, c.getName(), c.getCity().getName(), text, label(gap), owner)));
        });
        return out;
    }

    private static String label(Gap g) {
        return switch (g) {
            case ATTENDANCE -> "Attendance gap";
            case PUNCTUALITY -> "Punctuality gap";
            case FILL -> "Checklist Fill gap";
            case QUALITY -> "Checklist Quality gap";
        };
    }

    private static String fmt(double v) { return String.valueOf(Math.round(v * 10) / 10.0); }
}
