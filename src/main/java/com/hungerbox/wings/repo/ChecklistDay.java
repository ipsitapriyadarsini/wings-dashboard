package com.hungerbox.wings.repo;

import java.time.LocalDate;

/** Question-level checklist counters for one cafe on one day. */
public record ChecklistDay(Long cafeId, LocalDate date, Long filled, Long total, Long compliant, Long eligible) {
    public long filledOrZero() { return filled == null ? 0 : filled; }
    public long totalOrZero() { return total == null ? 0 : total; }
    public long compliantOrZero() { return compliant == null ? 0 : compliant; }
    public long eligibleOrZero() { return eligible == null ? 0 : eligible; }
}
