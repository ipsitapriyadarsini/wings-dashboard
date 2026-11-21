package com.hungerbox.wings.service;

import com.hungerbox.wings.domain.AppUser;
import com.hungerbox.wings.domain.Cafe;
import com.hungerbox.wings.domain.Role;

import java.util.List;
import java.util.Map;

/** The cafes a user may see after applying access rules and the city/company/cafe filters. */
public record Scope(AppUser user, List<Long> cafeIds, Map<Long, Cafe> cafes, List<Long> vendorIds) {
    public Role role() { return user.getRole(); }
    public boolean allVendors() { return vendorIds.isEmpty(); }
    public List<Long> vendorIdsOrSentinel() { return vendorIds.isEmpty() ? List.of(-1L) : vendorIds; }
}
