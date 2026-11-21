package com.hungerbox.wings.domain;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "compliance_document", indexes = @Index(name = "ix_doc_cafe", columnList = "cafe_id"))
public class ComplianceDocument {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "cafe_id")
    private Cafe cafe;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "vendor_id")
    private Vendor vendor;
    @Column(nullable = false)
    private String name;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private DocumentCategory category;
    private LocalDate uploadedAt;
    private LocalDate expiryDate;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "uploaded_by")
    private AppUser uploadedBy;
    private boolean approved;
    private String fileUrl;

    protected ComplianceDocument() {}
    public ComplianceDocument(Cafe cafe, Vendor vendor, String name, DocumentCategory category, LocalDate uploadedAt,
                              LocalDate expiryDate, AppUser uploadedBy, boolean approved) {
        this.cafe = cafe; this.vendor = vendor; this.name = name; this.category = category;
        this.uploadedAt = uploadedAt; this.expiryDate = expiryDate; this.uploadedBy = uploadedBy; this.approved = approved;
    }

    public Long getId() { return id; }
    public Cafe getCafe() { return cafe; }
    public String getName() { return name; }
    public DocumentCategory getCategory() { return category; }
    public LocalDate getUploadedAt() { return uploadedAt; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public AppUser getUploadedBy() { return uploadedBy; }
    public boolean isApproved() { return approved; }
    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }
    public void setUploadedAt(LocalDate uploadedAt) { this.uploadedAt = uploadedAt; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
    public void setUploadedBy(AppUser uploadedBy) { this.uploadedBy = uploadedBy; }
    public void setApproved(boolean approved) { this.approved = approved; }
}
