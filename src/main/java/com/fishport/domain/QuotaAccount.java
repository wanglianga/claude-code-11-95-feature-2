package com.fishport.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

/** 物种额度账：严格按 海区+物种+年度 分立，剩余额度不得跨账相加 */
@Entity
@Table(name = "quota_accounts")
public class QuotaAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "area_code", nullable = false)
    private String areaCode;
    @Column(name = "species_code", nullable = false)
    private String speciesCode;
    @Column(name = "season_year", nullable = false)
    private Integer seasonYear;
    @Column(name = "total_kg", nullable = false)
    private BigDecimal totalKg;
    @Column(name = "reserved_kg", nullable = false)
    private BigDecimal reservedKg = BigDecimal.ZERO;
    @Column(name = "used_kg", nullable = false)
    private BigDecimal usedKg = BigDecimal.ZERO;
    @Version
    private Long version;

    /** 可用额度 = 总额 - 已保留 - 已使用 */
    public BigDecimal available() {
        return totalKg.subtract(reservedKg).subtract(usedKg);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAreaCode() { return areaCode; }
    public void setAreaCode(String areaCode) { this.areaCode = areaCode; }
    public String getSpeciesCode() { return speciesCode; }
    public void setSpeciesCode(String speciesCode) { this.speciesCode = speciesCode; }
    public Integer getSeasonYear() { return seasonYear; }
    public void setSeasonYear(Integer seasonYear) { this.seasonYear = seasonYear; }
    public BigDecimal getTotalKg() { return totalKg; }
    public void setTotalKg(BigDecimal totalKg) { this.totalKg = totalKg; }
    public BigDecimal getReservedKg() { return reservedKg; }
    public void setReservedKg(BigDecimal reservedKg) { this.reservedKg = reservedKg; }
    public BigDecimal getUsedKg() { return usedKg; }
    public void setUsedKg(BigDecimal usedKg) { this.usedKg = usedKg; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
