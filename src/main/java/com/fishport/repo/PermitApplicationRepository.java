package com.fishport.repo;

import com.fishport.domain.PermitApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PermitApplicationRepository extends JpaRepository<PermitApplication, Long> {
    Optional<PermitApplication> findByApplicationNo(String applicationNo);
}
