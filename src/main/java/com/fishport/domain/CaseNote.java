package com.fishport.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 案件备注：仅执法人员(ENFORCEMENT)的备注才是执法事实，客服备注不算 */
@Entity
@Table(name = "case_notes")
public class CaseNote {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "voyage_id", nullable = false)
    private Long voyageId;
    @Column(nullable = false)
    private String author;
    @Column(name = "author_role", nullable = false)
    private String authorRole;
    @Column(nullable = false, length = 512)
    private String content;
    @Column(name = "enforcement_fact", nullable = false)
    private boolean enforcementFact = false;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getVoyageId() { return voyageId; }
    public void setVoyageId(Long voyageId) { this.voyageId = voyageId; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public String getAuthorRole() { return authorRole; }
    public void setAuthorRole(String authorRole) { this.authorRole = authorRole; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public boolean isEnforcementFact() { return enforcementFact; }
    public void setEnforcementFact(boolean enforcementFact) { this.enforcementFact = enforcementFact; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
