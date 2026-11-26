package com.fishport.repo;

import com.fishport.domain.CrewMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CrewMemberRepository extends JpaRepository<CrewMember, Long> {
    List<CrewMember> findByRevisionId(Long revisionId);
}
