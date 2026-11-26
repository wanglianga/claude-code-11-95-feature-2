package com.fishport.service;

import com.fishport.domain.RegulatoryReceipt;
import com.fishport.domain.Types;
import com.fishport.repo.RegulatoryReceiptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** 监管回执：关键事件同步上报（模拟），回执单号可追溯，监管端可确认 */
@Service
public class RegulatorService {

    private final RegulatoryReceiptRepository receiptRepo;

    public RegulatorService(RegulatoryReceiptRepository receiptRepo) {
        this.receiptRepo = receiptRepo;
    }

    @Transactional
    public RegulatoryReceipt report(Types.ReceiptEventType eventType, Long voyageId, Long permitId, String payload) {
        RegulatoryReceipt r = new RegulatoryReceipt();
        r.setReceiptNo("RCPT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        r.setEventType(eventType);
        r.setVoyageId(voyageId);
        r.setPermitId(permitId);
        r.setPayload(payload);
        r.setStatus(Types.ReceiptStatus.REPORTED);
        return receiptRepo.save(r);
    }

    /** 模拟监管端确认回执；重复确认幂等 */
    @Transactional
    public RegulatoryReceipt ack(String receiptNo) {
        RegulatoryReceipt r = receiptRepo.findByReceiptNo(receiptNo)
                .orElseThrow(() -> BizException.notFound("回执不存在: " + receiptNo));
        if (r.getStatus() != Types.ReceiptStatus.ACKED) {
            r.setStatus(Types.ReceiptStatus.ACKED);
            r.setAckedAt(LocalDateTime.now());
            receiptRepo.save(r);
        }
        return r;
    }

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public List<RegulatoryReceipt> listAll() {
        return receiptRepo.findAll();
    }
}
