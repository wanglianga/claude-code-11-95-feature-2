package com.fishport.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 人员主档：证书、船型权限、专项资质与休息达标时刻的权威来源，名册修订行从主档复制快照 */
@Entity
@Table(name = "crew_person_master")
public class CrewPerson {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "person_no", nullable = false, unique = true)
    private String personNo;
    @Column(nullable = false)
    private String name;
    @Column(name = "position_code", nullable = false)
    private String positionCode;
    @Column(name = "certificate_no", nullable = false)
    private String certificateNo;
    @Column(name = "cert_valid_to", nullable = false)
    private LocalDateTime certValidTo;
    @Column(name = "vessel_type_grants", nullable = false)
    private String vesselTypeGrants;
    @Column(name = "qualification_codes")
    private String qualificationCodes;
    @Column(name = "rest_ready_at", nullable = false)
    private LocalDateTime restReadyAt;
    @Column(name = "emergency_phone")
    private String emergencyPhone;
    @Column(nullable = false)
    private boolean active = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPersonNo() { return personNo; }
    public void setPersonNo(String personNo) { this.personNo = personNo; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPositionCode() { return positionCode; }
    public void setPositionCode(String positionCode) { this.positionCode = positionCode; }
    public String getCertificateNo() { return certificateNo; }
    public void setCertificateNo(String certificateNo) { this.certificateNo = certificateNo; }
    public LocalDateTime getCertValidTo() { return certValidTo; }
    public void setCertValidTo(LocalDateTime certValidTo) { this.certValidTo = certValidTo; }
    public String getVesselTypeGrants() { return vesselTypeGrants; }
    public void setVesselTypeGrants(String vesselTypeGrants) { this.vesselTypeGrants = vesselTypeGrants; }
    public String getQualificationCodes() { return qualificationCodes; }
    public void setQualificationCodes(String qualificationCodes) { this.qualificationCodes = qualificationCodes; }
    public LocalDateTime getRestReadyAt() { return restReadyAt; }
    public void setRestReadyAt(LocalDateTime restReadyAt) { this.restReadyAt = restReadyAt; }
    public String getEmergencyPhone() { return emergencyPhone; }
    public void setEmergencyPhone(String emergencyPhone) { this.emergencyPhone = emergencyPhone; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
