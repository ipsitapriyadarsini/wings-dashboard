package com.hungerbox.wings.repo;

import com.hungerbox.wings.domain.Cafe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface CafeRepository extends JpaRepository<Cafe, Long> {

    String BASE = "select c from Cafe c join fetch c.city left join fetch c.company left join fetch c.areaManager where c.active = true";

    @Query(BASE)
    List<Cafe> findAllActive();

    @Query(BASE + " and c.city.id in :cityIds")
    List<Cafe> findActiveByCityIds(Collection<Long> cityIds);

    @Query(BASE + " and c.areaManager.id = :amId")
    List<Cafe> findActiveByAreaManager(Long amId);

    @Query(BASE + " and c.id in (select uc.id from AppUser u join u.cafes uc where u.id = :userId)")
    List<Cafe> findActiveByUser(Long userId);

    @Query("select c.id from Cafe c where c.active = true and c.scoreExcluded = false")
    List<Long> scorableIds();
}
