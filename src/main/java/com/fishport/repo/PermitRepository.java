package com.fishport.repo;

import com.fishport.domain.Permit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PermitRepository extends JpaRepository<Permit, Long> {
    Optional<Permit> findByPermitNo(String permitNo);

    List<Permit> findByVesselIdOrderByCreatedAtDesc(Long vesselId);
}
