package com.fishport.service;

import com.fishport.domain.*;
import com.fishport.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 航次状态机与卸货结算：
 * APPROVED -> DEPARTED -> RETURNED -> UNLOADING -> (REWEIGHING) -> CLOSED。
 * 到港/离港按 request_key 幂等；地磅票据按 ticket_no 幂等；签收结算幂等不重复扣减。
 * 重量争议、混装来源不清、许可证过期均阻断结案；争议只能由执法人员办结。
 */
@Service
public class VoyageService {

    private final VoyageRepository voyageRepo;
    private final PermitRepository permitRepo;
    private final UnloadRecordRepository recordRepo;
    private final WeighTicketRepository ticketRepo;
    private final DisputeRepository disputeRepo;
    private final VoyageEventRepository eventRepo;
    private final QuotaService quotaService;
    private final RegulatorService regulatorService;
    private final CrewRosterService crewRosterService;
    private final PlatformTransactionManager txManager;

    public VoyageService(VoyageRepository voyageRepo, PermitRepository permitRepo,
                         UnloadRecordRepository recordRepo, WeighTicketRepository ticketRepo,
                         DisputeRepository disputeRepo, VoyageEventRepository eventRepo,
                         QuotaService quotaService, RegulatorService regulatorService,
                         CrewRosterService crewRosterService, PlatformTransactionManager txManager) {
        this.voyageRepo = voyageRepo;
        this.permitRepo = permitRepo;
        this.recordRepo = recordRepo;
        this.ticketRepo = ticketRepo;
        this.disputeRepo = disputeRepo;
        this.eventRepo = eventRepo;
        this.quotaService = quotaService;
        this.regulatorService = regulatorService;
        this.crewRosterService = crewRosterService;
        this.txManager = txManager;
    }

    // ---------------- 离港 / 返港（幂等） ----------------

    @Transactional
    public IdemResult<Voyage> depart(String voyageNo, String actor) {
        Voyage voyage = lockVoyage(voyageNo);
        String key = "DEPART:" + voyage.getId();
        var existing = eventRepo.findByRequestKey(key);
        if (existing.isPresent()) {
            return IdemResult.duplicated(voyage); // 重复离港上报，返回原处理结果
        }
        requireState(voyage, Types.VoyageState.APPROVED, "离港");
        Permit permit = permitRepo.findById(voyage.getPermitId()).orElseThrow();
        if (permit.expiredAt(LocalDateTime.now())) {
            throw BizException.conflict("PERMIT_EXPIRED", "许可证已过有效期，不得离港");
        }
        // N-5：离港必须持已放行且整份名册核验达标的船员版本（待放行/休息不足/资质缺口均阻断）
        crewRosterService.assertDepartureReady(voyage);
        voyage.setState(Types.VoyageState.DEPARTED);
        voyage.setDepartedAt(LocalDateTime.now());
        voyageRepo.save(voyage);
        recordEvent(voyage, Types.VoyageEventType.DEPART, "船舶离港", actor, key);
        regulatorService.report(Types.ReceiptEventType.VOYAGE_DEPARTED, voyage.getId(), permit.getId(),
                "voyageNo=" + voyageNo);
        return IdemResult.first(voyage);
    }

    @Transactional
    public IdemResult<Voyage> arrive(String voyageNo, String actor) {
        Voyage voyage = lockVoyage(voyageNo);
        String key = "ARRIVE:" + voyage.getId();
        var existing = eventRepo.findByRequestKey(key);
        if (existing.isPresent()) {
            return IdemResult.duplicated(voyage); // 重复到港，返回原处理结果
        }
        requireState(voyage, Types.VoyageState.DEPARTED, "返港");
        voyage.setState(Types.VoyageState.RETURNED);
        voyage.setReturnedAt(LocalDateTime.now());
        voyageRepo.save(voyage);
        recordEvent(voyage, Types.VoyageEventType.ARRIVE, "船舶返港", actor, key);
        regulatorService.report(Types.ReceiptEventType.VOYAGE_RETURNED, voyage.getId(), voyage.getPermitId(),
                "voyageNo=" + voyageNo);
        return IdemResult.first(voyage);
    }

    // ---------------- 分物种卸货 ----------------

    @Transactional
    public UnloadRecord declareUnload(String voyageNo, String speciesCode, BigDecimal declaredKg, String actor) {
        Voyage voyage = lockVoyage(voyageNo);
        if (voyage.getState() != Types.VoyageState.RETURNED && voyage.getState() != Types.VoyageState.UNLOADING) {
            throw BizException.conflict("BAD_STATE", "当前状态 " + voyage.getState() + " 不得申报卸货");
        }
        Permit permit = permitRepo.findById(voyage.getPermitId()).orElseThrow();
        boolean inPermit = permit.getItems().stream().anyMatch(i -> i.getSpeciesCode().equals(speciesCode));
        if (!inPermit) {
            throw BizException.unprocessable("SPECIES_NOT_IN_PERMIT",
                    "物种 " + speciesCode + " 不在许可 " + permit.getPermitNo() + " 的目标物种内");
        }
        if (declaredKg == null || declaredKg.signum() < 0) {
            throw BizException.badRequest("申报卸货量不得为负");
        }
        var existing = recordRepo.findByVoyageIdAndSpeciesCode(voyage.getId(), speciesCode);
        if (existing.isPresent()) {
            throw BizException.conflict("UNLOAD_DUPLICATE",
                    "物种 " + speciesCode + " 本航次已有卸货记录 " + existing.get().getId());
        }
        UnloadRecord record = new UnloadRecord();
        record.setVoyageId(voyage.getId());
        record.setSpeciesCode(speciesCode);
        record.setDeclaredKg(declaredKg);
        record.setStatus(Types.UnloadStatus.DECLARED);
        recordRepo.save(record);
        voyage.setState(Types.VoyageState.UNLOADING);
        voyageRepo.save(voyage);
        recordEvent(voyage, Types.VoyageEventType.UNLOAD_DECLARED,
                "申报卸货 " + speciesCode + " " + declaredKg + "kg", actor, null);
        return record;
    }

    // ---------------- 地磅票据（幂等，含复称） ----------------

    @Transactional
    public IdemResult<WeighTicket> uploadTicket(String ticketNo, String voyageNo, String speciesCode,
                                                BigDecimal grossKg, BigDecimal tareKg,
                                                LocalDateTime weighedAt, String source) {
        var existing = ticketRepo.findByTicketNo(ticketNo);
        if (existing.isPresent()) {
            return IdemResult.duplicated(existing.get()); // 重传票据，返回原处理结果
        }
        if (grossKg == null || tareKg == null || grossKg.compareTo(tareKg) < 0) {
            throw BizException.badRequest("票据重量不合法：毛重必须不小于皮重");
        }
        Voyage voyage = lockVoyage(voyageNo);
        if (voyage.getState() != Types.VoyageState.UNLOADING && voyage.getState() != Types.VoyageState.REWEIGHING) {
            throw BizException.conflict("BAD_STATE", "当前状态 " + voyage.getState() + " 不得上传地磅票据");
        }
        UnloadRecord record = recordRepo.findByVoyageIdAndSpeciesCode(voyage.getId(), speciesCode)
                .orElseThrow(() -> BizException.unprocessable("UNLOAD_NOT_DECLARED",
                        "物种 " + speciesCode + " 尚未申报卸货，地磅票据无处挂靠"));
        if (record.getStatus() == Types.UnloadStatus.SIGNED) {
            throw BizException.conflict("ALREADY_SIGNED", "物种 " + speciesCode + " 已签收，不得再改票");
        }
        boolean reweigh = record.getStatus() == Types.UnloadStatus.DISPUTED
                || record.getStatus() == Types.UnloadStatus.REWEIGHED;

        WeighTicket ticket = new WeighTicket();
        ticket.setTicketNo(ticketNo);
        ticket.setVoyageId(voyage.getId());
        ticket.setUnloadRecordId(record.getId());
        ticket.setSpeciesCode(speciesCode);
        ticket.setGrossKg(grossKg);
        ticket.setTareKg(tareKg);
        ticket.setNetKg(grossKg.subtract(tareKg));
        ticket.setSource(source);
        ticket.setWeighedAt(weighedAt != null ? weighedAt : LocalDateTime.now());
        ticketRepo.save(ticket);

        record.setWeighedKg(ticket.getNetKg());
        record.setWeighTicketId(ticket.getId());
        record.setStatus(reweigh ? Types.UnloadStatus.REWEIGHED : Types.UnloadStatus.WEIGHED);
        recordRepo.save(record);

        if (reweigh) {
            // 复称核定：自动办结该记录上的重量争议
            for (Dispute d : disputeRepo.findByUnloadRecordIdAndStatus(record.getId(), Types.DisputeStatus.OPEN)) {
                if (d.getType() == Types.DisputeType.WEIGHT_DISPUTE) {
                    d.setStatus(Types.DisputeStatus.RESOLVED);
                    d.setResolution("复称核定重量 " + ticket.getNetKg() + "kg（票据 " + ticketNo + "）");
                    d.setResolvedBy("WEIGHBRIDGE");
                    d.setResolvedAt(LocalDateTime.now());
                    disputeRepo.save(d);
                    recordEvent(voyage, Types.VoyageEventType.DISPUTE_RESOLVED,
                            "重量争议经复称办结，核定 " + ticket.getNetKg() + "kg", "WEIGHBRIDGE", null);
                }
            }
        }
        // 若不存在仍处于争议中的记录，航次从复称态回到卸货态
        if (voyage.getState() == Types.VoyageState.REWEIGHING
                && recordRepo.findByVoyageId(voyage.getId()).stream()
                        .noneMatch(r -> r.getStatus() == Types.UnloadStatus.DISPUTED)) {
            voyage.setState(Types.VoyageState.UNLOADING);
            voyageRepo.save(voyage);
        }
        recordEvent(voyage, reweigh ? Types.VoyageEventType.REWEIGH_TICKET : Types.VoyageEventType.WEIGH_TICKET,
                (reweigh ? "复称" : "称重") + "票据 " + ticketNo + " 净重 " + ticket.getNetKg() + "kg", source, null);
        return IdemResult.first(ticket);
    }

    // ---------------- 签收结算（幂等） ----------------

    @Transactional
    public IdemResult<UnloadRecord> sign(Long recordId, String actor) {
        UnloadRecord record = recordRepo.findById(recordId)
                .orElseThrow(() -> BizException.notFound("卸货记录不存在: " + recordId));
        if (record.getStatus() == Types.UnloadStatus.SIGNED) {
            return IdemResult.duplicated(record); // 重复签收，返回原处理结果，不重复扣减
        }
        if (record.getStatus() != Types.UnloadStatus.WEIGHED && record.getStatus() != Types.UnloadStatus.REWEIGHED) {
            throw BizException.conflict("BAD_STATE", "卸货记录状态 " + record.getStatus() + " 不得签收");
        }
        var openDisputes = disputeRepo.findByUnloadRecordIdAndStatus(record.getId(), Types.DisputeStatus.OPEN);
        if (!openDisputes.isEmpty()) {
            throw BizException.conflict("DISPUTE_OPEN",
                    "存在未办结争议（" + openDisputes.get(0).getType() + "），相关物种不能签收结案");
        }
        Voyage voyage = voyageRepo.findById(record.getVoyageId()).orElseThrow();
        Permit permit = permitRepo.findById(voyage.getPermitId()).orElseThrow();
        BigDecimal limit = permit.getItems().stream()
                .filter(i -> i.getSpeciesCode().equals(record.getSpeciesCode()))
                .map(PermitItem::getLimitKg).findFirst()
                .orElseThrow(() -> BizException.unprocessable("SPECIES_NOT_IN_PERMIT", "物种不在许可内"));

        // 结算：按物种扣减实称、释放未捕部分（幂等键防重复扣减）
        quotaService.settle(permit.getId(), voyage.getId(), record.getId(), permit.getAreaCode(),
                record.getSpeciesCode(), permit.getValidFrom().getYear(), limit, record.getWeighedKg());

        record.setStatus(Types.UnloadStatus.SIGNED);
        record.setSignedAt(LocalDateTime.now());
        recordRepo.save(record);
        recordEvent(voyage, Types.VoyageEventType.SIGN,
                "签收 " + record.getSpeciesCode() + " 实称 " + record.getWeighedKg()
                        + "kg，扣减并释放未捕 " + limit.subtract(record.getWeighedKg()).max(BigDecimal.ZERO) + "kg",
                actor, null);
        return IdemResult.first(record);
    }

    // ---------------- 结案 ----------------

    @Transactional
    public IdemResult<Voyage> close(String voyageNo, String actor) {
        Voyage voyage = lockVoyage(voyageNo);
        if (voyage.getState() == Types.VoyageState.CLOSED) {
            return IdemResult.duplicated(voyage);
        }
        if (voyage.getState() != Types.VoyageState.UNLOADING && voyage.getState() != Types.VoyageState.RETURNED
                && voyage.getState() != Types.VoyageState.REWEIGHING) {
            throw BizException.conflict("BAD_STATE", "当前状态 " + voyage.getState() + " 不得结案");
        }
        Permit permit = permitRepo.findById(voyage.getPermitId()).orElseThrow();

        // 许可证过期：不能直接结案。自动登记争议（独立事务，不随本次结案失败回滚）；
        // 待执法人员办结该争议（认定渔获合法性）后才放行结案。
        if (permit.expiredAt(LocalDateTime.now())) {
            var expiryDisputes = disputeRepo.findByVoyageId(voyage.getId()).stream()
                    .filter(d -> d.getType() == Types.DisputeType.PERMIT_EXPIRED).toList();
            boolean reviewed = expiryDisputes.stream()
                    .anyMatch(d -> d.getStatus() == Types.DisputeStatus.RESOLVED);
            boolean open = expiryDisputes.stream()
                    .anyMatch(d -> d.getStatus() == Types.DisputeStatus.OPEN);
            if (!reviewed && !open) {
                ensureExpiryDispute(voyage, permit);
            }
            if (!reviewed) {
                throw BizException.conflict("PERMIT_EXPIRED",
                        "许可证已过期，相关物种不能直接结案，需执法人员办结争议后再结案");
            }
        }
        // 未签收物种 / 未办结争议阻断结案
        List<UnloadRecord> records = recordRepo.findByVoyageId(voyage.getId());
        var unsigned = records.stream().filter(r -> r.getStatus() != Types.UnloadStatus.SIGNED).toList();
        if (!unsigned.isEmpty()) {
            throw BizException.conflict("UNLOAD_UNSIGNED",
                    "物种 " + unsigned.get(0).getSpeciesCode() + " 未签收（状态 " + unsigned.get(0).getStatus() + "），不能结案");
        }
        var openDisputes = disputeRepo.findByVoyageIdAndStatus(voyage.getId(), Types.DisputeStatus.OPEN);
        if (!openDisputes.isEmpty()) {
            throw BizException.conflict("DISPUTE_OPEN",
                    "存在未办结争议（" + openDisputes.get(0).getType() + "），不能结案");
        }

        // 零渔获/未卸货物种：结案时全额释放保留额度
        int year = permit.getValidFrom().getYear();
        for (PermitItem item : permit.getItems()) {
            boolean settled = records.stream()
                    .anyMatch(r -> r.getSpeciesCode().equals(item.getSpeciesCode())
                            && r.getStatus() == Types.UnloadStatus.SIGNED);
            if (!settled) {
                quotaService.releaseAtClose(permit.getId(), voyage.getId(), permit.getAreaCode(),
                        item.getSpeciesCode(), year, item.getLimitKg());
            }
        }

        voyage.setState(Types.VoyageState.CLOSED);
        voyage.setClosedAt(LocalDateTime.now());
        voyageRepo.save(voyage);
        permit.setStatus(Types.PermitStatus.CLOSED);
        permitRepo.save(permit);
        recordEvent(voyage, Types.VoyageEventType.CLOSE, "航次结案", actor, "CLOSE:" + voyage.getId());
        regulatorService.report(Types.ReceiptEventType.VOYAGE_CLOSED, voyage.getId(), permit.getId(),
                "voyageNo=" + voyageNo);
        return IdemResult.first(voyage);
    }

    // ---------------- 争议 ----------------

    /** 许可过期争议登记：独立事务提交，即使结案请求失败回滚也保留该执法线索 */
    private void ensureExpiryDispute(Voyage voyage, Permit permit) {
        TransactionTemplate tt = new TransactionTemplate(txManager);
        tt.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        tt.executeWithoutResult(status -> {
            boolean alreadyOpen = disputeRepo
                    .findByVoyageIdAndStatus(voyage.getId(), Types.DisputeStatus.OPEN)
                    .stream().anyMatch(d -> d.getType() == Types.DisputeType.PERMIT_EXPIRED);
            if (!alreadyOpen) {
                raiseDisputeInternal(voyage, null, Types.DisputeType.PERMIT_EXPIRED,
                        "许可证 " + permit.getPermitNo() + " 有效期至 " + permit.getValidTo() + "，已过期", "SYSTEM");
            }
        });
    }

    @Transactional
    public Dispute raiseDispute(String voyageNo, Long recordId, Types.DisputeType type,
                                String description, String actor) {
        Voyage voyage = lockVoyage(voyageNo);
        UnloadRecord record = null;
        if (recordId != null) {
            record = recordRepo.findById(recordId)
                    .orElseThrow(() -> BizException.notFound("卸货记录不存在: " + recordId));
            if (!record.getVoyageId().equals(voyage.getId())) {
                throw BizException.badRequest("卸货记录不属于该航次");
            }
        }
        Dispute d = raiseDisputeInternal(voyage, record, type, description, actor);
        if (record != null && type == Types.DisputeType.WEIGHT_DISPUTE
                && (record.getStatus() == Types.UnloadStatus.WEIGHED || record.getStatus() == Types.UnloadStatus.REWEIGHED)) {
            record.setStatus(Types.UnloadStatus.DISPUTED);
            recordRepo.save(record);
            voyage.setState(Types.VoyageState.REWEIGHING);
            voyageRepo.save(voyage);
        }
        return d;
    }

    private Dispute raiseDisputeInternal(Voyage voyage, UnloadRecord record, Types.DisputeType type,
                                         String description, String actor) {
        Dispute d = new Dispute();
        d.setVoyageId(voyage.getId());
        d.setUnloadRecordId(record != null ? record.getId() : null);
        d.setType(type);
        d.setStatus(Types.DisputeStatus.OPEN);
        d.setDescription(description);
        d.setRaisedBy(actor);
        disputeRepo.save(d);
        recordEvent(voyage, Types.VoyageEventType.DISPUTE_RAISED,
                "争议登记：" + type + " - " + description, actor, null);
        return d;
    }

    /** 办结争议：仅执法人员；办结意见记为执法事实 */
    @Transactional
    public Dispute resolveDispute(Long disputeId, String resolution, String officer) {
        Dispute d = disputeRepo.findById(disputeId)
                .orElseThrow(() -> BizException.notFound("争议不存在: " + disputeId));
        if (d.getStatus() == Types.DisputeStatus.RESOLVED) {
            return d; // 幂等
        }
        d.setStatus(Types.DisputeStatus.RESOLVED);
        d.setResolution(resolution);
        d.setResolvedBy(officer);
        d.setResolvedAt(LocalDateTime.now());
        disputeRepo.save(d);
        Voyage voyage = voyageRepo.findById(d.getVoyageId()).orElseThrow();
        recordEvent(voyage, Types.VoyageEventType.DISPUTE_RESOLVED,
                "争议办结（执法）：" + resolution, officer, null);
        return d;
    }

    // ---------------- 查询 ----------------

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public Voyage getVoyage(String voyageNo) {
        return voyageRepo.findByVoyageNo(voyageNo)
                .orElseThrow(() -> BizException.notFound("航次不存在: " + voyageNo));
    }

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public List<UnloadRecord> recordsOf(String voyageNo) {
        Voyage v = getVoyage(voyageNo);
        return recordRepo.findByVoyageId(v.getId());
    }

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public List<Dispute> disputesOf(String voyageNo) {
        Voyage v = getVoyage(voyageNo);
        return disputeRepo.findByVoyageId(v.getId());
    }

    private Voyage lockVoyage(String voyageNo) {
        return voyageRepo.lockByVoyageNo(voyageNo)
                .orElseThrow(() -> BizException.notFound("航次不存在: " + voyageNo));
    }

    private void requireState(Voyage voyage, Types.VoyageState expected, String action) {
        if (voyage.getState() != expected) {
            throw BizException.conflict("BAD_STATE",
                    "当前状态 " + voyage.getState() + " 不得" + action + "（要求 " + expected + "）");
        }
    }

    private void recordEvent(Voyage voyage, Types.VoyageEventType type, String detail, String actor, String requestKey) {
        VoyageEvent e = new VoyageEvent();
        e.setVoyageId(voyage.getId());
        e.setPermitId(voyage.getPermitId());
        e.setEventType(type);
        e.setDetail(detail);
        e.setActor(actor);
        e.setRequestKey(requestKey);
        eventRepo.save(e);
    }
}
