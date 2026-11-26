package com.fishport.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 额度账流水：只追加；idempotency_key 唯一，保证不重复扣减 */
@Entity
@Table(name = "quota_ledger")
public class QuotaLedgerEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "account_id", nullable = false)
    private Long accountId;
    @Column(name = "voyage_id")
    private Long voyageId;
    @Column(name = "permit_id")
    private Long permitId;
    @Column(name = "unload_record_id")
    private Long unloadRecordId;
    @Column(name = "species_code", nullable = false)
    private String speciesCode;
    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false)
    private Types.LedgerType entryType;
    @Column(name = "amount_kg", nullable = false)
    private BigDecimal amountKg;
    @Column(name = "reserved_after", nullable = false)
    private BigDecimal reservedAfter;
    @Column(name = "used_after", nullable = false)
    private BigDecimal usedAfter;
    @Column(name = "available_after", nullable = false)
    private BigDecimal availableAfter;
    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;
    private String remark;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public Long getVoyageId() { return voyageId; }
    public void setVoyageId(Long voyageId) { this.voyageId = voyageId; }
    public Long getPermitId() { return permitId; }
    public void setPermitId(Long permitId) { this.permitId = permitId; }
    public Long getUnloadRecordId() { return unloadRecordId; }
    public void setUnloadRecordId(Long unloadRecordId) { this.unloadRecordId = unloadRecordId; }
    public String getSpeciesCode() { return speciesCode; }
    public void setSpeciesCode(String speciesCode) { this.speciesCode = speciesCode; }
    public Types.LedgerType getEntryType() { return entryType; }
    public void setEntryType(Types.LedgerType entryType) { this.entryType = entryType; }
    public BigDecimal getAmountKg() { return amountKg; }
    public void setAmountKg(BigDecimal amountKg) { this.amountKg = amountKg; }
    public BigDecimal getReservedAfter() { return reservedAfter; }
    public void setReservedAfter(BigDecimal reservedAfter) { this.reservedAfter = reservedAfter; }
    public BigDecimal getUsedAfter() { return usedAfter; }
    public void setUsedAfter(BigDecimal usedAfter) { this.usedAfter = usedAfter; }
    public BigDecimal getAvailableAfter() { return availableAfter; }
    public void setAvailableAfter(BigDecimal availableAfter) { this.availableAfter = availableAfter; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
