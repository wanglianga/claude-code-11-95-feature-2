package com.fishport.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "permit_applications")
public class PermitApplication {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "application_no", nullable = false, unique = true)
    private String applicationNo;
    @Column(name = "vessel_id", nullable = false)
    private Long vesselId;
    @Column(name = "captain_name", nullable = false)
    private String captainName;
    @Column(name = "crew_count", nullable = false)
    private Integer crewCount;
    @Column(name = "crew_list", columnDefinition = "TEXT")
    private String crewList;
    @Column(name = "area_code", nullable = false)
    private String areaCode;
    @Column(name = "gear_type", nullable = false)
    private String gearType;
    @Column(name = "planned_start", nullable = false)
    private LocalDateTime plannedStart;
    @Column(name = "planned_end", nullable = false)
    private LocalDateTime plannedEnd;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Types.ApplicationStatus status = Types.ApplicationStatus.PENDING;
    @Column(name = "reject_reasons", columnDefinition = "TEXT")
    private String rejectReasons;
    @Column(name = "created_by")
    private String createdBy;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "application_id", nullable = false)
    private List<PermitApplicationItem> items = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getApplicationNo() { return applicationNo; }
    public void setApplicationNo(String applicationNo) { this.applicationNo = applicationNo; }
    public Long getVesselId() { return vesselId; }
    public void setVesselId(Long vesselId) { this.vesselId = vesselId; }
    public String getCaptainName() { return captainName; }
    public void setCaptainName(String captainName) { this.captainName = captainName; }
    public Integer getCrewCount() { return crewCount; }
    public void setCrewCount(Integer crewCount) { this.crewCount = crewCount; }
    public String getCrewList() { return crewList; }
    public void setCrewList(String crewList) { this.crewList = crewList; }
    public String getAreaCode() { return areaCode; }
    public void setAreaCode(String areaCode) { this.areaCode = areaCode; }
    public String getGearType() { return gearType; }
    public void setGearType(String gearType) { this.gearType = gearType; }
    public LocalDateTime getPlannedStart() { return plannedStart; }
    public void setPlannedStart(LocalDateTime plannedStart) { this.plannedStart = plannedStart; }
    public LocalDateTime getPlannedEnd() { return plannedEnd; }
    public void setPlannedEnd(LocalDateTime plannedEnd) { this.plannedEnd = plannedEnd; }
    public Types.ApplicationStatus getStatus() { return status; }
    public void setStatus(Types.ApplicationStatus status) { this.status = status; }
    public String getRejectReasons() { return rejectReasons; }
    public void setRejectReasons(String rejectReasons) { this.rejectReasons = rejectReasons; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public List<PermitApplicationItem> getItems() { return items; }
    public void setItems(List<PermitApplicationItem> items) { this.items = items; }
}
