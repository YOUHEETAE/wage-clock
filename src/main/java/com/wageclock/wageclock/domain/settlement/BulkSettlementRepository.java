package com.wageclock.wageclock.domain.settlement;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BulkSettlementRepository extends JpaRepository<BulkSettlement, Long> {
    Optional<BulkSettlement> findByPortOnePaymentId(String portOnePaymentId);

    List<BulkSettlement> findByStatus(BulkSettlement.BulkSettlementStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM BulkSettlement b WHERE b.portOnePaymentId = :portOnePaymentId")
    Optional<BulkSettlement> findByPortOnePaymentIdWithLock(@Param("portOnePaymentId") String portOnePaymentId);

}
