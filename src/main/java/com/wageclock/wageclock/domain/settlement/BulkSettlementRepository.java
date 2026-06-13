package com.wageclock.wageclock.domain.settlement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BulkSettlementRepository extends JpaRepository<BulkSettlement, Long> {
    Optional<BulkSettlement> findByPortOnePaymentId(String portOnePaymentId);
    List<BulkSettlement> findByStatus(BulkSettlement.BulkSettlementStatus status);
}
