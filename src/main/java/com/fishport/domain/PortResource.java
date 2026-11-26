package com.fishport.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

/** 港口资源：一条卸货线 + 一台地磅 + 有限冷库，作业间清场 cleanupMin 分钟 */
@Entity
@Table(name = "port_resources")
public class PortResource {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String code;
    @Column(nullable = false)
    private String name;
    @Column(name = "open_time", nullable = false, length = 5)
    private String openTime;
    @Column(name = "close_time", nullable = false, length = 5)
    private String closeTime;
    @Column(name = "cleanup_min", nullable = false)
    private Integer cleanupMin = 5;
    @Column(name = "cold_capacity_kg", nullable = false)
    private BigDecimal coldCapacityKg;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getOpenTime() { return openTime; }
    public void setOpenTime(String openTime) { this.openTime = openTime; }
    public String getCloseTime() { return closeTime; }
    public void setCloseTime(String closeTime) { this.closeTime = closeTime; }
    public Integer getCleanupMin() { return cleanupMin; }
    public void setCleanupMin(Integer cleanupMin) { this.cleanupMin = cleanupMin; }
    public BigDecimal getColdCapacityKg() { return coldCapacityKg; }
    public void setColdCapacityKg(BigDecimal coldCapacityKg) { this.coldCapacityKg = coldCapacityKg; }
}
