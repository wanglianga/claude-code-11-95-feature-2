package com.fishport.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 地磅票据：ticket_no 唯一，重传返回原处理结果 */
@Entity
@Table(name = "weigh_tickets")
public class WeighTicket {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "ticket_no", nullable = false, unique = true)
    private String ticketNo;
    @Column(name = "voyage_id", nullable = false)
    private Long voyageId;
    @Column(name = "unload_record_id", nullable = false)
    private Long unloadRecordId;
    @Column(name = "species_code", nullable = false)
    private String speciesCode;
    @Column(name = "gross_kg", nullable = false)
    private BigDecimal grossKg;
    @Column(name = "tare_kg", nullable = false)
    private BigDecimal tareKg;
    @Column(name = "net_kg", nullable = false)
    private BigDecimal netKg;
    @Column(nullable = false)
    private String source;
    @Column(name = "weighed_at")
    private LocalDateTime weighedAt;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTicketNo() { return ticketNo; }
    public void setTicketNo(String ticketNo) { this.ticketNo = ticketNo; }
    public Long getVoyageId() { return voyageId; }
    public void setVoyageId(Long voyageId) { this.voyageId = voyageId; }
    public Long getUnloadRecordId() { return unloadRecordId; }
    public void setUnloadRecordId(Long unloadRecordId) { this.unloadRecordId = unloadRecordId; }
    public String getSpeciesCode() { return speciesCode; }
    public void setSpeciesCode(String speciesCode) { this.speciesCode = speciesCode; }
    public BigDecimal getGrossKg() { return grossKg; }
    public void setGrossKg(BigDecimal grossKg) { this.grossKg = grossKg; }
    public BigDecimal getTareKg() { return tareKg; }
    public void setTareKg(BigDecimal tareKg) { this.tareKg = tareKg; }
    public BigDecimal getNetKg() { return netKg; }
    public void setNetKg(BigDecimal netKg) { this.netKg = netKg; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public LocalDateTime getWeighedAt() { return weighedAt; }
    public void setWeighedAt(LocalDateTime weighedAt) { this.weighedAt = weighedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
