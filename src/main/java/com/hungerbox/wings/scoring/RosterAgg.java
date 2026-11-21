package com.hungerbox.wings.scoring;

import com.hungerbox.wings.domain.RosterStatus;
import com.hungerbox.wings.repo.ChecklistDay;
import com.hungerbox.wings.repo.RosterRow;

/**
 * Mutable accumulator of roster attendance + (cafe-level) checklist counters for any grouping (SM, cafe, AM, total).
 * <p>Definitions used (see README "Assumptions"):
 * <ul>
 *   <li>Approved-leave rosters are excluded from attendance / no-show / punctuality denominators.</li>
 *   <li>Attendance % = punched / (rostered - leave).</li>
 *   <li>Punctuality (drill-down) = valid attendances (all 4 checks true) / punched.</li>
 *   <li>Punctuality (SM score) = average of the 4 equally weighted checks over punched rosters.</li>
 * </ul>
 */
public class RosterAgg {
    public long rostered, leave, punched, noShow, valid, ciOnTime, coOnTime, ciLoc, coLoc;
    public long fillFilled, fillTotal, qualCompliant, qualEligible;

    public void add(RosterRow r) {
        rostered++;
        if (r.status() == RosterStatus.APPROVED_LEAVE) { leave++; return; }
        if (Boolean.TRUE.equals(r.punched())) {
            punched++;
            boolean a = Boolean.TRUE.equals(r.ciOnTime()), b = Boolean.TRUE.equals(r.coOnTime());
            boolean c = Boolean.TRUE.equals(r.ciLoc()), d = Boolean.TRUE.equals(r.coLoc());
            if (a) ciOnTime++;
            if (b) coOnTime++;
            if (c) ciLoc++;
            if (d) coLoc++;
            if (a && b && c && d) valid++;
        } else {
            noShow++;
        }
    }

    public void addChecklist(ChecklistDay d) {
        fillFilled += d.filledOrZero();
        fillTotal += d.totalOrZero();
        qualCompliant += d.compliantOrZero();
        qualEligible += d.eligibleOrZero();
    }

    public RosterAgg merge(RosterAgg o) {
        RosterAgg m = new RosterAgg();
        m.rostered = rostered + o.rostered; m.leave = leave + o.leave; m.punched = punched + o.punched;
        m.noShow = noShow + o.noShow; m.valid = valid + o.valid;
        m.ciOnTime = ciOnTime + o.ciOnTime; m.coOnTime = coOnTime + o.coOnTime;
        m.ciLoc = ciLoc + o.ciLoc; m.coLoc = coLoc + o.coLoc;
        m.fillFilled = fillFilled + o.fillFilled; m.fillTotal = fillTotal + o.fillTotal;
        m.qualCompliant = qualCompliant + o.qualCompliant; m.qualEligible = qualEligible + o.qualEligible;
        return m;
    }

    public long expected() { return rostered - leave; }
    public Double attendancePct() { return ScoreMath.pct(punched, expected()); }
    public Double validPunctualityPct() { return ScoreMath.pct(valid, punched); }
    public Double componentPunctualityPct() { return ScoreMath.pct(ciOnTime + coOnTime + ciLoc + coLoc, 4 * punched); }
    public Double noShowPct() { return ScoreMath.pct(noShow, expected()); }
    public Double fillRate() { return ScoreMath.pct(fillFilled, fillTotal); }
    public Double quality() { return ScoreMath.pct(qualCompliant, qualEligible); }
}
