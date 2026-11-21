package com.hungerbox.wings.service;

import com.hungerbox.wings.common.ApiException;
import com.hungerbox.wings.domain.AppUser;
import com.hungerbox.wings.domain.Role;
import com.hungerbox.wings.repo.AppUserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

/**
 * Identity is currently taken from the X-User-Id header. Replace with the real SSO/JWT principal when wiring this
 * into Wings; every service only needs a user id + role from here.
 */
@Component
public class AccessGuard {
    private final AppUserRepository users;

    public AccessGuard(AppUserRepository users) { this.users = users; }

    @Transactional(readOnly = true)
    public AppUser user(Long id) {
        if (id == null) throw ApiException.unauthorized("Missing X-User-Id");
        return users.findById(id).orElseThrow(() -> ApiException.unauthorized("Unknown user " + id));
    }

    public AppUser require(Long id, Role... roles) {
        AppUser u = user(id);
        if (!Arrays.asList(roles).contains(u.getRole())) {
            throw ApiException.forbidden("Role " + u.getRole() + " cannot access this resource");
        }
        return u;
    }
}
