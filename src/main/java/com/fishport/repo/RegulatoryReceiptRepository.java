package com.fishport.repo;

import com.fishport.domain.RegulatoryReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RegulatoryReceiptRepository extends JpaRepository<RegulatoryReceipt, Long> {
    Optional<RegulatoryReceipt> findByReceiptNo(String receiptNo);

    List<RegulatoryReceipt> findByVoyageIdOrderByCreatedAtAsc(Long voyageId);

    List<RegulatoryReceipt> findByPermitIdOrderByCreatedAtAsc(Long permitId);
}
