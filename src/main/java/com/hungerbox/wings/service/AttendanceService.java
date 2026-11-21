package com.hungerbox.wings.service;

import com.hungerbox.wings.common.TopBottom;
import com.hungerbox.wings.domain.Role;
import com.hungerbox.wings.repo.RosterRow;
import com.hungerbox.wings.scoring.RosterAgg;
import com.hungerbox.wings.scoring.ScoreMath;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;

@Service
@Transactional(readOnly = true)
public class AttendanceService {
    public static final String AM_NOTE = "Area Manager metrics are derived from the aggregated attendance data of the " +
            "Site Managers mapped to the respective Area Manager. This view is provided for ease of access and does " +
            "not represent individual Area Manager performance.";

    public record AttendanceRow(Long id, String name, Double attendancePct, Double punctualityPct) {}
    public record AttendanceReport(DateRange range, long totalRosters, Double smAttendancePct, Double punctualityPct,
                                   Double noShowPct, TopBottom.View<AttendanceRow> cafes,
                                   TopBottom.View<AttendanceRow> siteManagers, TopBottom.View<AttendanceRow> areaManagers,
                                   String areaManagerNote) {}

    private final ScopeService scopes;
    private final DateRangeService dates;
    private final RosterMetricsService metrics;

    public AttendanceService(ScopeService scopes, DateRangeService dates, RosterMetricsService metrics) {
        this.scopes = scopes; this.dates = dates; this.metrics = metrics;
    }

    /** @param sortBy "attendance" (default) or "punctuality"; only changes display order, not the Top/Bottom 10 set. */
    public AttendanceReport report(Long userId, DashboardFilter f, String sortBy, boolean ascending) {
        Scope scope = scopes.resolve(userId, f);
        DateRange r = dates.general(f);
        List<RosterRow> rows = metrics.rows(scope.cafeIds(), r.from(), r.to());

        RosterAgg all = new RosterAgg();
        rows.forEach(all::add);
        boolean bySecond = "punctuality".equalsIgnoreCase(sortBy);

        Map<Long, String> cafeNames = new HashMap<>(), smNames = new HashMap<>(), amNames = new HashMap<>();
        for (RosterRow x : rows) {
            cafeNames.put(x.cafeId(), x.cafeName());
            smNames.put(x.smId(), x.smName());
            if (x.amId() != null) amNames.put(x.amId(), x.amName());
        }
        boolean hideAm = scope.role() == Role.AREA_MANAGER; // AM view has no AM breakdown table
        return new AttendanceReport(r, all.rostered, ScoreMath.r2(all.attendancePct()), ScoreMath.r2(all.validPunctualityPct()),
                ScoreMath.r2(all.noShowPct()),
                view(metrics.aggregate(rows, RosterRow::cafeId, null), cafeNames, bySecond, ascending),
                view(metrics.aggregate(rows, RosterRow::smId, null), smNames, bySecond, ascending),
                hideAm ? null : view(metrics.aggregate(rows, RosterRow::amId, null), amNames, bySecond, ascending),
                hideAm ? null : AM_NOTE);
    }

    private TopBottom.View<AttendanceRow> view(Map<Long, RosterAgg> agg, Map<Long, String> names, boolean bySecond, boolean asc) {
        List<AttendanceRow> list = new ArrayList<>();
        agg.forEach((id, a) -> {
            if (a.expected() > 0) // nothing to rank if every roster was approved leave
                list.add(new AttendanceRow(id, names.get(id), ScoreMath.r2(a.attendancePct()), ScoreMath.r2(a.validPunctualityPct())));
        });
        Function<AttendanceRow, Double> p = AttendanceRow::attendancePct, s = AttendanceRow::punctualityPct;
        return TopBottom.of(list, p, s, AttendanceRow::name, bySecond, asc, 10);
    }
}
