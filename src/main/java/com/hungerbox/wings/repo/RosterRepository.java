package com.hungerbox.wings.repo;

import com.hungerbox.wings.domain.Roster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface RosterRepository extends JpaRepository<Roster, Long> {

    @Query("select new com.hungerbox.wings.repo.RosterRow(sm.id, sm.name, c.id, c.name, am.id, am.name, r.rosterDate, " +
            "r.status, r.punched, r.checkInOnTime, r.checkOutOnTime, r.checkInInLocation, r.checkOutInLocation) " +
            "from Roster r join r.sm sm join r.cafe c left join c.areaManager am " +
            "where c.id in :cafeIds and r.rosterDate between :fromDate and :toDate")
    List<RosterRow> rows(Collection<Long> cafeIds, LocalDate fromDate, LocalDate toDate);
}
