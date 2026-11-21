package com.hungerbox.wings.repo;

import com.hungerbox.wings.domain.CafeDailyOrders;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;

public interface CafeDailyOrdersRepository extends JpaRepository<CafeDailyOrders, Long> {

    @Query("select coalesce(sum(o.eligibleOrders), 0) from CafeDailyOrders o " +
            "where o.cafe.id in :cafeIds and o.orderDate between :fromDate and :toDate")
    Long totalEligible(Collection<Long> cafeIds, LocalDate fromDate, LocalDate toDate);
}
