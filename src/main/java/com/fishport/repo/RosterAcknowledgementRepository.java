package com.fishport.repo;

import com.fishport.domain.RosterAcknowledgement;
import com.fishport.domain.Types;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RosterAcknowledgementRepository extends JpaRepository<RosterAcknowledgement, Long> {
    List<RosterAcknowledgement> findByRevisionIdOrderByPartyAsc(Long revisionId);
    Optional<RosterAcknowledgement> findByRevisionIdAndParty(Long revisionId, Types.RosterParty party);
    List<RosterAcknowledgement> findByVoyageIdOrderByIdAsc(Long voyageId);
}
