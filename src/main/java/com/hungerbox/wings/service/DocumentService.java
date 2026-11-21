package com.hungerbox.wings.service;

import com.hungerbox.wings.common.ApiException;
import com.hungerbox.wings.domain.AppUser;
import com.hungerbox.wings.domain.ComplianceDocument;
import com.hungerbox.wings.domain.DocumentCategory;
import com.hungerbox.wings.domain.Role;
import com.hungerbox.wings.repo.ComplianceDocumentRepository;
import com.hungerbox.wings.scoring.ScoreMath;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class DocumentService {

    /** Quick guide to the documents expected per section (PRD 7.5, section 2). */
    public static final Map<String, List<String>> CATEGORY_GUIDE = new LinkedHashMap<>();
    static {
        CATEGORY_GUIDE.put("CLRA Compliance", List.of("Offer Letter", "In & Out Register", "Wages Register", "Pay Slips",
                "Insurance Card/Policy", "PF Details", "PF & ESI Challans"));
        CATEGORY_GUIDE.put("Food Safety Compliance", List.of("FSSAI Licence", "Medical Reports", "Water/Food Test Reports",
                "FOSTAC Certificate", "Gas Bank Licence", "HACCP/ISO Certificate", "Packaging Material Certificate"));
        CATEGORY_GUIDE.put("Business Compliance", List.of("Trade Licence", "Shop & Establishment Licence"));
        CATEGORY_GUIDE.put("CISO & IT Security Policies", List.of("Managed and uploaded by the Super Admin"));
    }

    public record DocItem(Long id, String name, String category, String cafe, LocalDate uploadedAt, LocalDate expiryDate,
                          String status, String statusType, String uploadedBy, boolean approved, String fileUrl) {}
    public record CategoryGroup(String category, int count, List<DocItem> documents) {}
    public record DocumentsReport(Double compliantPct, long compliantCount, long totalDocuments, long expired,
                                  long expiringIn30Days, boolean canApprove, List<CategoryGroup> actionables,
                                  Map<String, List<String>> categoryGuide) {}
    public record UploadRequest(String fileUrl, LocalDate expiryDate) {}

    private final ScopeService scopes;
    private final AccessGuard guard;
    private final DateRangeService dates;
    private final ComplianceDocumentRepository docs;

    public DocumentService(ScopeService scopes, AccessGuard guard, DateRangeService dates, ComplianceDocumentRepository docs) {
        this.scopes = scopes; this.guard = guard; this.dates = dates; this.docs = docs;
    }

    /** Document status is evaluated as of today; the date-range filter does not apply to this module. */
    public DocumentsReport report(Long userId, DashboardFilter f) {
        Scope scope = scopes.resolve(userId, f);
        LocalDate today = dates.today();
        List<ComplianceDocument> list = scope.cafeIds().isEmpty() ? List.of()
                : docs.inScope(scope.cafeIds(), scope.allVendors(), scope.vendorIdsOrSentinel());

        long expired = 0, expiring = 0;
        Map<DocumentCategory, List<DocItem>> grouped = new EnumMap<>(DocumentCategory.class);
        for (ComplianceDocument d : list) {
            String type = statusType(d, today);
            if (type.equals("EXPIRED")) expired++;
            if (type.equals("EXPIRING")) expiring++;
            grouped.computeIfAbsent(d.getCategory(), k -> new ArrayList<>()).add(toItem(d, today, type));
        }
        long compliant = list.size() - expired; // valid = not expired (includes those expiring soon)
        List<CategoryGroup> groups = new ArrayList<>();
        for (DocumentCategory c : DocumentCategory.values()) {
            List<DocItem> items = new ArrayList<>(grouped.getOrDefault(c, List.of()));
            items.sort(Comparator.comparing(DocItem::expiryDate, Comparator.nullsLast(Comparator.naturalOrder())));
            groups.add(new CategoryGroup(c.label(), items.size(), items));
        }
        boolean canApprove = scope.role() == Role.CITY_OPS_HEAD || scope.role() == Role.OPS_HEAD;
        return new DocumentsReport(ScoreMath.r2(ScoreMath.pct(compliant, list.size())), compliant, list.size(), expired,
                expiring, canApprove, groups, CATEGORY_GUIDE);
    }

    /** Approve action is visible/allowed only for City Ops Heads. */
    @Transactional
    public DocItem approve(Long userId, Long docId) {
        AppUser u = guard.require(userId, Role.CITY_OPS_HEAD, Role.OPS_HEAD);
        ComplianceDocument d = inScope(userId, docId);
        d.setApproved(true);
        return toItem(d, dates.today(), statusType(d, dates.today()));
    }

    /** Upload / re-upload: stores the new file reference and resets approval. File storage itself is out of scope. */
    @Transactional
    public DocItem upload(Long userId, Long docId, UploadRequest req) {
        AppUser u = guard.require(userId, Role.AREA_MANAGER, Role.CITY_OPS_HEAD, Role.OPS_HEAD);
        if (req == null || req.fileUrl() == null || req.fileUrl().isBlank()) throw ApiException.badRequest("fileUrl is required");
        ComplianceDocument d = inScope(userId, docId);
        d.setFileUrl(req.fileUrl());
        d.setExpiryDate(req.expiryDate());
        d.setUploadedAt(dates.today());
        d.setUploadedBy(u);
        d.setApproved(false);
        return toItem(d, dates.today(), statusType(d, dates.today()));
    }

    private ComplianceDocument inScope(Long userId, Long docId) {
        ComplianceDocument d = docs.findWithCafe(docId).orElseThrow(() -> ApiException.notFound("Document " + docId));
        if (!scopes.resolve(userId, DashboardFilter.empty()).cafeIds().contains(d.getCafe().getId())) {
            throw ApiException.forbidden("Document is outside your access scope");
        }
        return d;
    }

    private static String statusType(ComplianceDocument d, LocalDate today) {
        if (d.getExpiryDate() == null) return "VALID";
        long days = ChronoUnit.DAYS.between(today, d.getExpiryDate());
        return days < 0 ? "EXPIRED" : days <= 30 ? "EXPIRING" : "VALID";
    }

    private static DocItem toItem(ComplianceDocument d, LocalDate today, String type) {
        String status = "Valid";
        if (d.getExpiryDate() != null) {
            long days = ChronoUnit.DAYS.between(today, d.getExpiryDate());
            if (type.equals("EXPIRED")) status = (-days) + " days overdue";
            else if (type.equals("EXPIRING")) status = "Expires in " + days + " days";
        }
        return new DocItem(d.getId(), d.getName(), d.getCategory().label(), d.getCafe().getName(), d.getUploadedAt(),
                d.getExpiryDate(), status, type, d.getUploadedBy() == null ? null : d.getUploadedBy().getName(),
                d.isApproved(), d.getFileUrl());
    }
}
