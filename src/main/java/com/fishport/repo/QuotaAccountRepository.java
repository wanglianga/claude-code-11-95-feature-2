package com.fishport.repo;

import com.fishport.domain.QuotaAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface QuotaAccountRepository extends JpaRepository<QuotaAccount, Long> {

    Optional<QuotaAccount> findByAreaCodeAndSpeciesCodeAndSeasonYear(String areaCode, String speciesCode, Integer seasonYear);

    /** 额度变动必须串行：悲观写锁 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from QuotaAccount a where a.areaCode = :area and a.speciesCode = :species and a.seasonYear = :year")
    Optional<QuotaAccount> lockByKey(@Param("area") String area, @Param("species") String species, @Param("year") Integer year);
}
