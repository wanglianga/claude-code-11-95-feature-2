package com.fishport.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "permit_items")
public class PermitItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "species_code", nullable = false)
    private String speciesCode;
    @Column(name = "limit_kg", nullable = false)
    private BigDecimal limitKg;

    public PermitItem() {
    }

    public PermitItem(String speciesCode, BigDecimal limitKg) {
        this.speciesCode = speciesCode;
        this.limitKg = limitKg;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSpeciesCode() { return speciesCode; }
    public void setSpeciesCode(String speciesCode) { this.speciesCode = speciesCode; }
    public BigDecimal getLimitKg() { return limitKg; }
    public void setLimitKg(BigDecimal limitKg) { this.limitKg = limitKg; }
}
