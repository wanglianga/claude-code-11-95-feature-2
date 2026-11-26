package com.fishport.repo;

import com.fishport.domain.GearRestriction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GearRestrictionRepository extends JpaRepository<GearRestriction, Long> {
    Optional<GearRestriction> findByAreaCodeAndGearType(String areaCode, String gearType);
}
