package com.fishport.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 争议：OPEN 期间阻断相关物种结案；只能由执法人员办结 */
@Entity
@Table(name = "disputes")
public class Dispute {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "voyage_id", nullable = false)
    private Long voyageId;
    @Column(name = "unload_record_id")
    private Long unloadRecordId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Types.DisputeType type;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Types.DisputeStatus status = Types.DisputeStatus.OPEN;
    @Column(length = 512)
    private String description;
    @Column(name = "raised_by")
    private String raisedBy;
    @Column(length = 512)
    private String resolution;
    @Column(name = "resolved_by")
    private String resolvedBy;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getVoyageId() { return voyageId; }
    public void setVoyageId(Long voyageId) { this.voyageId = voyageId; }
    public Long getUnloadRecordId() { return unloadRecordId; }
    public void setUnloadRecordId(Long unloadRecordId) { this.unloadRecordId = unloadRecordId; }
    public Types.DisputeType getType() { return type; }
    public void setType(Types.DisputeType type) { this.type = type; }
    public Types.DisputeStatus getStatus() { return status; }
    public void setStatus(Types.DisputeStatus status) { this.status = status; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getRaisedBy() { return raisedBy; }
    public void setRaisedBy(String raisedBy) { this.raisedBy = raisedBy; }
    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }
    public String getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
}
