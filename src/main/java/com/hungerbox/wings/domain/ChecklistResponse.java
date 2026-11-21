package com.hungerbox.wings.domain;

import jakarta.persistence.*;

/** One mapped question of a checklist and its answer state (UNFILLED rows are kept so fill rate is computable). */
@Entity
@Table(name = "checklist_response", indexes = @Index(name = "ix_cr_checklist", columnList = "checklist_id"))
public class ChecklistResponse {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "checklist_id")
    private Checklist checklist;
    @Column(nullable = false, length = 500)
    private String questionText;
    /** Predefined bucket/category, e.g. "Hygiene & Cleanliness", "Food Safety". */
    private String bucket;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private AnswerType answerType;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private ResponseStatus status;
    /** Only meaningful for ANSWERED Boolean / AI-graded questions. */
    private Boolean compliant;

    protected ChecklistResponse() {}
    public ChecklistResponse(Checklist checklist, String questionText, String bucket, AnswerType answerType,
                             ResponseStatus status, Boolean compliant) {
        this.checklist = checklist; this.questionText = questionText; this.bucket = bucket;
        this.answerType = answerType; this.status = status; this.compliant = compliant;
    }
}
