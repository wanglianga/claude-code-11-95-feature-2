package com.fishport.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 名册行：单个船员在某一版名册中的岗位覆盖与任职资格。
 * 离港闸按整份名册逐行重核岗位覆盖（岗位、船型权限、证书、休息、航次职责、专项资质），
 * 而非只查被替换的一行。
 */
@Entity
@Table(name = "crew_members")
public class CrewMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "revision_id", nullable = false)
    private Long revisionId;
    /** 同一自然人跨版本的稳定人员编号 */
    @Column(name = "person_no", nullable = false)
    private String personNo;
    @Column(nullable = false)
    private String name;
    /** 岗位：CAPTAIN 船长 / CHIEF_ENGINEER 轮机长 / ENGINEER 轮机员 / SAILOR 船员 ... */
    @Column(name = "position_code", nullable = false)
    private String positionCode;
    /** 航次职责：本航次实际承担职责（如 当班船长/值班轮机员） */
    @Column(name = "voyage_duty", nullable = false)
    private String voyageDuty;
    /** 证书编号 */
    @Column(name = "certificate_no", nullable = false)
    private String certificateNo;
    @Column(name = "cert_valid_to", nullable = false)
    private LocalDateTime certValidTo;
    /** 可任职船型权限码列表，逗号分隔（如 TRAWLER,LONGLINER） */
    @Column(name = "vessel_type_grants", nullable = false)
    private String vesselTypeGrants;
    /** 专项资质码列表，逗号分隔（如 REEFER 冷藏设备操作）；可为空 */
    @Column(name = "qualification_codes")
    private String qualificationCodes;
    /** 最近一次休息结束时刻：休息达标要求 restReadyAt 不晚于离港时刻 */
    @Column(name = "rest_ready_at", nullable = false)
    private LocalDateTime restReadyAt;
    /** 应急联系电话（仅应急方投影包含该字段） */
    @Column(name = "emergency_phone")
    private String emergencyPhone;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public CrewMember() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRevisionId() { return revisionId; }
    public void setRevisionId(Long revisionId) { this.revisionId = revisionId; }
    public String getPersonNo() { return personNo; }
    public void setPersonNo(String personNo) { this.personNo = personNo; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPositionCode() { return positionCode; }
    public void setPositionCode(String positionCode) { this.positionCode = positionCode; }
    public String getVoyageDuty() { return voyageDuty; }
    public void setVoyageDuty(String voyageDuty) { this.voyageDuty = voyageDuty; }
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
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
