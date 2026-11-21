package com.hungerbox.wings.common;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * Top-N / Bottom-N selection used by the attendance and checklist breakdowns.
 * <ul>
 *   <li>Selection is always fixed on the primary metric (tie-break: secondary metric, then name A-Z).</li>
 *   <li>Top N = highest N. Bottom N = lowest N of the remainder (never overlaps Top N).</li>
 *   <li>If nothing remains for Bottom N, the toggle is hidden and the full list is returned in {@code top}.</li>
 *   <li>The user's sort only changes display order inside each list, never the selection.</li>
 * </ul>
 */
public final class TopBottom {
    private TopBottom() {}

    public record View<T>(List<T> top, List<T> bottom, boolean toggleVisible) {}

    public static <T> View<T> of(List<T> items, Function<T, Double> primary, Function<T, Double> secondary,
                                 Function<T, String> name, boolean sortBySecondary, boolean ascending, int n) {
        Comparator<Double> desc = Comparator.nullsLast(Comparator.<Double>reverseOrder());
        Comparator<T> rank = Comparator.<T, Double>comparing(primary, desc)
                .thenComparing(secondary, desc)
                .thenComparing(name);

        Comparator<Double> dir = ascending
                ? Comparator.nullsLast(Comparator.<Double>naturalOrder())
                : Comparator.nullsLast(Comparator.<Double>reverseOrder());
        Comparator<T> display = Comparator.<T, Double>comparing(sortBySecondary ? secondary : primary, dir)
                .thenComparing(name);

        List<T> ranked = new ArrayList<>(items);
        ranked.sort(rank);

        if (ranked.size() <= n) {
            ranked.sort(display);
            return new View<>(ranked, List.of(), false);
        }
        List<T> top = new ArrayList<>(ranked.subList(0, n));
        List<T> rest = ranked.subList(n, ranked.size());
        List<T> bottom = new ArrayList<>(rest.subList(Math.max(0, rest.size() - n), rest.size()));
        top.sort(display);
        bottom.sort(display);
        return new View<>(top, bottom, true);
    }
}
