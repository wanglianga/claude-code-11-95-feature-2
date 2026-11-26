package com.fishport.repo;

import com.fishport.domain.CrewPerson;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CrewPersonRepository extends JpaRepository<CrewPerson, Long> {
    Optional<CrewPerson> findByPersonNo(String personNo);
}
