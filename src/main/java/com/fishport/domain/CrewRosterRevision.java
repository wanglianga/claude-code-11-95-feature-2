package com.fishport.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 船员名册修订（只追加版本链）。
 * 每次重签产生新修订并取代上一版；接收方确认按字段投影绑定（见 RosterAcknowledgement），
 * 投影内容或协议版本变化时该方确认失效。三方确认齐备后修订才转为 RELEASED 可放行。
 */
@Entity
@Table(name = "crew_roster_revisions")
public class CrewRosterRevision {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "revision_no", nullable = false, unique = true)
    private String revisionNo;
    @Column(name = "voyage_id", nullable = false)
    private Long voyageId;
    /** 版本序号，从 1 开始，逐版递增 */
    @Column(name = "revision_seq", nullable = false)
    private Integer revisionSeq;
    @Column(name = "supersedes_id")
    private Long supersedesId;
    @Enumerated(EnumType.STRING)
    @Column(name = "change_kind", nullable = false)
    private Types.RosterChangeKind changeKind;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Types.RosterStatus status = Types.RosterStatus.DRAFT;
    /** 触发修订的原因（如 原船长休息不足2小时、轮机员请假、更正应急电话） */
    @Column(name = "change_reason", length = 512)
    private String changeReason;
    /** 船东决定外键（决定后回填） */
    @Column(name = "owner_decision_id")
    private Long ownerDecisionId;
    /** 本航次要求的专项资质（逗号分隔，如 REEFER），离港闸按整份名册核验覆盖 */
    @Column(name = "required_qualifications")
    private String requiredQualifications;
    /** 重签完成时刻：旧凭证同时撤销、三方已读状态重置 */
    @Column(name = "signed_at")
    private LocalDateTime signedAt;
    /** 放行时刻：三方投影确认齐备 */
    @Column(name = "released_at")
    private LocalDateTime releasedAt;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    @Version
    private Long version;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRevisionNo() { return revisionNo; }
    public void setRevisionNo(String revisionNo) { this.revisionNo = revisionNo; }
    public Long getVoyageId() { return voyageId; }
    public void setVoyageId(Long voyageId) { this.voyageId = voyageId; }
    public Integer getRevisionSeq() { return revisionSeq; }
    public void setRevisionSeq(Integer revisionSeq) { this.revisionSeq = revisionSeq; }
    public Long getSupersedesId() { return supersedesId; }
    public void setSupersedesId(Long supersedesId) { this.supersedesId = supersedesId; }
    public Types.RosterChangeKind getChangeKind() { return changeKind; }
    public void setChangeKind(Types.RosterChangeKind changeKind) { this.changeKind = changeKind; }
    public Types.RosterStatus getStatus() { return status; }
    public void setStatus(Types.RosterStatus status) { this.status = status; }
    public String getChangeReason() { return changeReason; }
    public void setChangeReason(String changeReason) { this.changeReason = changeReason; }
    public Long getOwnerDecisionId() { return ownerDecisionId; }
    public void setOwnerDecisionId(Long ownerDecisionId) { this.ownerDecisionId = ownerDecisionId; }
    public String getRequiredQualifications() { return requiredQualifications; }
    public void setRequiredQualifications(String requiredQualifications) { this.requiredQualifications = requiredQualifications; }
    public LocalDateTime getSignedAt() { return signedAt; }
    public void setSignedAt(LocalDateTime signedAt) { this.signedAt = signedAt; }
    public LocalDateTime getReleasedAt() { return releasedAt; }
    public void setReleasedAt(LocalDateTime releasedAt) { this.releasedAt = releasedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
