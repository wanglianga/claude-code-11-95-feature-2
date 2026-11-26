package com.fishport.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 港口闸口二维码凭证：只接受当时有效的船员版本。
 * 名册重签放行时旧凭证同时撤销（REVOKED），旧二维码再刷一律拒绝。
 */
@Entity
@Table(name = "gate_credentials")
public class GateCredential {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /** 二维码内容（唯一码） */
    @Column(name = "qr_code", nullable = false, unique = true, length = 128)
    private String qrCode;
    @Column(name = "voyage_id", nullable = false)
    private Long voyageId;
    @Column(name = "revision_id", nullable = false)
    private Long revisionId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Types.GateCredentialStatus status = Types.GateCredentialStatus.ACTIVE;
    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt = LocalDateTime.now();
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;
    /** 撤销原因（如 名册修订 RST-xxx 重签放行，旧凭证同时撤销） */
    @Column(name = "revoke_reason", length = 512)
    private String revokeReason;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getQrCode() { return qrCode; }
    public void setQrCode(String qrCode) { this.qrCode = qrCode; }
    public Long getVoyageId() { return voyageId; }
    public void setVoyageId(Long voyageId) { this.voyageId = voyageId; }
    public Long getRevisionId() { return revisionId; }
    public void setRevisionId(Long revisionId) { this.revisionId = revisionId; }
    public Types.GateCredentialStatus getStatus() { return status; }
    public void setStatus(Types.GateCredentialStatus status) { this.status = status; }
    public LocalDateTime getIssuedAt() { return issuedAt; }
    public void setIssuedAt(LocalDateTime issuedAt) { this.issuedAt = issuedAt; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(LocalDateTime revokedAt) { this.revokedAt = revokedAt; }
    public String getRevokeReason() { return revokeReason; }
    public void setRevokeReason(String revokeReason) { this.revokeReason = revokeReason; }
}
