package com.fishport.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 船东决定：两种选择（原船长休息后继续 / 启用替补）及名册更正都必须保留船东决定与通知回执。
 */
@Entity
@Table(name = "owner_decisions")
public class OwnerDecision {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "decision_no", nullable = false, unique = true)
    private String decisionNo;
    @Column(name = "voyage_id", nullable = false)
    private Long voyageId;
    @Enumerated(EnumType.STRING)
    @Column(name = "decision_type", nullable = false)
    private Types.OwnerDecisionType decisionType;
    @Column(name = "decided_by", nullable = false)
    private String decidedBy;
    @Column(name = "decided_at", nullable = false)
    private LocalDateTime decidedAt = LocalDateTime.now();
    /** 决定理由/备注（如 赶11:00当前离港窗） */
    @Column(length = 512)
    private String reason;
    /** 该决定触发的名册修订 */
    @Column(name = "revision_id")
    private Long revisionId;
    /** 通知送达回执（消息渠道回执号），与决定成对保留 */
    @Column(name = "notify_receipt_no")
    private String notifyReceiptNo;
    @Column(name = "notified_at")
    private LocalDateTime notifiedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDecisionNo() { return decisionNo; }
    public void setDecisionNo(String decisionNo) { this.decisionNo = decisionNo; }
    public Long getVoyageId() { return voyageId; }
    public void setVoyageId(Long voyageId) { this.voyageId = voyageId; }
    public Types.OwnerDecisionType getDecisionType() { return decisionType; }
    public void setDecisionType(Types.OwnerDecisionType decisionType) { this.decisionType = decisionType; }
    public String getDecidedBy() { return decidedBy; }
    public void setDecidedBy(String decidedBy) { this.decidedBy = decidedBy; }
    public LocalDateTime getDecidedAt() { return decidedAt; }
    public void setDecidedAt(LocalDateTime decidedAt) { this.decidedAt = decidedAt; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Long getRevisionId() { return revisionId; }
    public void setRevisionId(Long revisionId) { this.revisionId = revisionId; }
    public String getNotifyReceiptNo() { return notifyReceiptNo; }
    public void setNotifyReceiptNo(String notifyReceiptNo) { this.notifyReceiptNo = notifyReceiptNo; }
    public LocalDateTime getNotifiedAt() { return notifiedAt; }
    public void setNotifiedAt(LocalDateTime notifiedAt) { this.notifiedAt = notifiedAt; }
}
