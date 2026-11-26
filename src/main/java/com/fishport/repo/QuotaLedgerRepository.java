package com.fishport.repo;

import com.fishport.domain.QuotaLedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuotaLedgerRepository extends JpaRepository<QuotaLedgerEntry, Long> {
    Optional<QuotaLedgerEntry> findByIdempotencyKey(String idempotencyKey);

    List<QuotaLedgerEntry> findByAccountIdOrderByCreatedAtAsc(Long accountId);

    List<QuotaLedgerEntry> findByVoyageIdOrderByCreatedAtAsc(Long voyageId);

    List<QuotaLedgerEntry> findByPermitIdOrderByCreatedAtAsc(Long permitId);
}
