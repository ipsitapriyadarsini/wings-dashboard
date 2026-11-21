package com.hungerbox.wings.repo;

import com.hungerbox.wings.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    @Query("select ci.id from AppUser u join u.cities ci where u.id = :userId")
    List<Long> cityIds(Long userId);

    /** [smId, smName] of SMs mapped to any of the cafes. */
    @Query("select distinct u.id, u.name from AppUser u join u.cafes c " +
            "where u.role = com.hungerbox.wings.domain.Role.SITE_MANAGER and c.id in :cafeIds")
    List<Object[]> smsInCafes(Collection<Long> cafeIds);

    /** [cafeId, smName] */
    @Query("select c.id, u.name from AppUser u join u.cafes c " +
            "where u.role = com.hungerbox.wings.domain.Role.SITE_MANAGER and c.id in :cafeIds")
    List<Object[]> cafeSms(Collection<Long> cafeIds);

    /** [smId, areaManagerName] across the SM's in-scope cafes. */
    @Query("select distinct u.id, am.name from AppUser u join u.cafes c join c.areaManager am " +
            "where u.role = com.hungerbox.wings.domain.Role.SITE_MANAGER and c.id in :cafeIds")
    List<Object[]> smAreaManagers(Collection<Long> cafeIds);
}
