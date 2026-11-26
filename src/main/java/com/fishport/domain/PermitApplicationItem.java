package com.fishport.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "permit_application_items")
public class PermitApplicationItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "species_code", nullable = false)
    private String speciesCode;
    @Column(name = "declared_limit_kg", nullable = false)
    private BigDecimal declaredLimitKg;

    public PermitApplicationItem() {
    }

    public PermitApplicationItem(String speciesCode, BigDecimal declaredLimitKg) {
        this.speciesCode = speciesCode;
        this.declaredLimitKg = declaredLimitKg;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSpeciesCode() { return speciesCode; }
    public void setSpeciesCode(String speciesCode) { this.speciesCode = speciesCode; }
    public BigDecimal getDeclaredLimitKg() { return declaredLimitKg; }
    public void setDeclaredLimitKg(BigDecimal declaredLimitKg) { this.declaredLimitKg = declaredLimitKg; }
}
