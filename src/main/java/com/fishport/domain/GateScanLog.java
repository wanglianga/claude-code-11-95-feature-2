package com.fishport.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 闸口扫码记录：每次刷二维码留痕。
 * 已离港后的迟到签到只能留作核查（RECORDED_FOR_AUDIT），不能回滚新名册。
 */
@Entity
@Table(name = "gate_scan_logs")
public class GateScanLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "scan_no", nullable = false, unique = true)
    private String scanNo;
    @Column(name = "voyage_id")
    private Long voyageId;
    @Column(name = "qr_code", nullable = false, length = 128)
    private String qrCode;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Types.GateScanVerdict verdict;
    /** 拒绝/放行原因码（OLD_CREDENTIAL_REVOKED / WINDOW_CLOSED / ACK_PENDING / NOT_YET_RESTED ...） */
    @Column(name = "reason_code", length = 64)
    private String reasonCode;
    @Column(length = 512)
    private String detail;
    /** 扫码时航次是否已离港：已离港后的迟到签到仅留作核查 */
    @Column(name = "after_departure", nullable = false)
    private boolean afterDeparture;
    @Enumerated(EnumType.STRING)
    @Column(name = "audit_status")
    private Types.LateCheckinStatus auditStatus;
    @Column(name = "scanned_at", nullable = false)
    private LocalDateTime scannedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getScanNo() { return scanNo; }
    public void setScanNo(String scanNo) { this.scanNo = scanNo; }
    public Long getVoyageId() { return voyageId; }
    public void setVoyageId(Long voyageId) { this.voyageId = voyageId; }
    public String getQrCode() { return qrCode; }
    public void setQrCode(String qrCode) { this.qrCode = qrCode; }
    public Types.GateScanVerdict getVerdict() { return verdict; }
    public void setVerdict(Types.GateScanVerdict verdict) { this.verdict = verdict; }
    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public boolean isAfterDeparture() { return afterDeparture; }
    public void setAfterDeparture(boolean afterDeparture) { this.afterDeparture = afterDeparture; }
    public Types.LateCheckinStatus getAuditStatus() { return auditStatus; }
    public void setAuditStatus(Types.LateCheckinStatus auditStatus) { this.auditStatus = auditStatus; }
    public LocalDateTime getScannedAt() { return scannedAt; }
    public void setScannedAt(LocalDateTime scannedAt) { this.scannedAt = scannedAt; }
}
