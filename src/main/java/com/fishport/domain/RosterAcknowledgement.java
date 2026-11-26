package com.fishport.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 接收方投影确认（字段投影绑定模型，本系统唯一实现，不提供整单/投影切换）。
 * 每条确认绑定：接收方 + 名册修订 + 投影协议版本 + 投影内容哈希。
 * 下一版名册重签时：
 *  - 投影内容与协议版本逐项一致 → 旧确认沿用（CARRIED，不打扰未受影响方）；
 *  - 任一变化            → 旧确认置 INVALIDATED 并记录失效原因，该方需重新签认；
 * 严禁新名册配旧船长确认：修订号不同的确认永远不能直接放行，只有投影逐项一致才可沿用。
 */
@Entity
@Table(name = "roster_acknowledgements")
public class RosterAcknowledgement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "ack_no", nullable = false, unique = true)
    private String ackNo;
    @Column(name = "revision_id", nullable = false)
    private Long revisionId;
    @Column(name = "voyage_id", nullable = false)
    private Long voyageId;
    @Enumerated(EnumType.STRING)
    @Column(name = "party", nullable = false)
    private Types.RosterParty party;
    /** 投影协议版本：协议升级即旧确认全部失效 */
    @Column(name = "projection_version", nullable = false)
    private String projectionVersion;
    /** 该修订下该方投影字段的内容哈希 */
    @Column(name = "projection_hash", nullable = false, length = 128)
    private String projectionHash;
    /** 投影包含的字段清单（逗号分隔，用于展示回执依赖） */
    @Column(name = "projection_fields", nullable = false, length = 512)
    private String projectionFields;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Types.AcknowledgementStatus status = Types.AcknowledgementStatus.PENDING;
    /** CARRIED 时记录来源确认号；否则为空 */
    @Column(name = "carried_from_ack_id")
    private Long carriedFromAckId;
    @Column(name = "confirmed_by")
    private String confirmedBy;
    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;
    /** 失效原因（如 船长/岗位/航次身份变化；投影哈希变化；协议版本变化） */
    @Column(name = "invalidated_reason", length = 512)
    private String invalidatedReason;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAckNo() { return ackNo; }
    public void setAckNo(String ackNo) { this.ackNo = ackNo; }
    public Long getRevisionId() { return revisionId; }
    public void setRevisionId(Long revisionId) { this.revisionId = revisionId; }
    public Long getVoyageId() { return voyageId; }
    public void setVoyageId(Long voyageId) { this.voyageId = voyageId; }
    public Types.RosterParty getParty() { return party; }
    public void setParty(Types.RosterParty party) { this.party = party; }
    public String getProjectionVersion() { return projectionVersion; }
    public void setProjectionVersion(String projectionVersion) { this.projectionVersion = projectionVersion; }
    public String getProjectionHash() { return projectionHash; }
    public void setProjectionHash(String projectionHash) { this.projectionHash = projectionHash; }
    public String getProjectionFields() { return projectionFields; }
    public void setProjectionFields(String projectionFields) { this.projectionFields = projectionFields; }
    public Types.AcknowledgementStatus getStatus() { return status; }
    public void setStatus(Types.AcknowledgementStatus status) { this.status = status; }
    public Long getCarriedFromAckId() { return carriedFromAckId; }
    public void setCarriedFromAckId(Long carriedFromAckId) { this.carriedFromAckId = carriedFromAckId; }
    public String getConfirmedBy() { return confirmedBy; }
    public void setConfirmedBy(String confirmedBy) { this.confirmedBy = confirmedBy; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(LocalDateTime confirmedAt) { this.confirmedAt = confirmedAt; }
    public String getInvalidatedReason() { return invalidatedReason; }
    public void setInvalidatedReason(String invalidatedReason) { this.invalidatedReason = invalidatedReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
