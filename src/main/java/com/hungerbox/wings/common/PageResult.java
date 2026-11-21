package com.hungerbox.wings.common;

import java.util.List;

/** "Show 5 at a time" pagination used by scorecards. */
public record PageResult<T>(List<T> items, int page, int size, long total, boolean hasMore,
                            boolean sortToggleVisible, String sort) {

    public static <T> PageResult<T> of(List<T> all, int page, int size, boolean sortToggleVisible, String sort) {
        int from = Math.min(Math.max(page, 0) * size, all.size());
        int to = Math.min(from + size, all.size());
        return new PageResult<>(all.subList(from, to), page, size, all.size(), to < all.size(), sortToggleVisible, sort);
    }
}
