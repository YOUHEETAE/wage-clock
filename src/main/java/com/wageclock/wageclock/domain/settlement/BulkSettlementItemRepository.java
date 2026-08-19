package com.wageclock.wageclock.domain.settlement;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BulkSettlementItemRepository extends JpaRepository<BulkSettlementItem, Long> {
    boolean existsByPayPeriod_IdAndBulkSettlement_StatusNotIn(
            Long payPeriodId,
            List<BulkSettlement.BulkSettlementStatus> statuses
    );
    // 컨텍스트를 만들 때 아이템마다 worker 프록시를 깨우면 N+1이 된다.
    // 단일 연관만 타므로 조인해도 행이 곱해지지 않는다.
    @EntityGraph(attributePaths = {
            "bulkSettlement",
            "payPeriod",
            "payPeriod.employment",
            "payPeriod.employment.worker"
    })
    List<BulkSettlementItem> findByBulkSettlement_PortOnePaymentIdAndStatusIn(
            String portOnePaymentId,
            List<BulkSettlementItem.BulkSettlementItemStatus> statuses
    );

    Optional<BulkSettlementItem> findByMessageNo(String messageNo);

    @Query("SELECT i FROM BulkSettlementItem i " +
            "JOIN FETCH i.payPeriod pp " +
            "JOIN FETCH pp.employment emp " +
            "JOIN FETCH emp.worker " +
            "WHERE i.id = :id")
    Optional<BulkSettlementItem> findByIdWithWorker(Long id);


}
