package com.fishport.repo;

import com.fishport.domain.PortResource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PortResourceRepository extends JpaRepository<PortResource, Long> {
    Optional<PortResource> findByCode(String code);
}
