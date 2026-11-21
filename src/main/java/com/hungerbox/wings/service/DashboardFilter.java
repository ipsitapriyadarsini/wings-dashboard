package com.hungerbox.wings.service;

import java.time.LocalDate;
import java.util.List;

/**
 * Global dashboard filters. Bound from query params, e.g.
 * {@code ?range=LAST_7_DAYS&cityIds=1,2&companyIds=3&cafeIds=4&vendorIds=5}.
 * <ul>
 *   <li>{@code range/from/to}: date filter for non-score sections (default LAST_7_DAYS).</li>
 *   <li>{@code scoreRange/scoreFrom/scoreTo}: date filter for Cafe Score, scorecards (default LAST_WEEK).
 *       Only week/month presets are allowed; CUSTOM snaps to whole weeks.</li>
 * </ul>
 */
public record DashboardFilter(RangePreset range, LocalDate from, LocalDate to,
                              RangePreset scoreRange, LocalDate scoreFrom, LocalDate scoreTo,
                              List<Long> cityIds, List<Long> companyIds, List<Long> cafeIds, List<Long> vendorIds) {
    public DashboardFilter {
        cityIds = cityIds == null ? List.of() : cityIds;
        companyIds = companyIds == null ? List.of() : companyIds;
        cafeIds = cafeIds == null ? List.of() : cafeIds;
        vendorIds = vendorIds == null ? List.of() : vendorIds;
    }

    public static DashboardFilter empty() {
        return new DashboardFilter(null, null, null, null, null, null, null, null, null, null);
    }
}
