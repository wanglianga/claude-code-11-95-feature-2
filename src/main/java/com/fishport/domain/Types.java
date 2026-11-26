package com.fishport.domain;

/** 领域枚举汇总 */
public final class Types {

    private Types() {
    }

    /** 许可申请状态 */
    public enum ApplicationStatus { PENDING, APPROVED, REJECTED }

    /** 许可证状态 */
    public enum PermitStatus { ACTIVE, CLOSED }

    /** 航次状态机：获批 -> 离港 -> 返港 -> 卸货 -> (复称) -> 结案 */
    public enum VoyageState { APPROVED, DEPARTED, RETURNED, UNLOADING, REWEIGHING, CLOSED }

    /** 卸货记录状态 */
    public enum UnloadStatus { DECLARED, WEIGHED, DISPUTED, REWEIGHED, SIGNED }

    /** 额度账流水类型：保留 / 扣减 / 释放 / 调整 */
    public enum LedgerType { RESERVE, DEDUCT, RELEASE, ADJUST }

    /** 预约模式：固定时段 / 滚动叫号 */
    public enum ApptMode { FIXED, ROLLING }

    /** 预约状态 */
    public enum ApptStatus { BOOKED, SCHEDULED, CHECKED_IN, RESCHEDULED, DONE, CANCELLED }

    /** 争议类型：重量争议 / 混装来源不清 / 许可证过期 */
    public enum DisputeType { WEIGHT_DISPUTE, MIXED_LOAD, PERMIT_EXPIRED }

    public enum DisputeStatus { OPEN, RESOLVED }

    /** 通知类型：排定 / 改期 / 队列变化 / 叫号 */
    public enum NotificationType { SCHEDULED, RESCHEDULED, QUEUE_CHANGED, CALLED }

    public enum ReceiptStatus { REPORTED, ACKED }

    /** 航次事件类型（用于历史回放） */
    public enum VoyageEventType {
        PERMIT_ISSUED, DEPART, ARRIVE, UNLOAD_DECLARED, WEIGH_TICKET, REWEIGH_TICKET,
        SIGN, DISPUTE_RAISED, DISPUTE_RESOLVED, NOTE_ADDED, CLOSE,
        ROSTER_REVISION, ROSTER_ACK, GATE_SCAN
    }

    /** 监管回执事件类型 */
    public enum ReceiptEventType { PERMIT_ISSUED, VOYAGE_DEPARTED, VOYAGE_RETURNED, VOYAGE_CLOSED }

    /** 名册修订状态：草拟（船东未决定）/ 待放行（已重签，等待接收方确认）/ 已放行（三方确认齐备）/ 已撤销（被更新修订取代） */
    public enum RosterStatus { DRAFT, PENDING_RELEASE, RELEASED, SUPERSEDED }

    /** 名册修订来源：INITIAL 许可批复时的初版 / REST 原船长休息达标 / SUBSTITUTE 启用替补 / CORRECTION 名册更正 */
    public enum RosterChangeKind { INITIAL, REST, SUBSTITUTE, CORRECTION }

    /** 名册确认接收方：港口闸口 / 保险 / 应急联络 */
    public enum RosterParty { PORT, INSURER, EMERGENCY }

    /** 接收方确认状态：待签 / 已确认 / 已失效（投影或协议版本变化） */
    public enum AcknowledgementStatus { PENDING, CONFIRMED, INVALIDATED }

    /** 二维码闸口凭证状态：有效 / 已撤销（旧名册凭证在重签确认时同时撤销） */
    public enum GateCredentialStatus { ACTIVE, REVOKED }

    /** 闸口扫码结果：放行 / 拒绝 */
    public enum GateScanVerdict { PASS, REJECT }

    /** 船东决定：原船长休息后继续 / 启用替补 / 名册更正 */
    public enum OwnerDecisionType { KEEP_CAPTAIN_REST, USE_SUBSTITUTE, CORRECT_ROSTER }

    /** 离港后迟到签到的处理：仅留作核查，不回滚新名册 */
    public enum LateCheckinStatus { RECORDED_FOR_AUDIT }
}
