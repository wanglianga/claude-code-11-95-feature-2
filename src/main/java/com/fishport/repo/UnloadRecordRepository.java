package com.fishport.repo;

import com.fishport.domain.Types;
import com.fishport.domain.UnloadRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UnloadRecordRepository extends JpaRepository<UnloadRecord, Long> {
    List<UnloadRecord> findByVoyageId(Long voyageId);

    Optional<UnloadRecord> findByVoyageIdAndSpeciesCode(Long voyageId, String speciesCode);

    List<UnloadRecord> findByStatus(Types.UnloadStatus status);
}
