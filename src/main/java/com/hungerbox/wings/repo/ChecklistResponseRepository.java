package com.hungerbox.wings.repo;

import com.hungerbox.wings.domain.ChecklistResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface ChecklistResponseRepository extends JpaRepository<ChecklistResponse, Long> {

    String RS = "com.hungerbox.wings.domain.ResponseStatus.";
    String AT = "com.hungerbox.wings.domain.AnswerType.";
    String ELIGIBLE_COND = "r.status = " + RS + "ANSWERED and r.answerType in (" + AT + "BOOLEAN, " + AT + "AI_GRADED)";
    /** Fill rate numerator: answered or marked NA. */
    String FILLED = "sum(case when r.status <> " + RS + "UNFILLED then 1L else 0L end)";
    /** Quality numerator / denominator: only Boolean and AI-graded answers; NA excluded. */
    String COMPLIANT = "sum(case when " + ELIGIBLE_COND + " and r.compliant = true then 1L else 0L end)";
    String ELIGIBLE = "sum(case when " + ELIGIBLE_COND + " then 1L else 0L end)";
    String FROM = " from ChecklistResponse r join r.checklist c where c.cafe.id in :cafeIds " +
            "and c.checklistDate between :fromDate and :toDate and (:allVendors = true or c.vendor.id in :vendorIds) ";

    @Query("select new com.hungerbox.wings.repo.ChecklistDay(c.cafe.id, c.checklistDate, " + FILLED + ", count(r), "
            + COMPLIANT + ", " + ELIGIBLE + ")" + FROM + "group by c.cafe.id, c.checklistDate")
    List<ChecklistDay> days(Collection<Long> cafeIds, LocalDate fromDate, LocalDate toDate,
                            boolean allVendors, Collection<Long> vendorIds);

    /** [checklistId, totalQuestions, filledQuestions] */
    @Query("select c.id, count(r), " + FILLED + FROM + "group by c.id")
    List<Object[]> perChecklist(Collection<Long> cafeIds, LocalDate fromDate, LocalDate toDate,
                                boolean allVendors, Collection<Long> vendorIds);

    /** [bucket, total, filled, compliant, eligible] */
    @Query("select r.bucket, count(r), " + FILLED + ", " + COMPLIANT + ", " + ELIGIBLE + FROM + "group by r.bucket")
    List<Object[]> byBucket(Collection<Long> cafeIds, LocalDate fromDate, LocalDate toDate,
                            boolean allVendors, Collection<Long> vendorIds);

    /** [question, bucket, compliant, eligible] */
    @Query("select r.questionText, r.bucket, " + COMPLIANT + ", " + ELIGIBLE + FROM + "group by r.questionText, r.bucket")
    List<Object[]> byQuestion(Collection<Long> cafeIds, LocalDate fromDate, LocalDate toDate,
                              boolean allVendors, Collection<Long> vendorIds);

    /** [question, unfilledCount] restricted to partially filled checklists. */
    @Query("select r.questionText, count(r) from ChecklistResponse r where r.status = " + RS + "UNFILLED " +
            "and r.checklist.id in (select c.id from ChecklistResponse r2 join r2.checklist c " +
            "where c.cafe.id in :cafeIds and c.checklistDate between :fromDate and :toDate " +
            "and (:allVendors = true or c.vendor.id in :vendorIds) group by c.id " +
            "having sum(case when r2.status = " + RS + "UNFILLED then 1 else 0 end) > 0 " +
            "and sum(case when r2.status <> " + RS + "UNFILLED then 1 else 0 end) > 0) " +
            "group by r.questionText order by count(r) desc")
    List<Object[]> unfilledInPartial(Collection<Long> cafeIds, LocalDate fromDate, LocalDate toDate,
                                     boolean allVendors, Collection<Long> vendorIds, Pageable pageable);
}
