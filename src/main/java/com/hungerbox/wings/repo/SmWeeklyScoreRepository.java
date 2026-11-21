package com.hungerbox.wings.repo;

import com.hungerbox.wings.domain.SmWeeklyScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface SmWeeklyScoreRepository extends JpaRepository<SmWeeklyScore, Long> {

    @Query("select max(s.weekStart) from SmWeeklyScore s")
    LocalDate latestWeek();

    @Query("select s from SmWeeklyScore s where s.smId in :smIds and s.weekStart in :weeks")
    List<SmWeeklyScore> find(Collection<Long> smIds, Collection<LocalDate> weeks);

    List<SmWeeklyScore> findByWeekStartAndScoreIsNotNull(LocalDate weekStart);

    boolean existsBySmIdAndWeekStartLessThanAndScoreIsNotNull(Long smId, LocalDate weekStart);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from SmWeeklyScore s where s.weekStart = :weekStart")
    void deleteWeek(LocalDate weekStart);
}
