package com.hungerbox.wings.service;

import com.hungerbox.wings.common.TopBottom;
import com.hungerbox.wings.repo.ChecklistDay;
import com.hungerbox.wings.repo.ChecklistResponseRepository;
import com.hungerbox.wings.repo.RosterRow;
import com.hungerbox.wings.scoring.RosterAgg;
import com.hungerbox.wings.scoring.ScoreMath;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional(readOnly = true)
public class ChecklistService {

    public record Completion(long total, long fullyFilled, long partiallyFilled, long notFilled, Double completionPct) {}
    public record DistributionBand(String band, long count, Double pctOfPartial) {}
    public record UnfilledQuestion(String question, long unfilledCount) {}
    public record Partial(long count, long nearComplete, List<DistributionBand> distribution,
                          List<UnfilledQuestion> mostUnfilledQuestions) {}
    public record BucketStat(String bucket, Double fillRatePct, Double qualityPct) {}
    public record FailingQuestion(String question, String bucket, long nonCompliantCount, Double qualityPct) {}
    public record ChecklistRow(Long id, String name, Double fillRatePct, Double qualityPct) {}
    public record ChecklistReport(DateRange range, Double fillRatePct, Double qualityPct, Completion completion,
                                  Partial partial, List<BucketStat> buckets, List<FailingQuestion> worstFailingQuestions,
                                  TopBottom.View<ChecklistRow> cafes, TopBottom.View<ChecklistRow> siteManagers) {}

    private final ScopeService scopes;
    private final DateRangeService dates;
    private final RosterMetricsService metrics;
    private final ChecklistResponseRepository repo;

    public ChecklistService(ScopeService scopes, DateRangeService dates, RosterMetricsService metrics,
                            ChecklistResponseRepository repo) {
        this.scopes = scopes; this.dates = dates; this.metrics = metrics; this.repo = repo;
    }

    /** @param sortBy "fillRate" (default) or "quality". */
    public ChecklistReport report(Long userId, DashboardFilter f, String sortBy, boolean ascending) {
        Scope scope = scopes.resolve(userId, f);
        DateRange r = dates.general(f);
        List<Long> cafeIds = scope.cafeIds();
        List<ChecklistDay> days = metrics.checklistDays(cafeIds, r.from(), r.to(), scope.vendorIds());
        boolean bySecond = "quality".equalsIgnoreCase(sortBy);

        RosterAgg all = new RosterAgg();
        days.forEach(all::addChecklist);

        // cafe + SM breakdown
        Map<Long, RosterAgg> byCafe = metrics.checklistByCafe(days);
        Map<Long, String> cafeNames = new HashMap<>();
        scope.cafes().forEach((id, c) -> cafeNames.put(id, c.getName()));
        List<RosterRow> rows = metrics.rows(cafeIds, r.from(), r.to());
        Map<Long, String> smNames = new HashMap<>();
        rows.forEach(x -> smNames.put(x.smId(), x.smName()));
        Map<Long, RosterAgg> bySm = metrics.aggregate(rows, RosterRow::smId, metrics.index(days));

        if (cafeIds.isEmpty()) {
            return new ChecklistReport(r, null, null, new Completion(0, 0, 0, 0, null),
                    new Partial(0, 0, List.of(), List.of()), List.of(), List.of(),
                    new TopBottom.View<>(List.of(), List.of(), false), new TopBottom.View<>(List.of(), List.of(), false));
        }
        return new ChecklistReport(r, ScoreMath.r2(all.fillRate()), ScoreMath.r2(all.quality()),
                completion(scope, r), partial(scope, r), buckets(scope, r), worstQuestions(scope, r),
                view(byCafe, cafeNames, bySecond, ascending), view(bySm, smNames, bySecond, ascending));
    }

    private Completion completion(Scope s, DateRange r) {
        long full = 0, partial = 0, none = 0;
        for (Object[] row : perChecklist(s, r)) {
            long total = ((Number) row[1]).longValue(), filled = ((Number) row[2]).longValue();
            if (filled == 0) none++; else if (filled >= total) full++; else partial++;
        }
        long total = full + partial + none;
        return new Completion(total, full, partial, none, ScoreMath.r2(ScoreMath.pct(full, total)));
    }

    private Partial partial(Scope s, DateRange r) {
        long[] band = new long[4]; // <50, 50-74, 75-94, 95-99
        long partial = 0;
        for (Object[] row : perChecklist(s, r)) {
            long total = ((Number) row[1]).longValue(), filled = ((Number) row[2]).longValue();
            if (filled == 0 || filled >= total) continue;
            partial++;
            double pct = filled * 100.0 / total;
            band[pct < 50 ? 0 : pct < 75 ? 1 : pct < 95 ? 2 : 3]++;
        }
        String[] labels = {"<50%", "50-74%", "75-94%", "95-99%"};
        List<DistributionBand> dist = new ArrayList<>();
        for (int i = 0; i < 4; i++) dist.add(new DistributionBand(labels[i], band[i], ScoreMath.r2(ScoreMath.pct(band[i], partial))));

        List<UnfilledQuestion> q = new ArrayList<>();
        for (Object[] row : repo.unfilledInPartial(s.cafeIds(), r.from(), r.to(), s.allVendors(), s.vendorIdsOrSentinel(),
                PageRequest.of(0, 10))) {
            q.add(new UnfilledQuestion((String) row[0], ((Number) row[1]).longValue()));
        }
        return new Partial(partial, band[3], dist, q);
    }

    private List<BucketStat> buckets(Scope s, DateRange r) {
        List<BucketStat> out = new ArrayList<>();
        for (Object[] row : repo.byBucket(s.cafeIds(), r.from(), r.to(), s.allVendors(), s.vendorIdsOrSentinel())) {
            long total = ((Number) row[1]).longValue(), filled = ((Number) row[2]).longValue();
            long compliant = ((Number) row[3]).longValue(), eligible = ((Number) row[4]).longValue();
            out.add(new BucketStat(row[0] == null ? "Uncategorised" : (String) row[0],
                    ScoreMath.r2(ScoreMath.pct(filled, total)), ScoreMath.r2(ScoreMath.pct(compliant, eligible))));
        }
        out.sort(Comparator.comparing(BucketStat::bucket));
        return out;
    }

    /** Bottom 6 questions across all buckets by lowest Quality Score %. */
    private List<FailingQuestion> worstQuestions(Scope s, DateRange r) {
        List<FailingQuestion> out = new ArrayList<>();
        for (Object[] row : repo.byQuestion(s.cafeIds(), r.from(), r.to(), s.allVendors(), s.vendorIdsOrSentinel())) {
            long compliant = ((Number) row[2]).longValue(), eligible = ((Number) row[3]).longValue();
            if (eligible == 0) continue;
            out.add(new FailingQuestion((String) row[0], row[1] == null ? "Uncategorised" : (String) row[1],
                    eligible - compliant, ScoreMath.r2(ScoreMath.pct(compliant, eligible))));
        }
        out.sort(Comparator.comparing(FailingQuestion::qualityPct).thenComparing(FailingQuestion::nonCompliantCount, Comparator.reverseOrder())
                .thenComparing(FailingQuestion::question));
        return out.size() > 6 ? new ArrayList<>(out.subList(0, 6)) : out;
    }

    private List<Object[]> perChecklist(Scope s, DateRange r) {
        return repo.perChecklist(s.cafeIds(), r.from(), r.to(), s.allVendors(), s.vendorIdsOrSentinel());
    }

    private TopBottom.View<ChecklistRow> view(Map<Long, RosterAgg> agg, Map<Long, String> names, boolean bySecond, boolean asc) {
        List<ChecklistRow> list = new ArrayList<>();
        agg.forEach((id, a) -> {
            if (a.fillTotal > 0)
                list.add(new ChecklistRow(id, names.get(id), ScoreMath.r2(a.fillRate()), ScoreMath.r2(a.quality())));
        });
        return TopBottom.of(list, ChecklistRow::fillRatePct, ChecklistRow::qualityPct, ChecklistRow::name, bySecond, asc, 10);
    }
}
