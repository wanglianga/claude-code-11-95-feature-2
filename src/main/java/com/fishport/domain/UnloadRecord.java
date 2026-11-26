package com.fishport.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 分物种卸货记录：一航次一物种一条 */
@Entity
@Table(name = "unload_records")
public class UnloadRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "voyage_id", nullable = false)
    private Long voyageId;
    @Column(name = "species_code", nullable = false)
    private String speciesCode;
    @Column(name = "declared_kg", nullable = false)
    private BigDecimal declaredKg;
    @Column(name = "weighed_kg")
    private BigDecimal weighedKg;
    @Column(name = "weigh_ticket_id")
    private Long weighTicketId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Types.UnloadStatus status = Types.UnloadStatus.DECLARED;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "signed_at")
    private LocalDateTime signedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getVoyageId() { return voyageId; }
    public void setVoyageId(Long voyageId) { this.voyageId = voyageId; }
    public String getSpeciesCode() { return speciesCode; }
    public void setSpeciesCode(String speciesCode) { this.speciesCode = speciesCode; }
    public BigDecimal getDeclaredKg() { return declaredKg; }
    public void setDeclaredKg(BigDecimal declaredKg) { this.declaredKg = declaredKg; }
    public BigDecimal getWeighedKg() { return weighedKg; }
    public void setWeighedKg(BigDecimal weighedKg) { this.weighedKg = weighedKg; }
    public Long getWeighTicketId() { return weighTicketId; }
    public void setWeighTicketId(Long weighTicketId) { this.weighTicketId = weighTicketId; }
    public Types.UnloadStatus getStatus() { return status; }
    public void setStatus(Types.UnloadStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getSignedAt() { return signedAt; }
    public void setSignedAt(LocalDateTime signedAt) { this.signedAt = signedAt; }
}
