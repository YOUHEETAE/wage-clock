package com.wageclock.wageclock.domain.settlement;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BulkSettlementItemRepository extends JpaRepository<BulkSettlementItem, Long> {
    boolean existsByPayPeriod_IdAndBulkSettlement_StatusNotIn(
            Long payPeriodId,
            List<BulkSettlement.BulkSettlementStatus> statuses
    );
    List<BulkSettlementItem> findByBulkSettlement_PortOnePaymentIdAndStatusIn(
            String portOnePaymentId,
            List<BulkSettlementItem.BulkSettlementItemStatus> statuses
    );
    @EntityGraph(attributePaths = {"payPeriod", "payPeriod.employment"})
    Optional<BulkSettlementItem> findByTransferId(String transferId);

}
