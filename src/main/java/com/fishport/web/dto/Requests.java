package com.fishport.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 请求 DTO 汇总 */
public final class Requests {

    private Requests() {
    }

    /** 许可申请：船号、船长与船员、计划海区、作业时间、渔具、目标物种及申报捕捞上限 */
    public record ApplyPermitRequest(
            @NotBlank String vesselNo,
            @NotBlank String captainName,
            @Positive int crewCount,
            String crewList,
            @NotBlank String areaCode,
            @NotNull LocalDateTime plannedStart,
            @NotNull LocalDateTime plannedEnd,
            @NotBlank String gearType,
            @NotNull List<Item> items) {
        public record Item(@NotBlank String speciesCode, @NotNull @Positive BigDecimal declaredLimitKg) {
        }
    }

    public record DeclareUnloadRequest(@NotBlank String speciesCode, @NotNull BigDecimal declaredKg) {
    }

    public record WeighTicketRequest(
            @NotBlank String ticketNo,
            @NotBlank String voyageNo,
            @NotBlank String speciesCode,
            @NotNull BigDecimal grossKg,
            @NotNull BigDecimal tareKg,
            LocalDateTime weighedAt) {
    }

    public record DisputeRequest(Long unloadRecordId, @NotBlank String type, String description) {
    }

    public record ResolveRequest(@NotBlank String resolution) {
    }

    public record NoteRequest(@NotBlank String content) {
    }

    public record BookAppointmentRequest(
            @NotBlank String voyageNo,
            @NotBlank String portCode,
            @NotBlank String mode,
            LocalDateTime requestedStart,
            LocalDateTime plannedArrival,
            @Positive int durationMin,
            @NotNull LocalDateTime latestFinish,
            BigDecimal expectedUnloadKg) {
    }

    public record ComputeRequest(@NotBlank String portCode, @NotNull LocalDate date, @NotBlank String mode) {
    }

    public record CheckinRequest(@NotNull LocalDateTime actualArrival) {
    }

    /** 模拟船舶终端事件 */
    public record TerminalEventRequest(@NotBlank String voyageNo, @NotBlank String eventType, String eventId) {
    }

    /** N-5 初版名册 */
    public record InitRosterRequest(@NotBlank String voyageNo,
                                    @NotNull List<@NotBlank String> personNos,
                                    List<String> requiredQualifications,
                                    String reason) {
    }

    /** N-5 船东决定（原船长休息 / 启用替补 / 更正通用理由） */
    public record RosterDecisionRequest(String reason) {
    }

    /** N-5 启用替补 */
    public record SubstituteRequest(@NotBlank String replacementPersonNo, String reason) {
    }

    /** N-5 名册更正：可给整份新人员清单（如轮机员请假换人），可覆盖应急电话 */
    public record RosterCorrectionRequest(List<String> personNos,
                                          Map<String, String> emergencyPhones,
                                          String reason) {
    }

    /** N-5 离港窗登记 */
    public record DepartureWindowRequest(@NotBlank String voyageNo, @NotBlank String portCode,
                                         @NotBlank String kind,
                                         @NotNull LocalDateTime windowFrom,
                                         @NotNull LocalDateTime windowTo) {
    }

    /** N-5 闸口扫码 */
    public record GateScanRequest(@NotBlank String qrCode) {
    }

    /** N-5 模拟时钟固定 */
    public record ClockRequest(@NotNull LocalDateTime at) {
    }

    /** N-5 人员请假/复职 */
    public record PersonLeaveRequest(boolean active) {
    }
}
