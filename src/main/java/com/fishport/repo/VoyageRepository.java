package com.fishport.repo;

import com.fishport.domain.Types;
import com.fishport.domain.Voyage;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VoyageRepository extends JpaRepository<Voyage, Long> {

    Optional<Voyage> findByVoyageNo(String voyageNo);

    Optional<Voyage> findByPermitId(Long permitId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Voyage v where v.voyageNo = :no")
    Optional<Voyage> lockByVoyageNo(@Param("no") String voyageNo);

    @Query("select v from Voyage v where v.vesselId = :vesselId and v.state <> 'CLOSED'")
    Optional<Voyage> findOpenByVesselId(@Param("vesselId") Long vesselId);

    /** 审批时串行化同一船的许可发放 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Voyage v where v.vesselId = :vesselId and v.state <> 'CLOSED'")
    Optional<Voyage> lockOpenByVesselId(@Param("vesselId") Long vesselId);

    List<Voyage> findByState(Types.VoyageState state);
}
