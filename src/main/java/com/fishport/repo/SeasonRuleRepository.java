package com.fishport.repo;

import com.fishport.domain.SeasonRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeasonRuleRepository extends JpaRepository<SeasonRule, Long> {
    List<SeasonRule> findByAreaCodeAndSpeciesCode(String areaCode, String speciesCode);
}
