package com.hungerbox.wings.repo;

import com.hungerbox.wings.domain.ComplianceDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface ComplianceDocumentRepository extends JpaRepository<ComplianceDocument, Long> {

    @Query("select d from ComplianceDocument d join fetch d.cafe left join fetch d.uploadedBy " +
            "where d.cafe.id in :cafeIds and (:allVendors = true or d.vendor.id in :vendorIds)")
    List<ComplianceDocument> inScope(Collection<Long> cafeIds, boolean allVendors, Collection<Long> vendorIds);

    @Query("select d from ComplianceDocument d join fetch d.cafe where d.id = :id")
    java.util.Optional<ComplianceDocument> findWithCafe(Long id);
}
