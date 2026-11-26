package com.fishport.service;

import com.fishport.domain.QuotaAccount;
import com.fishport.domain.QuotaLedgerEntry;
import com.fishport.domain.Types;
import com.fishport.repo.QuotaAccountRepository;
import com.fishport.repo.QuotaLedgerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 额度账服务：
 * - 批复时 RESERVE 保留本航次申报上限；
 * - 签收实称后 DEDUCT 按物种扣减、RELEASE 释放未捕部分；
 * - 所有流水只追加，idempotency_key 唯一，保证不重复扣减、结余可回放解释。
 */
@Service
public class QuotaService {

    private final QuotaAccountRepository accountRepo;
    private final QuotaLedgerRepository ledgerRepo;

    public QuotaService(QuotaAccountRepository accountRepo, QuotaLedgerRepository ledgerRepo) {
        this.accountRepo = accountRepo;
        this.ledgerRepo = ledgerRepo;
    }

    /** 批复保留：同一账户串行（悲观锁），幂等键 RESERVE:{permitId}:{species} */
    @Transactional
    public QuotaLedgerEntry reserve(Long permitId, Long voyageId, String areaCode, String speciesCode,
                                    int year, BigDecimal amount) {
        String key = "RESERVE:" + permitId + ":" + speciesCode;
        Optional<QuotaLedgerEntry> existing = ledgerRepo.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            return existing.get();
        }
        QuotaAccount account = lockAccount(areaCode, speciesCode, year);
        if (account.available().compareTo(amount) < 0) {
            throw BizException.unprocessable("QUOTA_INSUFFICIENT",
                    "海区 " + areaCode + " 物种 " + speciesCode + " 可用额度不足：剩余 "
                            + account.available() + "kg，申报 " + amount + "kg（不同海区/物种额度不得合并计算）");
        }
        account.setReservedKg(account.getReservedKg().add(amount));
        accountRepo.save(account);
        return append(account, voyageId, permitId, null, speciesCode, Types.LedgerType.RESERVE, amount, key,
                "许可批复保留本航次申报上限");
    }

    /**
     * 签收结算：DEDUCT 实称扣减 + RELEASE 释放未捕部分。
     * 幂等键 DEDUCT:{recordId} / RELEASE:{recordId}，重复签收直接返回已有流水，不重复扣减。
     */
    @Transactional
    public void settle(Long permitId, Long voyageId, Long recordId, String areaCode, String speciesCode,
                       int year, BigDecimal declaredLimit, BigDecimal actualKg) {
        String deductKey = "DEDUCT:" + recordId;
        if (ledgerRepo.findByIdempotencyKey(deductKey).isPresent()) {
            return; // 已结算过，幂等返回
        }
        QuotaAccount account = lockAccount(areaCode, speciesCode, year);
        // 实称部分：保留转已用
        account.setReservedKg(account.getReservedKg().subtract(actualKg));
        account.setUsedKg(account.getUsedKg().add(actualKg));
        accountRepo.save(account);
        append(account, voyageId, permitId, recordId, speciesCode, Types.LedgerType.DEDUCT, actualKg, deductKey,
                "签收实称扣减");

        BigDecimal release = declaredLimit.subtract(actualKg);
        if (release.signum() > 0) {
            String releaseKey = "RELEASE:" + recordId;
            if (ledgerRepo.findByIdempotencyKey(releaseKey).isEmpty()) {
                account.setReservedKg(account.getReservedKg().subtract(release));
                accountRepo.save(account);
                append(account, voyageId, permitId, recordId, speciesCode, Types.LedgerType.RELEASE, release,
                        releaseKey, "释放未捕部分额度");
            }
        }
    }

    /** 结案释放：航次结案时对未签收物种全额释放保留额度（幂等） */
    @Transactional
    public void releaseAtClose(Long permitId, Long voyageId, String areaCode, String speciesCode,
                               int year, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            return;
        }
        String key = "RELEASE:CLOSE:" + permitId + ":" + speciesCode;
        if (ledgerRepo.findByIdempotencyKey(key).isPresent()) {
            return;
        }
        QuotaAccount account = lockAccount(areaCode, speciesCode, year);
        account.setReservedKg(account.getReservedKg().subtract(amount));
        accountRepo.save(account);
        append(account, voyageId, permitId, null, speciesCode, Types.LedgerType.RELEASE, amount, key,
                "结案释放未卸货物种保留额度");
    }

    private QuotaAccount lockAccount(String areaCode, String speciesCode, int year) {
        return accountRepo.lockByKey(areaCode, speciesCode, year)
                .orElseThrow(() -> BizException.unprocessable("QUOTA_ACCOUNT_MISSING",
                        "海区 " + areaCode + " 物种 " + speciesCode + " 年度 " + year + " 无额度账"));
    }

    private QuotaLedgerEntry append(QuotaAccount account, Long voyageId, Long permitId, Long recordId,
                                    String speciesCode, Types.LedgerType type, BigDecimal amount,
                                    String key, String remark) {
        QuotaLedgerEntry e = new QuotaLedgerEntry();
        e.setAccountId(account.getId());
        e.setVoyageId(voyageId);
        e.setPermitId(permitId);
        e.setUnloadRecordId(recordId);
        e.setSpeciesCode(speciesCode);
        e.setEntryType(type);
        e.setAmountKg(amount);
        e.setReservedAfter(account.getReservedKg());
        e.setUsedAfter(account.getUsedKg());
        e.setAvailableAfter(account.available());
        e.setIdempotencyKey(key);
        e.setRemark(remark);
        return ledgerRepo.save(e);
    }

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public List<QuotaAccount> accounts() {
        return accountRepo.findAll();
    }

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public List<QuotaLedgerEntry> ledgerOfAccount(Long accountId) {
        return ledgerRepo.findByAccountIdOrderByCreatedAtAsc(accountId);
    }

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public List<QuotaLedgerEntry> ledgerOfPermit(Long permitId) {
        return ledgerRepo.findByPermitIdOrderByCreatedAtAsc(permitId);
    }
}
