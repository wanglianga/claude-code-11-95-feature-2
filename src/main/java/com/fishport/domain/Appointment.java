package com.fishport.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 卸港预约：FIXED 固定时段 / ROLLING 滚动叫号 */
@Entity
@Table(name = "appointments")
public class Appointment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "appointment_no", nullable = false, unique = true)
    private String appointmentNo;
    @Column(name = "voyage_id", nullable = false)
    private Long voyageId;
    @Column(name = "port_id", nullable = false)
    private Long portId;
    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Types.ApptMode mode;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Types.ApptStatus status = Types.ApptStatus.BOOKED;
    /** 固定模式下申请/已通知的时段起点 */
    @Column(name = "requested_start")
    private LocalDateTime requestedStart;
    @Column(name = "planned_arrival")
    private LocalDateTime plannedArrival;
    @Column(name = "actual_arrival")
    private LocalDateTime actualArrival;
    @Column(name = "slot_start")
    private LocalDateTime slotStart;
    @Column(name = "slot_end")
    private LocalDateTime slotEnd;
    @Column(name = "duration_min", nullable = false)
    private Integer durationMin;
    @Column(name = "cleanup_min", nullable = false)
    private Integer cleanupMin = 5;
    @Column(name = "latest_finish", nullable = false)
    private LocalDateTime latestFinish;
    @Column(name = "expected_unload_kg", nullable = false)
    private BigDecimal expectedUnloadKg = BigDecimal.ZERO;
    /** 已通知的排队序号 */
    private Integer seq;
    @Column(name = "deadline_breach", nullable = false)
    private boolean deadlineBreach = false;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    @Version
    private Long version;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAppointmentNo() { return appointmentNo; }
    public void setAppointmentNo(String appointmentNo) { this.appointmentNo = appointmentNo; }
    public Long getVoyageId() { return voyageId; }
    public void setVoyageId(Long voyageId) { this.voyageId = voyageId; }
    public Long getPortId() { return portId; }
    public void setPortId(Long portId) { this.portId = portId; }
    public LocalDate getServiceDate() { return serviceDate; }
    public void setServiceDate(LocalDate serviceDate) { this.serviceDate = serviceDate; }
    public Types.ApptMode getMode() { return mode; }
    public void setMode(Types.ApptMode mode) { this.mode = mode; }
    public Types.ApptStatus getStatus() { return status; }
    public void setStatus(Types.ApptStatus status) { this.status = status; }
    public LocalDateTime getRequestedStart() { return requestedStart; }
    public void setRequestedStart(LocalDateTime requestedStart) { this.requestedStart = requestedStart; }
    public LocalDateTime getPlannedArrival() { return plannedArrival; }
    public void setPlannedArrival(LocalDateTime plannedArrival) { this.plannedArrival = plannedArrival; }
    public LocalDateTime getActualArrival() { return actualArrival; }
    public void setActualArrival(LocalDateTime actualArrival) { this.actualArrival = actualArrival; }
    public LocalDateTime getSlotStart() { return slotStart; }
    public void setSlotStart(LocalDateTime slotStart) { this.slotStart = slotStart; }
    public LocalDateTime getSlotEnd() { return slotEnd; }
    public void setSlotEnd(LocalDateTime slotEnd) { this.slotEnd = slotEnd; }
    public Integer getDurationMin() { return durationMin; }
    public void setDurationMin(Integer durationMin) { this.durationMin = durationMin; }
    public Integer getCleanupMin() { return cleanupMin; }
    public void setCleanupMin(Integer cleanupMin) { this.cleanupMin = cleanupMin; }
    public LocalDateTime getLatestFinish() { return latestFinish; }
    public void setLatestFinish(LocalDateTime latestFinish) { this.latestFinish = latestFinish; }
    public BigDecimal getExpectedUnloadKg() { return expectedUnloadKg; }
    public void setExpectedUnloadKg(BigDecimal expectedUnloadKg) { this.expectedUnloadKg = expectedUnloadKg; }
    public Integer getSeq() { return seq; }
    public void setSeq(Integer seq) { this.seq = seq; }
    public boolean isDeadlineBreach() { return deadlineBreach; }
    public void setDeadlineBreach(boolean deadlineBreach) { this.deadlineBreach = deadlineBreach; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
