package com.fishport.repo;

import com.fishport.domain.OwnerDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OwnerDecisionRepository extends JpaRepository<OwnerDecision, Long> {
    Optional<OwnerDecision> findByDecisionNo(String decisionNo);
    List<OwnerDecision> findByVoyageIdOrderByDecidedAtAsc(Long voyageId);
}
