package com.hungerbox.wings.service;

import com.hungerbox.wings.common.ApiException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.EnumSet;
import java.util.Set;

@Service
public class DateRangeService {
    private static final Set<RangePreset> SCORE_PRESETS = EnumSet.of(RangePreset.THIS_WEEK, RangePreset.LAST_WEEK,
            RangePreset.THIS_MONTH, RangePreset.LAST_MONTH, RangePreset.LAST_3_MONTHS, RangePreset.CUSTOM);

    private final Clock clock;

    public DateRangeService(Clock clock) { this.clock = clock; }

    public LocalDate today() { return LocalDate.now(clock); }

    public static LocalDate mondayOf(LocalDate d) { return d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); }

    /** Range for non-score sections. */
    public DateRange general(DashboardFilter f) {
        RangePreset p = f.range() == null ? RangePreset.LAST_7_DAYS : f.range();
        return resolve(p, f.from(), f.to());
    }

    /** Range for score sections; weeks/months only. Custom ranges snap outward to whole Mon-Sun weeks. */
    public DateRange score(DashboardFilter f) {
        RangePreset p = f.scoreRange() == null ? RangePreset.LAST_WEEK : f.scoreRange();
        if (!SCORE_PRESETS.contains(p)) {
            throw ApiException.badRequest("scoreRange must be one of " + SCORE_PRESETS);
        }
        DateRange r = resolve(p, f.scoreFrom(), f.scoreTo());
        if (p == RangePreset.CUSTOM) {
            return new DateRange(p, mondayOf(r.from()), r.to().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)));
        }
        return r;
    }

    private DateRange resolve(RangePreset p, LocalDate from, LocalDate to) {
        LocalDate t = today();
        LocalDate monday = mondayOf(t);
        LocalDate firstOfMonth = t.withDayOfMonth(1);
        return switch (p) {
            case TODAY -> new DateRange(p, t, t);
            case YESTERDAY -> new DateRange(p, t.minusDays(1), t.minusDays(1));
            case LAST_7_DAYS -> new DateRange(p, t.minusDays(6), t);
            case LAST_30_DAYS -> new DateRange(p, t.minusDays(29), t);
            case THIS_WEEK -> new DateRange(p, monday, t);
            case LAST_WEEK -> new DateRange(p, monday.minusDays(7), monday.minusDays(1));
            case THIS_MONTH -> new DateRange(p, firstOfMonth, t);
            case LAST_MONTH -> new DateRange(p, firstOfMonth.minusMonths(1), firstOfMonth.minusDays(1));
            case LAST_3_MONTHS -> new DateRange(p, firstOfMonth.minusMonths(3), firstOfMonth.minusDays(1));
            case CUSTOM -> {
                if (from == null || to == null || to.isBefore(from)) {
                    throw ApiException.badRequest("CUSTOM range needs from <= to");
                }
                yield new DateRange(p, from, to);
            }
        };
    }
}
