package com.fishport.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 航次：APPROVED -> DEPARTED -> RETURNED -> UNLOADING -> (REWEIGHING) -> CLOSED */
@Entity
@Table(name = "voyages")
public class Voyage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "voyage_no", nullable = false, unique = true)
    private String voyageNo;
    @Column(name = "permit_id", nullable = false, unique = true)
    private Long permitId;
    @Column(name = "vessel_id", nullable = false)
    private Long vesselId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Types.VoyageState state = Types.VoyageState.APPROVED;
    @Column(name = "departed_at")
    private LocalDateTime departedAt;
    @Column(name = "returned_at")
    private LocalDateTime returnedAt;
    @Column(name = "closed_at")
    private LocalDateTime closedAt;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    @Version
    private Long version;

    public boolean isOpen() {
        return state != Types.VoyageState.CLOSED;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getVoyageNo() { return voyageNo; }
    public void setVoyageNo(String voyageNo) { this.voyageNo = voyageNo; }
    public Long getPermitId() { return permitId; }
    public void setPermitId(Long permitId) { this.permitId = permitId; }
    public Long getVesselId() { return vesselId; }
    public void setVesselId(Long vesselId) { this.vesselId = vesselId; }
    public Types.VoyageState getState() { return state; }
    public void setState(Types.VoyageState state) { this.state = state; }
    public LocalDateTime getDepartedAt() { return departedAt; }
    public void setDepartedAt(LocalDateTime departedAt) { this.departedAt = departedAt; }
    public LocalDateTime getReturnedAt() { return returnedAt; }
    public void setReturnedAt(LocalDateTime returnedAt) { this.returnedAt = returnedAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
