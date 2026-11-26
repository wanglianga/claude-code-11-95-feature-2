package com.fishport.domain;

import jakarta.persistence.*;

/** 禁渔期规则：closedFrom/closedTo 为 MM-dd，支持跨年区间 */
@Entity
@Table(name = "season_rules")
public class SeasonRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "area_code", nullable = false)
    private String areaCode;
    @Column(name = "species_code", nullable = false)
    private String speciesCode;
    @Column(name = "closed_from", nullable = false, length = 5)
    private String closedFrom;
    @Column(name = "closed_to", nullable = false, length = 5)
    private String closedTo;
    private String note;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAreaCode() { return areaCode; }
    public void setAreaCode(String areaCode) { this.areaCode = areaCode; }
    public String getSpeciesCode() { return speciesCode; }
    public void setSpeciesCode(String speciesCode) { this.speciesCode = speciesCode; }
    public String getClosedFrom() { return closedFrom; }
    public void setClosedFrom(String closedFrom) { this.closedFrom = closedFrom; }
    public String getClosedTo() { return closedTo; }
    public void setClosedTo(String closedTo) { this.closedTo = closedTo; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
