package com.fishport.web;

import com.fishport.domain.QuotaAccount;
import com.fishport.domain.QuotaLedgerEntry;
import com.fishport.service.QuotaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "额度账", description = "海区+物种+年度分立的额度账与只追加流水")
@RestController
@RequestMapping("/api/quota-accounts")
public class QuotaController {

    private final QuotaService quotaService;

    public QuotaController(QuotaService quotaService) {
        this.quotaService = quotaService;
    }

    @Operation(summary = "额度账列表（总额/已保留/已使用/可用）")
    @GetMapping
    public List<QuotaAccount> accounts() {
        return quotaService.accounts();
    }

    @Operation(summary = "账户流水")
    @GetMapping("/{id}/ledger")
    public List<QuotaLedgerEntry> ledger(@PathVariable Long id) {
        return quotaService.ledgerOfAccount(id);
    }
}
