package com.hungerbox.wings.common;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TopBottomTest {
    record Row(String name, Double primary, Double secondary) {}

    private static TopBottom.View<Row> view(List<Row> rows, boolean bySecond, boolean asc) {
        return TopBottom.of(rows, Row::primary, Row::secondary, Row::name, bySecond, asc, 10);
    }

    private static List<Row> rows(int n) {
        List<Row> l = new ArrayList<>();
        for (int i = 1; i <= n; i++) l.add(new Row("R" + i, (double) i, 50.0));
        return l;
    }

    @Test
    void under_or_equal_to_ten_hides_toggle_and_returns_everything() {
        var v = view(rows(10), false, false);
        assertFalse(v.toggleVisible());
        assertEquals(10, v.top().size());
        assertTrue(v.bottom().isEmpty());
    }

    @Test
    void bottom_never_overlaps_top_and_holds_the_lowest() {
        var v = view(rows(25), false, false);
        assertTrue(v.toggleVisible());
        assertEquals(10, v.top().size());
        assertEquals(10, v.bottom().size());
        assertEquals("R25", v.top().get(0).name());
        assertEquals("R1", v.bottom().get(v.bottom().size() - 1).name());
    }

    @Test
    void fewer_than_twenty_puts_only_the_remainder_in_bottom() {
        var v = view(rows(14), false, false);
        assertEquals(10, v.top().size());
        assertEquals(4, v.bottom().size());
    }

    @Test
    void sorting_by_secondary_metric_does_not_change_the_selected_set() {
        List<Row> l = rows(25);
        var byPrimary = view(l, false, false);
        var bySecondary = view(l, true, true);
        assertEquals(byPrimary.top().stream().map(Row::name).sorted().toList(),
                bySecondary.top().stream().map(Row::name).sorted().toList());
    }

    @Test
    void ties_at_the_boundary_use_secondary_then_name() {
        List<Row> l = new ArrayList<>(rows(9));
        l.add(new Row("Z", 5.0, 10.0));   // ties with R5 on primary, loses on secondary
        l.add(new Row("Y", 5.0, 90.0));   // ties, wins on secondary
        var v = view(l, false, false);
        assertTrue(v.top().stream().anyMatch(r -> r.name().equals("Y")));
    }
}
