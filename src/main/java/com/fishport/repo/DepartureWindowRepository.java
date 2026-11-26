package com.fishport.repo;

import com.fishport.domain.DepartureWindow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DepartureWindowRepository extends JpaRepository<DepartureWindow, Long> {
    Optional<DepartureWindow> findByWindowNo(String windowNo);
    List<DepartureWindow> findByVoyageIdOrderByWindowFromAsc(Long voyageId);
}
