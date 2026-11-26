package com.fishport.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 离港许可窗：当前港窗口与另一港窗口。
 * 窗口关闭时刻之后不得再显示为可用（available 由 window_to 与实际时刻共同决定）。
 */
@Entity
@Table(name = "departure_windows")
public class DepartureWindow {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "window_no", nullable = false, unique = true)
    private String windowNo;
    @Column(name = "voyage_id", nullable = false)
    private Long voyageId;
    @Column(name = "port_code", nullable = false)
    private String portCode;
    /** CURRENT 当前港窗口 / ALTERNATIVE 另一港窗口 */
    @Column(name = "window_kind", nullable = false, length = 16)
    private String windowKind;
    @Column(name = "window_from", nullable = false)
    private LocalDateTime windowFrom;
    @Column(name = "window_to", nullable = false)
    private LocalDateTime windowTo;
    /** 窗口是否被取消/作废（如船舶已离港后该窗口不再可用） */
    @Column(name = "cancelled", nullable = false)
    private boolean cancelled = false;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public boolean availableAt(LocalDateTime moment) {
        return !cancelled && !moment.isAfter(windowTo) && !moment.isBefore(windowFrom);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getWindowNo() { return windowNo; }
    public void setWindowNo(String windowNo) { this.windowNo = windowNo; }
    public Long getVoyageId() { return voyageId; }
    public void setVoyageId(Long voyageId) { this.voyageId = voyageId; }
    public String getPortCode() { return portCode; }
    public void setPortCode(String portCode) { this.portCode = portCode; }
    public String getWindowKind() { return windowKind; }
    public void setWindowKind(String windowKind) { this.windowKind = windowKind; }
    public LocalDateTime getWindowFrom() { return windowFrom; }
    public void setWindowFrom(LocalDateTime windowFrom) { this.windowFrom = windowFrom; }
    public LocalDateTime getWindowTo() { return windowTo; }
    public void setWindowTo(LocalDateTime windowTo) { this.windowTo = windowTo; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
