package com.fishport.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "fishing_areas")
public class FishingArea {
    @Id
    @Column(length = 16)
    private String code;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private boolean active = true;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
