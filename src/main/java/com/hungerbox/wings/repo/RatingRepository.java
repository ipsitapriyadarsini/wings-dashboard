package com.hungerbox.wings.repo;

import com.hungerbox.wings.domain.Rating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface RatingRepository extends JpaRepository<Rating, Long> {

    String W = " where r.cafe.id in :cafeIds and r.ratedOn between :fromDate and :toDate ";

    /** [cafeId, count, starSum] */
    @Query("select r.cafe.id, count(r), sum(r.stars) from Rating r" + W + "group by r.cafe.id")
    List<Object[]> byCafe(Collection<Long> cafeIds, LocalDate fromDate, LocalDate toDate);

    /** [cafeId, cafeName, count, starSum] */
    @Query("select c.id, c.name, count(r), sum(r.stars) from Rating r join r.cafe c" + W + "group by c.id, c.name")
    List<Object[]> byCafeNamed(Collection<Long> cafeIds, LocalDate fromDate, LocalDate toDate);

    /** [vendorId, vendorName, count, starSum] */
    @Query("select v.id, v.name, count(r), sum(r.stars) from Rating r join r.vendor v" + W + "group by v.id, v.name")
    List<Object[]> byVendor(Collection<Long> cafeIds, LocalDate fromDate, LocalDate toDate);

    /** [stars, count] */
    @Query("select r.stars, count(r) from Rating r" + W + "group by r.stars")
    List<Object[]> distribution(Collection<Long> cafeIds, LocalDate fromDate, LocalDate toDate);

    /** [date, count, starSum] */
    @Query("select r.ratedOn, count(r), sum(r.stars) from Rating r" + W + "group by r.ratedOn order by r.ratedOn")
    List<Object[]> byDay(Collection<Long> cafeIds, LocalDate fromDate, LocalDate toDate);

    /** [issue, subIssue, negativeCount (<=3 stars), instances (all ratings with the issue)] */
    @Query("select r.issue, r.subIssue, sum(case when r.stars <= 3 then 1L else 0L end), count(r) from Rating r" + W +
            "and r.issue is not null group by r.issue, r.subIssue")
    List<Object[]> issues(Collection<Long> cafeIds, LocalDate fromDate, LocalDate toDate);
}
