package com.fishport.repo;

import com.fishport.domain.GateScanLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GateScanLogRepository extends JpaRepository<GateScanLog, Long> {
    Optional<GateScanLog> findByScanNo(String scanNo);
    List<GateScanLog> findByVoyageIdOrderByScannedAtAsc(Long voyageId);
}
