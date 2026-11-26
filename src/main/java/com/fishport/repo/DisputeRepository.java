package com.fishport.repo;

import com.fishport.domain.Dispute;
import com.fishport.domain.Types;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DisputeRepository extends JpaRepository<Dispute, Long> {
    List<Dispute> findByVoyageId(Long voyageId);

    List<Dispute> findByVoyageIdAndStatus(Long voyageId, Types.DisputeStatus status);

    List<Dispute> findByStatus(Types.DisputeStatus status);

    List<Dispute> findByUnloadRecordIdAndStatus(Long unloadRecordId, Types.DisputeStatus status);
}
