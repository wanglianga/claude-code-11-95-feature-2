package com.fishport.repo;

import com.fishport.domain.VoyageEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VoyageEventRepository extends JpaRepository<VoyageEvent, Long> {
    Optional<VoyageEvent> findByRequestKey(String requestKey);

    List<VoyageEvent> findByVoyageIdOrderByCreatedAtAsc(Long voyageId);

    List<VoyageEvent> findByPermitIdOrderByCreatedAtAsc(Long permitId);
}
