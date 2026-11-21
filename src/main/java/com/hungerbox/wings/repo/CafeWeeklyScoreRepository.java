package com.hungerbox.wings.repo;

import com.hungerbox.wings.domain.CafeWeeklyScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface CafeWeeklyScoreRepository extends JpaRepository<CafeWeeklyScore, Long> {

    @Query("select max(s.weekStart) from CafeWeeklyScore s")
    LocalDate latestWeek();

    @Query("select max(s.computedAt) from CafeWeeklyScore s")
    Instant lastComputedAt();

    @Query("select s from CafeWeeklyScore s where s.cafeId in :cafeIds and s.weekStart in :weeks")
    List<CafeWeeklyScore> find(Collection<Long> cafeIds, Collection<LocalDate> weeks);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CafeWeeklyScore s where s.weekStart = :weekStart")
    void deleteWeek(LocalDate weekStart);
}
