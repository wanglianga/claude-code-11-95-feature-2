package com.fishport.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 排队/改期通知：滚动模式重排后需重新通知 */
@Entity
@Table(name = "notifications")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "appointment_id", nullable = false)
    private Long appointmentId;
    @Column(name = "voyage_id", nullable = false)
    private Long voyageId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Types.NotificationType type;
    @Column(nullable = false, length = 512)
    private String message;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Notification() {
    }

    public Notification(Long appointmentId, Long voyageId, Types.NotificationType type, String message) {
        this.appointmentId = appointmentId;
        this.voyageId = voyageId;
        this.type = type;
        this.message = message;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAppointmentId() { return appointmentId; }
    public void setAppointmentId(Long appointmentId) { this.appointmentId = appointmentId; }
    public Long getVoyageId() { return voyageId; }
    public void setVoyageId(Long voyageId) { this.voyageId = voyageId; }
    public Types.NotificationType getType() { return type; }
    public void setType(Types.NotificationType type) { this.type = type; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
