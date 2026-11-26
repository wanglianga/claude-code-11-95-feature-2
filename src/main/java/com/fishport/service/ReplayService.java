package com.fishport.service;

import com.fishport.domain.*;
import com.fishport.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 历史许可回放：按时间轴重放许可从申请、批复、离港、返港、称重、签收到结案的全部事件，
 * 并附额度账流水，验证最终结余可解释且未重复扣减。
 */
@Service
public class ReplayService {

    private final PermitRepository permitRepo;
    private final PermitApplicationRepository applicationRepo;
    private final VoyageRepository voyageRepo;
    private final VoyageEventRepository eventRepo;
    private final QuotaLedgerRepository ledgerRepo;
    private final QuotaAccountRepository accountRepo;
    private final WeighTicketRepository ticketRepo;
    private final RegulatoryReceiptRepository receiptRepo;

    public ReplayService(PermitRepository permitRepo, PermitApplicationRepository applicationRepo,
                         VoyageRepository voyageRepo, VoyageEventRepository eventRepo,
                         QuotaLedgerRepository ledgerRepo, QuotaAccountRepository accountRepo,
                         WeighTicketRepository ticketRepo, RegulatoryReceiptRepository receiptRepo) {
        this.permitRepo = permitRepo;
        this.applicationRepo = applicationRepo;
        this.voyageRepo = voyageRepo;
        this.eventRepo = eventRepo;
        this.ledgerRepo = ledgerRepo;
        this.accountRepo = accountRepo;
        this.ticketRepo = ticketRepo;
        this.receiptRepo = receiptRepo;
    }

    public record ReplayItem(LocalDateTime at, String category, String type, String detail) {
    }

    public record ReplayResult(Permit permit, PermitApplication application, Voyage voyage,
                               List<ReplayItem> timeline, List<QuotaLedgerEntry> ledger,
                               List<WeighTicket> tickets, List<RegulatoryReceipt> receipts,
                               boolean ledgerConsistent) {
    }

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public ReplayResult replay(String permitNo) {
        Permit permit = permitRepo.findByPermitNo(permitNo)
                .orElseThrow(() -> BizException.notFound("许可不存在: " + permitNo));
        PermitApplication application = applicationRepo.findById(permit.getApplicationId()).orElseThrow();
        Voyage voyage = voyageRepo.findByPermitId(permit.getId()).orElse(null);

        List<ReplayItem> timeline = new ArrayList<>();
        timeline.add(new ReplayItem(application.getCreatedAt(), "APPLICATION",
                application.getStatus().name(),
                "申请提交：海区 " + application.getAreaCode() + "，渔具 " + application.getGearType()
                        + (application.getRejectReasons() != null ? "，驳回原因：" + application.getRejectReasons() : "")));
        for (VoyageEvent e : eventRepo.findByPermitIdOrderByCreatedAtAsc(permit.getId())) {
            timeline.add(new ReplayItem(e.getCreatedAt(), "VOYAGE_EVENT", e.getEventType().name(), e.getDetail()));
        }
        List<QuotaLedgerEntry> ledger = ledgerRepo.findByPermitIdOrderByCreatedAtAsc(permit.getId());
        for (QuotaLedgerEntry le : ledger) {
            timeline.add(new ReplayItem(le.getCreatedAt(), "QUOTA_LEDGER", le.getEntryType().name(),
                    "物种 " + le.getSpeciesCode() + " " + le.getEntryType() + " " + le.getAmountKg()
                            + "kg → 保留 " + le.getReservedAfter() + " / 已用 " + le.getUsedAfter()
                            + " / 可用 " + le.getAvailableAfter()));
        }
        timeline.sort(Comparator.comparing(ReplayItem::at));

        List<WeighTicket> tickets = voyage != null
                ? ticketRepo.findByVoyageIdOrderByCreatedAtAsc(voyage.getId()) : List.of();
        List<RegulatoryReceipt> receipts = receiptRepo.findByPermitIdOrderByCreatedAtAsc(permit.getId());

        return new ReplayResult(permit, application, voyage, timeline, ledger, tickets, receipts,
                ledgerConsistent(ledger));
    }

    /** 校验流水自洽：从期初反推并逐条重放，结余须与流水记录一致（未重复扣减） */
    private boolean ledgerConsistent(List<QuotaLedgerEntry> ledger) {
        record Bal(java.math.BigDecimal reserved, java.math.BigDecimal used) {
        }
        var balances = new java.util.HashMap<Long, Bal>();
        for (QuotaLedgerEntry le : ledger) {
            // 首条流水按类型反推期初余额
            Bal bal = balances.computeIfAbsent(le.getAccountId(), k -> switch (le.getEntryType()) {
                case RESERVE -> new Bal(le.getReservedAfter().subtract(le.getAmountKg()), le.getUsedAfter());
                case DEDUCT -> new Bal(le.getReservedAfter().add(le.getAmountKg()),
                        le.getUsedAfter().subtract(le.getAmountKg()));
                case RELEASE -> new Bal(le.getReservedAfter().add(le.getAmountKg()), le.getUsedAfter());
                case ADJUST -> new Bal(le.getReservedAfter(), le.getUsedAfter().subtract(le.getAmountKg()));
            });
            java.math.BigDecimal reserved = bal.reserved();
            java.math.BigDecimal used = bal.used();
            switch (le.getEntryType()) {
                case RESERVE -> reserved = reserved.add(le.getAmountKg());
                case DEDUCT -> {
                    reserved = reserved.subtract(le.getAmountKg());
                    used = used.add(le.getAmountKg());
                }
                case RELEASE -> reserved = reserved.subtract(le.getAmountKg());
                case ADJUST -> used = used.add(le.getAmountKg());
            }
            if (reserved.compareTo(le.getReservedAfter()) != 0 || used.compareTo(le.getUsedAfter()) != 0) {
                return false;
            }
            balances.put(le.getAccountId(), new Bal(reserved, used));
        }
        return true;
    }
}
