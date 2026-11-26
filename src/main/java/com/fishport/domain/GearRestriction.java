package com.fishport.domain;

import jakarta.persistence.*;

/** 渔具限制：allowed=false 表示该海区禁用该渔具 */
@Entity
@Table(name = "gear_restrictions")
public class GearRestriction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "area_code", nullable = false)
    private String areaCode;
    @Column(name = "gear_type", nullable = false)
    private String gearType;
    @Column(nullable = false)
    private boolean allowed;
    private String note;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAreaCode() { return areaCode; }
    public void setAreaCode(String areaCode) { this.areaCode = areaCode; }
    public String getGearType() { return gearType; }
    public void setGearType(String gearType) { this.gearType = gearType; }
    public boolean isAllowed() { return allowed; }
    public void setAllowed(boolean allowed) { this.allowed = allowed; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
