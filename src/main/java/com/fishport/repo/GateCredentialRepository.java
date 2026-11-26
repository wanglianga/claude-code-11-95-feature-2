package com.fishport.repo;

import com.fishport.domain.GateCredential;
import com.fishport.domain.Types;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GateCredentialRepository extends JpaRepository<GateCredential, Long> {
    Optional<GateCredential> findByQrCode(String qrCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from GateCredential c where c.qrCode = :qrCode")
    Optional<GateCredential> lockByQrCode(@Param("qrCode") String qrCode);

    List<GateCredential> findByVoyageIdAndStatus(Long voyageId, Types.GateCredentialStatus status);
}
