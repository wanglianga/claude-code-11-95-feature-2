package com.fishport.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "permits")
public class Permit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "permit_no", nullable = false, unique = true)
    private String permitNo;
    @Column(name = "application_id", nullable = false)
    private Long applicationId;
    @Column(name = "vessel_id", nullable = false)
    private Long vesselId;
    @Column(name = "area_code", nullable = false)
    private String areaCode;
    @Column(name = "gear_type", nullable = false)
    private String gearType;
    @Column(name = "valid_from", nullable = false)
    private LocalDateTime validFrom;
    @Column(name = "valid_to", nullable = false)
    private LocalDateTime validTo;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Types.PermitStatus status = Types.PermitStatus.ACTIVE;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "permit_id", nullable = false)
    private List<PermitItem> items = new ArrayList<>();

    public boolean expiredAt(LocalDateTime moment) {
        return moment.isAfter(validTo);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPermitNo() { return permitNo; }
    public void setPermitNo(String permitNo) { this.permitNo = permitNo; }
    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }
    public Long getVesselId() { return vesselId; }
    public void setVesselId(Long vesselId) { this.vesselId = vesselId; }
    public String getAreaCode() { return areaCode; }
    public void setAreaCode(String areaCode) { this.areaCode = areaCode; }
    public String getGearType() { return gearType; }
    public void setGearType(String gearType) { this.gearType = gearType; }
    public LocalDateTime getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDateTime validFrom) { this.validFrom = validFrom; }
    public LocalDateTime getValidTo() { return validTo; }
    public void setValidTo(LocalDateTime validTo) { this.validTo = validTo; }
    public Types.PermitStatus getStatus() { return status; }
    public void setStatus(Types.PermitStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public List<PermitItem> getItems() { return items; }
    public void setItems(List<PermitItem> items) { this.items = items; }
}
