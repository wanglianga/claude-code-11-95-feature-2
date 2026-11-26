package com.fishport.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 监管回执：关键事件上报监管部门后取得的回执 */
@Entity
@Table(name = "regulatory_receipts")
public class RegulatoryReceipt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "receipt_no", nullable = false, unique = true)
    private String receiptNo;
    @Column(name = "voyage_id")
    private Long voyageId;
    @Column(name = "permit_id")
    private Long permitId;
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private Types.ReceiptEventType eventType;
    @Column(columnDefinition = "TEXT")
    private String payload;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Types.ReceiptStatus status = Types.ReceiptStatus.REPORTED;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "acked_at")
    private LocalDateTime ackedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getReceiptNo() { return receiptNo; }
    public void setReceiptNo(String receiptNo) { this.receiptNo = receiptNo; }
    public Long getVoyageId() { return voyageId; }
    public void setVoyageId(Long voyageId) { this.voyageId = voyageId; }
    public Long getPermitId() { return permitId; }
    public void setPermitId(Long permitId) { this.permitId = permitId; }
    public Types.ReceiptEventType getEventType() { return eventType; }
    public void setEventType(Types.ReceiptEventType eventType) { this.eventType = eventType; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public Types.ReceiptStatus getStatus() { return status; }
    public void setStatus(Types.ReceiptStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getAckedAt() { return ackedAt; }
    public void setAckedAt(LocalDateTime ackedAt) { this.ackedAt = ackedAt; }
}
