package com.fishport.repo;

import com.fishport.domain.CaseNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CaseNoteRepository extends JpaRepository<CaseNote, Long> {
    List<CaseNote> findByVoyageIdOrderByCreatedAtAsc(Long voyageId);
}
