package com.fishport.repo;

import com.fishport.domain.FishingArea;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FishingAreaRepository extends JpaRepository<FishingArea, String> {
}
