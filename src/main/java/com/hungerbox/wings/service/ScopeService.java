package com.hungerbox.wings.service;

import com.hungerbox.wings.common.ApiException;
import com.hungerbox.wings.domain.AppUser;
import com.hungerbox.wings.domain.Cafe;
import com.hungerbox.wings.domain.Role;
import com.hungerbox.wings.repo.AppUserRepository;
import com.hungerbox.wings.repo.CafeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Access rules (PRD section 4): the dashboard is for Area Managers, City Ops Heads and Ops Heads; SMs use the
 * Wings app instead. City Ops Head = all cafes in their cities, AM = cafes mapped to them.
 */
@Service
public class ScopeService {
    private final AccessGuard guard;
    private final CafeRepository cafes;
    private final AppUserRepository users;

    public ScopeService(AccessGuard guard, CafeRepository cafes, AppUserRepository users) {
        this.guard = guard; this.cafes = cafes; this.users = users;
    }

    @Transactional(readOnly = true)
    public Scope resolve(Long userId, DashboardFilter f) {
        AppUser u = guard.require(userId, Role.AREA_MANAGER, Role.CITY_OPS_HEAD, Role.OPS_HEAD);
        List<Cafe> base = baseCafes(u);
        Map<Long, Cafe> out = new LinkedHashMap<>();
        for (Cafe c : base) {
            if (!f.cityIds().isEmpty() && !f.cityIds().contains(c.getCity().getId())) continue;
            if (!f.companyIds().isEmpty() && (c.getCompany() == null || !f.companyIds().contains(c.getCompany().getId()))) continue;
            if (!f.cafeIds().isEmpty() && !f.cafeIds().contains(c.getId())) continue;
            out.put(c.getId(), c);
        }
        return new Scope(u, List.copyOf(out.keySet()), out, f.vendorIds());
    }

    /** Everything the user may see before filters; used to build filter options. */
    @Transactional(readOnly = true)
    public List<Cafe> accessibleCafes(Long userId) {
        return baseCafes(guard.require(userId, Role.AREA_MANAGER, Role.CITY_OPS_HEAD, Role.OPS_HEAD));
    }

    private List<Cafe> baseCafes(AppUser u) {
        return switch (u.getRole()) {
            case OPS_HEAD -> cafes.findAllActive();
            case CITY_OPS_HEAD -> {
                List<Long> cityIds = users.cityIds(u.getId());
                yield cityIds.isEmpty() ? List.of() : cafes.findActiveByCityIds(cityIds);
            }
            case AREA_MANAGER -> cafes.findActiveByAreaManager(u.getId());
            default -> throw ApiException.forbidden("Role " + u.getRole() + " has no dashboard access");
        };
    }
}
