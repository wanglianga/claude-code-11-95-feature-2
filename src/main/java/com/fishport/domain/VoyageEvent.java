package com.fishport.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 航次事件：历史回放时间线；request_key 唯一约束提供幂等 */
@Entity
@Table(name = "voyage_events")
public class VoyageEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "voyage_id")
    private Long voyageId;
    @Column(name = "permit_id")
    private Long permitId;
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private Types.VoyageEventType eventType;
    @Column(columnDefinition = "TEXT")
    private String detail;
    private String actor;
    @Column(name = "request_key", unique = true)
    private String requestKey;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getVoyageId() { return voyageId; }
    public void setVoyageId(Long voyageId) { this.voyageId = voyageId; }
    public Long getPermitId() { return permitId; }
    public void setPermitId(Long permitId) { this.permitId = permitId; }
    public Types.VoyageEventType getEventType() { return eventType; }
    public void setEventType(Types.VoyageEventType eventType) { this.eventType = eventType; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String requestKey) { this.requestKey = requestKey; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setCreatedAtNow() { this.createdAt = LocalDateTime.now(); }
}
