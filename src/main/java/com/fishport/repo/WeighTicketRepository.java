package com.fishport.repo;

import com.fishport.domain.WeighTicket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WeighTicketRepository extends JpaRepository<WeighTicket, Long> {
    Optional<WeighTicket> findByTicketNo(String ticketNo);

    List<WeighTicket> findByVoyageIdOrderByCreatedAtAsc(Long voyageId);
}
