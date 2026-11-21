package com.hungerbox.wings.service;

import com.hungerbox.wings.domain.RosterStatus;
import com.hungerbox.wings.repo.ChecklistDay;
import com.hungerbox.wings.repo.ChecklistResponseRepository;
import com.hungerbox.wings.repo.RosterRepository;
import com.hungerbox.wings.repo.RosterRow;
import com.hungerbox.wings.scoring.RosterAgg;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;

/** Shared aggregation of rosters and cafe-level checklist counters, used by scoring, drill-downs and insights. */
@Service
@Transactional(readOnly = true)
public class RosterMetricsService {
    private final RosterRepository rosters;
    private final ChecklistResponseRepository checklists;

    public RosterMetricsService(RosterRepository rosters, ChecklistResponseRepository checklists) {
        this.rosters = rosters; this.checklists = checklists;
    }

    public List<RosterRow> rows(Collection<Long> cafeIds, LocalDate from, LocalDate to) {
        return cafeIds.isEmpty() ? List.of() : rosters.rows(cafeIds, from, to);
    }

    public List<ChecklistDay> checklistDays(Collection<Long> cafeIds, LocalDate from, LocalDate to, Collection<Long> vendorIds) {
        if (cafeIds.isEmpty()) return List.of();
        boolean all = vendorIds == null || vendorIds.isEmpty();
        return checklists.days(cafeIds, from, to, all, all ? List.of(-1L) : vendorIds);
    }

    public Map<CafeDay, ChecklistDay> index(List<ChecklistDay> days) {
        Map<CafeDay, ChecklistDay> m = new HashMap<>();
        days.forEach(d -> m.put(new CafeDay(d.cafeId(), d.date()), d));
        return m;
    }

    /** Cafe-level checklist counters only (no roster data). */
    public Map<Long, RosterAgg> checklistByCafe(List<ChecklistDay> days) {
        Map<Long, RosterAgg> m = new HashMap<>();
        days.forEach(d -> m.computeIfAbsent(d.cafeId(), k -> new RosterAgg()).addChecklist(d));
        return m;
    }

    /**
     * Groups roster rows by {@code key}. When {@code checklist} is given, each (SM, cafe, day) inherits the cafe's
     * checklist counters once: checklists are cafe-level, so every SM rostered at the same time gets the same
     * fill rate / quality score (PRD 8.2 edge case). Approved-leave rosters inherit nothing.
     */
    public <K> Map<K, RosterAgg> aggregate(List<RosterRow> rows, Function<RosterRow, K> key,
                                           Map<CafeDay, ChecklistDay> checklist) {
        Map<K, RosterAgg> out = new LinkedHashMap<>();
        Map<K, Set<String>> seen = new HashMap<>();
        for (RosterRow r : rows) {
            K k = key.apply(r);
            if (k == null) continue;
            RosterAgg a = out.computeIfAbsent(k, x -> new RosterAgg());
            a.add(r);
            if (checklist != null && r.status() != RosterStatus.APPROVED_LEAVE) {
                String instance = r.smId() + "|" + r.cafeId() + "|" + r.date();
                if (seen.computeIfAbsent(k, x -> new HashSet<>()).add(instance)) {
                    ChecklistDay d = checklist.get(new CafeDay(r.cafeId(), r.date()));
                    if (d != null) a.addChecklist(d);
                }
            }
        }
        return out;
    }
}
