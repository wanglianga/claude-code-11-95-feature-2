package com.fishport.repo;

import com.fishport.domain.CrewRosterRevision;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CrewRosterRevisionRepository extends JpaRepository<CrewRosterRevision, Long> {

    List<CrewRosterRevision> findByVoyageIdOrderByRevisionSeqAsc(Long voyageId);

    Optional<CrewRosterRevision> findTopByVoyageIdOrderByRevisionSeqDesc(Long voyageId);

    Optional<CrewRosterRevision> findByRevisionNo(String revisionNo);

    /** 串行化同一航次的名册重签，避免并发产生两个当前版本 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CrewRosterRevision r where r.voyageId = :voyageId order by r.revisionSeq desc")
    List<CrewRosterRevision> lockByVoyageId(@Param("voyageId") Long voyageId);
}
