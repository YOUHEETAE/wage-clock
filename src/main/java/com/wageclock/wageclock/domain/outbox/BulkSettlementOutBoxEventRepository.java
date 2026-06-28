package com.wageclock.wageclock.domain.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BulkSettlementOutBoxEventRepository extends JpaRepository<BulkSettlementOutBoxEvent, Long> {
    List<BulkSettlementOutBoxEvent> findByStatus(BulkSettlementOutBoxEvent.OutBoxStatus status);
    Optional<BulkSettlementOutBoxEvent> findByPortOnePaymentId(String portOnePaymentId);
}
